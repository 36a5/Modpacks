# Executed as a player who just finished the Job Change Quest (from slb_jobs:quest_done).
# The White Flames allowlist (Abdulrhman-S and endry, see tick) is tagged slb_wf_track2 and counts
# job changes in slb_wf_try. The 11th one awards the Monarch of White Flames instead of a random
# job, once per player (job/white_flames tags slb_wf_done2). Every other change, and everyone
# else, gets the normal random roll, which never gives White Flames.
# slb_wf_try and the *2 tags replaced slb_wf_count / slb_wf_track / slb_wf_done on 2026-11-14 so
# both players started counting from zero again.
tag @s remove slb_wf_next
scoreboard players add @s[tag=slb_wf_track2] slb_wf_try 1
execute if entity @s[tag=slb_wf_track2] if score @s slb_wf_try matches 11.. run tag @s add slb_wf_next
execute if entity @s[tag=slb_wf_next] run function slb_jobs:job/white_flames
execute unless entity @s[tag=slb_wf_next] run function slb_jobs:roll/random
tag @s remove slb_wf_next
