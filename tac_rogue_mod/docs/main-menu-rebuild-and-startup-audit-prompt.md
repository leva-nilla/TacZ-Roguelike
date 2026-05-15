# Main Menu Rebuild And Startup Audit

## Summary
- Remove the old main menu branding path that overrides vanilla `minecraft.png` through an external resource pack.
- Rebuild the title screen overlay from a minimal, non-overlapping in-mod renderer.
- Keep the first-launch language selector, but prevent it from causing unnecessary reload work after the user has already selected a language.
- Record the current startup bottleneck from logs so future optimization work is grounded in evidence.

## Current Findings
- The active profile enables `file/tacz_branding`, which replaces `assets/minecraft/textures/gui/title/minecraft.png`.
- That external resource pack is why the old central TacZ logo appears even after the mod-side title overlay was changed.
- The latest startup log reports `Game took 100.901 seconds to start`.
- The slow path is concentrated around TacZ gun pack/resource loading:
  - large TacZ addon folders under the profile `tacz` directory,
  - resource manager reload with `tacz_resources`, `mod_resources`, `file/tacz_branding`, and `file/tac_rogue_items`,
  - ModernUI font/emoji setup and TacZ gun pack scanning.

## Main Menu Rebuild
- Delete/disable `tacz_branding` from:
  - current Modrinth profile resource packs,
  - modpack overrides,
  - workspace resource pack source.
- Remove `file/tacz_branding` from profile and modpack `options.txt`.
- Replace the current `MixinTitleScreen` implementation with a fresh renderer:
  - no custom `minecraft.png` replacement,
  - no large center logo image,
  - no bottom-left info panel,
  - no right-side panel unless it is guaranteed not to overlap,
  - draw only a compact tactical header over the vanilla logo/splash area,
  - leave vanilla buttons, Forge text, copyright/update text, and accessibility buttons unobstructed.
- Stop using `title_logo.png` for the title screen.

## Startup Mitigation
- Removing `tacz_branding` saves one resource pack and removes the old logo override.
- This will not eliminate the full startup cost because the heaviest work comes from TacZ addon scanning and large content mods.
- Do not remove weapon packs automatically in this task because they are gameplay content.

## Test Plan
- `.\gradlew.bat build` succeeds.
- Copied jar in the Modrinth profile matches the built jar.
- Profile `options.txt` no longer references `file/tacz_branding`.
- Profile `resourcepacks\tacz_branding` no longer exists.
- Title screen no longer shows the old central TacZ logo or overlapping bottom-left custom panel.
