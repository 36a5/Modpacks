# Executed as a player who just finished the Job Change Quest.
# Players tagged slb_wf_track count their job changes in slb_wf_count; the 6th one awards the
# Monarch of White Flames instead of a random job, once (job/white_flames tags slb_wf_done).
# Everyone else, and every other job change, gets the normal random roll.
tag @s remove slb_wf_next
scoreboard players add @s[tag=slb_wf_track] slb_wf_count 1
execute if entity @s[tag=slb_wf_track] if score @s slb_wf_count matches 6.. run tag @s add slb_wf_next
execute if entity @s[tag=slb_wf_next] run function slb_jobs:job/white_flames
execute unless entity @s[tag=slb_wf_next] run function slb_jobs:roll/random
tag @s remove slb_wf_next
