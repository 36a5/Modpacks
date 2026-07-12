# A Gate entity exists from the moment the mod decides to place one — the mod's own
# announcement is sent by the entity's own spawn code — so scanning for untagged Gates
# catches every Gate on the tick it appears.
execute as @e[type=#slb_gates:gates,tag=!slb_seen] at @s run function slb_gates:new_gate

# Serve anyone who clicked a [TELEPORT] button since the last tick.
execute as @a[scores={slb_gate=1..}] run function slb_gates:teleport

# /trigger stays disabled until re-enabled, once per use. Cheap for a handful of players.
scoreboard players enable @a slb_gate
