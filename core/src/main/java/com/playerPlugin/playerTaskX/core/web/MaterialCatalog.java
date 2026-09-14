package com.playerPlugin.playerTaskX.core.web;

import com.playerPlugin.playerTaskX.api.schema.ValueKind;
import com.playerPlugin.playerTaskX.core.integration.CustomContentHooks;
import com.playerPlugin.playerTaskX.core.integration.FishLoot;
import com.playerPlugin.playerTaskX.core.integration.MythicMobsHook;
import com.playerPlugin.playerTaskX.core.schema.ValueKinds;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 可供网页编辑器选择的物品与实体清单。
 *
 * <h2>版本适配靠运行期枚举</h2>
 * 素材直接来自服务端自己的 {@link Material} / {@link EntityType} 枚举，因此
 * <b>天然只含当前版本支持的项</b>——1.21.11 的服务端给出的就是 1.21.11 的清单，
 * 不需要维护「哪个物品是哪个版本加的」这类数据。
 *
 * <h2>译名交给 {@link LangFileStore}</h2>
 * 这里只负责「有哪些项、怎么分类」，人名从哪来不是它的职责：
 * 英文名读服务端自带的语言文件，中文名由 {@code LangFileStore} 提供（必要时下载）。
 * 早期这里内置过一份手工中文表，覆盖不全且要人工维护，已删除。
 *
 * <h2>为什么只列「能作为物品存在」的材质</h2>
 * {@code Material.isItem()} 为假的项（如 {@code AIR}、纯方块状态）不能作为任务目标，
 * 列出来只会让人选到无效值。
 */
public class MaterialCatalog {

    /** 分类展示顺序，前端按此顺序排列分组。 */
    private static final List<String> CATEGORY_ORDER = List.of("block", "item", "food", "fish");

    /** 来源 id：原版枚举。 */
    static final String SOURCE_MINECRAFT = "minecraft";
    /** 来源 id：MythicMobs 自定义怪（实体栏）。 */
    static final String SOURCE_MYTHICMOBS = "mythicmobs";
    /** 来源 id：CustomFishing 战利品（鱼 id 栏）。 */
    static final String SOURCE_CUSTOMFISHING = "customfishing";

    /**
     * 来源展示顺序与显示名。
     * <p>
     * 顺序固定（原版在最前，其余按接入的历史顺序），前端只按 id 过滤、按 label 显示，
     * 不自己维护一份名字表——两份表迟早会漂移。
     */
    private static final List<Map.Entry<String, String>> SOURCE_LABELS = List.of(
            Map.entry(SOURCE_MINECRAFT, "原版"),
            Map.entry(SOURCE_MYTHICMOBS, "MythicMobs"),
            Map.entry("itemsadder", "ItemsAdder"),
            Map.entry("craftengine", "CraftEngine"),
            Map.entry(SOURCE_CUSTOMFISHING, "CustomFishing"));

    private final LangFileStore langFiles;

    /** MythicMobs 接入点；null 表示服务端没有（或不支持）MythicMobs。 */
    private final MythicMobsHook mythicMobs;

    /** 自定义内容来源（ItemsAdder / CraftEngine）；空实现表示两家都没装。 */
    private final CustomContentHooks customContent;

    /**
     * CustomFishing 战利品清单。
     * <p>
     * 是 supplier 而不是现成列表：CustomFishing {@code reload} 之后注册表会被换掉，
     * 而本目录每次请求都重新算一遍，拿到的就该是当时的那份。
     */
    private final Supplier<List<FishLoot>> fishLoot;

    /**
     * 四个来源<b>都</b>由调用方传入，且刻意不提供「省略某个来源」的便捷构造器。
     * <p>
     * 曾经的 {@code MaterialCatalog(langFiles, mythicMobs)} 会把自定义内容悄悄当成空实现，
     * 于是「编辑器里选不到 ItemsAdder / CraftEngine 的物品与方块」这种错配编译期看不出来、
     * 运行期也不报错——只有管理员发现东西不在列表里。少一个参数就该编译不过。
     */
    public MaterialCatalog(LangFileStore langFiles, MythicMobsHook mythicMobs,
                           CustomContentHooks customContent, Supplier<List<FishLoot>> fishLoot) {
        this.langFiles = langFiles;
        this.mythicMobs = mythicMobs;
        this.customContent = customContent == null ? CustomContentHooks.empty() : customContent;
        this.fishLoot = fishLoot == null ? List::of : fishLoot;
    }

