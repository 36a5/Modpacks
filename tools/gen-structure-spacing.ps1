Add-Type -AssemblyName System.IO.Compression.FileSystem
$ErrorActionPreference = "Stop"

$mods    = "D:\Minecraft-dev-workspace\Modpacks\server\run\mods"
$outRoot = "D:\Minecraft-dev-workspace\Modpacks\pack\config\openloader\data\al-shabab-spacing"

# Overworld surface structures only. Dimension-exclusive sets (aether, blue_skies, undergarden,
# bumblezone, nullscape, incendium, deeperdarker, deep_aether) never compete with these, and
# vanilla ("minecraft") is left alone so villages keep their expected rhythm.
$targets = @(
  "iceandfire","structory","structory_towers","towns_and_towers","ctov","dungeons_arise",
  "dldungeonsjbg","battle_towers","cataclysm","born_in_chaos_v1","mowziesmobs","apotheosis",
  "supplementaries","tombstone","betterwitchhuts","betterdeserttemples","betterjungletemples",
  "betterdungeons","terralith"
)

$scale = 1.6      # spread everything out by 60%
$count = 0

if (Test-Path $outRoot) { Remove-Item -Recurse -Force $outRoot }
New-Item -ItemType Directory -Force $outRoot | Out-Null

# OpenLoader treats each folder under config/openloader/data as a datapack root.
@'
{
  "pack": {
    "pack_format": 15,
    "description": "al Shabab - structure spacing overrides (stops structures generating inside each other)"
  }
}
'@ | Set-Content (Join-Path $outRoot "pack.mcmeta") -Encoding utf8

foreach ($jar in Get-ChildItem $mods -Filter *.jar) {
    try { $z = [IO.Compression.ZipFile]::OpenRead($jar.FullName) } catch { continue }
    foreach ($e in $z.Entries) {
        if ($e.FullName -match '^data/([^/]+)/worldgen/structure_set/(.+)\.json$') {
            $ns = $Matches[1]; $name = $Matches[2]
            if ($targets -notcontains $ns) { continue }

            $sr = New-Object IO.StreamReader($e.Open()); $txt = $sr.ReadToEnd(); $sr.Close()

            $spM = [regex]::Match($txt, '"spacing"\s*:\s*(\d+)')
            $seM = [regex]::Match($txt, '"separation"\s*:\s*(\d+)')
            if (-not $spM.Success) { continue }

            $sp = [int]$spM.Groups[1].Value
            $se = if ($seM.Success) { [int]$seM.Groups[1].Value } else { 0 }
            if ($sp -lt 14) { continue }   # leave dense cave sets alone

            $newSp = [int][math]::Round($sp * $scale)
            $newSe = [int][math]::Round($se * $scale)
            # Minecraft requires separation < spacing, else worldgen throws.
            if ($newSe -ge $newSp) { $newSe = $newSp - 1 }
            if ($newSe -lt 0) { $newSe = 0 }

            $new = $txt -replace '"spacing"\s*:\s*\d+', "`"spacing`": $newSp"
            if ($seM.Success) { $new = $new -replace '"separation"\s*:\s*\d+', "`"separation`": $newSe" }

            $dir = Join-Path $outRoot "data\$ns\worldgen\structure_set"
            $sub = Split-Path $name -Parent
            if ($sub) { $dir = Join-Path $dir $sub }
            New-Item -ItemType Directory -Force $dir | Out-Null
            $file = Join-Path $dir ((Split-Path $name -Leaf) + ".json")
            $new | Set-Content $file -Encoding utf8

            # sanity: must still parse as JSON
            try { Get-Content $file -Raw | ConvertFrom-Json | Out-Null }
            catch { Write-Host "  INVALID JSON: $ns/$name"; Remove-Item $file }

            $count++
        }
    }
    $z.Dispose()
}

Write-Host "wrote $count structure_set overrides"
Write-Host "`nsample:"
Get-ChildItem $outRoot -Recurse -Filter *.json | Select-Object -First 5 | ForEach-Object {
    $j = Get-Content $_.FullName -Raw | ConvertFrom-Json
    "  {0,-46} spacing={1} separation={2}" -f $_.FullName.Replace($outRoot,''), $j.placement.spacing, $j.placement.separation
}
