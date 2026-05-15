package com.levanilla.rogue.client;

import com.levanilla.rogue.client.compat.LeaWindsCompat;
import com.levanilla.rogue.client.hud.*;
import com.levanilla.rogue.mixin.MixinScreenAccessor;
import com.levanilla.rogue.client.model.TacRogueBossModel;
import com.levanilla.rogue.client.model.TacRogueNpcModel;
import com.levanilla.rogue.client.renderer.TacRogueBossRenderer;
import com.levanilla.rogue.client.renderer.TacRogueNpcRenderer;
import com.levanilla.rogue.core.ModEntities;
import com.levanilla.rogue.core.WeaponRarity;
import com.levanilla.rogue.core.RunManager;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * クライアントサイド専用のイベントハンドラー (v0.4.0)
 * 描画ロジックは hud/, compat/, keybind/ パッケージに委譲。
 * このクラスはイベント登録とディスパッチのみ行う。
 */
@Mod.EventBusSubscriber(modid = "tac_rogue", value = Dist.CLIENT)
public class ClientEventHandler {
    private static boolean rogueSneakToggleSaved = false;
    private static boolean lastSneakDown = false;
    private static boolean lastSneakKeyDown = false;
    private static boolean lastCrawlDown = false;
    private static boolean lastAdsInputSent = false;
    private static int lastAdsInputPacketTick = -20;
    private static boolean pendingWelcomeScreen = false;
    private static int pendingWelcomeTicks = 0;

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

    // ===== チャットメッセージ → HUD リダイレクト =====

    @SubscribeEvent
    public static void onChatReceived(ClientChatReceivedEvent event) {
        try {
            String msg = event.getMessage().getString();
            
            // BiggerStacksのチュートリアル・セットアップメッセージ等を強制非表示（全ディメンション）
            if (msg.contains("Bigger Stacks") || msg.contains("BiggerStacks") || msg.toLowerCase().contains("/biggerstacks")) {
                event.setCanceled(true);
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            boolean inRogue = mc.level.dimension().location().getNamespace().equals("tac_rogue") || RunManager.isRunActive();
            if (!inRogue) return;

            if (msg.contains("[SHOP]") || msg.contains("[ERROR]") || msg.contains("[MEDKIT]") ||
                msg.contains("LOADOUT") || msg.contains("ゴールド") || msg.contains("Gold") ||
                msg.contains("スタッシュ") || msg.contains("Stash") || msg.contains("insufficient") ||
                msg.contains("purchased") || msg.contains("upgrade") || msg.contains("SOLD") ||
                msg.contains("sold") || msg.contains("Floor") || msg.contains("FLOOR")) {
                event.setCanceled(true);
                int color = msg.contains("ERROR") ? 0xFFFF4444 :
                            msg.contains("SHOP") ? 0xFFFFD700 :
                            msg.contains("insufficient") ? 0xFFFF4444 : 0xFFFFFFFF;
                NotificationManager.add(msg, color);
            }
        } catch (Exception e) {
            // 例外時はチャットをそのまま表示させる
        }
    }

    // ===== インベントリ画面のフック =====

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
            float fireRateMult = WeaponRarity.getFireRateMult(stack);
            event.getToolTip().add(Component.translatable(
                "tooltip.tac_rogue.rarity.fire_rate",
                String.format(java.util.Locale.ROOT, "%.2f", fireRateMult)
            ));
            float effectiveFireRateMult = WeaponRarity.getEffectiveFireRateMult(stack, event.getEntity());
            if (effectiveFireRateMult > fireRateMult + 0.005f) {
                event.getToolTip().add(Component.translatable(
                    "tooltip.tac_rogue.rarity.fire_rate_effective",
                    String.format(java.util.Locale.ROOT, "%.2f", effectiveFireRateMult)
                ));
            }
        }
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();

        if (event.getScreen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
            event.setNewScreen(new TacRogueTitleScreen());
            return;
        }
        if (event.getScreen() instanceof net.minecraft.client.gui.screens.worldselection.SelectWorldScreen) {
            event.setCanceled(true);
            mc.tell(() -> RogueWorldSelectScreen.openFromVanilla(new TacRogueTitleScreen()));
            return;
        }

