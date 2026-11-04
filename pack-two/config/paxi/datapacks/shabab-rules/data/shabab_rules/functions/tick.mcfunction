# endry only: start the welcome countdown on first join or on any rejoin.
execute as @a[name=endry,tag=!sr_seen] run scoreboard players set @s sr_wait 200
tag @a[name=endry] add sr_seen
execute as @a[name=endry,scores={sr_left=1..}] run scoreboard players set @s sr_wait 200
scoreboard players reset @a[name=endry,scores={sr_left=1..}] sr_left

scoreboard players remove @a[scores={sr_wait=1..}] sr_wait 1
execute as @a[name=endry,scores={sr_wait=0}] run function shabab_rules:welcome_endry
