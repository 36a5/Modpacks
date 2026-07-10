# Hard-wipe every player's Gate-kill total. Optional: the Discord bot ranks by weekly
# gain (snapshot each Sunday) so it "resets weekly" without this. Provided for admins.
# pk_prev / pk_init are left intact so tracking continues cleanly afterwards.
scoreboard players reset * pk_total
tellraw @a {"text":"[Gate Kills] weekly totals reset","color":"aqua"}
