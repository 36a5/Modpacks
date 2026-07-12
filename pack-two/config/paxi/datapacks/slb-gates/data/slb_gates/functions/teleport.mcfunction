# Executed as a player who clicked a [TELEPORT] button. Their trigger value is the slot
# of the Gate they want.
scoreboard players operation #req slb_gid = @s slb_gate
scoreboard players set @s slb_gate 0
scoreboard players set #found slb_pos 0

# Tag rather than pass the player along: the teleport has to run *as the Gate*, because
# /tp sends its target to the dimension of whoever is executing, and the Gate is the only
# thing that knows which dimension that is.
tag @s add slb_tping
execute as @e[type=#slb_gates:gates,tag=slb_seen] if score @s slb_gid = #req slb_gid at @s run function slb_gates:do_tp
tag @s remove slb_tping

execute if score #found slb_pos matches 0 run tellraw @s [{"text":"[ Gate ] ","color":"red","bold":true},{"text":"That Gate has already closed.","color":"gray"}]
