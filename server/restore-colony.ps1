# ============================================================
#  al Shabab - restore one colony from a MineColonies backup
#
#  MineColonies writes a colonies-<timestamp>.zip into the world
#  folder on every server start. Those zips hold colonyN.dat -
#  the whole colony: buildings and their levels, citizens,
#  research, requests.
#
#  `/mc colony loadBackup <id>` does NOT read those zips. It
#  re-reads world/minecolonies/<dim>/colonyN.dat off disk. So
#  restoring means putting the old .dat back yourself, which is
#  what this does.
#
#  It refuses to run while the server is up: the server holds the
#  colony in memory and rewrites the .dat on save and on stop, so
#  a swap made now would simply be overwritten.
#
#  WARNING: this reverts the WHOLE colony to the snapshot -
#  every building, citizen, research and request. Blocks in the
#  world are NOT reverted, so structures stay standing; only the
#  colony's memory of them rolls back.
#
#  Usage:
#    .\restore-colony.ps1 -Colony 1 -Backup .\colony-backups-safe\colonies-2026-07-12_22.00.20.zip
#    .\restore-colony.ps1 -List
# ============================================================
[CmdletBinding()]
param(
    [int]$Colony,
    [string]$Backup,
    [string]$Dimension = "minecraft/overworld",
    [switch]$List
)

$ErrorActionPreference = "Stop"
$run    = Join-Path $PSScriptRoot "run"
$safe   = Join-Path $PSScriptRoot "colony-backups-safe"
$world  = Join-Path $run "world\minecolonies"

if ($List) {
    Write-Host "Backups in the world folder:" -ForegroundColor Cyan
    Get-ChildItem (Join-Path $world "colonies-*.zip") -ErrorAction SilentlyContinue |
        Sort-Object Name | ForEach-Object { Write-Host "  $($_.Name)" }
    Write-Host ""
    Write-Host "Preserved copies (safe from restarts and reset-world.ps1):" -ForegroundColor Cyan
    Get-ChildItem (Join-Path $safe "colonies-*.zip") -ErrorAction SilentlyContinue |
        Sort-Object Name | ForEach-Object { Write-Host "  $($_.Name)" }
    exit 0
}

if (-not $Colony -or -not $Backup) {
    Write-Host "Usage: .\restore-colony.ps1 -Colony <n> -Backup <path to colonies-*.zip>" -ForegroundColor Red
    Write-Host "       .\restore-colony.ps1 -List"
    exit 1
}

$live = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match "forge" }
if ($live) {
    Write-Host "The server is still running. Stop it first (type 'stop' in its console)." -ForegroundColor Red
    Write-Host "It keeps the colony in memory and rewrites colony$Colony.dat on save - a swap now would be lost."
    exit 1
}

if (-not (Test-Path $Backup)) { Write-Host "No such backup: $Backup" -ForegroundColor Red; exit 1 }

$target = Join-Path $world ($Dimension.Replace('/', '\')) | Join-Path -ChildPath "colony$Colony.dat"
if (-not (Test-Path $target)) { Write-Host "No live colony$Colony.dat at $target" -ForegroundColor Red; exit 1 }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $Backup))
try {
    # Zip entries are written with backslashes here, so match on the leaf name rather than the path.
    $entry = $zip.Entries | Where-Object { $_.FullName.Replace('\', '/') -like "*/colony$Colony.dat" } | Select-Object -First 1
    if (-not $entry) {
        Write-Host "That backup has no colony$Colony.dat. It holds:" -ForegroundColor Red
        $zip.Entries | ForEach-Object { Write-Host "  $($_.FullName)" }
        exit 1
    }

    $stamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
    Copy-Item $target "$target.before-restore-$stamp" -Force

    $out = [System.IO.File]::Create($target)
    try   { $entry.Open().CopyTo($out) }
    finally { $out.Dispose() }

    Write-Host ""
    Write-Host "  Restored colony$Colony from $(Split-Path $Backup -Leaf)" -ForegroundColor Green
    Write-Host "  ($($entry.FullName), $($entry.Length) bytes)"
    Write-Host "  previous file kept at: $target.before-restore-$stamp"
    Write-Host ""
    Write-Host "  Colony $Colony is now back to that snapshot - buildings, levels, citizens, research." -ForegroundColor Yellow
    Write-Host "  Anything done in it since is gone. Blocks in the world were not touched."
    Write-Host ""
    Write-Host "  Start the server again (.\start.ps1)." -ForegroundColor Green
}
finally {
    $zip.Dispose()
}
