package com.levanilla.rogue.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * LeaWinds Third Person MOD との三人称視点互換処理。
 * リフレクションで LeaWinds の視点制御を行い、TacZ の銃器との連携を実現する。
 */
public final class LeaWindsCompat {

    /** LeaWinds が利用可能かどうかのキャッシュ */
    private static Boolean leawindAvailable = null;

    public enum ScopeAdsMode {
        AUTO_SCOPED_FIRST_PERSON("gui.tac_rogue.camera.scope_ads.auto"),
        FORCE_FIRST_PERSON("gui.tac_rogue.camera.scope_ads.force"),
        STAY_THIRD_PERSON("gui.tac_rogue.camera.scope_ads.third_person");

        private final String translationKey;

        ScopeAdsMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    private static boolean cameraSettingsLoaded = false;
    private static ScopeAdsMode scopeAdsMode = ScopeAdsMode.AUTO_SCOPED_FIRST_PERSON;

    private static boolean leawindCrosshairDisabled = false;
    private static long renderFrameSerial = 0L;
    private static AimTargetCache renderAimCache = null;
    private static InteractionAimTargetCache renderInteractionCache = null;
    private static Object savedRotateMode = null;
    private static boolean gunRotateModeApplied = false;
    private static int lastPassiveAimSyncTick = -20;

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

    public static boolean isAdsForceFirstPerson() { return getScopeAdsMode() == ScopeAdsMode.FORCE_FIRST_PERSON; }

    public static ScopeAdsMode getScopeAdsMode() {
        loadCameraSettings();
        return scopeAdsMode;
    }

    public static String getScopeAdsModeTranslationKey() {
        return getScopeAdsMode().translationKey();
    }

    public static void beginRenderFrame() {
        renderFrameSerial++;
        renderAimCache = null;
        renderInteractionCache = null;
    }

    public static void toggleAdsForceFirstPerson() {
        cycleScopeAdsMode();
    }