    /**
     * 构建供前端使用的清单。
     * <p>
     * 一次性返回全部条目（1500+ 条，gzip 后约 20KB）：编辑器在本地做搜索与筛选，
     * 因此输入关键词时不再发请求，中文搜索也能即时响应。
     */
    public Map<String, Object> build() {
        Map<String, String> english = langFiles.english();
        Map<String, String> chinese = langFiles.chinese();

        List<Map<String, Object>> materials = new ArrayList<>();
        for (Material material : Material.values()) {
            // 方块与物品的并集：只列 isItem 会漏掉「能挖但没有物品形态」的方块（刷怪笼、耕地…），
            // 而挖掘目标恰恰要能选到它们；值域由每条的 kinds 决定，选择器不会再列错
            if (!material.isItem() && !material.isBlock()) {
                continue;
            }
            materials.add(entry(material.name(), english, chinese, categoryOf(material), SOURCE_MINECRAFT,
                    ValueKinds.of(material)));
        }
        // 自定义物品与自定义方块混进材质列表、id 带插件前缀：写入 target 的值天然就是我们要的语法
        // （与下面 MythicMobs 的处理同一套思路），前端也不用新增一种选择器
        materials.addAll(customEntries(customContent));
        materials.sort(Comparator.comparing(entry -> String.valueOf(entry.get("id"))));

        List<Map<String, Object>> entities = new ArrayList<>();
        for (EntityType type : EntityType.values()) {
            if (type == EntityType.UNKNOWN) {
                // 纯技术实体，永远不会出现在任务里
                continue;
            }
            entities.add(entry(type.name(), english, chinese, null, SOURCE_MINECRAFT, ValueKinds.of(type)));
        }
        // MythicMobs 的怪物不是 EntityType，编辑器原本无从选起（只能手打 mythic:<id>）。
        // 直接混进实体列表、id 带 mythic: 前缀：写入 target 的值天然就是我们要的语法，
        // 前端也不用新增一种选择器
        entities.addAll(mythicMobEntries(mythicMobs));
        entities.sort(Comparator.comparing(entry -> String.valueOf(entry.get("id"))));

        // 鱼 id 单独一栏：它不是材质也不是实体，混进材质列表会让人把它填进 break_block.target，
        // 那样永远命中不了——正是本项目最想根除的「配了却不生效」。
        // 必须包一层 ArrayList：没装 CustomFishing 时 fishEntries 返回的是不可变空表，直接 sort 会抛异常
        List<Map<String, Object>> fish = new ArrayList<>(fishEntries(fishLoot.get()));
        fish.sort(Comparator.comparing(entry -> String.valueOf(entry.get("id"))));

        // 附魔同样自成一份清单：它不是材质，也与实体无关
        List<Map<String, Object>> enchantments = enchantmentEntries(english, chinese);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("materials", materials);
        result.put("entities", entities);
        result.put("fish", fish);
        result.put("enchantments", enchantments);
        result.put("categories", CATEGORY_ORDER);
        // 来源清单只列「这次真的有东西」的那些：没装 CraftEngine 的服务器上不该出现它的筛选标签
        result.put("sources", sourcesOf(materials, entities, fish, enchantments));
        String version = Bukkit.getBukkitVersion();
        result.put("serverVersion", version == null ? "" : version);
        result.put("hasChinese", langFiles.hasChinese());
        return result;
    }

    /**
     * 本次目录里出现过的来源，按 {@link #SOURCE_LABELS} 的固定顺序给出 {@code {id,label}}。
     * <p>
     * 由后端算而不是让前端自己从条目里推：显示名（ItemsAdder / CraftEngine…）属于后端的知识，
     * 前端再抄一份就会与接入层漂移。静态、不碰 Bukkit，可单测。
     */
    @SafeVarargs
    static List<Map<String, Object>> sourcesOf(List<Map<String, Object>>... groups) {
        Map<String, Boolean> present = new LinkedHashMap<>();
        for (List<Map<String, Object>> group : groups) {
            if (group == null) {
                continue;
            }
            for (Map<String, Object> entry : group) {
                Object source = entry.get("source");
                if (source != null) {
                    present.putIfAbsent(String.valueOf(source), Boolean.TRUE);
                }
            }
        }
        List<Map<String, Object>> sources = new ArrayList<>();
        for (Map.Entry<String, String> known : SOURCE_LABELS) {
            if (!present.containsKey(known.getKey())) {
                continue;
            }
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("id", known.getKey());
            source.put("label", known.getValue());
            sources.add(source);
        }
        // 兜底：接入了名字表里没有的新来源时也要能被筛出来，而不是从界面上消失
        for (String id : present.keySet()) {
            if (SOURCE_LABELS.stream().noneMatch(known -> known.getKey().equals(id))) {
                Map<String, Object> source = new LinkedHashMap<>();
                source.put("id", id);
                source.put("label", id);
                sources.add(source);
            }
        }
        return sources;
    }

