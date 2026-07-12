# JOB=4 is Monarch of White Flames. The quest can no longer produce it, but an OP running
# `/slr <player> job MonarchOfWhiteFlames` still can, and anyone who already holds it from
# before this datapack existed would keep it. Re-roll them into one of the three allowed
# jobs instead of merely resetting them to jobless, which would silently cost them a job
# they earned.
scoreboard players set @s sl_job_pre 0
function slb_jobs:roll/any
tellraw @s [{"text":"[ SYSTEM ] ","color":"aqua","bold":true},{"text":"The Monarch of White Flames is sealed on this world. Your authority has been rewritten.","color":"gray"}]
