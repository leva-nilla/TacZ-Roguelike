package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.GameConstants;
import com.levanilla.rogue.core.ShopStockManager;
import com.levanilla.rogue.core.TacZRegistryHelper;
import com.levanilla.rogue.core.WeaponRarity;
import com.levanilla.rogue.core.registry.ShopCatalog;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class ChestLootService {
    public static final String CHEST_INDEX_KEY = "TacRogueChestIndex";
    public static final String CHEST_TOTAL_KEY = "TacRogueChestTotal";
    public static final String CHEST_LOOT_SEED_KEY = "TacRogueLootSeed";

    private ChestLootService() {}

    public static String claimKey(int floor, int chestIndex) {
        return Math.max(1, floor) + ":" + Math.max(0, chestIndex);
    }

    public static List<ItemStack> generatePersonalChestLoot(ServerPlayer player, int floor, int chestIndex) {
        return generatePersonalChestLoot(player, floor, chestIndex, 0L);
    }

    public static List<ItemStack> generatePersonalChestLoot(ServerPlayer player, int floor, int chestIndex, long chestLootSeed) {
        int safeFloor = Math.max(1, floor);
        int safeChestIndex = Math.max(0, chestIndex);
        Random rand = new Random(stableSeed(player.getUUID(), safeFloor, safeChestIndex, chestLootSeed));
        List<ItemStack> loot = new ArrayList<>();

        if (rand.nextFloat() < recoverySlotChance(safeFloor, safeChestIndex)) {
            addIfPresent(loot, rollRecovery(rand, safeFloor));
        }
        if (rand.nextFloat() < ammoSlotChance(safeFloor, safeChestIndex)) {
            addAmmo(loot, player, rand, safeFloor);
        }
        rollDefense(loot, rand, safeFloor);
        rollTactical(loot, rand, safeFloor);
        rollUtility(loot, rand, safeFloor);
        rollMaterial(loot, rand, safeFloor);
        rollAttachment(loot, rand, safeFloor);
        rollWeapon(loot, rand, safeFloor);
        addBandSupportGuarantee(loot, player, rand, safeFloor, safeChestIndex);
        addSupplyLineBonus(loot, player, rand, safeFloor);

        if (loot.isEmpty()) {
            addIfPresent(loot, createScrapMetal(1));
        }
        return loot;
    }

    private static long stableSeed(UUID uuid, int floor, int chestIndex, long chestLootSeed) {
        long seed = 0x7E57_1A2B_D4C3_9F01L;
        seed ^= uuid.getMostSignificantBits();
        seed = Long.rotateLeft(seed, 19) ^ uuid.getLeastSignificantBits();
        seed ^= (long) floor * 0x9E37_79B9_7F4A_7C15L;
        seed = Long.rotateLeft(seed, 23) ^ ((long) chestIndex * 0xD1B5_4A32_D192_ED03L);
        seed = Long.rotateLeft(seed, 17) ^ chestLootSeed;
        return seed;
    }

    private static float recoverySlotChance(int floor, int chestIndex) {
        float base = floor <= 4 ? 0.85F : floor <= 14 ? 0.75F : floor <= 34 ? 0.68F : 0.62F;
        if (chestIndex > 0) base -= 0.20F;
        return clamp(base, 0.35F, 0.90F);
    }

    private static float ammoSlotChance(int floor, int chestIndex) {
        float base = floor <= 4 ? 0.90F : floor <= 14 ? 0.82F : floor <= 34 ? 0.76F : 0.70F;
        if (chestIndex > 0) base -= 0.20F;
        return clamp(base, 0.45F, 0.95F);
    }

    private static ItemStack rollRecovery(Random rand, int floor) {
        float roll = rand.nextFloat();
        if (floor <= 4) {
            if (roll < 0.45F) return RogueItemFactory.createRecoveryItem("rogue:bandage");
            if (roll < 0.90F) return RogueItemFactory.createRecoveryItem("rogue:field_ration");
            return RogueItemFactory.createRecoveryItem("rogue:medkit");
        }
        if (floor <= 14) {
            if (roll < 0.30F) return RogueItemFactory.createRecoveryItem("rogue:bandage");
            if (roll < 0.70F) return RogueItemFactory.createRecoveryItem("rogue:field_ration");
            if (roll < 0.95F) return RogueItemFactory.createRecoveryItem("rogue:medkit");
            return createEmergencyRation();
        }
        if (floor <= 34) {
            if (roll < 0.20F) return RogueItemFactory.createRecoveryItem("rogue:bandage");
            if (roll < 0.55F) return RogueItemFactory.createRecoveryItem("rogue:field_ration");
            if (roll < 0.90F) return RogueItemFactory.createRecoveryItem("rogue:medkit");
            return createEmergencyRation();
        }
        if (roll < 0.15F) return RogueItemFactory.createRecoveryItem("rogue:bandage");
        if (roll < 0.45F) return RogueItemFactory.createRecoveryItem("rogue:field_ration");
        if (roll < 0.80F) return RogueItemFactory.createRecoveryItem("rogue:medkit");
        return createEmergencyRation();
    }

    private static void addAmmo(List<ItemStack> loot, ServerPlayer player, Random rand, int floor) {
        List<ShopCatalog.ShopItem> sources = weaponCandidates(floor, false);
        if (sources.isEmpty()) return;
        ShopCatalog.ShopItem source = sources.get(rand.nextInt(sources.size()));
        String ammoId = TacZRegistryHelper.getAmmoForGun(source.id);
        addIfPresent(loot, RogueItemFactory.createAmmoStack(player, ammoId));
    }

    private static void rollDefense(List<ItemStack> loot, Random rand, int floor) {
        float chance = floor <= 4 ? 0.18F : floor <= 14 ? 0.26F : floor <= 34 ? 0.34F : 0.40F;
        if (rand.nextFloat() >= chance) return;
        float roll = rand.nextFloat();
        if (floor <= 4) {
            addIfPresent(loot, RogueItemFactory.createRecoveryItem("rogue:armor_plate"));
        } else if (floor <= 14) {
            addIfPresent(loot, roll < 0.80F
                ? RogueItemFactory.createRecoveryItem("rogue:armor_plate")
                : RogueItemFactory.createRecoveryItem("rogue:medkit"));
        } else if (floor <= 34) {
            if (roll < 0.75F) addIfPresent(loot, RogueItemFactory.createRecoveryItem("rogue:armor_plate"));
            else if (roll < 0.90F) addIfPresent(loot, RogueItemFactory.createRecoveryItem("rogue:medkit"));
            else addIfPresent(loot, createEmergencyRation());
        } else {
            if (roll < 0.70F) addIfPresent(loot, RogueItemFactory.createRecoveryItem("rogue:armor_plate"));
            else if (roll < 0.85F) addIfPresent(loot, RogueItemFactory.createRecoveryItem("rogue:medkit"));
            else addIfPresent(loot, createEmergencyRation());
        }
    }

    private static void rollTactical(List<ItemStack> loot, Random rand, int floor) {
        float chance = floor <= 4 ? 0.16F : floor <= 9 ? 0.24F : floor <= 24 ? 0.32F : 0.38F;
        if (rand.nextFloat() >= chance) return;
        float roll = rand.nextFloat();
        if (floor <= 4) {
            addIfPresent(loot, RogueItemFactory.createRecoveryItem("rogue:stamina_shot"));
        } else if (floor <= 9) {
            addIfPresent(loot, roll < 0.85F
                ? RogueItemFactory.createRecoveryItem("rogue:stamina_shot")
                : RogueItemFactory.createRecoveryItem("rogue:adrenaline"));
        } else if (floor <= 24) {
            if (roll < 0.65F) addIfPresent(loot, RogueItemFactory.createRecoveryItem("rogue:stamina_shot"));
            else if (roll < 0.90F) addIfPresent(loot, RogueItemFactory.createRecoveryItem("rogue:adrenaline"));
            else addIfPresent(loot, RogueItemFactory.createRecoveryItem("rogue:emp_device"));
        } else {
            if (roll < 0.55F) addIfPresent(loot, RogueItemFactory.createRecoveryItem("rogue:stamina_shot"));
            else if (roll < 0.85F) addIfPresent(loot, RogueItemFactory.createRecoveryItem("rogue:adrenaline"));
            else addIfPresent(loot, RogueItemFactory.createRecoveryItem("rogue:emp_device"));
        }
    }

    private static void rollUtility(List<ItemStack> loot, Random rand, int floor) {
        float chance = floor <= 4 ? 0.08F : floor <= 14 ? 0.14F : floor <= 34 ? 0.20F : 0.25F;
        if (rand.nextFloat() >= chance) return;
        addIfPresent(loot, RogueItemFactory.createRecoveryItem(
            RogueUtilityItemService.rollChestUtilityId(rand, floor)));
    }

    private static void rollMaterial(List<ItemStack> loot, Random rand, int floor) {
        float chance = floor <= 9 ? 0.35F : floor <= 29 ? 0.40F : 0.45F;
        if (rand.nextFloat() >= chance) return;
        if (floor <= 9) {
            addIfPresent(loot, createScrapMetal(1 + rand.nextInt(3)));
        } else if (floor <= 29) {
            addIfPresent(loot, rand.nextFloat() < 0.75F
                ? createScrapMetal(2 + rand.nextInt(3))
                : createGoldCache());
        } else {
            addIfPresent(loot, rand.nextFloat() < 0.60F
                ? createScrapMetal(3 + rand.nextInt(3))
                : createGoldCache());
        }
    }

    private static void rollAttachment(List<ItemStack> loot, Random rand, int floor) {
        float chance = clamp(0.08F + floor * 0.003F, 0.08F, 0.28F);
        if (rand.nextFloat() >= chance) return;
        List<String> attachments = TacZRegistryHelper.getAllAttachmentIds();
        if (attachments.isEmpty()) return;
        addIfPresent(loot, RogueItemFactory.createAttachmentStack(attachments.get(rand.nextInt(attachments.size()))));
    }

    private static void rollWeapon(List<ItemStack> loot, Random rand, int floor) {
        if (floor < 6) return;
        float chance = clamp(0.05F + (floor - 6) * 0.004F, 0.05F, 0.20F);
        if (rand.nextFloat() >= chance) return;
        List<ShopCatalog.ShopItem> candidates = weaponCandidates(floor, true);
        if (candidates.isEmpty()) return;
        ShopCatalog.ShopItem chosen = candidates.get(rand.nextInt(candidates.size()));
        WeaponRarity.Rarity rarity = WeaponRarity.rollRarity(
            Math.max(1, floor - 5), RandomSource.create(rand.nextLong()));
        ItemStack stack = chosen.category == ShopCatalog.Category.MELEE
            ? RogueItemFactory.createMeleeStack(chosen.id, rarity)
            : RogueItemFactory.createGunStack(chosen.id, rarity);
        if (chosen.category != ShopCatalog.Category.MELEE) {
            DeepProgressService.maybeApplyDeepModifier(stack, floor, RandomSource.create(rand.nextLong()), 0.16f);
        }
        addIfPresent(loot, stack);
    }

    private static void addBandSupportGuarantee(List<ItemStack> loot, ServerPlayer player, Random rand, int floor, int chestIndex) {
        if (chestIndex != 0) return;
        int floorInBand = Math.floorMod(floor - 1, 5) + 1;
        float chance = floorInBand == 5 ? 0.50F : 0.35F;
        if (rand.nextFloat() >= chance) return;
        switch (floorInBand) {
            case 1 -> addIfPresent(loot, RogueItemFactory.createRecoveryItem("rogue:armor_plate"));
            case 2 -> addGuaranteedAttachment(loot, rand);
            case 3 -> {
                addIfPresent(loot, createScrapMetal(2 + rand.nextInt(3)));
                if (rand.nextFloat() < 0.50F) {
                    addIfPresent(loot, RogueItemFactory.createRecoveryItem("rogue:stamina_shot"));
                }
            }
            case 4 -> addGuaranteedBandWeaponOrAttachment(loot, player, rand, floor);
            case 5 -> {
                addIfPresent(loot, rand.nextBoolean()
                    ? RogueItemFactory.createRecoveryItem("rogue:medkit")
                    : RogueItemFactory.createRecoveryItem("rogue:armor_plate"));
            }
            default -> { }
        }
    }

    private static void addGuaranteedAttachment(List<ItemStack> loot, Random rand) {
        List<String> attachments = TacZRegistryHelper.getAllAttachmentIds();
        if (attachments.isEmpty()) return;
        addIfPresent(loot, RogueItemFactory.createAttachmentStack(attachments.get(rand.nextInt(attachments.size()))));
    }

    private static void addGuaranteedBandWeaponOrAttachment(List<ItemStack> loot, ServerPlayer player, Random rand, int floor) {
        if (floor < 6) {
            addGuaranteedAttachment(loot, rand);
            return;
        }
        List<ShopCatalog.ShopItem> candidates = RewardSelectionService.chestWeaponCandidates(floor);
        if (candidates.isEmpty()) {
            addGuaranteedAttachment(loot, rand);
            return;
        }
        ShopCatalog.ShopItem chosen = candidates.get(rand.nextInt(candidates.size()));
        WeaponRarity.Rarity rarity = WeaponRarity.rollRarity(Math.max(1, floor - 5), RandomSource.create(rand.nextLong()));
        ItemStack stack = chosen.category == ShopCatalog.Category.MELEE
            ? RogueItemFactory.createMeleeStack(chosen.id, rarity)
            : RogueItemFactory.createGunStack(chosen.id, rarity);
        if (chosen.category != ShopCatalog.Category.MELEE) {
            float chance = 0.18f + DeepProgressService.getSupplyLineLevel(player) * 0.03f;
            DeepProgressService.maybeApplyDeepModifier(stack, floor, RandomSource.create(rand.nextLong()), chance);
        }
        addIfPresent(loot, stack);
    }

    private static void addSupplyLineBonus(List<ItemStack> loot, ServerPlayer player, Random rand, int floor) {
        int level = DeepProgressService.getSupplyLineLevel(player);
        if (level <= 0) return;
        float chance = clamp(0.10F + level * 0.05F, 0.0F, 0.38F);
        if (rand.nextFloat() >= chance) return;

        float roll = rand.nextFloat();
        if (roll < 0.25F) {
            addIfPresent(loot, RogueItemFactory.createRecoveryItem("rogue:armor_plate"));
        } else if (roll < 0.45F) {
            addIfPresent(loot, RogueItemFactory.createRecoveryItem("rogue:medkit"));
        } else if (roll < 0.65F) {
            addGuaranteedAttachment(loot, rand);
        } else if (roll < 0.85F) {
            addIfPresent(loot, createScrapMetal(2 + rand.nextInt(2 + level)));
        } else {
            addGuaranteedBandWeaponOrAttachment(loot, player, rand, Math.max(6, floor));
        }
    }

    private static List<ShopCatalog.ShopItem> weaponCandidates(int floor, boolean forWeaponDrop) {
        int safeFloor = Math.max(1, floor);
        int maxPrice = 700 + safeFloor * 120;
        List<ShopCatalog.ShopItem> weighted = new ArrayList<>();
        List<ShopCatalog.ShopItem> fallback = new ArrayList<>();

        for (ShopCatalog.ShopItem item : TacZRegistryHelper.getAllShopItems()) {
            if (item == null || !isAllowedChestWeaponCategory(item.category, safeFloor, forWeaponDrop)) continue;
            if (!ShopStockManager.isUnlockedByFloor(item.category, safeFloor)) continue;
            if (item.category == ShopCatalog.Category.PISTOL || item.category == ShopCatalog.Category.SMG) {
                fallback.add(item);
            }
            if (item.price > maxPrice) continue;

            int weight = item.category == ShopCatalog.Category.MELEE ? 2
                : item.category == ShopCatalog.Category.EXPLOSIVE ? 1 : 4;
            for (int i = 0; i < weight; i++) weighted.add(item);
        }

        return !weighted.isEmpty() ? weighted : fallback;
    }

    private static boolean isAllowedChestWeaponCategory(ShopCatalog.Category category, int floor, boolean forWeaponDrop) {
        if (category == null || category == ShopCatalog.Category.TACTICAL) return false;
        if (category == ShopCatalog.Category.MELEE) return forWeaponDrop;
        if (!category.isWeapon()) return false;
        if (!forWeaponDrop && floor <= 5) {
            return category == ShopCatalog.Category.PISTOL || category == ShopCatalog.Category.SMG;
        }
        if (floor <= 14) {
            return category == ShopCatalog.Category.PISTOL || category == ShopCatalog.Category.SMG;
        }
        if (floor <= 29) {
            return category == ShopCatalog.Category.PISTOL
                || category == ShopCatalog.Category.SMG
                || category == ShopCatalog.Category.SHOTGUN
                || category == ShopCatalog.Category.RIFLE;
        }
        if (floor <= 49) {
            return category == ShopCatalog.Category.PISTOL
                || category == ShopCatalog.Category.SMG
                || category == ShopCatalog.Category.SHOTGUN
                || category == ShopCatalog.Category.RIFLE
                || category == ShopCatalog.Category.LMG
                || category == ShopCatalog.Category.SNIPER;
        }
        return category == ShopCatalog.Category.PISTOL
            || category == ShopCatalog.Category.SMG
            || category == ShopCatalog.Category.SHOTGUN
            || category == ShopCatalog.Category.RIFLE
            || category == ShopCatalog.Category.LMG
            || category == ShopCatalog.Category.SNIPER
            || category == ShopCatalog.Category.EXPLOSIVE;
    }

    private static ItemStack createEmergencyRation() {
        ItemStack stack = new ItemStack(Items.GOLDEN_APPLE);
        applyRogueLore(stack,
            Component.translatable("item.tac_rogue.emergency_ration"),
            39001,
            Component.translatable("item.tac_rogue.emergency_ration.lore.0"),
            Component.translatable("item.tac_rogue.emergency_ration.lore.1"),
            Component.translatable("item.tac_rogue.rarity.rare"));
        stack.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 1);
        stack.getOrCreateTag().putInt("HideFlags", 1);
        return stack;
    }

    private static ItemStack createGoldCache() {
        ItemStack stack = new ItemStack(Items.RAW_GOLD, 1);
        applyRogueLore(stack,
            Component.translatable("item.tac_rogue.gold_cache"),
            39004,
            Component.translatable("item.tac_rogue.gold_cache.lore.0"),
            Component.translatable("item.tac_rogue.gold_cache.lore.1", GameConstants.GOLD_CACHE_VALUE),
            Component.translatable("item.tac_rogue.rarity.common"));
        return stack;
    }

    private static ItemStack createScrapMetal(int count) {
        ItemStack stack = new ItemStack(Items.RAW_IRON, Math.max(1, count));
        applyRogueLore(stack,
            Component.translatable("item.tac_rogue.scrap_metal"),
            39006,
            Component.translatable("item.tac_rogue.scrap_metal.lore.0"),
            Component.translatable("item.tac_rogue.scrap_metal.lore.1", GameConstants.SCRAP_SELL_VALUE),
            Component.translatable("item.tac_rogue.rarity.common"));
        return stack;
    }

    private static void applyRogueLore(ItemStack stack, Component name, int customModelData, Component... lore) {
        stack.setHoverName(name);
        CompoundTag display = stack.getOrCreateTagElement("display");
        ListTag loreList = new ListTag();
        for (Component line : lore) {
            loreList.add(StringTag.valueOf(Component.Serializer.toJson(line)));
        }
        display.put("Lore", loreList);
        stack.getOrCreateTag().putBoolean("rogue_item", true);
        stack.getOrCreateTag().putInt("CustomModelData", customModelData);
    }

    private static void addIfPresent(List<ItemStack> loot, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) loot.add(stack);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
