package com.playerPlugin.playerTaskX.core.daily;

import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import com.playerPlugin.playerTaskX.api.registry.QuestRegistry;
import com.playerPlugin.playerTaskX.core.config.PluginConfig;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;
import com.playerPlugin.playerTaskX.core.reward.CoinReward;
import com.playerPlugin.playerTaskX.core.reward.MoneyReward;
import com.playerPlugin.playerTaskX.core.reward.PointsReward;
import com.playerPlugin.playerTaskX.core.storage.JdbcPlayerQuestRepository;
import com.playerPlugin.playerTaskX.core.storage.PlayerQuestRepository;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 每日任务：从全局池按玩家抽取、跨天重置、消耗货币刷新。
 *
 * <h2>为什么用确定性种子</h2>
 * 抽取结果由 {@code hash(playerId, 周期, 刷新次数)} 决定，而不是「随机一次再存库」：
 * 这样同一玩家在同一天内重登、换服、掉线重连都会看到同一批任务，
 * 既符合玩家预期，也避免通过反复重连来刷任务。
 * 数据库只记录「这个周期已经发过」，不记录抽取过程。
 *
 * <h2>周期定义</h2>
 * 周期按配置的重置小时计算：早于重置时刻属于前一天。
 * 例如重置点为 04:00 时，某日 03:30 仍算作前一天，符合「熬夜到凌晨还算今天」的直觉。
 */
public final class DailyService {

    private final PluginConfig config;
    private final QuestRegistry quests;
    private final PlayerQuestRepository repository;
    private final ProgressService progressService;
    private final MoneyReward moneyReward;
    private final PointsReward pointsReward;
    private final CoinReward coinReward;

    public DailyService(PluginConfig config, QuestRegistry quests, PlayerQuestRepository repository,
                        ProgressService progressService, MoneyReward moneyReward, PointsReward pointsReward,
                        CoinReward coinReward) {
        this.config = config;
        this.quests = quests;
        this.repository = repository;
        this.progressService = progressService;
        this.moneyReward = moneyReward;
        this.pointsReward = pointsReward;
        this.coinReward = coinReward;
    }

    /** 当前周期字符串，形如 {@code 2026-09-10}。 */
    public String currentPeriod() {
        return periodOf(LocalDateTime.now(), config.getDailyResetHour());
    }

    /**
     * 周期计算（纯函数，便于测试）。
     * <p>
     * 早于重置时刻的时间归属前一天，因此重置点为 04:00 时，
     * 某日 03:30 与前一天 23:00 属于同一周期。
     */
    public static String periodOf(LocalDateTime moment, int resetHour) {
        LocalDate date = moment.toLocalDate();
        if (moment.getHour() < resetHour) {
            date = date.minusDays(1);
        }
        return date.toString();
    }

