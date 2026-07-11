scoreboard players set @s joincode 0
scoreboard players add @s shabab.tries 1

tellraw @s {"text":"Wrong join code.","color":"red"}
playsound minecraft:entity.villager.no master @s ~ ~ ~

# three strikes, same as Auth's password step
execute if score @s shabab.tries matches 3.. run function auth:kick
