package com.levanilla.rogue.world.generation;

import com.levanilla.rogue.world.generation.plan.DungeonCorridor;
import com.levanilla.rogue.world.generation.plan.DungeonPlan;
import com.levanilla.rogue.world.generation.plan.DungeonRoom;
import com.levanilla.rogue.world.generation.plan.RoomRole;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class DungeonPlanValidator {
    private static final int ROOM_RADIUS = 70;

    private DungeonPlanValidator() {}

    public static ValidationResult validate(DungeonPlan plan, boolean bossFloor) {
        if (plan == null) return ValidationResult.fail("plan is null");
        if (plan.rooms().isEmpty()) return ValidationResult.fail("no rooms");
        if (plan.room(plan.spawnRoomId()) == null) return ValidationResult.fail("missing spawn room");

        boolean hasBossArena = false;
        for (int i = 0; i < plan.rooms().size(); i++) {
            DungeonRoom room = plan.rooms().get(i);
            if (room.x() < -ROOM_RADIUS || room.z() < -ROOM_RADIUS
                || room.x() + room.width() > ROOM_RADIUS
                || room.z() + room.depth() > ROOM_RADIUS) {
                return ValidationResult.fail("room out of bounds: " + room.id());
            }
            if (room.role() == RoomRole.BOSS_ARENA) hasBossArena = true;
            for (int j = i + 1; j < plan.rooms().size(); j++) {
                DungeonRoom other = plan.rooms().get(j);
                if (overlaps(room, other, 2)) {
                    return ValidationResult.fail("room overlap: " + room.id() + "/" + other.id());
                }
            }
        }

        if (bossFloor && !hasBossArena) {
            return ValidationResult.fail("boss floor without arena");
        }
        if (!isConnected(plan)) {
            return ValidationResult.fail("room graph disconnected");
        }
        return ValidationResult.ok();
    }

    private static boolean overlaps(DungeonRoom a, DungeonRoom b, int margin) {
        return a.x() < b.x() + b.width() + margin
            && a.x() + a.width() + margin > b.x()
            && a.z() < b.z() + b.depth() + margin
            && a.z() + a.depth() + margin > b.z();
    }

    private static boolean isConnected(DungeonPlan plan) {
        Map<Integer, Set<Integer>> graph = new HashMap<>();
        for (DungeonRoom room : plan.rooms()) {
            graph.put(room.id(), new HashSet<>());
        }
        for (DungeonCorridor corridor : plan.corridors()) {
            if (!graph.containsKey(corridor.fromRoomId()) || !graph.containsKey(corridor.toRoomId())) continue;
            graph.get(corridor.fromRoomId()).add(corridor.toRoomId());
            graph.get(corridor.toRoomId()).add(corridor.fromRoomId());
        }
        Set<Integer> seen = new HashSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(plan.rooms().get(0).id());
        while (!queue.isEmpty()) {
            int id = queue.removeFirst();
            if (!seen.add(id)) continue;
            for (int next : graph.getOrDefault(id, Set.of())) {
                if (!seen.contains(next)) queue.addLast(next);
            }
        }
        return seen.size() == plan.rooms().size();
    }

    public record ValidationResult(boolean valid, String reason) {
        public static ValidationResult ok() {
            return new ValidationResult(true, "");
        }

        public static ValidationResult fail(String reason) {
            return new ValidationResult(false, reason == null ? "unknown" : reason);
        }
    }
}
