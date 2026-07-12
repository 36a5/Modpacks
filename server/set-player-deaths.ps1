# ============================================================
#  al Shabab - set one player's death count
#
#  Minecraft has no command for this: the death total lives in
#  world/stats/<uuid>.json, which the server rewrites from memory
#  on logout and on its periodic flush. Editing it while the
#  server is up therefore does nothing - the server overwrites it.
#  So this refuses to run unless the server is stopped.
#
#  The Discord leaderboard reads this file directly and ranks by
#  lifetime totals, so whatever is written here is what the board
#  will show on its next refresh. (It used to also need the bot's
#  weekly baseline zeroed; there is no baseline any more.)
#
#  Usage:  .\set-player-deaths.ps1 Abdulrhman-S 5
#          .\set-player-deaths.ps1 Abdulrhman-S        # defaults to 0
# ============================================================
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$Player,
    [Parameter(Position = 1)][ValidateRange(0, [int]::MaxValue)][int]$Deaths = 0
)

$ErrorActionPreference = "Stop"
$run = Join-Path $PSScriptRoot "run"

$live = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match "forge" }
if ($live) {
    Write-Host "The server is still running. Stop it first (type 'stop' in its console)." -ForegroundColor Red
    exit 1
}

$usercache = Join-Path $run "usercache.json"
if (-not (Test-Path $usercache)) { Write-Host "No usercache.json - has anyone ever joined?" -ForegroundColor Red; exit 1 }

$entry = (Get-Content $usercache -Raw | ConvertFrom-Json) | Where-Object { $_.name -eq $Player } | Select-Object -First 1
if (-not $entry) { Write-Host "No player named '$Player' in usercache.json." -ForegroundColor Red; exit 1 }
$uuid = $entry.uuid

$statsFile = Join-Path $run "world\stats\$uuid.json"
if (-not (Test-Path $statsFile)) { Write-Host "No stats file for $Player ($uuid)." -ForegroundColor Red; exit 1 }

$stats = Get-Content $statsFile -Raw | ConvertFrom-Json
$before = $stats.stats.'minecraft:custom'.'minecraft:deaths'
if ($null -eq $before) { $before = 0 }

Copy-Item $statsFile "$statsFile.bak" -Force
$stats.stats.'minecraft:custom'.'minecraft:deaths' = $Deaths

$utf8 = New-Object System.Text.UTF8Encoding($false)   # no BOM: the server reads this with Gson
[System.IO.File]::WriteAllText($statsFile, ($stats | ConvertTo-Json -Depth 10 -Compress), $utf8)

Write-Host "  $Player deaths: $before -> $Deaths" -ForegroundColor Green
Write-Host "  backup: $statsFile.bak"
Write-Host ""
Write-Host "  Done. Start the server again (.\start.ps1)." -ForegroundColor Green
