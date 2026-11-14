execute store result score @s sl_d_on run data get entity @s ForgeCaps."sololeveling:player_variables".ActiveDaily
execute if score @s sl_d_on matches 1 run function slb_jobs:daily/check
