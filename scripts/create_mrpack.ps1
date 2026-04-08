Add-Type -AssemblyName System.IO.Compression.FileSystem

# バージョンは gradle.properties から自動取得（一元管理）
$modVersion = ""
foreach ($line in Get-Content "tac_rogue_mod/gradle.properties") {
    if ($line -match "^mod_version=(.+)$") { $modVersion = $Matches[1] }
}
$outputFile = "releases/TacZ_Roguelike_v${modVersion}.mrpack"
$sourceDir = "tac_rogue_modpack"

Write-Host "Version: $modVersion" -ForegroundColor Yellow

if (Test-Path $outputFile) {
    Remove-Item $outputFile -Force
}

# --- 自動ビルド & 同期フェーズ ---
Write-Host "Building tac_rogue mod..." -ForegroundColor Cyan
Push-Location "tac_rogue_mod"
./gradlew assemble
Pop-Location

# 最新の JAR を取得して overrides へコピー
$modBuildDir = "tac_rogue_mod/build/libs"
$targetDir = "tac_rogue_modpack/overrides/mods"
$latestJar = Get-ChildItem -Path "$modBuildDir/tac_rogue-*.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1

if ($latestJar) {
    Remove-Item -Path "$targetDir/tac_rogue-*.jar" -Force -ErrorAction SilentlyContinue
    Write-Host "Syncing latest JAR: $($latestJar.Name)" -ForegroundColor Green
    Copy-Item -Path $latestJar.FullName -Destination "$targetDir/$($latestJar.Name)" -Force
} else {
    Write-Error "Failed to find built JAR in $modBuildDir"
    exit 1
}
# -----------------------------

$zip = [System.IO.Compression.ZipFile]::Open($outputFile, "Create")

try {
    # modrinth.index.json と overrides/ のみ含める（開発用ファイルを除外）
    $excludeNames = @("check_urls.py", "mods_list.txt")
    $files = Get-ChildItem -Path $sourceDir -Recurse | Where-Object {
        -not $_.PSIsContainer -and $excludeNames -notcontains $_.Name
    }
    foreach ($file in $files) {
        # USE FORWARD SLASHES (Modrinth standard)
        $relPath = $file.FullName.Substring((Get-Item $sourceDir).FullName.Length + 1).Replace("\", "/")
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $file.FullName, $relPath)
    }
} finally {
    $zip.Dispose()
}

Write-Host "Successfully created $outputFile with forward slashes and Dual Manifests."

# --- 自動デプロイフェーズ ---
$modrinthProfile = "$env:APPDATA\ModrinthApp\profiles\TacZ_Roguelike"
if (-not (Test-Path $modrinthProfile)) {
    # フォルダ名にバージョンが含まれる場合も検索
    $candidates = Get-ChildItem -Path "$env:APPDATA\ModrinthApp\profiles" -Directory -Filter "TacZ*" -ErrorAction SilentlyContinue
    if ($candidates) {
        $modrinthProfile = $candidates[0].FullName
    }
}

if (Test-Path $modrinthProfile) {
    # mrpack をプロファイルルートにコピー
    Copy-Item -Path $outputFile -Destination "$modrinthProfile\$outputFile" -Force
    Write-Host "Deployed $outputFile -> $modrinthProfile" -ForegroundColor Green

    # 最新の JAR をプロファイルの mods フォルダにもコピー
    $profileMods = "$modrinthProfile\mods"
    if (Test-Path $profileMods) {
        Remove-Item -Path "$profileMods\tac_rogue-*.jar" -Force -ErrorAction SilentlyContinue
        Copy-Item -Path $latestJar.FullName -Destination "$profileMods\$($latestJar.Name)" -Force
        Write-Host "Synced JAR -> $profileMods\$($latestJar.Name)" -ForegroundColor Green
    }
} else {
    Write-Host "Modrinth profile not found at: $modrinthProfile (skipping deploy)" -ForegroundColor Yellow
}
