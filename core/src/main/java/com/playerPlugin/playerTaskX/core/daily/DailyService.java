package com.playerPlugin.playerTaskX.core.daily;

import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import com.playerPlugin.playerTaskX.api.registry.QuestRegistry;
import com.playerPlugin.playerTaskX.core.config.PluginConfig;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;
import com.playerPlugin.playerTaskX.core.reward.MoneyReward;
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

    public DailyService(PluginConfig config, QuestRegistry quests, PlayerQuestRepository repository,
                        ProgressService progressService, MoneyReward moneyReward) {
        this.config = config;
        this.quests = quests;
        this.repository = repository;
        this.progressService = progressService;
        this.moneyReward = moneyReward;
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
     * 重置玩家的每日任务：重新抽取一批，<b>不扣费、不消耗刷新次数</b>（管理员工具用）。
     * <p>
     * 与 {@link #refresh(Player)} 的区别有两处，都是刻意的：
     * <ul>
     *   <li><b>不扣费</b>：管理员执行它是为了解决问题（玩家反馈任务做不了、任务配置刚改过），
     *       若还要先扣玩家的钱，这个工具就没法用了；</li>
     *   <li><b>不消耗刷新次数</b>：不占用玩家每天有限的刷新额度，
     *       否则管理员帮玩家重置几次就把玩家的额度用光了。</li>
     * </ul>
     * 抽取仍带刷新次数参与种子，因此重置后会拿到与当前不同的一批任务。
     */
    public RefreshResult resetDaily(Player player) {
        return doRefresh(player, false);
    }

    /**
     * 玩家自己刷新：扣费、消耗一次刷新次数。
     *
     * @return 刷新结果，供命令与 GUI 决定提示内容
     */
    public RefreshResult refresh(Player player) {
        return doRefresh(player, true);
    }

    /**
     * 刷新主体。
     *
     * @param charge 是否扣费并消耗次数
     */
    private RefreshResult doRefresh(Player player, boolean charge) {
        if (!config.isDailyEnabled()) {
            return RefreshResult.failed("每日任务未启用");
        }
        UUID playerId = player.getUniqueId();
        String period = currentPeriod();
        JdbcPlayerQuestRepository.DailyState state = dailyState(playerId);

        // 跨天时视为免费重发，不消耗玩家的刷新次数与货币
        boolean samePeriod = state != null && period.equals(state.period());
        int used = samePeriod ? state.refreshCount() : 0;

        if (charge && samePeriod && used >= config.getDailyRefreshLimit()) {
            return RefreshResult.limitReached(config.getDailyRefreshLimit());
        }

        double cost = config.getDailyRefreshCost();
        if (charge && samePeriod && cost > 0) {
            String failure = chargeMoney(player, cost);
            if (failure != null) {
                return RefreshResult.failed(failure);
            }
            assign(playerId, period, used + 1);
            return RefreshResult.success(cost);
        }

        if (charge) {
            assign(playerId, period, used + (samePeriod ? 1 : 0));
        } else {
            // 管理员重置：抽取用「已用次数 + 1」的种子以便换一批，
            // 但写回时保持原次数不变，避免占用玩家的刷新额度
            assign(playerId, period, used + 1, used);
        }
        // 免费重发与管理员重置都按 0 费用反馈，避免提示里出现根本没扣的钱
        return RefreshResult.success(0.0);
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

    /** 重新抽取并写入，写回与抽取用同一个次数。 */
    private void assign(UUID playerId, String period, int refreshCount) {
        assign(playerId, period, refreshCount, refreshCount);
    }

    /**
     * 重新抽取并写入玩家的每日任务。
     * <p>
     * 先清空该玩家上一批每日任务（含进度），再按确定性种子抽新的一批，
     * 因此刷新是「换一批任务」而不是「追加」。
     *
     * @param seedRefreshCount   参与抽取种子的次数：改变它才能换到不同的一批
     * @param storedRefreshCount 写回数据库的次数：管理员重置时保持不变，
     *                           避免占用玩家每日有限的刷新额度
     */
    private void assign(UUID playerId, String period, int seedRefreshCount, int storedRefreshCount) {
        List<Quest> pool = pool();
        if (pool.isEmpty()) {
            // 没有可用任务时也要记录周期，否则每次检查都会重复走一遍流程
            repository.transaction(() -> {
                repository.deleteByPlayerAndType(playerId, QuestType.DAILY);
                saveState(playerId, period, storedRefreshCount);
            });
            return;
        }

        int amount = Math.min(config.getDailyAmount(), pool.size());
        List<Quest> drawn = draw(pool, playerId, period, seedRefreshCount, amount);

        // 用同一时刻计算过期时间，保证同一批任务同时失效
        long now = System.currentTimeMillis();
        long expiresAt = nextResetMillis();

        repository.transaction(() -> {
            repository.deleteByPlayerAndType(playerId, QuestType.DAILY);
            for (Quest quest : drawn) {
                repository.save(PlayerQuest.assign(playerId, quest, now, expiresAt));
            }
            saveState(playerId, period, storedRefreshCount);
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
     * 扣金币（经 Vault）。
     * <p>
     * 刷新费用只用服务器的基础经济：这样管理员在别处看到的余额与这里的扣费是同一份数据，
     * 不会出现「插件内的一种货币玩家不知道从哪来」的困惑。
     *
     * @return 成功返回 null；失败返回给玩家看的原因
     */
    private String chargeMoney(Player player, double cost) {
        if (!moneyReward.available()) {
            return "未安装经济插件（Vault），无法扣除刷新费用";
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(player.getUniqueId());
        if (moneyReward.balance(offlinePlayer) < cost) {
            return "金币不足，需要 " + moneyReward.describe(cost);
        }
        if (!moneyReward.withdraw(offlinePlayer, cost)) {
            return "扣款失败，请稍后再试";
        }
        return null;
    }

    /** 刷新结果。 */
    public record RefreshResult(boolean success, double cost, String error, int limit) {

        public static RefreshResult success(double cost) {
            return new RefreshResult(true, cost, "", 0);
        }

        public static RefreshResult failed(String error) {
            return new RefreshResult(false, 0, error, 0);
        }

        public static RefreshResult limitReached(int limit) {
            return new RefreshResult(false, 0, "今日刷新次数已用完", limit);
        }
    }
}
