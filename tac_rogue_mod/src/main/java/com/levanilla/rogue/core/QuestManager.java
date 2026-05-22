package com.levanilla.rogue.core;

import com.levanilla.rogue.core.registry.ShopCatalog;
import com.levanilla.rogue.core.service.RogueItemFactory;
import com.levanilla.rogue.core.service.ShopPlacementService;
import com.levanilla.rogue.networking.PopupNotificationMessage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class QuestManager {

    public enum QuestType {
        KILL_COUNT("quest.tac_rogue.type.kill", 0xFFFF4444),
        FLOOR_CLEAR("quest.tac_rogue.type.floor_clear", 0xFF44FF44),
        HEADSHOT("quest.tac_rogue.type.headshot", 0xFFFFAA00),
        STEALTH_KILL("quest.tac_rogue.type.stealth_kill", 0xFF8888FF),
        BOSS_KILL("quest.tac_rogue.type.boss_kill", 0xFFFF00FF),
        GOLD_EARN("quest.tac_rogue.type.gold_earn", 0xFFFFD700),
        SURVIVE("quest.tac_rogue.type.survive", 0xFF00FFFF),
        NO_DAMAGE("quest.tac_rogue.type.no_damage", 0xFFFFFFFF),
        SPEEDRUN("quest.tac_rogue.type.speedrun", 0xFF00FF88),
        WEAPON_MASTERY("quest.tac_rogue.type.weapon_mastery", 0xFFFF8800),
        ELITE_HUNT("quest.tac_rogue.type.elite_hunt", 0xFFFF3355),
        CHEST_RECOVERY("quest.tac_rogue.type.chest_recovery", 0xFF66DDFF),
        RARITY_KILL("quest.tac_rogue.type.rarity_kill", 0xFFAA66FF),
        FAST_CHAIN("quest.tac_rogue.type.fast_chain", 0xFFFFCC44),
        LOW_HEALTH_CLEAR("quest.tac_rogue.type.low_health_clear", 0xFFFF6666);

        public final String langKey;
        public final int color;

        QuestType(String langKey, int color) {
            this.langKey = langKey;
            this.color = color;
        }
    }

    public enum QuestRole {
        STORY("quest.tac_rogue.role.story"),
        CONTRACT("quest.tac_rogue.role.contract"),
        BOUNTY("quest.tac_rogue.role.bounty"),
        SUPPLY("quest.tac_rogue.role.supply");

        public final String langKey;

        QuestRole(String langKey) {
            this.langKey = langKey;
        }
    }

    public static class Quest {
        public final String id;
        public final int chapter;
        public final QuestType type;
        public final QuestRole role;
        public final int targetAmount;
        public final int goldReward;
        public final int xpReward;
        public final String descriptionKey;
        public final boolean rareWeaponReward;

        public Quest(String id, int chapter, QuestType type, QuestRole role, int target, int gold, int xp) {
            this(id, chapter, type, role, target, gold, xp, false);
        }

        public Quest(String id, int chapter, QuestType type, QuestRole role, int target, int gold, int xp, boolean rareWeaponReward) {
            this.id = id;
            this.chapter = chapter;
            this.type = type;
            this.role = role;
            this.targetAmount = target;
            this.goldReward = gold;
            this.xpReward = xp;
            this.rareWeaponReward = rareWeaponReward;
            this.descriptionKey = "quest.tac_rogue." + id;
        }
    }

    public static class QuestProgress {
        public int currentChapter = 1;
        public final Map<String, Integer> questProgress = new LinkedHashMap<>();
        public final Set<String> completedQuests = new LinkedHashSet<>();
        public final List<String> candidateQuestIds = new ArrayList<>();
        public final List<String> selectedQuestIds = new ArrayList<>();
        public int selectionLockedChapter = 0;
        public int candidateChapter = 0;
        public int ngPlusLevel = 0;

        public CompoundTag saveToNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("CurrentChapter", currentChapter);
            tag.putInt("NgPlusLevel", ngPlusLevel);

            CompoundTag progressTag = new CompoundTag();
            for (Map.Entry<String, Integer> e : questProgress.entrySet()) {
                progressTag.putInt(e.getKey(), e.getValue());
            }
            tag.put("Progress", progressTag);

            ListTag completedTag = new ListTag();
            for (String id : completedQuests) {
                completedTag.add(StringTag.valueOf(id));
            }
            tag.put("Completed", completedTag);

            ListTag candidateTag = new ListTag();
            for (String id : candidateQuestIds) {
                candidateTag.add(StringTag.valueOf(id));
            }
            tag.put("CandidateQuestIds", candidateTag);

            ListTag selectedTag = new ListTag();
            for (String id : selectedQuestIds) {
                selectedTag.add(StringTag.valueOf(id));
            }
            tag.put("SelectedQuestIds", selectedTag);
            tag.putInt("SelectionLockedChapter", selectionLockedChapter);
            tag.putInt("CandidateChapter", candidateChapter);
            return tag;
        }

        public void loadFromNbt(CompoundTag tag) {
            currentChapter = tag.getInt("CurrentChapter");
            if (currentChapter < 1) currentChapter = 1;
            ngPlusLevel = tag.getInt("NgPlusLevel");

            questProgress.clear();
            if (tag.contains("Progress")) {
                CompoundTag progressTag = tag.getCompound("Progress");
                for (String key : progressTag.getAllKeys()) {
                    questProgress.put(key, progressTag.getInt(key));
                }
            }

            completedQuests.clear();
            if (tag.contains("Completed")) {
                ListTag completedTag = tag.getList("Completed", 8);
                for (int i = 0; i < completedTag.size(); i++) {
                    completedQuests.add(completedTag.getString(i));
                }
            }

            candidateQuestIds.clear();
            if (tag.contains("CandidateQuestIds")) {
                ListTag candidateTag = tag.getList("CandidateQuestIds", 8);
                for (int i = 0; i < candidateTag.size(); i++) {
                    candidateQuestIds.add(candidateTag.getString(i));
                }
            }

            selectedQuestIds.clear();
            if (tag.contains("SelectedQuestIds")) {
                ListTag selectedTag = tag.getList("SelectedQuestIds", 8);
                for (int i = 0; i < selectedTag.size(); i++) {
                    selectedQuestIds.add(selectedTag.getString(i));
                }
            }
            selectionLockedChapter = tag.getInt("SelectionLockedChapter");
            candidateChapter = tag.getInt("CandidateChapter");
        }
    }

    private static final Map<UUID, QuestProgress> playerProgress = new ConcurrentHashMap<>();
    private static final List<Quest> ALL_QUESTS = new ArrayList<>();
    public static final int MAX_CHAPTERS = 20;
    public static final int REQUIRED_SIDE_QUESTS = 3;
    public static final int CANDIDATE_SIDE_QUESTS = 5;
    public static final double RARE_QUEST_DIFFICULTY_MULTIPLIER = 1.35D;

    public static final String[][] CHAPTER_STORIES = {
        {"chapter.tac_rogue.1.title",  "chapter.tac_rogue.1.summary"},
        {"chapter.tac_rogue.2.title",  "chapter.tac_rogue.2.summary"},
        {"chapter.tac_rogue.3.title",  "chapter.tac_rogue.3.summary"},
        {"chapter.tac_rogue.4.title",  "chapter.tac_rogue.4.summary"},
        {"chapter.tac_rogue.5.title",  "chapter.tac_rogue.5.summary"},
        {"chapter.tac_rogue.6.title",  "chapter.tac_rogue.6.summary"},
        {"chapter.tac_rogue.7.title",  "chapter.tac_rogue.7.summary"},
        {"chapter.tac_rogue.8.title",  "chapter.tac_rogue.8.summary"},
        {"chapter.tac_rogue.9.title",  "chapter.tac_rogue.9.summary"},
        {"chapter.tac_rogue.10.title", "chapter.tac_rogue.10.summary"},
        {"chapter.tac_rogue.11.title", "chapter.tac_rogue.11.summary"},
        {"chapter.tac_rogue.12.title", "chapter.tac_rogue.12.summary"},
        {"chapter.tac_rogue.13.title", "chapter.tac_rogue.13.summary"},
        {"chapter.tac_rogue.14.title", "chapter.tac_rogue.14.summary"},
        {"chapter.tac_rogue.15.title", "chapter.tac_rogue.15.summary"},
        {"chapter.tac_rogue.16.title", "chapter.tac_rogue.16.summary"},
        {"chapter.tac_rogue.17.title", "chapter.tac_rogue.17.summary"},
        {"chapter.tac_rogue.18.title", "chapter.tac_rogue.18.summary"},
        {"chapter.tac_rogue.19.title", "chapter.tac_rogue.19.summary"},
        {"chapter.tac_rogue.20.title", "chapter.tac_rogue.20.summary"},
    };

    static {
        generateQuests();
    }

    public static String getChapterTitleKey(int chapter) {
        int idx = Math.max(0, Math.min(chapter - 1, CHAPTER_STORIES.length - 1));
        return CHAPTER_STORIES[idx][0];
    }

    public static String getChapterSummaryKey(int chapter) {
        int idx = Math.max(0, Math.min(chapter - 1, CHAPTER_STORIES.length - 1));
        return CHAPTER_STORIES[idx][1];
    }

    public static String getChapterBackgroundKey(int chapter) {
        int idx = Math.max(0, Math.min(chapter - 1, CHAPTER_STORIES.length - 1));
        return "chapter.tac_rogue." + (idx + 1) + ".background";
    }

    private static void generateQuests() {
        int questId = 0;
        Object[][] storyQuestDefs = {
            {QuestType.FLOOR_CLEAR, 3,  500},
            {QuestType.KILL_COUNT,  30, 600},
            {QuestType.HEADSHOT,    15, 700},
            {QuestType.STEALTH_KILL, 10, 800},
            {QuestType.BOSS_KILL,   2,  1000},
            {QuestType.NO_DAMAGE,   1,  1200},
            {QuestType.GOLD_EARN,   2000, 800},
            {QuestType.SPEEDRUN,    1,  1500},
            {QuestType.SURVIVE,     5,  1000},
            {QuestType.BOSS_KILL,   3,  2000},
            {QuestType.WEAPON_MASTERY, 50, 1200},
            {QuestType.HEADSHOT,    30, 1500},
            {QuestType.STEALTH_KILL, 20, 1800},
            {QuestType.KILL_COUNT,  100, 2000},
            {QuestType.BOSS_KILL,   5, 2500},
            {QuestType.NO_DAMAGE,   2, 3000},
            {QuestType.FLOOR_CLEAR, 15, 2500},
            {QuestType.SPEEDRUN,    3,  3500},
            {QuestType.KILL_COUNT, 150, 4000},
            {QuestType.BOSS_KILL,   10, 5000},
        };

        for (int ch = 1; ch <= MAX_CHAPTERS; ch++) {
            Object[] sd = storyQuestDefs[ch - 1];
            ALL_QUESTS.add(new Quest(
                "story_" + String.format("%02d", ch),
                ch, (QuestType) sd[0], QuestRole.STORY, (int) sd[1], (int) sd[2], ch * 20
            ));
            questId++;

            List<QuestPlan> plans = buildChapterPlans(ch);
            for (int q = 0; q < plans.size(); q++) {
                QuestPlan plan = plans.get(q);
                int baseDifficulty = ch * 5 + q * 2;
                int target = calculateTarget(ch, baseDifficulty, plan.type);
                if (plan.rareWeaponReward) {
                    target = scaleRareTarget(target, plan.type);
                }
                int goldReward = calculateReward(ch, q, plan.role, plan.rareWeaponReward);
                int xpReward = ch * 10 + q * 5;

                ALL_QUESTS.add(new Quest(
                    "q_" + String.format("%03d", questId++),
                    ch, plan.type, plan.role, target, goldReward, xpReward, plan.rareWeaponReward
                ));
            }
        }
    }

    private record QuestPlan(QuestRole role, QuestType type, boolean rareWeaponReward) {}

    private static List<QuestPlan> buildChapterPlans(int chapter) {
        RogueActManager.FloorPhase phase = RogueActManager.getPhase(chapter);
        List<QuestPlan> plans = new ArrayList<>();

        plans.add(new QuestPlan(QuestRole.CONTRACT, pickRareQuestType(phase, chapter), true));
        plans.add(new QuestPlan(QuestRole.BOUNTY, pickBountyType(phase, chapter), false));
        plans.add(new QuestPlan(QuestRole.SUPPLY, pickSupplyType(phase, chapter), false));

        QuestType[] rotation = switch (phase) {
            case SCOUT -> new QuestType[] {QuestType.FLOOR_CLEAR, QuestType.SPEEDRUN, QuestType.CHEST_RECOVERY, QuestType.HEADSHOT, QuestType.KILL_COUNT, QuestType.SURVIVE};
            case SUPPLY -> new QuestType[] {QuestType.GOLD_EARN, QuestType.CHEST_RECOVERY, QuestType.FLOOR_CLEAR, QuestType.NO_DAMAGE, QuestType.FAST_CHAIN, QuestType.WEAPON_MASTERY};
            case ELITE -> new QuestType[] {QuestType.ELITE_HUNT, QuestType.HEADSHOT, QuestType.WEAPON_MASTERY, QuestType.RARITY_KILL, QuestType.SURVIVE, QuestType.FLOOR_CLEAR};
            case DANGER -> new QuestType[] {QuestType.LOW_HEALTH_CLEAR, QuestType.SURVIVE, QuestType.STEALTH_KILL, QuestType.FAST_CHAIN, QuestType.SPEEDRUN, QuestType.KILL_COUNT};
            case BOSS -> new QuestType[] {QuestType.BOSS_KILL, QuestType.RARITY_KILL, QuestType.SURVIVE, QuestType.ELITE_HUNT, QuestType.HEADSHOT, QuestType.NO_DAMAGE};
        };

        for (int i = 0; plans.size() < 9; i++) {
            QuestRole role = switch (i % 3) {
                case 0 -> QuestRole.CONTRACT;
                case 1 -> QuestRole.BOUNTY;
                default -> QuestRole.SUPPLY;
            };
            plans.add(new QuestPlan(role, rotation[(chapter + i) % rotation.length], false));
        }
        return plans;
    }

    private static QuestType pickContractType(RogueActManager.FloorPhase phase, int chapter) {
        return switch (phase) {
            case SCOUT -> chapter % 2 == 0 ? QuestType.SPEEDRUN : QuestType.FLOOR_CLEAR;
            case SUPPLY -> chapter % 2 == 0 ? QuestType.CHEST_RECOVERY : QuestType.GOLD_EARN;
            case ELITE -> chapter % 2 == 0 ? QuestType.ELITE_HUNT : QuestType.WEAPON_MASTERY;
            case DANGER -> chapter % 2 == 0 ? QuestType.LOW_HEALTH_CLEAR : QuestType.SURVIVE;
            case BOSS -> QuestType.BOSS_KILL;
        };
    }

    private static QuestType pickBountyType(RogueActManager.FloorPhase phase, int chapter) {
        return switch (phase) {
            case SCOUT -> QuestType.HEADSHOT;
            case SUPPLY -> chapter % 2 == 0 ? QuestType.FAST_CHAIN : QuestType.HEADSHOT;
            case ELITE -> QuestType.ELITE_HUNT;
            case DANGER -> QuestType.STEALTH_KILL;
            case BOSS -> QuestType.BOSS_KILL;
        };
    }

    private static QuestType pickSupplyType(RogueActManager.FloorPhase phase, int chapter) {
        return switch (phase) {
            case SCOUT -> QuestType.CHEST_RECOVERY;
            case SUPPLY -> QuestType.GOLD_EARN;
            case ELITE -> QuestType.FLOOR_CLEAR;
            case DANGER -> QuestType.NO_DAMAGE;
            case BOSS -> QuestType.SURVIVE;
        };
    }

    private static QuestType pickRareQuestType(RogueActManager.FloorPhase phase, int chapter) {
        return switch (phase) {
            case SCOUT -> chapter <= 2 ? QuestType.WEAPON_MASTERY : QuestType.RARITY_KILL;
            case SUPPLY -> chapter < 7 ? QuestType.SPEEDRUN : QuestType.NO_DAMAGE;
            case ELITE -> chapter % 2 == 0 ? QuestType.ELITE_HUNT : QuestType.RARITY_KILL;
            case DANGER -> chapter % 2 == 0 ? QuestType.LOW_HEALTH_CLEAR : QuestType.SPEEDRUN;
            case BOSS -> QuestType.BOSS_KILL;
        };
    }

    private static int calculateTarget(int chapter, int baseDifficulty, QuestType type) {
        return switch (type) {
            case KILL_COUNT -> 10 + baseDifficulty * 2;
            case FLOOR_CLEAR -> 1 + chapter / 2;
            case HEADSHOT -> 5 + baseDifficulty;
            case STEALTH_KILL -> 3 + baseDifficulty / 2;
            case BOSS_KILL -> 1 + chapter / 5;
            case GOLD_EARN -> 200 + baseDifficulty * 50;
            case SURVIVE -> 1 + chapter / 3;
            case NO_DAMAGE -> chapter >= 16 ? 2 : 1;
            case SPEEDRUN -> chapter >= 18 ? 3 : 1;
            case WEAPON_MASTERY -> 20 + baseDifficulty;
            case ELITE_HUNT -> 2 + chapter / 3;
            case CHEST_RECOVERY -> Math.min(3, 1 + chapter / 6);
            case RARITY_KILL -> 8 + baseDifficulty / 2;
            case FAST_CHAIN -> 4 + chapter / 2;
            case LOW_HEALTH_CLEAR -> chapter >= 14 ? 2 : 1;
        };
    }

    private static int calculateReward(int chapter, int index, QuestRole role, boolean rareWeaponReward) {
        int base = switch (role) {
            case STORY -> 500 + chapter * 100;
            case CONTRACT -> 160 + chapter * 65;
            case BOUNTY -> 220 + chapter * 75;
            case SUPPLY -> 120 + chapter * 55;
        };
        if (rareWeaponReward) base += 250 + chapter * 30;
        return base + index * 20;
    }

    private static int scaleRareTarget(int target, QuestType type) {
        int scaled = Math.max(target + 1, (int) Math.ceil(target * RARE_QUEST_DIFFICULTY_MULTIPLIER));
        return switch (type) {
            case NO_DAMAGE, SPEEDRUN, LOW_HEALTH_CLEAR -> Math.max(target, scaled);
            case BOSS_KILL -> Math.max(1, scaled);
            default -> scaled;
        };
    }

    public static QuestProgress getProgress(ServerPlayer player) {
        QuestProgress cached = playerProgress.get(player.getUUID());
        if (cached != null) return cached;

        QuestSavedData savedData = QuestSavedData.get(player.serverLevel());
        QuestProgress progress = savedData.getProgress(player.getUUID());
        playerProgress.put(player.getUUID(), progress);
        return progress;
    }

    public static void migrateLegacyProgress(ServerPlayer player, CompoundTag legacyTag) {
        if (legacyTag == null || legacyTag.isEmpty()) return;

        QuestProgress saved = getProgress(player);
        QuestProgress legacy = new QuestProgress();
        legacy.loadFromNbt(legacyTag);

        saved.currentChapter = Math.max(saved.currentChapter, legacy.currentChapter);
        saved.ngPlusLevel = Math.max(saved.ngPlusLevel, legacy.ngPlusLevel);
        for (Map.Entry<String, Integer> entry : legacy.questProgress.entrySet()) {
            saved.questProgress.merge(entry.getKey(), entry.getValue(), Math::max);
        }
        saved.completedQuests.addAll(legacy.completedQuests);
        markDirty(player);
    }

    public static List<Quest> getChapterQuests(int chapter) {
        return ALL_QUESTS.stream()
            .filter(q -> q.chapter == chapter)
            .toList();
    }

    public static List<Quest> getVisibleChapterQuests(ServerPlayer player) {
        QuestProgress progress = getProgress(player);
        ensureChapterPlan(player, progress);
        List<Quest> visible = new ArrayList<>();
        for (Quest quest : getChapterQuests(progress.currentChapter)) {
            if (quest.role == QuestRole.STORY || progress.candidateQuestIds.contains(quest.id)) {
                visible.add(quest);
            }
        }
        return visible;
    }

    public static List<Quest> getActiveChapterQuests(ServerPlayer player) {
        QuestProgress progress = getProgress(player);
        ensureChapterPlan(player, progress);
        return getActiveChapterQuests(progress);
    }

    private static List<Quest> getActiveChapterQuests(QuestProgress progress) {
        List<Quest> active = new ArrayList<>();
        for (Quest quest : getChapterQuests(progress.currentChapter)) {
            if (quest.role == QuestRole.STORY || progress.selectedQuestIds.contains(quest.id)) {
                active.add(quest);
            }
        }
        return active;
    }

    public static void ensureChapterPlan(ServerPlayer player, QuestProgress progress) {
        if (progress.candidateChapter == progress.currentChapter
            && progress.candidateQuestIds.size() == CANDIDATE_SIDE_QUESTS
            && candidatesAreValid(progress)) {
            return;
        }

        progress.candidateQuestIds.clear();
        progress.selectedQuestIds.clear();
        progress.selectionLockedChapter = 0;
        progress.candidateChapter = progress.currentChapter;

        List<Quest> sideQuests = getChapterQuests(progress.currentChapter).stream()
            .filter(q -> q.role != QuestRole.STORY)
            .toList();
        List<Quest> pool = new ArrayList<>(sideQuests);
        List<Quest> selectedCandidates = new ArrayList<>();

        Quest rare = removeRandom(pool.stream().filter(q -> q.rareWeaponReward).toList(), pool, player);
        if (rare != null) {
            selectedCandidates.add(rare);
        }

        for (QuestRole role : new QuestRole[] {QuestRole.CONTRACT, QuestRole.BOUNTY, QuestRole.SUPPLY}) {
            if (selectedCandidates.size() >= CANDIDATE_SIDE_QUESTS) break;
            if (selectedCandidates.stream().anyMatch(q -> q.role == role)) continue;
            Quest picked = removeRandom(pool.stream().filter(q -> q.role == role && !q.rareWeaponReward).toList(), pool, player);
            if (picked != null) selectedCandidates.add(picked);
        }

        while (selectedCandidates.size() < CANDIDATE_SIDE_QUESTS && !pool.isEmpty()) {
            Quest picked = pool.remove(player.getRandom().nextInt(pool.size()));
            if (picked.rareWeaponReward && selectedCandidates.stream().anyMatch(q -> q.rareWeaponReward)) continue;
            selectedCandidates.add(picked);
        }

        for (Quest quest : selectedCandidates) {
            progress.candidateQuestIds.add(quest.id);
        }
        markDirty(player);
    }

    private static Quest removeRandom(List<Quest> candidates, List<Quest> pool, ServerPlayer player) {
        if (candidates.isEmpty()) return null;
        Quest picked = candidates.get(player.getRandom().nextInt(candidates.size()));
        pool.removeIf(q -> q.id.equals(picked.id));
        return picked;
    }

    private static boolean candidatesAreValid(QuestProgress progress) {
        Set<String> valid = new LinkedHashSet<>();
        for (Quest quest : getChapterQuests(progress.currentChapter)) {
            if (quest.role != QuestRole.STORY) valid.add(quest.id);
        }
        if (!valid.containsAll(progress.candidateQuestIds)) return false;
        if (progress.selectionLockedChapter == progress.currentChapter) {
            return progress.selectedQuestIds.size() == REQUIRED_SIDE_QUESTS
                && progress.candidateQuestIds.containsAll(progress.selectedQuestIds);
        }
        return progress.selectedQuestIds.isEmpty();
    }

    public static boolean selectChapterQuests(ServerPlayer player, List<String> questIds) {
        QuestProgress progress = getProgress(player);
        ensureChapterPlan(player, progress);
        if (progress.selectionLockedChapter == progress.currentChapter) return false;

        LinkedHashSet<String> unique = new LinkedHashSet<>(questIds);
        if (unique.size() != REQUIRED_SIDE_QUESTS) return false;
        if (!progress.candidateQuestIds.containsAll(unique)) return false;

        progress.selectedQuestIds.clear();
        progress.selectedQuestIds.addAll(unique);
        progress.selectionLockedChapter = progress.currentChapter;
        if (canAdvanceChapter(getActiveChapterQuests(progress), progress)) {
            advanceChapter(player, progress);
        }
        markDirty(player);
        RunManager.syncPlayer(player);
        return true;
    }

    public static void advanceQuest(ServerPlayer player, QuestType type, int amount) {
        QuestProgress progress = getProgress(player);
        ensureChapterPlan(player, progress);
        List<Quest> chapterQuests = getActiveChapterQuests(progress);

        for (Quest quest : chapterQuests) {
            if (quest.type != type) continue;
            if (progress.completedQuests.contains(quest.id)) continue;

            int current = progress.questProgress.getOrDefault(quest.id, 0) + amount;
            progress.questProgress.put(quest.id, current);

            if (current >= quest.targetAmount) {
                progress.completedQuests.add(quest.id);
                CurrencyManager.addGoldNoQuest(player, quest.goldReward);
                PopupNotificationMessage.send(
                    player,
                    PopupNotificationMessage.PopupType.QUEST,
                    Component.translatable("popup.tac_rogue.quest_complete.title"),
                    Component.translatable("message.tac_rogue.quest_complete", quest.id, quest.goldReward)
                );
                if (quest.rareWeaponReward) {
                    grantRareWeaponReward(player, quest);
                }
                RunManager.syncPlayer(player);

                if (canAdvanceChapter(chapterQuests, progress)) {
                    advanceChapter(player, progress);
                    markDirty(player);
                    return;
                }
            }
            markDirty(player);
        }
    }

    private static boolean canAdvanceChapter(List<Quest> chapterQuests, QuestProgress progress) {
        if (progress.selectionLockedChapter != progress.currentChapter
            || progress.selectedQuestIds.size() < REQUIRED_SIDE_QUESTS) {
            return false;
        }
        boolean storyDone = chapterQuests.stream()
            .filter(q -> q.role == QuestRole.STORY)
            .allMatch(q -> progress.completedQuests.contains(q.id));
        boolean sideDone = progress.selectedQuestIds.stream()
            .allMatch(progress.completedQuests::contains);
        return storyDone && sideDone;
    }

    private static void grantRareWeaponReward(ServerPlayer player, Quest quest) {
        ShopCatalog.ShopItem reward = null;
        ItemStack stack = ItemStack.EMPTY;
        for (int attempt = 0; attempt < 8; attempt++) {
            reward = selectRareWeaponReward(player, quest.chapter);
            if (reward == null) break;
            stack = RogueItemFactory.createRewardWeaponStack(
                player, reward.id, Math.max(1, quest.chapter * 2), player.getRandom());
            if (!stack.isEmpty()) break;
        }

        if (reward == null || stack.isEmpty()) {
            PopupNotificationMessage.send(
                player,
                PopupNotificationMessage.PopupType.WARNING,
                Component.translatable("popup.tac_rogue.reward_failed.title"),
                Component.translatable("message.tac_rogue.quest_weapon_failed")
            );
            return;
        }

        ShopPlacementService.placeRewardItem(player, stack, reward.id);
        PopupNotificationMessage.send(
            player,
            PopupNotificationMessage.PopupType.REWARD,
            Component.translatable("popup.tac_rogue.quest_weapon.title"),
            Component.translatable("message.tac_rogue.quest_weapon_reward", reward.displayName),
            150
        );
    }

    private static ShopCatalog.ShopItem selectRareWeaponReward(ServerPlayer player, int chapter) {
        List<ShopCatalog.Category> preferred = preferredRewardCategories(chapter);
        List<ShopCatalog.ShopItem> candidates = new ArrayList<>();
        int maxPrice = 900 + chapter * 220;
        candidates.addAll(com.levanilla.rogue.core.service.RewardSelectionService.weaponCandidates(
            chapter, maxPrice, preferred));
        if (candidates.isEmpty()) return null;

        return candidates.get(player.getRandom().nextInt(candidates.size()));
    }

    private static List<ShopCatalog.Category> preferredRewardCategories(int chapter) {
        RogueActManager.FloorPhase phase = RogueActManager.getPhase(chapter);
        return switch (phase) {
            case SCOUT -> chapter < 6
                ? List.of(ShopCatalog.Category.PISTOL, ShopCatalog.Category.SMG)
                : List.of(ShopCatalog.Category.SMG, ShopCatalog.Category.RIFLE, ShopCatalog.Category.SNIPER);
            case SUPPLY -> List.of(ShopCatalog.Category.PISTOL, ShopCatalog.Category.SMG, ShopCatalog.Category.RIFLE);
            case ELITE -> List.of(ShopCatalog.Category.RIFLE, ShopCatalog.Category.SHOTGUN, ShopCatalog.Category.LMG);
            case DANGER -> List.of(ShopCatalog.Category.SHOTGUN, ShopCatalog.Category.RIFLE, ShopCatalog.Category.SNIPER);
            case BOSS -> List.of(ShopCatalog.Category.RIFLE, ShopCatalog.Category.SNIPER, ShopCatalog.Category.LMG, ShopCatalog.Category.EXPLOSIVE);
        };
    }

    private static void advanceChapter(ServerPlayer player, QuestProgress progress) {
        if (progress.currentChapter < MAX_CHAPTERS) {
            progress.currentChapter++;
            resetChapterSelection(progress);
            PopupNotificationMessage.send(
                player,
                PopupNotificationMessage.PopupType.SYSTEM,
                Component.translatable("popup.tac_rogue.chapter.title"),
                Component.translatable("message.tac_rogue.chapter_unlock", progress.currentChapter),
                150
            );
        } else {
            progress.ngPlusLevel++;
            progress.currentChapter = 1;
            progress.questProgress.clear();
            progress.completedQuests.clear();
            resetChapterSelection(progress);
            PopupNotificationMessage.send(
                player,
                PopupNotificationMessage.PopupType.SYSTEM,
                Component.translatable("popup.tac_rogue.ng_plus.title"),
                Component.translatable("message.tac_rogue.ng_plus", progress.ngPlusLevel),
                170
            );
        }
    }

    private static void resetChapterSelection(QuestProgress progress) {
        progress.candidateQuestIds.clear();
        progress.selectedQuestIds.clear();
        progress.selectionLockedChapter = 0;
        progress.candidateChapter = 0;
    }

    public static void resetPlayer(UUID uuid) {
        playerProgress.remove(uuid);
    }

    public static int getTotalQuestCount() {
        return ALL_QUESTS.size();
    }

    private static void markDirty(ServerPlayer player) {
        try {
            QuestSavedData.get(player.serverLevel()).setDirty();
        } catch (Exception ignored) {
        }
    }

    public static void clearMemory() {
        playerProgress.clear();
    }
}
