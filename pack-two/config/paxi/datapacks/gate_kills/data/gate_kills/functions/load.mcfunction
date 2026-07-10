# Shabab 2 - Solo Leveling Gate kill tracker
# pk_kills : vanilla lifetime mob-kill counter (auto-updated by the game)
# pk_total : cumulative mobs killed inside a Gate dimension (the leaderboard number)
# pk_prev  : last tick's pk_kills, used to compute the per-tick kill delta
# pk_delta : scratch
# pk_init  : 1 once a player has been initialised (so their first delta is 0)
scoreboard objectives add pk_kills minecraft.custom:minecraft.mob_kills
scoreboard objectives add pk_total dummy {"text":"Gate Kills"}
scoreboard objectives add pk_prev dummy
scoreboard objectives add pk_delta dummy
scoreboard objectives add pk_init dummy
tellraw @a[tag=slb_admin] {"text":"[gate_kills] datapack loaded","color":"dark_gray"}
