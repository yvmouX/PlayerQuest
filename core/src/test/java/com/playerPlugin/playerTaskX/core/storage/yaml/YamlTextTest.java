package com.playerPlugin.playerTaskX.core.storage.yaml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * YAML 解析的「类型安全」测试：SnakeYAML 默认按 YAML 1.1 解析，{@code NO} 会变成布尔 false、{@code 012} 变成八进制、{@code 2024-01-01} 变成日期，而任务配置里这些全是合法字符串——实测 {@code target: NO} 读回来是 {@code false}，玩家只看到「任务不涨进度」，没有任何报错可查。
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
        // 手写的 quests/*.yml 与加载器必须得到同样的值，否则「文件里写的数字」与「实际生效的数字」会不一致
        assertEquals(12, value("v: 012"));
        assertEquals(16, value("v: 0x10"));
        assertEquals(15, value("v: 0o17"));
        assertEquals(-3, value("v: -3"));
        assertEquals(64, value("v: 64"));
        assertEquals("1_000", value("v: 1_000"), "下划线分隔不认，保持字符串");
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
    @DisplayName("顶层是列表或标量时 readMap 返回 null（由调用方决定告警还是报错）")
    void nonMappingTopsReturnNull() {
        assertEquals(null, YamlText.readMap("- a\n- b\n"));
        assertEquals(null, YamlText.readMap("就一句话"));
        assertEquals(null, YamlText.readMap(""));
        assertEquals(true, YamlText.isMapping("id: one"));
        assertEquals(false, YamlText.isMapping("- a\n"));
    }
}
