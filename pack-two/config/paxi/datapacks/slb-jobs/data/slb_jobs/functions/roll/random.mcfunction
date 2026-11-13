# Executed as a player who just finished the Job Change Quest.
# sl_job_pre holds the job they had when this run started; the roll excludes it, so a
# second run is always a real re-roll and never lands you back where you were.
# A player who had no job (or had White Flames) draws from all three.
execute if score @s sl_job_pre matches 1 run function slb_jobs:roll/not_shadow
execute if score @s sl_job_pre matches 2 run function slb_jobs:roll/not_mage
execute if score @s sl_job_pre matches 3 run function slb_jobs:roll/not_frost
execute unless score @s sl_job_pre matches 1..3 run function slb_jobs:roll/any
