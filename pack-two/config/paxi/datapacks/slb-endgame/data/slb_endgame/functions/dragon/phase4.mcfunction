# 25% health - second wind. A hard, guaranteed heal (set directly, since the dragon ignores
# regeneration) and the Herald of the Void answers. 120 = a strong recovery on a vanilla 200-HP
# dragon; on a health-rebalanced dragon it is an approximate top-up, never a nerf below the 25% it
# fires at.
scoreboard players set @s ed_phase 3
execute if score @s ed_hp matches ..119 run data merge entity @s {Health:120.0f}
scoreboard players set @s ed_atk 30
execute at @s run playsound minecraft:entity.wither.spawn master @a[distance=..300] ~ ~ ~ 3 0.7
title @a[distance=..200] subtitle {"text":"The Herald of the Void answers","color":"dark_purple"}
title @a[distance=..200] title {"text":"SECOND WIND","color":"light_purple","bold":true}
function slb_endgame:dragon/herald
