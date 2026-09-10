package com.playerPlugin.playerTaskX.core.gui;

import cn.yvmou.ylib.message.MessageService;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.registry.ObjectiveRegistry;
import com.playerPlugin.playerTaskX.api.registry.RewardRegistry;
import com.playerPlugin.playerTaskX.api.reward.RewardType;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;
import com.playerPlugin.playerTaskX.api.schema.FieldType;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 任务详情：多目标进度 + 多奖励预览。
 *
 * <h2>布局</h2>
 * <pre>
 *   第 1 行（0~8）  ：正中放任务头部（图标 + 名称 + 描述）
 *   第 2、3 行（9~26）：目标，每个目标一个物品，名称带「当前/需求」
 *   第 4、5 行（27~44）：奖励，每个奖励一个物品，描述列出配置
 *   第 6 行（45~53）：返回（49）、关闭（53）
 * </pre>
 * 目标与奖励之间空出一行，是为了让「要做什么」和「能拿什么」在视觉上分开；
 * 区域位置写成常量而不是散落的魔法数字，管理端的只读预览直接复用同一套常量与物品构造方法。
 *
 * <h2>playerQuest 可以为 null</h2>
 * 管理员预览看的是「任务定义」，没有玩家进度可读。因此进度相关的地方都要容忍 null
 * （统一按 0 处理），而不是复制一份「没有进度的详情界面」。
 */
public final class QuestDetailMenu extends Menu {

    static final int SIZE = 54;

    /** 头部：任务图标 + 名称 + 描述。 */
    static final int HEADER_SLOT = 4;
    /** 目标区：第 2、3 行。 */
    static final int OBJECTIVE_START = 9;
    static final int OBJECTIVE_LIMIT = 18;
    /** 奖励区：第 4、5 行。 */
    static final int REWARD_START = 27;
    static final int REWARD_LIMIT = 18;
    /** 底部操作行。 */
    static final int BACK_SLOT = 49;
    static final int CLOSE_SLOT = 53;

    private final Quest quest;
    /** 玩家进度；{@code null} 表示只看定义（管理员预览）。 */
    private final PlayerQuest playerQuest;

    /**
     * @param viewer      打开界面的玩家
     * @param messages    语言服务
     * @param quest       任务定义
     * @param playerQuest 该玩家的任务记录，可为 {@code null}
     */
    public QuestDetailMenu(Player viewer, MessageService messages, Quest quest, PlayerQuest playerQuest) {
        super(viewer, messages, SIZE, "gui.quest-detail-title", quest == null ? "" : quest.name());
        // 标题已经用掉了 quest，这里的 requireNonNull 只是把「传了 null」变成一句明确的报错
        this.quest = Objects.requireNonNull(quest, "quest");
        this.playerQuest = playerQuest;
        // 子类字段赋值完成后再构建：基类构造期间这些字段还是 null（见 Menu 的类注释）
        refresh();
    }

    @Override
    protected void build() {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        Player player = viewer();

        set(HEADER_SLOT, headerItem(quest));
        buildObjectives(plugin.objectiveTypes(), player);
        buildRewards(plugin.rewardTypes(), player);

        set(BACK_SLOT, MenuItem.of(Material.ARROW, text("gui.back"), List.of(),
                context -> new DailyQuestMenu(player, messages()).open()));
        set(CLOSE_SLOT, MenuItem.of(Material.BARRIER, text("gui.close"), List.of(),
                context -> player.closeInventory()));
        // 空位铺背景板：目标区与奖励区之间的分隔行靠它体现，否则是一片空洞
        fill(MenuItem.filler());
    }

    private void buildObjectives(ObjectiveRegistry objectiveTypes, Player player) {
        List<QuestObjective> objectives = quest.objectives();
        int slot = OBJECTIVE_START;
        for (int index = 0; index < objectives.size() && index < OBJECTIVE_LIMIT; index++) {
            set(slot++, objectiveItem(messages(), player, objectiveTypes, objectives.get(index), index, playerQuest));
        }
    }

    private void buildRewards(RewardRegistry rewardTypes, Player player) {
        List<QuestReward> rewards = quest.rewards();
        if (rewards.isEmpty()) {
            // 没有奖励时给一个占位说明，否则奖励区只剩背景板，玩家会以为界面出了问题
            set(REWARD_START, MenuItem.display(Material.CHEST, textOr("common.none", ""), List.of()));
            return;
        }
        int slot = REWARD_START;
        for (int index = 0; index < rewards.size() && index < REWARD_LIMIT; index++) {
            set(slot++, rewardItem(messages(), player, rewardTypes, rewards.get(index)));
        }
    }

    // ---------- 物品构造（static：管理端的只读预览复用同一套实现） ----------

    /** 头部物品：图标 + 名称 + 描述；描述为空时只有名称。 */
    static MenuItem headerItem(Quest quest) {
        List<String> lore = new ArrayList<>();
        for (String line : quest.description()) {
            if (line != null && !line.isBlank()) {
                lore.add(line);
            }
        }
        return MenuItem.display(MenuItem.material(quest.icon()), quest.name(), lore);
    }

