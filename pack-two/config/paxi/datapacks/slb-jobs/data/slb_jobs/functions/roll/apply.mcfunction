# sl_r is 1, 2 or 3. /slr is the only way to write the mod's JOB capability, and it needs
# permission level 3 — see load.mcfunction on function-permission-level.
execute if score @s sl_r matches 1 run function slb_jobs:job/shadow
execute if score @s sl_r matches 2 run function slb_jobs:job/mage
execute if score @s sl_r matches 3 run function slb_jobs:job/frost
