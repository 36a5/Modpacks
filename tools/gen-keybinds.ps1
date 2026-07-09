$ErrorActionPreference = "Stop"

# ---------------------------------------------------------------------------
# al Shabab keybind layout.
#
# Reserved and therefore never used below:
#   vanilla : W A S D E Q F T Tab Esc 1-9 Space Shift Ctrl L(advancements)
#             P(social) F1-F12 mouse1-3
#   Xaero's : M (world map) and Y (minimap settings) - Xaero registers its keys
#             at runtime so we cannot rebind them from a file; we route around them.
# ---------------------------------------------------------------------------

$UNBOUND = "key.keyboard.unknown"

# Keys vanilla Minecraft already owns. Binding a mod to any of these is a conflict
# even though the mod-vs-mod duplicate check would never notice.
$VANILLA = @(
    "key.keyboard.w","key.keyboard.a","key.keyboard.s","key.keyboard.d",
    "key.keyboard.e","key.keyboard.q","key.keyboard.f","key.keyboard.t",
    "key.keyboard.tab","key.keyboard.escape","key.keyboard.space",
    "key.keyboard.left.shift","key.keyboard.left.control",
    "key.keyboard.l","key.keyboard.p",
    "key.keyboard.1","key.keyboard.2","key.keyboard.3","key.keyboard.4","key.keyboard.5",
    "key.keyboard.6","key.keyboard.7","key.keyboard.8","key.keyboard.9",
    "key.keyboard.f1","key.keyboard.f2","key.keyboard.f3","key.keyboard.f5","key.keyboard.f11",
    "key.mouse.left","key.mouse.right","key.mouse.middle"
)

# Keys Xaero's Minimap / World Map claim at runtime (cannot be rebound from a file).
$XAERO = @("key.keyboard.m","key.keyboard.y")

$bind = [ordered]@{
    # ---- progression menus (one key each, all distinct) --------------------
    "key.puffish_skills.open"            = "key.keyboard.k"          # Skill Tree
    "key.reskillable.open_skills"        = "key.keyboard.n"          # Reskillable skills
    "key.solo_leveling.open_main_menu"   = "key.keyboard.h"          # Solo Leveling menu
    "key.beastiary"                      = "key.keyboard.j"          # Lycanites Beastiary
    "key.pets"                           = "key.keyboard.o"          # Lycanites pets
    "key.minions"                        = "key.keyboard.i"          # Lycanites minions
    "key.scalinghealth.difficultyMeter"  = "key.keyboard.semicolon"  # difficulty meter
    "key.mobends.menu"                   = "key.keyboard.apostrophe" # Mo' Bends settings
    "key.jade.config"                    = "key.keyboard.period"     # Jade config

    # ---- abilities --------------------------------------------------------
    "key.special_ability"                = "key.mouse.4"             # Alex's Caves special ability
    "key.cataclysm.ability"              = "key.mouse.5"             # Cataclysm ability
    "key.solo_leveling.use_skill"        = "key.keyboard.r"
    "key.solo_leveling.select_skill"     = "key.keyboard.x"
    "key.solo_leveling.target_lock"      = "key.keyboard.g"
    "key.elenaidodge2.dodge"             = "key.keyboard.c"          # dodge roll
    "key.summoning"                      = "key.keyboard.z"          # Lycanites summoning
    "key.aether.gravitite_jump_ability"  = "key.keyboard.right.bracket"
    "key.carry"                          = "key.keyboard.b"          # Carry On

    # ---- voice chat (bound explicitly so nothing else can claim V) ---------
    "key.voice_chat"                     = "key.keyboard.v"          # voice chat menu
    "key.push_to_talk"                   = "key.keyboard.caps.lock"
    "key.mute_microphone"                = "key.keyboard.backslash"
    "key.voice_chat_settings"            = $UNBOUND                  # inside the V menu
    "key.voice_chat_adjust_volumes"      = $UNBOUND                  # inside the V menu
    "key.voice_chat_group"               = $UNBOUND                  # inside the V menu
    "key.hide_icons"                     = $UNBOUND
    "key.disable_voice_chat"             = $UNBOUND
    "key.voice_chat_toggle_recording"    = $UNBOUND
    "key.whisper"                        = "key.keyboard.left.alt"

    # ---- mounts (distinct from movement; Ctrl is vanilla sprint) -----------
    "key.mount.ability"                  = "key.keyboard.left.bracket"
    "key.mount.descend"                  = "key.keyboard.page.down"
    "key.mount.inventory"                = "key.keyboard.u"
    "key.mount.dismount"                 = "key.keyboard.comma"

    # ---- storage / tools ---------------------------------------------------
    "key.sophisticatedbackpacks.open_backpack" = "key.keyboard.grave.accent"
    "key.travelertoolbelt.open_toolbelt"       = "key.keyboard.slash"
    "key.toms_storage.open_terminal"           = "key.keyboard.minus"

    # ---- unbound: reachable through another menu, or pure clutter ----------
    "key.curios.open"                    = $UNBOUND  # click the Curios tab in the inventory
    "key.aether.open_accessories"        = $UNBOUND  # same inventory tab
    "key.relics.ability_list"            = $UNBOUND  # HUD toggle, not needed on a key
    "key.index"                          = $UNBOUND  # Beastiary Index lives inside the Beastiary
    "key.fbp.open_settings"              = $UNBOUND  # cosmetic mod, config file
    "key.fbp.toggle_mod"                 = $UNBOUND
    "key.fbp.toggle_animations"          = $UNBOUND
    "key.fbp.freeze_particles"           = $UNBOUND
    "key.fbp.kill_particles"             = $UNBOUND
    "key.fbp.reload_config"              = $UNBOUND
    "key.fbp.add_to_blacklist"           = $UNBOUND
    "key.jade.narrate"                   = $UNBOUND
    "key.jade.toggle_liquid"             = $UNBOUND
}

