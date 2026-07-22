# Executed as a player who has no home marker yet (first join, or first load of this datapack).
# #next is a global counter holder; each new player takes the next id.
scoreboard players add #next slb_pid 1
scoreboard players operation @s slb_pid = #next slb_pid
tag @s add slb_has

# Spawn the marker on the player and stamp it with the same id so follow/enter/restore can find it.
# #pid carries the id across the `as @e` hop (a summoned entity cannot read the player's score).
scoreboard players operation #pid slb_pid = @s slb_pid
execute at @s run summon marker ~ ~ ~ {Tags:["slb_home","slb_fresh"]}
execute as @e[tag=slb_fresh] run scoreboard players operation @s slb_pid = #pid slb_pid
execute as @e[tag=slb_fresh] run tag @s remove slb_fresh
