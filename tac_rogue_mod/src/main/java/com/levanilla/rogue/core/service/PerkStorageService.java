package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.PerkDefinition;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Owns Tac Rogue perk storage. Normal perk ownership must not live in scoreboard tags. */
public final class PerkStorageService {
    public static final String PERK_TAGS_KEY = "TacRoguePerkTags";
    private static final String MIGRATED_KEY = "TacRoguePerksMigratedFromTags";
    private static final String STORAGE_VERSION_KEY = "TacRoguePerkStorageVersion";
    private static final ConcurrentHashMap<UUID, Set<String>> MEMORY = new ConcurrentHashMap<>();

    private PerkStorageService() {}

    public static List<String> getPerkTags(ServerPlayer player) {
        if (player == null) return List.of();
        migrateLegacyScoreboardTags(player);
        LinkedHashSet<String> tags = readStoredSet(player);
        Set<String> remembered = MEMORY.get(player.getUUID());
        if (remembered != null && !remembered.isEmpty() && tags.addAll(remembered)) {
            writeSet(player, tags);
        }
        return List.copyOf(tags);
    }

    public static Set<String> getPerkTagSet(ServerPlayer player) {
        return new LinkedHashSet<>(getPerkTags(player));
    }

    public static int getPerkCount(ServerPlayer player) {
        return getPerkTags(player).size();
    }

    public static int getStorageVersion(ServerPlayer player) {
        return player == null ? 0 : player.getPersistentData().getInt(STORAGE_VERSION_KEY);
    }

    public static void addPerk(ServerPlayer player, String storedTag) {
        if (player == null || !isPerkTag(storedTag)) return;
        LinkedHashSet<String> tags = new LinkedHashSet<>(getPerkTags(player));
        if (tags.add(storedTag)) {
            writeSet(player, tags);
        }
    }

    public static boolean removePerk(ServerPlayer player, String storedTag) {
        if (player == null || !isPerkTag(storedTag)) return false;
        LinkedHashSet<String> tags = new LinkedHashSet<>(getPerkTags(player));
        boolean removed = tags.remove(storedTag);
        if (removed) {
            writeSet(player, tags);
        }
        return removed;
    }

    public static int removeMatching(ServerPlayer player, Predicate<String> predicate) {
        if (player == null || predicate == null) return 0;
        LinkedHashSet<String> tags = new LinkedHashSet<>(getPerkTags(player));
        int before = tags.size();
        tags.removeIf(predicate);
        int removed = before - tags.size();
        if (removed > 0) {
            writeSet(player, tags);
        }
        return removed;
    }

    public static void clearPerks(ServerPlayer player) {
        if (player == null) return;
        writeSet(player, Set.of());
        removeLegacyScoreboardTags(player);
    }

    public static void setPerks(ServerPlayer player, Collection<String> perkTags) {
        if (player == null) return;
        LinkedHashSet<String> clean = new LinkedHashSet<>();
        if (perkTags != null) {
            for (String tag : perkTags) {
                if (isPerkTag(tag)) clean.add(tag);
            }
        }
        writeSet(player, clean);
    }

    public static void migrateLegacyScoreboardTags(ServerPlayer player) {
        if (player == null) return;
        LinkedHashSet<String> union = readStoredSet(player);
        Set<String> remembered = MEMORY.get(player.getUUID());
        if (remembered != null) union.addAll(remembered);

        boolean foundLegacy = false;
        for (String tag : player.getTags()) {
            if (isPerkTag(tag)) {
                union.add(tag);
                foundLegacy = true;
            }
        }

        if (foundLegacy || !player.getPersistentData().getBoolean(MIGRATED_KEY)) {
            writeSet(player, union);
            player.getPersistentData().putBoolean(MIGRATED_KEY, true);
        }
        if (foundLegacy) {
            removeLegacyScoreboardTags(player);
        }
    }

    public static float sumEffect(ServerPlayer player, String perkPrefix) {
        if (player == null || perkPrefix == null) return 0.0f;
        float total = 0.0f;
        PerkDefinition.Category category = categoryFromPrefix(perkPrefix);
        for (String tag : getPerkTags(player)) {
            if (tag.startsWith(perkPrefix)) {
                PerkDefinition perk = PerkDefinition.fromTag(tag);
                total += perk.calculateEffect();
                if (category == null) category = perk.category;
            }
        }
        return category == null ? total : PerkDefinition.softcapTotalEffect(category, total);
    }

    public static float sumCategoryEffect(LivingEntity entity, PerkDefinition.Category category) {
        if (entity == null || category == null) return 0.0f;
        if (entity instanceof ServerPlayer player) {
            return sumEffect(player, "perk:" + category.name());
        }
        if (entity.level().isClientSide) {
            return PerkDefinition.sumClientCategoryEffect(category);
        }
        float total = 0.0f;
        String prefix = "perk:" + category.name();
        for (String tag : entity.getTags()) {
            if (tag.startsWith(prefix)) {
                total += PerkDefinition.fromTag(tag).calculateEffect();
            }
        }
        return PerkDefinition.softcapTotalEffect(category, total);
    }

    public static void clearMemory(UUID uuid) {
        if (uuid != null) MEMORY.remove(uuid);
    }

    public static void clearMemory() {
        MEMORY.clear();
    }

    private static LinkedHashSet<String> readStoredSet(ServerPlayer player) {
        LinkedHashSet<String> stored = new LinkedHashSet<>();
        if (player == null) return stored;
        if (!player.getPersistentData().contains(PERK_TAGS_KEY)) return stored;
        ListTag tags = player.getPersistentData().getList(PERK_TAGS_KEY, Tag.TAG_STRING);
        for (int i = 0; i < tags.size(); i++) {
            String tag = tags.getString(i);
            if (isPerkTag(tag)) stored.add(tag);
        }
        return stored;
    }

    private static void writeSet(ServerPlayer player, Collection<String> perkTags) {
        ListTag tags = new ListTag();
        LinkedHashSet<String> clean = new LinkedHashSet<>();
        for (String tag : perkTags) {
            if (isPerkTag(tag) && clean.add(tag)) {
                tags.add(StringTag.valueOf(tag));
            }
        }
        player.getPersistentData().put(PERK_TAGS_KEY, tags);
        player.getPersistentData().putBoolean(MIGRATED_KEY, true);
        player.getPersistentData().putInt(STORAGE_VERSION_KEY,
            player.getPersistentData().getInt(STORAGE_VERSION_KEY) + 1);
        MEMORY.put(player.getUUID(), Collections.unmodifiableSet(clean));
        PlayerPerkTickService.invalidateSnapshot(player);
    }

    private static void removeLegacyScoreboardTags(ServerPlayer player) {
        List<String> legacy = new ArrayList<>();
        for (String tag : player.getTags()) {
            if (isPerkTag(tag)) legacy.add(tag);
        }
        for (String tag : legacy) {
            player.removeTag(tag);
        }
    }

    private static boolean isPerkTag(String tag) {
        return tag != null && tag.startsWith("perk:");
    }

    private static PerkDefinition.Category categoryFromPrefix(String perkPrefix) {
        if (perkPrefix == null || !perkPrefix.startsWith("perk:")) return null;
        String rest = perkPrefix.substring("perk:".length());
        int colon = rest.indexOf(':');
        String categoryName = colon >= 0 ? rest.substring(0, colon) : rest;
        try {
            return PerkDefinition.Category.valueOf(categoryName);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
