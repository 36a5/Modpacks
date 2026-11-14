# Executed as the player who finished second. Both of them become Monarch of White Flames.
execute as @a[tag=slb_wf_partner] run function slb_jobs:job/white_flames
function slb_jobs:job/white_flames
tag @s add slb_wf_handled
