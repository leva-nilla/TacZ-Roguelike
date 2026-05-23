package com.levanilla.rogue.world.generation.plan;

import com.levanilla.rogue.world.generation.FloorGenerationContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record DungeonPlan(
    FloorGenerationContext context,
    MacroArchetype archetype,
    long layoutHash,
    int spawnRoomId,
    List<DungeonRoom> rooms,
    List<DungeonCorridor> corridors
) {
    public DungeonPlan {
        rooms = Collections.unmodifiableList(new ArrayList<>(rooms));
        corridors = Collections.unmodifiableList(new ArrayList<>(corridors));
    }

    public List<int[]> legacyRooms() {
        List<int[]> result = new ArrayList<>(rooms.size());
        for (DungeonRoom room : rooms) {
            result.add(room.toLegacyArray());
        }
        return result;
    }

    public DungeonRoom room(int id) {
        for (DungeonRoom room : rooms) {
            if (room.id() == id) return room;
        }
        return null;
    }
}
