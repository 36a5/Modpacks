# al Shabab dedicated server launcher (Windows).
# Downloads Forge + pack contents on first run, self-updates the pack on every restart.
$ErrorActionPreference = "Stop"
$runDir = Join-Path $PSScriptRoot "run"
if (-not (Test-Path $runDir)) { New-Item -ItemType Directory $runDir | Out-Null }
Set-Location $runDir

# ── Settings ────────────────────────────────────────────────────────────────
$PackUrl      = if ($env:PACK_URL) { $env:PACK_URL } else { "https://36a5.github.io/Modpacks/pack.toml" }
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

Set-Content user_jvm_args.txt "-Xms$Memory -Xmx$Memory -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20"
Set-Content eula.txt "eula=true"

# ── Discord bots: live exactly as long as the server does ───────────────────
# Started here, killed in the finally block below even if Minecraft crashes.
$botDir = Join-Path (Split-Path $PSScriptRoot -Parent) "..\discord-bots" | Resolve-Path -ErrorAction SilentlyContinue
$bot = $null
if ($botDir -and (Test-Path (Join-Path $botDir ".env"))) {
    Write-Host "[al-shabab] Starting Discord bots..."
    $bot = Start-Process -FilePath "node" -ArgumentList "src/index.js" -WorkingDirectory $botDir -PassThru -NoNewWindow
} elseif ($botDir) {
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
