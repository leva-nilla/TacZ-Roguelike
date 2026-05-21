package com.levanilla.rogue.client;

import com.levanilla.rogue.client.compat.LeaWindsCompat;
import com.levanilla.rogue.client.hud.DamageIndicatorRenderer;
import com.levanilla.rogue.client.hud.HudRenderer;
import com.levanilla.rogue.client.hud.NotificationManager;
import com.levanilla.rogue.core.RunManager;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;

final class ClientInputEventDelegate {
    private static boolean rogueSneakToggleSaved = false;
    private static boolean lastSneakDown = false;
    private static boolean lastSneakKeyDown = false;
    private static boolean lastCrawlDown = false;
    private static boolean lastAdsInputSent = false;
    private static int lastAdsInputPacketTick = -20;

    private ClientInputEventDelegate() {}

    static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
            HudRenderer.tickVictory();
            DamageIndicatorRenderer.tick();
            NotificationManager.tick();
            DynamicLightManager.tick();

            Minecraft mc = Minecraft.getInstance();
            ClientWelcomeScreenDelegate.handlePendingWelcomeScreen(mc);
            syncAdsInput(mc);
            handleRogueSneakToggle(mc);
            KeyComboManager.tick(mc);
            if (LeaWindsCompat.isLeawindAvailable()) {
                LeaWindsCompat.syncThirdPersonGunAim();
            }

            while (ClientKeyBinds.FLASHLIGHT.consumeClick()) {
                com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.sendToServer(
                    new com.levanilla.rogue.networking.RogueActionMessage(
                        com.levanilla.rogue.networking.RogueActionMessage.ActionType.FLASHLIGHT_TOGGLE, ""));
                boolean enabled = DynamicLightManager.toggle();
                NotificationManager.add(enabled ? "\u00A7eFlashlight ON" : "\u00A77Flashlight OFF",
                    enabled ? 0xFFFFD700 : 0xFF888888);
            }

