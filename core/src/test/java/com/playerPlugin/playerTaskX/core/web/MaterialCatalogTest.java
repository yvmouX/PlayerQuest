package com.playerPlugin.playerTaskX.core.web;

import com.playerPlugin.playerTaskX.core.integration.FakeMythicMobsHook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 语言文件解析与枚举名兜底的测试。
 *
 * <p>译名从「内置手工表」改成「读服务端语言文件 + 可选下载中文」之后，最容易出错的
 * 地方就变成了<b>解析</b>：语言文件里含转义、方块与物品同名、实体键不该混进来。
 * 这些都不会编译报错，只会让编辑器里安静地显示出错的名字，因此逐个钉住。</p>
 *
 * <p>这里不触碰 {@link LangFileStore#initialize()}——它需要服务端实例与网络。</p>
 */
class MaterialCatalogTest {

    private static Map<String, String> parse(String json) throws Exception {
        try (InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
            return LangFileStore.parse(stream);
        }
    }

    @Test
    @DisplayName("方块与物品同名时以物品为准")
    void itemWinsOverBlock() throws Exception {
        // 原版语言文件里 stone 同时有 block 与 item 两个键，二者文案可能不同
        Map<String, String> names = parse("""
                {
                  "block.minecraft.stone": "方块石头",
                  "item.minecraft.stone": "石头"
                }
                """);
        assertEquals(1, names.size(), "同名键应合并为一条");
        assertEquals("石头", names.get("stone"));
    }

    @Test
    @DisplayName("实体键也收录，且枚举名剥掉 MINECRAFT_ 前缀后能对上")
    void entityKeysAreCollected() throws Exception {
        Map<String, String> names = parse("""
                {
                  "entity.minecraft.zombie": "僵尸",
                  "entity.minecraft.zombified_piglin": "僵尸猪灵",
                  "entity.minecraft.tropical_fish.predefined.0": "海葵鱼",
                  "item.minecraft.diamond": "钻石"
                }
                """);
        assertEquals("僵尸", names.get("zombie"));
        // 枚举名 MINECRAFT_ZOMBIE → 去前缀 → 小写，正好是这个键
        assertEquals("僵尸猪灵", names.get("zombified_piglin"));
        assertFalse(names.containsKey("tropical_fish.predefined.0"),
                "带点号的子键不是实体本身的名字，不该收录");
        assertEquals("钻石", names.get("diamond"));
    }

    @Test
    @DisplayName("只认 minecraft 命名空间，模组与其它键不混进来")
    void onlyMinecraftNamespace() throws Exception {
        Map<String, String> names = parse("""
                {
                  "item.minecraft.diamond": "钻石",
                  "block.minecraft.diamond_block": "钻石块",
                  "item.mod.magic_wand": "法杖",
                  "some.other.key": "无关"
                }
                """);
        assertEquals(2, names.size(), "只有 minecraft 命名空间的键应被收录");
        assertTrue(names.containsKey("diamond"));
        assertTrue(names.containsKey("diamond_block"));
        assertFalse(names.containsKey("magic_wand"), "非 minecraft 命名空间不收录");
    }

    @Test
    @DisplayName("键统一转小写，便于与枚举名小写化后对齐")
    void keysAreLowerCase() throws Exception {
        Map<String, String> names = parse("""
                { "item.minecraft.diamond_ore": "钻石矿石" }
                """);
        assertTrue(names.containsKey("diamond_ore"), "键应为小写: " + names.keySet());
    }

    @Test
    @DisplayName("JSON 转义被还原（\\u0020、引号、换行）")
    void escapesAreDecoded() throws Exception {
        Map<String, String> names = parse("""
                {
                  "item.minecraft.a": "Glow\\u0020Ink Sac",
                  "item.minecraft.b": "带\\"引号\\"的名字",
                  "item.minecraft.c": "第一行\\n第二行",
                  "item.minecraft.d": "反斜杠\\\\本身"
                }
                """);
        assertEquals("Glow Ink Sac", names.get("a"));
        assertEquals("带\"引号\"的名字", names.get("b"));
        assertEquals("第一行\n第二行", names.get("c"));
        assertEquals("反斜杠\\本身", names.get("d"));
    }

    @Test
    @DisplayName("空输入与损坏内容不抛异常，只是解析不出东西")
    void toleratesGarbage() throws Exception {
        assertTrue(parse("").isEmpty());
        assertTrue(parse("{ 这不是 JSON").isEmpty());
        assertTrue(parse("{\"item.minecraft.x\": }").isEmpty());
    }

    @Test
    @DisplayName("枚举名兜底：下划线断词并首字母大写")
    void prettyFormatsEnumNames() {
        assertEquals("Diamond Ore", MaterialCatalog.pretty("DIAMOND_ORE"));
        assertEquals("Stone", MaterialCatalog.pretty("STONE"));
        assertEquals("Oak Log", MaterialCatalog.pretty("OAK_LOG"));
        assertEquals("Deepslate Diamond Ore", MaterialCatalog.pretty("DEEPSLATE_DIAMOND_ORE"));
        assertEquals("", MaterialCatalog.pretty(null));
        assertEquals("", MaterialCatalog.pretty(""));
    }

    @Test
    @DisplayName("MythicMobs 怪物进实体列表时 id 必须带 mythic: 前缀（它就是写入 target 的值）")
    void mythicMobsEnterEntityCatalogWithPrefix() {
        var entries = MaterialCatalog.mythicMobEntries(
                FakeMythicMobsHook.ofMobIds("SkeletalKnight", "Boss"));

        assertEquals(2, entries.size());
        assertEquals("mythic:SkeletalKnight", entries.get(0).get("id"),
                "少了前缀，管理员选出来的值就匹配不上 kill 目标的 mythic: 语法");
        assertTrue(String.valueOf(entries.get(0).get("en")).contains("SkeletalKnight"),
                "显示名要能被人认出来，实际: " + entries.get(0).get("en"));
        assertEquals("", entries.get(0).get("zh"), "原版语言文件里没有自定义怪的译名，留空由前端回退");
    }

    @Test
    @DisplayName("没有 MythicMobs、或怪物列表为空时，不往实体列表里塞东西")
    void mythicCatalogIsEmptyWithoutHook() {
        assertTrue(MaterialCatalog.mythicMobEntries(null).isEmpty());
        assertTrue(MaterialCatalog.mythicMobEntries(FakeMythicMobsHook.ofMobIds()).isEmpty());
    }
}
