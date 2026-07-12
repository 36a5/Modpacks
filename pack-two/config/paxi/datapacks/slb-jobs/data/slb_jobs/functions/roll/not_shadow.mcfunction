# They walked in as Shadow Monarch: coin-flip between the other two.
scoreboard players set @s sl_r 3
execute if predicate slb_jobs:one_half run scoreboard players set @s sl_r 2
function slb_jobs:roll/apply
