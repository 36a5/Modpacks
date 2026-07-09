# ============================================================
#  al Shabab - one-click installer / updater
#
#  Does everything a player would otherwise do by hand:
#    1. finds Java 17, or downloads a portable copy (no admin rights needed)
#    2. installs Forge 1.20.1 into the game folder
#    3. downloads the modpack (mods, configs, shaderpacks)
#    4. creates an "al Shabab" launcher profile with the right amount of RAM
#
#  Safe to re-run any time: that is also how you update.
# ============================================================
[CmdletBinding()]
param(
    [string]$GameDir = "$env:APPDATA\.minecraft"
)

$ErrorActionPreference = "Stop"
$ProgressPreference    = "SilentlyContinue"   # makes Invoke-WebRequest far faster

$PackUrl      = "https://36a5.github.io/Modpacks/pack.toml"
$McVersion    = "1.20.1"
$ForgeVersion = "47.4.18"
$ForgeId      = "$McVersion-forge-$ForgeVersion"
$RuntimeDir   = "$env:LOCALAPPDATA\al-shabab\jre17"

function Say  ($m) { Write-Host "[al-shabab] $m" -ForegroundColor Cyan }
function Good ($m) { Write-Host "[al-shabab] $m" -ForegroundColor Green }
function Bad  ($m) { Write-Host "[al-shabab] $m" -ForegroundColor Red }

Write-Host ""
Write-Host "  al Shabab - Minecraft modpack installer" -ForegroundColor White
Write-Host "  ---------------------------------------" -ForegroundColor DarkGray
Write-Host ""

# ── 1. Java 17 ──────────────────────────────────────────────────────────────
function Get-Java17 {
    # Prefer our private runtime, then whatever is on PATH.
    $candidates = @()
    if (Test-Path "$RuntimeDir\bin\java.exe") { $candidates += "$RuntimeDir\bin\java.exe" }
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd) { $candidates += $cmd.Source }

    foreach ($java in $candidates) {
        # `java -version` writes to stderr. Probing it through cmd keeps PowerShell from
        # turning that stderr into a terminating error under $ErrorActionPreference = Stop.
        # cmd returns one string per line; -match against an array filters rather than
        # capturing, so join first or $Matches is never populated.
        $out = (cmd /c "`"$java`" -version 2>&1") -join "`n"
        $m = [regex]::Match($out, 'version "(\d+)')
        if ($m.Success -and [int]$m.Groups[1].Value -ge 17) { return $java }
    }
    return $null
}

$Java = Get-Java17
if ($Java) {
    Good "Found Java: $Java"
} else {
    Say "No Java 17 found. Downloading a private copy (about 45 MB, no admin rights needed)..."
    New-Item -ItemType Directory -Force (Split-Path $RuntimeDir) | Out-Null
    $zip = "$env:TEMP\al-shabab-jre17.zip"
    $url = "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jre/hotspot/normal/eclipse"
    Invoke-WebRequest $url -OutFile $zip

    if (Test-Path $RuntimeDir) { Remove-Item -Recurse -Force $RuntimeDir }
    $tmp = "$env:TEMP\al-shabab-jre-extract"
    if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
    Expand-Archive $zip -DestinationPath $tmp
    # the zip contains a single jdk-17.x.x-jre\ folder; hoist it
    $inner = Get-ChildItem $tmp -Directory | Select-Object -First 1
    Move-Item $inner.FullName $RuntimeDir
    Remove-Item -Recurse -Force $tmp, $zip -ErrorAction SilentlyContinue

    $Java = Get-Java17
    if (-not $Java) { Bad "Java install failed. Install Java 17 from https://adoptium.net and re-run."; exit 1 }
    Good "Java ready: $Java"
}

# ── 2. Game directory ───────────────────────────────────────────────────────
if (-not (Test-Path $GameDir)) {
    Say "Creating game folder: $GameDir"
    New-Item -ItemType Directory -Force $GameDir | Out-Null
}
# Forge's installer refuses to run without this file.
$profilesJson = Join-Path $GameDir "launcher_profiles.json"
if (-not (Test-Path $profilesJson)) {
    Say "Creating a launcher profile file (first-time setup)"
    '{"profiles":{},"selectedProfile":"","clientToken":"","authenticationDatabase":{}}' |
        Set-Content $profilesJson -Encoding utf8
}

