# 1. Solo Leveling's own HUD. The mod cancels the vanilla hearts, hunger, armor and XP bars
#    when its per-player CustomHUD flag is on, and ships /ToggleCustomHUD to flip it - but
#    nothing ever calls it, so every player renders both HUDs. Flip it once per player.
execute as @a[tag=!shabab.hud] at @s run function shabab_gate:hud_on

# 2. The Auth password step. Auth's own 30-second kick is disabled in load; this is the
#    replacement, 12000 ticks = 10 minutes, so a slow-loading client is never kicked mid-load.
execute as @a[tag=!auth.logged,tag=!auth.bypass] run scoreboard players add @s shabab.wait 1
execute as @a[tag=!auth.logged,tag=!auth.bypass] if score @s shabab.wait matches 12000.. at @s run function auth:kick
execute as @a[tag=auth.logged] run scoreboard players reset @s shabab.wait

# 3. The join-code gate, once the player is past the password. Held in adventure + blind until
#    they type the code. Admins can skip it: /tag <player> add shabab.bypass
execute as @a[tag=auth.logged,tag=!shabab.code,tag=!shabab.bypass] at @s run function shabab_gate:gate
