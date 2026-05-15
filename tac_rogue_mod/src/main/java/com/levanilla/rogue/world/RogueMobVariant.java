package com.levanilla.rogue.world;

import com.levanilla.rogue.core.RogueActManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Random;

/**
 * Tac Rogue original enemy variants.
 *
 * These are intentionally built on top of stable vanilla entities for now. That
 * keeps the gameplay tunable before committing to custom models, animations, or
 * a new entity registry.
 */
public final class RogueMobVariant {

    private static final String VARIANT_TAG = "tac_rogue_variant";
    private static final String VARIANT_ID_KEY = "TacRogueVariant";

    public enum Role {
        RUSHER,
        BRUISER,
        RANGED,
        DISRUPTOR,
        ELITE
    }

    private static final List<Definition> VARIANTS = List.of(
        new Definition("ash_stalker", "\u00A76[Ash Stalker]",
            EntityType.HUSK, Role.RUSHER, 1, 18, RogueMobVariant::applyAshStalker),
        new Definition("rift_marauder", "\u00A75[Rift Marauder]",
            EntityType.ZOMBIE, Role.BRUISER, 3, 16, RogueMobVariant::applyRiftMarauder),
        new Definition("plague_crawler", "\u00A72[Plague Crawler]",
            EntityType.CAVE_SPIDER, Role.DISRUPTOR, 5, 14, RogueMobVariant::applyPlagueCrawler),
        new Definition("iron_bruiser", "\u00A77[Iron Bruiser]",
            EntityType.VINDICATOR, Role.BRUISER, 8, 12, RogueMobVariant::applyIronBruiser),
        new Definition("rift_gunner", "\u00A79[Rift Gunner]",
            EntityType.PILLAGER, Role.RANGED, 10, 10, RogueMobVariant::applyRiftGunner),
        new Definition("void_wraith", "\u00A78[Void Wraith]",
            EntityType.WITHER_SKELETON, Role.ELITE, 15, 8, RogueMobVariant::applyVoidWraith),
        new Definition("phase_commando", "\u00A7b[Phase Commando]",
            EntityType.PILLAGER, Role.RANGED, 30, 8, RogueMobVariant::applyPhaseCommando),
        new Definition("aegis_bruiser", "\u00A73[Aegis Bruiser]",
            EntityType.VINDICATOR, Role.BRUISER, 50, 6, RogueMobVariant::applyAegisBruiser)
    );

    private RogueMobVariant() {
    }

    public static Roll roll(ServerLevel level, int floor, Random random) {
        if (!shouldUseVariant(floor, random)) {
            return null;
        }

        Definition definition = pickDefinition(floor, random);
        if (definition == null) {
            return null;
        }

        Mob mob = definition.type.create(level);
        return mob == null ? null : new Roll(definition, mob);
    }

