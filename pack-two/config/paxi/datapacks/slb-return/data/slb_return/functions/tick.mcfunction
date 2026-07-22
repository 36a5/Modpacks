# 1. Any player we have not seen: hand out an id and spawn their personal home marker.
execute as @a[tag=!slb_has] run function slb_return:setup

# 2. Who is inside a Gate / Punishment dimension right now.
scoreboard players set @a slb_solo 0
execute as @a[predicate=slb_return:in_solo] run scoreboard players set @s slb_solo 1

# 3. In a safe dimension, and not on the tick we just teleported them home (prev=0): keep the marker
#    glued to the player. This is what makes the recorded spot the *exact* place they last stood.
execute as @a[scores={slb_solo=0,slb_prev=0}] at @s run function slb_return:follow

# 4. Entered a Gate / Punishment dimension this tick (safe last tick -> solo now): the marker is
#    still standing at the doorstep. Freeze it and force-load its chunk so it lives while they raid.
execute as @a[scores={slb_solo=1,slb_prev=0}] run function slb_return:enter

# 5. Left a Gate / Punishment dimension this tick (solo last tick -> safe now): the mod has just
#    dropped the player at its own overworld point. Override it - send them back to their marker.
execute as @a[scores={slb_solo=0,slb_prev=1}] run function slb_return:restore

# 6. Remember this tick's status for next tick. Per-player via `as @s`: `operation @a X = @a Y`
#    would cross-assign between players (see gate_kills:tick for the same trap).
execute as @a run scoreboard players operation @s slb_prev = @s slb_solo