    /**
     * MythicMobs 怪物条目，形如 {@code id = mythic:SkeletalKnight}。
     * <p>
     * 显示名带上 {@code MythicMobs} 前缀，好让搜索「mythic」或「MythicMobs」时能一次筛出全部自定义怪，
     * 也让人一眼看出它不是原版实体。中文名留空（原版语言文件里当然没有它），
     * 前端会退回英文名，搜索照样命中 id。
     * <p>
     * 静态、且不碰译名数据与 Bukkit：这样它可以被单独测试（见 {@code MaterialCatalogTest}）。
     */
    static List<Map<String, Object>> mythicMobEntries(MythicMobsHook mythicMobs) {
        if (mythicMobs == null) {
            return List.of();
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        for (String mobId : mythicMobs.mobIds()) {
            if (mobId == null || mobId.isBlank()) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", MythicMobsHook.PREFIX + mobId);
            entry.put("en", "MythicMobs: " + mobId);
            entry.put("zh", "");
            entry.put("source", SOURCE_MYTHICMOBS);
            // MythicMobs 的怪是活体：既能当击杀目标（LIVING），也能当交互对象（ENTITY）
            entry.put("kinds", kindsJson(ValueKinds.mythicMob()));
            entries.add(entry);
        }
        return entries;
    }

    /**
     * CustomFishing 战利品条目：{@code id} 就是写进 {@code custom_fish} 的 {@code target} 的值
     * （<b>不带前缀</b>——监听器推给进度引擎的战利品 id 是裸的）。
     * <p>
     * 分类固定为 {@code fish}：它既不是方块也不是物品，混进材质分类只会让人把它填错字段。
     * 显示名用 CustomFishing 配置里的 {@code nick}，缺失时 {@link FishLoot} 已退回 id。
     * <p>
     * 静态、不碰 Bukkit：可以单独测试（见 {@code MaterialCatalogTest}）。
     */
    static List<Map<String, Object>> fishEntries(List<FishLoot> loot) {
        if (loot == null || loot.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> entries = new ArrayList<>(loot.size());
        for (FishLoot item : loot) {
            if (item == null || item.id() == null || item.id().isBlank()) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", item.id());
            entry.put("en", item.name());
            entry.put("zh", "");
            entry.put("category", "fish");
            entry.put("source", SOURCE_CUSTOMFISHING);
            entry.put("kinds", kindsJson(ValueKinds.fish()));
            entries.add(entry);
        }
        return entries;
    }

    /**
     * 自定义物品与方块条目：{@code id} 形如 {@code itemsadder:myitems:ruby}，
     * 显示名带上插件名前缀，好让搜索「itemsadder」一次筛出全部自定义内容。
     * <p>
     * 方块与物品都进材质列表：目标任务里的 {@code target} 本来就既可能是方块也可能是物品，
     * 选择器再分一类只会让人找不到。<b>同一个 id 既在方块表又在物品表时只留一条</b>
     * （CraftEngine 的默认包就这样，例如火把），否则「全部」分类里会并排出现两个一模一样的条目，
     * 而它们写进 {@code target} 的值完全相同。
     * <p>
     * 静态、且不碰译名数据与 Bukkit：这样它可以被单独测试（见 {@code MaterialCatalogTest}）。
     */
    static List<Map<String, Object>> customEntries(CustomContentHooks customContent) {
        if (customContent == null || customContent.isEmpty()) {
            return List.of();
        }
        Map<String, Map<String, Object>> entries = new LinkedHashMap<>();
        for (String id : customContent.blockIds()) {
            entries.putIfAbsent(id, customEntry(id, "block"));
        }
        for (String id : customContent.itemIds()) {
            entries.putIfAbsent(id, customEntry(id, "item"));
        }
        return List.copyOf(entries.values());
    }

    /**
     * 单条自定义内容：{@code id} 是写进 {@code target} 的值（带插件前缀），
     * 显示名去掉重复的前缀（{@code craftengine:default:torch} → {@code CraftEngine: default:torch}）。
     */
    private static Map<String, Object> customEntry(String id, String category) {
        String prefix = prefixOf(id);
        String plugin = CustomContentHooks.pluginOf(prefix);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", id);
        entry.put("en", (plugin == null ? "" : plugin + ": ") + id.substring(prefix.length()));
        entry.put("zh", "");
        entry.put("category", category);
        // 来源就是前缀本身（itemsadder / craftengine），去掉冒号与前缀语义无关
        entry.put("source", prefix.isEmpty() ? SOURCE_MINECRAFT : prefix.substring(0, prefix.length() - 1));
        // 自定义方块同时也是物品：它不是「世界里的一个方块」，而是拿在手里右键放下去的东西
        // （这正是它区别于原版方块的地方）。因此值域与原版方块对齐：block + placeable + item。
        // 少了 ITEM 的后果很具体：CraftEngine 的方块在「合成 / 消耗 / 提交」这类物品字段里
        // 一条都选不到——而它们明明有物品形态，也确实是合成产物。
        entry.put("kinds", kindsJson("block".equals(category)
                ? EnumSet.of(ValueKind.BLOCK, ValueKind.PLACEABLE, ValueKind.ITEM)
                : EnumSet.of(ValueKind.ITEM)));
        return entry;
    }

    /** 附魔条目：id 就是 Bukkit 的附魔名（{@code SHARPNESS}），也是写进 target 的值。 */
    static List<Map<String, Object>> enchantmentEntries(Map<String, String> english,
                                                        Map<String, String> chinese) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Enchantment enchantment : ValueKinds.enchantments()) {
            String id = enchantment.getKey().getKey().toUpperCase(Locale.ROOT);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", id);
            entry.put("en", english.getOrDefault(id.toLowerCase(Locale.ROOT), pretty(id)));
            entry.put("zh", chinese.getOrDefault(id.toLowerCase(Locale.ROOT), ""));
            entry.put("source", SOURCE_MINECRAFT);
            entry.put("kinds", kindsJson(ValueKinds.enchantment()));
            entries.add(entry);
        }
        entries.sort(Comparator.comparing(entry -> String.valueOf(entry.get("id"))));
        return entries;
    }

