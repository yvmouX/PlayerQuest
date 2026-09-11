package com.playerPlugin.playerTaskX.core.web;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 可供编辑器选择的材料（物品/方块）与实体清单。
 *
 * <h2>版本适配靠运行期枚举</h2>
 * 素材直接来自服务端自己的 {@link Material} 枚举，因此<b>天然只包含当前版本支持的项</b>——
 * 1.21.11 的服务端给出的就是 1.21.11 的清单，不需要维护「哪个物品是哪个版本加的」这类数据。
 *
 * <h2>英文名来自服务端语言文件</h2>
 * 枚举名（{@code DIAMOND_ORE}）对使用者不友好，因此优先读服务端 jar 里的
 * {@code assets/minecraft/lang/en_us.json} 拿显示名（{@code Diamond Pickaxe}）。
 * 取不到时退回把枚举名转成「单词首字母大写」的形式，不影响使用。
 *
 * <h2>中文名是内置的精选映射</h2>
 * 中文译名只存在于客户端资源里，服务端 jar 没有 {@code zh_cn.json}，插件也无从获取。
 * 因此这里内置一份<b>精选映射</b>，覆盖矿石、木材、作物、生物掉落、工具、食物等任务里常用的项；
 * 未收录的项回退英文名。搜索在中文与英文两个字段上同时进行，因此即使没有中文名也能按英文搜到。
 */
public final class MaterialCatalog {

    /**
     * 精选中文译名。
     * <p>
     * key 为 Material 枚举名。只收录常见项：任务目标里出现的材质九成落在这个范围内，
     * 而把 1600 多项全部翻译既无必要也无法保证准确（中文译名属客户端资源，需人工维护）。
     */
    private static final Map<String, String> CHINESE_NAMES = buildChineseNames();

    /** 服务端语言文件里的显示名，按小写 key 索引（如 {@code diamond_ore} → {@code Diamond Ore}）。 */
    private static volatile Map<String, String> serverDisplayNames;

    /** 分类展示顺序，前端按此顺序排列分组。 */
    private static final List<String> CATEGORY_ORDER = List.of("block", "item", "food");

    private MaterialCatalog() {
    }

    /**
     * 构建供前端使用的清单。
     * <p>
     * 一次性返回全部条目（约 1000+ 条，压缩后几十 KB）：编辑器在本地做搜索与筛选，
     * 输入时不再发请求，中文搜索也能即时响应。
     */
    public static Map<String, Object> build() {
        Map<String, String> displayNames = serverDisplayNames();

        List<Map<String, Object>> materials = new ArrayList<Map<String, Object>>();
        for (Material material : Material.values()) {
            if (!material.isItem()) {
                // 不是物品的材质（如 AIR、纯方块状态）不能作为任务目标
                continue;
            }
            Map<String, Object> entry = new java.util.LinkedHashMap<String, Object>();
            String id = material.name();
            String english = displayName(displayNames, id, material);
            entry.put("id", id);
            entry.put("en", english);
            entry.put("zh", CHINESE_NAMES.getOrDefault(id, ""));
            entry.put("category", categoryOf(material));
            materials.add(entry);
        }
        materials.sort(Comparator.comparing(entry -> String.valueOf(entry.get("id"))));

        List<Map<String, Object>> entities = new ArrayList<Map<String, Object>>();
        for (EntityType type : EntityType.values()) {
            if (type == EntityType.UNKNOWN) {
                // 纯技术实体，永远不会出现在任务里
                continue;
            }
            Map<String, Object> entry = new java.util.LinkedHashMap<String, Object>();
            String id = type.name();
            entry.put("id", id);
            entry.put("en", pretty(id));
            entry.put("zh", CHINESE_NAMES.getOrDefault(id, ""));
            entities.add(entry);
        }
        entities.sort(Comparator.comparing(entry -> String.valueOf(entry.get("id"))));

        Map<String, Object> result = new java.util.LinkedHashMap<String, Object>();
        result.put("materials", materials);
        result.put("entities", entities);
        result.put("categories", CATEGORY_ORDER);
        String version = Bukkit.getBukkitVersion();
        result.put("serverVersion", version == null ? "" : version);
        return result;
    }

