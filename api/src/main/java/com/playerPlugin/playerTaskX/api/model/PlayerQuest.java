package com.playerPlugin.playerTaskX.api.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家进行中的任务（运行期状态）。进度按「目标下标 → 计数」存，因此目标顺序一变旧进度就整体错位，
 * 靠 {@code structureHash} 识别并重置——比对与重置在 {@code ProgressService}，本类不做兼容。
 */
public final class PlayerQuest {

    private final UUID playerId;
    private final String questId;
    private final QuestType type;
    private final long assignedAt;
    private final long expiresAt;
    private final Map<Integer, Integer> progress = new LinkedHashMap<>();
    private QuestStatus status;

    /** 接手该任务时的目标列表结构摘要；比对由 {@code ProgressService} 做，不一致就重置进度（空串 = 旧数据，只补齐不重置）。 */
    private String structureHash = "";

    public PlayerQuest(UUID playerId, String questId, QuestType type,
                       long assignedAt, long expiresAt, QuestStatus status) {
        this.playerId = playerId;
        this.questId = questId;
        this.type = type;
        this.assignedAt = assignedAt;
        this.expiresAt = expiresAt;
        this.status = status == null ? QuestStatus.IN_PROGRESS : status;
    }

    /** 目标列表结构摘要；空串表示未知（旧数据）。 */
    public String structureHash() {
        return structureHash;
    }

    /** 设置结构摘要。 */
    public void structureHash(String hash) {
        this.structureHash = hash == null ? "" : hash;
    }

    public static PlayerQuest assign(UUID playerId, Quest quest, long now, long expiresAt) {
        return new PlayerQuest(playerId, quest.id(), quest.type(), now, expiresAt, QuestStatus.IN_PROGRESS);
    }

    public UUID playerId() {
        return playerId;
    }

    public String questId() {
        return questId;
    }

    public QuestType type() {
        return type;
    }

    public long assignedAt() {
        return assignedAt;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public QuestStatus status() {
        return status;
    }

    public void status(QuestStatus status) {
        this.status = status;
    }

    public boolean isActive() {
        return status == QuestStatus.IN_PROGRESS;
    }

    /** 全部进度快照（不可变副本）。 */
    public Map<Integer, Integer> progress() {
        return Map.copyOf(progress);
    }

    public int progress(int objectiveIndex) {
        return progress.getOrDefault(objectiveIndex, 0);
    }

    /** 设置进度并返回是否发生变化。 */
    public boolean setProgress(int objectiveIndex, int value) {
        int current = progress(objectiveIndex);
        if (current == value) return false;
        progress.put(objectiveIndex, value);
        return true;
    }

    /**
     * 累加进度，按目标所需数量封顶。
     *
     * @return 实际增加的数值（0 表示已达上限，无需保存）
     */
    public int addProgress(int objectiveIndex, int delta, int required) {
        if (delta <= 0) return 0;
        int current = progress(objectiveIndex);
        if (current >= required) return 0;
        int next = Math.min(required, current + delta);
        progress.put(objectiveIndex, next);
        return next - current;
    }

    /** 从持久化数据恢复进度。 */
    public void restoreProgress(Map<Integer, Integer> saved) {
        progress.clear();
        if (saved != null) {
            progress.putAll(saved);
        }
    }

    /** 目标是否已全部达成（超出下标范围的进度视为缺失）。 */
    public boolean isFullyCompleted(Quest quest) {
        for (int i = 0; i < quest.objectives().size(); i++) {
            if (progress(i) < quest.objectives().get(i).amount()) {
                return false;
            }
        }
        return true;
    }

    /** 总体完成度 0.0 ~ 1.0，用于进度条与变量。 */
    public double completionRatio(Quest quest) {
        int total = 0;
        int done = 0;
        for (int i = 0; i < quest.objectives().size(); i++) {
            int required = quest.objectives().get(i).amount();
            total += required;
            done += Math.min(required, progress(i));
        }
        return total == 0 ? 0.0 : (double) done / total;
    }
}
