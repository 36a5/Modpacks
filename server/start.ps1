# al Shabab dedicated server launcher (Windows).
# Downloads Forge + pack contents on first run, self-updates the pack on every restart.
$ErrorActionPreference = "Stop"
$runDir = Join-Path $PSScriptRoot "run"
if (-not (Test-Path $runDir)) { New-Item -ItemType Directory $runDir | Out-Null }
Set-Location $runDir

# ── Settings ────────────────────────────────────────────────────────────────
$PackUrl      = if ($env:PACK_URL) { $env:PACK_URL } else { "https://36a5.github.io/Modpacks/pack-two/pack.toml" }
$McVersion    = "1.20.1"
$ForgeVersion = "47.4.18"
$Memory       = if ($env:MEMORY) { $env:MEMORY } else { "8G" }
# ────────────────────────────────────────────────────────────────────────────

$bootstrapUrl      = "https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest/download/packwiz-installer-bootstrap.jar"
$forgeInstallerUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/$McVersion-$ForgeVersion/forge-$McVersion-$ForgeVersion-installer.jar"

if (-not (Test-Path "libraries/net/minecraftforge/forge/$McVersion-$ForgeVersion")) {
    Write-Host "[al-shabab] Installing Forge $McVersion-$ForgeVersion..."
    Invoke-WebRequest -Uri $forgeInstallerUrl -OutFile forge-installer.jar
    java -jar forge-installer.jar --installServer
    Remove-Item forge-installer.jar, forge-installer.jar.log -ErrorAction SilentlyContinue
}

if (-not (Test-Path "packwiz-installer-bootstrap.jar")) {
    Invoke-WebRequest -Uri $bootstrapUrl -OutFile packwiz-installer-bootstrap.jar
}
Write-Host "[al-shabab] Syncing pack from $PackUrl..."
java -jar packwiz-installer-bootstrap.jar -g -s server $PackUrl
if ($LASTEXITCODE -ne 0) {
    Write-Host "[al-shabab] Pack sync FAILED - refusing to start the server without mods."
    exit 1
}

# ParallelGCThreads/ConcGCThreads capped: box is only 6c/12t and the client runs on the
# same machine while playing. Uncapped, G1 grabs threads = logical cores and starves the
# client's render thread during the join-time chunk-gen GC spike. Leave headroom for it.
Set-Content user_jvm_args.txt "-Xms$Memory -Xmx$Memory -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:ParallelGCThreads=4 -XX:ConcGCThreads=2"
Set-Content eula.txt "eula=true"

# ── server.properties baseline ──────────────────────────────────────────────
# The template used to be documentation that nothing applied, so the live file drifted all
# the way back to vanilla defaults. Now [enforce] keys are written on every boot and [warn]
# keys only produce a warning: silently flipping online-mode would rewrite every player's
# UUID. Read as ISO-8859-1 because that is what java.util.Properties writes.
function Sync-ServerProperties {
    param(
        [string]$TemplatePath = (Join-Path $PSScriptRoot "server.properties.template"),
        [string]$LivePath     = (Join-Path $runDir "server.properties")
    )
    $templatePath = $TemplatePath
    $livePath     = $LivePath
    if (-not (Test-Path $templatePath)) { return }

    $enforce = [ordered]@{}
    $warn    = [ordered]@{}
    $section = $null
    foreach ($raw in Get-Content $templatePath) {
        $line = $raw.Trim()
        if ($line -eq "[enforce]") { $section = $enforce; continue }
        if ($line -eq "[warn]")    { $section = $warn;    continue }
        if ($line -eq "" -or $line.StartsWith("#") -or $null -eq $section) { continue }
        $i = $line.IndexOf("=")
        if ($i -lt 1) { continue }
        $section[$line.Substring(0, $i)] = $line.Substring($i + 1)
    }

    $latin1 = [System.Text.Encoding]::GetEncoding(28591)

    if (-not (Test-Path $livePath)) {
        Write-Host "[al-shabab] No server.properties yet - writing the full baseline."
        $fresh = @()
        foreach ($k in $enforce.Keys) { $fresh += "$k=$($enforce[$k])" }
        foreach ($k in $warn.Keys)    { $fresh += "$k=$($warn[$k])" }
        [IO.File]::WriteAllLines($livePath, $fresh, $latin1)
        return
    }

    $lines   = [IO.File]::ReadAllLines($livePath, $latin1)
    $seen    = @{}
    $changed = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ($line -match '^\s*#' -or -not $line.Contains("=")) { continue }
        $eq = $line.IndexOf("=")
        $k  = $line.Substring(0, $eq)
        $v  = $line.Substring($eq + 1)
        $seen[$k] = $true

        if ($enforce.Contains($k)) {
            if ($v -ne $enforce[$k]) {
                Write-Host "[al-shabab] server.properties: $k=$v -> $($enforce[$k])"
                $lines[$i] = "$k=$($enforce[$k])"
                $changed = $true
            }
        } elseif ($warn.Contains($k) -and $v -ne $warn[$k]) {
            Write-Warning "server.properties: $k=$v, but the baseline says $k=$($warn[$k]). Not changing it - see server.properties.template."
        }
    }
    foreach ($k in $enforce.Keys) {
        if (-not $seen.ContainsKey($k)) {
            Write-Host "[al-shabab] server.properties: adding $k=$($enforce[$k])"
            $lines += "$k=$($enforce[$k])"
            $changed = $true
        }
    }

    if ($changed) { [IO.File]::WriteAllLines($livePath, $lines, $latin1) }
    else          { Write-Host "[al-shabab] server.properties matches the enforced baseline." }
}

Sync-ServerProperties

# ── Discord bots: live exactly as long as the server does ───────────────────
# Started here, killed in the finally block below even if Minecraft crashes.
$botDir = "C:\vs_code_workspace\Minecraft-workspace"
$bot = $null
if ((Test-Path $botDir) -and (Test-Path (Join-Path $botDir ".env"))) {
    Write-Host "[al-shabab] Starting Discord bots..."
    $bot = Start-Process -FilePath (Join-Path $botDir ".venv\Scripts\python.exe") -ArgumentList "-m","bots.main" -WorkingDirectory $botDir -PassThru -NoNewWindow
} elseif (Test-Path $botDir) {
    Write-Host "[al-shabab] Discord bots present but no .env - skipping (copy .env.example to .env)."
}

try {
    & .\run.bat nogui
} finally {
    if ($bot -and -not $bot.HasExited) {
        Write-Host "[al-shabab] Server stopped - stopping Discord bots..."
        Stop-Process -Id $bot.Id -Force -ErrorAction SilentlyContinue
    }
}
