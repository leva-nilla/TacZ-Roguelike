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
    private static long renderFrameSerial = 0L;
    private static AimTargetCache renderAimCache = null;

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

    public static void beginRenderFrame() {
        renderFrameSerial++;
        renderAimCache = null;
    }

    public static void toggleAdsForceFirstPerson() {
        adsForceFirstPerson = !adsForceFirstPerson;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(
                "§e[TPS] ADS一人称切替: " + (adsForceFirstPerson ? "§aON §7(スコープ武器向け)" : "§cOFF §7(常に三人称)")));
        }
    }

    public static net.minecraft.world.phys.Vec3 resolveThirdPersonAimTarget(Minecraft mc, double range) {
        if (mc == null || mc.player == null || mc.level == null || mc.gameRenderer == null) return null;

        net.minecraft.world.phys.HitResult leaWindsHit = getLeaWindsHitResult();
        if (leaWindsHit != null && leaWindsHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            return resolvePlayerShotTarget(mc, leaWindsHit.getLocation(), range);
        }

        net.minecraft.client.Camera camera = mc.gameRenderer.getMainCamera();
        net.minecraft.world.phys.Vec3 start = camera.getPosition();
        net.minecraft.world.phys.Vec3 look = net.minecraft.world.phys.Vec3.directionFromRotation(camera.getXRot(), camera.getYRot());
        net.minecraft.world.phys.Vec3 cameraTarget = traceAimTarget(mc, start, look, range);
        return resolvePlayerShotTarget(mc, cameraTarget, range);
    }

    public static net.minecraft.world.phys.Vec3 resolveThirdPersonAimTargetForRender(Minecraft mc, double range) {
        if (mc == null || mc.player == null || mc.level == null || mc.gameRenderer == null) return null;
        AimTargetCache cached = renderAimCache;
        if (cached != null
            && cached.frameSerial == renderFrameSerial
            && cached.player == mc.player
            && Double.compare(cached.range, range) == 0) {
            return cached.target;
        }
        net.minecraft.world.phys.Vec3 target = resolveThirdPersonAimTarget(mc, range);
        renderAimCache = new AimTargetCache(renderFrameSerial, mc.player, range, target);
        return target;
    }

    private record AimTargetCache(long frameSerial, net.minecraft.client.player.LocalPlayer player,
                                  double range, net.minecraft.world.phys.Vec3 target) {}

    private static net.minecraft.world.phys.HitResult getLeaWindsHitResult() {
        try {
            Class<?> tpClass = Class.forName("com.github.leawind.thirdperson.ThirdPerson");
            if (!(boolean) tpClass.getMethod("isAvailable").invoke(null)) return null;
            Object cameraAgent = tpClass.getField("CAMERA_AGENT").get(null);
            Object hitResult = cameraAgent.getClass().getMethod("getHitResult").invoke(cameraAgent);
            return hitResult instanceof net.minecraft.world.phys.HitResult hr ? hr : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static net.minecraft.world.phys.Vec3 traceAimTarget(Minecraft mc, net.minecraft.world.phys.Vec3 start,
                                                               net.minecraft.world.phys.Vec3 look, double range) {
        net.minecraft.world.phys.Vec3 end = start.add(look.scale(range));
        net.minecraft.world.phys.HitResult blockHit = mc.level.clip(new net.minecraft.world.level.ClipContext(
            start, end,
            net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE,
            mc.player));

        net.minecraft.world.phys.Vec3 target = blockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
            ? end
            : blockHit.getLocation();

        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
            Math.min(start.x, end.x), Math.min(start.y, end.y), Math.min(start.z, end.z),
            Math.max(start.x, end.x), Math.max(start.y, end.y), Math.max(start.z, end.z)
        ).inflate(1.0D);
        net.minecraft.world.phys.EntityHitResult entityHit =
            net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                mc.player,
                start,
                end,
                box,
                entity -> entity != mc.player && !entity.isSpectator() && entity.isPickable(),
                range * range);

        if (entityHit != null && start.distanceToSqr(entityHit.getLocation()) < start.distanceToSqr(target)) {
            target = entityHit.getLocation();
        }
        return target;
    }

    private static net.minecraft.world.phys.Vec3 resolvePlayerShotTarget(Minecraft mc,
                                                                         net.minecraft.world.phys.Vec3 cameraTarget,
                                                                         double range) {
        if (cameraTarget == null || mc.player == null) return cameraTarget;
        net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition(1.0f);
        net.minecraft.world.phys.Vec3 toTarget = cameraTarget.subtract(eye);
        if (toTarget.lengthSqr() < 1.0E-6D) {
            net.minecraft.client.Camera camera = mc.gameRenderer.getMainCamera();
            toTarget = net.minecraft.world.phys.Vec3.directionFromRotation(camera.getXRot(), camera.getYRot());
        }
        net.minecraft.world.phys.Vec3 shotLook = toTarget.normalize();
        double shotRange = Math.max(range, Math.sqrt(toTarget.lengthSqr()) + 2.0D);
        return traceAimTarget(mc, eye, shotLook, shotRange);
    }

    private static net.minecraft.client.CameraType previousCameraType = null;
    private static boolean wasForcedFirstPerson = false;
    private static final long AIM_SYNC_SHOT_GRACE_MS = 280L;

    /** 三人称視点での銃エイム同期 */
    @SuppressWarnings("resource")
    public static void syncThirdPersonGunAim() {
        syncThirdPersonGunAim(false);
    }

    public static void syncThirdPersonGunAimForShot() {
        syncThirdPersonGunAim(true);
    }

    @SuppressWarnings("resource")
    private static void syncThirdPersonGunAim(boolean forceForShot) {
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

            if (holdingGun) {
                if (forceForShot) {
                    sendAimRotationPacket(mc);
                }
                return;
            }

            if (!forceForShot && holdingGun && LeaWindsRecoilController.isRecoilActive()) {
                RecoilDebugLogger.logAimSync("AIM_SYNC_SKIPPED_RECOIL");
                return;
            }
            if (!forceForShot && holdingGun && isWithinRecentTacZShotGrace()) {
                RecoilDebugLogger.logAimSync("AIM_SYNC_SKIPPED_SHOT_GRACE");
                return;
            }

            RecoilDebugLogger.logAimSync("AIM_SYNC_MELEE_BEFORE");

            // === 三人称のまま: キャラをカメラの照準方向に向ける ===
            net.minecraft.world.phys.Vec3 target = resolveThirdPersonAimTarget(mc, 96.0D);
            if (target != null) {
                net.minecraft.world.phys.Vec3 eyePos = mc.player.getEyePosition(1.0f);
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

            RecoilDebugLogger.logAimSync("AIM_SYNC_MELEE_AFTER");

        } catch (Exception ignored) {
            // LeaWinds / TacZ が無い場合は何もしない
        }
    }

    private static boolean isWithinRecentTacZShotGrace() {
        TacZReflection.init();
        long ts = TacZReflection.getShootTimeStamp();
        return ts > 0L && System.currentTimeMillis() - ts <= AIM_SYNC_SHOT_GRACE_MS;
    }

    private static void sendAimRotationPacket(Minecraft mc) {
        if (mc.getConnection() == null || mc.player == null) return;
        net.minecraft.world.phys.Vec3 target = resolveThirdPersonAimTarget(mc, 96.0D);
        if (target == null) return;

        net.minecraft.world.phys.Vec3 eyePos = mc.player.getEyePosition(1.0f);
        double dx = target.x - eyePos.x;
        double dy = target.y - eyePos.y;
        double dz = target.z - eyePos.z;
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        if (horizDist < 0.001D) return;

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizDist));
        pitch = net.minecraft.util.Mth.clamp(pitch, -90.0f, 90.0f);

        RecoilDebugLogger.logAimSync("AIM_ROT_PACKET_BEFORE");
        mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(
            yaw,
            pitch,
            mc.player.onGround()
        ));
        RecoilDebugLogger.logAimSync("AIM_ROT_PACKET_SENT");
    }
}
