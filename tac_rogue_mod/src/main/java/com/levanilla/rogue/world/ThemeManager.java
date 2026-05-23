package com.levanilla.rogue.world;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 45 種のテーマを管理するシステム。
 * 9 バイオーム群 × 5 バリエーション で構築し、5 層ごとにバイオーム群を切り替える。
 * 同一バイオーム群内の 5 層はそれぞれ異なるバリエーションを使用する。
 */
public class ThemeManager {

    /** テーマの構成要素 */
    public static class ThemeInstance {
        public final BlockState floor;
        public final BlockState wall;
        public final BlockState ceil;
        public final BlockState decor;
        public final BlockState accent;   // 装飾アクセント（柱やフレーム）
        public final BlockState light;    // 照明ブロック
        public final String biomeName;    // バイオーム群名
        public final String variantName;  // バリエーション名
        public final String displayName;  // 表示名

        public ThemeInstance(BlockState floor, BlockState wall, BlockState ceil, BlockState decor,
                            BlockState accent, BlockState light, String biomeName, String variantName) {
            this.floor = floor;
            this.wall = wall;
            this.ceil = ceil;
            this.decor = decor;
            this.accent = accent;
            this.light = light;
            this.biomeName = biomeName;
            this.variantName = variantName;
            this.displayName = biomeName + " - " + variantName;
        }
    }

    // ===== バイオーム群の定義 (9 群) =====
    public enum Biome {
        RUINS, LAB, UNDERGROUND, MILITARY, NETHER, OCEAN, URBAN, TEMPLE, VOID
    }

    public enum ThemeGenerationStyle {
        DEFAULT,
        BROKEN_RUINS,
        ORGANIC_CAVE,
        FLOODED_LOW,
        SHIPWRECK,
        PIPELINE,
        SUBWAY,
        SEWER,
        HANGAR,
        RADAR_OPEN,
        LAB_COMPLEX,
        MILITARY_COMPOUND,
        NETHER_FORTRESS,
        URBAN_INTERIOR,
        TEMPLE_AXIS,
        VOID_ALIEN,
        ROOFTOP_OPEN
    }

    // バリエーション名（各バイオーム群内で 5 種）
    private static final String[][] VARIANT_NAMES = {
        /* RUINS       */ {"OVERGROWN", "FLOODED", "FROZEN", "CRUMBLING", "BURNING"},
        /* LAB         */ {"STERILE", "CONTAMINATED", "ABANDONED", "POWERED", "DARK"},
        /* UNDERGROUND */ {"CAVE", "CRYSTAL", "MAGMA", "ICE", "MUSHROOM"},
        /* MILITARY    */ {"BUNKER", "COMMAND", "ARMORY", "HANGAR", "RADAR"},
        /* NETHER      */ {"FORTRESS", "BASALT", "SOUL", "WARPED", "CRIMSON"},
        /* OCEAN       */ {"SUNKEN_RUINS", "CORAL", "TRENCH", "SHIPWRECK", "PIPELINE"},
        /* URBAN       */ {"SUBWAY", "OFFICE", "HOSPITAL", "SEWERS", "ROOFTOP"},
        /* TEMPLE      */ {"ANCIENT", "JUNGLE", "DESERT", "END_SHRINE", "NETHER_ALTAR"},
        /* VOID        */ {"RIFT", "CRYSTAL_VOID", "ECHO", "SCULK", "WARDEN"}
    };

