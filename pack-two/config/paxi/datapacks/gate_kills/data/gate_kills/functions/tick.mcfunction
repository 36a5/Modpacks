# Runs every tick (cheap: a handful of players, simple scoreboard ops).
# 1) Initialise any player we have not seen yet so their first delta is 0.
execute as @a unless score @s pk_init matches 1 run function gate_kills:init_player
# 2) For players currently inside a Solo Leveling Gate dimension, add the kills
#    they made since last tick to their Gate-kill total.
execute as @a[predicate=gate_kills:in_gate] run function gate_kills:add_delta
# 3) Snapshot each player's kill count for next tick's delta.
#
# This has to run per-player. `scoreboard players operation @a pk_prev = @a pk_kills` reads as if
# it pairs each player with themselves, but the command applies every *target* against every
# *source* in a nested loop — so with two or more players online, everyone's pk_prev ends up
# holding whichever player happened to be iterated last, not their own count. add_delta then
# computes (my kills - someone else's kills), and anyone above that player is credited the
# difference again on every single tick they stand in a Gate. It only ever looked right because
# a nested loop over one player is self-assignment.
execute as @a run scoreboard players operation @s pk_prev = @s pk_kills
