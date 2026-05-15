# Mrpack Build Prompt

## Goal
Create a Modrinth `.mrpack` from the current TacZ Roguelike workspace for local playtesting.

## Source Layout
- Mod source: `tac_rogue_mod`
- Modpack source: `tac_rogue_modpack`
- Modpack manifest: `tac_rogue_modpack/modrinth.index.json`
- Overrides: `tac_rogue_modpack/overrides`
- Output directory: `releases`

## Build Steps
1. Build the latest TacZ Roguelike jar from `tac_rogue_mod`.
2. Copy the latest `tac_rogue-*.jar` into `tac_rogue_modpack/overrides/mods`.
3. Create `releases/TacZ_Roguelike_v<mod_version>.mrpack` using the version from `tac_rogue_mod/gradle.properties`.
4. Include only valid Modrinth pack contents:
   - `modrinth.index.json`
   - `overrides/**`
5. Exclude development helper files such as `check_urls.py` and `mods_list.txt`.
6. Verify that the generated archive contains forward-slash paths.

## Constraints
- Do not publish to Modrinth.
- Do not delete unrelated workspace files.
- Do not modify gameplay code while packaging.
- Keep the existing `modrinth.index.json` dependency list unless packaging fails because of manifest format issues.

## Verification
- `./gradlew.bat build` or the existing packaging script build step must succeed.
- The `.mrpack` file must exist under `releases`.
- The archive must contain `modrinth.index.json` at the root.
- The archive must contain the current `tac_rogue-*.jar` under `overrides/mods`.