    /**
     * 目标物品。
     * <p>
     * 名称 =「目标类型显示名 + 当前/需求」，数量也按进度设置，让图标本身就能看出推进程度；
     * 描述 = 该次配置用到的字段说明 + 配置原文。
     * <p>
     * 类型显示名优先取语言键 {@code objective.<type>}，缺失时退回类型自带的 displayName，
     * 绝不把 {@code break_block} 这种内部 id 摆到玩家面前。
     */
    static MenuItem objectiveItem(MessageService messages, Player viewer, ObjectiveRegistry objectiveTypes,
                                  QuestObjective objective, int index, PlayerQuest playerQuest) {
        int required = objective.amount();
        int current = playerQuest == null ? 0 : playerQuest.progress(index);
        String name = localized(messages, viewer, "objective." + objective.type(),
                objectiveTypes.displayName(objective.type()));

        List<String> lore = new ArrayList<>(fieldHints(objectiveTypes, objective));
        String properties = properties(objective.properties());
        if (!properties.isEmpty()) {
            lore.add("&8" + properties);
        }

        return MenuItem.of(objectiveIcon(objectiveTypes, objective),
                name + " &7" + current + "/" + required, lore, null).withAmount(current);
    }

    /**
     * 奖励物品。
     * <p>
     * 名称 = 奖励类型显示名（同样走 {@code reward.<type>} 语言键）；
     * 描述 = 配置键值；类型不可用时把 {@code unavailableReason()} 写进描述，
     * 否则玩家会看到「有奖励却拿不到」而毫无线索。
     */
    static MenuItem rewardItem(MessageService messages, Player viewer, RewardRegistry rewardTypes, QuestReward reward) {
        String name = localized(messages, viewer, "reward." + reward.type(),
                rewardTypes.displayName(reward.type()));

        List<String> lore = new ArrayList<>();
        String properties = properties(reward.properties());
        if (!properties.isEmpty()) {
            lore.add("&8" + properties);
        }
        RewardType type = rewardTypes.find(reward.type()).orElse(null);
        if (type == null) {
            // 未知奖励类型：玩家侧只说「不可用」，内部 id 留给管理端当校验问题展示
            lore.add(localized(messages, viewer, "quest.unavailable", ""));
        } else if (!type.available()) {
            lore.add("&c" + type.unavailableReason());
        }
        return MenuItem.display(rewardIcon(rewardTypes, reward), name, lore);
    }

    /**
     * 字段说明行：对本次配置<b>实际用到</b>的字段输出「标签: 提示」。
     * <p>
     * 标签与提示都取自类型自己的 {@code schema()}，不是界面里写死的文案，
     * 因此新增目标类型不需要回头改 GUI——这正是类型自描述的意义。
     */
    static List<String> fieldHints(ObjectiveRegistry objectiveTypes, QuestObjective objective) {
        ObjectiveType type = objectiveTypes.find(objective.type()).orElse(null);
        if (type == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (ConfigField field : type.schema()) {
            if (!objective.properties().containsKey(field.key())) {
                continue;
            }
            String hint = field.hint();
            if (hint == null || hint.isBlank()) {
                continue;
            }
            String label = (field.label() == null || field.label().isBlank()) ? field.key() : field.label();
            lines.add("&7" + label + "&8: &7" + hint);
        }
        return lines;
    }

    /**
     * 目标图标：优先取配置里 MATERIAL 字段的目标（挖钻石矿就直接显示钻石矿），
     * 其次把 ENTITY 字段当刷怪蛋用（击杀僵尸 → 僵尸刷怪蛋），都没有才退回纸。
     * <p>
     * 这样任何新增的目标类型都能自动得到一个像样的图标，界面不必认识每一种类型 id。
     */
    static Material objectiveIcon(ObjectiveRegistry objectiveTypes, QuestObjective objective) {
        ObjectiveType type = objectiveTypes.find(objective.type()).orElse(null);
        if (type == null) {
            return Material.PAPER;
        }
        String entity = null;
        for (ConfigField field : type.schema()) {
            if (field.type() == FieldType.MATERIAL) {
                Material material = match(objective.string(field.key(), ""));
                if (material != null) {
                    return material;
                }
            } else if (field.type() == FieldType.ENTITY && entity == null) {
                entity = objective.string(field.key(), "");
            }
        }
        if (entity != null && !entity.isBlank() && !"*".equals(entity.trim())) {
            // ENTITY 允许逗号分隔多值，取第一个当示意
            Material egg = match(entity.split(",")[0].trim() + "_SPAWN_EGG");
            if (egg != null) {
                return egg;
            }
        }
        return Material.PAPER;
    }

    /** 奖励图标：有 MATERIAL 字段就用它（物品奖励直接显示会发的东西），否则用箱子示意「奖励」。 */
    static Material rewardIcon(RewardRegistry rewardTypes, QuestReward reward) {
        RewardType type = rewardTypes.find(reward.type()).orElse(null);
        if (type != null) {
            for (ConfigField field : type.schema()) {
                if (field.type() != FieldType.MATERIAL) {
                    continue;
                }
                Material material = match(reward.string(field.key(), ""));
                if (material != null) {
                    return material;
                }
            }
        }
        return Material.CHEST;
    }

    /**
     * 把配置摊成一行 {@code (key=value, ...)}。
     * <p>
     * 键名是配置键（英文标识符）而不是玩家文案，原样显示即可：
     * 它同时也是管理员排查配置时需要的原文，硬翻成中文反而对不上配置文件。
     */
    static String properties(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("(");
        boolean first = true;
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return builder.append(')').toString();
    }

    /**
     * 材质名 → Material，未配置或非法返回 {@code null}。
     * <p>
     * 这里刻意不用 {@link MenuItem#material(String)}：那个方法把失败也归到 PAPER，
     * 而图标推导需要区分「没配材质」（继续找下一个字段）与「就配了 PAPER」。
     */
    private static Material match(String name) {
        if (name == null || name.isBlank() || "*".equals(name.trim())) {
            return null;
        }
        return Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
    }
}
