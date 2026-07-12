slr @s job GrandMage
title @s title {"text":"GRAND MAGE","color":"aqua","bold":true}
title @s subtitle {"text":"The mana answers.","color":"gray","italic":true}
playsound minecraft:entity.illusioner.cast_spell master @s ~ ~ ~ 1 1
tellraw @a [{"text":"⚔ ","color":"gold"},{"selector":"@s","color":"yellow"},{"text":" has awakened as the ","color":"gray"},{"text":"Grand Mage","color":"aqua","bold":true},{"text":".","color":"gray"}]
