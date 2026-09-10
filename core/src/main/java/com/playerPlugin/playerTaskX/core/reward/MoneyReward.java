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

    private Economy economy;

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
        Economy service = economy();
        if (service == null) {
            return;
        }
        double amount = reward.decimal("amount", 0.0);
        if (amount <= 0) {
            return;
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(player.getUniqueId());
        service.depositPlayer(offlinePlayer, amount);
    }

    @Override
    public boolean available() {
        return economy() != null;
    }

    @Override
    public String unavailableReason() {
        return economy() == null ? "未安装 Vault 或没有经济插件" : "";
    }

    /** 延迟解析 Vault 经济服务：插件启动顺序不保证 Vault 已就绪，因此每次用时探测并缓存。 */
    private Economy economy() {
        if (economy != null && economy.isEnabled()) {
            return economy;
        }
        economy = null;
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (provider != null) {
            economy = provider.getProvider();
        }
        return economy;
    }

    /** 供命令与 GUI 展示。 */
    public String format(double amount) {
        Economy service = economy();
        return service == null ? String.valueOf(amount) : service.format(amount);
    }

    /** 供刷新费用扣除使用；返回是否扣款成功。 */
    public boolean withdraw(OfflinePlayer player, double amount) {
        Economy service = economy();
        if (service == null || amount <= 0) {
            return false;
        }
        return service.withdrawPlayer(player, amount).transactionSuccess();
    }

    /** 供 GUI 展示余额。 */
    public double balance(OfflinePlayer player) {
        Economy service = economy();
        return service == null ? 0.0 : service.getBalance(player);
    }

    /** 统一走 TextRenderer，保证与其它文本一致的格式处理。 */
    public String describe(double amount) {
        return TextRenderer.strip(format(amount));
    }
}
