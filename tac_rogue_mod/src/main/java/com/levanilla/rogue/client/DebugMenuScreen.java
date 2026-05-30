package com.levanilla.rogue.client;

import com.levanilla.rogue.core.ClientRunState;
import com.levanilla.rogue.core.DifficultyManager;
import com.levanilla.rogue.core.PerkDefinition;
import com.levanilla.rogue.core.QuestManager;
import com.levanilla.rogue.core.TacZRegistryHelper;
import com.levanilla.rogue.core.WeaponRarity;
import com.levanilla.rogue.core.registry.ShopCatalog;
import com.levanilla.rogue.core.service.RogueItemFactory;
import com.levanilla.rogue.networking.DebugActionMessage;
import com.levanilla.rogue.networking.TacRogueNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DebugMenuScreen extends Screen {
    private enum Tab { WEAPONS, ITEMS, PERKS, QUESTS, STATE, MULTIPLAYER }

    private static final ShopCatalog.Category[] WEAPON_CATEGORIES = {
        ShopCatalog.Category.PISTOL,
        ShopCatalog.Category.SMG,
        ShopCatalog.Category.RIFLE,
        ShopCatalog.Category.SHOTGUN,
        ShopCatalog.Category.SNIPER,
        ShopCatalog.Category.LMG,
        ShopCatalog.Category.EXPLOSIVE
    };

    private static final int SLOT = 22;
    private static final int WEAPON_COLS = 8;
    private static final int WEAPON_ROWS = 5;
    private static final int WEAPON_PAGE_SIZE = WEAPON_COLS * WEAPON_ROWS;
    private static final int CATEGORY_W = 84;
    private static final int PERK_CATEGORY_W = 124;
    private static final int CATEGORY_ROW_H = 18;
    private static final PerkDefinition.Category[] PERK_CATEGORIES = Arrays.stream(PerkDefinition.Category.values())
        .sorted(Comparator.comparing(c -> c.displayName.toLowerCase(Locale.ROOT)))
        .toArray(PerkDefinition.Category[]::new);

    private Tab tab = Tab.WEAPONS;
    private EditBox searchBox;
    private int weaponCategoryIndex = 0;
    private int weaponCategoryScroll = 0;
    private int weaponPage = 0;
    private int selectedWeaponIndex = 0;
    private int hoveredWeaponIndex = -1;
    private String weaponSearch = "";
    private int itemPage = 0;
    private int selectedItemIndex = 0;
    private int hoveredItemIndex = -1;
    private String itemSearch = "";
    private WeaponRarity.Rarity selectedRarity = WeaponRarity.Rarity.COMMON;
    private int perkCategoryIndex = 0;
    private int perkCategoryScroll = 0;
    private int perkModifierIndex = 0;
    private int perkLevel = 1;
    private int questTypeIndex = 0;
    private int questTypeScroll = 0;
    private int questAmount = 1;
    private int mpPartySize = 2;
    private int mpVirtualJoinCount = 1;
    private PerkDefinition previewPerk;
    private List<ShopCatalog.ShopItem> cachedFilteredWeapons = List.of();
    private List<ShopCatalog.ShopItem> cachedWeaponSource = null;
    private ShopCatalog.Category cachedWeaponCategory = null;
    private String cachedWeaponSearch = null;
    private List<DebugItem> cachedFilteredDebugItems = List.of();
    private String cachedDebugItemSearch = null;
    private final Map<String, ItemStack> previewWeaponStackCache = new HashMap<>();
    private final Map<String, ItemStack> previewDebugItemStackCache = new HashMap<>();
    private WeaponRarity.Rarity cachedPreviewRarity = null;

    private static final List<DebugItem> DEBUG_ITEMS = List.of(
        new DebugItem("rogue:medkit", "Medkit", "Recovery"),
        new DebugItem("rogue:field_ration", "Field Ration", "Recovery"),
        new DebugItem("rogue:stamina_shot", "Stamina Shot", "Recovery"),
        new DebugItem("rogue:bandage", "Bandage", "Recovery"),
        new DebugItem("rogue:armor_plate", "Armor Plate", "Recovery"),
        new DebugItem("rogue:adrenaline", "Adrenaline Syringe", "Recovery"),
        new DebugItem("rogue:emp_device", "EMP Device", "Recovery"),
        new DebugItem("rogue:emergency_ration", "Emergency Ration", "Recovery"),
        new DebugItem("minecraft:snowball", "Snowball", "Tactical"),
        new DebugItem("rogue:noise_maker", "Noise Maker", "Tactical"),
        new DebugItem("rogue:smoke_canister", "Smoke Canister", "Tactical"),
        new DebugItem("rogue:flash_charge", "Flash Charge", "Tactical"),
        new DebugItem("rogue:portable_shield", "Portable Shield", "Tactical"),
        new DebugItem("rogue:micro_turret", "Micro Turret", "Tactical"),
        new DebugItem("rogue:ballistic_charm", "Ballistic Charm", "Passive"),
        new DebugItem("rogue:quickdraw_charm", "Quickdraw Charm", "Passive"),
        new DebugItem("rogue:ammo_saver_charm", "Ammo Saver Charm", "Passive"),
        new DebugItem("rogue:terminal_decoder", "Terminal Decoder", "Passive"),
        new DebugItem("rogue:recovery_beacon", "Recovery Beacon", "Passive"),
        new DebugItem("rogue:defense_sensor", "Defense Sensor", "Passive"),
        new DebugItem("rogue:maintenance_kit", "Maintenance Kit", "Passive"),
        new DebugItem("rogue:range_card", "Range Card", "Passive"),
        new DebugItem("rogue:ballistic_computer", "Ballistic Computer", "Passive"),
        new DebugItem("rogue:ballistic_insert", "Ballistic Insert", "Risk"),
        new DebugItem("rogue:mag_pouch_rig", "Mag Pouch Rig", "Risk"),
        new DebugItem("rogue:blood_dogtag", "Blood Dogtag", "Risk"),
        new DebugItem("rogue:overheat_core", "Overheat Core", "Risk"),
        new DebugItem("rogue:gold_cache", "Gold Cache", "Currency"),
        new DebugItem("rogue:scrap_metal", "Scrap Metal", "Currency")
    );

    public DebugMenuScreen() {
        super(Component.translatable("gui.tac_rogue.debug.title"));
    }

    public static void open() {
        Minecraft.getInstance().tell(() ->
            Minecraft.getInstance().setScreen(new DebugMenuScreen()));
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        this.clearWidgets();
        int panelX = panelX();
        int panelY = panelY();
        int panelW = panelW();
        int panelH = panelH();

        addTabButtons(panelX, panelY, panelW);

        switch (tab) {
            case WEAPONS -> initWeapons(panelX, panelY, panelW, panelH);
            case ITEMS -> initItems(panelX, panelY, panelW, panelH);
            case PERKS -> initPerks(panelX, panelY, panelW, panelH);
            case QUESTS -> initQuests(panelX, panelY, panelW, panelH);
            case STATE -> initState(panelX, panelY, panelW, panelH);
            case MULTIPLAYER -> initMultiplayer(panelX, panelY, panelW, panelH);
        }
    }

    private void addTabButtons(int panelX, int panelY, int panelW) {
        Tab[] tabs = Tab.values();
        int gap = 5;
        int tabW = Math.max(44, Math.min(88, (panelW - 24 - gap * (tabs.length - 1)) / tabs.length));
        int x = panelX + 12;
        for (Tab target : tabs) {
            addTabButton(x, panelY + 8, tabW, target);
            x += tabW + gap;
        }
    }

    private void addTabButton(int x, int y, int w, Tab target) {
        this.addRenderableWidget(Button.builder(
            Component.translatable("gui.tac_rogue.debug.tab." + target.name().toLowerCase(Locale.ROOT), tab == target ? "> " : ""),
            b -> {
                tab = target;
                hoveredWeaponIndex = -1;
                rebuild();
            }).bounds(x, y, w, 18).build());
    }

    private void initWeapons(int panelX, int panelY, int panelW, int panelH) {
        int gridX = weaponGridX(panelX);
        int top = panelY + 32;
        int searchW = Math.max(86, Math.min(190, weaponDetailX(panelX) - gridX - 8));
        searchBox = new EditBox(this.font, gridX, top, searchW, 18, Component.translatable("gui.tac_rogue.debug.search"));
        searchBox.setValue(weaponSearch);
        searchBox.setResponder(s -> {
            weaponSearch = s;
            weaponPage = 0;
            selectedWeaponIndex = 0;
        });
        this.addRenderableWidget(searchBox);

        addWeaponCategoryButtons(panelX, panelY, panelH);
        addRarityButtons(panelX, panelY, panelW, panelH);

        int detailX = weaponDetailX(panelX);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.debug.give_selected_gun"), b -> {
            List<ShopCatalog.ShopItem> current = getFilteredWeapons();
            if (!current.isEmpty()) {
                int index = Math.max(0, Math.min(selectedWeaponIndex, current.size() - 1));
                send(DebugActionMessage.ActionType.GIVE_GUN, current.get(index).id + "|" + selectedRarity.name());
            }
        }).bounds(detailX, panelY + panelH - 28, 140, 20).build());
    }

    private void initItems(int panelX, int panelY, int panelW, int panelH) {
        int gridX = panelX + 18;
        int top = panelY + 32;
        int searchW = Math.max(120, Math.min(240, panelW / 3));
        searchBox = new EditBox(this.font, gridX, top, searchW, 18, Component.translatable("gui.tac_rogue.debug.search"));
        searchBox.setValue(itemSearch);
        searchBox.setResponder(s -> {
            itemSearch = s;
            itemPage = 0;
            selectedItemIndex = 0;
        });
        this.addRenderableWidget(searchBox);

        int detailX = gridX + WEAPON_COLS * SLOT + 18;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.debug.give_selected_item"), b -> {
            List<DebugItem> current = getFilteredDebugItems();
            if (!current.isEmpty()) {
                int index = Math.max(0, Math.min(selectedItemIndex, current.size() - 1));
                send(DebugActionMessage.ActionType.GIVE_ITEM, current.get(index).id());
            }
        }).bounds(detailX, panelY + panelH - 28, 140, 20).build());
    }

    private void addWeaponCategoryButtons(int panelX, int panelY, int panelH) {
        int visible = visibleCategoryRows(panelH);
        int maxScroll = Math.max(0, WEAPON_CATEGORIES.length - visible);
        weaponCategoryScroll = Math.max(0, Math.min(weaponCategoryScroll, maxScroll));
        int x = panelX + 12;
        int y = categoryListTop(panelY);
        for (int row = 0; row < visible; row++) {
            int index = weaponCategoryScroll + row;
            if (index >= WEAPON_CATEGORIES.length) break;
            ShopCatalog.Category category = WEAPON_CATEGORIES[index];
            Component label = Component.empty()
                .append(index == weaponCategoryIndex ? "> " : "")
                .append(categoryLabel(category));
            this.addRenderableWidget(Button.builder(label, b -> {
                weaponCategoryIndex = index;
                weaponPage = 0;
                selectedWeaponIndex = 0;
                rebuild();
            }).bounds(x, y + row * CATEGORY_ROW_H, CATEGORY_W - 4, 16).build());
        }
    }

    private void addRarityButtons(int panelX, int panelY, int panelW, int panelH) {
        int detailX = weaponDetailX(panelX);
        int detailW = Math.max(110, panelX + panelW - detailX - 12);
        int startY = panelY + panelH - 78;
        int cols = detailW >= 224 ? 3 : 2;
        int buttonW = Math.max(54, Math.min(70, (detailW - (cols - 1) * 4) / cols));
        int i = 0;
        for (WeaponRarity.Rarity rarity : WeaponRarity.Rarity.values()) {
            int col = i % cols;
            int row = i / cols;
            this.addRenderableWidget(Button.builder(Component.empty()
                .append(rarity == selectedRarity ? "> " : "")
                .append(Component.translatable("rarity.tac_rogue." + rarity.name().toLowerCase(Locale.ROOT))), b -> {
                selectedRarity = rarity;
                rebuild();
            }).bounds(detailX + col * (buttonW + 4), startY + row * 20, buttonW, 18).build());
            i++;
        }
    }

    private void initPerks(int panelX, int panelY, int panelW, int panelH) {
        addPerkCategoryButtons(panelX, panelY, panelH);

        perkCategoryIndex = Math.max(0, Math.min(perkCategoryIndex, PERK_CATEGORIES.length - 1));
        PerkDefinition.Category category = PERK_CATEGORIES[perkCategoryIndex];
        PerkDefinition.Modifier modifier = PerkDefinition.Modifier.values()[perkModifierIndex];
        PerkDefinition preview = new PerkDefinition(category, modifier, perkLevel);
        this.previewPerk = preview;

        int x = panelX + 12 + PERK_CATEGORY_W + 18;
        int y = panelY + 60;
        int detailW = Math.max(180, panelX + panelW - x - 14);
        int smallW = Math.max(72, Math.min(94, (detailW - 16) / 3));

        this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.debug.prev_modifier"), b -> {
            perkModifierIndex = Math.floorMod(perkModifierIndex - 1, PerkDefinition.Modifier.values().length);
            rebuild();
        }).bounds(x, y + 48, smallW, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.debug.next_modifier"), b -> {
            perkModifierIndex = (perkModifierIndex + 1) % PerkDefinition.Modifier.values().length;
            rebuild();
        }).bounds(x + detailW - smallW, y + 48, smallW, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.debug.level_down"), b -> {
            perkLevel = Math.max(1, perkLevel - 1);
            rebuild();
        }).bounds(x, y + 86, smallW, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.debug.level_up"), b -> {
            perkLevel = Math.min(10, perkLevel + 1);
            rebuild();
        }).bounds(x + detailW - smallW, y + 86, smallW, 20).build());

        int actionGap = 8;
        int actionW = Math.max(82, Math.min(126, (detailW - actionGap * 2) / 3));
        int actionY = panelY + panelH - 58;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.debug.give_perk"), b ->
            send(DebugActionMessage.ActionType.GIVE_PERK,
                category.name() + "|" + modifier.name() + "|" + perkLevel)
        ).bounds(x, actionY, actionW, 22).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.debug.remove_selected"), b ->
            send(DebugActionMessage.ActionType.REMOVE_PERK,
                category.name() + "|" + modifier.name() + "|" + perkLevel)
        ).bounds(x + actionW + actionGap, actionY, actionW, 22).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.debug.clear_all_perks"), b ->
            send(DebugActionMessage.ActionType.CLEAR_PERKS, "")
        ).bounds(x + (actionW + actionGap) * 2, actionY, actionW, 22).build());
    }

    private void addPerkCategoryButtons(int panelX, int panelY, int panelH) {
        PerkDefinition.Category[] categories = PERK_CATEGORIES;
        int visible = visibleCategoryRows(panelH);
        int maxScroll = Math.max(0, categories.length - visible);
        perkCategoryScroll = Math.max(0, Math.min(perkCategoryScroll, maxScroll));
        int x = panelX + 12;
        int y = categoryListTop(panelY);
        for (int row = 0; row < visible; row++) {
            int index = perkCategoryScroll + row;
            if (index >= categories.length) break;
            PerkDefinition.Category category = categories[index];
            Component label = Component.literal((index == perkCategoryIndex ? "> " : "") + trim(category.displayName, 16));
            this.addRenderableWidget(Button.builder(label, b -> {
                perkCategoryIndex = index;
                rebuild();
            }).bounds(x, y + row * CATEGORY_ROW_H, PERK_CATEGORY_W - 4, 16).build());
        }
    }

    private void initQuests(int panelX, int panelY, int panelW, int panelH) {
        addQuestTypeButtons(panelX, panelY, panelH);

        int x = panelX + 12 + PERK_CATEGORY_W + 18;
        int y = panelY + 132;
        int bx = x;
        for (int amount : new int[] {1, 5, 10, 100}) {
            this.addRenderableWidget(Button.builder(Component.literal((questAmount == amount ? "> " : "") + amount), b -> {
                questAmount = amount;
                rebuild();
            }).bounds(bx, y, 58, 20).build());
            bx += 64;
        }

        QuestManager.QuestType type = QuestManager.QuestType.values()[questTypeIndex];
        this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.debug.advance_quest"), b ->
            send(DebugActionMessage.ActionType.ADVANCE_QUEST, type.name() + "|" + questAmount)
        ).bounds(x, y + 34, 140, 24).build());
    }

    private void addQuestTypeButtons(int panelX, int panelY, int panelH) {
        QuestManager.QuestType[] types = QuestManager.QuestType.values();
        int visible = visibleCategoryRows(panelH);
        int maxScroll = Math.max(0, types.length - visible);
        questTypeScroll = Math.max(0, Math.min(questTypeScroll, maxScroll));
        int x = panelX + 12;
        int y = categoryListTop(panelY);
        for (int row = 0; row < visible; row++) {
            int index = questTypeScroll + row;
            if (index >= types.length) break;
            QuestManager.QuestType type = types[index];
            Component label = Component.empty()
                .append(index == questTypeIndex ? "> " : "")
                .append(Component.translatable(type.langKey));
            this.addRenderableWidget(Button.builder(label, b -> {
                questTypeIndex = index;
                rebuild();
            }).bounds(x, y + row * CATEGORY_ROW_H, PERK_CATEGORY_W - 4, 16).build());
        }
    }

    private void initState(int panelX, int panelY, int panelW, int panelH) {
        int controlsY = Math.min(panelY + panelH - 150, panelY + 158);
        int usableW = panelW - 36;
        int cols = panelW >= 650 ? 5 : panelW >= 500 ? 4 : 3;
        addButtonGrid(panelX + 18, controlsY, usableW, cols, List.of(
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.add_gold"), b ->
                send(DebugActionMessage.ActionType.ADD_GOLD, "1000")),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.sync"), b ->
                send(DebugActionMessage.ActionType.SYNC, "")),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.ammo_add"), b ->
                send(DebugActionMessage.ActionType.GIVE_AMMO, "current|128")),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.clear_on"), b ->
                send(DebugActionMessage.ActionType.SET_FLOOR_CLEARED, "true")),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.clear_off"), b ->
                send(DebugActionMessage.ActionType.SET_FLOOR_CLEARED, "false")),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.floor_up"), b ->
                send(DebugActionMessage.ActionType.SET_FLOOR, String.valueOf(Math.max(1, ClientRunState.getFloor() + 1)))),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.floor_up_5"), b ->
                send(DebugActionMessage.ActionType.SET_FLOOR, String.valueOf(Math.max(1, ClientRunState.getFloor() + 5)))),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.floor_up_100"), b ->
                send(DebugActionMessage.ActionType.SET_FLOOR, String.valueOf(Math.max(1, ClientRunState.getFloor() + 100)))),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.max_floor"), b ->
                send(DebugActionMessage.ActionType.SET_MAX_FLOOR, String.valueOf(Math.max(0, ClientRunState.getFloor())))),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.flash_up"), b ->
                send(DebugActionMessage.ActionType.SET_FLASHLIGHT_LEVEL, String.valueOf(ClientRunState.getFlashlightLevel() + 1))),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.reload_info"), b ->
                send(DebugActionMessage.ActionType.RELOAD_INFO, "")),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.ai_overlay",
                DebugAiOverlayManager.isEnabled() ? "ON" : "OFF"), b -> {
                DebugAiOverlayManager.toggle();
                rebuild();
            }),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.boss_info"), b ->
                send(DebugActionMessage.ActionType.BOSS_INFO, "")),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.kill_floor_enemies"), b ->
                send(DebugActionMessage.ActionType.KILL_DUNGEON_ENEMIES, "")),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.deep_core"), b ->
                send(DebugActionMessage.ActionType.ADD_DEEP_CORE, "25")),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.deep_gun"), b ->
                send(DebugActionMessage.ActionType.GIVE_DEEP_GUN, "")),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.deep_task"), b ->
                send(DebugActionMessage.ActionType.COMPLETE_DEEP_TASK, "")),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.prestige_set"), b ->
                send(DebugActionMessage.ActionType.SET_PRESTIGE, "1")),
            debugCommandButton("gui.tac_rogue.debug.cmd_smoke_monster", "debug smoke monster"),
            debugCommandButton("gui.tac_rogue.debug.cmd_smoke_objective", "debug smoke objective"),
            debugCommandButton("gui.tac_rogue.debug.cmd_smoke_encounter", "debug smoke encounter"),
            debugCommandButton("gui.tac_rogue.debug.cmd_smoke_perk_storage", "debug smoke perk_storage"),
            debugCommandButton("gui.tac_rogue.debug.cmd_perf_start", "debug perf_start 30 gui"),
            debugCommandButton("gui.tac_rogue.debug.cmd_perf_stop", "debug perf_stop"),
            debugCommandButton("gui.tac_rogue.debug.cmd_fire_probe", "debug fire_rate_probe start 10"),
            debugCommandButton("gui.tac_rogue.debug.cmd_fire_result", "debug fire_rate_probe result"),
            debugCommandButton("gui.tac_rogue.debug.cmd_alert_probe", "debug alert_probe 40 false"),
            debugCommandButton("gui.tac_rogue.debug.cmd_support_team", "debug support_team 4"),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.boss_breach"), b ->
                send(DebugActionMessage.ActionType.SPAWN_BOSS, "BREACHER")),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.boss_cmd"), b ->
                send(DebugActionMessage.ActionType.SPAWN_BOSS, "COMMANDER")),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.boss_void"), b ->
                send(DebugActionMessage.ActionType.SPAWN_BOSS, "VOID_WARDEN")),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.boss_pyro"), b ->
                send(DebugActionMessage.ActionType.SPAWN_BOSS, "PYRO")),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.boss_lev"), b ->
                send(DebugActionMessage.ActionType.SPAWN_BOSS, "LEVIATHAN"))
        ));
    }

    private DebugButtonSpec debugCommandButton(String langKey, String command) {
        return new DebugButtonSpec(Component.translatable(langKey), b ->
            send(DebugActionMessage.ActionType.RUN_DEBUG_COMMAND, command));
    }

    private void initMultiplayer(int panelX, int panelY, int panelW, int panelH) {
        int x = panelX + 18;
        int y = panelY + 96;
        int usableW = panelW - 36;
        int cols = panelW >= 600 ? 4 : panelW >= 460 ? 3 : 2;

        addButtonGrid(x, y, usableW, cols, List.of(
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.mp_state"), b ->
                send(DebugActionMessage.ActionType.MP_STATE, "")),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.mp_start_now"), b ->
                send(DebugActionMessage.ActionType.MP_START_NOW, "")),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.mp_complete"), b ->
                send(DebugActionMessage.ActionType.MP_COMPLETE, "")),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.mp_leave"), b ->
                send(DebugActionMessage.ActionType.MP_LEAVE, "")),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.mp_party_minus"), b -> {
                mpPartySize = Math.max(1, mpPartySize - 1);
                rebuild();
            }),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.mp_party_set", mpPartySize), b ->
                send(DebugActionMessage.ActionType.MP_PARTY_SIZE, String.valueOf(mpPartySize))),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.mp_party_plus"), b -> {
                mpPartySize = Math.min(8, mpPartySize + 1);
                rebuild();
            }),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.mp_virtual_minus"), b -> {
                mpVirtualJoinCount = Math.max(1, mpVirtualJoinCount - 1);
                rebuild();
            }),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.mp_virtual_join", mpVirtualJoinCount), b ->
                send(DebugActionMessage.ActionType.MP_VIRTUAL_JOIN, String.valueOf(mpVirtualJoinCount))),
            new DebugButtonSpec(Component.translatable("gui.tac_rogue.debug.mp_virtual_plus"), b -> {
                mpVirtualJoinCount = Math.min(7, mpVirtualJoinCount + 1);
                rebuild();
            })
        ));
    }

    private List<ShopCatalog.ShopItem> getFilteredWeapons() {
        String query = weaponSearch.toLowerCase(Locale.ROOT).trim();
        ShopCatalog.Category category = WEAPON_CATEGORIES[weaponCategoryIndex];
        List<ShopCatalog.ShopItem> source = TacZRegistryHelper.getAllShopItems();
        if (cachedWeaponSource == source
            && cachedWeaponCategory == category
            && query.equals(cachedWeaponSearch)) {
            return cachedFilteredWeapons;
        }

        if (cachedWeaponSource != source) {
            previewWeaponStackCache.clear();
        }
        List<ShopCatalog.ShopItem> result = new ArrayList<>();
        for (ShopCatalog.ShopItem item : source) {
            if (item.category != category) continue;
            if (!query.isEmpty()
                && !item.id.toLowerCase(Locale.ROOT).contains(query)
                && !item.displayName.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            result.add(item);
        }
        cachedWeaponSource = source;
        cachedWeaponCategory = category;
        cachedWeaponSearch = query;
        cachedFilteredWeapons = result;
        return cachedFilteredWeapons;
    }

    private List<DebugItem> getFilteredDebugItems() {
        String query = itemSearch.toLowerCase(Locale.ROOT).trim();
        if (query.equals(cachedDebugItemSearch)) {
            return cachedFilteredDebugItems;
        }
        if (query.isEmpty()) {
            cachedDebugItemSearch = query;
            cachedFilteredDebugItems = DEBUG_ITEMS;
            return cachedFilteredDebugItems;
        }
        List<DebugItem> result = new ArrayList<>();
        for (DebugItem item : DEBUG_ITEMS) {
            if (item.id().toLowerCase(Locale.ROOT).contains(query)
                || item.label().toLowerCase(Locale.ROOT).contains(query)
                || item.group().toLowerCase(Locale.ROOT).contains(query)) {
                result.add(item);
            }
        }
        cachedDebugItemSearch = query;
        cachedFilteredDebugItems = result;
        return cachedFilteredDebugItems;
    }

    private void send(DebugActionMessage.ActionType action, String data) {
        TacRogueNetworking.CHANNEL.sendToServer(new DebugActionMessage(action, data));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int panelX = panelX();
        int panelY = panelY();
        int panelW = panelW();
        int panelH = panelH();
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xEA07111F);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, 0xFF44AAFF);
        graphics.renderOutline(panelX, panelY, panelW, panelH, 0xAA44AAFF);

        switch (tab) {
            case WEAPONS -> renderWeapons(graphics, mouseX, mouseY, panelX, panelY, panelW, panelH);
            case ITEMS -> renderItems(graphics, mouseX, mouseY, panelX, panelY, panelW, panelH);
            case PERKS -> renderPerks(graphics, panelX, panelY, panelW, panelH);
            case QUESTS -> renderQuests(graphics, panelX, panelY, panelW, panelH);
            case STATE -> renderStateInfo(graphics, panelX, panelY, panelW, panelH);
            case MULTIPLAYER -> renderMultiplayer(graphics, panelX, panelY, panelW, panelH);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        if (tab == Tab.WEAPONS) renderWeaponTooltip(graphics, mouseX, mouseY);
        if (tab == Tab.ITEMS) renderItemTooltip(graphics, mouseX, mouseY);
    }

    private void renderWeapons(GuiGraphics graphics, int mouseX, int mouseY, int panelX, int panelY, int panelW, int panelH) {
        renderCategoryPanel(graphics, panelX + 10, categoryListTop(panelY) - 2, CATEGORY_W + 2,
            visibleCategoryRows(panelH), weaponCategoryScroll, Math.max(0, WEAPON_CATEGORIES.length - visibleCategoryRows(panelH)));

        int gridX = weaponGridX(panelX);
        int gridY = panelY + 58;
        graphics.drawString(this.font, categoryLabel(WEAPON_CATEGORIES[weaponCategoryIndex]), panelX + 12, panelY + 34, WEAPON_CATEGORIES[weaponCategoryIndex].color, false);
        renderWeaponGrid(graphics, mouseX, mouseY, gridX, gridY);
        renderPageControls(graphics, gridX, panelY + panelH - 28, getFilteredWeapons().size(), weaponPage);
        renderWeaponDetail(graphics, weaponDetailX(panelX), gridY, panelX + panelW - weaponDetailX(panelX) - 12, panelH - 88);
    }

    private void renderWeaponGrid(GuiGraphics graphics, int mouseX, int mouseY, int gridX, int gridY) {
        List<ShopCatalog.ShopItem> weapons = getFilteredWeapons();
        int maxPage = maxPage(weapons.size(), WEAPON_PAGE_SIZE);
        if (weaponPage > maxPage) weaponPage = maxPage;
        hoveredWeaponIndex = -1;
        int start = weaponPage * WEAPON_PAGE_SIZE;
        for (int i = 0; i < WEAPON_PAGE_SIZE; i++) {
            int index = start + i;
            int col = i % WEAPON_COLS;
            int row = i / WEAPON_COLS;
            int sx = gridX + col * SLOT;
            int sy = gridY + row * SLOT;
            boolean hovered = mouseX >= sx && mouseX < sx + 20 && mouseY >= sy && mouseY < sy + 20;
            boolean selected = index == selectedWeaponIndex;
            graphics.fill(sx, sy, sx + 20, sy + 20, selected ? 0x7744AAFF : hovered ? 0x5544AAFF : 0x55000000);
            graphics.renderOutline(sx, sy, 20, 20, selected ? 0xFFAAEEFF : 0x66336655);
            if (index >= weapons.size()) continue;
            if (hovered) hoveredWeaponIndex = index;
            ItemStack stack = previewWeaponStack(weapons.get(index));
            if (!stack.isEmpty()) {
                if (!TacZGuiIconRenderer.renderLightweightIcon(graphics, this.font, stack, sx + 2, sy + 2)) {
                    graphics.renderItem(stack, sx + 2, sy + 2);
                    graphics.renderItemDecorations(this.font, stack, sx + 2, sy + 2);
                }
            } else {
                graphics.drawCenteredString(this.font, "?", sx + 10, sy + 6, 0xFF777777);
            }
        }
    }

    private void renderWeaponDetail(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0x55000000);
        graphics.renderOutline(x, y, w, h, 0x66336655);
        List<ShopCatalog.ShopItem> weapons = getFilteredWeapons();
        if (weapons.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.tac_rogue.debug.no_item"), x + 8, y + 8, 0xFF777777, false);
            return;
        }
        int index = Math.max(0, Math.min(selectedWeaponIndex, weapons.size() - 1));
        ShopCatalog.ShopItem item = weapons.get(index);
        selectedWeaponIndex = index;

        drawWrapped(graphics, item.displayName, x + 8, y + 8, w - 16, 0xFFFFFFFF);
        graphics.drawString(this.font, trim(item.id, 34), x + 8, y + 34, 0xFF888888, false);
        graphics.drawString(this.font, categoryLabel(item.category), x + 8, y + 48, item.category.color, false);
        graphics.drawString(this.font, "$" + item.price, x + 8, y + 62, 0xFFFFD45C, false);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.shop_screen.mag_ammo",
            TacZRegistryHelper.getMagazineSize(item.id), trimId(TacZRegistryHelper.getAmmoForGun(item.id))), x + 8, y + 76, 0xFFAAFFDD, false);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.debug.rarity",
            Component.translatable("rarity.tac_rogue." + selectedRarity.name().toLowerCase(Locale.ROOT))), x + 8, y + 94, selectedRarity.color, false);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.debug.damage_mult",
            fmt(selectedRarity.damageMult)), x + 8, y + 110, 0xFFCCCCCC, false);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.debug.reload_mult",
            fmt(selectedRarity.reloadMult)), x + 94, y + 110, 0xFFCCCCCC, false);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.debug.mag_mult",
            fmt(selectedRarity.magSizeMult)), x + 8, y + 124, 0xFFCCCCCC, false);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.debug.fire_rate_mult",
            fmt(selectedRarity.fireRateMult)), x + 94, y + 124, 0xFFCCCCCC, false);
    }

    private void renderItems(GuiGraphics graphics, int mouseX, int mouseY, int panelX, int panelY, int panelW, int panelH) {
        int gridX = panelX + 18;
        int gridY = panelY + 58;
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.debug.items_title"), gridX, panelY + 34, 0xFF88CCFF, false);
        renderItemGrid(graphics, mouseX, mouseY, gridX, gridY);
        renderPageControls(graphics, gridX, panelY + panelH - 28, getFilteredDebugItems().size(), itemPage);
        int detailX = gridX + WEAPON_COLS * SLOT + 18;
        renderItemDetail(graphics, detailX, gridY, panelX + panelW - detailX - 12, panelH - 88);
    }

    private void renderItemGrid(GuiGraphics graphics, int mouseX, int mouseY, int gridX, int gridY) {
        List<DebugItem> items = getFilteredDebugItems();
        int maxPage = maxPage(items.size(), WEAPON_PAGE_SIZE);
        if (itemPage > maxPage) itemPage = maxPage;
        hoveredItemIndex = -1;
        int start = itemPage * WEAPON_PAGE_SIZE;
        for (int i = 0; i < WEAPON_PAGE_SIZE; i++) {
            int index = start + i;
            int col = i % WEAPON_COLS;
            int row = i / WEAPON_COLS;
            int sx = gridX + col * SLOT;
            int sy = gridY + row * SLOT;
            boolean hovered = mouseX >= sx && mouseX < sx + 20 && mouseY >= sy && mouseY < sy + 20;
            boolean selected = index == selectedItemIndex;
            graphics.fill(sx, sy, sx + 20, sy + 20, selected ? 0x7744AAFF : hovered ? 0x5544AAFF : 0x55000000);
            graphics.renderOutline(sx, sy, 20, 20, selected ? 0xFFAAEEFF : 0x66336655);
            if (index >= items.size()) continue;
            if (hovered) hoveredItemIndex = index;
            ItemStack stack = previewDebugItemStack(items.get(index));
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, sx + 2, sy + 2);
                graphics.renderItemDecorations(this.font, stack, sx + 2, sy + 2);
            } else {
                graphics.drawCenteredString(this.font, "?", sx + 10, sy + 6, 0xFF777777);
            }
        }
    }

    private void renderItemDetail(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0x55000000);
        graphics.renderOutline(x, y, w, h, 0x66336655);
        List<DebugItem> items = getFilteredDebugItems();
        if (items.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.tac_rogue.debug.no_item"), x + 8, y + 8, 0xFF777777, false);
            return;
        }
        int index = Math.max(0, Math.min(selectedItemIndex, items.size() - 1));
        DebugItem item = items.get(index);
        selectedItemIndex = index;
        ItemStack stack = previewDebugItemStack(item);

        graphics.drawString(this.font, stack.isEmpty() ? Component.literal(item.label()) : stack.getHoverName(), x + 8, y + 8, 0xFFFFFFFF, false);
        graphics.drawString(this.font, item.id(), x + 8, y + 26, 0xFF888888, false);
        graphics.drawString(this.font, item.group(), x + 8, y + 42, itemGroupColor(item.group()), false);
        if (!stack.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.tac_rogue.debug.item_stack", stack.getCount(), stack.getMaxStackSize()), x + 8, y + 60, 0xFFAAFFDD, false);
            graphics.renderItem(stack, x + 8, y + 82);
            graphics.renderItemDecorations(this.font, stack, x + 8, y + 82);
        }
    }

    private void renderPerks(GuiGraphics graphics, int panelX, int panelY, int panelW, int panelH) {
        renderCategoryPanel(graphics, panelX + 10, categoryListTop(panelY) - 2, PERK_CATEGORY_W + 2,
            visibleCategoryRows(panelH), perkCategoryScroll,
            Math.max(0, PERK_CATEGORIES.length - visibleCategoryRows(panelH)));
        if (previewPerk == null) return;

        int x = panelX + 12 + PERK_CATEGORY_W + 18;
        int y = panelY + 58;
        int detailW = panelX + panelW - x - 14;
        graphics.fill(x, y, x + detailW, y + 112, 0x55000000);
        graphics.renderOutline(x, y, detailW, 112, 0x66336655);
        graphics.drawCenteredString(this.font, previewPerk.getDisplayName(), x + detailW / 2, y + 10, previewPerk.getRarityColor());
        graphics.drawCenteredString(this.font, previewPerk.getDescriptionComponent(), x + detailW / 2, y + 28, 0xFFDDDDDD);
        graphics.drawCenteredString(this.font, previewPerk.getModifierDescriptionComponent().getString(), x + detailW / 2, y + 68, previewPerk.modifier.color);
        graphics.drawCenteredString(this.font, Component.translatable("gui.tac_rogue.debug.tag", previewPerk.toTag()), x + detailW / 2, y + 90, 0xFF888888);
        graphics.drawCenteredString(this.font, previewPerk.modifier.name() + " / Lv." + perkLevel, x + detailW / 2, y + 50, 0xFFFFFFFF);
        int selectedCount = countSelectedPerk(previewPerk);
        int totalCount = ClientRunState.getPerkTags().size();
        graphics.drawCenteredString(this.font,
            Component.literal("Owned selected: " + selectedCount + " / total: " + totalCount),
            x + detailW / 2, y + 104, selectedCount > 0 ? 0xFFAAFFDD : 0xFFFFCC66);
    }

    private int countSelectedPerk(PerkDefinition target) {
        if (target == null) return 0;
        int count = 0;
        for (String tag : ClientRunState.getPerkTags()) {
            if (!tag.startsWith("perk:")) continue;
            PerkDefinition perk = PerkDefinition.fromTag(tag);
            if (perk.category == target.category && perk.modifier == target.modifier && perk.level == target.level) {
                count++;
            }
        }
        return count;
    }

    private void renderQuests(GuiGraphics graphics, int panelX, int panelY, int panelW, int panelH) {
        renderCategoryPanel(graphics, panelX + 10, categoryListTop(panelY) - 2, PERK_CATEGORY_W + 2,
            visibleCategoryRows(panelH), questTypeScroll,
            Math.max(0, QuestManager.QuestType.values().length - visibleCategoryRows(panelH)));

        int x = panelX + 12 + PERK_CATEGORY_W + 18;
        int y = panelY + 58;
        int w = panelX + panelW - x - 14;
        QuestManager.QuestType type = QuestManager.QuestType.values()[questTypeIndex];
        graphics.fill(x, y, x + w, y + 64, 0x55000000);
        graphics.renderOutline(x, y, w, 64, 0x66336655);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.debug.quest_type"), x + 8, y + 8, 0xFF88CCFF, false);
        graphics.drawString(this.font, Component.translatable(type.langKey), x + 8, y + 24, type.color, false);
        graphics.drawString(this.font, type.name(), x + 8, y + 38, 0xFF888888, false);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.debug.amount", questAmount), x + 8, y + 78, 0xFFFFFFFF, false);
    }

    private void renderStateInfo(GuiGraphics graphics, int panelX, int panelY, int panelW, int panelH) {
        int x = panelX + 18;
        int y = panelY + 46;
        int rowW = panelW - 36;
        List<Component> rows = List.of(
            Component.translatable("gui.tac_rogue.debug.state.gold", ClientRunState.getGold()),
            Component.translatable("gui.tac_rogue.debug.state.floor", ClientRunState.getFloor()),
            Component.translatable("gui.tac_rogue.debug.state.max_floor", ClientRunState.getMaxReachedFloor()),
            Component.translatable("gui.tac_rogue.debug.state.theme", ClientRunState.getThemeName()),
            Component.translatable("gui.tac_rogue.debug.state.run_active", ClientRunState.isRunActive()),
            Component.translatable("gui.tac_rogue.debug.state.floor_cleared", ClientRunState.isFloorCleared()),
            Component.translatable("gui.tac_rogue.debug.state.difficulty", DifficultyManager.getDifficulty().displayName),
            Component.translatable("gui.tac_rogue.debug.state.flashlight", ClientRunState.getFlashlightLevel()),
            Component.translatable("gui.tac_rogue.debug.state.perks", ClientRunState.getPerkTags().size()),
            Component.translatable("gui.tac_rogue.debug.state.upgrades",
                ClientRunState.getAmmoCapacityLevel(), ClientRunState.getInventoryLevel(), ClientRunState.getMeleeLevel()),
            Component.translatable("gui.tac_rogue.debug.state.deep",
                ClientRunState.getDeepCore(), ClientRunState.getPrestigeLevel(), ClientRunState.getCurrentDeepBand()),
            Component.translatable("gui.tac_rogue.debug.state.ai_overlay", DebugAiOverlayManager.isEnabled() ? "ON" : "OFF")
        );
        for (int i = 0; i < rows.size(); i++) {
            int ry = y + i * 16;
            graphics.fill(x, ry, x + rowW, ry + 14, i % 2 == 0 ? 0x44224466 : 0x33112233);
            graphics.drawString(this.font, rows.get(i), x + 8, ry + 3, 0xFFFFFFFF, false);
        }
    }

    private void renderMultiplayer(GuiGraphics graphics, int panelX, int panelY, int panelW, int panelH) {
        int x = panelX + 18;
        int y = panelY + 46;
        int rowW = panelW - 36;
        graphics.fill(x, y, x + rowW, y + 42, 0x55224466);
        graphics.renderOutline(x, y, rowW, 42, 0x66336655);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.debug.mp_title"), x + 8, y + 8, 0xFF88CCFF, false);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.debug.mp_party_value", mpPartySize), x + 8, y + 24, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.debug.mp_virtual_value", mpVirtualJoinCount), x + Math.max(190, rowW / 2), y + 24, 0xFFFFFFFF, false);
    }

    private void addButtonGrid(int x, int y, int width, int columns, List<DebugButtonSpec> buttons) {
        int gap = 6;
        int cols = Math.max(1, columns);
        int buttonW = Math.max(72, (width - gap * (cols - 1)) / cols);
        int buttonH = 18;
        int rowH = 22;
        for (int i = 0; i < buttons.size(); i++) {
            DebugButtonSpec spec = buttons.get(i);
            int col = i % cols;
            int row = i / cols;
            this.addRenderableWidget(Button.builder(spec.label(), spec.onPress())
                .bounds(x + col * (buttonW + gap), y + row * rowH, buttonW, buttonH)
                .build());
        }
    }

    private record DebugButtonSpec(Component label, Button.OnPress onPress) {}

    private void renderCategoryPanel(GuiGraphics graphics, int x, int y, int w, int visibleRows, int scroll, int maxScroll) {
        int h = visibleRows * CATEGORY_ROW_H + 2;
        graphics.fill(x, y, x + w, y + h, 0x55000000);
        graphics.renderOutline(x, y, w, h, 0x66336655);
        if (scroll > 0) {
            graphics.drawString(this.font, "^", x + w - 10, y - 10, 0xFF88FFCC, false);
        }
        if (scroll < maxScroll) {
            graphics.drawString(this.font, "v", x + w - 10, y + h + 2, 0xFF88FFCC, false);
        }
    }

    private void renderPageControls(GuiGraphics graphics, int x, int y, int total, int page) {
        int max = maxPage(total, WEAPON_PAGE_SIZE);
        graphics.fill(x, y, x + 40, y + 18, page > 0 ? 0x6644AAFF : 0x33222222);
        graphics.fill(x + 54, y, x + 94, y + 18, page < max ? 0x6644AAFF : 0x33222222);
        graphics.renderOutline(x, y, 40, 18, 0x66336655);
        graphics.renderOutline(x + 54, y, 40, 18, 0x66336655);
        graphics.drawCenteredString(this.font, "<", x + 20, y + 5, 0xFFCCCCCC);
        graphics.drawCenteredString(this.font, ">", x + 74, y + 5, 0xFFCCCCCC);
        graphics.drawString(this.font, (page + 1) + "/" + (max + 1), x + 104, y + 5, 0xFF888888, false);
    }

    private void renderWeaponTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoveredWeaponIndex < 0) return;
        List<ShopCatalog.ShopItem> weapons = getFilteredWeapons();
        if (hoveredWeaponIndex >= weapons.size()) return;
        ItemStack stack = previewWeaponStack(weapons.get(hoveredWeaponIndex));
        if (!stack.isEmpty()) {
            graphics.renderTooltip(this.font, stack, mouseX, mouseY);
        }
    }

    private void renderItemTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoveredItemIndex < 0) return;
        List<DebugItem> items = getFilteredDebugItems();
        if (hoveredItemIndex >= items.size()) return;
        ItemStack stack = previewDebugItemStack(items.get(hoveredItemIndex));
        if (!stack.isEmpty()) {
            graphics.renderTooltip(this.font, stack, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab == Tab.WEAPONS) {
            int panelX = panelX();
            int panelY = panelY();
            int panelH = panelH();
            int gridX = weaponGridX(panelX);
            int gridY = panelY + 58;
            int gridW = WEAPON_COLS * SLOT;
            int gridH = WEAPON_ROWS * SLOT;
            if (mouseX >= gridX && mouseX < gridX + gridW && mouseY >= gridY && mouseY < gridY + gridH) {
                int col = ((int) mouseX - gridX) / SLOT;
                int row = ((int) mouseY - gridY) / SLOT;
                int index = weaponPage * WEAPON_PAGE_SIZE + row * WEAPON_COLS + col;
                if (index < getFilteredWeapons().size()) {
                    selectedWeaponIndex = index;
                    return true;
                }
            }
            int pageY = panelY + panelH - 28;
            if (mouseX >= gridX && mouseX < gridX + 40 && mouseY >= pageY && mouseY < pageY + 18) {
                if (weaponPage > 0) weaponPage--;
                return true;
            }
            if (mouseX >= gridX + 54 && mouseX < gridX + 94 && mouseY >= pageY && mouseY < pageY + 18) {
                int max = maxPage(getFilteredWeapons().size(), WEAPON_PAGE_SIZE);
                if (weaponPage < max) weaponPage++;
                return true;
            }
        }
        if (tab == Tab.ITEMS) {
            int panelX = panelX();
            int panelY = panelY();
            int panelH = panelH();
            int gridX = panelX + 18;
            int gridY = panelY + 58;
            int gridW = WEAPON_COLS * SLOT;
            int gridH = WEAPON_ROWS * SLOT;
            if (mouseX >= gridX && mouseX < gridX + gridW && mouseY >= gridY && mouseY < gridY + gridH) {
                int col = ((int) mouseX - gridX) / SLOT;
                int row = ((int) mouseY - gridY) / SLOT;
                int index = itemPage * WEAPON_PAGE_SIZE + row * WEAPON_COLS + col;
                if (index < getFilteredDebugItems().size()) {
                    selectedItemIndex = index;
                    return true;
                }
            }
            int pageY = panelY + panelH - 28;
            if (mouseX >= gridX && mouseX < gridX + 40 && mouseY >= pageY && mouseY < pageY + 18) {
                if (itemPage > 0) itemPage--;
                return true;
            }
            if (mouseX >= gridX + 54 && mouseX < gridX + 94 && mouseY >= pageY && mouseY < pageY + 18) {
                int max = maxPage(getFilteredDebugItems().size(), WEAPON_PAGE_SIZE);
                if (itemPage < max) itemPage++;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int panelX = panelX();
        int panelY = panelY();
        int panelH = panelH();
        if (tab == Tab.WEAPONS) {
            if (isInList(mouseX, mouseY, panelX + 10, categoryListTop(panelY) - 2, CATEGORY_W + 2, visibleCategoryRows(panelH))) {
                int maxScroll = Math.max(0, WEAPON_CATEGORIES.length - visibleCategoryRows(panelH));
                weaponCategoryScroll = scrollIndex(weaponCategoryScroll, maxScroll, delta);
                rebuild();
                return true;
            }
            int max = maxPage(getFilteredWeapons().size(), WEAPON_PAGE_SIZE);
            weaponPage = scrollIndex(weaponPage, max, delta);
            return true;
        }
        if (tab == Tab.ITEMS) {
            int max = maxPage(getFilteredDebugItems().size(), WEAPON_PAGE_SIZE);
            itemPage = scrollIndex(itemPage, max, delta);
            return true;
        }
        if (tab == Tab.PERKS) {
            if (isInList(mouseX, mouseY, panelX + 10, categoryListTop(panelY) - 2, PERK_CATEGORY_W + 2, visibleCategoryRows(panelH))) {
                int maxScroll = Math.max(0, PERK_CATEGORIES.length - visibleCategoryRows(panelH));
                perkCategoryScroll = scrollIndex(perkCategoryScroll, maxScroll, delta);
                rebuild();
                return true;
            }
        }
        if (tab == Tab.QUESTS) {
            if (isInList(mouseX, mouseY, panelX + 10, categoryListTop(panelY) - 2, PERK_CATEGORY_W + 2, visibleCategoryRows(panelH))) {
                int maxScroll = Math.max(0, QuestManager.QuestType.values().length - visibleCategoryRows(panelH));
                questTypeScroll = scrollIndex(questTypeScroll, maxScroll, delta);
                rebuild();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private ItemStack previewWeaponStack(ShopCatalog.ShopItem item) {
        if (cachedPreviewRarity != selectedRarity) {
            cachedPreviewRarity = selectedRarity;
            previewWeaponStackCache.clear();
        }
        String fullId = item.id.contains(":") ? item.id : "tacz:" + item.id;
        return previewWeaponStackCache.computeIfAbsent(fullId,
            id -> RogueItemFactory.createGunStack(id, selectedRarity));
    }

    private ItemStack previewDebugItemStack(DebugItem item) {
        return previewDebugItemStackCache.computeIfAbsent(item.id(), id -> RogueItemFactory.createItemStack(null, id));
    }

    private int itemGroupColor(String group) {
        return switch (group) {
            case "Recovery" -> 0xFF80FFAA;
            case "Tactical" -> 0xFFFFD45C;
            case "Passive" -> 0xFF88CCFF;
            case "Risk" -> 0xFFFF8866;
            case "Currency" -> 0xFFFFE680;
            default -> 0xFFCCCCCC;
        };
    }

    private int panelW() {
        return Math.min(720, Math.max(300, this.width - 24));
    }

    private int panelH() {
        return Math.min(330, Math.max(200, this.height - 24));
    }

    private int panelX() {
        return (this.width - panelW()) / 2;
    }

    private int panelY() {
        return (this.height - panelH()) / 2;
    }

    private int categoryListTop(int panelY) {
        return panelY + 58;
    }

    private int weaponGridX(int panelX) {
        return panelX + 12 + CATEGORY_W + 14;
    }

    private int weaponDetailX(int panelX) {
        return weaponGridX(panelX) + WEAPON_COLS * SLOT + 16;
    }

    private int visibleCategoryRows(int panelH) {
        return Math.max(3, Math.min(11, (panelH - 84) / CATEGORY_ROW_H));
    }

    private boolean isInList(double mouseX, double mouseY, int x, int y, int w, int rows) {
        int h = rows * CATEGORY_ROW_H + 2;
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private int scrollIndex(int value, int max, double delta) {
        if (delta < 0) return Math.min(max, value + 1);
        if (delta > 0) return Math.max(0, value - 1);
        return value;
    }

    private int maxPage(int total, int pageSize) {
        return total <= 0 ? 0 : (total - 1) / pageSize;
    }

    private void drawWrapped(GuiGraphics graphics, String text, int x, int y, int width, int color) {
        for (var line : this.font.split(Component.literal(text), width)) {
            graphics.drawString(this.font, line, x, y, color, false);
            y += 10;
        }
    }

    private Component categoryLabel(ShopCatalog.Category category) {
        return Component.translatable("gui.tac_rogue.shop_screen.category." + category.name().toLowerCase(Locale.ROOT));
    }

    private String trimId(String id) {
        if (id == null) return "";
        String value = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return value.length() > 16 ? value.substring(0, 14) + ".." : value;
    }

    private static String trim(String value, int max) {
        if (value.length() <= max) return value;
        return value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private static String fmt(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private record DebugItem(String id, String label, String group) {}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