    /**
     * 下一次重置时刻（纯函数，便于测试）。
     * <p>
     * 已经过了今天的重置点就取明天的，保证返回值永远在未来。
     */
    public static long nextResetMillis(LocalDateTime moment, int resetHour) {
        LocalDateTime reset = moment.toLocalDate().atTime(resetHour, 0);
        if (!reset.isAfter(moment)) {
            reset = reset.plusDays(1);
        }
        return reset.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** 每日任务候选池：配置了 daily.pool 就用它，否则取全部 DAILY 任务。 */
    public List<Quest> pool() {
        List<String> configured = config.getDailyPool();
        List<Quest> pool = new ArrayList<>();
        if (configured != null && !configured.isEmpty()) {
            for (String id : configured) {
                quests.find(id)
                        .filter(Quest::enabled)
                        .ifPresent(pool::add);
            }
        }
        if (pool.isEmpty()) {
            pool.addAll(quests.daily());
        }
        // 排序保证抽取顺序稳定：注册表顺序可能随加载顺序变化
        pool.sort(Comparator.comparing(Quest::id));
        return pool;
    }

    /**
     * 确保玩家拿到本周期的每日任务。
     * <p>
     * 玩家登录与定时检查都会调用；已在当前周期发放过则直接返回，不产生写操作。
     *
     * @return 是否重新发放（true 表示跨天重置过，调用方可据此提示玩家）
     */
    public boolean ensureAssigned(Player player) {
        return ensureAssigned(player.getUniqueId());
    }

    /**
     * 按玩家 id 发放（与 {@link #ensureAssigned(Player)} 同逻辑，便于脱离服务端测试）。
     */
    public boolean ensureAssigned(UUID playerId) {
        if (!config.isDailyEnabled()) {
            return false;
        }
        String period = currentPeriod();
        JdbcPlayerQuestRepository.DailyState state = dailyState(playerId);

        if (state != null && period.equals(state.period())) {
            return false;
        }

        assign(playerId, period, 0);
        return true;
    }

    /**
     * 消耗货币刷新玩家的每日任务（只影响该玩家自己）。
     *
     * @return 刷新结果，供命令与 GUI 决定提示内容
     */
    public RefreshResult refresh(Player player) {
        if (!config.isDailyEnabled()) {
            return RefreshResult.failed("每日任务未启用");
        }
        UUID playerId = player.getUniqueId();
        String period = currentPeriod();
        JdbcPlayerQuestRepository.DailyState state = dailyState(playerId);

        // 跨天时视为免费重发，不消耗玩家的刷新次数与货币
        boolean samePeriod = state != null && period.equals(state.period());
        int used = samePeriod ? state.refreshCount() : 0;

        if (samePeriod && used >= config.getDailyRefreshLimit()) {
            return RefreshResult.limitReached(config.getDailyRefreshLimit());
        }

        double cost = config.getDailyRefreshCost();
        if (samePeriod && cost > 0) {
            ChargeResult charged = charge(player, cost);
            if (!charged.success()) {
                return RefreshResult.failed(charged.reason());
            }
            assign(playerId, period, used + 1);
            return RefreshResult.success(cost, charged.currency());
        }

        assign(playerId, period, used + (samePeriod ? 1 : 0));
        return RefreshResult.success(samePeriod ? cost : 0.0, null);
    }

    /** 玩家在当前周期已刷新的次数。 */
    public int refreshCount(UUID playerId) {
        JdbcPlayerQuestRepository.DailyState state = dailyState(playerId);
        if (state == null || !currentPeriod().equals(state.period())) {
            return 0;
        }
        return state.refreshCount();
    }

    /** 剩余可刷新次数。 */
    public int remainingRefreshes(UUID playerId) {
        return Math.max(0, config.getDailyRefreshLimit() - refreshCount(playerId));
    }

    /** 玩家当前周期的每日任务（含已完成待领取）。 */
    public List<PlayerQuest> currentQuests(UUID playerId) {
        return progressService.questsOfType(playerId, QuestType.DAILY);
    }

    /**
     * 重新抽取并写入玩家的每日任务。
     * <p>
     * 先清空该玩家上一批每日任务（含进度），再按确定性种子抽新的一批，
     * 因此刷新是「换一批任务」而不是「追加」。
     */
    private void assign(UUID playerId, String period, int refreshCount) {
        List<Quest> pool = pool();
        if (pool.isEmpty()) {
            // 没有可用任务时也要记录周期，否则每次检查都会重复走一遍流程
            repository.transaction(() -> {
                repository.deleteByPlayerAndType(playerId, QuestType.DAILY);
                saveState(playerId, period, refreshCount);
            });
            return;
        }

        int amount = Math.min(config.getDailyAmount(), pool.size());
        List<Quest> drawn = draw(pool, playerId, period, refreshCount, amount);

        // 用同一时刻计算过期时间，保证同一批任务同时失效
        long now = System.currentTimeMillis();
        long expiresAt = nextResetMillis();

        repository.transaction(() -> {
            repository.deleteByPlayerAndType(playerId, QuestType.DAILY);
            for (Quest quest : drawn) {
                repository.save(PlayerQuest.assign(playerId, quest, now, expiresAt));
            }
            saveState(playerId, period, refreshCount);
        });

        // 索引必须跟着换，否则旧任务的下标仍在内存里，进度会记到新任务上
        progressService.forget(playerId, QuestType.DAILY);
        progressService.load(playerId);
    }

    /**
     * 确定性抽取：同一 (玩家, 周期, 刷新次数) 必然得到同一批任务。
     * <p>
     * 把刷新次数纳入种子，这样刷新后拿到的是「另一批」而不是同一批。
     * 抽成静态纯函数是为了能直接测试——「同玩家同日结果一致」是本功能的核心不变量。
     *
     * @param pool 已排序的候选池（顺序必须稳定，否则结果不确定）
     */
    public static List<Quest> draw(List<Quest> pool, UUID playerId, String period,
                                   int refreshCount, int amount) {
        long seed = playerId.getMostSignificantBits()
                ^ playerId.getLeastSignificantBits()
                ^ period.hashCode()
                ^ (refreshCount * 0x9E3779B97F4A7C15L);
        List<Quest> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, new Random(seed));
        return List.copyOf(shuffled.subList(0, Math.min(amount, shuffled.size())));
    }

