# Executed as the requested Gate, at the Gate. The teleporting player is tagged slb_tping.
# ~ ~1 ~ so they land on top of the Gate rather than clipped inside it.
tp @a[tag=slb_tping,limit=1] ~ ~1 ~
scoreboard players set #found slb_pos 1
execute as @a[tag=slb_tping,limit=1] at @s run playsound minecraft:entity.enderman.teleport master @s ~ ~ ~ 1 1
tellraw @a[tag=slb_tping,limit=1] [{"text":"[ Gate ] ","color":"aqua","bold":true},{"text":"Teleported.","color":"gray"}]
