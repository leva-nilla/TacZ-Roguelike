package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.TacZRegistryHelper;
import com.levanilla.rogue.core.WeaponRarity;
import com.levanilla.rogue.core.registry.LrTacticalRegistry;
import com.levanilla.rogue.core.registry.ShopCatalog;
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
        if (isKnownAmmo(fullId)) return createAmmoStack(player, fullId);
        if (!isShopGun(fullId, itemId)) return createItemStack(player, itemId);

        WeaponRarity.Rarity rarity = WeaponRarity.rollShopRarity(shopFloor, fullId);
        return createGunStack(fullId, rarity);
    }

    public static ItemStack createRewardWeaponStack(ServerPlayer player, String itemId, int floor, net.minecraft.util.RandomSource random) {
        String fullId = itemId.contains(":") ? itemId : ("tacz:" + itemId);
        WeaponRarity.Rarity rarity = WeaponRarity.atLeast(
            WeaponRarity.rollRarity(floor, random), WeaponRarity.Rarity.RARE);
        return createGunStack(fullId, rarity);
    }

    public static ItemStack createMeleeStack(String fullId) {
        if (LrTacticalRegistry.isAvailable()) {
            ResourceLocation resLoc = new ResourceLocation(fullId);
            ItemStack apiStack = LrTacticalRegistry.createMeleeStack(resLoc);
            if (!apiStack.isEmpty()) return apiStack;
        }

        net.minecraft.world.item.Item meleeBase = ForgeRegistries.ITEMS.getValue(
            new ResourceLocation("lrtactical", "melee"));
        if (meleeBase != null && meleeBase != Items.AIR) {
            ItemStack stack = new ItemStack(meleeBase);
            CompoundTag tag = new CompoundTag();
            tag.putString("MeleeWeaponId", fullId);
            tag.putBoolean("Unbreakable", true);
            stack.setTag(tag);
            return stack;
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack createAttachmentStack(String fullId) {
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
        net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(
            new ResourceLocation("tacz", "ammo"));
        if (item != null) {
            ItemStack stack = new ItemStack(item);
            CompoundTag tag = new CompoundTag();
            tag.putString("AmmoId", fullId);
            stack.setTag(tag);

            int baseAmount = TacZRegistryHelper.getAmmoStackSize(fullId);
            stack.setCount(RunManager.getAmmoStackLimit(player, baseAmount));

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
            int magazineSize = WeaponRarity.getEffectiveMagazineSize(stack, TacZRegistryHelper.getMagazineSize(fullId));
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
                applyRecoveryLore(stack, "\u00a7f\u2726 FIELD RATION", new String[]{
                    "\u00a77Right click to consume.",
                    "\u00a77HP +6 / hunger recovery."
                });
                stack.getOrCreateTag().putInt("CustomModelData", 39003);
                yield stack;
            }
            case "rogue:stamina_shot" -> {
                var stack = new ItemStack(Items.HONEY_BOTTLE, 1);
                applyRecoveryLore(stack, "\u00a7b\u2726 STAMINA SHOT", new String[]{
                    "\u00a77Right click to inject.",
                    "\u00a77Restores stamina and raises the maximum temporarily."
                });
                stack.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 1);
                stack.getOrCreateTag().putInt("HideFlags", 1);
                stack.getOrCreateTag().putInt("CustomModelData", 39005);
                yield stack;
            }
            case "rogue:bandage" -> {
                var stack = new ItemStack(Items.STRING, 1);
                applyRecoveryLore(stack, "\u00a7f\u2726 BANDAGE", new String[]{
                    "\u00a77Right click to use.",
                    "\u00a77Restores 15% of max HP."
                });
                stack.getOrCreateTag().putInt("CustomModelData", 39010);
                yield stack;
            }
            case "rogue:armor_plate" -> {
                var stack = new ItemStack(Items.IRON_INGOT, 1);
                applyRecoveryLore(stack, "\u00a7b\u2726 ARMOR PLATE", new String[]{
                    "\u00a77Right click to equip.",
                    "\u00a77Armor +8 for 30 seconds."
                });
                stack.getOrCreateTag().putInt("CustomModelData", 39011);
                yield stack;
            }
            case "rogue:adrenaline" -> {
                var stack = new ItemStack(Items.GLASS_BOTTLE, 1);
                applyRecoveryLore(stack, "\u00a7c\u2726 ADRENALINE SYRINGE", new String[]{
                    "\u00a77Right click to inject.",
                    "\u00a77Damage +30% and speed +20% for 20 seconds.",
                    "\u00a74Applies slowness after the effect ends."
                });
                stack.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 1);
                stack.getOrCreateTag().putInt("HideFlags", 1);
                stack.getOrCreateTag().putInt("CustomModelData", 39012);
                yield stack;
            }
            case "rogue:emp_device" -> {
                var stack = new ItemStack(Items.REDSTONE, 1);
                applyRecoveryLore(stack, "\u00a7e\u2726 EMP DEVICE", new String[]{
                    "\u00a77Right click to discharge.",
                    "\u00a77Slows and weakens hostiles within 10 blocks.",
                    "\u00a77Duration: 5 seconds."
                });
                stack.getOrCreateTag().putInt("CustomModelData", 39013);
                yield stack;
            }
            default -> ItemStack.EMPTY;
        };
    }

    private static boolean isKnownAttachment(String fullId) {
        return TacZRegistryHelper.getAllAttachmentIds().contains(fullId);
    }

    private static boolean isKnownAmmo(String fullId) {
        return TacZRegistryHelper.getAllAmmoIds().contains(fullId);
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

    private static void applyRecoveryLore(ItemStack stack, String name, String[] lore) {
        stack.setHoverName(Component.literal(name));
        CompoundTag display = stack.getOrCreateTagElement("display");
        ListTag loreList = new ListTag();
        for (String line : lore) {
            loreList.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(line))));
        }
        display.put("Lore", loreList);
        stack.getOrCreateTag().putBoolean("rogue_item", true);
    }
}
