package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.TacZRegistryHelper;
import com.levanilla.rogue.core.WeaponRarity;
import com.levanilla.rogue.core.registry.LrTacticalRegistry;
import com.levanilla.rogue.core.registry.ShopCatalog;
import com.levanilla.rogue.core.registry.TacZGunRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

public final class RogueItemFactory {

    private RogueItemFactory() {}

    public static ItemStack createItemStack(ServerPlayer player, String itemId) {
        if (itemId.equals("minecraft:snowball")) {
            return new ItemStack(Items.SNOWBALL, 16);
        }

        if (itemId.startsWith("rogue:")) {
            return createRecoveryItem(itemId);
        }

        String fullId = itemId.contains(":") ? itemId : ("tacz:" + itemId);
        ResourceLocation resLoc = new ResourceLocation(fullId);

        if (LrTacticalRegistry.isAvailable()) {
            for (var melee : ShopCatalog.getBuiltinMelee()) {
                if (melee.id.equals(fullId) || melee.id.equals(itemId)) {
                    return LrTacticalRegistry.createMeleeStack(resLoc);
                }
            }
            for (var tactical : ShopCatalog.getBuiltinTactical()) {
                if (tactical.id.equals(fullId) || tactical.id.equals(itemId)) {
                    return LrTacticalRegistry.createThrowableStack(resLoc, 2);
                }
            }
        }

        if (isKnownAttachment(fullId)) {
            return createAttachmentStack(fullId);
        }
        if (isKnownAmmo(fullId)) {
            return createAmmoStack(player, fullId);
        }
        return createGunStack(fullId);
    }

    public static ItemStack createShopItemStack(ServerPlayer player, String itemId, int shopFloor) {
        if (itemId.equals("minecraft:snowball") || itemId.startsWith("rogue:")) {
            return createItemStack(player, itemId);
        }

        String fullId = itemId.contains(":") ? itemId : ("tacz:" + itemId);
        if (isKnownAttachment(fullId)) return createAttachmentStack(fullId);
        if (isKnownAmmo(fullId)) return createShopAmmoStack(fullId);
        if (isKnownMelee(fullId, itemId)) {
            WeaponRarity.Rarity rarity = WeaponRarity.rollShopRarity(shopFloor, fullId);
            return createMeleeStack(fullId, rarity);
        }
        if (!isShopGun(fullId, itemId)) return createItemStack(player, itemId);

        WeaponRarity.Rarity rarity = WeaponRarity.rollShopRarity(shopFloor, fullId);
        return createGunStack(fullId, rarity);
    }

    public static ItemStack createRewardWeaponStack(ServerPlayer player, String itemId, int floor, net.minecraft.util.RandomSource random) {
        String fullId = itemId.contains(":") ? itemId : ("tacz:" + itemId);
        WeaponRarity.Rarity rarity = WeaponRarity.atLeast(
            WeaponRarity.rollRarity(floor, random), WeaponRarity.Rarity.RARE);
        ItemStack stack = createGunStack(fullId, rarity);
        DeepProgressService.maybeApplyDeepModifier(stack, floor, random, 0.18f);
        return stack;
    }

    public static ItemStack createMeleeStack(String fullId) {
        return createMeleeStack(fullId, null);
    }

    public static ItemStack createMeleeStack(String fullId, WeaponRarity.Rarity rarity) {
        ItemStack stack = ItemStack.EMPTY;
        if (LrTacticalRegistry.isAvailable()) {
            ResourceLocation resLoc = new ResourceLocation(fullId);
            ItemStack apiStack = LrTacticalRegistry.createMeleeStack(resLoc);
            if (!apiStack.isEmpty()) stack = apiStack;
        }

        if (stack.isEmpty()) {
            net.minecraft.world.item.Item meleeBase = ForgeRegistries.ITEMS.getValue(
                new ResourceLocation("lrtactical", "melee"));
            if (meleeBase == null || meleeBase == Items.AIR) return ItemStack.EMPTY;
            stack = new ItemStack(meleeBase);
        }

        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("MeleeWeaponId", fullId);
        tag.putBoolean("Unbreakable", true);

        if (rarity != null) {
            WeaponRarity.applyRarity(stack, rarity);
        }
        return stack;
    }

