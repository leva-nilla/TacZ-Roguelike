package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.CommonEventHandler;
import com.levanilla.rogue.core.PlayerRunData;
import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.networking.PopupNotificationMessage;
import com.levanilla.rogue.world.ThemeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

final class FloorEntryModeHandler {
    private FloorEntryModeHandler() {}

    static void enterFloor(ServerPlayer player, int floor, FloorInstanceManager.EntryMode mode) {
        if (mode == FloorInstanceManager.EntryMode.SOLO) {
            createSoloInstance(player, floor);
        } else {
            joinOrCreatePublicInstance(player, floor);
        }
    }

    private static void createSoloInstance(ServerPlayer player, int floor) {
        FloorInstanceManager.leaveInstance(player, false);
        long tick = player.server.getTickCount();
        String id = "solo-" + player.getUUID() + "-" + tick + "-" + UUID.randomUUID();
        BlockPos origin = RunManager.getPrivateDungeonOrigin(player);
        boolean questEligible = FloorInstanceManager.isQuestEligibleFloor(floor,
            RunManager.getData(player).getMaxReachedFloor());
        FloorInstanceManager.FloorInstance instance = new FloorInstanceManager.FloorInstance(id, floor,
            FloorInstanceManager.EntryMode.SOLO, player.getUUID(), origin, tick, System.nanoTime(),
            System.nanoTime() ^ tick, questEligible);
        instance.participants.add(player.getUUID());
        instance.initialParticipantCount = 1;
        FloorInstanceManager.INSTANCES.put(id, instance);
        FloorInstanceManager.PLAYER_INSTANCES.put(player.getUUID(), id);
        FloorInstanceManager.syncWaitClear(player);
        FloorInstanceManager.activateInstance(player.server, instance);
    }

    private static void joinOrCreatePublicInstance(ServerPlayer player, int floor) {
        FloorInstanceManager.leaveInstance(player, false);
        FloorInstanceManager.FloorInstance existing = getPublicInstance(floor);
        if (existing != null && existing.state == FloorInstanceManager.State.ACTIVE) {
            if (!canJoinActiveInstance(player, existing)) return;
            addParticipant(player, existing, true);
            FloorInstanceManager.teleportParticipant(player, existing);
            FloorInstanceManager.applyMidRunJoinScaling(player.server, existing);
            FloorInstanceManager.syncBossBar(player.server, existing, true);
            PopupNotificationMessage.send(
                player,
                PopupNotificationMessage.PopupType.SYSTEM,
                Component.literal("CO-OP JOIN"),
                Component.literal("Joined active Floor " + floor + " instance."),
                100);
            return;
        }
        if (existing != null && existing.state == FloorInstanceManager.State.PREPARING) {
            addParticipant(player, existing, false);
            FloorInstanceManager.syncWaitUpdate(player, existing, 1);
            PopupNotificationMessage.send(
                player,
                PopupNotificationMessage.PopupType.SYSTEM,
                Component.literal("PUBLIC CO-OP"),
                Component.literal("Floor " + floor + " is deploying. Joining launch group."),
                100);
            return;
        }

        if (existing == null || existing.state == FloorInstanceManager.State.CLEARED) {
            long tick = player.server.getTickCount();
            String id = "public-floor-" + floor + "-" + tick + "-" + UUID.randomUUID();
            boolean questEligible = FloorInstanceManager.isQuestEligibleFloor(floor,
                RunManager.getData(player).getMaxReachedFloor());
            existing = new FloorInstanceManager.FloorInstance(id, floor, FloorInstanceManager.EntryMode.PUBLIC,
                player.getUUID(), FloorInstanceManager.publicOriginForFloor(floor),
                tick, System.nanoTime(), System.nanoTime() ^ ((long) floor << 32), questEligible);
            FloorInstanceManager.INSTANCES.put(id, existing);
            FloorInstanceManager.PUBLIC_INSTANCES_BY_FLOOR.put(floor, id);
        }

        addParticipant(player, existing, false);
        int secondsLeft = FloorInstanceManager.waitingSecondsLeft(player.server, existing);
        FloorInstanceManager.syncWaitUpdate(player, existing, secondsLeft);
        PopupNotificationMessage.send(
            player,
            PopupNotificationMessage.PopupType.SYSTEM,
            Component.literal("PUBLIC CO-OP"),
            Component.literal("Floor " + floor + " starts in " + secondsLeft + "s."),
            120);
    }

    private static FloorInstanceManager.FloorInstance getPublicInstance(int floor) {
        String id = FloorInstanceManager.PUBLIC_INSTANCES_BY_FLOOR.get(floor);
        if (id == null) return null;
        FloorInstanceManager.FloorInstance instance = FloorInstanceManager.INSTANCES.get(id);
        if (instance == null || instance.mode != FloorInstanceManager.EntryMode.PUBLIC) {
            FloorInstanceManager.PUBLIC_INSTANCES_BY_FLOOR.remove(floor);
            return null;
        }
        return instance;
    }

    private static boolean canJoinActiveInstance(ServerPlayer player, FloorInstanceManager.FloorInstance instance) {
        if (ThemeManager.isBossFloor(instance.floor)) {
            LivingEntity boss = FloorInstanceManager.findBoss(player.server.getLevel(CommonEventHandler.ROGUE_DIM), instance);
            if (boss != null && boss.getHealth() <= boss.getMaxHealth() * FloorInstanceManager.BOSS_JOIN_HP_RATIO) {
                player.sendSystemMessage(Component.literal("§c[LR-TAC] Boss engagement is too far along to join."));
                return false;
            }
        }
        return true;
    }

    static void addParticipant(ServerPlayer player, FloorInstanceManager.FloorInstance instance, boolean active) {
        instance.participants.add(player.getUUID());
        FloorInstanceManager.PLAYER_INSTANCES.put(player.getUUID(), instance.id);
        RunManager.restorePerkTags(player);
        PlayerRunData data = RunManager.getData(player);
        data.setCurrentFloor(instance.floor);
        data.setDungeonOrigin(instance.origin);
        data.setFloorCleared(false);
        data.setRunActive(active);
        if (data.getMaxReachedFloor() < 1) data.setMaxReachedFloor(1);
        data.setRunSeed(instance.runSeed);
        data.setFloorSeedSalt(instance.floorSeedSalt);
        if (active && player.server != null) {
            data.setFloorStartTick(player.server.getTickCount());
            LowHealthChallengeService.activateForFloor(player);
        }
        RunManager.refreshThemeName(data);
        RunManager.syncPlayer(player);
    }
}
