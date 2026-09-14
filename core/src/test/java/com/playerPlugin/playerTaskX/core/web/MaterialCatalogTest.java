package com.playerPlugin.playerTaskX.core.web;

import com.playerPlugin.playerTaskX.core.integration.CustomContentHooks;
import com.playerPlugin.playerTaskX.core.integration.FakeMythicMobsHook;
import com.playerPlugin.playerTaskX.core.integration.FishLoot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
    @DisplayName("附魔译名也收录：编辑器的「附魔」字段是选择器，中文名同样来自语言文件")
    void enchantmentNamesAreCollected() throws Exception {
        Map<String, String> names = parse("""
                {
                  "enchantment.minecraft.sharpness": "锋利",
                  "enchantment.minecraft.efficiency": "效率"
                }
                """);
        assertEquals("锋利", names.get("sharpness"));
        assertEquals("效率", names.get("efficiency"));
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

    @Test
    @DisplayName("自定义物品/方块进材质列表时 id 带插件前缀（它就是写入 target 的值）")
    void customContentEntersMaterialCatalogWithPrefix() {
        var entries = MaterialCatalog.customEntries(CustomContentHooks.of(
                FakeCustomContentHook.of("ItemsAdder", "itemsadder:",
                        List.of("myitems:ruby"), List.of("myitems:ruby_block"))));

        // 方块排在物品前面：同一个选择器里先看到能挖/能放的那些
        assertEquals(2, entries.size());
        assertEquals("itemsadder:myitems:ruby_block", entries.get(0).get("id"));
        assertEquals("block", entries.get(0).get("category"));
        assertEquals("itemsadder:myitems:ruby", entries.get(1).get("id"));
        assertEquals("item", entries.get(1).get("category"));
        assertTrue(String.valueOf(entries.get(0).get("en")).contains("ItemsAdder"),
                "显示名要让人一眼看出不是原版方块，实际: " + entries.get(0).get("en"));
    }

    @Test
    @DisplayName("显示名不重复插件前缀：id 里已经有 craftengine:，名字里不再叠一遍")
    void customEntryNameDropsTheRepeatedPrefix() {
        var entries = MaterialCatalog.customEntries(CustomContentHooks.of(
                FakeCustomContentHook.of("CraftEngine", "craftengine:",
                        List.of(), List.of("default:amethyst_torch"))));

        assertEquals("craftengine:default:amethyst_torch", entries.get(0).get("id"),
                "写入 target 的值必须带前缀");
        assertEquals("CraftEngine: default:amethyst_torch", entries.get(0).get("en"),
                "显示名里的 id 要剥掉前缀，否则读起来是「CraftEngine: craftengine:…」");
    }

    @Test
    @DisplayName("同一个 id 既在方块表又在物品表时只留一条（否则「全部」里并排两条一样的）")
    void customContentIsDeduplicatedById() {
        var entries = MaterialCatalog.customEntries(CustomContentHooks.of(
                FakeCustomContentHook.of("CraftEngine", "craftengine:",
                        List.of("default:torch"), List.of("default:torch"))));

        assertEquals(1, entries.size(), "两条的 id 完全一样，写进 target 的值也完全一样");
        assertEquals("block", entries.get(0).get("category"), "方块优先：先看到的应该是能挖/能放的那类");
    }

    @Test
    @DisplayName("每条自定义内容都带来源标记：编辑器据此按插件筛选")
    void customContentCarriesItsSource() {
        var entries = MaterialCatalog.customEntries(CustomContentHooks.of(
                FakeCustomContentHook.of("ItemsAdder", "itemsadder:",
                        List.of("myitems:ruby"), List.of()),
                FakeCustomContentHook.of("CraftEngine", "craftengine:",
                        List.of("default:bench"), List.of())));

        assertEquals("itemsadder", entries.get(0).get("source"), "来源取自 id 的前缀");
        assertEquals("craftengine", entries.get(1).get("source"));
        assertEquals("mythicmobs", MaterialCatalog.mythicMobEntries(
                        FakeMythicMobsHook.ofMobIds("Boss")).get(0).get("source"),
                "MythicMobs 的怪同样要带来源，否则按插件筛选时它们会无处可去");
    }

    @Test
    @DisplayName("自定义方块同时也是物品：合成/消耗这类字段必须能选到它")
    void customBlocksAreAlsoItems() {
        var entries = MaterialCatalog.customEntries(CustomContentHooks.of(
                FakeCustomContentHook.of("CraftEngine", "craftengine:",
                        List.of("default:bench"), List.of("default:torch"))));

        assertEquals(List.of("block", "placeable", "item"), entries.get(0).get("kinds"),
                "自定义方块是「拿在手里右键放下去」的东西，因此与原版方块一样同时属于三种值域；"
                        + "少了 item，它就会从合成/消耗/提交的候选里整片消失");
        assertEquals(List.of("item"), entries.get(1).get("kinds"), "纯物品只有 item");
    }

    @Test
    @DisplayName("CustomFishing 战利品单独一栏：id 不带前缀，显示名用配置里的 nick")
    void fishEntriesCarryBareIds() {
        var entries = MaterialCatalog.fishEntries(List.of(
                new FishLoot("my_custom_fish", "<yellow>大鱼"),
                new FishLoot("no_nick_fish", "")));

        assertEquals(2, entries.size());
        assertEquals("my_custom_fish", entries.get(0).get("id"),
                "监听器推给进度引擎的就是这个裸 id，加了前缀反而永远匹配不上");
        assertEquals("<yellow>大鱼", entries.get(0).get("en"), "显示名用 CustomFishing 配置里的 nick");
        assertEquals("fish", entries.get(0).get("category"));
        assertEquals("customfishing", entries.get(0).get("source"));
        assertEquals("no_nick_fish", entries.get(1).get("en"), "没有 nick 时退回 id，至少还能认出是哪条");
    }

    @Test
    @DisplayName("没装 CustomFishing（清单为空）时不往目录里塞鱼")
    void fishCatalogIsEmptyWithoutCustomFishing() {
        assertTrue(MaterialCatalog.fishEntries(null).isEmpty());
        assertTrue(MaterialCatalog.fishEntries(List.of()).isEmpty());
    }

    @Test
    @DisplayName("来源清单只列这次真的有东西的那些，且顺序固定（原版最前）")
    void sourcesListOnlyContainsPresentSources() {
        var materials = MaterialCatalog.customEntries(CustomContentHooks.of(
                FakeCustomContentHook.of("CraftEngine", "craftengine:",
                        List.of("default:bench"), List.of())));
        var entities = MaterialCatalog.mythicMobEntries(FakeMythicMobsHook.ofMobIds("Boss"));
        var fish = MaterialCatalog.fishEntries(List.of(new FishLoot("my_custom_fish", "")));

        var sources = MaterialCatalog.sourcesOf(
                List.of(Map.of("id", "STONE", "source", "minecraft")), entities, fish);
        assertEquals(List.of("minecraft", "mythicmobs", "customfishing"), ids(sources),
                "没接入的来源不该出现，否则界面上会多出一堆点了没结果的筛选标签");

        var withCustomContent = MaterialCatalog.sourcesOf(
                List.of(Map.of("id", "STONE", "source", "minecraft")), entities, materials);
        assertEquals(List.of("minecraft", "mythicmobs", "craftengine"), ids(withCustomContent),
                "顺序固定：原版 → MythicMobs → ItemsAdder → CraftEngine → CustomFishing，与接入历史一致");

        assertEquals("CraftEngine", withCustomContent.get(2).get("label"),
                "显示名由后端给（前端不抄第二份名字表）");
    }

    @Test
    @DisplayName("来源清单兜底：名字表里没有的新来源也要能被筛出来，而不是从界面上消失")
    void unknownSourceStillGetsATab() {
        var sources = MaterialCatalog.sourcesOf(
                List.of(Map.of("id", "x", "source", "somefutureplugin")), List.of(), List.of());

        assertEquals(List.of("somefutureplugin"), ids(sources));
        assertEquals("somefutureplugin", sources.get(0).get("label"), "认不出来就原样显示 id");
    }

    private static List<String> ids(List<Map<String, Object>> sources) {
        return sources.stream().map(source -> String.valueOf(source.get("id"))).toList();
    }

    @Test
    @DisplayName("两家都没接上时，材质列表里一条自定义内容都不加")
    void customCatalogIsEmptyWithoutHooks() {
        assertTrue(MaterialCatalog.customEntries(null).isEmpty());
        assertTrue(MaterialCatalog.customEntries(CustomContentHooks.empty()).isEmpty());
    }
}