    public static void cycleScopeAdsMode() {
        ScopeAdsMode[] modes = ScopeAdsMode.values();
        ScopeAdsMode current = getScopeAdsMode();
        scopeAdsMode = modes[(current.ordinal() + 1) % modes.length];
        saveCameraSettings();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.translatable(
                "message.tac_rogue.camera.scope_ads",
                Component.translatable(scopeAdsMode.translationKey())));
        }
    }

    private static void loadCameraSettings() {
        if (cameraSettingsLoaded) return;
        cameraSettingsLoaded = true;
        Path path = cameraSettingsPath();
        if (!Files.exists(path)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            String mode = properties.getProperty("third_person_ads", scopeAdsMode.name());
            scopeAdsMode = ScopeAdsMode.valueOf(mode);
        } catch (Exception ignored) {
            scopeAdsMode = ScopeAdsMode.AUTO_SCOPED_FIRST_PERSON;
        }
    }

    private static void saveCameraSettings() {
        Path path = cameraSettingsPath();
        Properties properties = new Properties();
        properties.setProperty("third_person_ads", scopeAdsMode.name());
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, "TacZ Roguelike camera settings");
            }
        } catch (IOException ignored) {
        }
    }

    private static Path cameraSettingsPath() {
        Minecraft mc = Minecraft.getInstance();
        return mc.gameDirectory.toPath().resolve("config").resolve("tac_rogue_camera.properties");
    }

    public static net.minecraft.world.phys.Vec3 resolveThirdPersonAimTarget(Minecraft mc, double range) {
        GunAimResult result = resolveThirdPersonAimResult(mc, range);
        return result == null ? null : result.target();
    }

    public static GunAimResult resolveThirdPersonGunAimForRender(Minecraft mc, double range) {
        if (mc == null || mc.player == null || mc.level == null || mc.gameRenderer == null) return null;
        AimTargetCache cached = renderAimCache;
        if (cached != null
            && cached.frameSerial == renderFrameSerial
            && cached.player == mc.player
            && Double.compare(cached.range, range) == 0) {
            return cached.result;
        }
        GunAimResult result = resolveThirdPersonAimResult(mc, range);
        renderAimCache = new AimTargetCache(renderFrameSerial, mc.player, range, result);
        return result;
    }

    private static GunAimResult resolveThirdPersonAimResult(Minecraft mc, double range) {
        if (mc == null || mc.player == null || mc.level == null || mc.gameRenderer == null) return null;

        net.minecraft.client.Camera camera = mc.gameRenderer.getMainCamera();
        net.minecraft.world.phys.Vec3 shotLook = resolveCameraAimDirection(camera);
        net.minecraft.world.phys.Vec3 bulletStart = getTacZBulletStart(mc.player);
        AimTraceResult bulletTrace = traceAim(mc, bulletStart, shotLook, range);
        AimTraceResult cameraTrace = traceAim(mc, camera.getPosition(), shotLook, range);
        boolean cameraObstructed = isCameraObstructingShot(camera.getPosition(), cameraTrace, bulletTrace);
        return new GunAimResult(bulletTrace.target(), cameraObstructed, cameraTrace.hit() ? cameraTrace.target() : null);
    }

    public static net.minecraft.world.phys.Vec3 resolveThirdPersonAimTargetForRender(Minecraft mc, double range) {
        GunAimResult result = resolveThirdPersonGunAimForRender(mc, range);
        return result == null ? null : result.target();
    }

    public static net.minecraft.world.phys.Vec3 resolveThirdPersonAimTargetForShot(Minecraft mc, double range) {
        if (mc == null || mc.player == null || mc.level == null || mc.gameRenderer == null) return null;
        GunAimResult result = resolveThirdPersonAimResult(mc, range);
        renderAimCache = new AimTargetCache(renderFrameSerial, mc.player, range, result);
        return result == null ? null : result.target();
    }

    public static InteractionAimResult resolveThirdPersonInteractionTargetForRender(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null || mc.gameRenderer == null) return null;
        double range = Math.max(getBlockReach(mc), getEntityReach(mc));
        InteractionAimTargetCache cached = renderInteractionCache;
        if (cached != null
            && cached.frameSerial == renderFrameSerial
            && cached.player == mc.player
            && Double.compare(cached.range, range) == 0) {
            return cached.result;
        }
        InteractionAimResult result = resolveThirdPersonInteractionTarget(mc, range);
        renderInteractionCache = new InteractionAimTargetCache(renderFrameSerial, mc.player, range, result);
        return result;
    }

    private record AimTargetCache(long frameSerial, net.minecraft.client.player.LocalPlayer player,
                                  double range, GunAimResult result) {}
    public record GunAimResult(net.minecraft.world.phys.Vec3 target, boolean cameraObstructed,
                               net.minecraft.world.phys.Vec3 cameraHit) {}
    public record InteractionAimResult(net.minecraft.world.phys.Vec3 target, boolean reachable) {}
    private record InteractionAimTargetCache(long frameSerial, net.minecraft.client.player.LocalPlayer player,
                                             double range, InteractionAimResult result) {}

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

    private static InteractionAimResult resolveThirdPersonInteractionTarget(Minecraft mc, double maxRange) {
        InteractionAimResult projectileTarget = resolveSnowballImpactTarget(mc);
        if (projectileTarget != null) {
            return projectileTarget;
        }

        InteractionAimResult rogueInteractTarget = resolveRogueCustomInteractionTarget(mc);
        if (rogueInteractTarget != null) {
            return rogueInteractTarget;
        }

        net.minecraft.world.phys.Vec3 playerEye = mc.player.getEyePosition(1.0F);
        net.minecraft.world.phys.HitResult vanillaHit = resolveVanillaInteractionHit(mc);
        if (vanillaHit != null && vanillaHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            double reach = vanillaHit.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY
                ? getEntityReach(mc)
                : getBlockReach(mc);
            boolean reachable = isWithinReach(playerEye, vanillaHit.getLocation(), reach);
            return new InteractionAimResult(vanillaHit.getLocation(), reachable);
        }

        net.minecraft.world.phys.Vec3 look = mc.player.getViewVector(1.0F);
        return traceInteractionTarget(mc, playerEye, playerEye, look, getBlockReach(mc), getEntityReach(mc));
    }

    private static InteractionAimResult resolveRogueCustomInteractionTarget(Minecraft mc) {
        if (!isRogueContext(mc)) return null;
        net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition(1.0F);

        if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult entityHit
            && entityHit.getEntity() instanceof com.levanilla.rogue.world.TacRogueNpcEntity npc
            && npc.distanceToSqr(mc.player) <= 25.0D) {
            return new InteractionAimResult(entityHit.getLocation(), true);
        }

        net.minecraft.world.phys.Vec3 look = mc.player.getViewVector(1.0F);
        net.minecraft.world.phys.Vec3 end = eye.add(look.scale(5.0D));
        net.minecraft.world.phys.AABB searchBox = mc.player.getBoundingBox().expandTowards(look.scale(5.0D)).inflate(1.0D);
        java.util.List<com.levanilla.rogue.world.TacRogueNpcEntity> npcs =
            mc.level.getEntitiesOfClass(com.levanilla.rogue.world.TacRogueNpcEntity.class, searchBox, e -> e.isAlive());

        net.minecraft.world.phys.Vec3 bestHit = null;
        double bestDist = Double.MAX_VALUE;
        for (com.levanilla.rogue.world.TacRogueNpcEntity npc : npcs) {
            java.util.Optional<net.minecraft.world.phys.Vec3> hit = npc.getBoundingBox().inflate(0.6D).clip(eye, end);
            if (hit.isEmpty()) continue;
            double dist = eye.distanceToSqr(hit.get());
            if (dist < bestDist) {
                bestDist = dist;
                bestHit = hit.get();
            }
        }
        return bestHit == null ? null : new InteractionAimResult(bestHit, true);
    }

    private static boolean isRogueContext(Minecraft mc) {
        if (mc.level == null) return com.levanilla.rogue.core.RunManager.isRunActive();
        return mc.level.dimension().location().getNamespace().equals("tac_rogue")
            || com.levanilla.rogue.core.RunManager.isRunActive();
    }

    private static net.minecraft.world.phys.HitResult resolveVanillaInteractionHit(Minecraft mc) {
        net.minecraft.world.entity.Entity picker = mc.player;
        double blockReach = getBlockReach(mc);
        double entityReach = getEntityReach(mc);

        net.minecraft.world.phys.HitResult blockHit = picker.pick(blockReach, 1.0F, false);
        net.minecraft.world.phys.Vec3 eye = picker.getEyePosition(1.0F);
        net.minecraft.world.phys.Vec3 look = picker.getViewVector(1.0F);

        double nearestDistanceSqr = entityReach * entityReach;
        if (blockHit != null && blockHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            nearestDistanceSqr = Math.min(nearestDistanceSqr, eye.distanceToSqr(blockHit.getLocation()));
        }

        double searchRange = Math.max(blockReach, entityReach);
        net.minecraft.world.phys.Vec3 end = eye.add(look.scale(searchRange));
        net.minecraft.world.phys.AABB searchBox = picker.getBoundingBox()
            .expandTowards(look.scale(searchRange))
            .inflate(1.0D);
        net.minecraft.world.phys.EntityHitResult entityHit =
            net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                picker,
                eye,
                end,
                searchBox,
                entity -> entity != picker && !entity.isSpectator() && entity.isPickable(),
                nearestDistanceSqr);

        if (entityHit != null) {
            return entityHit;
        }
        if (blockHit != null) {
            return blockHit;
        }
        return mc.hitResult;
    }

    private static InteractionAimResult resolveSnowballImpactTarget(Minecraft mc) {
        if (mc.player == null || mc.level == null) return null;
        if (!mc.player.getMainHandItem().is(net.minecraft.world.item.Items.SNOWBALL)) return null;

        net.minecraft.world.phys.Vec3 pos = new net.minecraft.world.phys.Vec3(
            mc.player.getX(),
            mc.player.getEyeY() - 0.10000000149011612D,
            mc.player.getZ());
        net.minecraft.world.phys.Vec3 velocity = mc.player.getViewVector(1.0F).normalize().scale(1.5D)
            .add(mc.player.getDeltaMovement().x, mc.player.onGround() ? 0.0D : mc.player.getDeltaMovement().y, mc.player.getDeltaMovement().z);

        net.minecraft.world.phys.Vec3 last = pos;
        for (int tick = 0; tick < 140; tick++) {
            net.minecraft.world.phys.Vec3 next = pos.add(velocity);
            net.minecraft.world.phys.HitResult blockHit = mc.level.clip(new net.minecraft.world.level.ClipContext(
                pos, next,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                mc.player));
            net.minecraft.world.phys.Vec3 segmentEnd = blockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                ? next
                : blockHit.getLocation();

            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(pos, segmentEnd).inflate(1.0D);
            net.minecraft.world.phys.EntityHitResult entityHit =
                net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                    mc.player,
                    pos,
                    segmentEnd,
                    box,
                    entity -> entity != mc.player && !entity.isSpectator() && entity.isPickable(),
                    pos.distanceToSqr(segmentEnd));

            if (entityHit != null) {
                return new InteractionAimResult(entityHit.getLocation(), true);
            }
            if (blockHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                return new InteractionAimResult(blockHit.getLocation(), true);
            }

            last = next;
            pos = next;
            double drag = mc.player.isInWater() ? 0.8D : 0.99D;
            velocity = velocity.scale(drag).add(0.0D, -0.03D, 0.0D);
            if (pos.y < mc.level.getMinBuildHeight() - 8) {
                break;
            }
        }
        return new InteractionAimResult(last, false);
    }

    private static InteractionAimResult traceInteractionTarget(Minecraft mc,
                                                               net.minecraft.world.phys.Vec3 cameraStart,
                                                               net.minecraft.world.phys.Vec3 playerEye,
                                                               net.minecraft.world.phys.Vec3 look,
                                                               double blockReach,
                                                               double entityReach) {
        double maxRange = Math.max(blockReach, entityReach);
        double cameraToPlayer = cameraStart.distanceTo(playerEye);
        double traceRange = maxRange + cameraToPlayer + 1.0D;
        net.minecraft.world.phys.Vec3 traceEnd = cameraStart.add(look.scale(traceRange));
        net.minecraft.world.phys.HitResult blockHit = mc.level.clip(new net.minecraft.world.level.ClipContext(
            cameraStart, traceEnd,
            net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE,
            mc.player));

        net.minecraft.world.phys.Vec3 target = null;
        boolean reachable = false;
        if (blockHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            target = blockHit.getLocation();
            reachable = isWithinReach(playerEye, target, blockReach);
        }

        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
            Math.min(cameraStart.x, traceEnd.x), Math.min(cameraStart.y, traceEnd.y), Math.min(cameraStart.z, traceEnd.z),
            Math.max(cameraStart.x, traceEnd.x), Math.max(cameraStart.y, traceEnd.y), Math.max(cameraStart.z, traceEnd.z)
        ).inflate(1.0D);
        net.minecraft.world.phys.EntityHitResult entityHit =
            net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                mc.player,
                cameraStart,
                traceEnd,
                box,
                entity -> entity != mc.player && !entity.isSpectator() && entity.isPickable(),
                traceRange * traceRange);

        if (entityHit != null
            && (target == null || cameraStart.distanceToSqr(entityHit.getLocation()) < cameraStart.distanceToSqr(target))) {
            target = entityHit.getLocation();
            reachable = isWithinReach(playerEye, target, entityReach);
        }
        if (target == null) {
            return new InteractionAimResult(playerEye.add(look.scale(maxRange)), false);
        }
        return new InteractionAimResult(target, reachable);
    }

    private static boolean isWithinReach(net.minecraft.world.phys.Vec3 origin,
                                         net.minecraft.world.phys.Vec3 target,
                                         double reach) {
        double slack = 0.25D;
        double effectiveReach = reach + slack;
        return origin.distanceToSqr(target) <= effectiveReach * effectiveReach;
    }

    private static double getBlockReach(Minecraft mc) {
        try {
            if (mc.player != null) {
                return Math.max(1.0D, mc.player.getAttributeValue(net.minecraftforge.common.ForgeMod.BLOCK_REACH.get()));
            }
        } catch (Throwable ignored) {
        }
        return mc.gameMode != null && mc.gameMode.hasFarPickRange() ? 5.0D : 4.5D;
    }

    private static double getEntityReach(Minecraft mc) {
        try {
            if (mc.player != null) {
                return Math.max(1.0D, mc.player.getAttributeValue(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get()));
            }
        } catch (Throwable ignored) {
        }
        return 3.0D;
    }

    private static net.minecraft.world.phys.Vec3 traceAimTarget(Minecraft mc, net.minecraft.world.phys.Vec3 start,
                                                               net.minecraft.world.phys.Vec3 look, double range) {
        return traceAim(mc, start, look, range).target();
    }

    private record AimTraceResult(net.minecraft.world.phys.Vec3 target, boolean hit) {}

    private static AimTraceResult traceAim(Minecraft mc, net.minecraft.world.phys.Vec3 start,
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
        boolean hit = blockHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS;

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
            hit = true;
        }
        return new AimTraceResult(target, hit);
    }

    private static boolean isCameraObstructingShot(net.minecraft.world.phys.Vec3 cameraPos,
                                                   AimTraceResult cameraTrace,
                                                   AimTraceResult bulletTrace) {
        if (cameraTrace == null || bulletTrace == null || !cameraTrace.hit()) return false;
        double cameraHitDistance = cameraPos.distanceTo(cameraTrace.target());
        double bulletTargetDistance = cameraPos.distanceTo(bulletTrace.target());
        if (cameraHitDistance + 0.75D >= bulletTargetDistance) return false;
        return cameraTrace.target().distanceToSqr(bulletTrace.target()) > 0.75D * 0.75D;
    }

    private static net.minecraft.world.phys.Vec3 resolveCameraAimDirection(net.minecraft.client.Camera camera) {
        return net.minecraft.world.phys.Vec3.directionFromRotation(camera.getXRot(), camera.getYRot());
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
        if (mc.player == null || mc.level == null) {
            restoreLeaWindsRotateMode();
            return;
        }

        try {
            Class<?> tpClass = Class.forName("com.github.leawind.thirdperson.ThirdPerson");
            if (!(boolean) tpClass.getMethod("isAvailable").invoke(null)) {
                restoreLeaWindsRotateMode();
                return;
            }

            Class<?> statusClass = Class.forName("com.github.leawind.thirdperson.ThirdPersonStatus");
            boolean inThirdPerson = (boolean) statusClass.getMethod("isRenderingInThirdPerson").invoke(null);

            // TacZ 銃所持チェック
            boolean holdingGun = isHoldingTacZGun(mc.player);

            // === スコープ付き武器のADS時は常に一人称に切替 ===
            if (holdingGun) {
                try {
                    var operator = com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player);
                    float aimProgress = operator.getClientAimingProgress(0.0f);

                    if (aimProgress > 0.0f) {
                        boolean hasScope = com.levanilla.rogue.core.registry.TacZGunRegistry.hasScope(mc.player.getMainHandItem());
                        ScopeAdsMode scopeMode = getScopeAdsMode();

                        if (scopeMode == ScopeAdsMode.FORCE_FIRST_PERSON
                            || (scopeMode == ScopeAdsMode.AUTO_SCOPED_FIRST_PERSON && hasScope)) {
                            if (!wasForcedFirstPerson && !mc.options.getCameraType().isFirstPerson()) {
                                previousCameraType = mc.options.getCameraType();
                                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                                wasForcedFirstPerson = true;
                            }
                            restoreLeaWindsRotateMode();
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

            if (!holdingGun) {
                restoreLeaWindsRotateMode();
                return;
            }

            if (!inThirdPerson || mc.options.getCameraType().isFirstPerson()) {
                restoreLeaWindsRotateMode();
                return;
            }

            applyLeaWindsGunRotateMode();
            if (!forceForShot && LeaWindsRecoilController.isRecoilActive()) {
                RecoilDebugLogger.logAimSync("AIM_SYNC_SKIPPED_RECOIL");
                return;
            }
            if (!forceForShot && isWithinRecentTacZShotGrace()) {
                RecoilDebugLogger.logAimSync("AIM_SYNC_SKIPPED_SHOT_GRACE");
                return;
            }
            if (forceForShot) {
                sendAimRotationPacket(mc, "AIM_SYNC_GUN");
            } else if (shouldPassiveSyncAimRotation(mc)) {
                sendAimRotationPacket(mc, "AIM_SYNC_GUN_PASSIVE");
            }

        } catch (Exception ignored) {
            // LeaWinds / TacZ が無い場合は何もしない
            restoreLeaWindsRotateMode();
        }
    }

    private static boolean isWithinRecentTacZShotGrace() {
        TacZReflection.init();
        long ts = TacZReflection.getShootTimeStamp();
        return ts > 0L && System.currentTimeMillis() - ts <= AIM_SYNC_SHOT_GRACE_MS;
    }

    private static boolean shouldPassiveSyncAimRotation(Minecraft mc) {
        if (mc.player == null) return false;
        int tick = mc.player.tickCount;
        if (tick - lastPassiveAimSyncTick < 2) return false;
        lastPassiveAimSyncTick = tick;
        return true;
    }

    public static boolean isHoldingTacZGun(net.minecraft.client.player.LocalPlayer player) {
        if (player == null) return false;
        try {
            if (com.tacz.guns.api.item.IGun.mainHandHoldGun(player)) return true;
        } catch (Throwable ignored) {
        }
        try {
            return com.tacz.guns.api.item.IGun.getIGunOrNull(player.getMainHandItem()) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void applyLeaWindsGunRotateMode() {
        applyLeaWindsRotateMode("PARALLEL_WITH_CAMERA", true);
    }

    private static void applyLeaWindsRotateMode(String modeName, boolean rememberPrevious) {
        try {
            Class<?> thirdPersonClass = Class.forName("com.github.leawind.thirdperson.ThirdPerson");
            Object configObj = thirdPersonClass.getMethod("getConfig").invoke(null);
            java.lang.reflect.Field field = configObj.getClass().getField("normal_rotate_mode");
            Object current = field.get(configObj);
            Class<?> modeClass = Class.forName("com.github.leawind.thirdperson.config.AbstractConfig$PlayerRotateMode");
            Object targetMode = java.lang.Enum.valueOf((Class<? extends Enum>) modeClass.asSubclass(Enum.class), modeName);
            if (rememberPrevious && !gunRotateModeApplied) {
                savedRotateMode = current;
                gunRotateModeApplied = true;
            }
            if (current != targetMode) {
                field.set(configObj, targetMode);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void restoreLeaWindsRotateMode() {
        if (!gunRotateModeApplied) return;
        try {
            Class<?> thirdPersonClass = Class.forName("com.github.leawind.thirdperson.ThirdPerson");
            Object configObj = thirdPersonClass.getMethod("getConfig").invoke(null);
            java.lang.reflect.Field field = configObj.getClass().getField("normal_rotate_mode");
            if (savedRotateMode != null) {
                field.set(configObj, savedRotateMode);
            }
        } catch (Throwable ignored) {
        } finally {
            savedRotateMode = null;
            gunRotateModeApplied = false;
        }
    }

    private static void sendAimRotationPacket(Minecraft mc, String debugLabel) {
        if (mc.player == null || mc.gameRenderer == null) return;
        AimRotation rotation = resolveAimRotation(mc);
        if (rotation == null) return;

        if (mc.getConnection() != null) {
            RecoilDebugLogger.logAimSync(debugLabel + "_PACKET_BEFORE");
            mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(
                rotation.yaw(),
                rotation.pitch(),
                mc.player.onGround()
            ));
            RecoilDebugLogger.logAimSync(debugLabel + "_PACKET_SENT");
        }
    }

    private static AimRotation resolveAimRotation(Minecraft mc) {
        net.minecraft.world.phys.Vec3 target = resolveThirdPersonAimTargetForShot(mc, 96.0D);
        if (target == null) {
            net.minecraft.client.Camera camera = mc.gameRenderer.getMainCamera();
            return new AimRotation(camera.getYRot(), net.minecraft.util.Mth.clamp(camera.getXRot(), -90.0f, 90.0f));
        }

        net.minecraft.world.phys.Vec3 bulletStart = getTacZBulletStart(mc.player);
        double dx = target.x - bulletStart.x;
        double dy = target.y - bulletStart.y;
        double dz = target.z - bulletStart.z;
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        if (horizDist < 0.001D) {
            net.minecraft.client.Camera camera = mc.gameRenderer.getMainCamera();
            return new AimRotation(camera.getYRot(), net.minecraft.util.Mth.clamp(camera.getXRot(), -90.0f, 90.0f));
        }

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizDist));
        pitch = net.minecraft.util.Mth.clamp(pitch, -90.0f, 90.0f);
        return new AimRotation(yaw, pitch);
    }

    private static net.minecraft.world.phys.Vec3 getTacZBulletStart(net.minecraft.world.entity.LivingEntity shooter) {
        return new net.minecraft.world.phys.Vec3(shooter.getX(), shooter.getEyeY() - 0.1D, shooter.getZ());
    }

    private record AimRotation(float yaw, float pitch) {}
}
