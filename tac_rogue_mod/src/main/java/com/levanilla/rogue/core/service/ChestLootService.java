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

    private ChestLootService() {}

    public static String claimKey(int floor, int chestIndex) {
        return Math.max(1, floor) + ":" + Math.max(0, chestIndex);
    }

    public static List<ItemStack> generatePersonalChestLoot(ServerPlayer player, int floor, int chestIndex) {
        int safeFloor = Math.max(1, floor);
        Random rand = new Random(stableSeed(player.getUUID(), safeFloor, Math.max(0, chestIndex)));
        List<ItemStack> loot = new ArrayList<>();

        addIfPresent(loot, rollRecovery(rand, safeFloor));
        addAmmo(loot, player, rand, safeFloor);
        rollDefense(loot, rand, safeFloor);
        rollTactical(loot, rand, safeFloor);
        rollMaterial(loot, rand, safeFloor);
        rollAttachment(loot, rand, safeFloor);
        rollWeapon(loot, rand, safeFloor);

        return loot;
    }

    private static long stableSeed(UUID uuid, int floor, int chestIndex) {
        long seed = 0x7E57_1A2B_D4C3_9F01L;
        seed ^= uuid.getMostSignificantBits();
        seed = Long.rotateLeft(seed, 19) ^ uuid.getLeastSignificantBits();
        seed ^= (long) floor * 0x9E37_79B9_7F4A_7C15L;
        seed = Long.rotateLeft(seed, 23) ^ ((long) chestIndex * 0xD1B5_4A32_D192_ED03L);
        return seed;
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
        float chance = floor <= 4 ? 0.25F : floor <= 14 ? 0.35F : floor <= 34 ? 0.45F : 0.55F;
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
        float chance = floor <= 4 ? 0.25F : floor <= 9 ? 0.35F : floor <= 24 ? 0.45F : 0.55F;
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

    private static void rollMaterial(List<ItemStack> loot, Random rand, int floor) {
        float chance = floor <= 9 ? 0.45F : floor <= 29 ? 0.50F : 0.55F;
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
        float chance = clamp(0.10F + floor * 0.004F, 0.10F, 0.35F);
        if (rand.nextFloat() >= chance) return;
        List<String> attachments = TacZRegistryHelper.getAllAttachmentIds();
        if (attachments.isEmpty()) return;
        addIfPresent(loot, RogueItemFactory.createAttachmentStack(attachments.get(rand.nextInt(attachments.size()))));
    }

    private static void rollWeapon(List<ItemStack> loot, Random rand, int floor) {
        if (floor < 6) return;
        float chance = clamp(0.06F + (floor - 6) * 0.006F, 0.06F, 0.28F);
        if (rand.nextFloat() >= chance) return;
        List<ShopCatalog.ShopItem> candidates = weaponCandidates(floor, true);
        if (candidates.isEmpty()) return;
        ShopCatalog.ShopItem chosen = candidates.get(rand.nextInt(candidates.size()));
        WeaponRarity.Rarity rarity = WeaponRarity.rollRarity(
            Math.max(1, floor - 5), RandomSource.create(rand.nextLong()));
        addIfPresent(loot, RogueItemFactory.createGunStack(chosen.id, rarity));
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

            int weight = item.category == ShopCatalog.Category.EXPLOSIVE ? 1 : 4;
            for (int i = 0; i < weight; i++) weighted.add(item);
        }

        return !weighted.isEmpty() ? weighted : fallback;
    }

    private static boolean isAllowedChestWeaponCategory(ShopCatalog.Category category, int floor, boolean forWeaponDrop) {
        if (category == null || category == ShopCatalog.Category.MELEE || category == ShopCatalog.Category.TACTICAL) return false;
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
        applyRogueLore(stack, "\u00a76* EMERGENCY RATION", new String[]{
            "\u00a77Restores health in an emergency.",
            "\u00a77Right click to recover HP.",
            "\u00a78\u00a7oRarity: \u00a7eRARE"
        }, 39001);
        stack.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 1);
        stack.getOrCreateTag().putInt("HideFlags", 1);
        return stack;
    }

    private static ItemStack createGoldCache() {
        ItemStack stack = new ItemStack(Items.RAW_GOLD, 1);
        applyRogueLore(stack, "\u00a7e* GOLD CACHE", new String[]{
            "\u00a77A small cache of gold.",
            "\u00a77Right click in lobby to gain \u00a7e" + GameConstants.GOLD_CACHE_VALUE + " G\u00a77.",
            "\u00a78\u00a7oRarity: \u00a77COMMON"
        }, 39004);
        return stack;
    }

    private static ItemStack createScrapMetal(int count) {
        ItemStack stack = new ItemStack(Items.RAW_IRON, Math.max(1, count));
        applyRogueLore(stack, "\u00a78* SCRAP METAL", new String[]{
            "\u00a77Usable scrap material.",
            "\u00a77Right click in lobby to gain \u00a7e" + GameConstants.SCRAP_SELL_VALUE + " G\u00a77.",
            "\u00a78\u00a7oRarity: \u00a77COMMON"
        }, 39006);
        return stack;
    }

    private static void applyRogueLore(ItemStack stack, String name, String[] lore, int customModelData) {
        stack.setHoverName(Component.literal(name));
        CompoundTag display = stack.getOrCreateTagElement("display");
        ListTag loreList = new ListTag();
        for (String line : lore) {
            loreList.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(line))));
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
