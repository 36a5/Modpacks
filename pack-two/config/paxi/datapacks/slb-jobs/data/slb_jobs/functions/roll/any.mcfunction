# Uniform over the three jobs. A random predicate is re-rolled every time it is
# evaluated, so `if predicate` / `unless predicate` on the same predicate are two
# independent coins and can both fire or neither. Every branch below therefore reads the
# predicate once and parks the answer in sl_r.
#   1/3            -> 1
#   2/3 * 1/2      -> 2
#   the rest       -> 3
scoreboard players set @s sl_r 3
execute if predicate slb_jobs:one_third run scoreboard players set @s sl_r 1
execute if score @s sl_r matches 3 if predicate slb_jobs:one_half run scoreboard players set @s sl_r 2
function slb_jobs:roll/apply
