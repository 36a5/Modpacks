# Mirror the mod's capability into scoreboards. A player who has never awakened has no
# such capability data; `execute store result` leaves the score at 0 in that case, which
# is exactly "no job, level 0", so no special case is needed.
execute as @a store result score @s sl_job run data get entity @s ForgeCaps."sololeveling:player_variables".JOB
execute as @a store result score @s sl_jct run data get entity @s ForgeCaps."sololeveling:player_variables".JobChange_timer
execute as @a store result score @s sl_level run data get entity @s ForgeCaps."sololeveling:player_variables".Level

# The end of the quest is no longer watched here. shababparty's JobChangeTalkMixin skips the
# mod's ending cutscene and calls slb_jobs:quest_done itself, once per finished run.

# Monarch of White Flames is not available on this server, however it was come by - except to
# the players on this allowlist, who keep it. The tag is re-applied every tick rather than set
# once, so it survives a player NBT wipe and needs no manual /tag after a reset.
tag @a[name=!Abdulrhman-S,name=!endry] remove slb_wf_ok
execute as @a[name=Abdulrhman-S] run tag @s add slb_wf_ok
execute as @a[name=endry] run tag @s add slb_wf_ok
execute as @a[tag=slb_wf_ok,tag=!slb_wf_done2] run tag @s add slb_wf_track2
execute as @a[tag=!slb_wf_ok] if score @s sl_job matches 4 run function slb_jobs:strip_white_flames

# Bonus Job Change Quest Keys.
execute as @a run function slb_jobs:keys

# Per-player, not `scoreboard players operation @a sl_jct_prev = @a sl_jct`: that form applies
# every target against every source in a nested loop, so each player would end up snapshotting
# whichever player was iterated last rather than themselves — and the quest-finished edge would
# fire for the wrong people.
execute as @a run scoreboard players operation @s sl_jct_prev = @s sl_jct
