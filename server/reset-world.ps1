# ============================================================
#  al Shabab - reset the server world
#
#  Deletes ONLY the world. Keeps the whitelist, ops, bans,
#  server.properties, player passwords, and all mods/configs.
#
#  Use after an update that changes worldgen or progression
#  (new structure mods, Scaling Health, Serene Seasons, etc).
#
#  Usage:  .\reset-world.ps1          (asks for confirmation)
#          .\reset-world.ps1 -Force   (no prompt)
# ============================================================
[CmdletBinding()]
param([switch]$Force)

$ErrorActionPreference = "Stop"
$run = Join-Path $PSScriptRoot "run"

if (-not (Test-Path $run)) { Write-Host "No server/run folder yet - nothing to reset."; exit 0 }

# A running server would recreate the world as we delete it.
$live = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match "forge" }
if ($live) {
    Write-Host "The server is still running. Stop it first (type 'stop' in its console)." -ForegroundColor Red
    exit 1
}

$worlds = Get-ChildItem $run -Directory | Where-Object { $_.Name -match '^world' }
if (-not $worlds) { Write-Host "No world folder found - already fresh."; exit 0 }

Write-Host ""
Write-Host "  About to DELETE (permanent):" -ForegroundColor Yellow
foreach ($w in $worlds) {
    $mb = (Get-ChildItem $w.FullName -Recurse -File -EA SilentlyContinue | Measure-Object Length -Sum).Sum / 1MB
    "    {0,-16} {1:N1} MB   (terrain, player inventories, bases)" -f $w.Name, $mb
}
Write-Host ""
Write-Host "  Kept:" -ForegroundColor Green
foreach ($f in @("whitelist.json","ops.json","banned-players.json","server.properties","usercache.json")) {
    if (Test-Path (Join-Path $run $f)) { "    $f" }
}
"    mods\, config\  (players keep their registered passwords)"
Write-Host ""

if (-not $Force) {
    $answer = Read-Host "  Type DELETE to confirm"
    if ($answer -ne "DELETE") { Write-Host "Cancelled - nothing was removed."; exit 0 }
}

# Back up first. Cheap insurance; the caller can delete it later.
$stamp  = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$backup = Join-Path $PSScriptRoot "world-backup-$stamp"
New-Item -ItemType Directory -Force $backup | Out-Null
foreach ($w in $worlds) { Move-Item $w.FullName (Join-Path $backup $w.Name) }

Write-Host ""
Write-Host "  Old world moved to: $backup" -ForegroundColor Green
Write-Host "  Start the server (.\start.ps1) and it will generate a brand new world."
Write-Host "  Once you're happy with it, delete that backup folder to reclaim the space."
Write-Host ""
