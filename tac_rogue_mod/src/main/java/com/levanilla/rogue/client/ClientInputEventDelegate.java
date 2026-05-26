package com.levanilla.rogue.client;

import com.levanilla.rogue.client.compat.LeaWindsCompat;
import com.levanilla.rogue.client.hud.DamageIndicatorRenderer;
import com.levanilla.rogue.client.hud.HudRenderer;
import com.levanilla.rogue.client.hud.NotificationManager;
import com.levanilla.rogue.client.hud.TutorialGuideManager;
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
    private static boolean guiScaleCapped = false;

    private ClientInputEventDelegate() {}

    static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
            Minecraft mc = Minecraft.getInstance();
            boolean rogueContext = isRogueContext(mc);
            boolean rogueDungeon = isRogueDungeonContext(mc);

            HudRenderer.tickVictory();
            if (!DamageIndicatorRenderer.isEmpty()) {
                DamageIndicatorRenderer.tick();
            }
            NotificationManager.tick();
            long perfStart;
            if (rogueDungeon || RunManager.isRunActive()) {
                perfStart = ClientPerformanceProfiler.onProfileSectionStart();
                DynamicLightManager.tick();
                ClientPerformanceProfiler.onProfileSectionEnd(ClientPerformanceProfiler.ProfileSection.DYNAMIC_LIGHT_TICK, perfStart);
            }

            if (rogueContext) {
                DebugAiOverlayManager.tick(mc);
            }
            ClientWelcomeScreenDelegate.handlePendingWelcomeScreen(mc);
            ensureTacRogueGuiScale(mc);
            if (rogueContext) {
                TutorialGuideManager.tick(mc);
            }
            syncAdsInput(mc);
            handleRogueSneakToggle(mc);
            KeyComboManager.tick(mc);
            SmokeClientAutomation.tick(mc);
            ClientBenchmarkAutomation.tick(mc);
            ClientPerformanceProfiler.onClientTick(mc);
            if (rogueContext && LeaWindsCompat.isLeawindAvailable()) {
                perfStart = ClientPerformanceProfiler.onProfileSectionStart();
                LeaWindsCompat.syncThirdPersonGunAim();
                ClientPerformanceProfiler.onProfileSectionEnd(ClientPerformanceProfiler.ProfileSection.LEAWINDS_AIM_SYNC, perfStart);
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

            while (ClientKeyBinds.DEBUG_MENU.consumeClick()) {
                DebugMenuScreen.open();
            }

            while (ClientKeyBinds.DEBUG_AI_OVERLAY.consumeClick()) {
                DebugAiOverlayManager.toggle();
            }

            while (ClientKeyBinds.TUTORIAL_NEXT.consumeClick()) {
                TutorialGuideManager.advanceManually(mc);
            }
        }
    }

    private static boolean isRogueDungeonContext(Minecraft mc) {
        if (mc == null || mc.level == null) return false;
        net.minecraft.resources.ResourceLocation dimension = mc.level.dimension().location();
        return "tac_rogue".equals(dimension.getNamespace()) && "rogue_dimension".equals(dimension.getPath());
    }

    private static void ensureTacRogueGuiScale(Minecraft mc) {
        if (mc == null || mc.options == null) return;
        boolean inTacRogueDimension = mc.level != null
            && "tac_rogue".equals(mc.level.dimension().location().getNamespace());
        boolean inTacRogueScreen = mc.screen != null
            && mc.screen.getClass().getName().startsWith("com.levanilla.rogue.client");
        if (!inTacRogueDimension && !inTacRogueScreen) {
            guiScaleCapped = false;
            return;
        }
        try {
            int scale = mc.options.guiScale().get();
            if (scale == 0 || scale > 2) {
                mc.options.guiScale().set(2);
                mc.options.save();
                mc.resizeDisplay();
                if (mc.screen != null) {
                    mc.screen.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
                }
                if (!guiScaleCapped) {
                    NotificationManager.add("\u00A7bGUI Scale set to 2 for TacZ: Rogue Protocol", 0xFF66E8FF);
                }
                guiScaleCapped = true;
            }
        } catch (Exception ignored) {
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

        handleNonGunCrawlKey(mc, event);

        if (event.getAction() == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            net.minecraft.client.KeyMapping taczInteract = findKeyMapping(mc, "key.tacz.interact.desc", "key.tacz.interact");
            if (taczInteract != null && taczInteract.matches(event.getKey(), event.getScanCode())) {
                tryCustomInteractFromClient(mc);
            }
        }
    }

    private static void handleNonGunCrawlKey(Minecraft mc, net.minecraftforge.client.event.InputEvent.Key event) {
        if (!isRogueContext(mc) || mc.level == null || mc.player == null) return;
        if (isHoldingTacZGun(mc)) return;
        net.minecraft.client.KeyMapping crawl = findKeyMapping(mc, "key.tacz.crawl.desc", "key.tacz.crawl");
        if (crawl == null || !crawl.matches(event.getKey(), event.getScanCode())) return;
        try {
            if (!com.tacz.guns.config.sync.SyncConfig.ENABLE_CRAWL.get()) return;
            var operator = com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player);
            boolean hold = com.tacz.guns.config.client.KeyConfig.HOLD_TO_CRAWL.get();
            if (event.getAction() == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
                operator.crawl(hold || !operator.isCrawl());
            } else if (hold && event.getAction() == org.lwjgl.glfw.GLFW.GLFW_RELEASE) {
                operator.crawl(false);
            }
        } catch (Throwable ignored) {
        }
    }

    static void onMouseButton(net.minecraftforge.client.event.InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;
        if (event.getAction() != org.lwjgl.glfw.GLFW.GLFW_PRESS) return;
        net.minecraft.client.KeyMapping taczInteract = findKeyMapping(mc, "key.tacz.interact.desc", "key.tacz.interact");
        if (taczInteract != null && taczInteract.matchesMouse(event.getButton()) && tryCustomInteractFromClient(mc)) {
            event.setCanceled(true);
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

    static boolean hasObjectiveInteractTarget(Minecraft mc) {
        return findLookedAtObjectiveBlock(mc) != null;
    }

    static String getTacZInteractKeyName(Minecraft mc) {
        net.minecraft.client.KeyMapping mapping = findKeyMapping(mc, "key.tacz.interact.desc", "key.tacz.interact");
        if (mapping == null) return "O";
        String translated = mapping.getTranslatedKeyMessage().getString();
        return translated == null || translated.isBlank() ? "O" : translated;
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

        net.minecraft.core.BlockPos objectivePos = findLookedAtObjectiveBlock(mc);
        if (objectivePos != null) {
            com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.sendToServer(
                new com.levanilla.rogue.networking.RogueActionMessage(
                    com.levanilla.rogue.networking.RogueActionMessage.ActionType.INTERACT_OBJECTIVE,
                    objectivePos.getX() + ":" + objectivePos.getY() + ":" + objectivePos.getZ()));
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

    private static net.minecraft.core.BlockPos findLookedAtObjectiveBlock(Minecraft mc) {
        if (!(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit)) return null;
        net.minecraft.resources.ResourceLocation dimension = mc.level.dimension().location();
        if (!dimension.getNamespace().equals("tac_rogue") || !dimension.getPath().equals("rogue_dimension")) return null;
        net.minecraft.core.BlockPos pos = blockHit.getBlockPos();
        if (mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 42.25D) return null;
        net.minecraft.world.level.block.state.BlockState state = mc.level.getBlockState(pos);
        if (state.is(net.minecraft.world.level.block.Blocks.LECTERN)
            || state.is(net.minecraft.world.level.block.Blocks.BARREL)
            || state.is(net.minecraft.world.level.block.Blocks.LODESTONE)) {
            com.levanilla.rogue.core.ClientRunState.ObjectiveState objective =
                com.levanilla.rogue.core.ClientRunState.getObjectiveState();
            if (objective != null && objective.hasTarget()) {
                net.minecraft.core.BlockPos target =
                    new net.minecraft.core.BlockPos(objective.targetX(), objective.targetY(), objective.targetZ());
                return target.distSqr(pos) <= 2.25D ? pos : null;
            }
            return pos;
        }
        return null;
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