    public static void apply(Roll roll, int floor, Random random) {
        if (roll == null || roll.mob == null) {
            return;
        }

        Mob mob = roll.mob;
        Definition definition = roll.definition;

        mob.addTag(VARIANT_TAG);
        mob.addTag("tac_rogue_variant_" + definition.id);
        mob.addTag("tac_rogue_role_" + definition.role.name().toLowerCase(java.util.Locale.ROOT));
        mob.getPersistentData().putString(VARIANT_ID_KEY, definition.id);
        mob.getPersistentData().putString("TacRogueVariantRole", definition.role.name());
        mob.setCustomName(Component.literal(definition.displayName));
        mob.setCustomNameVisible(false);

        definition.customizer.apply(mob, floor, random);
        applyHighFloorPressure(mob, definition.role, floor);

        AttributeInstance maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            mob.setHealth((float) maxHealth.getValue());
        }
    }

    private static boolean shouldUseVariant(int floor, Random random) {
        double phaseBonus = RogueActManager.getPhase(floor).variantChanceBonus;
        double chance = Math.min(0.70, 0.20 + Math.max(0, floor - 1) * 0.015 + phaseBonus);
        return random.nextDouble() < chance;
    }

    private static Definition pickDefinition(int floor, Random random) {
        int totalWeight = 0;
        for (Definition definition : VARIANTS) {
            if (floor >= definition.minFloor) {
                totalWeight += definition.weight;
            }
        }
        if (totalWeight <= 0) {
            return null;
        }

        int roll = random.nextInt(totalWeight);
        for (Definition definition : VARIANTS) {
            if (floor < definition.minFloor) {
                continue;
            }
            roll -= definition.weight;
            if (roll < 0) {
                return definition;
            }
        }
        return null;
    }

    private static void applyAshStalker(Mob mob, int floor, Random random) {
        multiplyAttribute(mob, Attributes.MAX_HEALTH, 0.9);
        multiplyAttribute(mob, Attributes.ATTACK_DAMAGE, 1.2);
        multiplyAttribute(mob, Attributes.MOVEMENT_SPEED, 1.25);
        mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
    }

    private static void applyRiftMarauder(Mob mob, int floor, Random random) {
        multiplyAttribute(mob, Attributes.MAX_HEALTH, 1.25);
        multiplyAttribute(mob, Attributes.ATTACK_DAMAGE, 1.15);
        mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
    }

    private static void applyPlagueCrawler(Mob mob, int floor, Random random) {
        multiplyAttribute(mob, Attributes.MAX_HEALTH, 0.85);
        multiplyAttribute(mob, Attributes.MOVEMENT_SPEED, 1.35);
        mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 0, false, false));
    }

    private static void applyIronBruiser(Mob mob, int floor, Random random) {
        multiplyAttribute(mob, Attributes.MAX_HEALTH, 1.8);
        multiplyAttribute(mob, Attributes.ATTACK_DAMAGE, 1.25);
        multiplyAttribute(mob, Attributes.MOVEMENT_SPEED, 0.85);
        setAttribute(mob, Attributes.ARMOR, 8.0 + floor * 0.25);
        mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
    }

    private static void applyRiftGunner(Mob mob, int floor, Random random) {
        multiplyAttribute(mob, Attributes.MAX_HEALTH, 1.05);
        multiplyAttribute(mob, Attributes.ATTACK_DAMAGE, 1.35);
        multiplyAttribute(mob, Attributes.MOVEMENT_SPEED, 0.95);
        mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
    }

    private static void applyVoidWraith(Mob mob, int floor, Random random) {
        multiplyAttribute(mob, Attributes.MAX_HEALTH, 1.15);
        multiplyAttribute(mob, Attributes.ATTACK_DAMAGE, 1.45);
        multiplyAttribute(mob, Attributes.MOVEMENT_SPEED, 1.15);
        mob.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
        mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
    }

    private static void applyPhaseCommando(Mob mob, int floor, Random random) {
        multiplyAttribute(mob, Attributes.MAX_HEALTH, 1.15);
        multiplyAttribute(mob, Attributes.ATTACK_DAMAGE, 1.35);
        multiplyAttribute(mob, Attributes.MOVEMENT_SPEED, 1.05);
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        crossbow.enchant(net.minecraft.world.item.enchantment.Enchantments.QUICK_CHARGE, 1);
        mob.setItemSlot(EquipmentSlot.MAINHAND, crossbow);
        mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
        mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
    }

    private static void applyAegisBruiser(Mob mob, int floor, Random random) {
        multiplyAttribute(mob, Attributes.MAX_HEALTH, 1.55);
        multiplyAttribute(mob, Attributes.ATTACK_DAMAGE, 1.35);
        multiplyAttribute(mob, Attributes.MOVEMENT_SPEED, 0.95);
        setAttribute(mob, Attributes.ARMOR, Math.min(24.0, 10.0 + floor * 0.20));
        mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
    }

    private static void applyHighFloorPressure(Mob mob, Role role, int floor) {
        if (floor < 30) {
            return;
        }
        double pressure = Math.min(1.0, Math.max(0.0, (floor - 30) / 70.0));
        addAttribute(mob, Attributes.ARMOR, 2.0 + pressure * 6.0);
        multiplyAttribute(mob, Attributes.ATTACK_DAMAGE, 1.0 + pressure * 0.12);
        multiplyAttribute(mob, Attributes.MOVEMENT_SPEED, 1.0 + pressure * 0.08);

        if (floor >= 50 && role == Role.RANGED) {
            ItemStack mainHand = mob.getMainHandItem();
            if (!mainHand.isEmpty() && mainHand.is(Items.CROSSBOW)) {
                mainHand.enchant(net.minecraft.world.item.enchantment.Enchantments.QUICK_CHARGE, floor >= 75 ? 2 : 1);
            }
        }
    }

    private static void multiplyAttribute(Mob mob, Attribute attribute, double multiplier) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(instance.getBaseValue() * multiplier);
        }
    }

    private static void setAttribute(Mob mob, Attribute attribute, double value) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private static void addAttribute(Mob mob, Attribute attribute, double value) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(instance.getBaseValue() + value);
        }
    }

    public record Roll(Definition definition, Mob mob) {
    }

    public record Definition(
        String id,
        String displayName,
        EntityType<? extends Mob> type,
        Role role,
        int minFloor,
        int weight,
        Customizer customizer
    ) {
    }

    @FunctionalInterface
    public interface Customizer {
        void apply(Mob mob, int floor, Random random);
    }
}