        // 画面フックの無限ループを防止（すでにカスタム画面ならスキップ）
        if (event.getScreen() instanceof StashScreen
            || event.getScreen() instanceof RogueSupplyChestScreen
            || event.getScreen() instanceof RogueInventoryScreen
            || event.getScreen() instanceof RogueWorldSelectScreen) {
            return;
        }

        // ChestMenu系コンテナをRogue用画面へ差し替え
        if (event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen containerScreen) {
            net.minecraft.world.inventory.ChestMenu chestMenu = containerScreen.getMenu();
            String title = containerScreen.getTitle().getString();
            if (title.contains("STASH TERMINAL")) {
                event.setCanceled(true);
                mc.tell(() -> {
                    StashScreen stashScreen = new StashScreen(chestMenu, mc.player.getInventory(), containerScreen.getTitle());
                    mc.setScreen(stashScreen);
                });
                return;
            }
            event.setCanceled(true);
            mc.tell(() -> {
                if (mc.player != null) {
                    RogueSupplyChestScreen supplyScreen =
                        new RogueSupplyChestScreen(chestMenu, mc.player.getInventory(), containerScreen.getTitle());
                    mc.setScreen(supplyScreen);
                }
            });
            return;
        }

        // インベントリ → ローグライクカスタム UI に差し替え
        if (mc.level != null && event.getScreen() instanceof InventoryScreen) {
            boolean inRogue = mc.level.dimension().location().getNamespace().equals("tac_rogue");
            if (inRogue || RunManager.isRunActive()) {
                event.setCanceled(true);
                openInventoryWithTab(RogueInventoryScreen.Tab.INVENTORY);
            }
        }
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!TacticalScreenStyle.isWorldFlowScreen(event.getScreen())) return;
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        if (event.getScreen() instanceof net.minecraft.client.gui.screens.worldselection.SelectWorldScreen) {
            TacticalScreenStyle.drawWorldSelectOverlay(graphics, mc.font, event.getScreen().width, event.getScreen().height);
        }

        for (Renderable renderable : ((MixinScreenAccessor) event.getScreen()).tacRogue$getRenderables()) {
            if (renderable instanceof Button button && button.visible) {
                TacticalScreenStyle.drawButton(graphics, mc.font, button);
            }
        }
    }

    public static void openInventoryWithTab(RogueInventoryScreen.Tab tab) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.player.LocalPlayer player = mc.player;
        if (player == null) return;
        mc.tell(() -> {
            net.minecraft.client.player.LocalPlayer p = mc.player;
            if (p != null && p.inventoryMenu != null) {
                RogueInventoryScreen screen = new RogueInventoryScreen(p.inventoryMenu, p.getInventory(), Component.literal("ROGUE INVENTORY"));
                screen.setActiveTab(tab);
                mc.setScreen(screen);
            }
        });
    }

    // ===== バニラ HUD の非表示 =====

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!isRogueContext(mc)) return;
        if (event.getOverlay().id().equals(VanillaGuiOverlay.PLAYER_HEALTH.id()) ||
            event.getOverlay().id().equals(VanillaGuiOverlay.FOOD_LEVEL.id()) ||
            event.getOverlay().id().equals(VanillaGuiOverlay.ARMOR_LEVEL.id()) ||
            event.getOverlay().id().equals(VanillaGuiOverlay.EXPERIENCE_BAR.id()) ||
            event.getOverlay().id().equals(VanillaGuiOverlay.AIR_LEVEL.id())) {
            event.setCanceled(true);
        }
    }

    // ===== カスタムホットバーと HUD — 各レンダラーに委譲 =====

    @SubscribeEvent
    public static void onRenderHotbar(RenderGuiOverlayEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        boolean isRogueDim = mc.level.dimension().location().getNamespace().equals("tac_rogue");
        if (!isRogueDim && !RunManager.isRunActive()) return;

        if (event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) {
            event.setCanceled(true);

            Player player = mc.player;
            if (player == null || player.isSpectator()) return;

            GuiGraphics graphics = event.getGuiGraphics();
            int width = event.getWindow().getGuiScaledWidth();
            int height = event.getWindow().getGuiScaledHeight();

            HotbarRenderer.render(graphics, mc, player, width, height);
            HudRenderer.render(graphics, mc, player, width, height);
            PublicCoopWaitState.render(graphics, mc, width, height);
            DamageIndicatorRenderer.renderGui(graphics, mc, width, height);
            NotificationManager.render(graphics, mc, width, height);
            NotificationManager.renderPopups(graphics, mc, width, height);
            renderBackgroundLoadIndicator(graphics, mc, width);

            // 三人称ADS時のフォールバッククロスヘア描画
            if (!mc.options.getCameraType().isFirstPerson()) {
                net.minecraft.world.item.ItemStack mainHand = player.getMainHandItem();
                if (!mainHand.isEmpty() && mainHand.hasTag()) {
                    net.minecraft.nbt.CompoundTag tag = mainHand.getTag();
                    if (tag != null && tag.contains("GunId")) {
                        try {
                            net.minecraft.resources.ResourceLocation crosshairLoc = 
                                com.tacz.guns.client.renderer.crosshair.CrosshairType.getTextureLocation(
                                    com.tacz.guns.config.client.RenderConfig.CROSSHAIR_TYPE.get()
                                );
                            
                            int texSize = 16;
                            int cx = width / 2;
                            int cy = height / 2;

                            net.minecraft.world.phys.Vec3 targetPos =
                                LeaWindsCompat.resolveThirdPersonAimTargetForRender(mc, 96.0D);
                            if (targetPos == null) return;
                            
                            net.minecraft.client.Camera camera = mc.gameRenderer.getMainCamera();
                            net.minecraft.world.phys.Vec3 camPos = camera.getPosition();
                            
                            org.joml.Vector4f pos = new org.joml.Vector4f(
                                (float)(targetPos.x - camPos.x),
                                (float)(targetPos.y - camPos.y),
                                (float)(targetPos.z - camPos.z),
                                1.0f
                            );
                            
                            lastViewMatrix.transform(pos);
                            lastProjectionMatrix.transform(pos);
                            
                            if (pos.w() > 0.0f) {
                                pos.div(pos.w());
                                if (pos.z() > -1.0f && pos.z() < 1.0f) {
                                    cx = (int) ((pos.x() + 1.0f) / 2.0f * width);
                                    cy = (int) ((1.0f - pos.y()) / 2.0f * height);
                                }
                            }
                            
                            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                            com.mojang.blaze3d.systems.RenderSystem.blendFunc(
                                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, 
                                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
                            );
                            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.9f);
                            
                            graphics.blit(crosshairLoc, cx - texSize / 2, cy - texSize / 2, 0, 0, texSize, texSize, texSize, texSize);
                            
                            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                        } catch (Exception e) {
                            // フォールバック
                        }
                    }
                }
            }
        }
    }

    private static void renderBackgroundLoadIndicator(GuiGraphics graphics, Minecraft mc, int screenWidth) {
        if (mc.level == null || !"tac_rogue:lobby_dimension".equals(mc.level.dimension().location().toString())) return;
        PrewarmStatus status = readPrewarmStatus();
        if (status == null || !status.pending || status.total <= 0) return;

        int w = 142;
        int h = 29;
        int x = Math.max(8, screenWidth - w - 10);
        int y = 10;
        int loaded = Math.max(0, Math.min(status.loaded, status.total));
        float progress = Math.max(0.0F, Math.min(1.0F, status.progress));
        int fillW = Math.round((w - 14) * progress);

        graphics.fill(x, y, x + w, y + h, 0xD8041019);
        graphics.fill(x, y, x + 2, y + h, 0xEE55DDAA);
        graphics.renderOutline(x, y, w, h, 0xAA55DDAA);
        graphics.drawString(mc.font, Component.translatable("hud.tac_rogue.loading.title"), x + 7, y + 4, 0xFFBFFFF3, false);
        graphics.drawString(mc.font,
            Component.translatable(status.started ? "hud.tac_rogue.loading.sound_cache" : "hud.tac_rogue.loading.waiting"),
            x + 7, y + 14, 0xFF8C99A6, false);
        graphics.fill(x + 7, y + h - 6, x + w - 7, y + h - 3, 0xFF17242C);
        graphics.fill(x + 7, y + h - 6, x + 7 + fillW, y + h - 3, 0xFF55DDAA);
        String count = loaded + "/" + status.total;
        graphics.drawString(mc.font, count, x + w - 7 - mc.font.width(count), y + 4, 0xFFD7E8EA, false);
    }

    private static PrewarmStatus readPrewarmStatus() {
        try {
            Class<?> manager = Class.forName("com.levanilla.taczstartuphelper.ClientPrewarmManager");
            boolean pending = (boolean) manager.getMethod("hasPendingPrewarm").invoke(null);
            if (!pending) return null;
            int total = (int) manager.getMethod("getTotalSoundCount").invoke(null);
            int loaded = (int) manager.getMethod("getLoadedSoundCount").invoke(null);
            boolean started = (boolean) manager.getMethod("hasStartedPrewarm").invoke(null);
            float progress = (float) manager.getMethod("getProgress").invoke(null);
            return new PrewarmStatus(pending, started, total, loaded, progress);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private record PrewarmStatus(boolean pending, boolean started, int total, int loaded, float progress) {}

    // ===== Client Tick — 各サブシステムに委譲 =====

    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
            HudRenderer.tickVictory();
            DamageIndicatorRenderer.tick();
            NotificationManager.tick();
            DynamicLightManager.tick();

            Minecraft mc = Minecraft.getInstance();
            handlePendingWelcomeScreen(mc);
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

    public static boolean isRogueSneakToggled() {
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

    public static org.joml.Matrix4f lastViewMatrix = new org.joml.Matrix4f();
    public static org.joml.Matrix4f lastProjectionMatrix = new org.joml.Matrix4f();

    public static void openStarterGearScreen() {
        Minecraft.getInstance().setScreen(new StarterGearScreen());
    }

    public static void openFloorSelectionScreen(int maxFloor, long worldSeed) {
        Minecraft.getInstance().setScreen(new FloorSelectionScreen(maxFloor, worldSeed));
    }

    @SubscribeEvent
    public static void onRenderLevelStage(net.minecraftforge.client.event.RenderLevelStageEvent event) {
        DamageIndicatorRenderer.renderWorld(event);
        if (event.getStage() == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            lastViewMatrix.set(event.getPoseStack().last().pose());
            lastProjectionMatrix.set(event.getProjectionMatrix());
        }
    }

    // ===== ログイン時 / ワールド入室時のチュートリアル表示 =====

    @SubscribeEvent
    public static void onClientPlayerJoin(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft mc = Minecraft.getInstance();
        pendingWelcomeScreen = true;
        pendingWelcomeTicks = 0;
        mc.tell(() -> {
            pendingWelcomeScreen = mc.player != null && !ClientPreferenceManager.hasSeenWelcomeForCurrentWorld();
        });
    }

    private static void handlePendingWelcomeScreen(Minecraft mc) {
        if (!pendingWelcomeScreen) return;
        if (mc.player == null || mc.level == null) {
            pendingWelcomeTicks = 0;
            return;
        }
        if (ClientPreferenceManager.hasSeenWelcomeForCurrentWorld()) {
            pendingWelcomeScreen = false;
            pendingWelcomeTicks = 0;
            return;
        }
        pendingWelcomeTicks++;
        if (pendingWelcomeTicks < 10) return;
        if (mc.screen == null || mc.screen instanceof com.levanilla.rogue.client.StarterGearScreen) {
            mc.setScreen(new com.levanilla.rogue.client.hud.WelcomeScreen());
            pendingWelcomeScreen = false;
            pendingWelcomeTicks = 0;
        }
    }

    // ===== カスタムキーバインド =====

    @SubscribeEvent
    public static void onMouseScroll(net.minecraftforge.client.event.InputEvent.MouseScrollingEvent event) {
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

    @SubscribeEvent
    public static void onKey(net.minecraftforge.client.event.InputEvent.Key event) {
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

    @SubscribeEvent
    public static void onMouseButton(net.minecraftforge.client.event.InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;
        if (event.getAction() != org.lwjgl.glfw.GLFW.GLFW_PRESS) return;
        net.minecraft.client.KeyMapping taczInteract = findKeyMapping(mc, "key.tacz.interact.desc", "key.tacz.interact");
        if (taczInteract != null && taczInteract.matchesMouse(event.getButton()) && !isHoldingTacZGun(mc)) {
            tryCustomInteractFromClient(mc);
        }
    }

    private static boolean isRogueContext(Minecraft mc) {
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

    // ===== LeaWinds 三人称互換 =====

    @SubscribeEvent
    public static void onRenderTick(net.minecraftforge.event.TickEvent.RenderTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.START) {
            LeaWindsCompat.beginRenderFrame();
        }
    }
}
