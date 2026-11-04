# Runs once for a player who has just joined again. The login and join-code timers count how
# long *this visit* has been waiting, so they start from zero on every join. Before this they
# carried over between visits: a player who spent 10 minutes on the login screen in total,
# across several sessions, hit the limit the moment they joined and was frozen every time.
scoreboard players reset @s shabab.wait
scoreboard players reset @s shabab.gatewait
scoreboard players reset @s shabab.left
