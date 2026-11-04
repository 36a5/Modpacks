# A real kick. Auth's own auth:kick is not one: it fires two billion forced particles at the
# player, which freezes their game on "Joining world..." instead of disconnecting them, and
# keeps freezing it on every rejoin while the timer that triggered it stays over the limit.
# kick works from a function because server.properties sets function-permission-level=4.
scoreboard players reset @s shabab.wait
scoreboard players reset @s shabab.gatewait
scoreboard players reset @s shabab.tries
kick @s Took too long, or too many wrong tries. Rejoin and type the command shown on screen.
