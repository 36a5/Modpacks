# Seed a newly-seen player so their first computed delta is 0 (prevents crediting
# lifetime kills earned before this datapack existed).
scoreboard players operation @s pk_prev = @s pk_kills
scoreboard players set @s pk_init 1
