package com.playerPlugin.playerTaskX.core.reward;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * 刷新费用可用的货币。
 *
 * <h2>兜底链</h2>
 * 服务器未必装经济插件，但「刷新任务」这个功能不该因此直接不可用。
 * 因此按 <b>金币 → 点券 → 经验</b> 的顺序自动挑一个可用的：
 * <ul>
 *   <li><b>MONEY</b>：Vault + 任一经济插件（大多数服务器都有）</li>
 *   <li><b>POINTS</b>：PlayerPoints（很多服务器用它做第二货币）</li>
 *   <li><b>EXP</b>：原版经验，任何服务端都有，因此这条兜底永远成立</li>
 * </ul>
 * 这样同一份配置在装了经济插件的服务器上扣钱、在没有的服务器上扣经验，
 * 管理员不必为「有没有装 Vault」分别写配置。
 *
 * <h2>为什么把「检测与扣费」放在枚举里</h2>
 * 扣费涉及三个来源各自的可用性判断、余额读取与扣除方式，散落在 DailyService 里
 * 会让「到底扣了哪种货币」难以追查。集中到这里后，DailyService 只负责
 * 「确定货币 → 调用扣除 → 组装提示」。
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
            (player, amount) -> PointsReward.takeFrom(player.getUniqueId(), (int) amount)),

    /** 经验（原版，总是可用）。 */
    EXP("exp", "经验",
            ExpReward::isAvailable,
            ExpReward::totalExperience,
            (player, amount) -> ExpReward.take(player, (int) amount));

    private final String id;
    private final String displayName;
    private final Supplier<Boolean> availability;
    private final BalanceReader balanceReader;
    private final Charger charger;

    CurrencyType(String id, String displayName, Supplier<Boolean> availability,
                 BalanceReader balanceReader, Charger charger) {
        this.id = id;
        this.displayName = displayName;
        this.availability = availability;
        this.balanceReader = balanceReader;
        this.charger = charger;
    }

    /** 语言文件与命令里使用的 id（与奖励类型 id 一致，因此 {@code reward.<id>} 语言键可直接复用）。 */
    public String id() {
        return id;
    }

    /** 显示名。 */
    public String displayName() {
        return displayName;
    }

    /** 该货币当前是否可用。 */
    public boolean available() {
        return Boolean.TRUE.equals(availability.get());
    }

    /** 玩家在该货币下的余额。 */
    public long balance(Player player) {
        return balanceReader.read(player);
    }

    /** 扣除；余额不足或扣款失败时返回 false 且不改动数据。 */
    public boolean charge(Player player, long amount) {
        return charger.charge(player, amount);
    }

    /**
     * 按配置的顺序挑选可用货币。
     * <p>
     * 配置的是一个<b>有序列表</b>，先出现的优先：写 {@code [EXP, MONEY]} 表示优先扣经验。
     * 列表里没有的货币视为禁用（如只写 {@code [EXP]} 就完全不碰经济插件）。
     * 配置为空、或配的货币在当前环境下都不可用时退回内置顺序——经验总是满足条件，
     * 因此这里不会返回 null。
     *
     * @param configured 配置的货币 id 顺序
     */
    public static CurrencyType select(List<String> configured) {
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
        return EXP;
    }

    /** 按枚举名或 id 解析，无法识别时返回 {@code null}（让调用方跳过该项继续往下找）。 */
    private static @Nullable CurrencyType parse(String name) {
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