    public static ItemStack createAttachmentStack(String fullId) {
        if (!TacZGunRegistry.isStandaloneAttachmentId(fullId)) {
            return ItemStack.EMPTY;
        }
        net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(
            new ResourceLocation("tacz", "attachment"));
        if (item != null) {
            ItemStack stack = new ItemStack(item);
            CompoundTag tag = new CompoundTag();
            tag.putString("AttachmentId", fullId);
            stack.setTag(tag);
            return stack;
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack createAmmoStack(ServerPlayer player, String fullId) {
        return createAmmoStack(player, fullId, true);
    }

    public static ItemStack createShopAmmoStack(String fullId) {
        return createAmmoStack(null, fullId, false);
    }

    private static ItemStack createAmmoStack(ServerPlayer player, String fullId, boolean applyAmmoPouch) {
        net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(
            new ResourceLocation("tacz", "ammo"));
        if (item != null) {
            ItemStack stack = new ItemStack(item);
            CompoundTag tag = new CompoundTag();
            tag.putString("AmmoId", fullId);
            stack.setTag(tag);

            int baseAmount = TacZRegistryHelper.getAmmoStackSize(fullId);
            stack.setCount(applyAmmoPouch ? RunManager.getAmmoStackLimit(player, baseAmount) : Math.max(1, baseAmount));

            return stack;
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack createGunStack(String fullId) {
        return createGunStack(fullId, WeaponRarity.Rarity.COMMON);
    }

    public static ItemStack createGunStack(String fullId, WeaponRarity.Rarity rarity) {
        net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(
            new ResourceLocation("tacz", "modern_kinetic_gun"));
        if (item != null) {
            ItemStack stack = new ItemStack(item);
            CompoundTag tag = new CompoundTag();
            tag.putString("GunId", fullId);
            tag.putString("GunFireMode", "SEMI");
            tag.putBoolean("HasBulletInBarrel", true);
            stack.setTag(tag);
            WeaponRarity.applyRarity(stack, rarity);
            int magazineSize = com.levanilla.rogue.core.TacZMagazineHelper.getEffectiveMagazineSize(
                stack, null, TacZRegistryHelper.getMagazineSize(fullId));
            stack.getOrCreateTag().putInt("GunCurrentAmmoCount", magazineSize);
            return stack;
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack createRecoveryItem(String itemId) {
        return switch (itemId) {
            case "rogue:medkit" -> GearService.createMedkitStack(1);
            case "rogue:field_ration" -> {
                var stack = new ItemStack(Items.COOKED_BEEF, 3);
                applyRecoveryLore(stack,
                    Component.translatable("item.tac_rogue.field_ration"),
                    Component.translatable("item.tac_rogue.field_ration.lore.0"),
                    Component.translatable("item.tac_rogue.field_ration.lore.1"));
                stack.getOrCreateTag().putInt("CustomModelData", 39003);
                yield stack;
            }
            case "rogue:stamina_shot" -> {
                var stack = new ItemStack(Items.HONEY_BOTTLE, 1);
                applyRecoveryLore(stack,
                    Component.translatable("item.tac_rogue.stamina_shot"),
                    Component.translatable("item.tac_rogue.stamina_shot.lore.0"),
                    Component.translatable("item.tac_rogue.stamina_shot.lore.1"));
                stack.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 1);
                stack.getOrCreateTag().putInt("HideFlags", 1);
                stack.getOrCreateTag().putInt("CustomModelData", 39005);
                yield stack;
            }
            case "rogue:bandage" -> {
                var stack = new ItemStack(Items.STRING, 1);
                applyRecoveryLore(stack,
                    Component.translatable("item.tac_rogue.bandage"),
                    Component.translatable("item.tac_rogue.bandage.lore.0"),
                    Component.translatable("item.tac_rogue.bandage.lore.1"));
                stack.getOrCreateTag().putInt("CustomModelData", 39010);
                yield stack;
            }
            case "rogue:armor_plate" -> {
                var stack = new ItemStack(Items.IRON_INGOT, 1);
                applyRecoveryLore(stack,
                    Component.translatable("item.tac_rogue.armor_plate"),
                    Component.translatable("item.tac_rogue.armor_plate.lore.0"),
                    Component.translatable("item.tac_rogue.armor_plate.lore.1"));
                stack.getOrCreateTag().putInt("CustomModelData", 39011);
                yield stack;
            }
            case "rogue:adrenaline" -> {
                var stack = new ItemStack(Items.GLASS_BOTTLE, 1);
                applyRecoveryLore(stack,
                    Component.translatable("item.tac_rogue.adrenaline"),
                    Component.translatable("item.tac_rogue.adrenaline.lore.0"),
                    Component.translatable("item.tac_rogue.adrenaline.lore.1"),
                    Component.translatable("item.tac_rogue.adrenaline.lore.2"));
                stack.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 1);
                stack.getOrCreateTag().putInt("HideFlags", 1);
                stack.getOrCreateTag().putInt("CustomModelData", 39012);
                yield stack;
            }
            case "rogue:emp_device" -> {
                var stack = new ItemStack(Items.REDSTONE, 1);
                applyRecoveryLore(stack,
                    Component.translatable("item.tac_rogue.emp_device"),
                    Component.translatable("item.tac_rogue.emp_device.lore.0"),
                    Component.translatable("item.tac_rogue.emp_device.lore.1"),
                    Component.translatable("item.tac_rogue.emp_device.lore.2"));
                stack.getOrCreateTag().putInt("CustomModelData", 39013);
                yield stack;
            }
            case "rogue:ballistic_charm" -> createUtilityItem(Items.PRISMARINE_SHARD, itemId, 39200, 1);
            case "rogue:quickdraw_charm" -> createUtilityItem(Items.RABBIT_FOOT, itemId, 39201, 1);
            case "rogue:ammo_saver_charm" -> createUtilityItem(Items.AMETHYST_SHARD, itemId, 39202, 1);
            case "rogue:ballistic_insert" -> createUtilityItem(Items.NETHERITE_SCRAP, itemId, 39203, 2);
            case "rogue:mag_pouch_rig" -> createUtilityItem(Items.LEATHER, itemId, 39204, 2);
            case "rogue:terminal_decoder" -> createUtilityItem(Items.COMPARATOR, itemId, 39205, 1);
            case "rogue:recovery_beacon" -> createUtilityItem(Items.EMERALD, itemId, 39206, 1);
            case "rogue:defense_sensor" -> createUtilityItem(Items.OBSERVER, itemId, 39207, 1);
            case "rogue:blood_dogtag" -> createUtilityItem(Items.NAME_TAG, itemId, 39208, 2);
            case "rogue:overheat_core" -> createUtilityItem(Items.BLAZE_POWDER, itemId, 39209, 2);
            case "rogue:smoke_canister" -> createUtilityItem(Items.GUNPOWDER, itemId, 39210, 1);
            case "rogue:flash_charge" -> createUtilityItem(Items.GLOWSTONE_DUST, itemId, 39211, 1);
            case "rogue:noise_maker" -> createUtilityItem(Items.NOTE_BLOCK, itemId, 39212, 1);
            case "rogue:portable_shield" -> createUtilityItem(Items.SHIELD, itemId, 39213, 1);
            case "rogue:micro_turret" -> createUtilityItem(Items.DISPENSER, itemId, 39214, 1);
            case "rogue:maintenance_kit" -> createUtilityItem(Items.IRON_NUGGET, itemId, 39215, 1);
            case "rogue:ballistic_computer" -> createUtilityItem(Items.CLOCK, itemId, 39216, 1);
            case "rogue:range_card" -> createUtilityItem(Items.MAP, itemId, 39217, 1);
            case "rogue:inv_upgrade" -> createSpecialPreviewItem(
                Items.LEATHER, "shop_item.tac_rogue.inv_upgrade", 39100);
            case "rogue:stash_upgrade" -> createSpecialPreviewItem(
                Items.ENDER_CHEST, "shop_item.tac_rogue.stash_upgrade", 39101);
            case "rogue:ammo_capacity_upgrade" -> createSpecialPreviewItem(
                Items.ARROW, "shop_item.tac_rogue.ammo_capacity_upgrade", 39102);
            case "rogue:melee_upgrade" -> createSpecialPreviewItem(
                Items.IRON_SWORD, "shop_item.tac_rogue.melee_upgrade", 39103);
            case "rogue:flashlight_upgrade" -> createSpecialPreviewItem(
                Items.TORCH, "shop_item.tac_rogue.flashlight_upgrade", 39104);
            case "rogue:random_perk" -> createSpecialPreviewItem(
                Items.ENCHANTED_BOOK, "shop_item.tac_rogue.random_perk", 39105);
            default -> ItemStack.EMPTY;
        };
    }

    private static ItemStack createUtilityItem(net.minecraft.world.item.Item item, String itemId, int customModelData, int loreLines) {
        ItemStack stack = new ItemStack(item);
        String suffix = RogueUtilityItemService.translationSuffix(itemId);
        Component[] lore = new Component[Math.max(1, loreLines + 1)];
        lore[0] = Component.translatable(RogueUtilityItemService.isPassiveUtilityId(itemId)
            ? "item.tac_rogue.utility.active_limit"
            : "item.tac_rogue.utility.consumable");
        for (int i = 0; i < loreLines; i++) {
            lore[i + 1] = Component.translatable("item.tac_rogue." + suffix + ".lore." + i);
        }
        applyRecoveryLore(stack, Component.translatable("item.tac_rogue." + suffix), lore);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(RogueUtilityItemService.UTILITY_ID_KEY, itemId);
        tag.putInt("CustomModelData", customModelData);
        if (RogueUtilityItemService.isConsumableUtilityId(itemId)) {
            tag.putBoolean("TacRogueConsumableUtility", true);
        } else {
            tag.putBoolean("TacRoguePassiveUtility", true);
        }
        return stack;
    }

    private static ItemStack createSpecialPreviewItem(net.minecraft.world.item.Item item, String nameKey, int customModelData) {
        ItemStack stack = new ItemStack(item);
        stack.setHoverName(Component.translatable(nameKey));
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean("rogue_item", true);
        tag.putBoolean("TacRoguePreviewOnly", true);
        tag.putInt("CustomModelData", customModelData);
        return stack;
    }

    private static boolean isKnownAttachment(String fullId) {
        return TacZRegistryHelper.getAllAttachmentIds().contains(fullId);
    }

    private static boolean isKnownAmmo(String fullId) {
        return TacZRegistryHelper.getAllAmmoIds().contains(fullId);
    }

    private static boolean isKnownMelee(String fullId, String itemId) {
        if (!LrTacticalRegistry.isAvailable()) return false;
        for (var melee : ShopCatalog.getBuiltinMelee()) {
            if (melee.id.equals(fullId) || melee.id.equals(itemId)) return true;
        }
        return false;
    }

    private static boolean isShopGun(String fullId, String itemId) {
        for (ShopCatalog.ShopItem item : TacZRegistryHelper.getAllShopItems()) {
            if (item.id.equals(fullId) || item.id.equals(itemId)) {
                return item.category == ShopCatalog.Category.PISTOL
                    || item.category == ShopCatalog.Category.RIFLE
                    || item.category == ShopCatalog.Category.SMG
                    || item.category == ShopCatalog.Category.SHOTGUN
                    || item.category == ShopCatalog.Category.SNIPER
                    || item.category == ShopCatalog.Category.LMG
                    || item.category == ShopCatalog.Category.EXPLOSIVE;
            }
        }
        return true;
    }

    private static void applyRecoveryLore(ItemStack stack, Component name, Component... lore) {
        stack.setHoverName(name);
        CompoundTag display = stack.getOrCreateTagElement("display");
        ListTag loreList = new ListTag();
        for (Component line : lore) {
            loreList.add(StringTag.valueOf(Component.Serializer.toJson(line)));
        }
        display.put("Lore", loreList);
        stack.getOrCreateTag().putBoolean("rogue_item", true);
    }
}
