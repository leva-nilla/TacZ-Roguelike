package com.levanilla.rogue.core.registry;


import com.levanilla.rogue.core.PriceManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Shop category definitions and built-in item catalog.
 * v0.4.0: All prices rebalanced for 70 mobs/floor economy. Currency: $.
 */
public final class ShopCatalog {

    private ShopCatalog() {}

    /** Shop categories */
    public enum Category {
        PISTOL("PISTOL", 0xFFAACC00),
        RIFLE("RIFLE", 0xFF00AAFF),
        SMG("SMG", 0xFF00FFAA),
        SHOTGUN("SHOTGUN", 0xFFFF6600),
        SNIPER("SNIPER", 0xFFFF0066),
        LMG("LMG", 0xFFFFAA00),
        EXPLOSIVE("EXPLOSIVE", 0xFFFF4400),
        MELEE("MELEE", 0xFFFF3333),
        TACTICAL("TACTICAL", 0xFFFF8800),
        ATTACHMENT("ATTACHMENT", 0xFF8800FF),
        AMMO("AMMO", 0xFF888888),
        SPECIAL("SPECIAL", 0xFFFFFF00);

        public final String label;
        public final int color;
        Category(String label, int color) { this.label = label; this.color = color; }

        public boolean isWeapon() {
            return this == PISTOL || this == RIFLE || this == SMG
                || this == SHOTGUN || this == SNIPER || this == LMG
                || this == EXPLOSIVE || this == MELEE || this == TACTICAL;
        }
    }

    /** Shop item data */
    public static class ShopItem {
        public final String id;
        public final String displayName;
        public final int price;
        public final Category category;

        public ShopItem(String id, String displayName, int price, Category category) {
            this.id = id;
            this.displayName = displayName;
            this.price = price;
            this.category = category;
        }
    }

    // ========== Built-in Item List (v0.4.0 pricing) ==========

    public static List<ShopItem> getBuiltinGuns() {
        List<ShopItem> items = new ArrayList<>();
        List<TacZGunRegistry.GunProfile> profiles = TacZGunRegistry.getAllGuns();
        
        for (TacZGunRegistry.GunProfile profile : profiles) {
            items.add(new ShopItem(
                profile.id.toString(),
                profile.displayName,
                profile.autoPrice,
                profile.category
            ));
        }
        return items;
    }

    public static List<ShopItem> getBuiltinAttachments() {
        List<ShopItem> items = new ArrayList<>();
        List<String> attachmentIds = TacZGunRegistry.getAllAttachmentIds();
        if (attachmentIds != null) {
            for (String id : attachmentIds) {
                String name = extractName(id).toUpperCase();
                items.add(new ShopItem(id, name, PriceManager.getAttachmentBuyPrice(id), Category.ATTACHMENT));
            }
        }
        return items;
    }

    public static List<ShopItem> getBuiltinAmmo() {
        List<ShopItem> items = new ArrayList<>();
        java.util.Set<String> usedAmmo = new java.util.HashSet<>();
        
        List<TacZGunRegistry.GunProfile> profiles = TacZGunRegistry.getAllGuns();
        if (profiles != null) {
            for (TacZGunRegistry.GunProfile profile : profiles) {
                if (profile.ammoId != null) {
                    usedAmmo.add(profile.ammoId.toString());
                }
            }
        }
        
        for (String id : usedAmmo) {
            String name = extractName(id).toUpperCase() + " AMMO";
            int basePrice = 150;
            if (name.contains("50 BMG") || name.contains("338")) basePrice = 300;
            else if (name.contains("RPG") || name.contains("40MM")) basePrice = 500;
            
            items.add(new ShopItem(id, name, basePrice, Category.AMMO));
        }
        
        return items;
    }

    /**
     * 近接武器: lrtactical API から動的取得。
     * lrtactical 未インストール時は空リスト。
     */
    public static List<ShopItem> getBuiltinMelee() {
        return LrTacticalRegistry.getAllMeleeWeapons();
    }

    /**
     * 投擲武器: lrtactical API から動的取得。
     * lrtactical 未インストール時は空リスト。
     */
    public static List<ShopItem> getBuiltinTactical() {
        return LrTacticalRegistry.getAllThrowables();
    }

