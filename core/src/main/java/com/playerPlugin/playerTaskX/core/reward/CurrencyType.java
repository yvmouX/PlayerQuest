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
 * <h2>只有两种，且都可能不可用</h2>
 * <ul>
 *   <li><b>MONEY</b>：任一经济插件（经 Vault 注册的 {@code Economy} 服务；Vault、VaultUnlocked 都算）</li>
 *   <li><b>POINTS</b>：PlayerPoints（很多服务器用它做第二货币）</li>
 * </ul>
 * 这两样都没有时，刷新功能就是不可用——{@link #select} 返回 {@code null}，
 * 调用方给出明确提示（{@link #unavailableReason()}）。刻意<b>不</b>留一条「免费刷新」或
 * 「扣经验」的兜底：前者会让配错的 {@code refresh-cost} 看不出来，后者要靠读写玩家的
 * 等级与经验进度当余额，等于把一件小事做成一等公民。
 *
 * <h2>为什么把「检测与扣费」放在枚举里</h2>
 * 扣费涉及两个来源各自的可用性判断、余额读取与扣除方式，散落在 PeriodicService 里
 * 会让「到底扣了哪种货币」难以追查。集中到这里后，PeriodicService 只负责
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
            (player, amount) -> PointsReward.takeFrom(player.getUniqueId(), (int) amount));

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
     * 配置的是一个<b>有序列表</b>，先出现的优先：写 {@code [POINTS, MONEY]} 表示优先扣点券。
     * 列表里没有的货币视为禁用（如只写 {@code [POINTS]} 就完全不碰经济插件）。
     * 配置为空、或配的货币在当前环境下都不可用时退回内置顺序。
     *
     * @param configured 配置的货币 id 顺序
     * @return 可用的货币；服务器一个都没有时返回 {@code null}——
     *         调用方必须据此给出「刷新不可用」的明确提示，而不是当成免费刷新
     */
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
