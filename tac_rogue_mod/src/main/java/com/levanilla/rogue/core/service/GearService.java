package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.*;
import com.levanilla.rogue.networking.TacRogueNetworking;
import com.levanilla.rogue.networking.SyncDataMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 初期装備配布とステータスプリセットの適用ロジック。
 * RogueActionMessage から抽出。
 */
public final class GearService {

    private GearService() {}

    /**
     * プリセットに基づいてステータスと初期装備を配布する。
     */
    public static void applyBalanced(ServerPlayer player) {
        adjustStats(player, GameConstants.BALANCED_HP, GameConstants.BALANCED_ARMOR,
                    GameConstants.BALANCED_STAMINA, GameConstants.BALANCED_SPEED);
        giveStarterGear(player, GameConstants.BALANCED_GUN, GameConstants.BALANCED_AMMO,
                        GameConstants.BALANCED_MAG, GameConstants.BALANCED_AMMO_COUNT);
        sendPerkInitPacket(player);
    }

    public static void applyPower(ServerPlayer player) {
        adjustStats(player, GameConstants.POWER_HP, GameConstants.POWER_ARMOR,
                    GameConstants.POWER_STAMINA, GameConstants.POWER_SPEED);
        giveStarterGear(player, GameConstants.POWER_GUN, GameConstants.POWER_AMMO,
                        GameConstants.POWER_MAG, GameConstants.POWER_AMMO_COUNT);
        sendPerkInitPacket(player);
    }

    public static void applyClassic(ServerPlayer player) {
        adjustStats(player, GameConstants.CLASSIC_HP, GameConstants.CLASSIC_ARMOR,
                    GameConstants.CLASSIC_STAMINA, GameConstants.CLASSIC_SPEED);
        giveStarterGear(player, GameConstants.CLASSIC_GUN, GameConstants.CLASSIC_AMMO,
                        GameConstants.CLASSIC_MAG, GameConstants.CLASSIC_AMMO_COUNT);
        sendPerkInitPacket(player);
    }

    /**
     * プレイヤーのステータス（体力、防御力、スタミナ、速度）をプリセットに合わせて調整する。
     */
    public static void adjustStats(ServerPlayer player, float hp, float armor, float stamina, double speedBonus) {
        float hpScale = switch(DifficultyManager.getDifficulty()) {
            case EASY -> 1.5f;
            case HARD -> 0.9f;
            case EXTREME -> 0.75f;
            case IRONMAN -> 0.65f;
            default -> 1.0f;
        };
        hp *= hpScale;

        AttributeInstance hpAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(hp);
            player.setHealth(hp);
        }

