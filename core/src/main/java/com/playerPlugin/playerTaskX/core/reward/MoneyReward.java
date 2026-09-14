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
 * <p>
 * 判据是「服务端有没有注册 {@link Economy} 服务」，不是「有没有叫 Vault 的插件」：
 * Vault 只是 API 桥，真正决定「钱发不发得出去」的是经济插件有没有把服务注册进来。
 * 没有可用服务时 {@link #available()} 为 false，编辑器与 GUI 会据此提示管理员，
 * 而不是静默发不出奖励。
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
                ConfigField.decimal("amount", "数量", 1000.0, "发放的金币数量")
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

    /**
     * 当前可用的经济服务；没有则返回 {@code null}。
     * <p>
     * <b>判据是服务注册，不是插件名</b>：Vault 只提供 API，钱由经济插件（EssentialsX、CMI…）
     * 实现，两边通过 {@link Economy} 服务对接——「有没有经济插件」这件事只有服务管理器知道。
     * 老实现先查 {@code SoftDependency.isPresent("Vault")}：装了 Vault 却没有经济插件时它照样放行，
     * 于是失败被写成「未安装 Vault 或没有经济插件」这句把两种情形混在一起的话——
     * 真机排查时正是这句话把「Vault 没装」和「没有经济插件」混为一谈（实测：Vault 装了、
     * 只是没有任何经济插件在册）。判据换成服务注册后，能装钱的只有那一种情形，措辞也就不再含糊。
     * <p>
     * <b>刻意不做静态缓存</b>：这就是一次 map 查找，而调用点（发奖、读余额、格式化、
     * 可用性校验）都不在进度热路径上；缓存反而把「经济插件被禁用/换掉」变成一个要重启才纠正的
     * 陈旧判断，也会让测试之间互相污染（静态缓存跨用例存活，而每个用例的服务端是各自 mock 的）。
     */
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