# --- validate: no two bound actions share a key ----------------------------
$dupes = $bind.GetEnumerator() |
    Where-Object { $_.Value -ne $UNBOUND } |
    Group-Object Value |
    Where-Object { $_.Count -gt 1 }

if ($dupes) {
    Write-Host "MOD-vs-MOD KEY CONFLICTS:" -ForegroundColor Red
    foreach ($d in $dupes) { "  {0} <- {1}" -f $d.Name, (($d.Group.Key) -join ', ') }
    exit 1
}

# --- validate: nothing lands on a vanilla or Xaero key ---------------------
$bad = $bind.GetEnumerator() | Where-Object {
    $_.Value -ne $UNBOUND -and ($VANILLA -contains $_.Value -or $XAERO -contains $_.Value)
}
if ($bad) {
    Write-Host "CONFLICTS WITH VANILLA / XAERO:" -ForegroundColor Red
    $bad | ForEach-Object { "  {0} -> {1}" -f $_.Key, $_.Value }
    exit 1
}

# --- validate: every id we bind was actually seen in a mod jar --------------
$csv   = Import-Csv "C:\Users\abdul\AppData\Local\Temp\build\scratchpad\keybinds.csv"
$known = $csv.KeyId | ForEach-Object { $_ -replace '\.desc$','' } | Sort-Object -Unique

$unknown = $bind.Keys | Where-Object { $known -notcontains $_ }
if ($unknown) {
    Write-Host "IDs not found in any mod jar (would be ignored by Minecraft):" -ForegroundColor Yellow
    $unknown | ForEach-Object { "  $_" }
}

# --- write keybindings.txt --------------------------------------------------
$out = "D:\Minecraft-dev-workspace\Modpacks\pack\config\defaultoptions\keybindings.txt"
New-Item -ItemType Directory -Force (Split-Path $out) | Out-Null

$lines = foreach ($k in $bind.Keys) { "key_{0}:{1}" -f $k, $bind[$k] }
$enc = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText($out, (($lines -join "`n") + "`n"), $enc)

Write-Host ""
Write-Host ("wrote {0} bindings ({1} bound, {2} unbound), 0 conflicts" -f `
    $bind.Count,
    ($bind.Values | Where-Object { $_ -ne $UNBOUND }).Count,
    ($bind.Values | Where-Object { $_ -eq $UNBOUND }).Count)
Write-Host ""
Write-Host "bound keys in use:"
$bind.GetEnumerator() | Where-Object { $_.Value -ne $UNBOUND } | Sort-Object Value |
    ForEach-Object { "  {0,-24} {1}" -f ($_.Value -replace 'key\.(keyboard|mouse)\.',''), $_.Key }
