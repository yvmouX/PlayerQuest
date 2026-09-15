package com.playerPlugin.playerTaskX.core.gui.editor;

import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.model.QuestType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 编辑器里的任务草稿：可变，改完由 {@link #toQuest()} 交给 QuestAdminService 保存。
 * 改动只留在内存里、不碰注册表，因此中途允许是坏的（没 id、没目标）——坏在哪由界面上的校验提示逐条说；目标/奖励存的是作者写的那份配置，预设引用要原样保住。
 */
public final class QuestDraft {

    /** 目标或奖励的统一视图：两者形状一样（类型 + 作者写的配置），编辑界面只认这一种，不必到处 instanceof。 */
    public record Node(String type, Map<String, Object> authored) {
    }

    private String id = "";
    private String name = "";
    private String icon = "PAPER";
    private String category = "";
    private QuestType type = QuestType.NORMAL;
    private double refreshCost;
    private boolean enabled = true;
    private final List<String> description = new ArrayList<>();
    private final List<QuestObjective> objectives = new ArrayList<>();
    private final List<QuestReward> rewards = new ArrayList<>();

    private QuestDraft() {
    }

    /** 新任务的空草稿：id 与名称待填，默认启用。 */
    public static QuestDraft creating() {
        return new QuestDraft();
    }

    /** 编辑现有任务：按 {@code authored}（作者写的那份）取目标与奖励，免得把预设展开后的生效值存回去。 */
    public static QuestDraft of(Quest quest) {
        QuestDraft draft = new QuestDraft();
        draft.id = blankToEmpty(quest.id());
        draft.name = blankToEmpty(quest.name());
        draft.icon = quest.icon();
        draft.category = blankToEmpty(quest.category());
        draft.type = quest.type();
        draft.refreshCost = quest.refreshCost();
        draft.enabled = quest.enabled();
        draft.description.addAll(quest.description());
        for (QuestObjective objective : quest.objectives()) {
            draft.addNode(false, new Node(objective.type(), objective.authored()));
        }
        for (QuestReward reward : quest.rewards()) {
            draft.addNode(true, new Node(reward.type(), reward.authored()));
        }
        return draft;
    }

    /** 当前草稿对应的任务定义（校验与保存都用它）。 */
    public Quest toQuest() {
        return new Quest(id, name, description, icon, category, type, objectives, rewards, refreshCost, enabled);
    }

    // ---------- 目标与奖励 ----------

    /** 该类别在任务里的顺序（奖励按顺序发放与展示），因此顺序是有意义的，编辑界面可以上下移动。 */
    public List<Node> nodes(boolean reward) {
        List<Node> nodes = new ArrayList<>();
        if (reward) {
            for (QuestReward node : rewards) {
                nodes.add(new Node(node.type(), node.authored()));
            }
        } else {
            for (QuestObjective node : objectives) {
                nodes.add(new Node(node.type(), node.authored()));
            }
        }
        return nodes;
    }

    /** 替换第 {@code index} 个节点（改字段就是换一份配置）。 */
    public void node(boolean reward, int index, Node node) {
        if (reward) {
            rewards.set(index, new QuestReward(node.type(), node.authored()));
        } else {
            objectives.set(index, new QuestObjective(node.type(), node.authored()));
        }
    }

    /** 追加一个节点（新建时配置为空，字段由编辑界面逐项填）。 */
    public void addNode(boolean reward, Node node) {
        if (reward) {
            rewards.add(new QuestReward(node.type(), node.authored()));
        } else {
            objectives.add(new QuestObjective(node.type(), node.authored()));
        }
    }

    /** 删除第 {@code index} 个节点。 */
    public void removeNode(boolean reward, int index) {
        if (reward) {
            rewards.remove(index);
        } else {
            objectives.remove(index);
        }
    }

    /** 与相邻节点交换位置；越界时什么都不做。 */
    public void swapNodes(boolean reward, int first, int second) {
        List<Node> nodes = nodes(reward);
        if (first < 0 || second < 0 || first >= nodes.size() || second >= nodes.size()) {
            return;
        }
        Node held = nodes.get(first);
        node(reward, first, nodes.get(second));
        node(reward, second, held);
    }

    // ---------- 基本字段 ----------

    public String id() {
        return id;
    }

    public void id(String id) {
        this.id = blankToEmpty(id);
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = blankToEmpty(name);
    }

    /** 描述行（可直接增删）。 */
    public List<String> description() {
        return description;
    }

    public String icon() {
        return icon;
    }

    public void icon(String icon) {
        this.icon = blankToEmpty(icon);
    }

    public String category() {
        return category;
    }

    public void category(String category) {
        this.category = blankToEmpty(category);
    }

    public QuestType type() {
        return type;
    }

    public void type(QuestType type) {
        this.type = type == null ? QuestType.NORMAL : type;
    }

    public double refreshCost() {
        return refreshCost;
    }

    public void refreshCost(double refreshCost) {
        this.refreshCost = refreshCost;
    }

    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
