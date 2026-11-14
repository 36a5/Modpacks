# Job Change Quest survival: pull straggling Demon Knights to the players.
# shababparty's JobQuestWave spawns the whole knight wave at once and ends the quest the moment no
# knight is left within 150 blocks. A knight that gets stuck in a wall or wanders off kept that
# from ever happening, so the run fell back to the full 100 second timer. From 10 seconds into the
# survival (jobtimer 90 and below), any knight more than 24 blocks from every player is teleported
# to the nearest one, so the last knights always come to you and killing them ends the quest.
# Every 10 ticks, and only for players in the Igris dungeon: `data get entity` serialises the player.
schedule function slb_jobs:quest_wave 10t replace
execute as @a at @s if dimension sololeveling:dungeon_dimension_igris run function slb_jobs:quest_wave/player
