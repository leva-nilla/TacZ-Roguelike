package com.levanilla.rogue.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * パークをプロシージャルに生成するエンジン。
 * フロアの深さやボス戦かどうかに応じて、適切な品質のパーク候補を 3 つ生成する。
 */
public class PerkGenerator {

    private static final Random RANDOM = new Random();

    /**
     * フロアクリア時のパーク候補を 3 つ生成する。
     * @param floor 現在の階層
     * @param isBoss ボスフロアかどうか
     * @param existingPerks 既に取得済みのパークタグ（重複防止）
     * @param overclockedCount 現在プレイヤーが所持しているOVERCLOCKEDパークの数
     */
    public static List<PerkDefinition> generateChoices(int floor, boolean isBoss, Set<String> existingPerks, int overclockedCount) {
        return generateChoices(floor, isBoss, existingPerks, overclockedCount, 3);
    }

    public static List<PerkDefinition> generateChoices(int floor, boolean isBoss, Set<String> existingPerks, int overclockedCount, int choiceCount) {
        List<PerkDefinition> choices = new ArrayList<>();
        int attempts = 0;
        int targetChoices = Math.max(3, Math.min(5, choiceCount));

        while (choices.size() < targetChoices && attempts < 140) {
            attempts++;
            PerkDefinition perk = generateSingle(floor, isBoss, overclockedCount);
            // 重複チェック（同一カテゴリ+修飾子の組み合わせは除外）
            String key = perk.category.name() + ":" + perk.modifier.name();
            boolean duplicate = existingPerks.contains(perk.toTag()) ||
                choices.stream().anyMatch(p -> (p.category.name() + ":" + p.modifier.name()).equals(key));
            if (!duplicate) {
                choices.add(perk);
            }
        }

        // フォールバック: 3 つ埋まらなかった場合
        while (choices.size() < targetChoices) {
            PerkDefinition.Category cat = PerkDefinition.Category.values()[RANDOM.nextInt(PerkDefinition.Category.values().length)];
            choices.add(new PerkDefinition(cat, PerkDefinition.Modifier.NONE, 1));
        }

        return choices;
    }

    /**
     * 初期パーク候補を生成する（ゲーム開始時）。
     * Lv.1-2, 修飾子なしの低品質パーク。
     */
    public static List<PerkDefinition> generateInitialChoices() {
        return generateInitialChoices(3);
    }

    public static List<PerkDefinition> generateInitialChoices(int choiceCount) {
        List<PerkDefinition> choices = new ArrayList<>();
        PerkDefinition.Category[] cats = PerkDefinition.Category.values();
        int targetChoices = Math.max(3, Math.min(5, choiceCount));

        // 異なるカテゴリから 3 つ選出
        List<Integer> usedIndices = new ArrayList<>();
        while (choices.size() < targetChoices) {
            int idx = RANDOM.nextInt(cats.length);
            if (!usedIndices.contains(idx)) {
                usedIndices.add(idx);
                int level = 1 + RANDOM.nextInt(2); // Lv.1-2
                choices.add(new PerkDefinition(cats[idx], PerkDefinition.Modifier.NONE, level));
            }
        }
        return choices;
    }

