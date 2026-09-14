package com.playerPlugin.playerTaskX.core.period;

import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import com.playerPlugin.playerTaskX.api.registry.QuestRegistry;
import com.playerPlugin.playerTaskX.core.config.PeriodSettings;
import com.playerPlugin.playerTaskX.core.config.PluginConfig;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;
import com.playerPlugin.playerTaskX.core.quest.PrerequisiteService;
import com.playerPlugin.playerTaskX.core.reward.CurrencyType;
import com.playerPlugin.playerTaskX.core.reward.MoneyReward;
import com.playerPlugin.playerTaskX.core.storage.PlayerQuestRepository;
import cn.yvmou.ylib.message.MessageService;
import cn.yvmou.ylib.scheduler.UniversalScheduler;
import cn.yvmou.ylib.scheduler.UniversalTask;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 周期任务：每日 / 每周 / 每月 / 自定义四种周期，各自从池里按玩家抽取、过期作废、可消耗货币刷新。
 *
 * <h2>四种周期是同一套逻辑</h2>
 * 差别只有「周期怎么算」与「配置读哪一段」，因此这里所有方法都带一个 {@link QuestType}：
 * 抽谁、发给谁、按哪个周期判重、刷新扣谁的费用。周期算法本身在 {@link Periods}（纯函数、可单测）。
 * 登录时发放由 {@code PlayerListener} 触发 {@link #ensureAssigned}；挂线玩家的跨期检查由
 * {@link #startResetCheck} 的定时器负责——定时是必需的，不能只在登录时判断。
 *
 * <h2>为什么用确定性种子</h2>
 * 抽取结果由 {@code hash(playerId, 周期, 刷新次数)} 决定，而不是「随机一次再存库」：
 * 这样同一玩家在同一周期内重登、换服、掉线重连都会看到同一批任务，
 * 既符合玩家预期，也避免通过反复重连来刷任务。
 * 数据库只记录「这个周期已经发过」，不记录抽取过程。
 */
public final class PeriodicService {

    private final PluginConfig config;
    private final QuestRegistry quests;
    private final PlayerQuestRepository repository;
    private final ProgressService progressService;
    private final PrerequisiteService prerequisites;
    private UniversalTask resetTask;

    public PeriodicService(PluginConfig config, QuestRegistry quests, PlayerQuestRepository repository,
                           ProgressService progressService, PrerequisiteService prerequisites) {
        this.config = config;
        this.quests = quests;
        this.repository = repository;
        this.progressService = progressService;
        this.prerequisites = prerequisites;
    }

    /** 已启用的周期类型（配置里 enabled 的那些），固定按 每日→每周→每月→自定义 的顺序。 */
    public List<QuestType> enabledTypes() {
        List<QuestType> types = new ArrayList<>();
        for (QuestType type : QuestType.values()) {
            if (type.isPeriodic() && settings(type).isEnabled()) {
                types.add(type);
            }
        }
        return types;
    }

    /** 某种周期的配置。 */
    public PeriodSettings settings(QuestType type) {
        return config.periodic(type);
    }

    /** 当前周期标识，形如 {@code 2026-09-10} / {@code W2026-09-14} / {@code C3d#6893}。 */
    public String currentPeriod(QuestType type) {
        return Periods.periodOf(type, settings(type), LocalDateTime.now());
    }

    /**
     * 启动跨期检查定时器：挂着不下线的玩家也必须换任务。
     * <p>
     * 调用方（入口类）只负责在启停时机上调用，轮询参数与发放逻辑都在本类——
     * 「什么时候该重发」是周期任务的领域知识，不该散落在装配类里。
     *
     * @param onlinePlayers 在线玩家供应器，延迟求值（装配完成时还没有玩家）
     */
    public void startResetCheck(UniversalScheduler scheduler, MessageService messages,
                                Supplier<Collection<? extends Player>> onlinePlayers) {
        if (!config.isAnyPeriodicEnabled()) {
            return;
        }
        resetTask = scheduler.runTimer(() -> {
            for (Player player : onlinePlayers.get()) {
                for (QuestType type : ensureAssigned(player)) {
                    messages.send(player, "periodic.reset", Periods.label(type));
                }
            }
        }, 100L, 6000L);
    }

    /** 停止跨期检查定时器（插件禁用时调用；未启动过则为空操作）。 */
    public void shutdown() {
        if (resetTask != null) {
            resetTask.cancel();
            resetTask = null;
        }
    }

    /**
     * 某种周期的候选池：配置里写了 {@code pool} 就用它，否则取该类型的全部任务。
     * <p>
     * 池子按 id 排序：注册表顺序可能随加载顺序变化，而不稳定的池会让抽取结果不确定。
     */
    public List<Quest> pool(QuestType type) {
        List<String> configured = settings(type).pool();
        List<Quest> pool = new ArrayList<>();
        if (configured != null && !configured.isEmpty()) {
            for (String id : configured) {
                quests.find(id)
                        .filter(Quest::enabled)
                        .filter(quest -> quest.type() == type)
                        .ifPresent(pool::add);
            }
        }
        if (pool.isEmpty()) {
            pool.addAll(quests.ofType(type));
        }
        pool.sort(Comparator.comparing(Quest::id));
        return pool;
    }

    /**
     * 该玩家当前可抽取的池：全局池去掉前置尚未满足的任务。
     * <p>
     * 前置未满足的任务<b>不进池</b>，玩家因此不会抽到一个做不了的任务。
     * 代价是锁住的任务在界面里完全不出现——这是刻意的：抽到再做不了才是真的坑。
     * <p>
     * 前置判定放在抽取前（而不是抽到后再补抽）：抽取种子只由
     * (玩家, 周期, 刷新次数) 决定，加一次过滤就让「同一玩家同一周期结果一致」不再成立。
     * 因此前置满足与否会直接改变本周期抽到的那一批，这符合直觉——解锁后新任务才会出现。
     *
     * @param playerId 玩家；null 时按「没有任何前置满足」处理（全锁的任务排除）
     */
    public List<Quest> availablePool(UUID playerId, QuestType type) {
        List<Quest> pool = pool(type);
        Set<String> claimed = prerequisites.claimedIds(playerId);
        return pool.stream()
                .filter(quest -> prerequisites.isUnlocked(claimed, quest))
                .toList();
    }

    /**
     * 确保玩家拿到每种已启用周期的当前任务。
     * <p>
     * 玩家登录与定时检查都会调用；某个周期已在当前周期发放过就跳过它，不产生写操作。
     *
     * @return 本次真正重新发放的周期类型（空表示什么都没变），调用方可据此提示玩家
     */
    public List<QuestType> ensureAssigned(Player player) {
        return ensureAssigned(player.getUniqueId());
    }

    /** 按玩家 id 发放（与 {@link #ensureAssigned(Player)} 同逻辑，便于脱离服务端测试）。 */
    public List<QuestType> ensureAssigned(UUID playerId) {
        List<QuestType> assigned = new ArrayList<>();
        for (QuestType type : enabledTypes()) {
            String period = currentPeriod(type);
            PlayerQuestRepository.PeriodState state = repository.findPeriodState(playerId, type);
            if (state != null && period.equals(state.period())) {
                continue;
            }
            assign(playerId, type, period, 0);
            assigned.add(type);
        }
        return assigned;
    }

    /**
     * 重置玩家某种周期的任务：重新抽取一批，<b>不扣费、不消耗刷新次数</b>（管理员工具用）。
     * <p>
     * 与 {@link #refresh(Player, QuestType)} 的区别有两处，都是刻意的：
     * <ul>
     *   <li><b>不扣费</b>：管理员执行它是为了解决问题（玩家反馈任务做不了、任务配置刚改过），
     *       若还要先扣玩家的钱，这个工具就没法用了；</li>
     *   <li><b>不消耗刷新次数</b>：不占用玩家本周期有限的刷新额度，
     *       否则管理员帮玩家重置几次就把玩家的额度用光了。</li>
     * </ul>
     * 抽取仍带刷新次数参与种子，因此重置后会拿到与当前不同的一批任务。
     */
    public RefreshResult resetPeriod(Player player, QuestType type) {
        return doRefresh(player, type, false);
    }

    /**
     * 玩家自己刷新某种周期：扣费、消耗一次刷新次数。
     *
     * @return 刷新结果，供命令与 GUI 反馈给玩家
     */
    public RefreshResult refresh(Player player, QuestType type) {
        return doRefresh(player, type, true);
    }

    /**
     * 刷新主体。
     *
     * @param charge 是否扣费并消耗次数
     */
    private RefreshResult doRefresh(Player player, QuestType type, boolean charge) {
        PeriodSettings settings = settings(type);
        String label = Periods.label(type);
        if (!settings.isEnabled()) {
            return RefreshResult.of("quest.refresh-failed", label + "任务未启用");
        }
        UUID playerId = player.getUniqueId();
        String period = currentPeriod(type);
        PlayerQuestRepository.PeriodState state = repository.findPeriodState(playerId, type);

        // 跨周期时视为免费重发，不消耗玩家的刷新次数与货币
        boolean samePeriod = state != null && period.equals(state.period());
        int used = samePeriod ? state.refreshCount() : 0;

        if (charge && samePeriod && used >= settings.refreshLimit()) {
            return RefreshResult.of("quest.refresh-limit", label, settings.refreshLimit());
        }

        double cost = settings.refreshCost();
        if (charge && samePeriod && cost > 0) {
            // 货币顺序由配置决定（默认「金币 → 点券 → 经验」）：装了经济插件扣钱，
            // 没装的服务器扣经验，刷新功能在任何服务端上都可用
            CurrencyType currency = CurrencyType.select(config.getRefreshCurrency());
            long units = currency.toUnits(cost);
            if (!currency.charge(player, units)) {
                return RefreshResult.of("quest.refresh-failed", currency.displayName() + "不足，需要 " + units
                        + "（当前 " + currency.balance(player) + "）");
            }
            assign(playerId, type, period, used + 1);
            return new RefreshResult(List.of(
                    new RefreshResult.Reply("quest.refreshed", label),
                    new RefreshResult.Reply("quest.refresh-cost", formatCost(cost))));
        }

        if (charge) {
            assign(playerId, type, period, used + (samePeriod ? 1 : 0));
        } else {
            // 管理员重置：抽取用「已用次数 + 1」的种子以便换一批，
            // 但写回时保持原次数不变，避免占用玩家的刷新额度
            assign(playerId, type, period, used + 1, used);
        }
        // 免费重发与管理员重置都不提费用，避免提示里出现根本没扣的钱
        return RefreshResult.of("quest.refreshed", label);
    }

    /** 玩家在当前周期已刷新的次数。 */
    public int refreshCount(UUID playerId, QuestType type) {
        PlayerQuestRepository.PeriodState state = repository.findPeriodState(playerId, type);
        if (state == null || !currentPeriod(type).equals(state.period())) {
            return 0;
        }
        return state.refreshCount();
    }

    /** 剩余可刷新次数。 */
    public int remainingRefreshes(UUID playerId, QuestType type) {
        return Math.max(0, settings(type).refreshLimit() - refreshCount(playerId, type));
    }

    /** 玩家当前周期的任务（含已完成待领取）。 */
    public List<PlayerQuest> currentQuests(UUID playerId, QuestType type) {
        return progressService.questsOfType(playerId, type);
    }

    /**
     * 把刷新费用渲染成给玩家看的文案，如「1,000 金币」「1000 经验」。
     * <p>
     * 金币交给 Vault 的格式化（与服务器经济插件显示一致），其它货币是整数，直接用其显示名。
     * 放在这里而不是各个调用点：费用文案与实际扣费必须用同一套货币推断，
     * 分开写迟早会不一致。
     */
    public String formatCost(double cost) {
        CurrencyType currency = CurrencyType.select(config.getRefreshCurrency());
        if (currency == CurrencyType.MONEY) {
            return MoneyReward.format(cost);
        }
        return currency.toUnits(cost) + " " + currency.displayName();
    }

    /** 重新抽取并写入，写回与抽取用同一个次数。 */
    private void assign(UUID playerId, QuestType type, String period, int refreshCount) {
        assign(playerId, type, period, refreshCount, refreshCount);
    }

    /**
     * 重新抽取并写入玩家的某种周期任务。
     * <p>
     * 先清空该玩家上一批这种周期的任务（含进度），再按确定性种子抽新的一批，
     * 因此刷新是「换一批任务」而不是「追加」。
     *
     * @param seedRefreshCount   参与抽取种子的次数：改变它才能换到不同的一批
     * @param storedRefreshCount 写回数据库的次数：管理员重置时保持不变，
     *                           避免占用玩家本周期有限的刷新额度
     */
    private void assign(UUID playerId, QuestType type, String period,
                        int seedRefreshCount, int storedRefreshCount) {
        // 前置未满足的任务不进池：玩家不该抽到一个被锁住的任务
        List<Quest> pool = availablePool(playerId, type);
        if (pool.isEmpty()) {
            // 没有可用任务时也要记录周期，否则每次检查都会重复走一遍流程
            repository.transaction(() -> {
                repository.deleteByPlayerAndType(playerId, type);
                saveState(playerId, type, period, storedRefreshCount);
            });
            progressService.forget(playerId, type);
            return;
        }

        int amount = Math.min(settings(type).amount(), pool.size());
        List<Quest> drawn = draw(pool, playerId, period, seedRefreshCount, amount);

        // 用同一时刻计算过期时间，保证同一批任务同时失效
        long now = System.currentTimeMillis();
        long expiresAt = Periods.nextResetMillis(type, settings(type), LocalDateTime.now());

        repository.transaction(() -> {
            repository.deleteByPlayerAndType(playerId, type);
            for (Quest quest : drawn) {
                PlayerQuest assigned = PlayerQuest.assign(playerId, quest, now, expiresAt);
                // 记下接手时的目标结构：日后定义变过就能检测出进度下标错位并重置
                assigned.structureHash(ProgressService.structureHash(quest));
                repository.save(assigned);
            }
            saveState(playerId, type, period, storedRefreshCount);
        });

        // 索引必须跟着换，否则旧任务的下标仍在内存里，进度会记到新任务上
        progressService.forget(playerId, type);
        progressService.load(playerId);
    }

    /**
     * 确定性抽取：同一 (玩家, 周期, 刷新次数) 必然得到同一批任务。
     * <p>
     * 把刷新次数纳入种子，这样刷新后拿到的是「另一批」而不是同一批。
     * 抽成静态纯函数是为了能直接测试——「同玩家同周期结果一致」是本功能的核心不变量。
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

    private void saveState(UUID playerId, QuestType type, String period, int refreshCount) {
        repository.savePeriodState(playerId, type, period, refreshCount, System.currentTimeMillis());
    }

    /**
     * 刷新结果：要发给玩家的提示序列（成功与失败都在里面）。
     * <p>
     * 直接带上回复而不是让调用方拿 {@code success/cost/limit/error} 自己拼：
     * 玩家命令、管理员命令、GUI 三个入口各拼一次，措辞迟早会不一致，
     * 而玩家会把「同一个功能两种说法」当成 bug。
     */
    public record RefreshResult(List<Reply> replies) {

        /** 一条提示：语言键 + 占位符参数。 */
        public record Reply(String key, Object... args) {
        }

        /** 单条提示的便捷构造。 */
        static RefreshResult of(String key, Object... args) {
            return new RefreshResult(List.of(new Reply(key, args)));
        }

        /** 按顺序发给接收者。措辞只在这里定义一次。 */
        public void report(MessageService messages, CommandSender receiver) {
            for (Reply reply : replies) {
                messages.send(receiver, reply.key(), reply.args());
            }
        }
    }
}
