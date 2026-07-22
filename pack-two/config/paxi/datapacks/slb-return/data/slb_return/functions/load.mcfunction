# Shabab 2 - Solo Leveling return-to-origin
#
# The mod's own DungeonDimension / SystemVoidDimension "player leaves" procedures drop you at a
# saved-or-random spot in the OVERWORLD, always. Enter a Gate from the Twilight Forest, the Aether,
# the Nether - or just from a different overworld base than the mod remembers - and you come out
# somewhere you never were. Two players clearing the same Gate both get flung to the mod's single
# stored point, not to their own doorsteps.
#
# This datapack keeps a per-player "home" marker that rides along through every safe dimension,
# recording the exact block you last stood on and the dimension you stood in. The instant you step
# into a Gate or the Punishment Zone the marker freezes there and its chunk is force-loaded so it
# survives while you are away. When the mod spits you back out, we override its drop and send you to
# the marker: same dimension, same coordinates, every time, independently for every player.
#
# slb_pid  : a unique id shared by a player and their own home marker (this is how each of several
#            players is matched to their own marker - same trick as slb-gates' slb_gid).
# slb_solo : 1 while the player is inside a Gate / Punishment dimension this tick.
# slb_prev : slb_solo from last tick, so we can catch the exact enter and exit ticks.
scoreboard objectives add slb_pid dummy
scoreboard objectives add slb_solo dummy
scoreboard objectives add slb_prev dummy

tellraw @a[tag=slb_admin] {"text":"[slb-return] datapack loaded","color":"dark_gray"}
