package com.playerPlugin.playerTaskX.core.reward;

import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.reward.RewardType;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.List;

/**
 * 奖励：金币（经 Vault 的经济服务）。
 * 判据是服务端有没有注册 {@link Economy} 服务，而不是有没有叫 Vault 的插件——Vault 只是 API 桥。
 */
public final class MoneyReward implements RewardType {

    public static final String ID = "money";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "金币";
    }

    @Override
    public List<ConfigField> schema() {
        return List.of(
                ConfigField.decimal("amount", "数量", "发放的金币数量，可以是小数")
        );
    }

    @Override
    public void grant(org.bukkit.entity.Player player, QuestReward reward) {
        if (!isAvailable()) {
            return;
        }
        double amount = reward.decimal("amount", 0.0);
        if (amount <= 0) {
            return;
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(player.getUniqueId());
        deposit(offlinePlayer, amount);
    }

    @Override
    public boolean available() {
        return isAvailable();
    }

    @Override
    public String unavailableReason() {
        return isAvailable() ? "" : "未检测到经济插件，Vault 只提供接口";
    }

    // ------------------------------------------------------------------
    // 静态访问：供 CurrencyType 等无实例的调用方使用
    // ------------------------------------------------------------------

    /** 经济服务是否可用。 */
    public static boolean isAvailable() {
        return economy() != null;
    }

    /** 余额。 */
    public static double balanceOf(OfflinePlayer player) {
        Economy service = economy();
        return service == null ? 0.0 : service.getBalance(player);
    }

    /** 发放；返回是否成功。 */
    public static boolean deposit(OfflinePlayer player, double amount) {
        Economy service = economy();
        if (service == null || amount <= 0) {
            return false;
        }
        return service.depositPlayer(player, amount).transactionSuccess();
    }

    /** 扣款；返回是否成功。 */
    public static boolean withdraw(OfflinePlayer player, double amount) {
        Economy service = economy();
        if (service == null || amount <= 0) {
            return false;
        }
        return service.withdrawPlayer(player, amount).transactionSuccess();
    }

    /** 按服务器经济插件的格式渲染金额；不可用时退回纯数字。 */
    public static String format(double amount) {
        Economy service = economy();
        return service == null ? String.valueOf(amount) : service.format(amount);
    }

    /** 当前可用的经济服务，没有则返回 {@code null}；判据是服务管理器里注册的 {@link Economy} 服务而不是插件名，且刻意不做静态缓存（缓存会把「经济插件被禁用/换掉」变成要重启才纠正的陈旧判断）。 */
    private static Economy economy() {
        try {
            RegisteredServiceProvider<Economy> provider =
                    Bukkit.getServicesManager().getRegistration(Economy.class);
            Economy service = provider == null ? null : provider.getProvider();
            // 经济插件被禁用时它的服务仍在册，但钱发不出去
            return service != null && service.isEnabled() ? service : null;
        } catch (NoClassDefFoundError e) {
            // 没装 Vault：Economy 这个类根本加载不到。与「有 API 但没有经济插件」同解
            return null;
        } catch (Throwable ignored) {
            // 服务管理器不可用（单测环境、非 CraftBukkit 实现等）：视为没有经济服务
            return null;
        }
    }
}
