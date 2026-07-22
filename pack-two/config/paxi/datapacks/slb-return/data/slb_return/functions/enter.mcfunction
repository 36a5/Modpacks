# Executed as a player on the tick they entered a Gate / Punishment dimension. Their marker did NOT
# follow them in (follow is skipped once slb_solo=1), so it is still frozen at the doorstep in the
# safe dimension. Force-load the chunk it sits in, otherwise the marker unloads while the player is
# away and cannot be found on the way back out.
scoreboard players operation #pid slb_pid = @s slb_pid
execute as @e[tag=slb_home] if score @s slb_pid = #pid slb_pid at @s run forceload add ~ ~
