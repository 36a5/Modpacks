# Daily Quest at 5 of each instead of 25 push-ups, 25 sit-ups, 25 squats and 500 running.
# The mod's targets are hard-coded and it only completes the quest when every counter is at its
# full value, so once a player has 5 of each we call its own /slr <player> OP FinishDaily, which fills the
# counters and lets the mod pay out the reward as usual. It only acts while ActiveDaily is set.
# Runs once a second, not every tick: each `data get entity` serialises the whole player.
schedule function slb_jobs:daily 20t replace
execute as @a run function slb_jobs:daily/player
