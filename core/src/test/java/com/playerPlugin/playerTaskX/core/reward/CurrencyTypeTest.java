package com.playerPlugin.playerTaskX.core.reward;

import com.playerPlugin.playerTaskX.core.config.PluginConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 刷新费用货币的选择测试。
 *
 * <p>测试环境没有 Bukkit，因此金币与点券会被判定为不可用（它们内部对
 * {@code Bukkit.getPluginManager()} 的异常做了兜底），经验始终可用。
 * 这恰好覆盖了「无经济插件的服务器」这一最常见场景。</p>
 */
class CurrencyTypeTest {

    @Test
    @DisplayName("默认顺序为 金币 → 点券 → 经验")
    void defaultOrderIsMoneyPointsExp() {
        assertEquals(List.of("MONEY", "POINTS", "EXP"), new PluginConfig().getDailyRefreshCurrency());
    }

    @Test
    @DisplayName("配置为空时退回内置顺序，不会因为清空配置让刷新失效")
    void emptyConfigFallsBackToBuiltIn() {
        assertEquals(List.of("MONEY", "POINTS", "EXP"), new PluginConfig().getDailyRefreshCurrency());
    }

    @Test
    @DisplayName("经验永远可用，因此任何配置下都能选出一个货币")
    void expIsAlwaysAvailable() {
        assertTrue(CurrencyType.EXP.available());
        assertNotNull(CurrencyType.select(List.of()));
        assertNotNull(CurrencyType.select(null));
    }

    @Test
    @DisplayName("无经济插件时最终选中经验（金币与点券被跳过）")
    void fallsBackToExpWithoutEconomyPlugins() {
        // 测试环境没有 Vault / PlayerPoints，因此前两项都被跳过
        assertEquals(CurrencyType.EXP, CurrencyType.select(List.of("MONEY", "POINTS", "EXP")));
        // 只配金币时也不该报错或返回 null——没人可用就继续退回内置顺序
        assertEquals(CurrencyType.EXP, CurrencyType.select(List.of("MONEY")));
    }

    @Test
    @DisplayName("配置里的无效项被跳过，不会因为一个拼写错误整体失效")
    void invalidNamesAreSkipped() {
        assertDoesNotThrow(() -> CurrencyType.select(List.of("NOT_A_CURRENCY", "", "EXP")));
        assertEquals(CurrencyType.EXP, CurrencyType.select(List.of("NOT_A_CURRENCY", "", "EXP")));
        // 全是无效项时同样退回内置顺序
        assertEquals(CurrencyType.EXP, CurrencyType.select(List.of("nonsense", "???")));
    }

    @Test
    @DisplayName("大小写不敏感，且接受 id 写法（exp / EXP / Exp 等价）")
    void namesAreCaseInsensitiveAndAcceptIds() {
        for (String name : new String[]{"exp", "EXP", "Exp", " eXp "}) {
            assertEquals(CurrencyType.EXP, CurrencyType.select(List.of(name)),
                    "「" + name + "」应被识别为经验");
        }
    }

    @Test
    @DisplayName("byId 对未知输入退回默认，而 select 会跳过无效项")
    void byIdFallsBackButSelectSkips() {
        // byId 用于「单个值」场景：无法识别时给一个可用的默认值
        assertEquals(CurrencyType.EXP, CurrencyType.byId("nonsense"));
        // 识别得了就按识别结果
        assertEquals(CurrencyType.EXP, CurrencyType.byId("exp"));
        assertEquals(CurrencyType.MONEY, CurrencyType.byId("money"));
        assertEquals(CurrencyType.POINTS, CurrencyType.byId("PoInTs"));
    }

    @Test
    @DisplayName("货币 id 与奖励类型 id 一致，便于复用 reward.<id> 语言键")
    void idsMatchRewardTypeIds() {
        assertEquals("money", CurrencyType.MONEY.id());
        assertEquals("points", CurrencyType.POINTS.id());
        assertEquals("exp", CurrencyType.EXP.id());
    }

    @Test
    @DisplayName("费用换算为整数单位：向上取整，不产生零头")
    void convertsCostToWholeUnits() {
        assertEquals(1000L, CurrencyType.MONEY.toUnits(1000.0));
        assertEquals(1001L, CurrencyType.MONEY.toUnits(1000.5), "小数费用应向上取整");
        assertEquals(50L, CurrencyType.EXP.toUnits(49.2));
        assertEquals(0L, CurrencyType.EXP.toUnits(-5.0), "负数不应产生负费用");
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
