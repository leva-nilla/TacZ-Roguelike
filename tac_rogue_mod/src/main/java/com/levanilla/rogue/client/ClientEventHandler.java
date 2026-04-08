package com.levanilla.rogue.client;

import com.levanilla.rogue.client.compat.LeaWindsCompat;
import com.levanilla.rogue.client.hud.*;
import com.levanilla.rogue.core.RunManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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

    @Mod.EventBusSubscriber(modid = "tac_rogue", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void onRegisterReloadListener(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(new ClientReloadListener());
        }
    }

    // ===== 後方互換 API (SyncDataMessage → DamageIndicatorRenderer への委譲) =====

    public static void addDamageIndicator(double x, double y, double z, float damage, boolean isCritical) {
        DamageIndicatorRenderer.addDamageIndicator(x, y, z, damage, isCritical);
    }

    public static void addShotgunDamageIndicator(double x, double y, double z, float damage, boolean isCritical) {
        DamageIndicatorRenderer.addShotgunDamageIndicator(x, y, z, damage, isCritical);
    }

    public static void addDropIndicator(double x, double y, double z, String itemName) {
        DamageIndicatorRenderer.addDropIndicator(x, y, z, itemName);
    }

    public static void addNotification(String text, int color) {
        NotificationManager.add(text, color);
    }

    // ===== チャットメッセージ → HUD リダイレクト =====

    @SubscribeEvent
    public static void onChatReceived(ClientChatReceivedEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean inRogue = mc.level.dimension().location().getNamespace().equals("tac_rogue") || RunManager.isRunActive();
        if (!inRogue) return;

        try {
            String msg = event.getMessage().getString();
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
        if (event.getItemStack().is(net.minecraft.world.item.Items.SNOWBALL)) {
            event.getToolTip().add(Component.translatable("tooltip.tac_rogue.snowball_distract"));
        }
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();

        // 画面フックの無限ループを防止（すでにカスタム画面ならスキップ）
        if (event.getScreen() instanceof StashScreen || event.getScreen() instanceof RogueInventoryScreen) {
            return;
        }

        // Stash Terminal: バニラ ChestScreen → カスタム StashScreen に差し替え
        if (event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen containerScreen) {
            String title = containerScreen.getTitle().getString();
            if (title.contains("STASH TERMINAL")) {
                event.setCanceled(true);
                mc.tell(() -> {
                    StashScreen stashScreen = new StashScreen(containerScreen.getMenu(), mc.player.getInventory(), containerScreen.getTitle());
                    mc.setScreen(stashScreen);
                });
                return;
            }
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
            NotificationManager.render(graphics, mc, width, height);

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

                            // 実際の着弾点を計算してプロットする
                            float pTick = mc.getFrameTime();
                            net.minecraft.world.phys.Vec3 start = player.getEyePosition(pTick);
                            net.minecraft.world.phys.Vec3 look = player.getViewVector(pTick);
                            net.minecraft.world.phys.Vec3 end = start.add(look.x * 64.0, look.y * 64.0, look.z * 64.0);

                            net.minecraft.world.phys.HitResult hit = mc.level.clip(new net.minecraft.world.level.ClipContext(
                                start, end, 
                                net.minecraft.world.level.ClipContext.Block.COLLIDER, 
                                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
                            
                            net.minecraft.world.phys.Vec3 targetPos = (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) 
                                ? hit.getLocation() 
                                : end;
                            
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

    // ===== Client Tick — 各サブシステムに委譲 =====

    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
            HudRenderer.tickVictory();
            DamageIndicatorRenderer.tick();
            NotificationManager.tick();
            DynamicLightManager.tick();

            Minecraft mc = Minecraft.getInstance();

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

    public static org.joml.Matrix4f lastViewMatrix = new org.joml.Matrix4f();
    public static org.joml.Matrix4f lastProjectionMatrix = new org.joml.Matrix4f();

    @SubscribeEvent
    public static void onRenderLevelStage(net.minecraftforge.client.event.RenderLevelStageEvent event) {
        DamageIndicatorRenderer.renderWorld(event);
        if (event.getStage() == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            lastViewMatrix.set(event.getPoseStack().last().pose());
            lastProjectionMatrix.set(event.getProjectionMatrix());
        }
    }

    // ===== ログイン時 / ワールド入室時のチュートリアル表示 =====
    private static boolean welcomeScreenShown = false; // 1回の起動につき1度だけチェック

    @SubscribeEvent
    public static void onClientPlayerJoin(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft mc = Minecraft.getInstance();
        // 少し遅らせて画面を開く
        mc.tell(() -> {
            if (mc.player != null) {
                if (!welcomeScreenShown && !mc.player.getTags().contains("rogue:tutorial_seen")) {
                    java.io.File marker = new java.io.File(mc.gameDirectory, ".tac_rogue_tutorial");
                    if (!marker.exists()) {
                        mc.setScreen(new com.levanilla.rogue.client.hud.WelcomeScreen());
                        try { marker.createNewFile(); } catch (Exception ignored) {}
                    }
                    welcomeScreenShown = true;
                }
            }
        });
    }

    // ===== カスタムキーバインド =====

    @SubscribeEvent
    public static void onKey(net.minecraftforge.client.event.InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;

        // Fキー（オフハンドスワップ）ブロック
        if (event.getAction() == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            if (mc.level != null && mc.level.dimension().location().getNamespace().equals("tac_rogue")) {
                if (event.getKey() == org.lwjgl.glfw.GLFW.GLFW_KEY_F) {
                    return;
                }
            }
        }
    }

    // ===== LeaWinds 三人称互換 =====

    @SubscribeEvent
    public static void onRenderTick(net.minecraftforge.event.TickEvent.RenderTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (!LeaWindsCompat.isLeawindAvailable()) return;
        LeaWindsCompat.syncThirdPersonGunAim();
    }
}
