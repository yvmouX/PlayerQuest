package com.playerPlugin.playerTaskX.core.gui.menu;

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
import com.playerPlugin.playerTaskX.api.schema.ValueKind;
import com.playerPlugin.playerTaskX.core.text.Texts;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import cn.yvmou.ylib.gui.Menu;
import cn.yvmou.ylib.gui.MenuItem;

/**
 * 任务详情：多目标进度 + 多奖励预览。
 * {@code playerQuest} 可为 {@code null}（管理员预览只看任务定义，进度一律按 0）；「返回」的落点由注入的 {@code back} 决定，玩家侧回周期列表、管理侧回原来那一页。
 */
public final class QuestDetailMenu extends Menu {

    private static final int SIZE = 54;

    /** 目标区：第 2、3 行（列表区用数字下标，见 {@code Menu#layout}）。 */
    private static final int OBJECTIVE_START = 9;
    private static final int OBJECTIVE_LIMIT = 18;
    /** 奖励区：第 4、5 行。 */
    private static final int REWARD_START = 27;
    private static final int REWARD_LIMIT = 18;

    /** 界面布局（见 {@code SlotLayout}）：目标区（第 2、3 行）与奖励区（第 4、5 行）是数字下标摆的。 */
    private static final String[] SHAPE = {
            "    `header`",
            "",
            "",
            "",
            "",
            "    `back`   `close`",
    };

    private final Quest quest;
    /** 玩家进度；{@code null} 表示只看定义（管理员预览）。 */
    private final PlayerQuest playerQuest;
    /** 「返回」按钮的落点。 */
    private final Runnable back;

    /** 玩家侧：返回周期任务列表（回到打开详情前看的那一种周期）。 */
    public QuestDetailMenu(Player viewer, MessageService messages, Quest quest, PlayerQuest playerQuest) {
        this(viewer, messages, quest, playerQuest,
                () -> new PeriodicQuestMenu(viewer, messages, playerQuest.type()).open());
    }

    /** {@code playerQuest} 可为 {@code null}（管理员预览只看任务定义，进度按 0），{@code back} 是「返回」按钮的动作。 */
    public QuestDetailMenu(Player viewer, MessageService messages, Quest quest, PlayerQuest playerQuest,
                           Runnable back) {
        super(viewer, messages, SIZE, "gui.quest-detail-title", quest == null ? "" : quest.name());
        // 标题已经用掉了 quest，这里的 requireNonNull 只是把「传了 null」变成一句明确的报错
        this.quest = Objects.requireNonNull(quest, "quest");
        this.playerQuest = playerQuest;
        this.back = Objects.requireNonNull(back, "back");
    }

