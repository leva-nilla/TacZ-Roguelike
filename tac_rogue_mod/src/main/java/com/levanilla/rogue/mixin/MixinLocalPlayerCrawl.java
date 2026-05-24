package com.levanilla.rogue.mixin;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessagePlayerCrawl;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = com.tacz.guns.client.gameplay.LocalPlayerCrawl.class, remap = false)
public abstract class MixinLocalPlayerCrawl {
    @Shadow @Final private LocalPlayer player;
    @Shadow private boolean isCrawling;
    @Shadow private int crawCooldownTicks;
    @Shadow private void setCrawlPose() {}

    @Inject(method = "crawl", at = @At("HEAD"), cancellable = true)
    private void tacRogue$allowNonGunCrawl(boolean isCrawl, CallbackInfo ci) {
        if (!tacRogue$canUseRogueNonGunCrawl()) return;
        ci.cancel();
        if (crawCooldownTicks > 0) return;
        if (player.isSpectator() || player.isPassenger() || !player.onGround()) return;
        this.isCrawling = isCrawl;
        this.crawCooldownTicks = 10;
        NetworkHandler.CHANNEL.sendToServer(new ClientMessagePlayerCrawl(isCrawl));
    }

    @Inject(method = "tickCrawl", at = @At("HEAD"), cancellable = true)
    private void tacRogue$keepNonGunCrawl(CallbackInfo ci) {
        if (!tacRogue$isTacRogueDimension()) return;
        if (crawCooldownTicks > 0) {
            crawCooldownTicks--;
        }
        if (tacRogue$mainHandIsGun()) return;
        ci.cancel();
        if (player.isSpectator() || player.isPassenger() || player.isSwimming() || !player.onGround()) {
            isCrawling = false;
        }
        setCrawlPose();
    }

    @Unique
    private boolean tacRogue$canUseRogueNonGunCrawl() {
        return tacRogue$isTacRogueDimension() && !tacRogue$mainHandIsGun();
    }

    @Unique
    private boolean tacRogue$mainHandIsGun() {
        ItemStack stack = player.getMainHandItem();
        return stack.getItem() instanceof IGun;
    }

    @Unique
    private boolean tacRogue$isTacRogueDimension() {
        return player != null
            && player.level() != null
            && "tac_rogue".equals(player.level().dimension().location().getNamespace());
    }
}
