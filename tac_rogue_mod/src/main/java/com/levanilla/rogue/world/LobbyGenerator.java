package com.levanilla.rogue.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ロビー（コマンドセンター）生成 — 4つの専用ゾーンを持つ豪華なハイテク基地
 *
 * レイアウト (31×31, radius=15):
 *   NW: Intel Briefing Room    |  NE: Commander Ops Room
 *   ─────── Central Corridor ───────
 *   SW: Medical Bay            |  SE: Quartermaster Armory
 *
 * 中央: ビーコン＋ミッションポータル＋スタッシュターミナル
 */
public class LobbyGenerator {

    public static void buildLobby(ServerLevel level, BlockPos center) {
        int radius = 15;

        // ── Phase 1: Clear ──
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -1; y < 10; y++) {
                    level.setBlockAndUpdate(center.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }

        // ── Phase 2: Shell (floor, outer walls, ceiling) ──
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos p = center.offset(x, -1, z);
                int dist = Math.max(Math.abs(x), Math.abs(z));

                BlockState floor = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
                if ((x + z) % 4 == 0) floor = Blocks.GOLD_BLOCK.defaultBlockState();
                if (dist == radius) floor = Blocks.CRYING_OBSIDIAN.defaultBlockState();
                if (dist == 0) floor = Blocks.BEACON.defaultBlockState();
                level.setBlockAndUpdate(p, floor);

                if (dist == radius) {
                    for (int y = 0; y < 8; y++) {
                        BlockState wall = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
                        if (y > 1 && y < 6) {
                            wall = (x + y + z) % 3 == 0
                                ? Blocks.SEA_LANTERN.defaultBlockState()
                                : Blocks.OBSIDIAN.defaultBlockState();
                        }
                        if (y == 0 || y == 7 || (Math.abs(x) == radius && Math.abs(z) == radius)) {
                            wall = Blocks.BEDROCK.defaultBlockState();
                        }
                        level.setBlockAndUpdate(p.offset(0, y, 0), wall);
                    }
                }

                BlockState ceiling = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
                if (dist % 4 == 0) ceiling = Blocks.SEA_LANTERN.defaultBlockState();
                level.setBlockAndUpdate(p.offset(0, 8, 0), ceiling);
            }
        }

        // ── Phase 3: Central Core ──
        level.setBlockAndUpdate(center, Blocks.BEACON.defaultBlockState());
        level.setBlockAndUpdate(center.offset(0, 3, 0), Blocks.SEA_LANTERN.defaultBlockState());

        // ── Phase 4: Mission Portal (north corridor) ──
        BlockPos portalPos = center.offset(0, 0, -4);
        for (int y = 0; y < 3; y++) {
            level.setBlockAndUpdate(portalPos.offset(-1, y, 0), Blocks.GOLD_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(portalPos.offset(1, y, 0), Blocks.GOLD_BLOCK.defaultBlockState());
        }
        level.setBlockAndUpdate(portalPos.offset(0, 2, 0), Blocks.SEA_LANTERN.defaultBlockState());

        // ── Phase 5: Stash Terminal (south corridor) ──
        BlockPos stashPos = center.offset(0, 0, 4);
        level.setBlockAndUpdate(stashPos.below(), Blocks.CRYING_OBSIDIAN.defaultBlockState());
        level.setBlockAndUpdate(stashPos, Blocks.CRYING_OBSIDIAN.defaultBlockState());
        level.setBlockAndUpdate(stashPos.above(),
            com.levanilla.rogue.core.ModBlocks.STASH_TERMINAL.get().defaultBlockState());

        // ── Phase 6: Themed Zones ──
        buildOpsRoom(level, center);      // NE — Commander
        buildIntelRoom(level, center);    // NW — Intel Officer
        buildShopArmory(level, center);   // SE — Quartermaster
        buildMedicalBay(level, center);   // SW — Medic

        // ── Phase 7: NPCs ──
        NpcManager.ensureNpcsSpawned(level, center);
    }

    // ═══════════════════════════════════════════════════════════
    //  Room Shell Helper
    // ═══════════════════════════════════════════════════════════

    /**
     * 矩形の部屋を構築（4面の壁＋天井＋床、指定面にドア穴）
     */
    private static void buildRoomShell(ServerLevel level, BlockPos c,
            int x1, int x2, int z1, int z2, int h,
            BlockState wall, BlockState accent,
            BlockState floor1, BlockState floor2,
            boolean dN, boolean dS, boolean dE, boolean dW) {
        int mx = (x1 + x2) / 2, mz = (z1 + z2) / 2;

        // Floor
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++)
                level.setBlockAndUpdate(c.offset(x, -1, z),
                    (x + z) % 2 == 0 ? floor1 : floor2);

        // 4 walls
        for (int x = x1; x <= x2; x++) for (int y = 0; y < h; y++) {
            if (!(dN && x >= mx-1 && x <= mx+1 && y < 3))
                level.setBlockAndUpdate(c.offset(x, y, z1), y == 2 ? accent : wall);
            if (!(dS && x >= mx-1 && x <= mx+1 && y < 3))
                level.setBlockAndUpdate(c.offset(x, y, z2), y == 2 ? accent : wall);
        }
        for (int z = z1; z <= z2; z++) for (int y = 0; y < h; y++) {
            if (!(dE && z >= mz-1 && z <= mz+1 && y < 3))
                level.setBlockAndUpdate(c.offset(x2, y, z), y == 2 ? accent : wall);
            if (!(dW && z >= mz-1 && z <= mz+1 && y < 3))
                level.setBlockAndUpdate(c.offset(x1, y, z), y == 2 ? accent : wall);
        }

        // Ceiling
        for (int x = x1+1; x < x2; x++)
            for (int z = z1+1; z < z2; z++)
                level.setBlockAndUpdate(c.offset(x, h, z), wall);
    }

    // ═══════════════════════════════════════════════════════════
    //  NE — Commander's Operations Room (作戦室)
    // ═══════════════════════════════════════════════════════════

    private static void buildOpsRoom(ServerLevel level, BlockPos c) {
        // Room: X[+6,+13] Z[-13,-6], doors on South(+Z) & West(-X)
        buildRoomShell(level, c, 6, 13, -13, -6, 5,
            Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
            Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(),
            Blocks.GOLD_BLOCK.defaultBlockState(),
            Blocks.POLISHED_BLACKSTONE.defaultBlockState(),
            false, true, false, true);

        // War Table (dark oak slab L-shape)
        for (int dx = -1; dx <= 1; dx++) {
            set(level, c, 9+dx, 0, -10, Blocks.DARK_OAK_SLAB);
            set(level, c, 9+dx, 0, -9, Blocks.DARK_OAK_SLAB);
        }
        set(level, c, 9, 1, -10, Blocks.CARTOGRAPHY_TABLE);  // Map on table

        // Back wall display — emblem + gold flanks
        set(level, c, 9, 3, -13, Blocks.CHISELED_STONE_BRICKS);
        set(level, c, 8, 3, -13, Blocks.GOLD_BLOCK);
        set(level, c, 10, 3, -13, Blocks.GOLD_BLOCK);

        // Tactical screens (sea lanterns)
        set(level, c, 7, 2, -13, Blocks.SEA_LANTERN);
        set(level, c, 11, 2, -13, Blocks.SEA_LANTERN);
        set(level, c, 12, 2, -13, Blocks.SEA_LANTERN);

        // Ceiling lamps
        set(level, c, 8, 4, -10, Blocks.LANTERN);
        set(level, c, 11, 4, -10, Blocks.LANTERN);

        // Yellow carpet runner
        for (int z = -12; z <= -7; z++) set(level, c, 9, 0, z, Blocks.YELLOW_CARPET);

        // Equipment racks
        set(level, c, 12, 0, -12, Blocks.ANVIL);
        set(level, c, 12, 0, -8, Blocks.GRINDSTONE);
        set(level, c, 7, 0, -12, Blocks.SMITHING_TABLE);
    }

    // ═══════════════════════════════════════════════════════════
    //  NW — Intelligence Briefing Room (情報分析室)
    // ═══════════════════════════════════════════════════════════

    private static void buildIntelRoom(ServerLevel level, BlockPos c) {
        // Room: X[-13,-6] Z[-13,-6], doors on South & East
        buildRoomShell(level, c, -13, -6, -13, -6, 5,
            Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
            Blocks.DARK_PRISMARINE.defaultBlockState(),
            Blocks.DARK_PRISMARINE.defaultBlockState(),
            Blocks.POLISHED_BLACKSTONE.defaultBlockState(),
            false, true, true, false);

        // Intel desk — cartography + lectern
        set(level, c, -10, 0, -10, Blocks.CARTOGRAPHY_TABLE);
        set(level, c, -9, 0, -10, Blocks.LECTERN);
        set(level, c, -8, 0, -10, Blocks.DARK_OAK_SLAB);

        // Strategic screens on back wall
        for (int x = -12; x <= -7; x += 2)
            set(level, c, x, 2, -13, Blocks.SEA_LANTERN);
        set(level, c, -10, 3, -13, Blocks.PRISMARINE_WALL);
        set(level, c, -9, 3, -13, Blocks.PRISMARINE_WALL);

        // Blue carpet runner
        for (int z = -12; z <= -7; z++) set(level, c, -9, 0, z, Blocks.BLUE_CARPET);

        // Ceiling lamps (soul lanterns for cool blue mood)
        set(level, c, -8, 4, -10, Blocks.SOUL_LANTERN);
        set(level, c, -11, 4, -10, Blocks.SOUL_LANTERN);

        // Bookshelf display
        set(level, c, -12, 0, -12, Blocks.BOOKSHELF);
        set(level, c, -12, 1, -12, Blocks.BOOKSHELF);
        set(level, c, -7, 0, -12, Blocks.BOOKSHELF);
        set(level, c, -7, 1, -12, Blocks.BOOKSHELF);
    }

    // ═══════════════════════════════════════════════════════════
    //  SE — Quartermaster Armory & Shop (武装ショップ)
    // ═══════════════════════════════════════════════════════════

    private static void buildShopArmory(ServerLevel level, BlockPos c) {
        // Room: X[+6,+13] Z[+6,+13], doors on North & West
        buildRoomShell(level, c, 6, 13, 6, 13, 5,
            Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
            Blocks.POLISHED_BLACKSTONE.defaultBlockState(),
            Blocks.EMERALD_BLOCK.defaultBlockState(),
            Blocks.POLISHED_BLACKSTONE.defaultBlockState(),
            true, false, false, true);

        // Shop counter (3-wide slab counter)
        for (int dx = -1; dx <= 1; dx++) {
            set(level, c, 9+dx, 0, 9, Blocks.POLISHED_BLACKSTONE_SLAB);
        }

        // Supply crates (barrels along back wall)
        for (int x = 7; x <= 12; x += 2) {
            set(level, c, x, 0, 12, Blocks.BARREL);
            set(level, c, x, 1, 12, Blocks.BARREL);
        }

        // Crafting stations
        set(level, c, 12, 0, 7, Blocks.SMITHING_TABLE);
        set(level, c, 12, 0, 8, Blocks.BLAST_FURNACE);
        set(level, c, 12, 0, 10, Blocks.GRINDSTONE);

        // Green carpet
        for (int z = 7; z <= 12; z++) set(level, c, 9, 0, z, Blocks.GREEN_CARPET);

        // Ceiling lamps
        set(level, c, 8, 4, 9, Blocks.LANTERN);
        set(level, c, 11, 4, 9, Blocks.LANTERN);

        // Display (emerald + gold on wall)
        set(level, c, 9, 3, 13, Blocks.EMERALD_BLOCK);
        set(level, c, 10, 3, 13, Blocks.GOLD_BLOCK);
        set(level, c, 8, 3, 13, Blocks.GOLD_BLOCK);
    }

    // ═══════════════════════════════════════════════════════════
    //  SW — Medical Bay (医療ベイ)
    // ═══════════════════════════════════════════════════════════

    private static void buildMedicalBay(ServerLevel level, BlockPos c) {
        // Room: X[-13,-6] Z[+6,+13], doors on North & East
        buildRoomShell(level, c, -13, -6, 6, 13, 5,
            Blocks.QUARTZ_BRICKS.defaultBlockState(),
            Blocks.QUARTZ_PILLAR.defaultBlockState(),
            Blocks.QUARTZ_BLOCK.defaultBlockState(),
            Blocks.SMOOTH_QUARTZ.defaultBlockState(),
            true, false, true, false);

        // Medical stations (brewing stands)
        set(level, c, -12, 0, 12, Blocks.BREWING_STAND);
        set(level, c, -12, 0, 10, Blocks.BREWING_STAND);

        // Cauldron (water basin)
        set(level, c, -12, 0, 8, Blocks.CAULDRON);

        // Treatment beds (white wool benches)
        for (int x = -10; x <= -8; x++) {
            set(level, c, x, 0, 12, Blocks.WHITE_WOOL);
            set(level, c, x, 0, 11, Blocks.WHITE_WOOL);
        }

        // Pink carpet
        for (int z = 7; z <= 12; z++) set(level, c, -9, 0, z, Blocks.PINK_CARPET);

        // Sterile lighting (soul lanterns)
        set(level, c, -8, 4, 9, Blocks.SOUL_LANTERN);
        set(level, c, -11, 4, 9, Blocks.SOUL_LANTERN);
        set(level, c, -9, 4, 12, Blocks.SOUL_LANTERN);

        // Red cross on back wall (redstone blocks + quartz)
        set(level, c, -9, 2, 13, Blocks.REDSTONE_BLOCK);
        set(level, c, -9, 3, 13, Blocks.REDSTONE_BLOCK);
        set(level, c, -10, 2, 13, Blocks.REDSTONE_BLOCK);
        set(level, c, -8, 2, 13, Blocks.REDSTONE_BLOCK);
    }

    // ═══════════════════════════════════════════════════════════
    //  Utility
    // ═══════════════════════════════════════════════════════════

    private static void set(ServerLevel level, BlockPos c, int dx, int dy, int dz,
                            net.minecraft.world.level.block.Block block) {
        level.setBlockAndUpdate(c.offset(dx, dy, dz), block.defaultBlockState());
    }
}
