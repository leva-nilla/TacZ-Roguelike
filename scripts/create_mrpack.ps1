Add-Type -AssemblyName System.IO.Compression.FileSystem

$workspaceRoot = Split-Path -Parent $PSScriptRoot
Set-Location $workspaceRoot

# バージョンは gradle.properties から自動取得（一元管理）
$modVersion = ""
foreach ($line in Get-Content "tac_rogue_mod/gradle.properties") {
    if ($line -match "^mod_version=(.+)$") { $modVersion = $Matches[1] }
}
$outputFile = Join-Path $workspaceRoot "releases/TacZ_Roguelike_v${modVersion}.mrpack"
$sourceDir = Join-Path $workspaceRoot "tac_rogue_modpack"

Write-Host "Version: $modVersion" -ForegroundColor Yellow

if (Test-Path $outputFile) {
    Remove-Item $outputFile -Force
}

# --- 自動ビルド & 同期フェーズ ---
Write-Host "Building tac_rogue mod..." -ForegroundColor Cyan
Push-Location "tac_rogue_mod"
./gradlew assemble :tacz_startup_helper:assemble
Pop-Location

# 最新の JAR を取得して overrides へコピー
$modBuildDir = "tac_rogue_mod/build/libs"
$helperBuildDir = "tac_rogue_mod/tacz_startup_helper/build/libs"
$targetDir = "tac_rogue_modpack/overrides/mods"
$latestJar = Get-ChildItem -Path "$modBuildDir/tac_rogue-*.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
$latestHelperJar = Get-ChildItem -Path "$helperBuildDir/tacz_startup_helper-*.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1

if ($latestJar) {
    Remove-Item -Path "$targetDir/tac_rogue-*.jar" -Force -ErrorAction SilentlyContinue
    Write-Host "Syncing latest JAR: $($latestJar.Name)" -ForegroundColor Green
    Copy-Item -Path $latestJar.FullName -Destination "$targetDir/$($latestJar.Name)" -Force
} else {
    Write-Error "Failed to find built JAR in $modBuildDir"
    exit 1
}
if ($latestHelperJar) {
    Remove-Item -Path "$targetDir/tacz_startup_helper-*.jar" -Force -ErrorAction SilentlyContinue
    Write-Host "Syncing startup helper JAR: $($latestHelperJar.Name)" -ForegroundColor Green
    Copy-Item -Path $latestHelperJar.FullName -Destination "$targetDir/$($latestHelperJar.Name)" -Force
} else {
    Write-Error "Failed to find built helper JAR in $helperBuildDir"
    exit 1
}
# -----------------------------

$env:TAC_ROGUE_MRPACK_SOURCE = (Resolve-Path $sourceDir).Path
$env:TAC_ROGUE_MRPACK_OUTPUT = (Join-Path (Resolve-Path (Split-Path $outputFile -Parent)).Path (Split-Path $outputFile -Leaf))

$profileSyncScript = @'
import os
import zipfile
from pathlib import Path

source = Path(os.environ["TAC_ROGUE_MRPACK_SOURCE"])
output = Path(os.environ["TAC_ROGUE_MRPACK_OUTPUT"])
if output.exists():
    output.unlink()

with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as zf:
    zf.write(source / "modrinth.index.json", "modrinth.index.json")
    for path in sorted((source / "overrides").rglob("*")):
        if path.is_file():
            zf.write(path, path.relative_to(source).as_posix())
'@ | python -

if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to create mrpack archive."
    exit 1
}

Write-Host "Successfully created $outputFile with forward slashes and Dual Manifests."

# --- 自動デプロイフェーズ ---
$workspaceRoot = Split-Path -Parent $PSScriptRoot
Set-Location $workspaceRoot
$profileRoot = "$env:APPDATA\ModrinthApp\profiles"
$preferredProfiles = @(
    "$profileRoot\TacZ_Roguelike_v${modVersion}",
    "$profileRoot\TacZ_Roguelike"
)
$modrinthProfile = $null
foreach ($profile in $preferredProfiles) {
    if (Test-Path $profile) {
        $modrinthProfile = $profile
        break
    }
}
if (-not $modrinthProfile) {
    # フォルダ名にバージョンが含まれる場合も検索
    $candidates = Get-ChildItem -Path $profileRoot -Directory -Filter "TacZ*" -ErrorAction SilentlyContinue | Sort-Object Name
    if ($candidates) {
        $modrinthProfile = $candidates[0].FullName
    }
}

