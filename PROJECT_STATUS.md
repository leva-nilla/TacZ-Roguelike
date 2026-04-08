# TacZ Roguelike Project Status (v0.1.7)

This document summarizes the current state of the project and outlines the necessary steps for further development.

## 1. Implemented Features (What's Done)
## 進捗状況 / Current Status

- [x] **ショップ UI のページネーション実装** (COMPLETED: 6アイテム/ページ、ページ送り機能)
- [x] **動的価格設定システムの導入** (COMPLETED: 武器性能に基づいた自動算出)
- [x] **ダンジョンの壁抜け問題の修正** (COMPLETED: 通路外周の生成ロジック強化)
- [x] **文字化け (セクション記号等) の解消** (COMPLETED: Gradle UTF-8設定 + コード修正)
- [x] **ロビーのスタッシュ・ショップ機能の刷新** (COMPLETED: カスタムブロック Stash Terminal 導入)
- [x] **スタッシュの容量拡張システム** (COMPLETED: 最大90スロット、ショップでアップグレード可能)
- [x] **コードの安定化と Null チェックの徹底** (COMPLETED: クラッシュ防止対策済み)

## 今後の拡張案 / Future Ideas
- ボス戦の実装とフロア毎の難易度スケーリングの微調整。
- 特殊エンチャント（Perk）のさらなるバリエーション追加。
- New guns default to a placeholder price of **500 Gold**. Implementing a custom price map for each addon is recommended.
- **Resource Leaks (Lints)**: Minor resource leaks (Closeable not closed) in `CommonEventHandler`, `RogueInventoryScreen`, and `StarterGearScreen`.
- **Null Safety**: Potential NullPointerExceptions in `ClientEventHandler` and `RogueInventoryScreen` for `@Nullable` fields.

## Status: v0.1.9 Final (Bug Fixes & Polish)
- [x] **Stash Crash Fix**: Networking separated to prevent "Connection Lost".
- [x] **Death System**: No item loss, 5% Gold penalty, Lobby respawn.
- [x] **Spawn Safety**: Suffocation prevention via AIR block detection.
- [x] **Sprint Polish**: Sprinting allowed at low hunger (>0).
- [x] **Starter Kit**: Distribution fixed for all presets, added 16x Cooked Beef.

## 3. Next Steps (Bug Fixes & Polish)
1. **Security Fix**: Patch `RogueActionMessage.java` to prevent arbitrary client `APPLY_PERK` tag injection.
2. **Memory Leak Fix**: Ensure `cleanupProcessedKills()` in `TacZEventHandler.java` is called periodically (e.g., in a server tick event).
3. **Multiplayer Safety**: Fix `RunManager.java`'s `startNextFloor` to not blindly teleport players to `existingPlayer.blockPosition()` without structural/air checks, or use the dungeon origin spawn point instead.
4. **Logic Bugs**:
   - `ShopService.java`: Add bounds checking in `handleSellItem` to prevent selling equipped armor, offhand items, or restricted slots.
   - `StashSavedData.java`: Enforce `unlockedLines` limit in the internal `setItem` / `getItem` methods.
   - `TacZEventHandler.java`: Clamp `GunCurrentAmmoCount` increments via `AMMO_EFFICIENCY` to the weapon's maximum magazine size.

---
*Created on 2026-03-27 by Antigravity*
