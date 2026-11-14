# Executed as a player whose Job Change Quest just ended, called by shababparty's
# JobChangeTalkMixin in place of the mod's chat cutscene. Remember the job they walked in with
# (read live, the sl_job mirror can be a tick old) so the roll never hands it back, then roll.
execute store result score @s sl_job_pre run data get entity @s ForgeCaps."sololeveling:player_variables".JOB
function slb_jobs:roll