    public static List<ShopItem> getBuiltinSpecial() {
        List<ShopItem> items = new ArrayList<>();
        items.add(new ShopItem("rogue:inv_upgrade", "\u2191 INVENTORY +2 SLOTS", 3000, Category.SPECIAL));
        items.add(new ShopItem("rogue:stash_upgrade", "\u2191 STASH +1 ROW", 3000, Category.SPECIAL));
        items.add(new ShopItem("rogue:ammo_capacity_upgrade", "\u2191 AMMO POUCH Lvl UP", 2000, Category.SPECIAL));
        items.add(new ShopItem("rogue:melee_upgrade", "\u2191 MELEE WEAPON Lvl UP", 1500, Category.SPECIAL));
        items.add(new ShopItem("rogue:flashlight_upgrade", "\u2191 FLASHLIGHT Lvl UP", 900, Category.SPECIAL));
        // --- Recovery Items ---
        items.add(new ShopItem("rogue:medkit", "\u2726 MEDKIT", 420, Category.SPECIAL));
        items.add(new ShopItem("rogue:field_ration", "\u2726 FIELD RATION x3", 180, Category.SPECIAL));
        items.add(new ShopItem("rogue:stamina_shot", "\u2726 STAMINA SHOT", 320, Category.SPECIAL));
        // --- Tactical Items ---
        items.add(new ShopItem("minecraft:snowball", "SNOWBALL x16", 120, Category.SPECIAL));
        // --- New Consumables ---
        items.add(new ShopItem("rogue:bandage", "\u2726 BANDAGE", 220, Category.SPECIAL));
        items.add(new ShopItem("rogue:armor_plate", "\u2726 ARMOR PLATE", 520, Category.SPECIAL));
        items.add(new ShopItem("rogue:adrenaline", "\u2726 ADRENALINE SYRINGE", 700, Category.SPECIAL));
        items.add(new ShopItem("rogue:emp_device", "\u2726 EMP DEVICE", 450, Category.SPECIAL));
        // --- Carry Utilities ---
        items.add(new ShopItem("rogue:ballistic_charm", "\u25C6 BALLISTIC CHARM", 850, Category.SPECIAL));
        items.add(new ShopItem("rogue:quickdraw_charm", "\u25C6 QUICKDRAW CHARM", 900, Category.SPECIAL));
        items.add(new ShopItem("rogue:ammo_saver_charm", "\u25C6 AMMO SAVER CHARM", 950, Category.SPECIAL));
        items.add(new ShopItem("rogue:ballistic_insert", "\u25C6 BALLISTIC INSERT", 1800, Category.SPECIAL));
        items.add(new ShopItem("rogue:mag_pouch_rig", "\u25C6 MAG POUCH RIG", 1600, Category.SPECIAL));
        items.add(new ShopItem("rogue:terminal_decoder", "\u25C6 TERMINAL DECODER", 1100, Category.SPECIAL));
        items.add(new ShopItem("rogue:recovery_beacon", "\u25C6 RECOVERY BEACON", 1250, Category.SPECIAL));
        items.add(new ShopItem("rogue:defense_sensor", "\u25C6 DEFENSE SENSOR", 1250, Category.SPECIAL));
        items.add(new ShopItem("rogue:blood_dogtag", "\u25C6 BLOOD DOGTAG", 1500, Category.SPECIAL));
        items.add(new ShopItem("rogue:overheat_core", "\u25C6 OVERHEAT CORE", 1700, Category.SPECIAL));
        items.add(new ShopItem("rogue:maintenance_kit", "\u25C6 MAINTENANCE KIT", 1050, Category.SPECIAL));
        items.add(new ShopItem("rogue:ballistic_computer", "\u25C6 BALLISTIC COMPUTER", 1350, Category.SPECIAL));
        items.add(new ShopItem("rogue:range_card", "\u25C6 RANGE CARD", 800, Category.SPECIAL));
        // --- Tactical Utilities ---
        items.add(new ShopItem("rogue:smoke_canister", "\u2726 SMOKE CANISTER", 360, Category.SPECIAL));
        items.add(new ShopItem("rogue:flash_charge", "\u2726 FLASH CHARGE", 420, Category.SPECIAL));
        items.add(new ShopItem("rogue:noise_maker", "\u2726 NOISE MAKER", 260, Category.SPECIAL));
        items.add(new ShopItem("rogue:portable_shield", "\u2726 PORTABLE SHIELD", 620, Category.SPECIAL));
        items.add(new ShopItem("rogue:micro_turret", "\u2726 MICRO TURRET", 1400, Category.SPECIAL));
        // --- Random Perk ---
        items.add(new ShopItem("rogue:random_perk", "\u2605 RANDOM PERK", 1500, Category.SPECIAL));
        return items;
    }

