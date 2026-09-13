package com.playerPlugin.playerTaskX.core.quest;

import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.registry.QuestRegistry;
import com.playerPlugin.playerTaskX.core.storage.QuestClaimRepository;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 前置任务：任务链的解锁判定与配置校验。
 *
 * <h2>判定标准：已领取奖励</h2>
 * 「前置任务已完成」= 该任务<b>已领取过奖励</b>，依据是永久账本
 * {@link QuestClaimRepository}，而不是 {@code player_quest} 里的当前记录：
 * 每日任务跨天/刷新时记录会被整批删除，拿它当依据的话任务链第二天就断了。
 *
 * <h2>判定与校验在同一处</h2>
 * 「游戏内能不能做」与「编辑器里配得对不对」是同一套关系知识的两个面，
 * 分散实现必然漂移（运行时按一种口径、校验按另一种口径）。因此解锁判定
 * （{@link #unsatisfied}）与配置问题（{@link #problems}）都放在这里，
 * 调用方只需拿结论。
 *
 * <h2>什么时候会用到</h2>
 * <ul>
 *   <li>每日任务抽取：前置未满足的任务不进候选池，玩家不会抽到一个做不了的任务；</li>
 *   <li>领取奖励：已发放的任务若因定义变更而锁定，仍然领不到（把关只放在抽取值不值钱）；</li>
 *   <li>任务详情界面：列出前置与各自是否达成；</li>
 *   <li>编辑器与管理界面：把「前置不存在 / 成环 / 指向已禁用任务」标出来。</li>
 * </ul>
 *
 * <p>刻意不缓存玩家的领取结果：账本读取一次是一条按玩家索引的查询，而缓存要处理
 * 「刚领完奖」的失效时机，收益与风险不成比例。批量判定（每日抽取要遍历整个池）由调用方
 * 用 {@link #claimedIds(UUID)} 取一次快照再逐个判定。</p>
 */
public final class PrerequisiteService {

    private final QuestRegistry quests;
    private final QuestClaimRepository claims;

    public PrerequisiteService(QuestRegistry quests, QuestClaimRepository claims) {
        this.quests = quests;
        this.claims = claims;
    }

    // ------------------------------------------------------------------
    // 解锁判定
    // ------------------------------------------------------------------

    /**
     * 玩家已领取奖励的任务 id 快照。
     * <p>
     * 供批量判定使用：每日抽取要遍历整个候选池，逐个任务查一次账本会变成 N 次查询。
     */
    public Set<String> claimedIds(UUID playerId) {
        if (playerId == null) {
            return Set.of();
        }
        return claims.claimedQuestIds(playerId);
    }

    /**
     * 目标任务的未满足前置。
     * <p>
     * 「前置任务不存在」也会出现在结果里：它永远满足不了，因此必须继续挡住目标任务，
     * 而不是当作没有前置。这个死结由 {@link #problems(Quest)} 在编辑器/日志里报出来。
     *
     * @param claimedIds {@link #claimedIds(UUID)} 的快照
     */
    public List<String> unsatisfied(Set<String> claimedIds, Quest quest) {
        if (quest == null || !quest.hasPrerequisites()) {
            return List.of();
        }
        Set<String> claimed = claimedIds == null ? Set.of() : claimedIds;
        List<String> missing = new ArrayList<>();
        for (String prerequisiteId : quest.prerequisites()) {
            if (!claimed.contains(prerequisiteId)) {
                missing.add(prerequisiteId);
            }
        }
        return missing;
    }

    /** 单个任务的便捷判定（领取校验、界面）；需要成批判定时用 {@link #unsatisfied(Set, Quest)}。 */
    public List<String> unsatisfied(UUID playerId, Quest quest) {
        return unsatisfied(claimedIds(playerId), quest);
    }

    /** 前置是否全部满足（含「没有前置」）。 */
    public boolean isUnlocked(Set<String> claimedIds, Quest quest) {
        return unsatisfied(claimedIds, quest).isEmpty();
    }

    /** 单个任务是否已解锁。 */
    public boolean isUnlocked(UUID playerId, Quest quest) {
        return unsatisfied(playerId, quest).isEmpty();
    }

    // ------------------------------------------------------------------
    // 配置校验
    // ------------------------------------------------------------------

    /**
     * 前置关系的配置问题（空表示没问题）。
     * <p>
     * 四类问题都会让目标任务<b>永远解锁不了</b>，而玩家侧看到的只是「这个任务一直不出现」，
     * 因此必须在管理员能看到的地方说出来：
     * <ul>
     *   <li>前置任务不存在（含 id 写错）；</li>
     *   <li>把自己列为前置；</li>
     *   <li>前置关系成环（A 等 B、B 等 A，两边都领不到）；</li>
     *   <li>前置任务已被禁用（禁用后不会被抽取，也就永远拿不到）。</li>
     * </ul>
     */
    public List<String> problems(Quest quest) {
        if (quest == null || !quest.hasPrerequisites()) {
            return List.of();
        }
        List<String> problems = new ArrayList<>();
        for (String prerequisiteId : quest.prerequisites()) {
            if (prerequisiteId.equals(quest.id())) {
                problems.add("前置任务不能是自己: " + prerequisiteId);
                continue;
            }
            Quest prerequisite = quests.find(prerequisiteId).orElse(null);
            if (prerequisite == null) {
                problems.add("前置任务不存在: " + prerequisiteId);
            } else if (!prerequisite.enabled()) {
                problems.add("前置任务已禁用，将永远无法完成: " + prerequisiteId);
            }
        }
        String cycle = findCycle(quest);
        if (cycle != null) {
            problems.add("前置关系成环: " + cycle);
        }
        return problems;
    }

    /**
     * 从该任务出发沿前置边找到的第一个环，形如 {@code a -> b -> a}；无环返回 null。
     * <p>
     * 起点用的是<b>待校验的任务本身</b>而不是注册表里的同名任务：编辑器保存前的任务
     * 可能还没进注册表，此时恰好是最需要检出新配出来的环的时候。
     */
    @Nullable
    private String findCycle(Quest candidate) {
        if (candidate.id() == null || candidate.id().isBlank()) {
            // 没有 id 的任务谈不上「回到自己」，而且 DFS 的起点判定也依赖 id
            return null;
        }
        List<String> path = new ArrayList<>();
        Set<String> settled = new HashSet<>();
        return walk(candidate, candidate.id(), path, settled);
    }

    /**
     * @param candidate 待校验的任务：它的前置以自己为准，其它节点的前置取自注册表
     * @param node      当前节点
     * @param path      当前路径（从起点到 node）
     * @param settled   已经确定「从这里出发无环」的节点，避免共享子图被反复展开
     */
    @Nullable
    private String walk(Quest candidate, String node, List<String> path, Set<String> settled) {
        path.add(node);
        for (String next : prerequisitesOf(candidate, node)) {
            if (next.equals(node)) {
                // 自环由「前置任务不能是自己」那条报出来，同一件事不报两遍
                continue;
            }
            int seenAt = path.indexOf(next);
            if (seenAt >= 0) {
                // 回到路径上的某个节点即构成环；从该节点开始描述，避免把整条前缀也当成环的一部分
                return String.join(" -> ", path.subList(seenAt, path.size())) + " -> " + next;
            }
            if (settled.contains(next)) {
                continue;
            }
            String found = walk(candidate, next, path, settled);
            if (found != null) {
                return found;
            }
        }
        path.remove(path.size() - 1);
        settled.add(node);
        return null;
    }

    /** 某个节点的前置：起点用待校验任务自己的配置，其余节点用注册表里的定义。 */
    private List<String> prerequisitesOf(Quest candidate, String node) {
        if (node != null && node.equals(candidate.id())) {
            return candidate.prerequisites();
        }
        return quests.find(node).map(Quest::prerequisites).orElse(List.of());
    }
}
