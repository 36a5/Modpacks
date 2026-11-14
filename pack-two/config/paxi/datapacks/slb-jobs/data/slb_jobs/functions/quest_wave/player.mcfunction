execute store result score @s sl_q_t run data get entity @s ForgeCaps."sololeveling:player_variables".jobtimer
execute if score @s sl_q_t matches 3..90 as @e[type=#slb_jobs:knights,distance=..150] at @s unless entity @a[distance=..24] run tp @s @p
