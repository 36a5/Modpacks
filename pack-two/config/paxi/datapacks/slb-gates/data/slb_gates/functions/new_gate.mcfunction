# Executed as a Gate entity that has not been announced yet, at its position.
tag @s add slb_seen

# tellraw can print a score but not a raw NBT number, so the coordinates go through
# scoreboard holders to be displayed.
execute store result score #x slb_pos run data get entity @s Pos[0]
execute store result score #y slb_pos run data get entity @s Pos[1]
execute store result score #z slb_pos run data get entity @s Pos[2]

# Played on each player where they stand, not at the Gate, so a Gate across the map is
# still audible.
execute as @a at @s run playsound minecraft:block.portal.trigger master @s ~ ~ ~ 0.5 0.6

function slb_gates:announce
