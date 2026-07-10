# Executed as a player who is inside a Gate dimension.
# delta = kills since last tick; if positive, credit it to their Gate-kill total.
scoreboard players operation @s pk_delta = @s pk_kills
scoreboard players operation @s pk_delta -= @s pk_prev
execute if score @s pk_delta matches 1.. run scoreboard players operation @s pk_total += @s pk_delta
