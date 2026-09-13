package com.playerPlugin.playerTaskX.core.storage.yaml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * YAML 编解码的「类型安全」测试。
 *
 * <p>这是本功能最危险的一处：SnakeYAML 默认按 YAML 1.1 解析，{@code NO} 会变成布尔 false、
 * {@code 012} 会变成八进制、{@code 2024-01-01} 会变成日期——而任务配置里这些全是合法字符串。
 * 真机冒烟时就实测到 {@code target: NO} 读回来是 {@code false}：玩家看到的只是「任务不涨进度」，
 * 没有任何报错可查。因此这里把「什么被当成字符串」逐个钉住。</p>
 */
class YamlTextTest {

    private static Object value(String yaml) {
        Map<String, Object> map = YamlText.readMap(yaml);
        return map == null ? null : map.get("v");
    }

    @Test
    @DisplayName("看似布尔的字符串保持字符串：NO / yes / on / off / y / n")
    void boolLookingStringsStayStrings() {
        for (String raw : new String[]{"NO", "no", "No", "yes", "YES", "on", "off", "y", "n", "Y", "N"}) {
            Object parsed = value("v: " + raw);
            assertInstanceOf(String.class, parsed, "「" + raw + "」被解析成了 " + parsed);
            assertEquals(raw, parsed);
        }
    }

    @Test
    @DisplayName("看似数字/日期的字符串保持字符串：1:30 / 2024-01-01 / 1.2.3 / 1_000")
    void numberLookingStringsStayStrings() {
        for (String raw : new String[]{"1:30", "2024-01-01", "1.2.3", "1_000"}) {
            Object parsed = value("v: " + raw);
            assertInstanceOf(String.class, parsed, "「" + raw + "」被解析成了 " + parsed);
            assertEquals(raw, parsed);
        }
    }

    @Test
    @DisplayName("整数按 YAML 1.2 core 解释：012 是十进制 12（不是八进制 10），0x10/0o17 按进制")
    void integersFollowYaml12Core() {
        // 与前端 js-yaml 的 CORE_SCHEMA 严格一致——两边读同一份文件必须得到同样的值
        assertEquals(12, value("v: 012"));
        assertEquals(16, value("v: 0x10"));
        assertEquals(15, value("v: 0o17"));
        assertEquals(-3, value("v: -3"));
        assertEquals(64, value("v: 64"));
        assertEquals("1_000", value("v: 1_000"), "js-yaml 也不认下划线分隔，保持一致");
    }

    @Test
    @DisplayName("真正需要的隐式类型照旧：布尔、整数、小数、null")
    void realTypesStillWork() {
        assertEquals(true, value("v: true"));
        assertEquals(false, value("v: false"));
        assertEquals(64, value("v: 64"));
        assertEquals(-3, value("v: -3"));
        assertEquals(1.5, value("v: 1.5"));
        assertEquals(1000.0, value("v: 1000.0"));
        assertEquals(null, value("v: ~"));
        assertEquals(null, value("v: null"));
        assertEquals(null, value("v:"));
    }

    @Test
    @DisplayName("写出去的歧义字符串带引号，读回来还是字符串")
    void writingQuotesAmbiguousStrings() {
        Map<String, Object> document = new java.util.LinkedHashMap<>();
        document.put("target", "NO");
        document.put("keyword", "yes");
        document.put("command", "on");
        document.put("empty", "");

        String yaml = YamlText.write(document);
        Map<String, Object> back = YamlText.readMap(yaml);

        assertEquals("NO", back.get("target"));
        assertEquals("yes", back.get("keyword"));
        assertEquals("on", back.get("command"));
        assertEquals("", back.get("empty"), "空串不能写成裸的空值，否则读回来是 null");
    }

    @Test
    @DisplayName("整数不写成 64.0，也不打 !!float 标签")
    void integralNumbersStayClean() {
        Map<String, Object> document = new java.util.LinkedHashMap<>();
        document.put("amount", YamlText.number(64.0));
        document.put("cost", YamlText.number(1000.0));
        document.put("ratio", YamlText.number(1.5));

        String yaml = YamlText.write(document);

        assertTrue(yaml.contains("amount: 64"), yaml);
        assertTrue(yaml.contains("cost: 1000"), yaml);
        assertTrue(yaml.contains("ratio: 1.5"), yaml);
        assertTrue(!yaml.contains("!!"), "不该出现显式标签: " + yaml);
    }

    @Test
    @DisplayName("长文本不折行：折行会改变字符串内容")
    void longTextIsNotFolded() {
        String longText = "很长的内容".repeat(50);
        Map<String, Object> document = Map.of("v", longText);

        assertEquals(longText, YamlText.readMap(YamlText.write(document)).get("v"));
    }

    @Test
    @DisplayName("属性表按键排序：同一份数据每次写出同样的字节，diff 才干净")
    void propertiesAreSorted() {
        Map<String, Object> unsorted = new java.util.LinkedHashMap<>();
        unsorted.put("target", "STONE");
        unsorted.put("amount", 64);
        unsorted.put("mode", "ANY");

        Map<String, Object> sorted = YamlText.sortedProperties(unsorted);

        assertEquals(java.util.List.of("amount", "mode", "target"), java.util.List.copyOf(sorted.keySet()));
    }
}
