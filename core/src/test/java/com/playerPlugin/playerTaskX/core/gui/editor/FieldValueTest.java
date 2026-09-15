package com.playerPlugin.playerTaskX.core.gui.editor;

import com.playerPlugin.playerTaskX.api.schema.ConfigField;
import com.playerPlugin.playerTaskX.api.schema.ValueKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 聊天栏输入解析的测试：解析错了不会报错，只会把垃圾值写进配置，所以每种形状的合法/非法输入都钉住。
 * 纯逻辑，不碰服务端。
 */
class FieldValueTest {

    private static final ConfigField TEXT = ConfigField.text("message", "消息内容", "整句话，可用 & 颜色码");
    private static final ConfigField INTEGER = ConfigField.integer("amount", "所需数量", "完成该目标需要的次数");
    private static final ConfigField DECIMAL = ConfigField.decimal("money", "金额", "发放的金币数量");
    private static final ConfigField BOOLEAN = ConfigField.bool("as-player", "以玩家身份执行", "是 或 否");
    private static final ConfigField CANDIDATES =
            ConfigField.of("material", "目标材质", "方块或物品", ValueKind.BLOCK, ValueKind.ITEM);

    // ---------- TEXT ----------

    @Test
    @DisplayName("文本：去掉首尾空白后原样保留")
    void textKeepsRawContent() {
        assertEquals("你好 世界", FieldValue.parse(TEXT, "  你好 世界  "));
    }

    @Test
    @DisplayName("文本：只有空白等于清除，返回 null")
    void blankTextClears() {
        assertNull(FieldValue.parse(TEXT, ""));
        assertNull(FieldValue.parse(TEXT, "   "));
        assertNull(FieldValue.parse(TEXT, null));
    }

    // ---------- INTEGER ----------

    @Test
    @DisplayName("整数：解析成 Long，负数与首尾空白都能收")
    void integerParsesToLong() {
        assertEquals(3L, FieldValue.parse(INTEGER, "3"));
        assertEquals(-12L, FieldValue.parse(INTEGER, " -12 "));
        assertEquals(9_000_000_000L, FieldValue.parse(INTEGER, "9000000000"));
    }

    @Test
    @DisplayName("整数：非法输入报出「需要一个整数」，消息可直接给玩家看")
    void integerRejectsGarbage() {
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> FieldValue.parse(INTEGER, "三个"));
        assertTrue(error.getMessage().contains("整数"), error.getMessage());
        assertThrows(IllegalArgumentException.class, () -> FieldValue.parse(INTEGER, "3.5"));
        assertThrows(IllegalArgumentException.class, () -> FieldValue.parse(INTEGER, "9".repeat(30)));
    }

    // ---------- DECIMAL ----------

    @Test
    @DisplayName("小数：解析成 Double，整数写法也收")
    void decimalParsesToDouble() {
        assertEquals(1.5, FieldValue.parse(DECIMAL, "1.5"));
        assertEquals(2.0, FieldValue.parse(DECIMAL, "2"));
        assertEquals(-0.25, FieldValue.parse(DECIMAL, " -0.25 "));
    }

    @Test
    @DisplayName("小数：非法输入报出「需要一个小数」")
    void decimalRejectsGarbage() {
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> FieldValue.parse(DECIMAL, "一点五"));
        assertTrue(error.getMessage().contains("小数"), error.getMessage());
    }

    // ---------- BOOLEAN ----------

    @Test
    @DisplayName("开关：true/false/yes/no/on/off/1/0/是/否/开/关 全部认识，且大小写无关")
    void booleanAcceptsEverySpelling() {
        for (String yes : new String[]{"true", "TRUE", "Yes", "ON", "1", "是", "开"}) {
            assertEquals(Boolean.TRUE, FieldValue.parse(BOOLEAN, yes), yes);
        }
        for (String no : new String[]{"false", "FALSE", "No", "off", "0", "否", "关"}) {
            assertEquals(Boolean.FALSE, FieldValue.parse(BOOLEAN, no), no);
        }
    }

    @Test
    @DisplayName("开关：只认是/否两类写法，其余报出「只能填 是 或 否」")
    void booleanRejectsOtherWords() {
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> FieldValue.parse(BOOLEAN, "也许"));
        assertEquals("只能填 是 或 否", error.getMessage());
        assertThrows(IllegalArgumentException.class, () -> FieldValue.parse(BOOLEAN, "2"));
    }

    // ---------- CANDIDATES ----------

    @Test
    @DisplayName("候选：通配符与带命名空间的第三方 id 都放行，清单校验不在这里做")
    void candidatesPassThroughAnything() {
        assertEquals("STONE", FieldValue.parse(CANDIDATES, "STONE"));
        assertEquals("*", FieldValue.parse(CANDIDATES, " * "));
        assertEquals("itemsadder:myitems:ruby", FieldValue.parse(CANDIDATES, "itemsadder:myitems:ruby"));
    }

    @Test
    @DisplayName("候选：空串等于清除，不报错")
    void blankCandidatesClears() {
        assertNull(FieldValue.parse(CANDIDATES, "  "));
    }

    // ---------- display ----------

    @Test
    @DisplayName("回显：null 是空串，布尔是「是/否」")
    void displayHandlesNullAndBoolean() {
        assertEquals("", FieldValue.display(null));
        assertEquals("是", FieldValue.display(Boolean.TRUE));
        assertEquals("否", FieldValue.display(Boolean.FALSE));
    }

    @Test
    @DisplayName("回显：整数值不带小数尾巴，真小数才显示小数")
    void displayDropsTrailingZero() {
        assertEquals("3", FieldValue.display(3.0));
        assertEquals("3", FieldValue.display(3L));
        assertEquals("-12", FieldValue.display(-12.0));
        assertEquals("1.5", FieldValue.display(1.5));
        assertEquals("0.25", FieldValue.display(0.25));
    }

    @Test
    @DisplayName("回显：文本原样")
    void displayKeepsText() {
        assertEquals("你好", FieldValue.display("你好"));
        assertEquals("", FieldValue.display(""));
    }
}
