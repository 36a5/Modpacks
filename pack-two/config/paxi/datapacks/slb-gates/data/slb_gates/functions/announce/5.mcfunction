# Slot 5. Identical to its siblings except for the slot number baked into the button - see
# new_gate.mcfunction for why that number cannot be a variable.
#
# Executed as the Gate entity, so {"selector":"@s"} prints the Gate's own name - "Gate - Ant Nest",
# "Gate - Goblin Sewers", "D Rank Gate" - which is how Solo Leveling distinguishes one Gate's
# contents from another's. No per-type branching needed.
#
# One line, deliberately. The old message wrapped onto three, which is what buried the button.
# "It closes in 4 days" now lives in the button's tooltip instead of costing a whole line.
tellraw @a [{"text":"⚡ ","color":"red","bold":true},{"selector":"@s","color":"gold","bold":true},{"text":"  X ","color":"dark_gray"},{"score":{"name":"#x","objective":"slb_pos"},"color":"white"},{"text":" Y ","color":"dark_gray"},{"score":{"name":"#y","objective":"slb_pos"},"color":"white"},{"text":" Z ","color":"dark_gray"},{"score":{"name":"#z","objective":"slb_pos"},"color":"white"},{"text":"  "},{"text":"[TP]","color":"aqua","bold":true,"clickEvent":{"action":"run_command","value":"/trigger slb_gate set 5"},"hoverEvent":{"action":"show_text","contents":[{"text":"Teleport to this Gate\n","color":"aqua"},{"text":"It closes in 4 days.","color":"gray","italic":true}]}}]
