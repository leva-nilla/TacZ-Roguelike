package com.levanilla.rogue.client;

import com.levanilla.rogue.client.compat.LeaWindsCompat;
import com.levanilla.rogue.client.compat.JourneyMapRefreshCompat;
import com.levanilla.rogue.client.hud.DamageIndicatorRenderer;
import com.levanilla.rogue.client.hud.NotificationManager;
import com.levanilla.rogue.client.hud.ObjectiveMarkerRenderer;
import com.levanilla.rogue.client.model.TacRogueBossModel;
import com.levanilla.rogue.client.model.TacRogueNpcModel;
import com.levanilla.rogue.client.renderer.TacRogueBossRenderer;
import com.levanilla.rogue.client.renderer.TacRogueNpcRenderer;
import com.levanilla.rogue.core.ModEntities;
import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.WeaponRarity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * クライアントサイド専用のイベントハンドラー (v0.4.0)
 * 描画ロジックは hud/, compat/, keybind/ パッケージに委譲。
 * このクラスはイベント登録とディスパッチのみ行う。
 */
@Mod.EventBusSubscriber(modid = "tac_rogue", value = Dist.CLIENT)
public class ClientEventHandler {
    public static org.joml.Matrix4f lastViewMatrix = new org.joml.Matrix4f();
    public static org.joml.Matrix4f lastProjectionMatrix = new org.joml.Matrix4f();

