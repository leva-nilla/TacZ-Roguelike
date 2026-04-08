package com.levanilla.rogue.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * クエストシステム — チャプター制ローグライク進行
 *
 * 20 チャプター × 10 クエスト/チャプター = 200 クエスト
 * チャプター内のクエストは自由順クリア可能。
 * 全チャプターをクリアすると周回 (NG+) に突入。
 *
 * ストーリー: マイクラのTacZ導入世界に突如現れた「亀裂」を調査する。
 */
public class QuestManager {

    /** クエストの種類 */
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
        WEAPON_MASTERY("quest.tac_rogue.type.weapon_mastery", 0xFFFF8800);

        public final String langKey;
        public final int color;
        QuestType(String langKey, int color) { this.langKey = langKey; this.color = color; }
    }

    /** クエスト定義 */
    public static class Quest {
        public final String id;
        public final int chapter;
        public final QuestType type;
        public final int targetAmount;
        public final int goldReward;
        public final int xpReward;
        public final String descriptionKey;

        public Quest(String id, int chapter, QuestType type, int target, int gold, int xp) {
            this.id = id;
            this.chapter = chapter;
            this.type = type;
            this.targetAmount = target;
            this.goldReward = gold;
            this.xpReward = xp;
            this.descriptionKey = "quest.tac_rogue." + id;
        }
    }

    /** プレイヤーのクエスト進行状況 */
    public static class QuestProgress {
        public int currentChapter = 1;
        public final Map<String, Integer> questProgress = new LinkedHashMap<>(); // questId → current count
        public final Set<String> completedQuests = new LinkedHashSet<>();
        public int ngPlusLevel = 0; // 周回カウント
    }

    // ===== データストア =====
    private static final Map<UUID, QuestProgress> playerProgress = new ConcurrentHashMap<>();
    private static final List<Quest> ALL_QUESTS = new ArrayList<>();
    public static final int MAX_CHAPTERS = 20;

    // ===== チャプターストーリー =====

    static {
        generateQuests();
    }

    /** チャプターごとのストーリータイトルと概要 (langキー) */
    public static final String[][] CHAPTER_STORIES = {
        /* Ch 1 */ {"chapter.tac_rogue.1.title",  "chapter.tac_rogue.1.summary"},
        /* Ch 2 */ {"chapter.tac_rogue.2.title",  "chapter.tac_rogue.2.summary"},
        /* Ch 3 */ {"chapter.tac_rogue.3.title",  "chapter.tac_rogue.3.summary"},
        /* Ch 4 */ {"chapter.tac_rogue.4.title",  "chapter.tac_rogue.4.summary"},
        /* Ch 5 */ {"chapter.tac_rogue.5.title",  "chapter.tac_rogue.5.summary"},
        /* Ch 6 */ {"chapter.tac_rogue.6.title",  "chapter.tac_rogue.6.summary"},
        /* Ch 7 */ {"chapter.tac_rogue.7.title",  "chapter.tac_rogue.7.summary"},
        /* Ch 8 */ {"chapter.tac_rogue.8.title",  "chapter.tac_rogue.8.summary"},
        /* Ch 9 */ {"chapter.tac_rogue.9.title",  "chapter.tac_rogue.9.summary"},
        /* Ch10 */ {"chapter.tac_rogue.10.title", "chapter.tac_rogue.10.summary"},
        /* Ch11 */ {"chapter.tac_rogue.11.title", "chapter.tac_rogue.11.summary"},
        /* Ch12 */ {"chapter.tac_rogue.12.title", "chapter.tac_rogue.12.summary"},
        /* Ch13 */ {"chapter.tac_rogue.13.title", "chapter.tac_rogue.13.summary"},
        /* Ch14 */ {"chapter.tac_rogue.14.title", "chapter.tac_rogue.14.summary"},
        /* Ch15 */ {"chapter.tac_rogue.15.title", "chapter.tac_rogue.15.summary"},
        /* Ch16 */ {"chapter.tac_rogue.16.title", "chapter.tac_rogue.16.summary"},
        /* Ch17 */ {"chapter.tac_rogue.17.title", "chapter.tac_rogue.17.summary"},
        /* Ch18 */ {"chapter.tac_rogue.18.title", "chapter.tac_rogue.18.summary"},
        /* Ch19 */ {"chapter.tac_rogue.19.title", "chapter.tac_rogue.19.summary"},
        /* Ch20 */ {"chapter.tac_rogue.20.title", "chapter.tac_rogue.20.summary"},
    };

    /** チャプタータイトル取得 */
    public static String getChapterTitleKey(int chapter) {
        int idx = Math.max(0, Math.min(chapter - 1, CHAPTER_STORIES.length - 1));
        return CHAPTER_STORIES[idx][0];
    }

    /** チャプター概要取得 */
    public static String getChapterSummaryKey(int chapter) {
        int idx = Math.max(0, Math.min(chapter - 1, CHAPTER_STORIES.length - 1));
        return CHAPTER_STORIES[idx][1];
    }

    /** 200 通常クエスト + 20 固有ストーリークエストを生成 */
    private static void generateQuests() {
        int questId = 0;

        // 固有ストーリークエストの定義: {type, target, goldReward}
        Object[][] storyQuestDefs = {
            /* Ch 1 */ {QuestType.FLOOR_CLEAR, 3,  500},
            /* Ch 2 */ {QuestType.KILL_COUNT,  30, 600},
            /* Ch 3 */ {QuestType.HEADSHOT,    15, 700},
            /* Ch 4 */ {QuestType.STEALTH_KILL, 10, 800},
            /* Ch 5 */ {QuestType.BOSS_KILL,   2,  1000},
            /* Ch 6 */ {QuestType.NO_DAMAGE,   1,  1200},
            /* Ch 7 */ {QuestType.GOLD_EARN,   2000, 800},
            /* Ch 8 */ {QuestType.SPEEDRUN,    1,  1500},
            /* Ch 9 */ {QuestType.SURVIVE,     5,  1000},
            /* Ch10 */ {QuestType.BOSS_KILL,   3,  2000},
            /* Ch11 */ {QuestType.WEAPON_MASTERY, 50, 1200},
            /* Ch12 */ {QuestType.HEADSHOT,    30, 1500},
            /* Ch13 */ {QuestType.STEALTH_KILL, 20, 1800},
            /* Ch14 */ {QuestType.KILL_COUNT,  100, 2000},
            /* Ch15 */ {QuestType.BOSS_KILL,   5, 2500},
            /* Ch16 */ {QuestType.NO_DAMAGE,   2, 3000},
            /* Ch17 */ {QuestType.FLOOR_CLEAR, 15, 2500},
            /* Ch18 */ {QuestType.SPEEDRUN,    3,  3500},
            /* Ch19 */ {QuestType.KILL_COUNT, 150, 4000},
            /* Ch20 */ {QuestType.BOSS_KILL,   10, 5000},
        };

        for (int ch = 1; ch <= MAX_CHAPTERS; ch++) {
            // === チャプター固有ストーリークエスト ===
            Object[] sd = storyQuestDefs[ch - 1];
            ALL_QUESTS.add(new Quest(
                "story_" + String.format("%02d", ch),
                ch, (QuestType) sd[0], (int) sd[1], (int) sd[2], ch * 20
            ));
            questId++;

            // === 通常プロシージャルクエスト (9個) ===
            QuestType[] types = QuestType.values();
            for (int q = 0; q < 9; q++) {
                QuestType type = types[(ch + q) % types.length];
                int baseDifficulty = ch * 5 + q * 2;

                int target = switch (type) {
                    case KILL_COUNT -> 10 + baseDifficulty * 2;
                    case FLOOR_CLEAR -> 1 + ch / 2;
                    case HEADSHOT -> 5 + baseDifficulty;
                    case STEALTH_KILL -> 3 + baseDifficulty / 2;
                    case BOSS_KILL -> 1 + ch / 5;
                    case GOLD_EARN -> 200 + baseDifficulty * 50;
                    case SURVIVE -> 1 + ch / 3;
                    case NO_DAMAGE -> 1;
                    case SPEEDRUN -> 1;
                    case WEAPON_MASTERY -> 20 + baseDifficulty;
                };

                int goldReward = 100 + ch * 50 + q * 20;
                int xpReward = ch * 10 + q * 5;

                ALL_QUESTS.add(new Quest(
                    "q_" + String.format("%03d", questId++),
                    ch, type, target, goldReward, xpReward
                ));
            }
        }
    }

    /** プレイヤーの進行状況を取得 */
    public static QuestProgress getProgress(ServerPlayer player) {
        return playerProgress.computeIfAbsent(player.getUUID(), k -> new QuestProgress());
    }

    /** 現在のチャプターのクエスト一覧を取得 */
    public static List<Quest> getChapterQuests(int chapter) {
        return ALL_QUESTS.stream()
            .filter(q -> q.chapter == chapter)
            .toList();
    }

    /** クエスト進捗を更新 */
    public static void advanceQuest(ServerPlayer player, QuestType type, int amount) {
        QuestProgress progress = getProgress(player);
        List<Quest> chapterQuests = getChapterQuests(progress.currentChapter);

        for (Quest quest : chapterQuests) {
            if (quest.type != type) continue;
            if (progress.completedQuests.contains(quest.id)) continue;

            int current = progress.questProgress.getOrDefault(quest.id, 0) + amount;
            progress.questProgress.put(quest.id, current);

            if (current >= quest.targetAmount) {
                // クエスト完了！
                progress.completedQuests.add(quest.id);
                CurrencyManager.addGold(player, quest.goldReward);
                player.sendSystemMessage(Component.translatable(
                    "message.tac_rogue.quest_complete", quest.id, quest.goldReward));
                RunManager.syncPlayer(player);

                // チャプター内全クエスト完了チェック
                boolean allDone = chapterQuests.stream()
                    .allMatch(q -> progress.completedQuests.contains(q.id));
                if (allDone) {
                    advanceChapter(player, progress);
                }
            }
        }
    }

    /** チャプター進行 */
    private static void advanceChapter(ServerPlayer player, QuestProgress progress) {
        if (progress.currentChapter < MAX_CHAPTERS) {
            progress.currentChapter++;
            player.sendSystemMessage(Component.translatable(
                "message.tac_rogue.chapter_unlock", progress.currentChapter));
        } else {
            // 全チャプタークリア → NG+
            progress.ngPlusLevel++;
            progress.currentChapter = 1;
            progress.questProgress.clear();
            progress.completedQuests.clear();
            player.sendSystemMessage(Component.translatable(
                "message.tac_rogue.ng_plus", progress.ngPlusLevel));
        }
    }

    /** クエスト進捗のリセット (データ管理用) */
    public static void resetPlayer(UUID uuid) {
        playerProgress.remove(uuid);
    }

    /** 全クエスト数 */
    public static int getTotalQuestCount() {
        return ALL_QUESTS.size();
    }
}
