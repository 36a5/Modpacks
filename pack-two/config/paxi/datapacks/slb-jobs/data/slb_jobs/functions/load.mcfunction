# Shabab 2 - Job Change Quest rules
#
# Vanilla Solo Leveling: the Job Change Quest always ends in Shadow Monarch, and the mod
# hands out exactly one Job Change Quest Key in a player's life (at the level set by the
# soloLevelingJobChangeLevel gamerule, default 40). Monarch of White Flames exists as
# JOB=4 but nothing in the game grants it — only `/slr <player> job MonarchOfWhiteFlames`.
#
# Here instead:
#   * finishing the quest awards a random job out of Shadow Monarch / Grand Mage /
#     Frost Monarch — never Monarch of White Flames
#   * finishing it again re-rolls, and never returns the job you walked in with, so a
#     re-roll always changes something
#   * a bonus Job Change Quest Key at level 100, 150, 200, ... on top of the mod's own key
#
# The mod keeps a player's job and level in a Forge capability, not in NBT we can write.
# We can *read* it (it is serialised under ForgeCaps) and we can *write* it through the
# mod's own /slr command — which needs permission level 3, hence
# function-permission-level=4 in server.properties. Without that, every /slr below fails
# silently and players just keep getting Shadow Monarch.
#
# sl_job      : the mod's JOB, mirrored     (0 none, 1 Shadow, 2 Grand Mage, 3 Frost, 4 White Flames)
# sl_job_pre  : the job held when the current quest run started — the one a re-roll must avoid
# sl_jct      : the mod's JobChange_timer, mirrored. It counts 1..9 through the quest's
#               ending cutscene and drops back to 0 on the tick the job is awarded, which
#               is the only reliable "the quest just finished" signal: watching JOB alone
#               misses a Shadow Monarch who re-rolls into Shadow Monarch.
# sl_jct_prev : last tick's sl_jct
# sl_level    : the mod's Level, mirrored
# sl_tier     : how many bonus keys this player's level entitles them to
# sl_keys     : how many bonus keys we have actually given them
# sl_r        : the roll
scoreboard objectives add sl_job dummy
scoreboard objectives add sl_job_pre dummy
scoreboard objectives add sl_jct dummy
scoreboard objectives add sl_jct_prev dummy
scoreboard objectives add sl_level dummy
scoreboard objectives add sl_tier dummy
scoreboard objectives add sl_keys dummy
scoreboard objectives add sl_r dummy
scoreboard objectives add sl_const dummy
scoreboard players set #50 sl_const 50

tellraw @a[tag=slb_admin] {"text":"[slb-jobs] datapack loaded","color":"dark_gray"}
