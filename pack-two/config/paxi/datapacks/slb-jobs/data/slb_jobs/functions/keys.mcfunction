# Seed sl_keys. A player who has never been given a bonus key has *no score* on this
# objective, and `if score @s sl_tier > @s sl_keys` is false when either side is unset - not
# "greater than nothing", just false. Without this line the grant below never fires for
# anybody, which is exactly what happened: a level 166 player sat at tier 2 with sl_keys
# unset and was handed nothing.
execute unless score @s sl_keys matches -2147483648..2147483647 run scoreboard players set @s sl_keys 0

# One bonus Job Change Quest Key per 50 levels, starting at 100: 100, 150, 200, ...
#
#   tier = (level - 50) / 50, floored, never below zero
#     level  99 -> (49)/50  = 0
#     level 100 -> (50)/50  = 1
#     level 149 -> (99)/50  = 1
#     level 150 -> (100)/50 = 2
#
# This is on top of the mod's own key, which it gives once at the soloLevelingJobChangeLevel
# gamerule's level (default 40) and never again — that path is untouched.
scoreboard players operation @s sl_tier = @s sl_level
scoreboard players remove @s sl_tier 50
execute if score @s sl_tier matches ..0 run scoreboard players set @s sl_tier 0
scoreboard players operation @s sl_tier /= #50 sl_const

# Owe them more than we have given? Hand one over and check again. A player who is already
# past 100 when this datapack first loads is owed their keys retroactively, and this loop
# is what pays them.
execute if score @s sl_tier > @s sl_keys run function slb_jobs:grant_key
