import re

# Fix RogueInventoryScreen switch block
path = 'c:/Users/user/Documents/TacZ_Roguelike_Workspace/tac_rogue_mod/src/main/java/com/levanilla/rogue/client/RogueInventoryScreen.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

lines = content.split('\n')
in_switch = False
switch_start = -1
switch_end = -1
for i, line in enumerate(lines):
    if 'String desc = switch (item.category)' in line:
        switch_start = i
        in_switch = True
    if in_switch and '};' in line:
        switch_end = i
        break

print(f"Switch block: lines {switch_start+1} to {switch_end+1}")

if switch_start >= 0 and switch_end >= 0:
    new_switch_lines = [
        '        String desc = switch (item.category) {',
        '            case PISTOL -> "Sidearm. Light and fast draw.";',
        '            case RIFLE -> "Primary assault rifle. High versatility.";',
        '            case SMG -> "Close-range suppression. High fire rate.";',
        '            case SHOTGUN -> "Devastating at close range. Slow reload.";',
        '            case SNIPER -> "Long-range precision. High scope magnification.";',
        '            case LMG -> "Suppressive fire with large ammo capacity.";',
        '            case MELEE -> "Close combat weapon. No ammo needed.";',
        '            case ATTACHMENT -> "Attach to weapons to boost performance.";',
        '            case AMMO -> "Ammo for matching weapons x32.";',
        '            case SPECIAL -> "Upgrade that changes gameplay.";',
        '        };',
    ]
    lines[switch_start:switch_end+1] = new_switch_lines
    print(f"Replaced switch block with {len(new_switch_lines)} lines")

# Fix the tooltip desc line and not-enough-gold line after switch
for i, line in enumerate(lines):
    if 'tooltip.add(Component.literal(' in line and 'desc' in line and i > switch_start:
        lines[i] = '        tooltip.add(Component.literal("\\u00A78" + desc));'
        print(f"Fixed tooltip desc line at {i+1}")
        break

for i, line in enumerate(lines):
    if '!canAfford' in line and 'tooltip.add' in line:
        lines[i] = '        if (!canAfford) tooltip.add(Component.literal("\\u00A7cNot enough gold!"));'
        print(f"Fixed not-enough-gold line at {i+1}")
        break

content2 = '\n'.join(lines)
with open(path, 'w', encoding='utf-8', newline='\n') as f:
    f.write(content2)
print("Fixed RogueInventoryScreen")

# Fix ShopService BOM
path2 = 'c:/Users/user/Documents/TacZ_Roguelike_Workspace/tac_rogue_mod/src/main/java/com/levanilla/rogue/core/service/ShopService.java'
with open(path2, 'rb') as f:
    data = f.read()
if data.startswith(b'\xef\xbb\xbf'):
    with open(path2, 'wb') as f:
        f.write(data[3:])
    print("Fixed ShopService BOM")
else:
    print("No BOM in ShopService")