    // ========== Utilities ==========

    /** Extract human-readable name from ID */
    public static String extractName(String id) {
        String name = id.contains(":") ? id.split(":")[1] : id;
        if (name.contains("/")) {
            name = name.substring(name.lastIndexOf('/') + 1);
        }
        return name.replace("_", " ");
    }

    /** Guess category from name */
    public static Category guessCategory(String name) {
        String lower = name.toLowerCase();
        if (lower.contains("knife") || lower.contains("karambit") || lower.contains("bayonet") ||
            lower.contains("machete") || lower.contains("crowbar") || lower.contains("bat"))
            return Category.MELEE;
        if (lower.contains("scope") || lower.contains("sight") || lower.contains("grip") ||
            lower.contains("suppressor") || lower.contains("mag") || lower.contains("stock") ||
            lower.contains("laser") || lower.contains("flash"))
            return Category.ATTACHMENT;
        if (lower.contains("rpg") || lower.contains("m320") || lower.contains("launcher"))
            return Category.EXPLOSIVE;
        if (lower.contains("awp") || lower.contains("svd") || lower.contains("kar") ||
            lower.contains("scout") || lower.contains("sniper") || lower.contains("m24") ||
            lower.contains("mosin"))
            return Category.SNIPER;
        if (lower.contains("m249") || lower.contains("rpk") || lower.contains("m60") ||
            lower.contains("negev") || lower.contains("lmg"))
            return Category.LMG;
        if (lower.contains("870") || lower.contains("spas") || lower.contains("aa_12") ||
            lower.contains("shotgun") || lower.contains("nova") || lower.contains("mag_7"))
            return Category.SHOTGUN;
        if (lower.contains("mp") || lower.contains("ump") || lower.contains("p90") ||
            lower.contains("mac") || lower.contains("vector") || lower.contains("pp_19") ||
            lower.contains("smg"))
            return Category.SMG;
        if (lower.contains("glock") || lower.contains("1911") || lower.contains("usp") ||
            lower.contains("deagle") || lower.contains("desert") || lower.contains("m500") ||
            lower.contains("cz75") || lower.contains("five_seven") || lower.contains("tec"))
            return Category.PISTOL;
        return Category.RIFLE;
    }

    /** Get attachment type description */
    public static String getAttachmentTypeDesc(String attachId) {
        String lower = attachId.toLowerCase();
        if (lower.contains("sight"))  return "§7[SIGHT] Red dot / holographic sight.";
        if (lower.contains("scope"))  return "§7[SCOPE] Magnified optic for medium-long range.";
        if (lower.contains("silencer") || lower.contains("suppressor"))
            return "§7[MUZZLE] Suppressor. Reduces sound and flash.";
        if (lower.contains("compensator"))
            return "§7[MUZZLE] Compensator. Reduces vertical recoil.";
        if (lower.contains("brake"))  return "§7[MUZZLE] Muzzle brake. Reduces horizontal recoil.";
        if (lower.contains("grip"))   return "§7[GRIP] Foregrip. Improves recoil control.";
        if (lower.contains("laser"))  return "§7[LASER] Laser sight. Improves hipfire accuracy.";
        if (lower.contains("ext") && lower.contains("mag"))
            return "§7[MAGAZINE] Extended magazine. Increases ammo capacity.";
        if (lower.contains("stock"))  return "§7[STOCK] Stock. Improves stability.";
        if (lower.contains("bayonet")) return "§7[BAYONET] Bayonet. Adds melee damage.";
        return "§7[ATTACHMENT] Weapon attachment.";
    }
}
