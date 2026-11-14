execute store result score @s sl_d_push run data get entity @s ForgeCaps."sololeveling:player_variables".pushup
execute store result score @s sl_d_sit run data get entity @s ForgeCaps."sololeveling:player_variables".situp
execute store result score @s sl_d_squat run data get entity @s ForgeCaps."sololeveling:player_variables".squat
execute store result score @s sl_d_run run data get entity @s ForgeCaps."sololeveling:player_variables".RUN
execute if score @s sl_d_push matches 5.. if score @s sl_d_sit matches 5.. if score @s sl_d_squat matches 5.. if score @s sl_d_run matches 5.. run slr @s OP FinishDaily
