# Runs every tick (cheap: a handful of players, simple scoreboard ops).
# 1) Initialise any player we have not seen yet so their first delta is 0.
execute as @a unless score @s pk_init matches 1 run function gate_kills:init_player
# 2) For players currently inside a Solo Leveling Gate dimension, add the kills
#    they made since last tick to their Gate-kill total.
execute as @a[predicate=gate_kills:in_gate] run function gate_kills:add_delta
# 3) Snapshot everyone's kill count for next tick's delta.
scoreboard players operation @a pk_prev = @a pk_kills
