# Solo Leveling's CustomHUD flag is per-player and persistent, so this runs exactly once per
# player. It cancels the vanilla health, hunger, armor and XP bars (see net.solocraft
# DisableHealthbar / DisableHungerBar / DisableArmorBar / DisableLevelBar) and leaves the mod's
# own HP/MP bar as the only one on screen.
#
# It is a *toggle*: a player who wants the vanilla hearts back just runs /ToggleCustomHUD.
tag @s add shabab.hud
ToggleCustomHUD
