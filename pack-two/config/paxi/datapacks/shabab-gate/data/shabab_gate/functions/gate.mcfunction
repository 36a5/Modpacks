# Held here until the join code is typed. Runs every tick for one gated player.

gamemode adventure @s
effect give @s blindness 3 0 true
effect give @s slowness 3 250 true

scoreboard players enable @s joincode
title @s actionbar [{"text":"Join code: ","color":"gold"},{"text":"/trigger joincode set <code>","color":"yellow","bold":true}]

# 10 minutes of grace, then out. Same window as the password step - deliberately long, because
# a player staring at a loading screen is not a player refusing to type.
scoreboard players add @s shabab.gatewait 1
execute if score @s shabab.gatewait matches 12000.. run function auth:kick

# code == 0 means the gate is switched off, and every player's trigger also starts at 0, so
# they pass here immediately. Set a real code to arm it (see load.mcfunction).
execute if score @s joincode = code shabab.gate run function shabab_gate:pass
execute unless score @s joincode matches 0 unless score @s joincode = code shabab.gate run function shabab_gate:wrong
