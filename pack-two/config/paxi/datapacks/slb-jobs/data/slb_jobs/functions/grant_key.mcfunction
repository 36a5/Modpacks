# Give one key, bank it, and recurse if they are owed another (a level-200 player who
# joins for the first time under this datapack is owed three).
give @s sololeveling:job_key 1
scoreboard players add @s sl_keys 1
playsound minecraft:entity.player.levelup master @s ~ ~ ~ 1 1.6
tellraw @s [{"text":"\n[ SYSTEM ] ","color":"aqua","bold":true},{"text":"A ","color":"white"},{"text":"Job Change Quest Key","color":"gold","bold":true},{"text":" has been granted for your progress.","color":"white"},{"text":"\nUse it to re-roll your job — you will never draw the one you already hold.\n","color":"dark_gray","italic":true}]
execute if score @s sl_tier > @s sl_keys run function slb_jobs:grant_key
