package com.playerPlugin.playerTaskX.core.web;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 编辑器素材目录的测试。
 *
 * <p>这里不调用 {@link MaterialCatalog#build()}——它需要服务端实例，
 * 而测试环境没有 Bukkit。因此改为验证它依赖的纯逻辑，以及最容易写错的
 * <b>中文译名表</b>：那些 key 是手写的枚举名，拼错一个不会编译报错，
 * 只会在编辑器里安静地少一个中文名，属于典型的「上线才发现」问题。</p>
 */
class MaterialCatalogTest {

    /** 中文译名表的 key（通过反射读取私有常量，避免为测试放宽可见性）。 */
    @SuppressWarnings("unchecked")
    private static Map<String, String> chineseNames() throws Exception {
        var field = MaterialCatalog.class.getDeclaredField("CHINESE_NAMES");
        field.setAccessible(true);
        return (Map<String, String>) field.get(null);
    }

    @Test
    @DisplayName("中文译名表的每个 key 都必须是真实的材质名或实体类型名")
    void everyChineseNameKeyExists() throws Exception {
        Set<String> materials = Stream.of(Material.values())
                .map(Material::name)
                .collect(Collectors.toSet());
        Set<String> entities = Stream.of(EntityType.values())
                .map(EntityType::name)
                .collect(Collectors.toSet());

        Set<String> unknown = new TreeSet<>();
        for (String key : chineseNames().keySet()) {
            if (!materials.contains(key) && !entities.contains(key)) {
                unknown.add(key);
            }
        }

        assertTrue(unknown.isEmpty(),
                "中文译名表里存在拼错或已移除的枚举名，编辑器里这些项会丢失中文名：" + unknown);
    }

    @Test
    @DisplayName("中文译名表没有空值，且译名里不含误留的占位内容")
    void chineseNamesAreWellFormed() throws Exception {
        Map<String, String> names = chineseNames();
        assertFalse(names.isEmpty(), "中文译名表不应为空");

        for (Map.Entry<String, String> entry : names.entrySet()) {
            String value = entry.getValue();
            assertFalse(value == null || value.isBlank(),
                    entry.getKey() + " 的中文译名为空——为空时应直接不收录，让前端回退英文名");
            // 注意不能断言「译名必须含中文汉字」：TNT 这类物品在中文里就叫 TNT
            assertFalse(value.contains("TODO") || value.contains("??"),
                    entry.getKey() + " 的中文译名疑似占位内容：" + value);
        }
    }

    @Test
    @DisplayName("任务里最高频的目标都有中文名，否则中文搜索等于不可用")
    void commonTargetsAreTranslated() throws Exception {
        Map<String, String> names = chineseNames();
        // 这些是预设与文档里实际会用到的项，缺任何一个都会让中文搜索出现明显空洞
        String[] common = {
                "STONE", "COBBLESTONE", "DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE", "IRON_ORE",
                "DIAMOND", "IRON_INGOT", "OAK_LOG", "TORCH", "BREAD", "WHEAT",
                "ZOMBIE", "SKELETON", "CREEPER", "COW", "PIG", "SHEEP", "CHICKEN",
                "WOLF", "VILLAGER", "ENDERMAN"
        };
        Set<String> missing = new TreeSet<>();
        for (String id : common) {
            if (!names.containsKey(id)) {
                missing.add(id);
            }
        }
        assertTrue(missing.isEmpty(), "以下常用项缺少中文译名：" + missing);
    }

    @Test
    @DisplayName("枚举名转可读形式：下划线断词并首字母大写")
    void prettyFormatsEnumNames() {
        assertEquals("Diamond Ore", MaterialCatalog.pretty("DIAMOND_ORE"));
        assertEquals("Stone", MaterialCatalog.pretty("STONE"));
        assertEquals("Oak Log", MaterialCatalog.pretty("OAK_LOG"));
        // 多段下划线不应产生多余空格
        assertEquals("Deepslate Diamond Ore", MaterialCatalog.pretty("DEEPSLATE_DIAMOND_ORE"));
        assertEquals("", MaterialCatalog.pretty(null));
        assertEquals("", MaterialCatalog.pretty(""));
    }

    @Test
    @DisplayName("解析服务端语言文件：item 与 block 同名时以 item 为准")
    void parseLangFilePrefersItemOverBlock() throws Exception {
        // 原版语言文件里 STONE 同时有 block.minecraft.stone 与 item.minecraft.stone，
        // 二者文案可能不同，编辑器希望拿到 item 的名字
        String json = """
                {
                  "block.minecraft.stone": "方块石头",
                  "item.minecraft.stone": "石头",
                  "item.minecraft.diamond_ore": "钻石矿石",
                  "block.minecraft.oak_log": "橡木原木",
                  "entity.minecraft.zombie": "僵尸",
                  "item.minecraft.glow_ink_sac": "Glow\\u0020Ink Sac"
                }
                """;
        Map<String, String> parsed = new LinkedHashMap<>();
        MaterialCatalog.parseLangFile(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), parsed);

        assertEquals("石头", parsed.get("stone"), "item 与 block 同名时应取 item 的文案");
        assertEquals("钻石矿石", parsed.get("diamond_ore"));
        assertEquals("橡木原木", parsed.get("oak_log"));
        assertEquals("Glow Ink Sac", parsed.get("glow_ink_sac"),
                "显示名里的 \\u0020 应还原成空格，否则界面上会出现字面量 \\u0020");
        assertFalse(parsed.containsKey("zombie"), "实体名不在本解析器的范围内（只认 item/block）");
    }

    @Test
    @DisplayName("语言文件损坏时不抛异常，只是解析不出内容")
    void parseLangFileToleratesGarbage() throws Exception {
        Map<String, String> parsed = new HashMap<>();
        // 不是合法 JSON：解析器按正则提取，不该抛异常
        MaterialCatalog.parseLangFile(
                new ByteArrayInputStream("{ this is not json at all ".getBytes(StandardCharsets.UTF_8)), parsed);
        assertTrue(parsed.isEmpty());
    }
}
