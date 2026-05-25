package com.levanilla.rogue.world;

import com.levanilla.rogue.world.generation.DungeonPlanGenerator;
import com.levanilla.rogue.world.generation.DungeonPlanValidator;
import com.levanilla.rogue.world.generation.FloorGenerationContext;
import com.levanilla.rogue.world.generation.plan.DungeonPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeGenerationTest {
    private static final String[][] VARIANTS = {
        {"RUINS", "OVERGROWN", "FLOODED", "FROZEN", "CRUMBLING", "BURNING"},
        {"LAB", "STERILE", "CONTAMINATED", "ABANDONED", "POWERED", "DARK"},
        {"UNDERGROUND", "CAVE", "CRYSTAL", "MAGMA", "ICE", "MUSHROOM"},
        {"MILITARY", "BUNKER", "COMMAND", "ARMORY", "HANGAR", "RADAR"},
        {"NETHER", "FORTRESS", "BASALT", "SOUL", "WARPED", "CRIMSON"},
        {"OCEAN", "SUNKEN_RUINS", "CORAL", "TRENCH", "SHIPWRECK", "PIPELINE"},
        {"URBAN", "SUBWAY", "OFFICE", "HOSPITAL", "SEWERS", "ROOFTOP"},
        {"TEMPLE", "ANCIENT", "JUNGLE", "DESERT", "END_SHRINE", "NETHER_ALTAR"},
        {"VOID", "RIFT", "CRYSTAL_VOID", "ECHO", "SCULK", "WARDEN"}
    };

    @Test
    void allThemeVariantsHaveGenerationStyleAndValidPlan() {
        int floor = 1;
        for (String[] group : VARIANTS) {
            String biome = group[0];
            for (int i = 1; i < group.length; i++) {
                String variant = group[i];
                ThemeManager.ThemeGenerationStyle style = ThemeGenerationStyleResolver.resolve(biome, variant);
                FloorGenerationContext context = new FloorGenerationContext(
                    0x1234ABCDL,
                    floor,
                    0x55AA10L + floor,
                    0,
                    "theme-test-" + biome + "-" + variant,
                    "SOLO",
                    "ELIMINATE",
                    1,
                    0xCAFEBABEL);
                boolean bossFloor = floor % 5 == 0;
                DungeonPlan plan = DungeonPlanGenerator.generate(context, bossFloor, style);
                DungeonPlanValidator.ValidationResult result = DungeonPlanValidator.validate(plan, bossFloor);

                int checkedFloor = floor;
                assertAll(biome + "/" + variant,
                    () -> assertNotNull(style, "style"),
                    () -> assertNotNull(plan, "plan"),
                    () -> assertTrue(result.valid(), "floor=" + checkedFloor + " " + result.reason()),
                    () -> assertTrue(plan.rooms().size() >= 6, "room count " + plan.rooms().size()),
                    () -> assertTrue(plan.corridors().size() >= plan.rooms().size() - 1,
                        "corridors=" + plan.corridors().size() + " rooms=" + plan.rooms().size())
                );
                floor++;
            }
        }
    }

    @Test
    void distinctThemeVariantsUseSpecialGenerationStyles() {
        for (String[] group : VARIANTS) {
            String biome = group[0];
            for (int i = 1; i < group.length; i++) {
                assertSpecial(biome, group[i]);
            }
        }
    }

    @Test
    void bossThemePlansRemainValidAcrossAllBiomeGroups() {
        int floor = 5;
        for (String[] group : VARIANTS) {
            String biome = group[0];
            String variant = group[1];
            ThemeManager.ThemeGenerationStyle style = ThemeGenerationStyleResolver.resolve(biome, variant);
            FloorGenerationContext context = new FloorGenerationContext(
                0x99112233L,
                floor,
                0x7788L + floor,
                0,
                "boss-theme-test-" + biome,
                "SOLO",
                "ELIMINATE",
                1,
                0x11223344L);
            DungeonPlan plan = DungeonPlanGenerator.generate(context, true, style);
            DungeonPlanValidator.ValidationResult result = DungeonPlanValidator.validate(plan, true);
            assertAll(biome + "/" + variant + " boss",
                () -> assertTrue(result.valid(), result.reason()),
                () -> assertTrue(plan.rooms().size() >= 6, "room count " + plan.rooms().size())
            );
            floor += 5;
        }
    }

    private static void assertSpecial(String biome, String variant) {
        ThemeManager.ThemeGenerationStyle style = ThemeGenerationStyleResolver.resolve(biome, variant);
        assertNotEquals(ThemeManager.ThemeGenerationStyle.DEFAULT, style, biome + "/" + variant);
    }
}
