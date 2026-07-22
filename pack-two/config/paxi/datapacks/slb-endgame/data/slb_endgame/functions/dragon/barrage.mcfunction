# Executed as the dragon, at the dragon, once per cooldown while enraged. Rain fire straight down on
# up to two random fighters. The fireball spawns high above each target with downward motion so it
# actually lands on them - a Void barrage they have to keep moving to dodge.
scoreboard players set @s ed_atk 100
execute as @a[distance=..120,gamemode=!spectator,limit=2,sort=random] at @s run summon minecraft:small_fireball ~ ~16 ~ {Motion:[0.0,-1.0,0.0],power:[0.0,-0.1,0.0],Tags:["slb_meteor"]}
execute as @a[distance=..120,gamemode=!spectator] at @s run playsound minecraft:entity.blaze.shoot master @s ~ ~ ~ 1 0.7
