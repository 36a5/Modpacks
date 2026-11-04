# Shabab 2 - server-wide gamerules, re-applied on every server start and /reload so a fresh
# world gets them too.

# Night is skipped once 30% of the players online are in bed (vanilla default is 100%).
# Comforts' sleeping bags and hammocks follow this gamerule as well.
gamerule playersSleepingPercentage 30

# Personal join messages moved to the /welcome command (shababparty). These objectives belonged
# to the old hardcoded version and are dropped here.
scoreboard objectives remove sr_left
scoreboard objectives remove sr_wait
