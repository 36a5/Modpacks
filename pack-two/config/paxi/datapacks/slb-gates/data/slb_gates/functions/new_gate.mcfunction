# Executed as a Gate entity that has not been announced yet, at its position.
tag @s add slb_seen

# Take the next slot, wrapping at 9. Whichever older Gate held this slot loses it: its
# button stops working rather than sending someone to the wrong Gate.
scoreboard players add #slot slb_gid 1
execute if score #slot slb_gid matches 10.. run scoreboard players set #slot slb_gid 1
execute as @e[type=#slb_gates:gates,tag=slb_seen] if score @s slb_gid = #slot slb_gid run scoreboard players reset @s slb_gid
scoreboard players operation @s slb_gid = #slot slb_gid

# tellraw can print a score but not a raw NBT number, so the coordinates go through
# scoreboard holders to be displayed.
execute store result score #x slb_pos run data get entity @s Pos[0]
execute store result score #y slb_pos run data get entity @s Pos[1]
execute store result score #z slb_pos run data get entity @s Pos[2]

# Played on each player where they stand, not at the Gate, so a Gate across the map is
# still audible.
execute as @a at @s run playsound minecraft:block.portal.trigger master @s ~ ~ ~ 0.5 0.6

# The button's command must be a literal, so there is one announce function per slot.
execute if score @s slb_gid matches 1 run function slb_gates:announce/1
execute if score @s slb_gid matches 2 run function slb_gates:announce/2
execute if score @s slb_gid matches 3 run function slb_gates:announce/3
execute if score @s slb_gid matches 4 run function slb_gates:announce/4
execute if score @s slb_gid matches 5 run function slb_gates:announce/5
execute if score @s slb_gid matches 6 run function slb_gates:announce/6
execute if score @s slb_gid matches 7 run function slb_gates:announce/7
execute if score @s slb_gid matches 8 run function slb_gates:announce/8
execute if score @s slb_gid matches 9 run function slb_gates:announce/9
