package com.playerPlugin.playerTaskX.core.period;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import com.playerPlugin.playerTaskX.core.reward.MoneyReward;
import com.playerPlugin.playerTaskX.core.reward.PointsReward;

/**
 * 刷新费用可用的货币：只认金币（Vault 经济）与点券（PlayerPoints）。
 * 两者都没有时刷新就是不可用——{@link #select} 返回 {@code null} 并让调用方给出明确提示，刻意不留「免费刷新」之类的兜底。
 */
public enum CurrencyType {

    /** 金币（Vault）。 */
    MONEY("money", "金币",
            MoneyReward::isAvailable,
            player -> (long) MoneyReward.balanceOf(Bukkit.getOfflinePlayer(player.getUniqueId())),
            (player, amount) -> MoneyReward.withdraw(Bukkit.getOfflinePlayer(player.getUniqueId()), amount)),

    /** 点券（PlayerPoints）。 */
    POINTS("points", "点券",
            PointsReward::isAvailable,
            player -> PointsReward.balanceOf(player.getUniqueId()),
            (player, amount) -> PointsReward.takeFrom(player.getUniqueId(), (int) amount));

    private final String id;
    private final String displayName;
    private final BooleanSupplier availability;
    private final BalanceReader balanceReader;
    private final Charger charger;

    CurrencyType(String id, String displayName, BooleanSupplier availability,
                 BalanceReader balanceReader, Charger charger) {
        this.id = id;
        this.displayName = displayName;
        this.availability = availability;
        this.balanceReader = balanceReader;
        this.charger = charger;
    }

    /** 语言文件与命令里使用的 id（与同名奖励类型一致，因此 {@code reward.<id>} 语言键可复用）。 */
    public String id() {
        return id;
    }

    /** 显示名。 */
    public String displayName() {
        return displayName;
    }

    /** 该货币当前是否可用。 */
    public boolean available() {
        return availability.getAsBoolean();
    }

    /** 玩家在该货币下的余额。 */
    public long balance(Player player) {
        return balanceReader.read(player);
    }

    /** 扣除；余额不足或扣款失败时返回 false 且不改动数据。 */
    public boolean charge(Player player, long amount) {
        return charger.charge(player, amount);
    }

    /** 按配置的顺序挑选可用货币（有序列表先出现的优先，没写的视为禁用，配置为空或选不出可用项时退回内置顺序）；一个都没有时返回 {@code null}，调用方必须据此提示「刷新不可用」而不是当成免费刷新。 */
    public static @Nullable CurrencyType select(List<String> configured) {
        if (configured != null) {
            for (String name : configured) {
                CurrencyType type = parse(name);
                if (type != null && type.available()) {
                    return type;
                }
            }
        }
        for (CurrencyType type : values()) {
            if (type.available()) {
                return type;
            }
        }
        return null;
    }

    /** 一个货币都不可用时给玩家看的说明；提示文案只有这一处，避免 GUI 与命令各写一句。 */
    public static String unavailableReason() {
        return "本服务器没有可用的货币（需要经济插件或 PlayerPoints）";
    }

    /**
     * 按枚举名或 id 解析，无法识别时返回 {@code null}（让调用方跳过该项继续往下找）。
     * <p>
     * 包内可见：货币名要在单测里逐个钉住——单测环境两种货币都不可用，
     * 光靠 {@link #select} 分不清「名字没认出来」与「认出来了但服务器没有」。
     */
    static @Nullable CurrencyType parse(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        for (CurrencyType type : values()) {
            if (type.name().equals(normalized) || type.id.equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        return null;
    }

    /** 把双精度费用换算成货币的整数单位（所有货币都按「向上取整」收费，不产生零头）。 */
    public long toUnits(double cost) {
        return (long) Math.ceil(Math.max(0.0, cost));
    }

    /** 余额读取器。 */
    @FunctionalInterface
    private interface BalanceReader {
        long read(Player player);
    }

    /** 扣款器。 */
    @FunctionalInterface
    private interface Charger {
        boolean charge(Player player, long amount);
    }
}
