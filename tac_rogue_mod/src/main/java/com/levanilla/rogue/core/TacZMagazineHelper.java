package com.levanilla.rogue.core;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class TacZMagazineHelper {
    private TacZMagazineHelper() {}

    public static int getEffectiveMagazineSize(ItemStack gunStack, LivingEntity holder, int fallbackBase) {
        if (gunStack == null || gunStack.isEmpty()) return Math.max(1, fallbackBase);

        int baseWithAttachment = getBaseWithAttachments(gunStack, fallbackBase);
        int withRarity = WeaponRarity.getEffectiveMagazineSize(gunStack, baseWithAttachment);
        float magPerk = sumMagSizePerk(holder);
        if (magPerk <= 0.0F) return Math.max(1, withRarity);
        return Math.max(1, Math.round(withRarity * (1.0F + magPerk / 100.0F)));
    }

    public static int getTacZBaseMagazineSize(ItemStack gunStack, int fallbackBase) {
        if (gunStack == null || gunStack.isEmpty()) return Math.max(1, fallbackBase);
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return Math.max(1, fallbackBase);
        try {
            ResourceLocation gunId = gun.getGunId(gunStack);
            return TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> Math.max(1, index.getGunData().getAmmoAmount()))
                .orElse(Math.max(1, fallbackBase));
        } catch (Throwable ignored) {
            return Math.max(1, fallbackBase);
        }
    }

    private static int getBaseWithAttachments(ItemStack gunStack, int fallbackBase) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return Math.max(1, fallbackBase);
        try {
            ResourceLocation gunId = gun.getGunId(gunStack);
            GunData gunData = TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> index.getGunData())
                .orElse(null);
            if (gunData == null) return Math.max(1, fallbackBase);
            return Math.max(1, AttachmentDataUtils.getAmmoCountWithAttachment(gunStack, gunData));
        } catch (Throwable ignored) {
            return Math.max(1, fallbackBase);
        }
    }

    private static float sumMagSizePerk(LivingEntity holder) {
        if (holder == null) return 0.0F;
        float total = 0.0F;
        for (String tag : holder.getTags()) {
            if (!tag.startsWith("perk:" + PerkDefinition.Category.MAG_SIZE.name())) continue;
            total += PerkDefinition.fromTag(tag).calculateEffect();
        }
        return total;
    }
}
