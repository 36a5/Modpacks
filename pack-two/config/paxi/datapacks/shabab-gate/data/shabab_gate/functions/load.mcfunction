# Shabab 2 gate - runs on every server start and /reload.

scoreboard objectives add shabab.gate dummy
scoreboard objectives add shabab.wait dummy
scoreboard objectives add shabab.gatewait dummy
scoreboard objectives add shabab.tries dummy
scoreboard objectives add joincode trigger

# The join code. 0 = gate disabled (everyone walks straight through), which is the shipped
# default so the real code never lives in a public git repo. Set it once, on the server:
#     /scoreboard players set code shabab.gate 4821
# It is stored in the world and survives restarts. Change it any time with the same command.
execute unless score code shabab.gate matches -2147483648..2147483647 run scoreboard players set code shabab.gate 0

# Auth ships a 30-second kick timer on the password step, which is far too short for a player
# whose client is still chewing through 380 mods' worth of terrain - that is what was kicking
# people mid-load. Turn Auth's timer off; shabab_gate:tick runs a 10-minute one instead.
# Auth still kicks after 3 wrong passwords, which is what the `kick` setting really guards.
scoreboard players set kick auth.settings 1
scoreboard players set kick_time auth.settings 0
