package com.levanilla.rogue.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Open forward operating base lobby.
 *
 * The lobby is rebuilt from a thick foundation first, then surrounded by a
 * visible military perimeter. This keeps the open-air feel without allowing
 * normal movement into the void.
 */
public class LobbyGenerator {
    private static final int RADIUS = 28;
    private static final int LOBBY_VERSION = 2;
    public static final BlockPos DEFAULT_CENTER = new BlockPos(0, 201, 0);

    public static boolean ensureLobbyBuilt(ServerLevel level, BlockPos center) {
        com.levanilla.rogue.core.LobbySavedData saved = com.levanilla.rogue.core.LobbySavedData.get(level);
        boolean currentBlocks = isCurrentLobby(level, center);
        if (saved.getVersion() == LOBBY_VERSION) {
            NpcManager.scheduleLobbyNormalization(level, 80);
            return false;
        }
        if (!currentBlocks || saved.getVersion() != LOBBY_VERSION) {
            buildLobby(level, center);
            saved.setVersion(LOBBY_VERSION);
            return true;
        }

        saved.setVersion(LOBBY_VERSION);
        NpcManager.scheduleLobbyNormalization(level, 80);
        return false;
    }

    public static boolean isCurrentLobby(ServerLevel level, BlockPos center) {
        return level.getBlockState(center.offset(0, -4, 0)).is(Blocks.COBBLED_DEEPSLATE)
            && level.getBlockState(center.offset(RADIUS, 3, 0)).is(Blocks.SEA_LANTERN)
            && level.getBlockState(center.offset(RADIUS - 3, 6, RADIUS - 3)).is(Blocks.SEA_LANTERN)
            && level.getBlockState(center.offset(-2, 0, -2)).is(Blocks.POLISHED_BLACKSTONE_WALL)
            && level.getBlockState(center.offset(8, 5, -22)).is(Blocks.POLISHED_DEEPSLATE)
            && level.getBlockState(center.offset(11, 5, -16)).is(Blocks.POLISHED_DEEPSLATE)
            && level.getBlockState(center.offset(0, 1, 10)).is(com.levanilla.rogue.core.ModBlocks.STASH_TERMINAL.get());
    }

    public static void buildLobby(ServerLevel level, BlockPos center) {
        clearArea(level, center, RADIUS + 2, -4, 15);

        buildFoundation(level, center);
        buildPerimeter(level, center);
        buildCentralHub(level, center);
        buildMissionGate(level, center);
        buildStashTerminal(level, center);

        buildCommanderOps(level, center);
        buildIntelBriefing(level, center);
        buildQuartermasterArmory(level, center);
        buildMedicalBay(level, center);
        buildWatchTowers(level, center);

        NpcManager.ensureNpcsSpawned(level, center);
        NpcManager.scheduleLobbyNormalization(level, 40);
    }