    // ===== 各テーマの素材定義 =====
    // [biome][variant] の 2D 配列で 45 テーマを網羅
    private static final BlockState[][] FLOORS = {
        /* RUINS       */ {s(Blocks.MOSSY_STONE_BRICKS), s(Blocks.CLAY), s(Blocks.PACKED_ICE), s(Blocks.CRACKED_STONE_BRICKS), s(Blocks.BLACKSTONE)},  // MAGMA_BLOCK→BLACKSTONE (ダメージ回避)
        /* LAB         */ {s(Blocks.QUARTZ_BLOCK), s(Blocks.SLIME_BLOCK), s(Blocks.POLISHED_ANDESITE), s(Blocks.IRON_BLOCK), s(Blocks.BLACK_CONCRETE)},
        /* UNDERGROUND */ {s(Blocks.STONE), s(Blocks.AMETHYST_BLOCK), s(Blocks.BASALT), s(Blocks.BLUE_ICE), s(Blocks.MYCELIUM)},
        /* MILITARY    */ {s(Blocks.GRAY_CONCRETE), s(Blocks.POLISHED_DEEPSLATE), s(Blocks.IRON_BLOCK), s(Blocks.LIGHT_GRAY_CONCRETE), s(Blocks.SMOOTH_STONE)},
        /* NETHER      */ {s(Blocks.NETHER_BRICKS), s(Blocks.POLISHED_BASALT), s(Blocks.NETHER_BRICKS), s(Blocks.WARPED_PLANKS), s(Blocks.CRIMSON_PLANKS)},  // SOUL_SOIL→NETHER_BRICKS (ダメージ回避)
        /* OCEAN       */ {s(Blocks.PRISMARINE_BRICKS), s(Blocks.TUBE_CORAL_BLOCK), s(Blocks.DARK_PRISMARINE), s(Blocks.DARK_OAK_PLANKS), s(Blocks.IRON_BLOCK)},
        /* URBAN       */ {s(Blocks.SMOOTH_STONE), s(Blocks.WHITE_CONCRETE), s(Blocks.QUARTZ_BLOCK), s(Blocks.DEEPSLATE_TILES), s(Blocks.GRAY_CONCRETE)},
        /* TEMPLE      */ {s(Blocks.SANDSTONE), s(Blocks.JUNGLE_PLANKS), s(Blocks.CUT_SANDSTONE), s(Blocks.END_STONE_BRICKS), s(Blocks.RED_NETHER_BRICKS)},
        /* VOID        */ {s(Blocks.OBSIDIAN), s(Blocks.AMETHYST_BLOCK), s(Blocks.DEEPSLATE), s(Blocks.SCULK), s(Blocks.REINFORCED_DEEPSLATE)}
    };

    private static final BlockState[][] WALLS = {
        /* RUINS       */ {s(Blocks.MOSSY_COBBLESTONE), s(Blocks.PRISMARINE), s(Blocks.PACKED_ICE), s(Blocks.COBBLESTONE), s(Blocks.BLACKSTONE)},
        /* LAB         */ {s(Blocks.WHITE_CONCRETE), s(Blocks.LIME_CONCRETE), s(Blocks.POLISHED_ANDESITE), s(Blocks.QUARTZ_BRICKS), s(Blocks.BLACK_CONCRETE)},
        /* UNDERGROUND */ {s(Blocks.STONE), s(Blocks.CALCITE), s(Blocks.POLISHED_BLACKSTONE), s(Blocks.BLUE_ICE), s(Blocks.MUSHROOM_STEM)},  // MAGMA_BLOCK→POLISHED_BLACKSTONE (ダメージ回避)
        /* MILITARY    */ {s(Blocks.GRAY_CONCRETE), s(Blocks.POLISHED_DEEPSLATE), s(Blocks.CUT_COPPER), s(Blocks.IRON_BLOCK), s(Blocks.STONE_BRICKS)},
        /* NETHER      */ {s(Blocks.NETHER_BRICKS), s(Blocks.BASALT), s(Blocks.SOUL_SAND), s(Blocks.WARPED_STEM), s(Blocks.CRIMSON_STEM)},  // SOUL_SANDは壁なのでOK(歩かない)
        /* OCEAN       */ {s(Blocks.PRISMARINE), s(Blocks.BRAIN_CORAL_BLOCK), s(Blocks.DARK_PRISMARINE), s(Blocks.SPRUCE_PLANKS), s(Blocks.CUT_COPPER)},
        /* URBAN       */ {s(Blocks.STONE_BRICKS), s(Blocks.WHITE_CONCRETE), s(Blocks.WHITE_TERRACOTTA), s(Blocks.COBBLESTONE), s(Blocks.LIGHT_GRAY_CONCRETE)},
        /* TEMPLE      */ {s(Blocks.SANDSTONE), s(Blocks.MOSSY_STONE_BRICKS), s(Blocks.CUT_SANDSTONE), s(Blocks.END_STONE_BRICKS), s(Blocks.RED_NETHER_BRICKS)},
        /* VOID        */ {s(Blocks.CRYING_OBSIDIAN), s(Blocks.AMETHYST_BLOCK), s(Blocks.DEEPSLATE_BRICKS), s(Blocks.SCULK_CATALYST), s(Blocks.REINFORCED_DEEPSLATE)}
    };

