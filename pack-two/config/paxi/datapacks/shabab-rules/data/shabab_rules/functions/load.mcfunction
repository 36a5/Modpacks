# Shabab 2 - server-wide gamerules, re-applied on every server start and /reload so a fresh
# world gets them too.

# Night is skipped once 30% of the players online are in bed (vanilla default is 100%).
# Comforts' sleeping bags and hammocks follow this gamerule as well.
gamerule playersSleepingPercentage 30

# endry's welcome. sr_left counts how many times a player has left the game, so a score
# above 0 on a player who is online means they just rejoined. sr_wait holds the message
# back about 10 seconds, until the client has finished loading and can actually show a title.
scoreboard objectives add sr_left minecraft.custom:minecraft.leave_game
scoreboard objectives add sr_wait dummy