    /** 下一次重置时刻的毫秒时间戳。 */
    private long nextResetMillis() {
        return nextResetMillis(LocalDateTime.now(), config.getDailyResetHour());
    }

    private JdbcPlayerQuestRepository.DailyState dailyState(UUID playerId) {
        if (repository instanceof JdbcPlayerQuestRepository jdbc) {
            return jdbc.findDailyState(playerId);
        }
        return null;
    }

    private void saveState(UUID playerId, String period, int refreshCount) {
        if (repository instanceof JdbcPlayerQuestRepository jdbc) {
            jdbc.saveDailyState(playerId, period, refreshCount, System.currentTimeMillis());
        }
    }

    /**
     * 扣费。
     * <p>
     * 货币种类由配置 {@code daily.refresh-currency} 决定：
     * {@code MONEY}（金币）/ {@code POINTS}（点券）/ {@code QUEST_COIN}（任务币）指定单一货币；
     * {@code AUTO} 则按金币 → 点券 → 任务币的顺序挑一个可用的。
     * AUTO 的默认顺序把任务币放最后：它通常是玩家攒着兑换奖励的货币，
     * 不该在玩家装了经济插件时被悄悄花掉。
     */
    private ChargeResult charge(Player player, double cost) {
        String currency = config.getDailyRefreshCurrency();
        return switch (currency) {
            case "MONEY" -> chargeMoney(player, cost);
            case "POINTS" -> chargePoints(player, cost);
            case "QUEST_COIN" -> chargeCoin(player, cost);
            default -> {
                // AUTO：按可用性依次尝试
                if (moneyReward.available()) {
                    yield chargeMoney(player, cost);
                }
                if (pointsReward.available()) {
                    yield chargePoints(player, cost);
                }
                if (coinReward != null) {
                    yield chargeCoin(player, cost);
                }
                yield new ChargeResult(false, "服务器未安装经济插件（Vault 或 PlayerPoints），也未启用任务币", null);
            }
        };
    }

    private ChargeResult chargeMoney(Player player, double cost) {
        if (!moneyReward.available()) {
            return new ChargeResult(false, "未安装 Vault 或经济插件，无法使用金币刷新", "money");
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(player.getUniqueId());
        if (moneyReward.balance(offlinePlayer) < cost) {
            return new ChargeResult(false, "金币不足，需要 " + moneyReward.describe(cost), "money");
        }
        if (moneyReward.withdraw(offlinePlayer, cost)) {
            return new ChargeResult(true, "", "money");
        }
        return new ChargeResult(false, "扣款失败", "money");
    }

    private ChargeResult chargePoints(Player player, double cost) {
        if (!pointsReward.available()) {
            return new ChargeResult(false, "未安装 PlayerPoints，无法使用点券刷新", "points");
        }
        int points = (int) Math.ceil(cost);
        if (pointsReward.balance(player.getUniqueId()) < points) {
            return new ChargeResult(false, "点券不足，需要 " + points, "points");
        }
        if (pointsReward.take(player.getUniqueId(), points)) {
            return new ChargeResult(true, "", "points");
        }
        return new ChargeResult(false, "扣款失败", "points");
    }

    private ChargeResult chargeCoin(Player player, double cost) {
        if (coinReward == null) {
            return new ChargeResult(false, "任务币未启用", "quest_coin");
        }
        long amount = (long) Math.ceil(cost);
        long balance = coinReward.balance(player.getUniqueId());
        if (balance < amount) {
            return new ChargeResult(false, "任务币不足，需要 " + amount + "（当前 " + balance + "）", "quest_coin");
        }
        if (coinReward.take(player.getUniqueId(), amount)) {
            return new ChargeResult(true, "", "quest_coin");
        }
        return new ChargeResult(false, "扣款失败", "quest_coin");
    }

    private record ChargeResult(boolean success, String reason, String currency) {
    }

    /** 刷新结果。 */
    public record RefreshResult(boolean success, double cost, String currency, String error, int limit) {

        public static RefreshResult success(double cost, String currency) {
            return new RefreshResult(true, cost, currency, "", 0);
        }

        public static RefreshResult failed(String error) {
            return new RefreshResult(false, 0, null, error, 0);
        }

        public static RefreshResult limitReached(int limit) {
            return new RefreshResult(false, 0, null, "今日刷新次数已用完", limit);
        }
    }
}
