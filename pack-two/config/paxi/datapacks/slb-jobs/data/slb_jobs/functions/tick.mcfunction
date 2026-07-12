# Mirror the mod's capability into scoreboards. A player who has never awakened has no
# such capability data; `execute store result` leaves the score at 0 in that case, which
# is exactly "no job, level 0", so no special case is needed.
execute as @a store result score @s sl_job run data get entity @s ForgeCaps."sololeveling:player_variables".JOB
execute as @a store result score @s sl_jct run data get entity @s ForgeCaps."sololeveling:player_variables".JobChange_timer
execute as @a store result score @s sl_level run data get entity @s ForgeCaps."sololeveling:player_variables".Level

# The quest's ending cutscene just started: remember what they are now, so the re-roll at
# the end of it can avoid handing back the same job.
execute as @a if score @s sl_jct matches 1.. if score @s sl_jct_prev matches 0 run scoreboard players operation @s sl_job_pre = @s sl_job

# The cutscene just ended. The mod has, one instruction ago, set them to Shadow Monarch.
# Overwrite that with the roll.
execute as @a if score @s sl_jct matches 0 if score @s sl_jct_prev matches 1.. run function slb_jobs:roll

# Monarch of White Flames is not available on this server, however it was come by.
execute as @a if score @s sl_job matches 4 run function slb_jobs:strip_white_flames

# Bonus Job Change Quest Keys.
execute as @a run function slb_jobs:keys

# Per-player, not `scoreboard players operation @a sl_jct_prev = @a sl_jct`: that form applies
# every target against every source in a nested loop, so each player would end up snapshotting
# whichever player was iterated last rather than themselves — and the quest-finished edge would
# fire for the wrong people.
execute as @a run scoreboard players operation @s sl_jct_prev = @s sl_jct
