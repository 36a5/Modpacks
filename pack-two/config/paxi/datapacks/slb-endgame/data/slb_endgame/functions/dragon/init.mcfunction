# Executed as a dragon we have not measured yet. Its current health is its maximum (it has just
# spawned), so read it and derive the phase thresholds from it - robust to health-rebalance mods.
execute store result score @s ed_max run data get entity @s Health 1

scoreboard players operation @s ed_t2 = @s ed_max
scoreboard players operation @s ed_t2 *= #75 ed_k
scoreboard players operation @s ed_t2 /= #100 ed_k

scoreboard players operation @s ed_t3 = @s ed_max
scoreboard players operation @s ed_t3 *= #50 ed_k
scoreboard players operation @s ed_t3 /= #100 ed_k

scoreboard players operation @s ed_t4 = @s ed_max
scoreboard players operation @s ed_t4 *= #25 ed_k
scoreboard players operation @s ed_t4 /= #100 ed_k

scoreboard players set @s ed_phase 0
scoreboard players set @s ed_init 1