    /** 単一のパークを生成 */
    private static PerkDefinition generateSingle(int floor, boolean isBoss, int overclockedCount) {
        PerkDefinition.Category[] cats = PerkDefinition.Category.values();
        PerkDefinition.Category category = chooseCategoryForFloor(floor, isBoss, cats);

        // 修飾子の決定
        PerkDefinition.Modifier modifier;
        if (isBoss) {
            // ボスフロア: Reinforced or Blessed 確定
            modifier = RANDOM.nextBoolean() ? PerkDefinition.Modifier.REINFORCED : PerkDefinition.Modifier.BLESSED;
        } else {
            // 通常フロア: 序盤は危険修飾子を抑え、Act進行で高リスクを解禁
            float roll = RANDOM.nextFloat();
            if (floor < 8) {
                if (roll < 0.50f) modifier = PerkDefinition.Modifier.NONE;
                else if (roll < 0.68f) modifier = PerkDefinition.Modifier.REINFORCED;
                else if (roll < 0.78f) modifier = PerkDefinition.Modifier.BLESSED;
                else if (roll < 0.86f) modifier = PerkDefinition.Modifier.FRACTURED;
                else if (roll < 0.93f) modifier = PerkDefinition.Modifier.PRIMAL;
                else modifier = PerkDefinition.Modifier.CURSED;
            } else if (floor < 16) {
                if (roll < 0.42f) modifier = PerkDefinition.Modifier.NONE;
                else if (roll < 0.58f) modifier = PerkDefinition.Modifier.REINFORCED;
                else if (roll < 0.66f) modifier = PerkDefinition.Modifier.BLESSED;
                else if (roll < 0.74f) modifier = PerkDefinition.Modifier.FRACTURED;
                else if (roll < 0.82f) modifier = PerkDefinition.Modifier.PRIMAL;
                else if (roll < 0.88f) modifier = PerkDefinition.Modifier.RADIANT;
                else if (roll < 0.94f) modifier = PerkDefinition.Modifier.CURSED;
                else modifier = (overclockedCount >= 2) ? PerkDefinition.Modifier.PRIMAL : PerkDefinition.Modifier.OVERCLOCKED;
            } else {
                if (roll < 0.35f) modifier = PerkDefinition.Modifier.NONE;
                else if (roll < 0.50f) modifier = PerkDefinition.Modifier.REINFORCED;
                else if (roll < 0.58f) modifier = PerkDefinition.Modifier.BLESSED;
                else if (roll < 0.66f) modifier = PerkDefinition.Modifier.FRACTURED;
                else if (roll < 0.74f) modifier = PerkDefinition.Modifier.PRIMAL;
                else if (roll < 0.82f) modifier = PerkDefinition.Modifier.RADIANT;
                else if (roll < 0.88f) modifier = PerkDefinition.Modifier.CURSED;
                else if (roll < 0.93f) modifier = (overclockedCount >= 2) ? PerkDefinition.Modifier.PRIMAL : PerkDefinition.Modifier.OVERCLOCKED;
                else if (roll < 0.97f) modifier = PerkDefinition.Modifier.CORRUPTED;
                else modifier = PerkDefinition.Modifier.TITANIC;
            }
        }

        // レベルの決定
        int maxLevelParam = Math.max(1, Math.min(10, 1 + (floor * 9 / 80)));
        int maxLevel = isBoss ? Math.min(10, maxLevelParam + 2) : maxLevelParam;
        int level = 1 + RANDOM.nextInt(maxLevel);

        return new PerkDefinition(category, modifier, level);
    }

    private static PerkDefinition.Category chooseCategoryForFloor(int floor, boolean isBoss, PerkDefinition.Category[] cats) {
        if (isBoss || RANDOM.nextFloat() < 0.60f) {
            List<PerkDefinition.Category> preferred = RogueActManager.getPreferredPerkCategories(floor);
            if (!preferred.isEmpty()) {
                return preferred.get(RANDOM.nextInt(preferred.size()));
            }
        }
        return cats[RANDOM.nextInt(cats.length)];
    }

    /** 隠し部屋で発見するパーク（自動獲得、Lv.1-2） */
    public static PerkDefinition generateSecretRoomPerk() {
        PerkDefinition.Category[] cats = PerkDefinition.Category.values();
        PerkDefinition.Category category = cats[RANDOM.nextInt(cats.length)];
        int level = 1 + RANDOM.nextInt(2);
        PerkDefinition.Modifier modifier = RANDOM.nextFloat() < 0.3f ?
            PerkDefinition.Modifier.REINFORCED : PerkDefinition.Modifier.NONE;
        return new PerkDefinition(category, modifier, level);
    }
}
