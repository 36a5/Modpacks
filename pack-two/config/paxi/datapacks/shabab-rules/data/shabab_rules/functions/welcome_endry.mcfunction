# Shown only to endry, every time they join. Nobody else sees it.
scoreboard players reset @s sr_wait
title @s times 10 80 20
title @s subtitle {"text":"(I Love YOU)","color":"light_purple","italic":true}
title @s title {"text":"This is your gift","color":"gold","bold":true}
tellraw @s [{"text":"♥ ","color":"red"},{"text":"This is your gift ","color":"gold","bold":true},{"text":"(I Love YOU)","color":"light_purple","italic":true},{"text":" ♥","color":"red"}]
playsound minecraft:entity.player.levelup master @s ~ ~ ~ 1 1.2
