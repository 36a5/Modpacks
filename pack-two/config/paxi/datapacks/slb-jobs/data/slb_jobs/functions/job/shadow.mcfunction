slr @s job ShadowMonarch
title @s title {"text":"SHADOW MONARCH","color":"dark_purple","bold":true}
title @s subtitle {"text":"Arise.","color":"gray","italic":true}
playsound minecraft:entity.wither.spawn master @s ~ ~ ~ 0.6 1.4
tellraw @a [{"text":"⚔ ","color":"gold"},{"selector":"@s","color":"yellow"},{"text":" has awakened as the ","color":"gray"},{"text":"Shadow Monarch","color":"dark_purple","bold":true},{"text":".","color":"gray"}]
