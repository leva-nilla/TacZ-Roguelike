package com.levanilla.rogue.core;

import java.util.List;

/**
 * Defines the 5-floor act rhythm used to shape enemy pressure, perk offers,
 * and future shop/quest tuning.
 */
public final class RogueActManager {

    private RogueActManager() {
    }

    public enum FloorPhase {
        SCOUT("Scout", 0.00, 0),
        SUPPLY("Supply", -0.05, 0),
        ELITE("Elite", 0.10, 1),
        DANGER("Danger", 0.18, 2),
        BOSS("Boss", 0.25, 0);

        public final String label;
        public final double variantChanceBonus;
        public final int mobCountBonus;

        FloorPhase(String label, double variantChanceBonus, int mobCountBonus) {
            this.label = label;
            this.variantChanceBonus = variantChanceBonus;
            this.mobCountBonus = mobCountBonus;
        }
    }

    public enum BuildArchetype {
        SURVIVOR,
        SCAVENGER,
        ASSAULT,
        ASSASSIN,
        BOSS_BREAKER
    }

    public static int getAct(int floor) {
        return Math.max(1, ((Math.max(1, floor) - 1) / 5) + 1);
    }

    public static int getFloorInAct(int floor) {
        return ((Math.max(1, floor) - 1) % 5) + 1;
    }

    public static FloorPhase getPhase(int floor) {
        return switch (getFloorInAct(floor)) {
            case 1 -> FloorPhase.SCOUT;
            case 2 -> FloorPhase.SUPPLY;
            case 3 -> FloorPhase.ELITE;
            case 4 -> FloorPhase.DANGER;
            default -> FloorPhase.BOSS;
        };
    }

    public static BuildArchetype getPreferredArchetype(int floor) {
        return switch (getPhase(floor)) {
            case SCOUT -> BuildArchetype.ASSASSIN;
            case SUPPLY -> BuildArchetype.SCAVENGER;
            case ELITE -> BuildArchetype.ASSAULT;
            case DANGER -> BuildArchetype.SURVIVOR;
            case BOSS -> BuildArchetype.BOSS_BREAKER;
        };
    }

    public static String getPhaseDirective(int floor) {
        return switch (getPhase(floor)) {
            case SCOUT -> "Scout the zone. Stealth and precision perks are favored.";
            case SUPPLY -> "Resupply window. Economy, ammo, and sustain perks are favored.";
            case ELITE -> "Elite contact expected. Direct firepower perks are favored.";
            case DANGER -> "High-risk floor. Defensive and recovery perks are favored.";
            case BOSS -> "Boss floor. Boss-killer and survival perks are favored.";
        };
    }

    public static List<PerkDefinition.Category> getPreferredPerkCategories(int floor) {
        return switch (getPreferredArchetype(floor)) {
            case SURVIVOR -> List.of(
                PerkDefinition.Category.VITALITY,
                PerkDefinition.Category.ARMOR,
                PerkDefinition.Category.RESISTANCE,
                PerkDefinition.Category.DODGE,
                PerkDefinition.Category.REGENERATION,
                PerkDefinition.Category.MEDIC
            );
            case SCAVENGER -> List.of(
                PerkDefinition.Category.SCAVENGER,
                PerkDefinition.Category.GOLD_RUSH,
                PerkDefinition.Category.AMMO_EFFICIENCY,
                PerkDefinition.Category.RELOAD_SPEED,
                PerkDefinition.Category.AUTOLOADER,
                PerkDefinition.Category.QUICK_FIX
            );
            case ASSAULT -> List.of(
                PerkDefinition.Category.DAMAGE,
                PerkDefinition.Category.GUN_PROFICIENCY,
                PerkDefinition.Category.FIRE_RATE,
                PerkDefinition.Category.MELEE_SPEED,
                PerkDefinition.Category.HANDLING,
                PerkDefinition.Category.RELOAD_SPEED,
                PerkDefinition.Category.AUTOLOADER,
                PerkDefinition.Category.FORTUNE,
                PerkDefinition.Category.HEAD_HUNTER
            );
            case ASSASSIN -> List.of(
                PerkDefinition.Category.STEALTH_EXTEND,
                PerkDefinition.Category.SHARPSHOOTER,
                PerkDefinition.Category.EXECUTIONER,
                PerkDefinition.Category.MELEE_SPEED,
                PerkDefinition.Category.VELOCITY,
                PerkDefinition.Category.FORTUNE
            );
            case BOSS_BREAKER -> List.of(
                PerkDefinition.Category.DAMAGE,
                PerkDefinition.Category.FIRE_RATE,
                PerkDefinition.Category.MELEE_SPEED,
                PerkDefinition.Category.RESISTANCE,
                PerkDefinition.Category.VAMPIRE,
                PerkDefinition.Category.BLOODLUST,
                PerkDefinition.Category.EXPLOSIVE,
                PerkDefinition.Category.HEAD_HUNTER
            );
        };
    }
}
