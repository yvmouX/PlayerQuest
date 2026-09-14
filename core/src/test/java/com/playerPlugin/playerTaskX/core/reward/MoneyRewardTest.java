package com.playerPlugin.playerTaskX.core.reward;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 金币奖励的可用性判据：<b>服务注册</b>，不是插件名。
 *
 * <p>这条判据两个方向都会出错，而且都不报错，只有管理员发现「配了却发不出钱」：
 * <ul>
 *   <li>只装了 Vault 而没有任何经济插件——插件名查得到，但服务是空的，钱照样发不出去，
 *       而老实现把这种情形写成「未安装 Vault 或没有经济插件」，排查时会被这句话带偏（踩过）；</li>
 *   <li>服务在册但经济插件被禁用——服务还在，钱同样发不出去。</li>
 * </ul>
 *
 * <p>因此这里直接 mock 服务管理器：<b>没有</b>任何叫 Vault 的插件，只注册 {@link Economy} 服务，
 * 金币就必须可用。
 */
class MoneyRewardTest {

    private static final double AMOUNT = 500.0;

    /** 注册了经济服务的服务管理器。 */
    private static ServicesManager servicesWith(Economy economy) {
        RegisteredServiceProvider<Economy> provider = mock(RegisteredServiceProvider.class);
        when(provider.getProvider()).thenReturn(economy);
        ServicesManager services = mock(ServicesManager.class);
        when(services.getRegistration(Economy.class)).thenReturn(provider);
        return services;
    }

    private static Economy enabledEconomy() {
        Economy economy = mock(Economy.class);
        when(economy.isEnabled()).thenReturn(true);
        return economy;
    }

    @Test
    @DisplayName("只要有经济服务注册，金币就可用——判据是服务，不是插件名")
    void economyServiceAloneMakesMoneyAvailable() {
        Economy economy = enabledEconomy();

        // 桩要在进入静态 mock 之前造好：在 thenReturn(...) 的实参里再做一次 when(...) 是嵌套桩，
        // Mockito 会直接以 UnfinishedStubbingException 拒绝
        ServicesManager services = servicesWith(economy);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);

            assertTrue(MoneyReward.isAvailable(), "服务在册就说明钱发得出去，与插件叫什么名字无关");
            assertEquals("", new MoneyReward().unavailableReason());
            // 刷新货币按同一条判据选：MONEY 在配置里就该被选中
            assertEquals(CurrencyType.MONEY, CurrencyType.select(List.of("MONEY", "EXP")),
                    "有经济服务却不用它、退回经验扣费，等于白装经济插件");
        }
    }

    @Test
    @DisplayName("服务在册但经济插件被禁用：视为不可用，刷新退回经验")
    void disabledEconomyCountsAsUnavailable() {
        Economy economy = mock(Economy.class);
        when(economy.isEnabled()).thenReturn(false);

        // 桩要在进入静态 mock 之前造好：在 thenReturn(...) 的实参里再做一次 when(...) 是嵌套桩，
        // Mockito 会直接以 UnfinishedStubbingException 拒绝
        ServicesManager services = servicesWith(economy);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);

            assertFalse(MoneyReward.isAvailable(), "禁用的经济插件发不出钱");
            assertTrue(new MoneyReward().unavailableReason().contains("Vault"),
                    "提示里要说清缺的是什么，实际: " + new MoneyReward().unavailableReason());
            assertEquals(CurrencyType.EXP, CurrencyType.select(List.of("MONEY", "EXP")));
        }
    }

    @Test
    @DisplayName("发奖与扣费都走服务本身：转账成功才算成功")
    void transfersGoThroughTheService() {
        Economy economy = enabledEconomy();
        when(economy.depositPlayer(any(OfflinePlayer.class), anyDouble()))
                .thenReturn(new EconomyResponse(AMOUNT, 0.0, EconomyResponse.ResponseType.SUCCESS, ""));
        when(economy.withdrawPlayer(any(OfflinePlayer.class), anyDouble()))
                .thenReturn(new EconomyResponse(AMOUNT, 0.0, EconomyResponse.ResponseType.FAILURE, "余额不足"));

        OfflinePlayer player = mock(OfflinePlayer.class);
        // 桩要在进入静态 mock 之前造好：在 thenReturn(...) 的实参里再做一次 when(...) 是嵌套桩，
        // Mockito 会直接以 UnfinishedStubbingException 拒绝
        ServicesManager services = servicesWith(economy);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);

            assertTrue(MoneyReward.deposit(player, AMOUNT));
            // 经济插件说失败就是失败：不能因为「调过了」就当成扣款成功
            assertFalse(MoneyReward.withdraw(player, AMOUNT));
            verify(economy).depositPlayer(eq(player), eq(AMOUNT));
        }
    }

    @Test
    @DisplayName("金额非正数时不碰经济服务（0 金币的任务不该产生一次转账）")
    void nonPositiveAmountDoesNotTouchTheService() {
        Economy economy = enabledEconomy();
        OfflinePlayer player = mock(OfflinePlayer.class);

        // 桩要在进入静态 mock 之前造好：在 thenReturn(...) 的实参里再做一次 when(...) 是嵌套桩，
        // Mockito 会直接以 UnfinishedStubbingException 拒绝
        ServicesManager services = servicesWith(economy);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);

            assertFalse(MoneyReward.deposit(player, 0.0));
            assertFalse(MoneyReward.withdraw(player, -1.0));
            verify(economy, never()).depositPlayer(any(OfflinePlayer.class), anyDouble());
            verify(economy, never()).withdrawPlayer(any(OfflinePlayer.class), anyDouble());
        }
    }
}