    /** 英文显示名：优先服务端语言文件，其次枚举名的可读化形式。 */
    private static String displayName(Map<String, String> displayNames, String id, Material material) {
        String fromLang = displayNames.get(id.toLowerCase(Locale.ROOT));
        if (fromLang != null && !fromLang.isBlank()) {
            return fromLang;
        }
        return pretty(id);
    }

    /** 把枚举名转成可读形式：{@code DIAMOND_ORE} → {@code Diamond Ore}。（包级可见以便测试） */
    static String pretty(String enumName) {
        if (enumName == null || enumName.isEmpty()) {
            return "";
        }
        String[] parts = enumName.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    /**
     * 分类，用于前端分组展示（缺省只按「方块 / 物品 / 其它」分，够用且不依赖人工维护）。
     */
    private static String categoryOf(Material material) {
        if (material.isBlock()) {
            return "block";
        }
        if (material.isEdible()) {
            return "food";
        }
        return "item";
    }

    /**
     * 解析服务端 jar 内的 {@code en_us.json}。
     * <p>
     * 只需 {@code item.minecraft.*} 与 {@code block.minecraft.*} 两类键；
     * 解析失败（非标准服务端、资源被裁剪）时返回空表，调用方退回枚举名，不影响功能。
     */
    private static Map<String, String> serverDisplayNames() {
        Map<String, String> cached = serverDisplayNames;
        if (cached != null) {
            return cached;
        }
        Map<String, String> names = new HashMap<String, String>();
        try (InputStream stream = MaterialCatalog.class.getResourceAsStream("/assets/minecraft/lang/en_us.json")) {
            if (stream != null) {
                parseLangFile(stream, names);
            }
        } catch (Exception ignored) {
            // 语言文件不可用：退回枚举名
        }
        serverDisplayNames = names;
        return names;
    }

    /** 极简的 JSON 对象解析：只认 {@code "key": "value"} 形式，足够处理语言文件。（包级可见以便测试） */
    static void parseLangFile(InputStream stream, Map<String, String> target) throws Exception {
        StringBuilder text = new StringBuilder();
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) > 0) {
                text.append(buffer, 0, read);
            }
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"(item|block)\\.minecraft\\.([a-z_0-9]+)\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(text);
        while (matcher.find()) {
            String key = matcher.group(2);
            String value = matcher.group(3);
            // 后出现的覆盖先出现的：语言文件里 item 与 block 可能同名，item 优先更符合直觉
            if (!target.containsKey(key) || "item".equals(matcher.group(1))) {
                target.put(key, value.replace("\\u0020", " "));
            }
        }
    }

    /** 精选中文译名表。 */
    private static Map<String, String> buildChineseNames() {
        Map<String, String> names = new HashMap<String, String>();

        // ---------- 矿石与矿物 ----------
        put(names, "COAL_ORE", "煤矿石", "DEEPSLATE_COAL_ORE", "深层煤矿石");
        put(names, "IRON_ORE", "铁矿石", "DEEPSLATE_IRON_ORE", "深层铁矿石");
        put(names, "COPPER_ORE", "铜矿石", "DEEPSLATE_COPPER_ORE", "深层铜矿石");
        put(names, "GOLD_ORE", "金矿石", "DEEPSLATE_GOLD_ORE", "深层金矿石");
        put(names, "REDSTONE_ORE", "红石矿石", "DEEPSLATE_REDSTONE_ORE", "深层红石矿石");
        put(names, "LAPIS_ORE", "青金石矿石", "DEEPSLATE_LAPIS_ORE", "深层青金石矿石");
        put(names, "DIAMOND_ORE", "钻石矿石", "DEEPSLATE_DIAMOND_ORE", "深层钻石矿石");
        put(names, "EMERALD_ORE", "绿宝石矿石", "DEEPSLATE_EMERALD_ORE", "深层绿宝石矿石");
        put(names, "NETHER_GOLD_ORE", "下界金矿石", "NETHER_QUARTZ_ORE", "下界石英矿石");
        put(names, "ANCIENT_DEBRIS", "远古残骸");
        put(names, "COAL", "煤炭", "CHARCOAL", "木炭");
        put(names, "RAW_IRON", "粗铁", "RAW_COPPER", "粗铜", "RAW_GOLD", "粗金");
        put(names, "IRON_INGOT", "铁锭", "COPPER_INGOT", "铜锭", "GOLD_INGOT", "金锭");
        put(names, "DIAMOND", "钻石", "EMERALD", "绿宝石", "LAPIS_LAZULI", "青金石");
        put(names, "REDSTONE", "红石", "NETHERITE_INGOT", "下界合金锭");
        put(names, "NETHERITE_SCRAP", "下界合金碎片", "QUARTZ", "下界石英");
        put(names, "AMETHYST_SHARD", "紫水晶碎片", "FLINT", "燧石");

        // ---------- 石块与建材 ----------
        put(names, "STONE", "石头", "COBBLESTONE", "圆石");
        put(names, "DEEPSLATE", "深板岩", "COBBLED_DEEPSLATE", "深板岩圆石");
        put(names, "GRANITE", "花岗岩", "DIORITE", "闪长岩", "ANDESITE", "安山岩");
        put(names, "DIRT", "泥土", "GRASS_BLOCK", "草方块", "PODZOL", "灰化土");
        put(names, "SAND", "沙子", "RED_SAND", "红沙", "GRAVEL", "砂砾");
        put(names, "CLAY", "黏土", "CLAY_BALL", "黏土球");
        put(names, "SANDSTONE", "砂岩", "RED_SANDSTONE", "红砂岩");
        put(names, "OBSIDIAN", "黑曜石", "CRYING_OBSIDIAN", "哭泣的黑曜石");
        put(names, "BEDROCK", "基岩", "NETHERRACK", "下界岩", "END_STONE", "末地石");
        put(names, "GLASS", "玻璃", "GLASS_PANE", "玻璃板");
        put(names, "BRICKS", "砖块", "BRICK", "红砖");
        put(names, "SNOW", "雪", "SNOW_BLOCK", "雪块", "ICE", "冰", "PACKED_ICE", "浮冰");
        put(names, "BLUE_ICE", "蓝冰", "MOSS_BLOCK", "苔藓块", "CALCITE", "方解石");
        put(names, "TUFF", "凝灰岩", "BASALT", "玄武岩", "BLACKSTONE", "黑石");
        put(names, "SPONGE", "海绵", "WET_SPONGE", "湿海绵");
        put(names, "HAY_BLOCK", "干草捆");

        // ---------- 木材 ----------
        put(names, "OAK_LOG", "橡木原木", "SPRUCE_LOG", "云杉原木", "BIRCH_LOG", "白桦原木");
        put(names, "JUNGLE_LOG", "丛林原木", "ACACIA_LOG", "金合欢原木", "DARK_OAK_LOG", "深色橡木原木");
        put(names, "MANGROVE_LOG", "红树原木", "CHERRY_LOG", "樱花原木");
        put(names, "CRIMSON_STEM", "绯红菌柄", "WARPED_STEM", "诡异菌柄");
        put(names, "OAK_PLANKS", "橡木木板", "SPRUCE_PLANKS", "云杉木板", "BIRCH_PLANKS", "白桦木板");
        put(names, "JUNGLE_PLANKS", "丛林木板", "ACACIA_PLANKS", "金合欢木板");
        put(names, "DARK_OAK_PLANKS", "深色橡木木板", "MANGROVE_PLANKS", "红树木板");
        put(names, "CHERRY_PLANKS", "樱花木板");
        put(names, "OAK_LEAVES", "橡树树叶", "SPRUCE_LEAVES", "云杉树叶");
        put(names, "OAK_SAPLING", "橡树树苗", "STICK", "木棍");

        // ---------- 作物与食物 ----------
        put(names, "WHEAT", "小麦", "WHEAT_SEEDS", "小麦种子", "BREAD", "面包");
        put(names, "CARROT", "胡萝卜", "POTATO", "马铃薯", "BAKED_POTATO", "烤马铃薯");
        put(names, "BEETROOT", "甜菜根", "PUMPKIN", "南瓜", "MELON", "西瓜");
        put(names, "MELON_SLICE", "西瓜片", "SUGAR_CANE", "甘蔗", "SUGAR", "糖");
        put(names, "BAMBOO", "竹子", "COCOA_BEANS", "可可豆", "SWEET_BERRIES", "甜浆果");
        put(names, "GLOW_BERRIES", "发光浆果", "NETHER_WART", "下界疣", "KELP", "海带");
        put(names, "APPLE", "苹果", "GOLDEN_APPLE", "金苹果", "ENCHANTED_GOLDEN_APPLE", "附魔金苹果");
        put(names, "COOKED_BEEF", "牛排", "BEEF", "生牛肉", "COOKED_PORKCHOP", "熟猪排");
        put(names, "PORKCHOP", "生猪排", "COOKED_CHICKEN", "熟鸡肉", "CHICKEN", "生鸡肉");
        put(names, "COOKED_MUTTON", "熟羊肉", "MUTTON", "生羊肉", "COOKED_RABBIT", "熟兔肉");
        put(names, "COOKED_COD", "熟鳕鱼", "COD", "生鳕鱼", "COOKED_SALMON", "熟鲑鱼");
        put(names, "SALMON", "生鲑鱼", "TROPICAL_FISH", "热带鱼", "PUFFERFISH", "河豚");
        put(names, "CAKE", "蛋糕", "COOKIE", "曲奇", "PUMPKIN_PIE", "南瓜派");
        put(names, "MUSHROOM_STEW", "蘑菇煲", "RABBIT_STEW", "兔肉煲", "SUSPICIOUS_STEW", "迷之炖菜");
        put(names, "HONEY_BOTTLE", "蜂蜜瓶", "MILK_BUCKET", "奶桶");

        // ---------- 生物掉落 ----------
        put(names, "LEATHER", "皮革", "ROTTEN_FLESH", "腐肉", "BONE", "骨头");
        put(names, "BONE_MEAL", "骨粉", "STRING", "线", "SPIDER_EYE", "蜘蛛眼");
        put(names, "FEATHER", "羽毛", "EGG", "鸡蛋", "GUNPOWDER", "火药");
        put(names, "BLAZE_ROD", "烈焰棒", "BLAZE_POWDER", "烈焰粉");
        put(names, "ENDER_PEARL", "末影珍珠", "ENDER_EYE", "末影之眼");
        put(names, "GHAST_TEAR", "恶魂之泪", "MAGMA_CREAM", "岩浆膏");
        put(names, "SLIME_BALL", "黏液球", "PHANTOM_MEMBRANE", "幻翼膜");
        put(names, "INK_SAC", "墨囊", "GLOW_INK_SAC", "荧光墨囊");
        put(names, "PRISMARINE_SHARD", "海晶碎片", "PRISMARINE_CRYSTALS", "海晶砂粒");
        put(names, "NAUTILUS_SHELL", "鹦鹉螺壳", "HEART_OF_THE_SEA", "海洋之心");
        put(names, "SHULKER_SHELL", "潜影壳", "TOTEM_OF_UNDYING", "不死图腾");
        put(names, "NETHER_STAR", "下界之星", "DRAGON_BREATH", "龙息");
        put(names, "WITHER_SKELETON_SKULL", "凋灵骷髅头颅", "TRIDENT", "三叉戟");
        put(names, "ELYTRA", "鞘翅", "TURTLE_EGG", "海龟蛋");

        // ---------- 羊毛与染料 ----------
        put(names, "WHITE_WOOL", "白色羊毛", "ORANGE_WOOL", "橙色羊毛", "MAGENTA_WOOL", "品红色羊毛");
        put(names, "LIGHT_BLUE_WOOL", "淡蓝色羊毛", "YELLOW_WOOL", "黄色羊毛", "LIME_WOOL", "黄绿色羊毛");
        put(names, "PINK_WOOL", "粉红色羊毛", "GRAY_WOOL", "灰色羊毛", "LIGHT_GRAY_WOOL", "淡灰色羊毛");
        put(names, "CYAN_WOOL", "青色羊毛", "PURPLE_WOOL", "紫色羊毛", "BLUE_WOOL", "蓝色羊毛");
        put(names, "BROWN_WOOL", "棕色羊毛", "GREEN_WOOL", "绿色羊毛", "RED_WOOL", "红色羊毛");
        put(names, "BLACK_WOOL", "黑色羊毛");
        put(names, "WHITE_DYE", "白色染料", "BLACK_DYE", "黑色染料", "RED_DYE", "红色染料");
        put(names, "BLUE_DYE", "蓝色染料", "GREEN_DYE", "绿色染料", "YELLOW_DYE", "黄色染料");

        // ---------- 工具与武器 ----------
        put(names, "WOODEN_PICKAXE", "木镐", "STONE_PICKAXE", "石镐", "IRON_PICKAXE", "铁镐");
        put(names, "GOLDEN_PICKAXE", "金镐", "DIAMOND_PICKAXE", "钻石镐", "NETHERITE_PICKAXE", "下界合金镐");
        put(names, "WOODEN_AXE", "木斧", "STONE_AXE", "石斧", "IRON_AXE", "铁斧");
        put(names, "DIAMOND_AXE", "钻石斧", "NETHERITE_AXE", "下界合金斧");
        put(names, "WOODEN_SHOVEL", "木锹", "IRON_SHOVEL", "铁锹", "DIAMOND_SHOVEL", "钻石锹");
        put(names, "WOODEN_HOE", "木锄", "IRON_HOE", "铁锄", "DIAMOND_HOE", "钻石锄");
        put(names, "WOODEN_SWORD", "木剑", "STONE_SWORD", "石剑", "IRON_SWORD", "铁剑");
        put(names, "DIAMOND_SWORD", "钻石剑", "NETHERITE_SWORD", "下界合金剑");
        put(names, "BOW", "弓", "CROSSBOW", "弩", "ARROW", "箭", "SPECTRAL_ARROW", "光灵箭");
        put(names, "SHIELD", "盾牌", "FISHING_ROD", "钓鱼竿", "SHEARS", "剪刀");
        put(names, "FLINT_AND_STEEL", "打火石", "BUCKET", "桶", "WATER_BUCKET", "水桶");
        put(names, "LAVA_BUCKET", "岩浆桶", "COMPASS", "指南针", "CLOCK", "时钟");
        put(names, "SPYGLASS", "望远镜", "LEAD", "拴绳", "NAME_TAG", "命名牌");
        put(names, "SADDLE", "鞍", "TORCH", "火把", "LANTERN", "灯笼");

        // ---------- 盔甲 ----------
        put(names, "LEATHER_HELMET", "皮革帽子", "LEATHER_CHESTPLATE", "皮革外套");
        put(names, "IRON_HELMET", "铁头盔", "IRON_CHESTPLATE", "铁胸甲");
        put(names, "IRON_LEGGINGS", "铁护腿", "IRON_BOOTS", "铁靴子");
        put(names, "GOLDEN_HELMET", "金头盔", "GOLDEN_CHESTPLATE", "金胸甲");
        put(names, "DIAMOND_HELMET", "钻石头盔", "DIAMOND_CHESTPLATE", "钻石胸甲");
        put(names, "DIAMOND_LEGGINGS", "钻石护腿", "DIAMOND_BOOTS", "钻石靴子");
        put(names, "NETHERITE_HELMET", "下界合金头盔", "NETHERITE_CHESTPLATE", "下界合金胸甲");
        put(names, "TURTLE_HELMET", "海龟壳");

        // ---------- 附魔与药水 ----------
        put(names, "ENCHANTED_BOOK", "附魔书", "BOOK", "书", "BOOKSHELF", "书架");
        put(names, "EXPERIENCE_BOTTLE", "附魔之瓶", "POTION", "药水", "SPLASH_POTION", "喷溅药水");
        put(names, "LINGERING_POTION", "滞留药水", "GLASS_BOTTLE", "玻璃瓶");
        put(names, "BREWING_STAND", "酿造台", "ENCHANTING_TABLE", "附魔台");
        put(names, "ANVIL", "铁砧", "GRINDSTONE", "砂轮", "SMITHING_TABLE", "锻造台");

        // ---------- 红石与功能方块 ----------
        put(names, "CHEST", "箱子", "TRAPPED_CHEST", "陷阱箱", "ENDER_CHEST", "末影箱");
        put(names, "BARREL", "木桶", "SHULKER_BOX", "潜影盒", "HOPPER", "漏斗");
        put(names, "FURNACE", "熔炉", "BLAST_FURNACE", "高炉", "SMOKER", "烟熏炉");
        put(names, "CRAFTING_TABLE", "工作台", "CAMPFIRE", "营火", "SOUL_CAMPFIRE", "灵魂营火");
        put(names, "REDSTONE_TORCH", "红石火把", "REPEATER", "红石中继器");
        put(names, "COMPARATOR", "红石比较器", "PISTON", "活塞", "STICKY_PISTON", "黏性活塞");
        put(names, "OBSERVER", "侦测器", "DISPENSER", "发射器", "DROPPER", "投掷器");
        put(names, "REDSTONE_LAMP", "红石灯", "DAYLIGHT_DETECTOR", "阳光探测器");
        put(names, "RAIL", "铁轨", "POWERED_RAIL", "动力铁轨", "MINECART", "矿车");
        put(names, "BEACON", "信标", "CONDUIT", "潮涌核心", "LODESTONE", "磁石");
        put(names, "LECTERN", "讲台", "CARTOGRAPHY_TABLE", "制图台");
        put(names, "LOOM", "织布机", "STONECUTTER", "切石机", "COMPOSTER", "堆肥桶");
        put(names, "BEEHIVE", "蜂箱", "BEE_NEST", "蜂巢", "SCAFFOLDING", "脚手架");
        put(names, "TNT", "TNT", "RESPAWN_ANCHOR", "重生锚");
        put(names, "HAY_BLOCK", "干草捆");

        // ---------- 实体 ----------
        put(names, "ZOMBIE", "僵尸", "SKELETON", "骷髅", "CREEPER", "苦力怕");
        put(names, "SPIDER", "蜘蛛", "CAVE_SPIDER", "洞穴蜘蛛", "ENDERMAN", "末影人");
        put(names, "BLAZE", "烈焰人", "GHAST", "恶魂", "MAGMA_CUBE", "岩浆怪");
        put(names, "SLIME", "史莱姆", "WITCH", "女巫", "PHANTOM", "幻翼");
        put(names, "DROWNED", "溺尸", "HUSK", "尸壳", "STRAY", "流浪者");
        put(names, "PILLAGER", "掠夺者", "VINDICATOR", "卫道士", "EVOKER", "唤魔者");
        put(names, "RAVAGER", "劫掠兽", "VEX", "恼鬼", "PIGLIN", "猪灵");
        put(names, "PIGLIN_BRUTE", "猪灵蛮兵", "HOGLIN", "疣猪兽", "ZOGLIN", "僵尸疣猪兽");
        put(names, "WITHER_SKELETON", "凋灵骷髅", "WITHER", "凋灵", "ENDER_DRAGON", "末影龙");
        put(names, "ELDER_GUARDIAN", "远古守卫者", "GUARDIAN", "守卫者", "SHULKER", "潜影贝");
        put(names, "WARDEN", "监守者", "SILVERFISH", "蠹虫", "ENDERMITE", "末影螨");
        put(names, "PIG", "猪", "COW", "牛", "SHEEP", "羊", "CHICKEN", "鸡");
        put(names, "RABBIT", "兔子", "HORSE", "马", "DONKEY", "驴", "MULE", "骡");
        put(names, "LLAMA", "羊驼", "WOLF", "狼", "CAT", "猫", "OCELOT", "豹猫");
        put(names, "PARROT", "鹦鹉", "FOX", "狐狸", "PANDA", "熊猫", "POLAR_BEAR", "北极熊");
        put(names, "TURTLE", "海龟", "DOLPHIN", "海豚", "SQUID", "鱿鱼", "GLOW_SQUID", "发光鱿鱼");
        put(names, "BEE", "蜜蜂", "GOAT", "山羊", "AXOLOTL", "美西螈", "FROG", "青蛙");
        put(names, "CAMEL", "骆驼", "SNIFFER", "嗅探兽", "ARMADILLO", "犰狳");
        put(names, "ALLAY", "悦灵", "VILLAGER", "村民", "WANDERING_TRADER", "流浪商人");
        put(names, "IRON_GOLEM", "铁傀儡", "SNOW_GOLEM", "雪傀儡");
        put(names, "COD", "鳕鱼", "SALMON", "鲑鱼", "TROPICAL_FISH", "热带鱼");
        put(names, "PUFFERFISH", "河豚", "BAT", "蝙蝠", "STRIDER", "炽足兽");
        put(names, "PLAYER", "玩家");

        return names;
    }

    /** 成对写入，减少样板代码。 */
    private static void put(Map<String, String> target, String... pairs) {
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            target.put(pairs[i], pairs[i + 1]);
        }
    }
}