    @Mod.EventBusSubscriber(modid = "tac_rogue", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void onRegisterReloadListener(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(new ClientReloadListener());
        }

        @SubscribeEvent
        public static void onRegisterRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.TAC_ROGUE_NPC.get(), TacRogueNpcRenderer::new);
            event.registerEntityRenderer(ModEntities.TAC_ROGUE_BOSS.get(), TacRogueBossRenderer::new);
        }

        @SubscribeEvent
        public static void onRegisterLayerDefinitions(net.minecraftforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(TacRogueNpcRenderer.LAYER, TacRogueNpcModel::createBodyLayer);
            event.registerLayerDefinition(TacRogueBossRenderer.LAYER, TacRogueBossModel::createBodyLayer);
        }
    }

    // ===== 後方互換 API (SyncDataMessage → DamageIndicatorRenderer への委譲) =====

    public static void addDamageIndicator(double x, double y, double z, float damage, boolean isCritical, boolean isHeadShot) {
        DamageIndicatorRenderer.addDamageIndicator(x, y, z, damage, isCritical, isHeadShot);
    }

    public static void addShotgunDamageIndicator(double x, double y, double z, float damage, boolean isCritical, boolean isHeadShot) {
        DamageIndicatorRenderer.addShotgunDamageIndicator(x, y, z, damage, isCritical, isHeadShot);
    }

    public static void addDropIndicator(double x, double y, double z, String itemName) {
        DamageIndicatorRenderer.addDropIndicator(x, y, z, itemName);
    }

    public static void addNotification(String text, int color) {
        NotificationManager.add(text, color);
    }

    public static void addPopupNotification(String type, Component title, Component body, int color, int durationTicks) {
        NotificationManager.addPopup(type, title, body, color, durationTicks);
    }

    @SubscribeEvent
    public static void onChatReceived(ClientChatReceivedEvent event) {
        ClientChatEventDelegate.onChatReceived(event);
    }

    @SubscribeEvent
    public static void onItemTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
        net.minecraft.world.item.ItemStack stack = event.getItemStack();
        if (stack.is(net.minecraft.world.item.Items.SNOWBALL)) {
            event.getToolTip().add(Component.translatable("tooltip.tac_rogue.snowball_distract"));
        }
        if (stack.hasTag() && stack.getTag().contains("RogueRarity")) {
            WeaponRarity.Rarity rarity = WeaponRarity.getRarity(stack);
            event.getToolTip().add(Component.translatable(
                "tooltip.tac_rogue.rarity.header",
                rarity.getStarsDisplay(),
                Component.translatable("rarity.tac_rogue." + rarity.name().toLowerCase(java.util.Locale.ROOT))
            ).withStyle(rarity.format));
            event.getToolTip().add(Component.translatable(
                "tooltip.tac_rogue.rarity.damage",
                String.format(java.util.Locale.ROOT, "%.2f", WeaponRarity.getDamageMult(stack))
            ));
            event.getToolTip().add(Component.translatable(
                "tooltip.tac_rogue.rarity.reload",
                String.format(java.util.Locale.ROOT, "%.2f", WeaponRarity.getReloadMult(stack))
            ));
            event.getToolTip().add(Component.translatable(
                "tooltip.tac_rogue.rarity.magazine",
                String.format(java.util.Locale.ROOT, "%.2f", WeaponRarity.getMagSizeMult(stack))
            ));
            boolean meleeStack = stack.getTag() != null && stack.getTag().contains("MeleeWeaponId")
                || com.levanilla.rogue.core.registry.LrTacticalRegistry.isMeleeWeapon(stack);
            float fireRateMult = WeaponRarity.getFireRateMult(stack);
            event.getToolTip().add(Component.translatable(
                meleeStack ? "tooltip.tac_rogue.rarity.melee_speed" : "tooltip.tac_rogue.rarity.fire_rate",
                String.format(java.util.Locale.ROOT, "%.2f", fireRateMult)
            ));
            float effectiveFireRateMult = meleeStack
                ? WeaponRarity.getEffectiveMeleeFireRateMult(stack, event.getEntity())
                : WeaponRarity.getEffectiveFireRateMult(stack, event.getEntity());
            if (effectiveFireRateMult > fireRateMult + 0.005f) {
                event.getToolTip().add(Component.translatable(
                    meleeStack ? "tooltip.tac_rogue.rarity.melee_speed_effective" : "tooltip.tac_rogue.rarity.fire_rate_effective",
                    String.format(java.util.Locale.ROOT, "%.2f", effectiveFireRateMult)
                ));
            }
        }
    }

    @SubscribeEvent
    public static void onComputeFovModifier(ComputeFovModifierEvent event) {
        if (event.getPlayer() == null || !isTacRogueDimension(event.getPlayer().level().dimension().location())) return;
        float walkingSpeed = event.getPlayer().getAbilities().getWalkingSpeed();
        if (walkingSpeed <= 0.0F) return;

        float movementSpeed = (float) event.getPlayer().getAttributeValue(Attributes.MOVEMENT_SPEED);
        float speedFactor = ((movementSpeed / walkingSpeed) + 1.0F) * 0.5F;
        if (!Float.isFinite(speedFactor) || speedFactor <= 0.001F) return;

        event.setNewFovModifier(event.getFovModifier() / speedFactor);
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        ClientScreenEventDelegate.onScreenOpening(event);
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        ClientScreenEventDelegate.onScreenRenderPost(event);
    }

    public static void openInventoryWithTab(RogueInventoryScreen.Tab tab) {
        ClientScreenEventDelegate.openInventoryWithTab(tab);
    }

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Pre event) {
        ClientHudEventDelegate.onRenderGuiOverlay(event);
    }

    @SubscribeEvent
    public static void onRenderLivingPre(net.minecraftforge.client.event.RenderLivingEvent.Pre<?, ?> event) {
        net.minecraft.world.entity.Entity entity = event.getEntity();
        if (!shouldProcessRogueLivingRender(entity)) return;

        long perfStart = ClientPerformanceProfiler.onProfileSectionStart();
        try {
            ClientPerformanceProfiler.onTrackedLivingRenderStart(entity);
            String name = entity.getCustomName() == null ? "" : entity.getCustomName().getString();
            float scale = 1.0F;
            if (entity.getTags().contains("TacRogueObjectiveElite") || name.contains("[PRIME]")) {
                scale = 1.18F;
            } else if (entity.getTags().contains("TacRogueEliteRoom") || name.contains("[ELITE]")) {
                scale = 1.10F;
            }
            if (scale > 1.0F) {
                event.getPoseStack().scale(scale, scale, scale);
            }
        } finally {
            ClientPerformanceProfiler.onProfileSectionEnd(ClientPerformanceProfiler.ProfileSection.LIVING_RENDER_PRE, perfStart);
        }
    }

    @SubscribeEvent
    public static void onRenderLivingPost(net.minecraftforge.client.event.RenderLivingEvent.Post<?, ?> event) {
        net.minecraft.world.entity.Entity entity = event.getEntity();
        if (!shouldProcessRogueLivingRender(entity)) return;
        ClientPerformanceProfiler.onTrackedLivingRenderEnd(entity);
    }

    @SubscribeEvent
    public static void onRenderHotbar(RenderGuiOverlayEvent.Pre event) {
        ClientHudEventDelegate.onRenderHotbar(event);
    }

    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        ClientInputEventDelegate.onClientTick(event);
        JourneyMapRefreshCompat.tick(event);
    }

    public static boolean isRogueSneakToggled() {
        return ClientInputEventDelegate.isRogueSneakToggled();
    }

    public static void openStarterGearScreen() {
        Minecraft.getInstance().setScreen(new StarterGearScreen());
    }

    public static void openFloorSelectionScreen(int maxFloor, long worldSeed) {
        Minecraft.getInstance().setScreen(new FloorSelectionScreen(maxFloor, worldSeed));
    }

    @SubscribeEvent
    public static void onRenderLevelStage(net.minecraftforge.client.event.RenderLevelStageEvent event) {
        boolean afterParticles = event.getStage() == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_PARTICLES;
        if (afterParticles && isRogueDungeonContext()) {
            if (!DamageIndicatorRenderer.isEmpty()) {
                DamageIndicatorRenderer.renderWorld(event);
            }
            if (RunManager.isRunActive() && !RunManager.isFloorCleared()) {
                long objectiveStart = ClientPerformanceProfiler.onHudSectionStart();
                ObjectiveMarkerRenderer.renderWorld(event);
                ClientPerformanceProfiler.onHudSectionEnd(ClientPerformanceProfiler.HudSection.WORLD_OBJECTIVE, objectiveStart);
            }
        }
        if (event.getStage() == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            lastViewMatrix.set(event.getPoseStack().last().pose());
            lastProjectionMatrix.set(event.getProjectionMatrix());
        }
    }

    @SubscribeEvent
    public static void onClientPlayerJoin(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        ClientWelcomeScreenDelegate.onClientPlayerJoin(event);
    }

    @SubscribeEvent
    public static void onMouseScroll(net.minecraftforge.client.event.InputEvent.MouseScrollingEvent event) {
        ClientInputEventDelegate.onMouseScroll(event);
    }

    @SubscribeEvent
    public static void onKey(net.minecraftforge.client.event.InputEvent.Key event) {
        ClientInputEventDelegate.onKey(event);
    }

    @SubscribeEvent
    public static void onMouseButton(net.minecraftforge.client.event.InputEvent.MouseButton.Pre event) {
        ClientInputEventDelegate.onMouseButton(event);
    }

    @SubscribeEvent
    public static void onRenderTick(net.minecraftforge.event.TickEvent.RenderTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.START) {
            LeaWindsCompat.beginRenderFrame();
        }
    }

    private static boolean shouldProcessRogueLivingRender(net.minecraft.world.entity.Entity entity) {
        if (entity == null || !isRogueDungeonContext()) return false;
        java.util.Set<String> tags = entity.getTags();
        if (tags.contains("TacRogueObjectiveElite")
            || tags.contains("TacRogueEliteRoom")
            || tags.contains("rogue:boss")
            || tags.contains("tac_rogue_npc")
            || tags.contains("tac_rogue_support_npc")) {
            return true;
        }
        Component customName = entity.getCustomName();
        if (customName == null) return false;
        String name = customName.getString();
        return name.contains("[PRIME]") || name.contains("[ELITE]");
    }

    private static boolean isRogueDungeonContext() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        net.minecraft.resources.ResourceLocation dimension = mc.level.dimension().location();
        return isTacRogueDimension(dimension) && "rogue_dimension".equals(dimension.getPath());
    }

    private static boolean isTacRogueDimension(net.minecraft.resources.ResourceLocation dimension) {
        if (dimension == null) return false;
        if (!"tac_rogue".equals(dimension.getNamespace())) return false;
        String path = dimension.getPath();
        return "rogue_dimension".equals(path) || "lobby_dimension".equals(path);
    }
}
