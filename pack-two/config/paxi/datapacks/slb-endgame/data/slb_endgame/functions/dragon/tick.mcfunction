# Executed as the dragon, at the dragon.
execute unless score @s ed_init matches 1 run function slb_endgame:dragon/init

execute store result score @s ed_hp run data get entity @s Health 1

# Fire each phase exactly once, as health crosses its threshold.
execute if score @s ed_phase matches 0 if score @s ed_hp <= @s ed_t2 run function slb_endgame:dragon/phase2
execute if score @s ed_phase matches 1 if score @s ed_hp <= @s ed_t3 run function slb_endgame:dragon/phase3
execute if score @s ed_phase matches 2 if score @s ed_hp <= @s ed_t4 run function slb_endgame:dragon/phase4

# From the enrage phase on, run the Void barrage on a cooldown.
execute if score @s ed_phase matches 2.. run scoreboard players remove @s ed_atk 1
execute if score @s ed_phase matches 2.. if score @s ed_atk matches ..0 run function slb_endgame:dragon/barrage
