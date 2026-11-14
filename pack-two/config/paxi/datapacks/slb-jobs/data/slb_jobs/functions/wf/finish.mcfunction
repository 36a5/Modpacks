# An allowlisted player without White Flames finished a run. Count it, stamp the time, and look
# for the other allowlisted player having finished within the last 60 seconds.
scoreboard players add @s slb_wf_try 1
execute store result score #now slb_wf_at run time query gametime
scoreboard players operation @s slb_wf_at = #now slb_wf_at
tag @s add slb_wf_self
execute as @a[tag=slb_wf_track2,tag=!slb_wf_self] if score @s slb_wf_at matches -2147483648..2147483647 run function slb_jobs:wf/partner_check
scoreboard players set #big slb_wf_at 0
execute if score @s slb_wf_try matches 7.. run scoreboard players set #big slb_wf_at 1
execute if entity @a[tag=slb_wf_partner,scores={slb_wf_try=7..}] run scoreboard players set #big slb_wf_at 1
execute if entity @a[tag=slb_wf_partner] if score #big slb_wf_at matches 1 run function slb_jobs:wf/award_pair
tag @a remove slb_wf_partner
tag @s remove slb_wf_self
