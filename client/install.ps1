# ============================================================
#  al Shabab - one-click installer / updater
#
#  Asks which launcher you use, then does everything for it:
#    1. finds Java 17, or downloads a portable copy (no admin rights)
#    2. creates or updates an "al-shabab" profile/instance in that launcher
#    3. installs Forge 1.20.1 where that launcher expects it
#    4. downloads the modpack (mods, configs, shaderpacks)
#
#  Safe to re-run any time: that is also how you update.
# ============================================================
[CmdletBinding()]
param(
    [string]$Pack,
    [ValidateSet("curseforge", "modrinth", "tlauncher", "vanilla")]
    [string]$Launcher,
    [string]$GameDir
)

$ErrorActionPreference = "Stop"
$ProgressPreference    = "SilentlyContinue"   # makes Invoke-WebRequest far faster

$McVersion    = "1.20.1"
$ForgeVersion = "47.4.18"
$ForgeId      = "$McVersion-forge-$ForgeVersion"
$RuntimeDir   = "$env:LOCALAPPDATA\al-shabab\jre17"   # Java is shared across packs (same MC version)

# Every pack MUST have a unique Slug: it becomes the instance/profile/gameDir name in
# every launcher, so two packs never share one mods\ folder and clobber each other.
$Packs = [ordered]@{
    "1" = @{ Slug = "al-shabab";   Display = "al Shabab"; Url = "https://36a5.github.io/Modpacks/pack/pack.toml" }
    "2" = @{ Slug = "al-shabab-2"; Display = "Shabab 2";  Url = "https://36a5.github.io/Modpacks/pack-two/pack.toml" }
}
# $PackUrl / $InstanceName / $DisplayName are set once the pack is chosen (section 0).

function Say  ($m) { Write-Host "[al-shabab] $m" -ForegroundColor Cyan }
function Good ($m) { Write-Host "[al-shabab] $m" -ForegroundColor Green }
function Bad  ($m) { Write-Host "[al-shabab] $m" -ForegroundColor Red }
function Warn ($m) { Write-Host "[al-shabab] $m" -ForegroundColor Yellow }

# The Minecraft Launcher, CurseForge and Modrinth all silently discard a JSON file
# they cannot parse, and none of them can parse a UTF-8 BOM. PowerShell 5.1's
# `Set-Content -Encoding utf8` writes one. Always write the bytes ourselves.
function Write-JsonFile([string]$Path, $Object) {
    $json = if ($Object -is [string]) { $Object } else { $Object | ConvertTo-Json -Depth 100 }
    [IO.File]::WriteAllText($Path, $json, (New-Object System.Text.UTF8Encoding($false)))
}

Write-Host ""
Write-Host "  Minecraft modpack installer" -ForegroundColor White
Write-Host "  ---------------------------" -ForegroundColor DarkGray
Write-Host ""

# ── 0. Which pack? ──────────────────────────────────────────────────────────
if ($Pack) {
    $sel = $Packs.Values | Where-Object { $_.Slug -eq $Pack } | Select-Object -First 1
    if (-not $sel) {
        $valid = ($Packs.Values | ForEach-Object { $_.Slug }) -join ", "
        Bad "Unknown pack '$Pack'. Valid: $valid"; exit 1
    }
} elseif ($Packs.Count -eq 1) {
    $sel = @($Packs.Values)[0]
} else {
    Write-Host "  Which pack do you want to install?" -ForegroundColor White
    Write-Host ""
    foreach ($k in $Packs.Keys) { "    {0}) {1}" -f $k, $Packs[$k].Display | Write-Host }
    Write-Host ""
    do { $pchoice = Read-Host "  Enter a number (1-$($Packs.Count))" } until ($Packs.Contains($pchoice))
    $sel = $Packs[$pchoice]
}
$PackUrl      = $sel.Url
$InstanceName = $sel.Slug      # profile / instance / gameDir name in every launcher
$DisplayName  = $sel.Display
Good "Pack: $DisplayName"

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

# How much RAM to give the game. Needed before we create an instance, because
# CurseForge and the Minecraft Launcher both store it on the profile itself.
$totalGb = [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB)
$allocGb = if ($totalGb -ge 16) { 8 } elseif ($totalGb -ge 12) { 6 } else { 4 }

