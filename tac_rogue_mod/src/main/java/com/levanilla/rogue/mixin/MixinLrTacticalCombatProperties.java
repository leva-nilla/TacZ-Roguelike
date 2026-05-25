package com.levanilla.rogue.mixin;

import com.levanilla.rogue.core.CommonEventHandler;
import com.levanilla.rogue.core.WeaponRarity;
import me.xjqsh.lrtactical.api.melee.MeleeAction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = me.xjqsh.lrtactical.capability.CombatProperties.class, remap = false)
public abstract class MixinLrTacticalCombatProperties {
    @Shadow @Final private Player entity;
    @Shadow private int coolDownTick;
    @Shadow private int lastMaxTick;

    @Inject(
        method = "preAttack",
        at = @At(
            value = "FIELD",
            target = "Lme/xjqsh/lrtactical/capability/CombatProperties;lastMaxTick:I",
            opcode = Opcodes.PUTFIELD,
            shift = At.Shift.AFTER
        ),
        remap = false
    )
    private void tacRogue$scaleMeleeCooldownForPerk(MeleeAction action, Vec3 pos, Vec3 look,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (entity == null || entity.level() == null) return;
        if (entity.level().dimension() != CommonEventHandler.ROGUE_DIM
                && entity.level().dimension() != CommonEventHandler.LOBBY_DIM) {
            return;
        }
        int adjusted = WeaponRarity.getMeleeSpeedPerkAdjustedTicks(this.coolDownTick, entity);
        if (adjusted < this.coolDownTick) {
            this.coolDownTick = adjusted;
            this.lastMaxTick = adjusted;
        }
    }
}
