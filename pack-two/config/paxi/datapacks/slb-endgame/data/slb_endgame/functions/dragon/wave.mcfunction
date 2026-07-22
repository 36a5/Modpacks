# Executed as the dragon, at the dragon. Spawn a small flight of Shadow phantoms on each player
# fighting nearby. Persistent so they do not despawn mid-fight; tagged slb_add for easy cleanup.
execute as @a[distance=..120,gamemode=!spectator] at @s run summon minecraft:phantom ~ ~6 ~ {PersistenceRequired:1b,Tags:["slb_add"],Size:1,CustomName:'{"text":"Shadow","color":"dark_purple"}'}
execute as @a[distance=..120,gamemode=!spectator] at @s run summon minecraft:phantom ~2 ~7 ~-2 {PersistenceRequired:1b,Tags:["slb_add"],Size:2,CustomName:'{"text":"Shadow","color":"dark_purple"}'}