# One definition, used by both the CurseForge instance and the Minecraft Launcher profile.
#
# -Xms equal to -Xmx: a heap that grows 2G -> 8G resizes repeatedly under load, and every resize is
# a full pause. Reserve it all up front.
#
# The G1 block is Aikar's: G1's defaults assume a small young generation, which is exactly wrong for
# Minecraft, where almost every allocation is short-lived garbage. A large young gen plus an early
# IHOP means G1 collects concurrently and rarely has to stop the world.
$jvmArgs = "-Xms${allocGb}G -Xmx${allocGb}G " +
           "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=50 " +
           "-XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch " +
           "-XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M " +
           "-XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 " +
           "-XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 " +
           "-XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 " +
           "-XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1"

if ($allocGb -lt 6) {
    Write-Host ""
    Warn "This PC has ${totalGb} GB of RAM. The pack wants 6-8 GB and may run poorly."
    Write-Host ""
}

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

# ── 3. Helpers: vanilla version, Forge, launcher instances ──────────────────

function Assert-NotRunning([string[]]$Names, [string]$AppName) {
    foreach ($n in $Names) {
        if (Get-Process -Name $n -ErrorAction SilentlyContinue) {
            Bad "$AppName is running. It rewrites its own instance list when it closes,"
            Bad "which would erase the profile this installer creates. Close it and re-run."
            exit 1
        }
    }
}

# Forge's version JSON is `"inheritsFrom": "1.20.1"` with no libraries and no assetIndex.
# The Forge installer downloads the vanilla client JAR but never writes 1.20.1.json, so a
# launcher that has never run vanilla 1.20.1 cannot resolve the parent: it reports
# "No libraries?!", falls back to the pre-1.7 default asset index ("legacy"), and fetches it
# from a Mojang CDN that was retired years ago. That 404 surfaces as
# "Unable to prepare assets for download". Install the parent ourselves.
function Install-VanillaVersion([string]$Root) {
    $dir  = Join-Path $Root "versions\$McVersion"
    $json = Join-Path $dir "$McVersion.json"
    $jar  = Join-Path $dir "$McVersion.jar"

    if ((Test-Path $json) -and (Test-Path $jar)) {
        Good "Minecraft $McVersion is already installed"
        return
    }

    Say "Installing the base Minecraft $McVersion files..."
    New-Item -ItemType Directory -Force $dir | Out-Null

    $manifest = Invoke-RestMethod "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    $entry    = $manifest.versions | Where-Object { $_.id -eq $McVersion } | Select-Object -First 1
    if (-not $entry) { throw "Mojang's version manifest has no $McVersion" }

    Invoke-WebRequest $entry.url -OutFile $json
    $sha = (Get-FileHash $json -Algorithm SHA1).Hash
    if ($sha -ne $entry.sha1.ToUpper()) { throw "$McVersion.json failed its checksum" }

    if (-not (Test-Path $jar)) {
        $client = (Get-Content $json -Raw | ConvertFrom-Json).downloads.client
        Invoke-WebRequest $client.url -OutFile $jar
        $sha = (Get-FileHash $jar -Algorithm SHA1).Hash
        if ($sha -ne $client.sha1.ToUpper()) { throw "$McVersion.jar failed its checksum" }
    }
    Good "Minecraft $McVersion installed"
}

function Install-Forge([string]$Root) {
    # Forge's installer refuses to run without this file.
    $profilesJson = Join-Path $Root "launcher_profiles.json"
    if (-not (Test-Path $profilesJson)) {
        Say "Creating a launcher profile file (first-time setup)"
        Write-JsonFile $profilesJson '{"profiles":{},"selectedProfile":"","clientToken":"","authenticationDatabase":{}}'
    }

    if (Test-Path (Join-Path $Root "versions\$ForgeId")) {
        Good "Forge $ForgeVersion already installed"
        return
    }
    Say "Installing Forge $ForgeVersion (this takes a minute)..."
    $installer = "$env:TEMP\forge-$McVersion-$ForgeVersion-installer.jar"
    Invoke-WebRequest "https://maven.minecraftforge.net/net/minecraftforge/forge/$McVersion-$ForgeVersion/forge-$McVersion-$ForgeVersion-installer.jar" -OutFile $installer

    # Forge writes installer.log into the working directory, so run it from $Root.
    # Launched as administrator, the inherited directory is C:\Windows\System32 and
    # the installer dies with "installer.log (Access is denied)".
    Push-Location $Root
    try {
        & $Java -jar $installer --installClient $Root | Out-Null
    } finally { Pop-Location }

    if (-not (Test-Path (Join-Path $Root "versions\$ForgeId"))) {
        Bad "Forge installation failed. Send this window to the server admin."
        exit 1
    }
    Remove-Item $installer, (Join-Path $Root "installer.log") -ErrorAction SilentlyContinue
    Good "Forge installed"
}

