# They walked in as Frost Monarch: coin-flip between the other two.
scoreboard players set @s sl_r 2
execute if predicate slb_jobs:one_half run scoreboard players set @s sl_r 1
function slb_jobs:roll/apply