        AttributeInstance armorAttr = player.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) {
            armorAttr.setBaseValue(armor);
        }

        AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(GameConstants.SPEED_MODIFIER_UUID);
            if (speedBonus != 0) {
                speedAttr.addTransientModifier(new AttributeModifier(
                    GameConstants.SPEED_MODIFIER_UUID,
                    "Rogue Balance Modifier", speedBonus,
                    AttributeModifier.Operation.MULTIPLY_BASE));
            }
        }

        float baseStamina = stamina * 2.0f;
        StaminaManager.setBaseMaxStamina(player, baseStamina);
        StaminaManager.setMaxStamina(player, baseStamina);
    }

    /**
     * 初期装備をプレイヤーに付与（配布順: 銃→近接→回復→食料→弾薬）。
     */
    public static void giveStarterGear(ServerPlayer player, String gunId, String ammoId, int magSize, int ammoCount) {
        player.getInventory().clearContent();

        // 1. 銃
        try {
            ItemStack gun = RogueItemFactory.createGunStack(gunId, WeaponRarity.Rarity.COMMON);
            if (!gun.isEmpty()) {
                int starterMag = WeaponRarity.getEffectiveMagazineSize(gun, magSize);
                gun.getOrCreateTag().putInt("GunCurrentAmmoCount", starterMag);
                player.getInventory().add(gun);
            }
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("\u00A7c[ERROR] Gun distribution failed: " + gunId));
        }

        // 2. 近接武器
        addMeleeToGear(player);

        // 3. MEDKIT
        player.getInventory().add(createMedkitStack(GameConstants.STARTER_MEDKIT_COUNT));

        // 4. 携帯食料
        ItemStack steak = new ItemStack(Items.COOKED_BEEF, GameConstants.STARTER_STEAK_COUNT);
        CompoundTag steakTag = new CompoundTag();
        steakTag.putBoolean("rogue_always_eat", true);
        steakTag.putBoolean("rogue_item", true);
        steakTag.putInt("CustomModelData", 39003);
        steak.setTag(steakTag);
        player.getInventory().add(steak);

        // 4.5 雪玉 (ステルス用)
        ItemStack snowballs = new ItemStack(Items.SNOWBALL, 16);
        player.getInventory().add(snowballs);

        // 5. 弾薬
        try {
            net.minecraft.world.item.Item ammoItem = ForgeRegistries.ITEMS.getValue(
                new ResourceLocation("tacz", "ammo"));
            if (ammoItem != null) {
                int maxStack = TacZRegistryHelper.getAmmoStackSize(ammoId);
                int remaining = ammoCount;
                while (remaining > 0) {
                    int stackSize = Math.min(remaining, maxStack);
                    ItemStack ammo = new ItemStack(ammoItem);
                    CompoundTag ammoTag = new CompoundTag();
                    ammoTag.putString("AmmoId", ammoId);
                    ammo.setTag(ammoTag);
                    ammo.setCount(stackSize);
                    player.getInventory().add(ammo);
                    remaining -= stackSize;
                }
            }
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("\u00A7c[ERROR] Ammo distribution failed: " + ammoId));
        }

        DeepProgressService.applyStarterPrestigeBonuses(player);
        player.addTag("rogue:gear_selected");
        player.sendSystemMessage(Component.translatable(
            "message.tac_rogue.loadout_selected",
            gunId.substring(gunId.indexOf(":") + 1).toUpperCase()), true);
    }

    private static void addMeleeToGear(ServerPlayer player) {
        try {
            net.minecraft.world.item.Item meleeBase = ForgeRegistries.ITEMS.getValue(
                new ResourceLocation("lrtactical", "melee"));
            if (meleeBase != null && meleeBase != Items.AIR) {
                ItemStack meleeStack = new ItemStack(meleeBase);
                CompoundTag tag = new CompoundTag();
                tag.putString("MeleeWeaponId", "lrtactical:dagger");
                tag.putBoolean("Unbreakable", true);
                meleeStack.setTag(tag);
                player.getInventory().add(meleeStack);
                return;
            }
        } catch (Exception ignored) {}
        player.getInventory().add(new ItemStack(Items.IRON_SWORD));
    }

    private static void sendPerkInitPacket(ServerPlayer player) {
        // SEC-1対策: サーバー側でパーク候補を生成し、セッションに登録してからクライアントに送信
        com.levanilla.rogue.networking.OpenPerkChoiceMessage.sendPerkChoices(player,
            com.levanilla.rogue.networking.OpenPerkChoiceMessage.PerkScreenType.INITIAL);
    }

    public static ItemStack createMedkitStack(int count) {
        ItemStack medkit = new ItemStack(Items.PAPER, count);
        medkit.setHoverName(Component.translatable("item.tac_rogue.medkit"));
        CompoundTag medkitTag = medkit.getOrCreateTag();
        medkitTag.putBoolean("rogue_item", true);
        medkitTag.putBoolean("rogue_medkit", true);
        medkitTag.putInt("CustomModelData", 1001);
        CompoundTag displayTag = medkit.getOrCreateTagElement("display");
        ListTag loreList = new ListTag();
        loreList.add(StringTag.valueOf(
            Component.Serializer.toJson(
                Component.translatable("item.tac_rogue.medkit.lore"))));
        displayTag.put("Lore", loreList);
        return medkit;
    }
}
