# Boss AI Texture Upgrade Prompt

## Summary
- 現在の手続き生成ボステクスチャを、AI生成ベースの高解像度テクスチャへ置き換える。
- Minecraftらしさは必須にしない。
- ただしEntityモデルへ貼るため、生成画像をそのまま貼るのではなく、ボスモデルのUV領域に合わせて再配置する。

## Target Assets
- `assets/tac_rogue/textures/entity/boss/breacher.png`
- `assets/tac_rogue/textures/entity/boss/commander.png`
- `assets/tac_rogue/textures/entity/boss/void_warden.png`
- `assets/tac_rogue/textures/entity/boss/pyro.png`
- `assets/tac_rogue/textures/entity/boss/leviathan.png`

## Resolution
- 最終ゲーム内テクスチャは `256x256` を基本にする。
- 理由:
  - 現在のモデルUVは `128x128` ベースだが、256なら細部を出しつつ軽量。
  - 512以上は見た目は良いが、現時点のボス数/モデル密度では費用対効果が低い。
- AI生成の素材は大きめの正方形で作り、必要部分を縮小/再配置する。

## Generation Strategy
- AI生成で「完成済みのMinecraft UVマップ」を直接狙わない。
  - UV整合が崩れやすいため。
- 代わりに、各ロール用の装甲テクスチャ/顔パネル/発光ラインの素材画像を生成する。
- 生成画像から以下の要素を抽出・再配置する。
  - 頭部/顔面装甲
  - 胴体装甲
  - 肩/腕装甲
  - 脚部装甲
  - 背面装備
  - ロール別の発光ライン/識別色

## Visual Direction
### BREACHER
- Heavy assault exosuit.
- Charcoal metal, worn ballistic armor, red warning lights.
- Brutal, close-range, breaching specialist.

### COMMANDER
- Tactical officer power armor.
- Olive drab, black ceramic armor, gold rank accents.
- Command antenna / control unit feel.

### VOID_WARDEN
- Non-Minecraft sci-fi horror armor.
- Black composite armor, violet void stains, cyan glowing face.
- Warden replacement, but not a vanilla Warden texture.

### PYRO
- Burned hazard armor.
- Blackened metal, orange heat vents, scorched fuel tanks.
- Fire-resistant suit impression.

### LEVIATHAN
- Aquatic heavy armor.
- Deep teal, dark metal, pressure-suit plating, pale blue glow.
- Heavy, durable, water/pressure theme.

## Constraints
- No text, logos, watermark, readable letters, or UI marks in generated images.
- Avoid pure flat colors; use scratches, layered plates, vents, grime, and material variation.
- Faces must be readable from front view.
- Back and sides must not be blank.
- Final texture must remain opaque PNG.
- Final UV output must not rely on transparent background.

## Implementation Steps
1. Generate one AI texture/concept source per boss role.
2. Save sources under:
   - `tmp/imagegen/boss_textures/`
3. Convert each source into a `256x256` UV-like texture atlas matching `TacRogueBossModel`.
4. Replace the current five boss textures.
5. Keep renderer paths unchanged.
6. Build the mod.
7. Verify jar contains the new textures.
8. Copy jar to the Modrinth profile.

## Validation
- `.\gradlew.bat build` succeeds.
- Jar includes all five replacement texture files.
- File dimensions are `256x256`.
- Texture files are non-empty and visually distinct by role.
- In-game check:
  - Debug GUI can spawn all five roles.
  - Each role uses a different texture.
  - Front face, body, arms, legs, and back have visible detail.

## Assumptions
- User accepts non-Minecraft-like texture style.
- Exact AI output may vary; final integration prioritizes readable role identity and UV stability over perfect concept-art fidelity.
- The model remains `TacRogueBossModel`; this task changes textures, not model geometry.
