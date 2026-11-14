# Executed as the other allowlisted player. They count as finishing together if their last
# finished run was at most 1200 ticks (60 seconds) ago.
scoreboard players operation #d slb_wf_at = #now slb_wf_at
scoreboard players operation #d slb_wf_at -= @s slb_wf_at
execute if score #d slb_wf_at matches 0..1200 run tag @s add slb_wf_partner
