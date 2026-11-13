slr @s job MonarchOfWhiteFlames
tag @s add slb_wf_done
title @s title {"text":"MONARCH OF WHITE FLAMES","color":"white","bold":true}
title @s subtitle {"text":"The flames answer you.","color":"gray","italic":true}
playsound minecraft:entity.blaze.shoot master @s ~ ~ ~ 0.8 0.6
tellraw @a [{"text":"⚔ ","color":"gold"},{"selector":"@s","color":"yellow"},{"text":" has awakened as the ","color":"gray"},{"text":"Monarch of White Flames","color":"white","bold":true},{"text":".","color":"gray"}]