if (Test-Path $modrinthProfile) {
    # mrpack をプロファイルルートにコピー
    Copy-Item -Path $outputFile -Destination (Join-Path $modrinthProfile (Split-Path $outputFile -Leaf)) -Force
    Write-Host "Deployed $outputFile -> $modrinthProfile" -ForegroundColor Green

    # 既存プロファイルへ overrides を反映。options.txt は全上書きせず、配布側で固定したいキーだけ同期する。
$profileSyncScript = @'
import os
import shutil
import sys
from pathlib import Path

profile = Path(sys.argv[1])
overrides = Path(sys.argv[2])
resourcepacks = overrides / "resourcepacks"
if resourcepacks.exists():
    target_root = profile / "resourcepacks"
    target_root.mkdir(parents=True, exist_ok=True)
    for pack in sorted(p for p in resourcepacks.iterdir() if p.is_dir()):
        target = target_root / pack.name
        if target.exists():
            shutil.rmtree(target)
        shutil.copytree(pack, target)
        print(f"Synced resourcepack -> {target}")

betterf3_config = overrides / "config" / "betterf3.toml"
if betterf3_config.exists():
    target = profile / "config" / "betterf3.toml"
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(betterf3_config, target)
    print(f"Synced BetterF3 config -> {target}")

fixed_keys = {
    "resourcePacks",
    "incompatibleResourcePacks",
    "narrator",
    "onboardAccessibility",
    "tutorialStep",
    "skipMultiplayerWarning",
    "toggleCrouch",
    "key_key.sneak",
    "key_key.sprint",
    "key_key.drop",
    "key_key.swapOffhand",
    "key_key.saveToolbarActivator",
    "key_key.loadToolbarActivator",
    "key_key.jade.show_details",
    "soundCategory_master",
    "soundCategory_music",
    "soundCategory_record",
    "soundCategory_weather",
    "soundCategory_block",
    "soundCategory_hostile",
    "soundCategory_neutral",
    "soundCategory_player",
    "soundCategory_ambient",
    "soundCategory_voice",
}
prefixes = (
    "key_key.tacz.",
    "key_key.tac_rogue.",
    "key_key.lrtactical.",
    "key_iris.",
    "key_key.jade.",
    "key_key.leawind_third_person.",
    "key_key.journeymap.",
    "key_key.yes_steve_model.",
)
override_options = overrides / "options.txt"
profile_options = profile / "options.txt"
if override_options.exists():
    desired = {}
    for line in override_options.read_text(encoding="utf-8").splitlines():
        key, sep, _ = line.partition(":")
        if sep and (key in fixed_keys or key.startswith(prefixes)):
            desired[key] = line

    lines = profile_options.read_text(encoding="utf-8").splitlines() if profile_options.exists() else []
    out = []
    seen = set()
    for line in lines:
        key, sep, _ = line.partition(":")
        if sep and key in desired:
            out.append(desired[key])
            seen.add(key)
        else:
            out.append(line)
    for key in sorted(desired):
        if key not in seen:
            out.append(desired[key])
    profile_options.write_text("\n".join(out) + "\n", encoding="utf-8")
    print(f"Merged fixed options -> {profile_options}")
'@
    $profileSyncScript | python - $modrinthProfile "C:\Users\user\Documents\TacZ_Roguelike_Workspace\tac_rogue_modpack\overrides"

    # 最新の JAR をプロファイルの mods フォルダにもコピー
    $profileMods = "$modrinthProfile\mods"
    if (Test-Path $profileMods) {
        Remove-Item -Path "$profileMods\tac_rogue-*.jar" -Force -ErrorAction SilentlyContinue
        Remove-Item -Path "$profileMods\tacz_startup_helper-*.jar" -Force -ErrorAction SilentlyContinue
        Remove-Item -Path "$profileMods\BetterF3-*.jar" -Force -ErrorAction SilentlyContinue
        Copy-Item -Path $latestJar.FullName -Destination "$profileMods\$($latestJar.Name)" -Force
        Write-Host "Synced JAR -> $profileMods\$($latestJar.Name)" -ForegroundColor Green
        Copy-Item -Path $latestHelperJar.FullName -Destination "$profileMods\$($latestHelperJar.Name)" -Force
        Write-Host "Synced helper JAR -> $profileMods\$($latestHelperJar.Name)" -ForegroundColor Green
        $betterF3Jar = Join-Path $workspaceRoot "tmp\betterf3\BetterF3-7.0.2-Forge-1.20.1.jar"
        if (-not (Test-Path $betterF3Jar)) {
            New-Item -ItemType Directory -Path (Split-Path $betterF3Jar -Parent) -Force | Out-Null
            Invoke-WebRequest -Uri "https://cdn.modrinth.com/data/8shC1gFX/versions/xo6HmgWj/BetterF3-7.0.2-Forge-1.20.1.jar" -OutFile $betterF3Jar
        }
        if (Test-Path $betterF3Jar) {
            Copy-Item -Path $betterF3Jar -Destination "$profileMods\BetterF3-7.0.2-Forge-1.20.1.jar" -Force
            Write-Host "Synced BetterF3 -> $profileMods\BetterF3-7.0.2-Forge-1.20.1.jar" -ForegroundColor Green
        }
    }
} else {
    Write-Host "Modrinth profile not found at: $modrinthProfile (skipping deploy)" -ForegroundColor Yellow
}