    private static final BlockState[][] DECORS = {
        /* RUINS       */ {s(Blocks.VINE), s(Blocks.KELP_PLANT), s(Blocks.SNOW), s(Blocks.COBWEB), s(Blocks.GLOWSTONE)},  // FIRE→GLOWSTONE (燃焼ダメージ回避)
        /* LAB         */ {s(Blocks.IRON_BARS), s(Blocks.SLIME_BLOCK), s(Blocks.COBWEB), s(Blocks.REDSTONE_LAMP), s(Blocks.CHAIN)},
        /* UNDERGROUND */ {s(Blocks.POINTED_DRIPSTONE), s(Blocks.AMETHYST_CLUSTER), s(Blocks.GLOWSTONE), s(Blocks.ICE), s(Blocks.BROWN_MUSHROOM)},  // FIRE→GLOWSTONE
        /* MILITARY    */ {s(Blocks.CAULDRON), s(Blocks.ANVIL), s(Blocks.BARREL), s(Blocks.IRON_BARS), s(Blocks.LEVER)},
        /* NETHER      */ {s(Blocks.SHROOMLIGHT), s(Blocks.CHAIN), s(Blocks.END_ROD), s(Blocks.WARPED_FUNGUS), s(Blocks.CRIMSON_FUNGUS)},  // SOUL_FIRE→SHROOMLIGHT
        /* OCEAN       */ {s(Blocks.SEA_PICKLE), s(Blocks.DEAD_BRAIN_CORAL), s(Blocks.KELP_PLANT), s(Blocks.BARREL), s(Blocks.IRON_BARS)},
        /* URBAN       */ {s(Blocks.FLOWER_POT), s(Blocks.BOOKSHELF), s(Blocks.IRON_BARS), s(Blocks.COBWEB), s(Blocks.CAULDRON)},
        /* TEMPLE      */ {s(Blocks.COBWEB), s(Blocks.VINE), s(Blocks.DEAD_BUSH), s(Blocks.END_ROD), s(Blocks.END_ROD)},
        /* VOID        */ {s(Blocks.END_ROD), s(Blocks.AMETHYST_CLUSTER), s(Blocks.SCULK_VEIN), s(Blocks.SCULK_SENSOR), s(Blocks.CHAIN)}
    };

    private static final BlockState[][] ACCENTS = {
        /* RUINS       */ {s(Blocks.OAK_LOG), s(Blocks.DARK_PRISMARINE), s(Blocks.SPRUCE_LOG), s(Blocks.STONE), s(Blocks.POLISHED_BLACKSTONE)},
        /* LAB         */ {s(Blocks.IRON_BLOCK), s(Blocks.GREEN_CONCRETE), s(Blocks.QUARTZ_PILLAR), s(Blocks.REDSTONE_BLOCK), s(Blocks.OBSIDIAN)},
        /* UNDERGROUND */ {s(Blocks.ANDESITE), s(Blocks.BUDDING_AMETHYST), s(Blocks.NETHERRACK), s(Blocks.PACKED_ICE), s(Blocks.RED_MUSHROOM_BLOCK)},  // LAVA→NETHERRACK (溶岩ダメージ回避)
        /* MILITARY    */ {s(Blocks.IRON_BARS), s(Blocks.POLISHED_BLACKSTONE_BRICKS), s(Blocks.EXPOSED_COPPER), s(Blocks.CHAIN), s(Blocks.STONE_BRICKS)},
        /* NETHER      */ {s(Blocks.NETHER_BRICK_FENCE), s(Blocks.POLISHED_BASALT), s(Blocks.BONE_BLOCK), s(Blocks.WARPED_HYPHAE), s(Blocks.CRIMSON_HYPHAE)},
        /* OCEAN       */ {s(Blocks.SEA_LANTERN), s(Blocks.HORN_CORAL_BLOCK), s(Blocks.PRISMARINE_BRICKS), s(Blocks.OAK_LOG), s(Blocks.COPPER_BLOCK)},
        /* URBAN       */ {s(Blocks.IRON_BARS), s(Blocks.QUARTZ_BLOCK), s(Blocks.QUARTZ_PILLAR), s(Blocks.MOSSY_COBBLESTONE), s(Blocks.IRON_BLOCK)},
        /* TEMPLE      */ {s(Blocks.CHISELED_SANDSTONE), s(Blocks.JUNGLE_LOG), s(Blocks.CHISELED_RED_SANDSTONE), s(Blocks.PURPUR_PILLAR), s(Blocks.GILDED_BLACKSTONE)},
        /* VOID        */ {s(Blocks.OBSIDIAN), s(Blocks.BUDDING_AMETHYST), s(Blocks.POLISHED_DEEPSLATE), s(Blocks.SCULK), s(Blocks.REINFORCED_DEEPSLATE)}
    };

