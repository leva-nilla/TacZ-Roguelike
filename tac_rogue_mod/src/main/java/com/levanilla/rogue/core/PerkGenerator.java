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
     */
    public static List<PerkDefinition> generateChoices(int floor, boolean isBoss, Set<String> existingPerks) {
        List<PerkDefinition> choices = new ArrayList<>();
        int attempts = 0;

        while (choices.size() < 3 && attempts < 100) {
            attempts++;
            PerkDefinition perk = generateSingle(floor, isBoss);
            // 重複チェック（同一カテゴリ+修飾子の組み合わせは除外）
            String key = perk.category.name() + ":" + perk.modifier.name();
            boolean duplicate = existingPerks.contains(perk.toTag()) ||
                choices.stream().anyMatch(p -> (p.category.name() + ":" + p.modifier.name()).equals(key));
            if (!duplicate) {
                choices.add(perk);
            }
        }

        // フォールバック: 3 つ埋まらなかった場合
        while (choices.size() < 3) {
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
        List<PerkDefinition> choices = new ArrayList<>();
        PerkDefinition.Category[] cats = PerkDefinition.Category.values();

        // 異なるカテゴリから 3 つ選出
        List<Integer> usedIndices = new ArrayList<>();
        while (choices.size() < 3) {
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
    private static PerkDefinition generateSingle(int floor, boolean isBoss) {
        PerkDefinition.Category[] cats = PerkDefinition.Category.values();
        PerkDefinition.Category category = cats[RANDOM.nextInt(cats.length)];

        // 修飾子の決定
        PerkDefinition.Modifier modifier;
        if (isBoss) {
            // ボスフロア: Reinforced or Blessed 確定
            modifier = RANDOM.nextBoolean() ? PerkDefinition.Modifier.REINFORCED : PerkDefinition.Modifier.BLESSED;
        } else {
            // 通常フロア: 重み付きランダム
            float roll = RANDOM.nextFloat();
            if (roll < 0.40f) modifier = PerkDefinition.Modifier.NONE;
            else if (roll < 0.55f) modifier = PerkDefinition.Modifier.REINFORCED;
            else if (roll < 0.65f) modifier = PerkDefinition.Modifier.CURSED;
            else if (roll < 0.73f) modifier = PerkDefinition.Modifier.VOLATILE;
            else if (roll < 0.78f) modifier = PerkDefinition.Modifier.BLESSED;
            else if (roll < 0.85f) modifier = PerkDefinition.Modifier.FRACTURED;
            else if (roll < 0.92f) modifier = PerkDefinition.Modifier.PRIMAL;
            else modifier = PerkDefinition.Modifier.OVERCLOCKED;
        }

        // レベルの決定
        int minLevel, maxLevel;
        if (isBoss) {
            minLevel = 3; maxLevel = 5;
        } else {
            minLevel = 1;
            maxLevel = Math.min(3 + floor / 5, 10);
        }
        int level = minLevel + RANDOM.nextInt(maxLevel - minLevel + 1);

        return new PerkDefinition(category, modifier, level);
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
