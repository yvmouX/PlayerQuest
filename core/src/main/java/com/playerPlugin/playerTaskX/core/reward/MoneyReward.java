package com.playerPlugin.playerTaskX.core.reward;

import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.reward.RewardType;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;
import com.playerPlugin.playerTaskX.core.text.TextRenderer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.List;

/**
 * 奖励：金币（经 Vault）。
 * <p>
 * Vault 是软依赖——未安装时 {@link #available()} 为 false，
 * 编辑器与 GUI 会据此提示管理员，而不是静默发不出奖励。
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
        return isAvailable() ? "" : "未安装 Vault 或没有经济插件";
    }

    // ------------------------------------------------------------------
    // 静态访问：供 CurrencyType 等无实例的调用方使用
    // ------------------------------------------------------------------

    /** 缓存的 Vault 经济服务；null 表示尚未解析或不可用。 */
    private static Economy cachedEconomy;

    /** Vault 经济服务是否可用。 */
    public static boolean isAvailable() {
        return resolveEconomy() != null;
    }

    /** 余额。 */
    public static double balanceOf(OfflinePlayer player) {
        Economy service = resolveEconomy();
        return service == null ? 0.0 : service.getBalance(player);
    }

    /** 发放；返回是否成功。 */
    public static boolean deposit(OfflinePlayer player, double amount) {
        Economy service = resolveEconomy();
        if (service == null || amount <= 0) {
            return false;
        }
        return service.depositPlayer(player, amount).transactionSuccess();
    }

    /** 扣款；返回是否成功。 */
    public static boolean withdraw(OfflinePlayer player, double amount) {
        Economy service = resolveEconomy();
        if (service == null || amount <= 0) {
            return false;
        }
        return service.withdrawPlayer(player, amount).transactionSuccess();
    }

    /** 按服务器经济插件的格式渲染金额；不可用时退回纯数字。 */
    public static String format(double amount) {
        Economy service = resolveEconomy();
        return service == null ? String.valueOf(amount) : service.format(amount);
    }

    /**
     * 解析并缓存 Vault 经济服务。
     * <p>
     * 用 {@link #isPluginPresent(String)} 而不是直接调 {@code Bukkit.getPluginManager()}：
     * 后者在服务端尚未初始化时返回 null（例如单元测试、或插件在引导阶段被触碰），
     * 直接解引用会抛 NPE。软依赖检测失败只应表示「不可用」，不应让调用方崩掉。
     */
    private static Economy resolveEconomy() {
        if (cachedEconomy != null && cachedEconomy.isEnabled()) {
            return cachedEconomy;
        }
        cachedEconomy = null;
        if (!isPluginPresent("Vault")) {
            return null;
        }
        try {
            RegisteredServiceProvider<Economy> provider =
                    Bukkit.getServicesManager().getRegistration(Economy.class);
            if (provider != null) {
                cachedEconomy = provider.getProvider();
            }
        } catch (Throwable ignored) {
            // 服务管理器不可用（非 CraftBukkit 实现等）：视为没有经济服务
            cachedEconomy = null;
        }
        return cachedEconomy;
    }

    /** 供命令与 GUI 展示。 */
    public String describeAmount(double amount) {
        return format(amount);
    }

    /** 统一走 TextRenderer，保证与其它文本一致的格式处理。 */
    public String describe(double amount) {
        return TextRenderer.strip(format(amount));
    }

    /**
     * 探测软依赖插件是否已加载。
     * <p>
     * 独立成静态方法是因为 Vault / PlayerPoints / PlaceholderAPI 三处都需要同样的
     * 「服务端未初始化时不崩」的保护；放在这里避免各处重复写 try/catch。
     */
    static boolean isPluginPresent(String name) {
        try {
            return Bukkit.getPluginManager() != null
                    && Bukkit.getPluginManager().getPlugin(name) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