    private static final BlockState[][] LIGHTS = {
        /* RUINS       */ {s(Blocks.GLOWSTONE), s(Blocks.SEA_LANTERN), s(Blocks.GLOWSTONE), s(Blocks.GLOWSTONE), s(Blocks.SHROOMLIGHT)},
        /* LAB         */ {s(Blocks.SEA_LANTERN), s(Blocks.GLOWSTONE), s(Blocks.SEA_LANTERN), s(Blocks.SEA_LANTERN), s(Blocks.SHROOMLIGHT)},
        /* UNDERGROUND */ {s(Blocks.GLOWSTONE), s(Blocks.SEA_LANTERN), s(Blocks.GLOWSTONE), s(Blocks.SEA_LANTERN), s(Blocks.SHROOMLIGHT)},
        /* MILITARY    */ {s(Blocks.SEA_LANTERN), s(Blocks.SEA_LANTERN), s(Blocks.GLOWSTONE), s(Blocks.SEA_LANTERN), s(Blocks.SEA_LANTERN)},
        /* NETHER      */ {s(Blocks.GLOWSTONE), s(Blocks.SHROOMLIGHT), s(Blocks.SHROOMLIGHT), s(Blocks.SHROOMLIGHT), s(Blocks.SHROOMLIGHT)},
        /* OCEAN       */ {s(Blocks.SEA_LANTERN), s(Blocks.SEA_LANTERN), s(Blocks.GLOWSTONE), s(Blocks.SEA_LANTERN), s(Blocks.SEA_LANTERN)},
        /* URBAN       */ {s(Blocks.SEA_LANTERN), s(Blocks.SEA_LANTERN), s(Blocks.SEA_LANTERN), s(Blocks.GLOWSTONE), s(Blocks.GLOWSTONE)},
        /* TEMPLE      */ {s(Blocks.GLOWSTONE), s(Blocks.SHROOMLIGHT), s(Blocks.GLOWSTONE), s(Blocks.SEA_LANTERN), s(Blocks.SHROOMLIGHT)},
        /* VOID        */ {s(Blocks.SEA_LANTERN), s(Blocks.SEA_LANTERN), s(Blocks.SHROOMLIGHT), s(Blocks.SHROOMLIGHT), s(Blocks.SEA_LANTERN)}
    };

    /** ショートカット */
    private static BlockState s(net.minecraft.world.level.block.Block block) {
        return block.defaultBlockState();
    }

    /**
     * 指定されたフロアに対応するテーマを取得する。
     * ワールドシードに基づいて、バイオーム群の順番をシャッフルし、バリエーションもランダムに決定する。
     */
    public static ThemeInstance getThemeForFloor(int floor, long worldSeed) {
        int effectiveFloor = Math.max(0, floor - 1); // 0-indexed
        
        int biomeIndex = getBiomeIndex(floor, worldSeed);
        
        // バリエーションはフロアごとにランダム（シード+フロア番号で固定）
        java.util.Random varRand = new java.util.Random(worldSeed ^ (effectiveFloor * 314159L));
        int variantIndex = varRand.nextInt(5);

        Biome biome = Biome.values()[biomeIndex];

        return new ThemeInstance(
            FLOORS[biomeIndex][variantIndex],
            WALLS[biomeIndex][variantIndex],
            WALLS[biomeIndex][variantIndex],  // 天井は壁と同じ素材
            DECORS[biomeIndex][variantIndex],
            ACCENTS[biomeIndex][variantIndex],
            LIGHTS[biomeIndex][variantIndex],
            biome.name(),
            VARIANT_NAMES[biomeIndex][variantIndex]
        );
    }

