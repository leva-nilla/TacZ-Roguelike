package com.levanilla.rogue.world;

/**
 * Resolves a theme name pair into a structural generation style without touching Minecraft block registries.
 */
public final class ThemeGenerationStyleResolver {
    private ThemeGenerationStyleResolver() {}

    public static ThemeManager.ThemeGenerationStyle resolve(String biome, String variant) {
        if ("URBAN".equals(biome)) {
            if ("ROOFTOP".equals(variant)) return ThemeManager.ThemeGenerationStyle.ROOFTOP_OPEN;
            if ("SUBWAY".equals(variant)) return ThemeManager.ThemeGenerationStyle.SUBWAY;
            if ("SEWERS".equals(variant)) return ThemeManager.ThemeGenerationStyle.SEWER;
            return ThemeManager.ThemeGenerationStyle.URBAN_INTERIOR;
        }
        if ("LAB".equals(biome)) return ThemeManager.ThemeGenerationStyle.LAB_COMPLEX;
        if ("UNDERGROUND".equals(biome)) return ThemeManager.ThemeGenerationStyle.ORGANIC_CAVE;
        if ("OCEAN".equals(biome)) {
            if ("SHIPWRECK".equals(variant)) return ThemeManager.ThemeGenerationStyle.SHIPWRECK;
            if ("PIPELINE".equals(variant)) return ThemeManager.ThemeGenerationStyle.PIPELINE;
            return ThemeManager.ThemeGenerationStyle.FLOODED_LOW;
        }
        if ("MILITARY".equals(biome)) {
            if ("HANGAR".equals(variant)) return ThemeManager.ThemeGenerationStyle.HANGAR;
            if ("RADAR".equals(variant)) return ThemeManager.ThemeGenerationStyle.RADAR_OPEN;
            return ThemeManager.ThemeGenerationStyle.MILITARY_COMPOUND;
        }
        if ("NETHER".equals(biome)) return ThemeManager.ThemeGenerationStyle.NETHER_FORTRESS;
        if ("RUINS".equals(biome)) return ThemeManager.ThemeGenerationStyle.BROKEN_RUINS;
        if ("TEMPLE".equals(biome)) return ThemeManager.ThemeGenerationStyle.TEMPLE_AXIS;
        if ("VOID".equals(biome)) return ThemeManager.ThemeGenerationStyle.VOID_ALIEN;
        return ThemeManager.ThemeGenerationStyle.DEFAULT;
    }
}
