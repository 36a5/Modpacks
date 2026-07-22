# 50% health - enrage. A bigger Shadow wave, and the Void barrage begins (ed_atk counts down in tick).
scoreboard players set @s ed_phase 2
scoreboard players set @s ed_atk 60
execute at @s run playsound minecraft:entity.ender_dragon.growl master @a[distance=..200] ~ ~ ~ 3 0.6
title @a[distance=..200] subtitle {"text":"The Void barrage begins","color":"gray"}
title @a[distance=..200] title {"text":"ENRAGED","color":"red","bold":true}
function slb_endgame:dragon/wave
function slb_endgame:dragon/wave
