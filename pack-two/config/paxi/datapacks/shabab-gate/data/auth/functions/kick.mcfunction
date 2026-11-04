# Overrides the Auth mod's auth:kick, which floods the player with two billion forced
# particles and freezes their game instead of kicking them. Auth still calls this after
# too many wrong passwords. If this pack loads below the mod's data, the override simply
# does not apply and shabab_gate:kick still covers every timer and join-code kick.
function shabab_gate:kick