    private static void clearArea(ServerLevel level, BlockPos c, int radius, int minY, int maxY) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    set(level, c, x, y, z, Blocks.AIR);
                }
            }
        }
    }

    private static void buildFoundation(ServerLevel level, BlockPos c) {
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                int dist = Math.max(Math.abs(x), Math.abs(z));
                set(level, c, x, -4, z, Blocks.COBBLED_DEEPSLATE);
                set(level, c, x, -3, z, Blocks.COBBLED_DEEPSLATE);
                set(level, c, x, -2, z, Blocks.DEEPSLATE);

                Block floor = Blocks.SMOOTH_STONE;
                if (Math.abs(x) <= 2 || Math.abs(z) <= 2) floor = Blocks.POLISHED_ANDESITE;
                if ((Math.abs(x) % 8 == 0 || Math.abs(z) % 8 == 0) && dist < RADIUS - 3) {
                    floor = Blocks.GRAY_CONCRETE;
                }
                if (dist >= RADIUS - 2) floor = Blocks.DEEPSLATE_TILES;
                if (Math.abs(x) <= 1 && Math.abs(z) <= 1) floor = Blocks.BEACON;
                set(level, c, x, -1, z, floor);
            }
        }
    }

    private static void buildPerimeter(ServerLevel level, BlockPos c) {
        for (int i = -RADIUS; i <= RADIUS; i++) {
            buildFence(level, c, i, -RADIUS, Math.abs(i) % 7 == 0);
            buildFence(level, c, i, RADIUS, Math.abs(i) % 7 == 0);
            buildFence(level, c, -RADIUS, i, Math.abs(i) % 7 == 0);
            buildFence(level, c, RADIUS, i, Math.abs(i) % 7 == 0);
        }

        for (int i = -RADIUS + 4; i <= RADIUS - 4; i += 4) {
            set(level, c, i, 0, -RADIUS + 2, Blocks.POLISHED_DEEPSLATE_WALL);
            set(level, c, i, 0, RADIUS - 2, Blocks.POLISHED_DEEPSLATE_WALL);
            set(level, c, -RADIUS + 2, 0, i, Blocks.POLISHED_DEEPSLATE_WALL);
            set(level, c, RADIUS - 2, 0, i, Blocks.POLISHED_DEEPSLATE_WALL);
        }
    }

    private static void buildFence(ServerLevel level, BlockPos c, int x, int z, boolean post) {
        set(level, c, x, 0, z, Blocks.POLISHED_DEEPSLATE);
        if (post) {
            set(level, c, x, 1, z, Blocks.POLISHED_DEEPSLATE_WALL);
            set(level, c, x, 2, z, Blocks.POLISHED_DEEPSLATE_WALL);
            set(level, c, x, 3, z, Blocks.SEA_LANTERN);
        } else {
            set(level, c, x, 1, z, Blocks.IRON_BARS);
            set(level, c, x, 2, z, Blocks.IRON_BARS);
        }
    }

    private static void buildCentralHub(ServerLevel level, BlockPos c) {
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                int dist = Math.max(Math.abs(x), Math.abs(z));
                if (dist == 5) {
                    set(level, c, x, -1, z, Blocks.POLISHED_BLACKSTONE);
                } else if (dist >= 3) {
                    set(level, c, x, -1, z, Blocks.POLISHED_ANDESITE);
                }
            }
        }

        // Keep the spawn point clear; surround it with a low mission table.
        set(level, c, -2, 0, 0, Blocks.POLISHED_BLACKSTONE_SLAB);
        set(level, c, 2, 0, 0, Blocks.POLISHED_BLACKSTONE_SLAB);
        set(level, c, 0, 0, -2, Blocks.POLISHED_BLACKSTONE_SLAB);
        set(level, c, 0, 0, 2, Blocks.POLISHED_BLACKSTONE_SLAB);
        set(level, c, -2, 0, -2, Blocks.POLISHED_BLACKSTONE_WALL);
        set(level, c, 2, 0, -2, Blocks.POLISHED_BLACKSTONE_WALL);
        set(level, c, -2, 0, 2, Blocks.POLISHED_BLACKSTONE_WALL);
        set(level, c, 2, 0, 2, Blocks.POLISHED_BLACKSTONE_WALL);
        set(level, c, -2, 1, -2, Blocks.SEA_LANTERN);
        set(level, c, 2, 1, -2, Blocks.SEA_LANTERN);
        set(level, c, -2, 1, 2, Blocks.SEA_LANTERN);
        set(level, c, 2, 1, 2, Blocks.SEA_LANTERN);

        for (int i = 4; i <= 18; i++) {
            set(level, c, i, 0, -i, Blocks.YELLOW_CARPET);
            set(level, c, -i, 0, -i, Blocks.BLUE_CARPET);
            set(level, c, i, 0, i, Blocks.GREEN_CARPET);
            set(level, c, -i, 0, i, Blocks.PINK_CARPET);
        }

        buildPylon(level, c, 5, -5, Blocks.GOLD_BLOCK, Blocks.YELLOW_STAINED_GLASS);
        buildPylon(level, c, -5, -5, Blocks.LAPIS_BLOCK, Blocks.BLUE_STAINED_GLASS);
        buildPylon(level, c, 5, 5, Blocks.EMERALD_BLOCK, Blocks.GREEN_STAINED_GLASS);
        buildPylon(level, c, -5, 5, Blocks.REDSTONE_BLOCK, Blocks.PINK_STAINED_GLASS);
    }

    private static void buildPylon(ServerLevel level, BlockPos c, int x, int z, Block base, Block glass) {
        set(level, c, x, 0, z, base);
        set(level, c, x, 1, z, glass);
        set(level, c, x, 2, z, glass);
        set(level, c, x, 3, z, Blocks.SEA_LANTERN);
    }

    private static void buildMissionGate(ServerLevel level, BlockPos c) {
        BlockPos gate = c.offset(0, 0, -11);
        for (int y = 0; y <= 4; y++) {
            set(level, gate, -3, y, 0, Blocks.POLISHED_BLACKSTONE);
            set(level, gate, 3, y, 0, Blocks.POLISHED_BLACKSTONE);
        }
        for (int x = -3; x <= 3; x++) {
            set(level, gate, x, 4, 0, Blocks.GOLD_BLOCK);
        }
        set(level, gate, -2, 1, 0, Blocks.YELLOW_STAINED_GLASS);
        set(level, gate, -1, 1, 0, Blocks.SEA_LANTERN);
        set(level, gate, 0, 1, 0, Blocks.YELLOW_STAINED_GLASS);
        set(level, gate, 1, 1, 0, Blocks.SEA_LANTERN);
        set(level, gate, 2, 1, 0, Blocks.YELLOW_STAINED_GLASS);

        for (int x = -5; x <= 5; x++) {
            set(level, c, x, 0, -14, Blocks.BLACK_CARPET);
            set(level, c, x, 0, -15, Blocks.YELLOW_CARPET);
        }
    }

    private static void buildStashTerminal(ServerLevel level, BlockPos c) {
        BlockPos stash = c.offset(0, 0, 10);
        set(level, stash, 0, -1, 0, Blocks.CRYING_OBSIDIAN);
        set(level, stash, 0, 0, 0, Blocks.CRYING_OBSIDIAN);
        level.setBlockAndUpdate(stash.above(),
            com.levanilla.rogue.core.ModBlocks.STASH_TERMINAL.get().defaultBlockState());

        for (int x = -3; x <= 3; x++) {
            set(level, stash, x, 0, 2, Blocks.POLISHED_BLACKSTONE_SLAB);
        }
        set(level, stash, -3, 0, 0, Blocks.POLISHED_DEEPSLATE_WALL);
        set(level, stash, 3, 0, 0, Blocks.POLISHED_DEEPSLATE_WALL);
        set(level, stash, -3, 1, 0, Blocks.SEA_LANTERN);
        set(level, stash, 3, 1, 0, Blocks.SEA_LANTERN);
    }

    private static void buildCommanderOps(ServerLevel level, BlockPos c) {
        buildZonePad(level, c, 8, 22, -22, -8, Blocks.POLISHED_BLACKSTONE, Blocks.GOLD_BLOCK);
        buildBackWall(level, c, 8, 22, -22, true, Blocks.POLISHED_DEEPSLATE, Blocks.YELLOW_STAINED_GLASS);
        buildSideWall(level, c, 22, -22, -8, true, Blocks.POLISHED_DEEPSLATE, Blocks.YELLOW_STAINED_GLASS);
        buildCanopy(level, c, 8, 22, -22, -8);

        for (int x = 12; x <= 16; x++) {
            set(level, c, x, 0, -17, Blocks.DARK_OAK_SLAB);
            set(level, c, x, 0, -16, Blocks.DARK_OAK_SLAB);
        }
        set(level, c, 14, 1, -17, Blocks.CARTOGRAPHY_TABLE);
        set(level, c, 10, 0, -20, Blocks.LECTERN);
        set(level, c, 18, 0, -20, Blocks.SMITHING_TABLE);
        set(level, c, 13, 2, -22, Blocks.SEA_LANTERN);
        set(level, c, 14, 2, -22, Blocks.YELLOW_STAINED_GLASS);
        set(level, c, 15, 2, -22, Blocks.SEA_LANTERN);
    }

    private static void buildIntelBriefing(ServerLevel level, BlockPos c) {
        buildZonePad(level, c, -22, -8, -22, -8, Blocks.DARK_PRISMARINE, Blocks.LAPIS_BLOCK);
        buildBackWall(level, c, -22, -8, -22, true, Blocks.POLISHED_DEEPSLATE, Blocks.BLUE_STAINED_GLASS);
        buildSideWall(level, c, -22, -22, -8, false, Blocks.POLISHED_DEEPSLATE, Blocks.BLUE_STAINED_GLASS);
        buildCanopy(level, c, -22, -8, -22, -8);

        set(level, c, -17, 0, -17, Blocks.CARTOGRAPHY_TABLE);
        set(level, c, -16, 0, -17, Blocks.LECTERN);
        set(level, c, -15, 0, -17, Blocks.COMPARATOR);
        set(level, c, -18, 0, -20, Blocks.BOOKSHELF);
        set(level, c, -18, 1, -20, Blocks.BOOKSHELF);
        set(level, c, -12, 0, -20, Blocks.LODESTONE);
        for (int x = -20; x <= -12; x += 2) {
            set(level, c, x, 2, -22, Blocks.SEA_LANTERN);
            set(level, c, x, 3, -22, Blocks.BLUE_STAINED_GLASS);
        }
    }

    private static void buildQuartermasterArmory(ServerLevel level, BlockPos c) {
        buildZonePad(level, c, 8, 22, 8, 22, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.EMERALD_BLOCK);
        buildBackWall(level, c, 8, 22, 22, false, Blocks.POLISHED_BLACKSTONE, Blocks.GREEN_STAINED_GLASS);
        buildSideWall(level, c, 22, 8, 22, true, Blocks.POLISHED_BLACKSTONE, Blocks.GREEN_STAINED_GLASS);
        buildCanopy(level, c, 8, 22, 8, 22);

        for (int x = 11; x <= 17; x++) {
            set(level, c, x, 0, 16, Blocks.POLISHED_BLACKSTONE_SLAB);
        }
        for (int x = 10; x <= 20; x += 2) {
            set(level, c, x, 0, 21, Blocks.BARREL);
            set(level, c, x, 1, 21, Blocks.BARREL);
        }
        set(level, c, 20, 0, 10, Blocks.SMITHING_TABLE);
        set(level, c, 20, 0, 12, Blocks.BLAST_FURNACE);
        set(level, c, 20, 0, 14, Blocks.GRINDSTONE);
        set(level, c, 10, 0, 20, Blocks.CHEST);
        set(level, c, 12, 0, 20, Blocks.CHEST);
        set(level, c, 14, 2, 22, Blocks.GREEN_STAINED_GLASS);
        set(level, c, 15, 2, 22, Blocks.EMERALD_BLOCK);
        set(level, c, 16, 2, 22, Blocks.GREEN_STAINED_GLASS);
    }

    private static void buildMedicalBay(ServerLevel level, BlockPos c) {
        buildZonePad(level, c, -22, -8, 8, 22, Blocks.QUARTZ_BLOCK, Blocks.REDSTONE_BLOCK);
        buildBackWall(level, c, -22, -8, 22, false, Blocks.QUARTZ_BRICKS, Blocks.PINK_STAINED_GLASS);
        buildSideWall(level, c, -22, 8, 22, false, Blocks.QUARTZ_BRICKS, Blocks.PINK_STAINED_GLASS);
        buildCanopy(level, c, -22, -8, 8, 22);

        for (int x = -18; x <= -15; x++) {
            set(level, c, x, 0, 19, Blocks.WHITE_WOOL);
            set(level, c, x, 1, 19, Blocks.PINK_CARPET);
        }
        set(level, c, -20, 0, 12, Blocks.BREWING_STAND);
        set(level, c, -20, 0, 14, Blocks.CAULDRON);
        set(level, c, -12, 0, 20, Blocks.BARREL);
        set(level, c, -12, 1, 20, Blocks.BARREL);
        set(level, c, -15, 2, 22, Blocks.REDSTONE_BLOCK);
        set(level, c, -15, 3, 22, Blocks.REDSTONE_BLOCK);
        set(level, c, -16, 2, 22, Blocks.REDSTONE_BLOCK);
        set(level, c, -14, 2, 22, Blocks.REDSTONE_BLOCK);
    }

    private static void buildZonePad(ServerLevel level, BlockPos c, int x1, int x2, int z1, int z2,
                                     Block floor, Block accent) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean border = x == x1 || x == x2 || z == z1 || z == z2;
                set(level, c, x, -1, z, border ? accent : floor);
            }
        }
    }

    private static void buildBackWall(ServerLevel level, BlockPos c, int x1, int x2, int z,
                                      boolean north, Block wall, Block glass) {
        for (int x = x1; x <= x2; x++) {
            for (int y = 0; y <= 4; y++) {
                Block block = y == 2 || y == 3 ? glass : wall;
                set(level, c, x, y, z, block);
            }
        }
        for (int x = x1 + 2; x <= x2 - 2; x += 4) {
            set(level, c, x, 5, z, Blocks.SEA_LANTERN);
        }
    }

    private static void buildSideWall(ServerLevel level, BlockPos c, int x, int z1, int z2,
                                      boolean east, Block wall, Block glass) {
        for (int z = z1; z <= z2; z++) {
            for (int y = 0; y <= 4; y++) {
                Block block = y == 2 || y == 3 ? glass : wall;
                set(level, c, x, y, z, block);
            }
        }
        for (int z = z1 + 2; z <= z2 - 2; z += 4) {
            set(level, c, x, 5, z, Blocks.SEA_LANTERN);
        }
    }

    private static void buildCanopy(ServerLevel level, BlockPos c, int x1, int x2, int z1, int z2) {
        int midX = (x1 + x2) / 2;
        int midZ = (z1 + z2) / 2;

        buildCanopySupport(level, c, x1, z1);
        buildCanopySupport(level, c, x2, z1);
        buildCanopySupport(level, c, x1, z2);
        buildCanopySupport(level, c, x2, z2);
        buildCanopySupport(level, c, midX, z1);
        buildCanopySupport(level, c, midX, z2);
        buildCanopySupport(level, c, x1, midZ);
        buildCanopySupport(level, c, x2, midZ);

        for (int x = x1; x <= x2; x++) {
            set(level, c, x, 5, z1, Blocks.POLISHED_DEEPSLATE);
            set(level, c, x, 5, z2, Blocks.POLISHED_DEEPSLATE);
        }
        for (int z = z1; z <= z2; z++) {
            set(level, c, x1, 5, z, Blocks.POLISHED_DEEPSLATE);
            set(level, c, x2, 5, z, Blocks.POLISHED_DEEPSLATE);
        }

        for (int x = x1; x <= x2; x += 3) {
            for (int z = z1; z <= z2; z++) {
                if (z == z1 || z == z2 || Math.abs(z) % 4 == 0) {
                    set(level, c, x, 5, z, Blocks.POLISHED_DEEPSLATE);
                    set(level, c, x, 6, z, Blocks.IRON_BARS);
                }
            }
        }
        for (int z = z1; z <= z2; z += 3) {
            for (int x = x1; x <= x2; x++) {
                if (x == x1 || x == x2 || Math.abs(x) % 4 == 0) {
                    set(level, c, x, 5, z, Blocks.POLISHED_DEEPSLATE);
                    set(level, c, x, 6, z, Blocks.IRON_BARS);
                }
            }
        }
        set(level, c, midX, 5, midZ, Blocks.SEA_LANTERN);
    }

    private static void buildCanopySupport(ServerLevel level, BlockPos c, int x, int z) {
        for (int y = 0; y <= 4; y++) {
            set(level, c, x, y, z, Blocks.POLISHED_DEEPSLATE_WALL);
        }
        set(level, c, x, 5, z, Blocks.POLISHED_DEEPSLATE);
    }

    private static void buildWatchTowers(ServerLevel level, BlockPos c) {
        buildTower(level, c, -RADIUS + 3, -RADIUS + 3);
        buildTower(level, c, RADIUS - 3, -RADIUS + 3);
        buildTower(level, c, -RADIUS + 3, RADIUS - 3);
        buildTower(level, c, RADIUS - 3, RADIUS - 3);
    }

    private static void buildTower(ServerLevel level, BlockPos c, int cx, int cz) {
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                set(level, c, x, -1, z, Blocks.POLISHED_DEEPSLATE);
                if (Math.abs(x - cx) == 1 || Math.abs(z - cz) == 1) {
                    set(level, c, x, 0, z, Blocks.POLISHED_DEEPSLATE_WALL);
                    set(level, c, x, 1, z, Blocks.IRON_BARS);
                    set(level, c, x, 2, z, Blocks.IRON_BARS);
                }
            }
        }
        for (int y = 0; y <= 6; y++) {
            set(level, c, cx, y, cz, y == 6 ? Blocks.SEA_LANTERN : Blocks.POLISHED_DEEPSLATE);
        }
    }

    private static void set(ServerLevel level, BlockPos c, int dx, int dy, int dz, Block block) {
        level.setBlockAndUpdate(c.offset(dx, dy, dz), block.defaultBlockState());
    }
}
