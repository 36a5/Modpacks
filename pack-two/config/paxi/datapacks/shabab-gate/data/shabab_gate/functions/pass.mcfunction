tag @s add shabab.code

scoreboard players reset @s joincode
scoreboard players reset @s shabab.gatewait
scoreboard players reset @s shabab.tries

effect clear @s blindness
effect clear @s slowness
gamemode survival @s

tellraw @s [{"text":"Join code accepted. ","color":"green","bold":true},{"text":"Welcome to Shabab 2.","color":"gray"}]
playsound minecraft:entity.player.levelup master @s ~ ~ ~ 1 1
