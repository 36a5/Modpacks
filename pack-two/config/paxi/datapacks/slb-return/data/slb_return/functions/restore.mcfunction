# Executed as a player on the tick they left a Gate / Punishment dimension. The mod has already
# dropped them at its own overworld point; we now override that and send them to their frozen home
# marker instead - its dimension and exact coordinates, the doorstep they left from.
scoreboard players operation #pid slb_pid = @s slb_pid
tag @s add slb_anchor

# tp the player (target) TO the marker (destination) -> player lands in the marker's dimension at its
# exact position. `@a[...]` so the marker can reach the player even though tp crosses dimensions.
# `unless predicate in_solo`: only if the marker is in a SAFE dimension. A player who logged in while
# already inside a Gate has a marker that never recorded a safe spot - restoring to it would drop them
# back inside. In that one case we leave the mod's own drop alone, and follow re-homes the marker as
# soon as they are somewhere safe.
execute as @e[tag=slb_home] if score @s slb_pid = #pid slb_pid unless predicate slb_return:in_solo run tp @a[tag=slb_anchor,limit=1] @s

# The marker is home again and travelling with the player from next tick; drop any force-load it had.
execute as @e[tag=slb_home] if score @s slb_pid = #pid slb_pid at @s run forceload remove ~ ~

tag @s remove slb_anchor
execute at @s run playsound minecraft:entity.enderman.teleport master @s ~ ~ ~ 1 1
tellraw @s [{"text":"[ Gate ] ","color":"aqua","bold":true},{"text":"Returned to where you entered.","color":"gray"}]
