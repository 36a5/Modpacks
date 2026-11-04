# Executed as the Gate entity, so {"selector":"@s"} prints the Gate's own name ("Gate - Ant Nest",
# "Gate - Goblin Sewers", "D Rank Gate"), which is how Solo Leveling tells one Gate's contents
# from another's.
tellraw @a [{"text":"⚡ ","color":"red","bold":true},{"selector":"@s","color":"gold","bold":true},{"text":"  X ","color":"dark_gray"},{"score":{"name":"#x","objective":"slb_pos"},"color":"white"},{"text":" Y ","color":"dark_gray"},{"score":{"name":"#y","objective":"slb_pos"},"color":"white"},{"text":" Z ","color":"dark_gray"},{"score":{"name":"#z","objective":"slb_pos"},"color":"white"},{"text":"  closes in 4 days","color":"gray","italic":true}]
