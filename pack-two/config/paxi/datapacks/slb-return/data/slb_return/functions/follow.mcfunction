# Executed as a player standing in a safe dimension. Move their home marker onto them.
#
# The player is tagged slb_anchor for the length of this call (only ever one player at a time, since
# tick calls this once per player in sequence). The marker is teleported to that player by tag, so
# it inherits the player's dimension and exact double-precision position - including when the player
# has walked into the Nether, Twilight Forest, Aether, etc. `@a[...]` is used, not `@n`/`@p`, because
# nearest-selectors are scoped to the executor's dimension and would lose a cross-dimension player.
scoreboard players operation #pid slb_pid = @s slb_pid
tag @s add slb_anchor
execute as @e[tag=slb_home] if score @s slb_pid = #pid slb_pid run tp @s @a[tag=slb_anchor,limit=1]
tag @s remove slb_anchor
