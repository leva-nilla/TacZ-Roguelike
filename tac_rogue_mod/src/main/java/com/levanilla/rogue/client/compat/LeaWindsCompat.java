package com.levanilla.rogue.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * LeaWinds Third Person MOD との三人称視点互換処理。
 * リフレクションで LeaWinds の視点制御を行い、TacZ の銃器との連携を実現する。
 */
public final class LeaWindsCompat {

    /** LeaWinds が利用可能かどうかのキャッシュ */
    private static Boolean leawindAvailable = null;

    /** ADS 時に一人称に強制するか — スコープ付き武器は常に一人称切替 */
    private static boolean adsForceFirstPerson = false;

    private static boolean leawindCrosshairDisabled = false;

    public static boolean isLeawindAvailable() {
        if (leawindAvailable == null) {
            try {
                Class.forName("com.github.leawind.thirdperson.ThirdPerson");
                leawindAvailable = true;
            } catch (ClassNotFoundException e) {
                leawindAvailable = false;
            }
        }
        
        // クロスヘアの強制非表示化
        if (leawindAvailable && !leawindCrosshairDisabled) {
            try {
                Class<?> thirdPersonClass = Class.forName("com.github.leawind.thirdperson.ThirdPerson");
                Object configObj = thirdPersonClass.getMethod("getConfig").invoke(null);
                java.lang.reflect.Field field = configObj.getClass().getField("is_draw_crosshair");
                field.set(configObj, false);
                leawindCrosshairDisabled = true;
            } catch (Exception e) {
                // Ignore if field doesn't exist
                leawindCrosshairDisabled = true; // prevent spamming reflection
            }
        }
        return leawindAvailable;
    }

    public static boolean isAdsForceFirstPerson() { return adsForceFirstPerson; }

    public static void toggleAdsForceFirstPerson() {
        adsForceFirstPerson = !adsForceFirstPerson;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(
                "§e[TPS] ADS一人称切替: " + (adsForceFirstPerson ? "§aON §7(スコープ武器向け)" : "§cOFF §7(常に三人称)")));
        }
    }

    private static net.minecraft.client.CameraType previousCameraType = null;
    private static boolean wasForcedFirstPerson = false;
    private static float lastRecoilPitch = 0f;
    private static float lastRecoilYaw = 0f;

    /** 三人称視点での銃エイム同期 */
    @SuppressWarnings("resource")
    public static void syncThirdPersonGunAim() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        try {
            Class<?> tpClass = Class.forName("com.github.leawind.thirdperson.ThirdPerson");
            if (!(boolean) tpClass.getMethod("isAvailable").invoke(null)) return;

            Class<?> statusClass = Class.forName("com.github.leawind.thirdperson.ThirdPersonStatus");
            boolean inThirdPerson = (boolean) statusClass.getMethod("isRenderingInThirdPerson").invoke(null);

            // TacZ 銃所持チェック
            boolean holdingGun = com.tacz.guns.api.item.IGun.mainHandHoldGun(mc.player);

            boolean holdingMelee = false;
            net.minecraft.nbt.CompoundTag handTag = mc.player.getMainHandItem().getTag();
            if (handTag != null && handTag.contains("MeleeWeaponId")) {
                holdingMelee = true;
            }

            // === スコープ付き武器のADS時は常に一人称に切替 ===
            if (holdingGun) {
                try {
                    var operator = com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player);
                    float aimProgress = operator.getClientAimingProgress(0.0f);

                    if (aimProgress > 0.0f) {
                        boolean hasScope = com.levanilla.rogue.core.registry.TacZGunRegistry.hasScope(mc.player.getMainHandItem());

                        if (hasScope || adsForceFirstPerson) {
                            if (!wasForcedFirstPerson && !mc.options.getCameraType().isFirstPerson()) {
                                previousCameraType = mc.options.getCameraType();
                                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                                wasForcedFirstPerson = true;
                            }
                            return; // 視点変更中は三人称回転同期を行わない
                        }
                    } else {
                        // ADS解除時に元の視点に戻す
                        if (wasForcedFirstPerson) {
                            if (mc.options.getCameraType().isFirstPerson() && previousCameraType != null) {
                                mc.options.setCameraType(previousCameraType);
                            }
                            wasForcedFirstPerson = false;
                            previousCameraType = null;
                        }
                    }
                } catch (Exception ignored) {}
            } else {
                // 銃を持っていない場合もリセット
                if (wasForcedFirstPerson) {
                    if (mc.options.getCameraType().isFirstPerson() && previousCameraType != null) {
                        mc.options.setCameraType(previousCameraType);
                    }
                    wasForcedFirstPerson = false;
                    previousCameraType = null;
                }
            }

            if (!holdingGun && !holdingMelee) return;
            if (!inThirdPerson && !wasForcedFirstPerson) return; // 三人称のときのみ回転操作

            // === 反動(Recoil)の適用 ===
            TacZReflection.init();
            float[] offsets = TacZReflection.getCurrentRecoilOffsets();
            float recoilPitch = offsets[0]; 
            float recoilYaw = offsets[1];
            
            // 1チック前との差分(Delta)を計算し、仮想マウス入力としてターン(視点移動)処理へ注入
            float deltaPitch = recoilPitch - lastRecoilPitch;
            float deltaYaw = recoilYaw - lastRecoilYaw;
            lastRecoilPitch = recoilPitch;
            lastRecoilYaw = recoilYaw;
            
            if (deltaPitch != 0 || deltaYaw != 0) {
                // 三人称時は反動が小さく感じられやすいため、1.5倍にして反動を強調する
                float recoilMultiplier = 1.5f;
                // mc.player.turn() は内部で setYRot, setXRot を呼び、Leawinds がそれをフックしてカメラを回す
                // ※上を向くにはピッチをマイナスにする必要があるため符号反転
                mc.player.turn(-deltaYaw * recoilMultiplier, -deltaPitch * recoilMultiplier);
            }

            // === 三人称のまま: キャラをカメラの照準方向に向ける ===
            Object cameraAgent = tpClass.getField("CAMERA_AGENT").get(null);
            Object hitResult = cameraAgent.getClass().getMethod("getHitResult").invoke(cameraAgent);

            if (hitResult instanceof net.minecraft.world.phys.HitResult hr
                    && hr.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                net.minecraft.world.phys.Vec3 eyePos = mc.player.getEyePosition(1.0f);
                net.minecraft.world.phys.Vec3 target = hr.getLocation();
                double dx = target.x - eyePos.x;
                double dy = target.y - eyePos.y;
                double dz = target.z - eyePos.z;
                double horizDist = Math.sqrt(dx * dx + dz * dz);
                if (horizDist < 0.001) {
                    net.minecraft.client.Camera camera = mc.gameRenderer.getMainCamera();
                    mc.player.setYRot(camera.getYRot());
                    mc.player.setXRot(camera.getXRot());
                } else {
                    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizDist));
                    mc.player.setYRot(yaw);
                    mc.player.setXRot(net.minecraft.util.Mth.clamp(pitch, -90.0f, 90.0f));
                }
            } else {
                net.minecraft.client.Camera camera = mc.gameRenderer.getMainCamera();
                mc.player.setYRot(camera.getYRot());
                mc.player.setXRot(camera.getXRot());
            }
            mc.player.yRotO = mc.player.getYRot();
            mc.player.xRotO = mc.player.getXRot();

        } catch (Exception ignored) {
            // LeaWinds / TacZ が無い場合は何もしない
        }
    }
}