            while (ClientKeyBinds.CAMERA_TOGGLE.consumeClick()) {
                LeaWindsCompat.toggleAdsForceFirstPerson();
            }
        }
    }

    private static void syncAdsInput(Minecraft mc) {
        boolean aiming = false;
        if (mc.player != null && mc.level != null && mc.screen == null) {
            try {
                aiming = mc.options.keyUse.isDown() && IGun.mainHandHoldGun(mc.player);
            } catch (Throwable ignored) {
                aiming = false;
            }
        }

        boolean changed = aiming != lastAdsInputSent;
        boolean keepAlive = aiming && mc.player != null && mc.player.tickCount - lastAdsInputPacketTick >= 4;
        if (!changed && !keepAlive) return;

        lastAdsInputSent = aiming;
        lastAdsInputPacketTick = mc.player != null ? mc.player.tickCount : lastAdsInputPacketTick;
        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.sendToServer(
            new com.levanilla.rogue.networking.AdsInputMessage(aiming));
    }

    static boolean isRogueSneakToggled() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && (mc.player.isShiftKeyDown() || mc.player.hasPose(net.minecraft.world.entity.Pose.CROUCHING));
    }

    private static void handleRogueSneakToggle(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            lastSneakDown = false;
            lastSneakKeyDown = false;
            lastCrawlDown = false;
            return;
        }

        boolean inRogue = mc.level.dimension().location().getNamespace().equals("tac_rogue") || RunManager.isRunActive();
        if (!inRogue) {
            lastSneakDown = false;
            lastSneakKeyDown = false;
            lastCrawlDown = false;
            return;
        }

        ensureVanillaToggleSneak(mc);

        boolean crawlDown = isTacZCrawling(mc);
        boolean sneakKeyDown = mc.options.keyShift.isDown();
        boolean sneakDown = sneakKeyDown || mc.player.isShiftKeyDown()
            || mc.player.hasPose(net.minecraft.world.entity.Pose.CROUCHING);
        boolean crawlStarted = crawlDown && !lastCrawlDown;
        boolean sneakStarted = sneakKeyDown && !lastSneakKeyDown;

        if (crawlStarted) {
            clearVanillaSneak(mc);
            sneakDown = false;
            sneakKeyDown = false;
        } else if (crawlDown && sneakStarted) {
            setTacZCrawling(mc, false);
            crawlDown = false;
        } else if (crawlDown && sneakDown) {
            clearVanillaSneak(mc);
            sneakDown = false;
            sneakKeyDown = false;
        }

        lastSneakDown = sneakDown;
        lastSneakKeyDown = sneakKeyDown;
        lastCrawlDown = crawlDown;
    }

    private static void clearVanillaSneak(Minecraft mc) {
        boolean toggleCrouch = false;
        try {
            toggleCrouch = mc.options.toggleCrouch().get();
            if (toggleCrouch) {
                mc.options.toggleCrouch().set(false);
            }
            mc.options.keyShift.setDown(false);
            mc.player.setShiftKeyDown(false);
        } catch (Exception ignored) {
            mc.options.keyShift.setDown(false);
            mc.player.setShiftKeyDown(false);
        } finally {
            try {
                if (toggleCrouch) {
                    mc.options.toggleCrouch().set(true);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void ensureVanillaToggleSneak(Minecraft mc) {
        if (rogueSneakToggleSaved) return;
        try {
            if (!mc.options.toggleCrouch().get()) {
                mc.options.toggleCrouch().set(true);
                mc.options.save();
            }
        } catch (Exception ignored) {
        }
        rogueSneakToggleSaved = true;
    }

    private static boolean isTacZCrawling(Minecraft mc) {
        try {
            return com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player).isCrawl()
                || mc.player.hasPose(net.minecraft.world.entity.Pose.SWIMMING);
        } catch (Throwable ignored) {
            return mc.player.hasPose(net.minecraft.world.entity.Pose.SWIMMING);
        }
    }

    private static void setTacZCrawling(Minecraft mc, boolean value) {
        try {
            com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player).crawl(value);
        } catch (Throwable ignored) {
        }
    }

    static void onMouseScroll(net.minecraftforge.client.event.InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || mc.player.isSpectator()) return;
        if (!isRogueContext(mc)) return;

        int current = mc.player.getInventory().selected;
        if (current < 0 || current > com.levanilla.rogue.core.GameConstants.SLOT_ITEM_END) {
            current = 0;
        }

        int direction = event.getScrollDelta() > 0 ? -1 : 1;
        int size = com.levanilla.rogue.core.GameConstants.SLOT_ITEM_END + 1;
        int next = (current + direction + size) % size;
        mc.player.getInventory().selected = next;
        if (mc.getConnection() != null) {
            mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(next));
        }
        event.setCanceled(true);
    }

    static void onKey(net.minecraftforge.client.event.InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;

        if (event.getAction() == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            net.minecraft.client.KeyMapping taczInteract = findKeyMapping(mc, "key.tacz.interact.desc", "key.tacz.interact");
            if (taczInteract != null && taczInteract.matches(event.getKey(), event.getScanCode())) {
                if (!isHoldingTacZGun(mc)) {
                    tryCustomInteractFromClient(mc);
                }
            }
        }
    }

    static void onMouseButton(net.minecraftforge.client.event.InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;
        if (event.getAction() != org.lwjgl.glfw.GLFW.GLFW_PRESS) return;
        net.minecraft.client.KeyMapping taczInteract = findKeyMapping(mc, "key.tacz.interact.desc", "key.tacz.interact");
        if (taczInteract != null && taczInteract.matchesMouse(event.getButton()) && !isHoldingTacZGun(mc)) {
            tryCustomInteractFromClient(mc);
        }
    }

    static boolean isRogueContext(Minecraft mc) {
        if (mc.level == null) return RunManager.isRunActive();
        return mc.level.dimension().location().getNamespace().equals("tac_rogue") || RunManager.isRunActive();
    }

    private static net.minecraft.client.KeyMapping findKeyMapping(Minecraft mc, String... names) {
        if (mc.options == null) return null;
        for (String name : names) {
            for (net.minecraft.client.KeyMapping mapping : mc.options.keyMappings) {
                if (mapping.getName().equals(name)) return mapping;
            }
        }
        return null;
    }

    private static boolean isHoldingTacZGun(Minecraft mc) {
        try {
            return mc.player != null && com.tacz.guns.api.item.IGun.mainHandHoldGun(mc.player);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean tryCustomInteractFromClient(Minecraft mc) {
        if (!isRogueContext(mc) || mc.level == null || mc.player == null) return false;
        com.levanilla.rogue.world.TacRogueNpcEntity npc = findLookedAtNpc(mc);
        if (npc != null) {
            com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.sendToServer(
                new com.levanilla.rogue.networking.NpcInteractMessage(npc.getId()));
            return true;
        }

        net.minecraft.core.BlockPos stashPos = findLookedAtStash(mc);
        if (stashPos != null) {
            com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.sendToServer(
                new com.levanilla.rogue.networking.RogueActionMessage(
                    com.levanilla.rogue.networking.RogueActionMessage.ActionType.INTERACT_STASH,
                    stashPos.getX() + ":" + stashPos.getY() + ":" + stashPos.getZ()));
            return true;
        }
        return false;
    }

    private static com.levanilla.rogue.world.TacRogueNpcEntity findLookedAtNpc(Minecraft mc) {
        if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult entityHit
            && entityHit.getEntity() instanceof com.levanilla.rogue.world.TacRogueNpcEntity npc
            && npc.distanceToSqr(mc.player) <= 25.0) {
            return npc;
        }

        net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition(1.0F);
        net.minecraft.world.phys.Vec3 look = mc.player.getViewVector(1.0F);
        net.minecraft.world.phys.Vec3 end = eye.add(look.scale(5.0D));
        net.minecraft.world.phys.AABB searchBox = mc.player.getBoundingBox().expandTowards(look.scale(5.0D)).inflate(1.0D);
        java.util.List<com.levanilla.rogue.world.TacRogueNpcEntity> npcs =
            mc.level.getEntitiesOfClass(com.levanilla.rogue.world.TacRogueNpcEntity.class, searchBox, e -> e.isAlive());

        com.levanilla.rogue.world.TacRogueNpcEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (com.levanilla.rogue.world.TacRogueNpcEntity npc : npcs) {
            java.util.Optional<net.minecraft.world.phys.Vec3> hit = npc.getBoundingBox().inflate(0.6D).clip(eye, end);
            if (hit.isEmpty()) continue;
            double dist = eye.distanceToSqr(hit.get());
            if (dist < bestDist) {
                bestDist = dist;
                best = npc;
            }
        }
        return best;
    }

    private static net.minecraft.core.BlockPos findLookedAtStash(Minecraft mc) {
        if (!(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit)) return null;
        net.minecraft.core.BlockPos pos = blockHit.getBlockPos();
        if (mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 36.0) return null;
        return mc.level.getBlockState(pos).is(com.levanilla.rogue.core.ModBlocks.STASH_TERMINAL.get()) ? pos : null;
    }
}
