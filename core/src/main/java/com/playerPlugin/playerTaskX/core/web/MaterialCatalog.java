package com.playerPlugin.playerTaskX.core.web;

import com.playerPlugin.playerTaskX.core.integration.CustomContentHooks;
import com.playerPlugin.playerTaskX.core.integration.MythicMobsHook;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private static final List<String> CATEGORY_ORDER = List.of("block", "item", "food");

    private final LangFileStore langFiles;

    /** MythicMobs 接入点；null 表示服务端没有（或不支持）MythicMobs。 */
    private final MythicMobsHook mythicMobs;

    /** 自定义内容来源（ItemsAdder / CraftEngine）；空实现表示两家都没装。 */
    private final CustomContentHooks customContent;

    public MaterialCatalog(LangFileStore langFiles) {
        this(langFiles, null, CustomContentHooks.empty());
    }

    public MaterialCatalog(LangFileStore langFiles, MythicMobsHook mythicMobs) {
        this(langFiles, mythicMobs, CustomContentHooks.empty());
    }

    public MaterialCatalog(LangFileStore langFiles, MythicMobsHook mythicMobs,
                           CustomContentHooks customContent) {
        this.langFiles = langFiles;
        this.mythicMobs = mythicMobs;
        this.customContent = customContent == null ? CustomContentHooks.empty() : customContent;
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
            if (!material.isItem()) {
                continue;
            }
            materials.add(entry(material.name(), english, chinese, categoryOf(material)));
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
            entities.add(entry(type.name(), english, chinese, null));
        }
        // MythicMobs 的怪物不是 EntityType，编辑器原本无从选起（只能手打 mythic:<id>）。
        // 直接混进实体列表、id 带 mythic: 前缀：写入 target 的值天然就是我们要的语法，
        // 前端也不用新增一种选择器
        entities.addAll(mythicMobEntries(mythicMobs));
        entities.sort(Comparator.comparing(entry -> String.valueOf(entry.get("id"))));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("materials", materials);
        result.put("entities", entities);
        result.put("categories", CATEGORY_ORDER);
        String version = Bukkit.getBukkitVersion();
        result.put("serverVersion", version == null ? "" : version);
        result.put("hasChinese", langFiles.hasChinese());
        return result;
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
            entries.add(entry);
        }
        return entries;
    }

    /**
     * 自定义物品与方块条目：{@code id} 形如 {@code itemsadder:myitems:ruby}，
     * 显示名带上插件名前缀，好让搜索「itemsadder」一次筛出全部自定义内容。
     * <p>
     * 方块与物品都进材质列表：目标任务里的 {@code target} 本来就既可能是方块也可能是物品，
     * 选择器再分一类只会让人找不到。
     * <p>
     * 静态、且不碰译名数据与 Bukkit：这样它可以被单独测试（见 {@code MaterialCatalogTest}）。
     */
    static List<Map<String, Object>> customEntries(CustomContentHooks customContent) {
        if (customContent == null || customContent.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        for (String id : customContent.blockIds()) {
            entries.add(customEntry(id, "block"));
        }
        for (String id : customContent.itemIds()) {
            entries.add(customEntry(id, "item"));
        }
        return entries;
    }

    private static Map<String, Object> customEntry(String id, String category) {
        String plugin = CustomContentHooks.pluginOf(prefixOf(id));
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", id);
        entry.put("en", (plugin == null ? "" : plugin + ": ") + id);
        entry.put("zh", "");
        entry.put("category", category);
        return entry;
    }

    /** 取 {@code itemsadder:xxx} 里的 {@code itemsadder:} 部分；没有前缀时返回空串。 */
    private static String prefixOf(String id) {
        int colon = id.indexOf(':');
        // 注意 itemsadder:ns:path 有两段冒号：这里只切第一段
        return colon < 0 ? "" : id.substring(0, colon + 1);
    }

    private static Map<String, Object> entry(String id, Map<String, String> english,
                                             Map<String, String> chinese, String category) {
        String key = id.toLowerCase(Locale.ROOT);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", id);
        entry.put("en", english.getOrDefault(key, pretty(id)));
        // 中文名缺失时留空串，由前端回退到英文名——因此搜索必须同时匹配三个字段
        entry.put("zh", chinese.getOrDefault(key, ""));
        if (category != null) {
            entry.put("category", category);
        }
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
