package com.levanilla.rogue.mixin;

import com.levanilla.rogue.client.compat.LeaWindsCompat;
import com.levanilla.rogue.core.CommonEventHandler;
import com.levanilla.rogue.core.WeaponRarity;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.resource.pojo.data.gun.BurstData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = com.tacz.guns.client.gameplay.LocalPlayerShoot.class, remap = false)
public abstract class MixinLocalPlayerShootBurst {
    @Shadow @Final private LocalPlayer player;

    @Inject(method = "doShoot", at = @At("HEAD"), remap = false)
    private void tacRogue$syncLeaWindsAimBeforeShoot(GunDisplayInstance display, IGun iGun,
                                                     ItemStack mainHandItem, GunData gunData, long delay,
                                                     float pitch, CallbackInfo ci) {
        LeaWindsCompat.syncThirdPersonGunAimForShot();
    }

    @Redirect(
        method = "doShoot",
        at = @At(value = "INVOKE", target = "Lcom/tacz/guns/resource/pojo/data/gun/GunData;getBurstShootInterval()J"),
        remap = false
    )
    private long tacRogue$adjustClientBurstShootInterval(GunData gunData, GunDisplayInstance display, IGun iGun,
                                                         ItemStack mainHandItem, GunData originalGunData, long delay,
                                                         float pitch) {
        long interval = gunData.getBurstShootInterval();
        return adjustIntervalMs(interval, mainHandItem);
    }

    @Redirect(
        method = "getCoolDown",
        at = @At(value = "INVOKE", target = "Lcom/tacz/guns/resource/pojo/data/gun/BurstData;getMinInterval()D"),
        remap = false
    )
    private double tacRogue$adjustClientBurstMinInterval(BurstData burstData, IGun iGun, ItemStack mainHandItem, GunData gunData) {
        double minInterval = burstData.getMinInterval();
        if (!canAdjust(mainHandItem)) return minInterval;
        return WeaponRarity.getFireRateAdjustedIntervalSeconds(minInterval, mainHandItem, player);
    }

    private long adjustIntervalMs(long intervalMs, ItemStack stack) {
        if (!canAdjust(stack)) return intervalMs;
        return WeaponRarity.getFireRateAdjustedIntervalMs(intervalMs, stack, player);
    }

    private boolean canAdjust(ItemStack stack) {
        return player != null
            && (player.level().dimension() == CommonEventHandler.ROGUE_DIM
                || player.level().dimension() == CommonEventHandler.LOBBY_DIM)
            && stack != null
            && !stack.isEmpty();
    }
}
