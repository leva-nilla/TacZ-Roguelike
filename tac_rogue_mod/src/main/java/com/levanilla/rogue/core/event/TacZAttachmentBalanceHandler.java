package com.levanilla.rogue.core.event;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.AttachmentPropertyEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage.DistanceDamagePair;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunFireModeAdjustData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedList;

@Mod.EventBusSubscriber(modid = "tac_rogue")
public final class TacZAttachmentBalanceHandler {
    private static final String LRADD_NAMESPACE = "lradd";
    private static final float LRADD_MAX_DAMAGE_MULT = 1.35f;
    private static final float LRADD_MAX_HEADSHOT_MULT = 1.35f;
    private static final float LRADD_MAX_HEADSHOT_ADD = 0.45f;
    private static final float LRADD_MAX_ARMOR_IGNORE = 0.35f;

    private TacZAttachmentBalanceHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttachmentProperty(AttachmentPropertyEvent event) {
        ItemStack gun = event.getGunItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null || !hasLraddAttachment(gun, iGun)) return;

        ResourceLocation gunId = iGun.getGunId(gun);
        TimelessAPI.getCommonGunIndex(gunId).ifPresent(index -> {
            GunData gunData = index.getGunData();
            clampDamage(event, gun, iGun, gunData);
            clampHeadshot(event, gun, iGun, gunData);
            clampArmorIgnore(event, gun, iGun, gunData);
        });
    }

    private static boolean hasLraddAttachment(ItemStack gun, IGun iGun) {
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            ResourceLocation attachmentId = iGun.getAttachmentId(gun, type);
            if (isLradd(attachmentId)) return true;
        }
        return false;
    }

    private static boolean isLradd(ResourceLocation id) {
        return id != null && LRADD_NAMESPACE.equals(id.getNamespace());
    }

    private static void clampDamage(AttachmentPropertyEvent event, ItemStack gun, IGun iGun, GunData gunData) {
        LinkedList<DistanceDamagePair> current = event.getCacheProperty().getCache(GunProperties.DAMAGE);
        if (current == null || current.isEmpty()) return;

        LinkedList<DistanceDamagePair> base = baseDamagePairs(gun, iGun, gunData);
        if (base.isEmpty()) return;

        LinkedList<DistanceDamagePair> clamped = new LinkedList<>();
        for (int i = 0; i < current.size(); i++) {
            DistanceDamagePair pair = current.get(i);
            float baseDamage = base.get(Math.min(i, base.size() - 1)).getDamage();
            float maxDamage = baseDamage > 0.0f ? baseDamage * LRADD_MAX_DAMAGE_MULT : pair.getDamage();
            float damage = Math.min(pair.getDamage(), maxDamage);
            clamped.add(new DistanceDamagePair(pair.getDistance(), damage));
        }
        event.getCacheProperty().setCache(GunProperties.DAMAGE, clamped);
    }

    private static void clampHeadshot(AttachmentPropertyEvent event, ItemStack gun, IGun iGun, GunData gunData) {
        Float current = event.getCacheProperty().getCache(GunProperties.HEADSHOT_MULTIPLIER);
        if (current == null || !Float.isFinite(current)) return;

        float base = baseHeadshot(gun, iGun, gunData);
        float cap = Math.max(base + LRADD_MAX_HEADSHOT_ADD, base * LRADD_MAX_HEADSHOT_MULT);
        event.getCacheProperty().setCache(GunProperties.HEADSHOT_MULTIPLIER, Math.min(current, cap));
    }

    private static void clampArmorIgnore(AttachmentPropertyEvent event, ItemStack gun, IGun iGun, GunData gunData) {
        Float current = event.getCacheProperty().getCache(GunProperties.ARMOR_IGNORE);
        if (current == null || !Float.isFinite(current)) return;

        float base = baseArmorIgnore(gun, iGun, gunData);
        float cap = Math.max(base, LRADD_MAX_ARMOR_IGNORE);
        event.getCacheProperty().setCache(GunProperties.ARMOR_IGNORE, Math.min(current, cap));
    }

    private static LinkedList<DistanceDamagePair> baseDamagePairs(ItemStack gun, IGun iGun, GunData gunData) {
        FireMode fireMode = iGun.getFireMode(gun);
        BulletData bulletData = gunData.getBulletData();
        GunFireModeAdjustData adjust = gunData.getFireModeAdjustData(fireMode);
        float fireAdjust = adjust != null ? adjust.getDamageAmount() : 0.0f;

        LinkedList<DistanceDamagePair> result = new LinkedList<>();
        ExtraDamage extraDamage = bulletData.getExtraDamage();
        if (extraDamage != null && extraDamage.getDamageAdjust() != null) {
            for (DistanceDamagePair pair : extraDamage.getDamageAdjust()) {
                float damage = (pair.getDamage() + fireAdjust) * SyncConfig.DAMAGE_BASE_MULTIPLIER.get().floatValue();
                result.add(new DistanceDamagePair(pair.getDistance(), damage));
            }
        } else {
            float damage = (bulletData.getDamageAmount() + fireAdjust) * SyncConfig.DAMAGE_BASE_MULTIPLIER.get().floatValue();
            result.add(new DistanceDamagePair(Integer.MAX_VALUE, damage));
        }
        return result;
    }

    private static float baseHeadshot(ItemStack gun, IGun iGun, GunData gunData) {
        FireMode fireMode = iGun.getFireMode(gun);
        BulletData bulletData = gunData.getBulletData();
        GunFireModeAdjustData adjust = gunData.getFireModeAdjustData(fireMode);
        ExtraDamage extraDamage = bulletData.getExtraDamage();
        float base = extraDamage != null ? extraDamage.getHeadShotMultiplier() : 0.0f;
        if (adjust != null) base += adjust.getHeadShotMultiplier();
        return base * SyncConfig.HEAD_SHOT_BASE_MULTIPLIER.get().floatValue();
    }

    private static float baseArmorIgnore(ItemStack gun, IGun iGun, GunData gunData) {
        FireMode fireMode = iGun.getFireMode(gun);
        BulletData bulletData = gunData.getBulletData();
        GunFireModeAdjustData adjust = gunData.getFireModeAdjustData(fireMode);
        ExtraDamage extraDamage = bulletData.getExtraDamage();
        float base = extraDamage != null ? extraDamage.getArmorIgnore() : 0.0f;
        if (adjust != null) base += adjust.getArmorIgnore();
        return base * SyncConfig.ARMOR_IGNORE_BASE_MULTIPLIER.get().floatValue();
    }
}
