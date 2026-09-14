package com.playerPlugin.playerTaskX.core.reward;

import com.playerPlugin.playerTaskX.core.config.PluginConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 刷新费用货币的选择测试。
 *
 * <p>测试环境没有 Bukkit，因此金币与点券都被判定为不可用（它们内部对
 * {@code Bukkit.getPluginManager()} 的异常做了兜底）。这恰好覆盖了「既没装经济插件、
 * 也没装 PlayerPoints」这一场景——此时刷新必须明确不可用，而不是假装有个兜底货币。</p>
 *
 * <p>名字解析单独用 {@link CurrencyType#parse} 钉：单测环境两种货币都不可用，
 * 光看 {@code select} 的结果分不清「名字没认出来」与「认出来了但服务器没有」。</p>
 */
class CurrencyTypeTest {

    @Test
    @DisplayName("默认顺序为 金币 → 点券")
    void defaultOrderIsMoneyPoints() {
        assertEquals(List.of("MONEY", "POINTS"), new PluginConfig().getRefreshCurrency());
    }

    @Test
    @DisplayName("两种货币都没有时选出 null：不给免费的刷新，也不给假货币")
    void noCurrencyMeansNull() {
        assertNull(CurrencyType.select(List.of("MONEY", "POINTS")));
        assertNull(CurrencyType.select(null), "配置为空时退回内置顺序，同样一个都不可用");
        assertNull(CurrencyType.select(List.of()));
        assertNull(CurrencyType.select(List.of("nonsense", "???")));
        assertNotNull(CurrencyType.unavailableReason(), "不可用时必须有一句人能看懂的说明");
        assertTrue(CurrencyType.unavailableReason().contains("经济插件"),
                "说明里要写清缺什么，实际: " + CurrencyType.unavailableReason());
    }

    @Test
    @DisplayName("名字解析：枚举名 / id 都认、大小写与空白不敏感、认不出的返回 null")
    void namesAreCaseInsensitiveAndAcceptIds() {
        assertSame(CurrencyType.MONEY, CurrencyType.parse("MONEY"));
        assertSame(CurrencyType.MONEY, CurrencyType.parse(" money "));
        assertSame(CurrencyType.POINTS, CurrencyType.parse("points"));
        assertSame(CurrencyType.POINTS, CurrencyType.parse("PoInTs"));
        // 经验已不再是货币（连奖励类型都不是了）：写进配置只会被跳过，不会报错也不会生效
        assertNull(CurrencyType.parse("EXP"));
        assertNull(CurrencyType.parse(""));
        assertNull(CurrencyType.parse(null));
        assertDoesNotThrow(() -> CurrencyType.select(List.of("EXP", "MONEY")));
    }

    @Test
    @DisplayName("id 与同名奖励类型一致，可复用 reward.<id> 语言键")
    void idsMatchRewardTypeIds() {
        assertEquals("money", CurrencyType.MONEY.id());
        assertEquals("points", CurrencyType.POINTS.id());
    }

    @Test
    @DisplayName("费用换算为整数单位：向上取整，不产生零头")
    void convertsCostToWholeUnits() {
        assertEquals(1000L, CurrencyType.MONEY.toUnits(1000.0));
        assertEquals(1001L, CurrencyType.MONEY.toUnits(1000.5), "小数费用应向上取整");
        assertEquals(50L, CurrencyType.POINTS.toUnits(49.2));
        assertEquals(0L, CurrencyType.POINTS.toUnits(-5.0), "负数不应产生负费用");
    }

    @Test
    @DisplayName("显示名非空，供提示文案使用")
    void displayNamesArePresent() {
        for (CurrencyType type : CurrencyType.values()) {
            assertNotNull(type.displayName());
            assertTrue(!type.displayName().isBlank(), type + " 的显示名不应为空");
        }
    }
}
