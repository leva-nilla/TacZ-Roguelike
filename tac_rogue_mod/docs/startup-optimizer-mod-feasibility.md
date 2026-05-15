# Startup Optimizer Mod Feasibility

## Summary
- A separate optimization mod is possible, but it should be treated as a staged compatibility mod.
- The current startup cost is mostly TacZ gunpack/resource loading, not TacZ Roguelike title/menu rendering.
- The safest first version should measure and reduce redundant work. It should not skip required TacZ assets.

## Evidence From Current Profile
- Latest observed startup line: `Game took 100.901 seconds to start`.
- Log sequence:
  - `12:55:05` TacZ starts scanning gun packs.
  - `12:55:06` ResourceManager reload starts.
  - `12:55:44` startup finishes.
- The profile `tacz` directory currently contains about 7103 files / 294 MB.
- Largest gunpack folders:
  - `lradd_default_gun`: 1356 files / 91.4 MB
  - `elitex_quality_guns`: 1115 files / 83.2 MB
  - `tacz_default_gun`: 2939 files / 69.3 MB
  - `daffas`: 1075 files / 30.1 MB

## TacZ Loading Path
- `com.tacz.guns.resource.GunPackLoader`
  - registered via `CommonRegistry.onAddPackFinders`
  - scans the game directory `tacz`
  - reads each `gunpack.meta.json`
  - wraps each gunpack as `PathPackResources`
  - returns a built-in pack named `tacz_resources`
- Resource reload then loads JSON, models, animations, scripts, sounds, pack info, and player animator data through TacZ managers.

## Separate Mod Strategy

### Stage 1: profiler/helper mod
- Add timestamped logging around:
  - `GunPackLoader.discoverExtensions`
  - `GunPackLoader.scanExtensions`
  - TacZ client/common reload listeners
  - `ResourceScanner.scanDirectory`
  - `ResourceScanner.scanDirectoryAll`
- Output per-manager timing and resource counts.
- Risk: low.

### Stage 2: gunpack layout optimizer
- Convert extracted gunpack directories into zip packs, or provide a command/tool to do so.
- Keep original content unchanged, only packaging changes.
- This may reduce filesystem traversal overhead on Windows.
- Risk: medium-low. Need verify TacZ accepts all zipped packs and asset paths.

### Stage 3: reload cache
- Cache parsed TacZ JSON/index data keyed by:
  - pack name,
  - file path,
  - size,
  - last modified time,
  - TacZ version,
  - modpack version.
- On unchanged startup, reuse cached parse results instead of re-reading every JSON.
- Risk: medium-high because TacZ managers keep internal object graphs and some client assets include runtime model/animation objects.

### Stage 4: lazy/heavy asset deferral
- Defer expensive models/animations/sounds until the gun is first viewed or used.
- Risk: high. This can cause stutter mid-game and can break shop/inventory previews.

## Recommendation
- Do not start with a full cache that skips TacZ reload internals.
- First build a small `tacz_startup_profiler` or fold the profiler into `tac_rogue` behind a config flag.
- Then test zipped gunpacks, because it is the fastest low-risk experiment.
- Only after timing data proves JSON parsing/model loading is the main cost should we write invasive Mixins.

## Accepted Implementation Scope
- Add a separate helper mod project named `tacz_startup_helper`.
- Stage 1:
  - Add profiling Mixins for TacZ gunpack discovery, resource JSON scans, sound preload, and export copy.
  - Logs use the `[TacZ Startup Helper]` prefix.
- Stage 2:
  - Remove eager `AttachmentDatabase.init()` from `TacRogue` construction.
  - Make `AttachmentDatabase` initialize on first public compatibility query.
  - Add elapsed-time logging to the database load.
- Stage 3:
  - Add a conservative export-copy skip for `GetJarResources.copyModDirectory(Class, String, Path, String)`.
  - Skip only when:
    - the target folder already exists,
    - the target folder contains `gunpack.meta.json`,
    - the source code location size/mtime and source path match the last successful copy marker.
  - If no marker exists, or source metadata changes, TacZ performs its normal copy.

## Test Plan
- Compare startup logs over three cold-ish launches:
  - baseline current profile,
  - after removing obsolete resource packs,
  - after zipped gunpack experiment.
- Record:
  - total startup time,
  - TacZ scan time,
  - ResourceManager reload time,
  - per TacZ manager time.
- Verify:
  - all shop weapons render,
  - quest rare rewards still generate,
  - reload/shoot animations still play,
  - third-person models still render,
  - no missing sound/model spam in `latest.log`.