    /** 值域集合 → JSON 数组（按枚举声明顺序，前端只做包含判断）。 */
    static List<String> kindsJson(Set<ValueKind> kinds) {
        List<String> ids = new ArrayList<>(kinds.size());
        for (ValueKind kind : kinds) {
            ids.add(kind.name().toLowerCase(Locale.ROOT));
        }
        return ids;
    }

    /** 取 {@code itemsadder:xxx} 里的 {@code itemsadder:} 部分；没有前缀时返回空串。 */
    private static String prefixOf(String id) {
        int colon = id.indexOf(':');
        // 注意 itemsadder:ns:path 有两段冒号：这里只切第一段
        return colon < 0 ? "" : id.substring(0, colon + 1);
    }

    private static Map<String, Object> entry(String id, Map<String, String> english,
                                             Map<String, String> chinese, String category, String source,
                                             Set<ValueKind> kinds) {
        String key = id.toLowerCase(Locale.ROOT);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", id);
        entry.put("en", english.getOrDefault(key, pretty(id)));
        // 中文名缺失时留空串，由前端回退到英文名——因此搜索必须同时匹配三个字段
        entry.put("zh", chinese.getOrDefault(key, ""));
        if (category != null) {
            entry.put("category", category);
        }
        entry.put("source", source);
        entry.put("kinds", kindsJson(kinds));
        return entry;
    }

    /** 枚举名的可读化形式，作为语言文件缺失时的兜底：{@code DIAMOND_ORE} → {@code Diamond Ore}。 */
    static String pretty(String enumName) {
        if (enumName == null || enumName.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(enumName.length());
        for (String part : enumName.toLowerCase(Locale.ROOT).split("_")) {
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
     * 分类，用于前端分组展示。
     * <p>
     * 只依赖 {@code isBlock()} / {@code isEdible()} 这类运行期判断，不维护分类表——
     * 分类表同样会随版本失效。
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
}