    public static ThemeInstance getThemeForIndices(int biomeIndex, int variantIndex) {
        int safeBiome = Math.max(0, Math.min(Biome.values().length - 1, biomeIndex));
        int safeVariant = Math.max(0, Math.min(VARIANT_NAMES[safeBiome].length - 1, variantIndex));
        Biome biome = Biome.values()[safeBiome];
        return new ThemeInstance(
            FLOORS[safeBiome][safeVariant],
            WALLS[safeBiome][safeVariant],
            WALLS[safeBiome][safeVariant],
            DECORS[safeBiome][safeVariant],
            ACCENTS[safeBiome][safeVariant],
            LIGHTS[safeBiome][safeVariant],
            biome.name(),
            VARIANT_NAMES[safeBiome][safeVariant]
        );
    }

    public static ThemeInstance getThemeForNames(String biomeName, String variantName) {
        int biomeIndex = biomeIndexOf(biomeName);
        if (biomeIndex < 0) return null;
        int variantIndex = variantIndexOf(biomeIndex, variantName);
        if (variantIndex < 0) return null;
        return getThemeForIndices(biomeIndex, variantIndex);
    }

    public static int biomeCount() {
        return Biome.values().length;
    }

    public static int variantCount(int biomeIndex) {
        int safeBiome = Math.max(0, Math.min(Biome.values().length - 1, biomeIndex));
        return VARIANT_NAMES[safeBiome].length;
    }

    public static String biomeNamesCsv() {
        return java.util.Arrays.stream(Biome.values())
            .map(Biome::name)
            .collect(java.util.stream.Collectors.joining(","));
    }

    public static String variantNamesCsv(String biomeName) {
        int biomeIndex = biomeIndexOf(biomeName);
        if (biomeIndex < 0) return "";
        return String.join(",", VARIANT_NAMES[biomeIndex]);
    }

    private static int biomeIndexOf(String biomeName) {
        if (biomeName == null) return -1;
        String normalized = biomeName.trim().toUpperCase(java.util.Locale.ROOT);
        for (int i = 0; i < Biome.values().length; i++) {
            if (Biome.values()[i].name().equals(normalized)) return i;
        }
        return -1;
    }

    private static int variantIndexOf(int biomeIndex, String variantName) {
        if (variantName == null || biomeIndex < 0 || biomeIndex >= VARIANT_NAMES.length) return -1;
        String normalized = variantName.trim().toUpperCase(java.util.Locale.ROOT);
        for (int i = 0; i < VARIANT_NAMES[biomeIndex].length; i++) {
            if (VARIANT_NAMES[biomeIndex][i].equals(normalized)) return i;
        }
        return -1;
    }

    public static ThemeGenerationStyle generationStyle(ThemeInstance theme) {
        if (theme == null) return ThemeGenerationStyle.DEFAULT;
        return ThemeGenerationStyleResolver.resolve(theme.biomeName, theme.variantName);
    }

    public static boolean isRooftop(ThemeInstance theme) {
        return generationStyle(theme) == ThemeGenerationStyle.ROOFTOP_OPEN;
    }

    /** バイオーム群のインデックスをシードベースで取得 */
    public static int getBiomeIndex(int floor, long worldSeed) {
        int effectiveFloor = Math.max(0, floor - 1);
        int biomeGroup = effectiveFloor / 5;
        int cycle = biomeGroup / Biome.values().length;
        
        java.util.List<Integer> list = new java.util.ArrayList<>();
        for (int i = 0; i < Biome.values().length; i++) list.add(i);
        
        // サイクルごとに異なるシャッフルを行う
        java.util.Collections.shuffle(list, new java.util.Random(worldSeed ^ (cycle * 8934L)));
        return list.get(biomeGroup % Biome.values().length);
    }

    /** ボスフロアかどうか */
    public static boolean isBossFloor(int floor) {
        return floor > 0 && floor % 5 == 0;
    }
}
