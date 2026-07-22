# Shabab 2 - Endgame overhaul
#
# The vanilla Ender Dragon has one script: circle, perch, breathe, die. This turns it into a
# staged fight that escalates as its health falls, without touching any mod or the jar:
#
#   Phase 1 (100%..75%)  vanilla behaviour.
#   Phase 2 ( 75%)       first blood: a small wave of Shadow adds and a short heal burst.
#   Phase 3 ( 50%)       enrage: the dragon speeds up, a bigger wave spawns, and it begins a
#                        periodic Void barrage that rains fire on nearby players.
#   Phase 4 ( 25%)       second wind: the dragon heals hard once and summons the Herald of the
#                        Void, a netherite mini-boss, to finish you off.
#
# Thresholds are read from the dragon's OWN max health on first sight, so it still works if another
# mod has rebalanced the dragon's health pool.
#
# ed_phase : 0 unseen, then 0..3 as the fight escalates (held on the dragon).
# ed_hp    : the dragon's current health this tick.
# ed_max   : the dragon's health the first tick we saw it (its effective maximum).
# ed_t2/3/4: the 75% / 50% / 25% health thresholds, precomputed from ed_max.
# ed_atk   : barrage cooldown, counts down each tick once enraged.
# ed_init  : 1 once a dragon has been measured.
scoreboard objectives add ed_phase dummy
scoreboard objectives add ed_hp dummy
scoreboard objectives add ed_max dummy
scoreboard objectives add ed_t2 dummy
scoreboard objectives add ed_t3 dummy
scoreboard objectives add ed_t4 dummy
scoreboard objectives add ed_atk dummy
scoreboard objectives add ed_init dummy

# Constant holders for the threshold percentages.
scoreboard objectives add ed_k dummy
scoreboard players set #25 ed_k 25
scoreboard players set #50 ed_k 50
scoreboard players set #75 ed_k 75
scoreboard players set #100 ed_k 100

tellraw @a[tag=slb_admin] {"text":"[slb-endgame] datapack loaded","color":"dark_gray"}
