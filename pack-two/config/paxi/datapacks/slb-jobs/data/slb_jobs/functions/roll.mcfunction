# Executed as a player who just finished the Job Change Quest.
# A player tagged slb_wf_next gets the Monarch of White Flames instead of a random job, once:
# job/white_flames tags them slb_wf_done so the tick line stops re-arming it.
execute if entity @s[tag=slb_wf_next] run function slb_jobs:job/white_flames
execute unless entity @s[tag=slb_wf_next] run function slb_jobs:roll/random
tag @s remove slb_wf_next