    @Override
    protected void build() {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        Player player = viewer();
        layout(SHAPE);

        set("header", headerItem(quest));
        buildObjectives(plugin.objectiveTypes(), player);
        buildRewards(plugin.rewardTypes(), player);

        set("back", MenuItem.of(Material.ARROW, text("gui.back"), List.of(),
                context -> back.run()));
        set("close", MenuItem.of(Material.BARRIER, text("gui.close"), List.of(),
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
            set(REWARD_START, MenuItem.display(Material.CHEST, text("common.none"), List.of()));
            return;
        }
        int slot = REWARD_START;
        for (int index = 0; index < rewards.size() && index < REWARD_LIMIT; index++) {
            set(slot++, rewardItem(messages(), player, rewardTypes, rewards.get(index)));
        }
    }

    // ---------- 物品构造 ----------

    /** 头部物品：图标 + 名称 + 描述；描述为空时只有名称。 */
    private static MenuItem headerItem(Quest quest) {
        List<String> lore = new ArrayList<>();
        for (String line : quest.description()) {
            if (line != null && !line.isBlank()) {
                lore.add(line);
            }
        }
        return MenuItem.display(MenuItem.material(quest.icon()), quest.name(), lore);
    }

    /** 目标物品：名称带「当前/需求」并同步数量，描述为字段说明 + 配置原文；显示名优先取 {@code objective.<type>} 语言键，绝不把内部 id 摆到玩家面前。 */
    private static MenuItem objectiveItem(MessageService messages, Player viewer, ObjectiveRegistry objectiveTypes,
                                          QuestObjective objective, int index, PlayerQuest playerQuest) {
        int required = objective.amount();
        int current = playerQuest == null ? 0 : playerQuest.progress(index);
        String name = Texts.typeName(messages, viewer, "objective", objective.type(),
                objectiveTypes.displayName(objective.type()));

        List<String> lore = new ArrayList<>(fieldHints(objectiveTypes, objective));
        String properties = Texts.properties(objective.properties());
        if (!properties.isEmpty()) {
            lore.add("&8" + properties);
        }

        return MenuItem.of(objectiveIcon(objectiveTypes, objective),
                name + " &7" + current + "/" + required, lore, null).withAmount(current);
    }

    /** 奖励物品：名称走 {@code reward.<type>} 语言键；类型不可用时把 {@code unavailableReason()} 写进描述，否则玩家只见「有奖励却拿不到」。 */
    private static MenuItem rewardItem(MessageService messages, Player viewer, RewardRegistry rewardTypes, QuestReward reward) {
        String name = Texts.typeName(messages, viewer, "reward", reward.type(),
                rewardTypes.displayName(reward.type()));

        List<String> lore = new ArrayList<>();
        String properties = Texts.properties(reward.properties());
        if (!properties.isEmpty()) {
            lore.add("&8" + properties);
        }
        RewardType type = rewardTypes.find(reward.type()).orElse(null);
        if (type == null) {
            // 未知奖励类型：玩家侧只说「不可用」，内部 id 留给管理端当校验问题展示
            lore.add(messages.has("quest.unavailable") ? messages.raw(viewer, "quest.unavailable") : "");
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
    private static List<String> fieldHints(ObjectiveRegistry objectiveTypes, QuestObjective objective) {
        ObjectiveType type = objectiveTypes.find(objective.type()).orElse(null);
        if (type == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (ConfigField field : type.schema()) {
            if (!objective.properties().containsKey(field.key()) || field.hint() == null || field.hint().isBlank()) {
                continue;
            }
            String label = (field.label() == null || field.label().isBlank()) ? field.key() : field.label();
            lines.add("&7" + label + "&8: &7" + field.hint());
        }
        return lines;
    }

    /** 目标图标：按字段声明的值域推导（方块/物品取材质、实体当刷怪蛋、附魔→附魔书、自定义鱼→生鳕鱼），推不出来才退回纸；与校验共用同一份值域，图标不会和放行的值打架。 */
    static Material objectiveIcon(ObjectiveRegistry objectiveTypes, QuestObjective objective) {
        ObjectiveType type = objectiveTypes.find(objective.type()).orElse(null);
        if (type == null) {
            return Material.PAPER;
        }
        String entity = null;
        for (ConfigField field : type.schema()) {
            List<ValueKind> kinds = field.kinds();
            if (kinds.isEmpty()) {
                continue;
            }
            String value = objective.string(field.key(), "");
            if (kinds.contains(ValueKind.BLOCK) || kinds.contains(ValueKind.ITEM)) {
                Material material = match(value);
                if (material != null) {
                    return material;
                }
            }
            if (kinds.contains(ValueKind.ENCHANTMENT)) {
                return Material.ENCHANTED_BOOK;
            }
            if (kinds.contains(ValueKind.FISH)) {
                return Material.COD;
            }
            if (entity == null && (kinds.contains(ValueKind.ENTITY) || kinds.contains(ValueKind.LIVING))) {
                entity = value;
            }
        }
        if (entity != null && !entity.isBlank() && !"*".equals(entity.trim())) {
            // 实体字段允许逗号分隔多值，取第一个当示意
            Material egg = match(entity.split(",")[0].trim() + "_SPAWN_EGG");
            if (egg != null) {
                return egg;
            }
        }
        return Material.PAPER;
    }

    /** 奖励图标：有物品值域就用它（物品奖励直接显示会发的东西），否则用箱子示意「奖励」。 */
    private static Material rewardIcon(RewardRegistry rewardTypes, QuestReward reward) {
        RewardType type = rewardTypes.find(reward.type()).orElse(null);
        if (type != null) {
            for (ConfigField field : type.schema()) {
                if (!field.kinds().contains(ValueKind.ITEM)) {
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

    /** 材质名 → Material，未配置或非法返回 {@code null}（刻意不用 {@link MenuItem#material(String)} 的 PAPER 兜底：图标推导要区分「没配」与「配了 PAPER」）；逗号多值取第一个能识别的。 */
    private static Material match(String name) {
        if (name == null || name.isBlank() || "*".equals(name.trim())) {
            return null;
        }
        for (String candidate : name.split(",")) {
            String trimmed = candidate.trim();
            if (trimmed.isEmpty() || "*".equals(trimmed)) {
                continue;
            }
            Material material = Material.matchMaterial(trimmed.toUpperCase(Locale.ROOT));
            if (material != null) {
                return material;
            }
        }
        return null;
    }
}