# ---- CurseForge -------------------------------------------------------------
# The CurseForge App rebuilds its instance list by scanning the Instances folder for
# minecraftinstance.json, so writing that file is enough to create an instance. The
# baseModLoader blob it wants (including the embedded Forge version JSON) is served by
# CurseForge's own public modloader endpoint - no API key.
function Install-CurseForgeInstance([int]$AllocGb) {
    # Newer CurseForge is a standalone Electron app; older builds run inside Overwolf.
    Assert-NotRunning @("CurseForge*", "Curse.Agent*", "Overwolf*") "The CurseForge App"

    $root = "$env:USERPROFILE\curseforge\minecraft\Instances"
    if (-not (Test-Path $root)) {
        Bad "The CurseForge App is not installed (no $root)."
        Bad "Install it from https://www.curseforge.com/download/app, open it once, then re-run this."
        exit 1
    }

    $dir  = Join-Path $root $InstanceName
    $file = Join-Path $dir "minecraftinstance.json"
    $new  = -not (Test-Path $file)
    New-Item -ItemType Directory -Force $dir | Out-Null

    Say "Fetching the Forge $ForgeVersion definition from CurseForge..."
    $modLoader = (Invoke-RestMethod "https://api.curseforge.com/v1/minecraft/modloader/forge-$ForgeVersion").data

    if ($new) {
        $inst = [ordered]@{
            guid                        = [guid]::NewGuid().ToString()
            installedAddons             = @()
            installedGamePrerequisites  = @()
            modpackOverrides            = @()
            cachedScans                 = @()
            playedCount                 = 0
            manifest                    = $null
            installedModpack            = $null
            projectID                   = 0
            fileID                      = 0
            javaArgsOverride            = $null
            profileImagePath            = $null
            preferenceReleaseType       = 1
        }
    } else {
        # Keep what CurseForge owns (its mod fingerprints, play stats, its GUID).
        $inst = [ordered]@{}
        (Get-Content $file -Raw | ConvertFrom-Json).PSObject.Properties | ForEach-Object { $inst[$_.Name] = $_.Value }
    }

    $inst["name"]             = $InstanceName
    $inst["gameVersion"]      = $McVersion
    $inst["baseModLoader"]    = $modLoader
    $inst["installPath"]      = "$dir\"
    $inst["gameTypeID"]       = 432
    $inst["isValid"]          = $true
    $inst["isEnabled"]        = $true
    $inst["isUnlocked"]       = $true
    $inst["isVanilla"]        = $false
    $inst["isMemoryOverride"] = $true
    $inst["allocatedMemory"]  = $AllocGb * 1024
    # CurseForge players were getting stock flags: a new instance is written with a null
    # javaArgsOverride and an existing one keeps whatever was already there. $script: because
    # $jvmArgs is script-scope and this is a function - unqualified, it would resolve to nothing
    # here and silently write an empty override.
    $inst["javaArgsOverride"]  = $script:jvmArgs
    if ($new) { $inst["installDate"] = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffffffZ") }

    Write-JsonFile $file ([pscustomobject]$inst)
    if ($new) { Good "CurseForge instance '$InstanceName' created" } else { Good "CurseForge instance '$InstanceName' updated" }
    return $dir
}

# ---- Modrinth ---------------------------------------------------------------
# The Modrinth App keeps no per-instance JSON: instance metadata lives in a SQLite
# database at %APPDATA%\ModrinthApp\app.db. Windows ships winsqlite3.dll, so we can
# talk to it without downloading anything.
$script:SqliteLoaded = $false
function Import-Sqlite {
    if ($script:SqliteLoaded) { return }
    Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;

public static class WinSqlite {
    const string DLL = "winsqlite3.dll";
    [DllImport(DLL, EntryPoint="sqlite3_open_v2",     CallingConvention=CallingConvention.Cdecl)] static extern int Open(byte[] f, out IntPtr db, int flags, IntPtr vfs);
    [DllImport(DLL, EntryPoint="sqlite3_close_v2",    CallingConvention=CallingConvention.Cdecl)] static extern int Close(IntPtr db);
    [DllImport(DLL, EntryPoint="sqlite3_prepare_v2",  CallingConvention=CallingConvention.Cdecl)] static extern int Prepare(IntPtr db, byte[] sql, int n, out IntPtr stmt, IntPtr tail);
    [DllImport(DLL, EntryPoint="sqlite3_step",        CallingConvention=CallingConvention.Cdecl)] static extern int Step(IntPtr stmt);
    [DllImport(DLL, EntryPoint="sqlite3_column_text", CallingConvention=CallingConvention.Cdecl)] static extern IntPtr ColText(IntPtr stmt, int i);
    [DllImport(DLL, EntryPoint="sqlite3_column_count",CallingConvention=CallingConvention.Cdecl)] static extern int ColCount(IntPtr stmt);
    [DllImport(DLL, EntryPoint="sqlite3_finalize",    CallingConvention=CallingConvention.Cdecl)] static extern int Fin(IntPtr stmt);
    [DllImport(DLL, EntryPoint="sqlite3_errmsg",      CallingConvention=CallingConvention.Cdecl)] static extern IntPtr ErrMsg(IntPtr db);

    static byte[] U8(string s) { return Encoding.UTF8.GetBytes(s + "\0"); }
    static string FromU8(IntPtr p) {
        if (p == IntPtr.Zero) return null;
        int len = 0; while (Marshal.ReadByte(p, len) != 0) len++;
        byte[] b = new byte[len]; Marshal.Copy(p, b, 0, len);
        return Encoding.UTF8.GetString(b);
    }

    public static List<string[]> Run(string dbPath, string sql) {
        IntPtr db;
        int rc = Open(U8(dbPath), out db, 2 /* SQLITE_OPEN_READWRITE */, IntPtr.Zero);
        if (rc != 0) { Close(db); throw new Exception("cannot open app.db (rc=" + rc + ")"); }
        try {
            IntPtr stmt;
            rc = Prepare(db, U8(sql), -1, out stmt, IntPtr.Zero);
            if (rc != 0) throw new Exception("app.db rejected a statement: " + FromU8(ErrMsg(db)));
            var rows = new List<string[]>();
            try {
                int cols = ColCount(stmt);
                while (true) {
                    rc = Step(stmt);
                    if (rc == 100) {            // SQLITE_ROW
                        var row = new string[cols];
                        for (int i = 0; i < cols; i++) row[i] = FromU8(ColText(stmt, i));
                        rows.Add(row);
                    } else if (rc == 101) break; // SQLITE_DONE
                    else throw new Exception("app.db write failed: " + FromU8(ErrMsg(db)));
                }
            } finally { Fin(stmt); }
            return rows;
        } finally { Close(db); }
    }
}
'@
    $script:SqliteLoaded = $true
}

function Install-ModrinthInstance {
    Assert-NotRunning @("Modrinth App", "ModrinthApp") "The Modrinth App"

    $appDir = "$env:APPDATA\ModrinthApp"
    $db     = Join-Path $appDir "app.db"
    if (-not (Test-Path $db)) {
        Bad "The Modrinth App is not installed (no $db)."
        Bad "Install it from https://modrinth.com/app, open it once, then re-run this."
        exit 1
    }

    Import-Sqlite

    # Refuse to touch a schema we do not recognise rather than corrupt someone's launcher.
    # Call [WinSqlite]::Run directly: piping or invoking it through a scriptblock would
    # unroll each row (a string[]) into separate strings and lose the row boundaries.
    $wanted = [ordered]@{
        instances             = @("id","path","applied_content_set_id","install_stage","launcher_feature_version","update_channel","name","icon_path","created","modified","last_played","submitted_time_played","recent_time_played")
        instance_content_sets = @("id","instance_id","name","source_kind","status","game_version","protocol_version","loader","loader_version","created","modified")
    }
    foreach ($table in $wanted.Keys) {
        $have    = @([WinSqlite]::Run($db, "SELECT name FROM pragma_table_info('$table')") | ForEach-Object { $_[0] })
        $missing = $wanted[$table] | Where-Object { $have -notcontains $_ }
        if ($missing) { throw "app.db has an unfamiliar '$table' table (missing: $($missing -join ', ')). The Modrinth App has changed its database format." }
    }

    Backup-File $db
    $now  = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $name = $InstanceName -replace "'", "''"
    $rows = [WinSqlite]::Run($db, "SELECT id, path, applied_content_set_id FROM instances WHERE name = '$name'")

    if ($rows.Count -gt 0) {
        $instanceId = $rows[0][0]
        $path       = $rows[0][1]
        $setId      = $rows[0][2]
        if ($setId) {
            [WinSqlite]::Run($db, "UPDATE instance_content_sets SET game_version = '$McVersion', loader = 'forge', loader_version = '$ForgeVersion', modified = $now WHERE id = '$($setId -replace "'","''")'") | Out-Null
        } else {
            $setId = "content-set:" + [guid]::NewGuid()
            [WinSqlite]::Run($db, "INSERT INTO instance_content_sets (id,instance_id,name,source_kind,status,game_version,protocol_version,loader,loader_version,created,modified) VALUES ('$setId','$instanceId','Default','local','available','$McVersion',NULL,'forge','$ForgeVersion',$now,$now)") | Out-Null
            [WinSqlite]::Run($db, "UPDATE instances SET applied_content_set_id = '$setId' WHERE id = '$instanceId'") | Out-Null
        }
        [WinSqlite]::Run($db, "UPDATE instances SET modified = $now WHERE id = '$instanceId'") | Out-Null
        Good "Modrinth instance '$InstanceName' updated"
    } else {
        $instanceId = "local:" + [guid]::NewGuid()
        $setId      = "content-set:" + [guid]::NewGuid()
        $path       = $InstanceName
        [WinSqlite]::Run($db, "INSERT INTO instances (id,path,applied_content_set_id,install_stage,launcher_feature_version,update_channel,name,icon_path,created,modified,last_played,submitted_time_played,recent_time_played) VALUES ('$instanceId','$path','$setId','installed','migrated_launch_hooks','release','$name',NULL,$now,$now,NULL,0,0)") | Out-Null
        [WinSqlite]::Run($db, "INSERT INTO instance_content_sets (id,instance_id,name,source_kind,status,game_version,protocol_version,loader,loader_version,created,modified) VALUES ('$setId','$instanceId','Default','local','available','$McVersion',NULL,'forge','$ForgeVersion',$now,$now)") | Out-Null
        Good "Modrinth instance '$InstanceName' created"
    }

    $dir = Join-Path (Join-Path $appDir "profiles") $path
    New-Item -ItemType Directory -Force $dir | Out-Null
    return $dir
}

function Backup-File([string]$Path) {
    $bak = "$Path.al-shabab-backup"
    if (-not (Test-Path $bak)) { Copy-Item $Path $bak }
}

# Pick an instance by hand. Used when auto-creation is not possible.
function Select-InstanceFolder([string]$root, [string]$appName) {
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

# ── 4. Put Forge and the pack where this launcher expects them ──────────────
# $LauncherRoot is where versions\ and libraries\ live; $GameDir is where mods\,
# config\ and saves\ live. The Minecraft Launcher always reads versions\ out of
# .minecraft no matter what a profile's gameDir says, so for it the two differ.
$writeProfile = $false
$LauncherRoot = $null

if ($GameDir) {
    Say "Using the folder you passed: $GameDir"
    $LauncherRoot = $GameDir
} else {
    switch ($Launcher) {
        "curseforge" {
            # CurseForge installs Forge itself from the instance's baseModLoader.
            $GameDir = Install-CurseForgeInstance $allocGb
        }
        "modrinth" {
            # Modrinth installs Forge itself from the instance's content set.
            try {
                $GameDir = Install-ModrinthInstance
            } catch {
                Warn "Could not create the Modrinth instance automatically: $($_.Exception.Message)"
                Warn "Open Modrinth -> Create new instance -> Minecraft $McVersion -> Forge, then pick it below."
                $GameDir = Select-InstanceFolder "$env:APPDATA\ModrinthApp\profiles" "Modrinth"
            }
        }
        "tlauncher" {
            # TLauncher uses the standard .minecraft folder and reads versions\ directly,
            # so installing Forge there is enough - it appears in the version dropdown.
            $GameDir      = "$env:APPDATA\.minecraft"
            $LauncherRoot = $GameDir
        }
        "vanilla" {
            # Keep the pack out of .minecraft: TLauncher rewrites version JSONs and both
            # launchers would otherwise share one mods\ folder.
            $LauncherRoot = "$env:APPDATA\.minecraft"
            $GameDir      = "$env:APPDATA\$InstanceName"
            $writeProfile = $true
        }
    }
}
Good "Game folder: $GameDir"

if (-not (Test-Path $GameDir)) { New-Item -ItemType Directory -Force $GameDir | Out-Null }

if ($LauncherRoot) {
    if (-not (Test-Path $LauncherRoot)) { New-Item -ItemType Directory -Force $LauncherRoot | Out-Null }
    Install-VanillaVersion $LauncherRoot
    Install-Forge $LauncherRoot
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

# ── 6. Minecraft Launcher profile ───────────────────────────────────────────
if ($writeProfile) {
    if (Get-Process -Name "Minecraft*", "MinecraftLauncher*" -ErrorAction SilentlyContinue) {
        Warn "The Minecraft Launcher is running. It rewrites launcher_profiles.json when it closes,"
        Warn "which would erase the profile we are about to add. Close it, then re-run this installer."
    } else {
        $profilesJson = Join-Path $LauncherRoot "launcher_profiles.json"
        try {
            Backup-File $profilesJson
            $json = Get-Content $profilesJson -Raw | ConvertFrom-Json
            if (-not $json.PSObject.Properties.Name.Contains("profiles")) {
                $json | Add-Member -NotePropertyName profiles -NotePropertyValue ([pscustomobject]@{}) -Force
            }

            $existing = $json.profiles.PSObject.Properties[$InstanceName]
            $created  = if ($existing) { $existing.Value.created } else { (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ") }

            # NB: not $profile - that is a PowerShell automatic variable.
            $javaArgs   = $jvmArgs
            $newProfile = [pscustomobject]@{
                name          = $DisplayName
                type          = "custom"
                created       = $created
                lastUsed      = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
                lastVersionId = $ForgeId
                gameDir       = $GameDir
                javaArgs      = $javaArgs
            }
            $json.profiles | Add-Member -NotePropertyName $InstanceName -NotePropertyValue $newProfile -Force
            Write-JsonFile $profilesJson $json

            if ($existing) { Good "Launcher profile '$DisplayName' updated (${allocGb} GB RAM)" }
            else           { Good "Launcher profile '$DisplayName' created (${allocGb} GB RAM)" }
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
    "vanilla"    { Write-Host "   1. Open the Minecraft Launcher and choose the " -NoNewline; Write-Host $DisplayName -ForegroundColor White -NoNewline; Write-Host " profile" }
    "tlauncher"  { Write-Host "   1. Open TLauncher, pick version " -NoNewline; Write-Host "$ForgeId" -ForegroundColor White -NoNewline; Write-Host ", set RAM to ${allocGb} GB" }
    "curseforge" { Write-Host "   1. Open CurseForge and press Play on the " -NoNewline; Write-Host $InstanceName -ForegroundColor White -NoNewline; Write-Host " instance" }
    "modrinth"   { Write-Host "   1. Open the Modrinth App and press Play on the " -NoNewline; Write-Host $InstanceName -ForegroundColor White -NoNewline; Write-Host " instance" }
}
Write-Host "   2. Press Play. The FIRST launch takes 3-8 minutes - it is not frozen."
Write-Host "   3. Multiplayer -> Add Server -> paste the address from Discord #server-info"
Write-Host "   4. On your first join, type the join code from #server-info into chat."
Write-Host "      Then set your password:  " -NoNewline; Write-Host "/trigger register set yourPassword" -ForegroundColor White
Write-Host ""
Write-Host "   To update later: run this installer again."
Write-Host ""
