# Lobby FPS / Background Load Report

## Checked Profile
- `C:\Users\user\AppData\Roaming\ModrinthApp\profiles\TacZ_Roguelike_v0.5.0-beta`

## Findings
- The active profile was not capped by vanilla FPS settings:
  - `enableVsync:false`
  - `maxFps:250`
- The modpack override was still capped for distribution:
  - `enableVsync:true`
  - `maxFps:120`
- The active shader stack is enabled:
  - `shaderPack=MakeUp-UltraFast-9.4c.zip`
  - `enableShaders=true`
  - `maxShadowRenderDistance=24`
- Logs show Oculus rebuilding its pipeline when entering the lobby dimension:
  - `Reloading pipeline on dimension change: null => tac_rogue:lobby_dimension`
  - `Creating pipeline for dimension tac_rogue:lobby_dimension`
- TacZ asset indexing is visible during resource reload, but does not appear to remain active in the background:
  - `ClientIndexManager.reload took 679 ms (201 guns, 188 attachments, 1518 priority sounds)`
  - No repeated long-running preload loop was visible in the latest log sample.
- Repeated autosave logs are visible for multiple dimensions. These can cause small periodic stutters, but they do not explain a stable 60fps lobby by themselves.

## Likely Cause
- Stable lobby FPS around 60 is most likely GPU/render-bound from shader rendering, shadow distance, render distance, cloud rendering, and lobby geometry.
- The previous dynamic flashlight mixin was also a potential render-path tax. It has been changed so the hook returns immediately when no dynamic lights are active, and flashlight lighting only runs in `tac_rogue:rogue_dimension`.

## Applied Config Changes
- Current Modrinth profile:
  - `renderDistance: 12 -> 8`
  - `simulationDistance: 12 -> 6`
  - `entityDistanceScaling: 1.0 -> 0.75`
  - `renderClouds: true -> false`
  - `mipmapLevels: 4 -> 2`
  - `maxShadowRenderDistance: 24 -> 12`
- Modpack overrides:
  - `enableVsync: true -> false`
  - `maxFps: 120 -> 250`
  - Same render/shadow reductions as the active profile.

## Next Diagnostic Step
- Restart the client so the options and Oculus shader config are fully applied.
- Test lobby FPS with:
  - MakeUp UltraFast ON
  - Shaders OFF
  - PotatoShaders ON
- If Shaders OFF jumps far above 60, the bottleneck is shader/GPU-side rather than TacZ Roguelike tick logic.
