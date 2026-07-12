# Shabab 2 - Gate teleport buttons
#
# Solo Leveling announces a new Gate itself, but as flat text: you read the coordinates
# and walk. This replaces that line with one that carries a [TELEPORT] button.
#
# The button cannot run /tp — only two players are OP and /tp needs level 2. It runs
# `/trigger`, which every player may use, and the tick function does the teleport on
# their behalf.
#
# slb_gate : trigger. The player writes a Gate's slot number here by clicking the button.
# slb_gid  : slot number, held by the Gate entity. Slots run 1..9 and are recycled, which
#            is why the button text can be a fixed literal per slot (1.20.1 has no macros).
# slb_pos  : scratch — the coordinates a Gate is announced with, and the tp result flag.
scoreboard objectives add slb_gate trigger
scoreboard objectives add slb_gid dummy
scoreboard objectives add slb_pos dummy

# The mod's own announcement is redundant once ours carries the same coordinates plus a
# button, and two lines per Gate is noise.
gamerule soloGateNotification false

tellraw @a[tag=slb_admin] {"text":"[slb-gates] datapack loaded","color":"dark_gray"}
