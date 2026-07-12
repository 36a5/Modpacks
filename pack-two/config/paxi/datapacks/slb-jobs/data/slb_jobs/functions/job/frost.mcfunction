slr @s job FrostMonarch
title @s title {"text":"FROST MONARCH","color":"blue","bold":true}
title @s subtitle {"text":"The cold obeys.","color":"gray","italic":true}
playsound minecraft:block.glass.break master @s ~ ~ ~ 1 0.6
tellraw @a [{"text":"⚔ ","color":"gold"},{"selector":"@s","color":"yellow"},{"text":" has awakened as the ","color":"gray"},{"text":"Frost Monarch","color":"blue","bold":true},{"text":".","color":"gray"}]
