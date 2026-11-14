# Executed as a player who just finished the Job Change Quest (from slb_jobs:quest_done).
#
# Monarch of White Flames is only ever given to Abdulrhman-S and endry (the slb_wf_ok allowlist in
# tick), only together, and never alone:
#   * every finished run counts for that player in slb_wf_try, solo runs included
#   * a run gives White Flames only if the other one also finished within 60 seconds (1200 ticks),
#     and the run is the 7th or later for either of them. Then both get it at once.
#   * a solo run never gives it, whatever the count; it gets the normal random roll
#   * once someone has it (tag slb_wf_done2), later job changes keep White Flames
# The first of the pair to finish gets a random job and is upgraded when the second finishes.
# Everyone else always gets the normal random roll, which never gives White Flames.
tag @s remove slb_wf_handled
execute if entity @s[tag=slb_wf_ok,tag=slb_wf_done2] run function slb_jobs:wf/keep
execute if entity @s[tag=slb_wf_track2,tag=!slb_wf_handled] run function slb_jobs:wf/finish
execute unless entity @s[tag=slb_wf_handled] run function slb_jobs:roll/random
tag @s remove slb_wf_handled
