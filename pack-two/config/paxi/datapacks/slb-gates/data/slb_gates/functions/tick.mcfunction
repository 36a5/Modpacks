# A Gate entity exists from the moment the mod decides to place one, so scanning for
# untagged Gates catches every Gate on the tick it appears.
execute as @e[type=#slb_gates:gates,tag=!slb_seen] at @s run function slb_gates:new_gate