# ── 3. Forge ────────────────────────────────────────────────────────────────
if (Test-Path (Join-Path $GameDir "versions\$ForgeId")) {
    Good "Forge $ForgeVersion already installed"
} else {
    Say "Installing Forge $ForgeVersion (this takes a minute)..."
    $installer = "$env:TEMP\forge-$McVersion-$ForgeVersion-installer.jar"
    $forgeUrl  = "https://maven.minecraftforge.net/net/minecraftforge/forge/$McVersion-$ForgeVersion/forge-$McVersion-$ForgeVersion-installer.jar"
    Invoke-WebRequest $forgeUrl -OutFile $installer

    & $Java -jar $installer --installClient $GameDir | Out-Null
    if (-not (Test-Path (Join-Path $GameDir "versions\$ForgeId"))) {
        Bad "Forge installation failed. Send this window to the server admin."
        exit 1
    }
    Remove-Item $installer, "$installer.log" -ErrorAction SilentlyContinue
    Good "Forge installed"
}

# ── 4. The modpack ──────────────────────────────────────────────────────────
Say "Downloading the modpack. First time takes 5-15 minutes - leave this window open."
Push-Location $GameDir
try {
    $bootstrap = "packwiz-installer-bootstrap.jar"
    if (-not (Test-Path $bootstrap)) {
        Invoke-WebRequest "https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest/download/packwiz-installer-bootstrap.jar" -OutFile $bootstrap
    }
    & $Java -jar $bootstrap -g -s client $PackUrl
    if ($LASTEXITCODE -ne 0) {
        Bad "The modpack download failed. Check your internet and re-run this installer."
        exit 1
    }
} finally { Pop-Location }
$modCount = (Get-ChildItem (Join-Path $GameDir "mods") -Filter *.jar -ErrorAction SilentlyContinue).Count
Good "Modpack installed ($modCount mods)"

# ── 5. Launcher profile with sane memory ────────────────────────────────────
$totalGb = [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB)
$allocGb = if ($totalGb -ge 16) { 8 } elseif ($totalGb -ge 12) { 6 } else { 4 }
if ($allocGb -lt 6) {
    Write-Host ""
    Bad "Warning: this PC has ${totalGb} GB of RAM. The pack wants 6-8 GB and may run poorly."
    Write-Host ""
}

try {
    $json = Get-Content $profilesJson -Raw | ConvertFrom-Json
    if (-not $json.profiles) { $json | Add-Member -NotePropertyName profiles -NotePropertyValue ([pscustomobject]@{}) -Force }

    $javaArgs = "-Xmx${allocGb}G -Xms2G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+DisableExplicitGC"
    $profile  = [pscustomobject]@{
        name          = "al Shabab"
        type          = "custom"
        created       = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
        lastVersionId = $ForgeId
        javaArgs      = $javaArgs
        gameDir       = $GameDir
    }
    $json.profiles | Add-Member -NotePropertyName "al-shabab" -NotePropertyValue $profile -Force
    $json | ConvertTo-Json -Depth 10 | Set-Content $profilesJson -Encoding utf8
    Good "Launcher profile 'al Shabab' created with ${allocGb} GB RAM"
} catch {
    Say "Could not write the launcher profile automatically - pick Forge $McVersion in your launcher and set RAM to ${allocGb} GB."
}

# ── Done ────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "  ============================================" -ForegroundColor Green
Write-Host "   Done. You are ready to play." -ForegroundColor Green
Write-Host "  ============================================" -ForegroundColor Green
Write-Host ""
Write-Host "   1. Open your Minecraft launcher"
Write-Host "   2. Choose the profile:  " -NoNewline; Write-Host "al Shabab" -ForegroundColor White
Write-Host "      (TLauncher: pick version '$ForgeId' and set RAM to ${allocGb} GB)"
Write-Host "   3. Press Play. The FIRST launch takes 3-8 minutes - it is not frozen."
Write-Host "   4. Multiplayer -> Add Server -> ask the admin for the address"
Write-Host "   5. On your first join, type in chat:"
Write-Host "        /trigger register set yourPassword" -ForegroundColor White
Write-Host ""
Write-Host "   To update later: just run this installer again."
Write-Host ""
