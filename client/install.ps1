# ============================================================
#  al Shabab - one-click installer / updater
#
#  Asks which launcher you use, then does everything for it:
#    1. finds Java 17, or downloads a portable copy (no admin rights)
#    2. installs Forge 1.20.1 where that launcher expects it
#    3. downloads the modpack (mods, configs, shaderpacks)
#    4. creates the launcher profile, where the launcher supports it
#
#  Safe to re-run any time: that is also how you update.
# ============================================================
[CmdletBinding()]
param(
    [ValidateSet("curseforge", "modrinth", "tlauncher", "vanilla")]
    [string]$Launcher,
    [string]$GameDir
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
function Warn ($m) { Write-Host "[al-shabab] $m" -ForegroundColor Yellow }

Write-Host ""
Write-Host "  al Shabab - Minecraft modpack installer" -ForegroundColor White
Write-Host "  ---------------------------------------" -ForegroundColor DarkGray
Write-Host ""

# ── 1. Which launcher? ──────────────────────────────────────────────────────
if (-not $Launcher) {
    Write-Host "  Which launcher do you play on?" -ForegroundColor White
    Write-Host ""
    Write-Host "    1) CurseForge App"
    Write-Host "    2) Modrinth App"
    Write-Host "    3) TLauncher            (no Minecraft account needed)"
    Write-Host "    4) Minecraft Launcher   (the official one)"
    Write-Host ""
    do {
        $choice = Read-Host "  Enter a number (1-4)"
    } until ($choice -match '^[1-4]$')

    $Launcher = @{ "1" = "curseforge"; "2" = "modrinth"; "3" = "tlauncher"; "4" = "vanilla" }[$choice]
}
Good "Launcher: $Launcher"

# ── 2. Java 17 ──────────────────────────────────────────────────────────────
function Get-Java17 {
    $candidates = @()
    if (Test-Path "$RuntimeDir\bin\java.exe") { $candidates += "$RuntimeDir\bin\java.exe" }
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd) { $candidates += $cmd.Source }

    foreach ($java in $candidates) {
        # `java -version` writes to stderr. Probing through cmd stops PowerShell from
        # turning that stderr into a terminating error under $ErrorActionPreference=Stop.
        # cmd returns one string per line; -match on an array filters instead of capturing,
        # so join first or $Matches is never populated.
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
    Invoke-WebRequest "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jre/hotspot/normal/eclipse" -OutFile $zip

    if (Test-Path $RuntimeDir) { Remove-Item -Recurse -Force $RuntimeDir }
    $tmp = "$env:TEMP\al-shabab-jre-extract"
    if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
    Expand-Archive $zip -DestinationPath $tmp
    Move-Item (Get-ChildItem $tmp -Directory | Select-Object -First 1).FullName $RuntimeDir
    Remove-Item -Recurse -Force $tmp, $zip -ErrorAction SilentlyContinue

    $Java = Get-Java17
    if (-not $Java) { Bad "Java install failed. Install Java 17 from https://adoptium.net and re-run."; exit 1 }
    Good "Java ready: $Java"
}

# ── 3. Where does this launcher keep its game folder? ───────────────────────
function Find-InstanceFolder([string]$root, [string]$appName) {
    if (-not (Test-Path $root)) {
        Bad "$appName is not installed (no $root)."
        Bad "Install it, create a Forge $McVersion instance, then run this again."
        exit 1
    }
    $instances = Get-ChildItem $root -Directory -ErrorAction SilentlyContinue
    if (-not $instances) {
        Bad "No instances found under $root."
        Bad "In $appName, create an instance with Minecraft $McVersion and mod loader Forge, then re-run."
        exit 1
    }

    Write-Host ""
    Write-Host "  Which $appName instance is al Shabab?" -ForegroundColor White
    Write-Host ""
    for ($i = 0; $i -lt $instances.Count; $i++) { "    {0}) {1}" -f ($i + 1), $instances[$i].Name | Write-Host }
    Write-Host ""
    do { $pick = Read-Host "  Enter a number (1-$($instances.Count))" }
    until ($pick -match '^\d+$' -and [int]$pick -ge 1 -and [int]$pick -le $instances.Count)

    return $instances[[int]$pick - 1].FullName
}

# The official launcher is the only one whose profile list we can safely write.
# Decided from $Launcher, not from which branch below runs — passing -GameDir used to
# skip the switch entirely and silently disable the profile write.
$writeProfile = ($Launcher -eq "vanilla")

if ($GameDir) {
    Say "Using the folder you passed: $GameDir"
} else {
    switch ($Launcher) {
        "curseforge" {
            Warn "The CurseForge App manages its own instance list, so this installer cannot create one."
            Warn "If you have not already: open CurseForge -> Create Custom Profile -> Minecraft $McVersion -> Forge."
            Write-Host ""
            $GameDir = Find-InstanceFolder "$env:USERPROFILE\curseforge\minecraft\Instances" "CurseForge"
        }
        "modrinth" {
            Warn "The Modrinth App manages its own instance list, so this installer cannot create one."
            Warn "If you have not already: open Modrinth -> Create new instance -> Minecraft $McVersion -> Forge."
            Write-Host ""
            $GameDir = Find-InstanceFolder "$env:APPDATA\ModrinthApp\profiles" "Modrinth"
        }
        "tlauncher" {
            # TLauncher uses the standard .minecraft folder and reads versions\ directly,
            # so installing Forge there is enough - it appears in the version dropdown.
            $GameDir = "$env:APPDATA\.minecraft"
        }
        "vanilla" {
            $GameDir = "$env:APPDATA\.minecraft"
        }
    }
}
Good "Game folder: $GameDir"

if (-not (Test-Path $GameDir)) { New-Item -ItemType Directory -Force $GameDir | Out-Null }

# Forge's installer refuses to run without this file.
$profilesJson = Join-Path $GameDir "launcher_profiles.json"
if (-not (Test-Path $profilesJson)) {
    Say "Creating a launcher profile file (first-time setup)"
    [IO.File]::WriteAllText(
        $profilesJson,
        '{"profiles":{},"selectedProfile":"","clientToken":"","authenticationDatabase":{}}',
        (New-Object System.Text.UTF8Encoding($false)))
}

# ── 4. Forge ────────────────────────────────────────────────────────────────
if (Test-Path (Join-Path $GameDir "versions\$ForgeId")) {
    Good "Forge $ForgeVersion already installed"
} else {
    Say "Installing Forge $ForgeVersion (this takes a minute)..."
    $installer = "$env:TEMP\forge-$McVersion-$ForgeVersion-installer.jar"
    Invoke-WebRequest "https://maven.minecraftforge.net/net/minecraftforge/forge/$McVersion-$ForgeVersion/forge-$McVersion-$ForgeVersion-installer.jar" -OutFile $installer

    & $Java -jar $installer --installClient $GameDir | Out-Null
    if (-not (Test-Path (Join-Path $GameDir "versions\$ForgeId"))) {
        Bad "Forge installation failed. Send this window to the server admin."
        exit 1
    }
    Remove-Item $installer, "$installer.log" -ErrorAction SilentlyContinue
    Good "Forge installed"
}

# ── 5. The modpack ──────────────────────────────────────────────────────────
Say "Downloading the modpack. First time takes 5-15 minutes - leave this window open."
Push-Location $GameDir
try {
    if (-not (Test-Path "packwiz-installer-bootstrap.jar")) {
        Invoke-WebRequest "https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest/download/packwiz-installer-bootstrap.jar" -OutFile "packwiz-installer-bootstrap.jar"
    }
    & $Java -jar packwiz-installer-bootstrap.jar -g -s client $PackUrl
    if ($LASTEXITCODE -ne 0) {
        Bad "The modpack download failed. Check your internet and re-run this installer."
        exit 1
    }
} finally { Pop-Location }
$modCount = (Get-ChildItem (Join-Path $GameDir "mods") -Filter *.jar -ErrorAction SilentlyContinue).Count
Good "Modpack installed ($modCount mods)"

# ── 6. Launcher profile (official launcher only) ────────────────────────────
$totalGb = [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB)
$allocGb = if ($totalGb -ge 16) { 8 } elseif ($totalGb -ge 12) { 6 } else { 4 }
if ($allocGb -lt 6) {
    Write-Host ""
    Warn "This PC has ${totalGb} GB of RAM. The pack wants 6-8 GB and may run poorly."
    Write-Host ""
}

if ($writeProfile) {
    if (Get-Process -Name "Minecraft*", "MinecraftLauncher*" -ErrorAction SilentlyContinue) {
        Warn "The Minecraft Launcher is running. It rewrites launcher_profiles.json when it closes,"
        Warn "which would erase the profile we are about to add. Close it, then re-run this installer."
    } else {
        try {
            $json = Get-Content $profilesJson -Raw | ConvertFrom-Json
            if (-not $json.PSObject.Properties.Name.Contains("profiles")) {
                $json | Add-Member -NotePropertyName profiles -NotePropertyValue ([pscustomobject]@{}) -Force
            }

            # NB: not $profile — that is a PowerShell automatic variable.
            $javaArgs   = "-Xmx${allocGb}G -Xms2G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+DisableExplicitGC"
            $newProfile = [pscustomobject]@{
                name          = "al Shabab"
                type          = "custom"
                created       = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
                lastUsed      = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
                lastVersionId = $ForgeId
                javaArgs      = $javaArgs
            }
            $json.profiles | Add-Member -NotePropertyName "al-shabab" -NotePropertyValue $newProfile -Force

            # The Minecraft Launcher silently discards launcher_profiles.json if it cannot
            # parse it, and it cannot parse a UTF-8 BOM. PowerShell 5.1's
            # `Set-Content -Encoding utf8` writes one. Write the bytes ourselves.
            [IO.File]::WriteAllText(
                $profilesJson,
                ($json | ConvertTo-Json -Depth 10),
                (New-Object System.Text.UTF8Encoding($false)))

            Good "Launcher profile 'al Shabab' created with ${allocGb} GB RAM"
        } catch {
            Warn "Could not write the launcher profile: $($_.Exception.Message)"
            Warn "Pick version '$ForgeId' manually in the launcher and set RAM to ${allocGb} GB."
        }
    }
}

# ── Done ────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "  ============================================" -ForegroundColor Green
Write-Host "   Done. You are ready to play." -ForegroundColor Green
Write-Host "  ============================================" -ForegroundColor Green
Write-Host ""
switch ($Launcher) {
    "vanilla"    { Write-Host "   1. Open the Minecraft Launcher and choose the " -NoNewline; Write-Host "al Shabab" -ForegroundColor White -NoNewline; Write-Host " profile" }
    "tlauncher"  { Write-Host "   1. Open TLauncher, pick version " -NoNewline; Write-Host "$ForgeId" -ForegroundColor White -NoNewline; Write-Host ", set RAM to ${allocGb} GB" }
    "curseforge" { Write-Host "   1. Open CurseForge and press Play on that instance (set RAM to ${allocGb} GB in its settings)" }
    "modrinth"   { Write-Host "   1. Open the Modrinth App and press Play on that instance (set RAM to ${allocGb} GB in Options -> Java)" }
}
Write-Host "   2. Press Play. The FIRST launch takes 3-8 minutes - it is not frozen."
Write-Host "   3. Multiplayer -> Add Server -> paste the address from Discord #server-info"
Write-Host "   4. On your first join, type the join code from #server-info into chat."
Write-Host "      Then set your password:  " -NoNewline; Write-Host "/trigger register set yourPassword" -ForegroundColor White
Write-Host ""
Write-Host "   To update later: run this installer again."
Write-Host ""
