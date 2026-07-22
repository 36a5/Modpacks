# 75% health - first blood. (The dragon ignores potion effects, so escalation is done with adds and
# the barrage, never with effects on the dragon itself.)
scoreboard players set @s ed_phase 1
execute at @s run playsound minecraft:entity.ender_dragon.growl master @a[distance=..200] ~ ~ ~ 2 0.8
title @a[distance=..200] subtitle {"text":"Shadows answer its cry","color":"gray"}
title @a[distance=..200] title {"text":"The Dragon bleeds","color":"dark_red"}
function slb_endgame:dragon/wave
