# Shabab 2 - Gate announcements
#
# Solo Leveling announces a new Gate itself, but as three wrapped lines of flat text. This
# replaces it with one tidy line: the Gate's name and coordinates. Players walk there.
#
# There used to be a [TP] button on that line. It was removed on purpose: Gates are meant
# to be found, not teleported to. The old slb_gate trigger objective is dropped below so a
# button still sitting in someone's chat history does nothing when clicked.
#
# slb_pos : scratch, the coordinates a Gate is announced with.
scoreboard objectives remove slb_gate
scoreboard objectives remove slb_gid
scoreboard objectives add slb_pos dummy

# The mod's own announcement is redundant once ours carries the same coordinates, and two
# lines per Gate is noise.
gamerule soloGateNotification false

# How often a new Gate opens, in seconds of server uptime. The mod default is 400 (about one
# every 7 minutes), which buried the world in Gates. 7200 = one every 2 hours.
gamerule soloGateDelay 7200

tellraw @a[tag=slb_admin] {"text":"[slb-gates] datapack loaded","color":"dark_gray"}
