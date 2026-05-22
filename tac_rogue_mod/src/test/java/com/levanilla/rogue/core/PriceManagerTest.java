package com.levanilla.rogue.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceManagerTest {

    @BeforeEach
    void setNormalDifficulty() {
        DifficultyManager.setDifficulty(DifficultyManager.Difficulty.NORMAL);
    }

    @AfterEach
    void resetDifficulty() {
        DifficultyManager.setDifficulty(DifficultyManager.Difficulty.NORMAL);
    }

    @Test
    void killRewardScalesWithFloorAndCaps() {
        assertEquals(23, PriceManager.getKillReward(1));
        assertEquals(50, PriceManager.getKillReward(10));
        assertEquals(GameConstants.KILL_REWARD_HIGH_CAP, PriceManager.getKillReward(100));
        assertEquals(GameConstants.KILL_REWARD_ENDLESS_CAP, PriceManager.getKillReward(120));
    }

    @Test
    void initialGoldComesFromGameConstants() {
        assertEquals(GameConstants.INITIAL_GOLD, PriceManager.getInitialGold());
    }

    @Test
    void deathPenaltyUsesActiveDifficultyAndNeverGoesNegative() {
        assertEquals(50, PriceManager.getDeathPenalty(1000));
        assertEquals(0, PriceManager.getDeathPenalty(-1000));

        DifficultyManager.setDifficulty(DifficultyManager.Difficulty.HARD);
        assertEquals(150, PriceManager.getDeathPenalty(1000));
    }

    @Test
    void upgradePricesUseKnownBasesAndLinearLevelStep() {
        assertEquals(GameConstants.STASH_UPGRADE_COST_BASE, PriceManager.getUpgradePrice("rogue:stash_upgrade", 0));
        assertEquals(GameConstants.INV_UPGRADE_COST_BASE * 3, PriceManager.getUpgradePrice("rogue:inv_upgrade", 2));
        assertEquals(GameConstants.AMMO_CAP_UPGRADE_COST_BASE * 2, PriceManager.getUpgradePrice("rogue:ammo_capacity_upgrade", 1));
        assertEquals(GameConstants.MELEE_UPGRADE_COST_BASE * 4, PriceManager.getUpgradePrice("rogue:melee_upgrade", 3));
        assertEquals(GameConstants.FLASHLIGHT_UPGRADE_COST_BASE * 5, PriceManager.getUpgradePrice("rogue:flashlight_upgrade", 4));
        assertEquals(2000, PriceManager.getUpgradePrice("rogue:unknown_upgrade", 0));
    }

    @Test
    void randomPerkPriceUsesDedicatedStep() {
        assertEquals(GameConstants.RANDOM_PERK_BASE_PRICE, PriceManager.getUpgradePrice("rogue:random_perk", 0));
        assertEquals(
            GameConstants.RANDOM_PERK_BASE_PRICE + GameConstants.RANDOM_PERK_PRICE_STEP * 3,
            PriceManager.getUpgradePrice("rogue:random_perk", 3)
        );
    }

    @Test
    void ammoBuyPriceClassifiesHighValueAmmoByName() {
        assertEquals(150, PriceManager.getAmmoBuyPrice("tacz:9mm"));
        assertEquals(300, PriceManager.getAmmoBuyPrice("tacz:50_bmg"));
        assertEquals(500, PriceManager.getAmmoBuyPrice("tacz:rpg_rocket"));
        assertEquals(500, PriceManager.getAmmoBuyPrice("tacz:40mm_grenade"));
    }
}
