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
import com.playerPlugin.playerTaskX.api.schema.ValueKind;
import com.playerPlugin.playerTaskX.core.text.Texts;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * 目标与奖励之间空出一行，是为了让「要做什么」和「能拿什么」在视觉上分开。
 *
 * <h2>两个可变输入</h2>
 * <ul>
 *   <li>{@code playerQuest} 可以为 {@code null}：管理员预览看的是任务<b>定义</b>，
 *       没有玩家进度可读，此时进度一律按 0 处理，而不是再写一个「没有进度的详情界面」；</li>
 *   <li>{@code back} 决定「返回」去哪：玩家侧回每日任务列表，管理侧回原来那一页管理列表。
 *       注入一个动作比继承出两个几乎相同的界面便宜得多。</li>
 * </ul>
 */
public final class QuestDetailMenu extends Menu {

    private static final int SIZE = 54;

    /** 头部：任务图标 + 名称 + 描述。 */
    private static final int HEADER_SLOT = 4;
    /** 目标区：第 2、3 行。 */
    private static final int OBJECTIVE_START = 9;
    private static final int OBJECTIVE_LIMIT = 18;
    /** 奖励区：第 4、5 行。 */
    private static final int REWARD_START = 27;
    private static final int REWARD_LIMIT = 18;
    /** 底部操作行。 */
    private static final int BACK_SLOT = 49;
    private static final int CLOSE_SLOT = 53;

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

    /**
     * @param viewer      打开界面的玩家
     * @param messages    语言服务
     * @param quest       任务定义
     * @param playerQuest 该玩家的任务记录，可为 {@code null}
     * @param back        「返回」按钮的动作
     */
    public QuestDetailMenu(Player viewer, MessageService messages, Quest quest, PlayerQuest playerQuest,
                           Runnable back) {
        super(viewer, messages, SIZE, "gui.quest-detail-title", quest == null ? "" : quest.name());
        // 标题已经用掉了 quest，这里的 requireNonNull 只是把「传了 null」变成一句明确的报错
        this.quest = Objects.requireNonNull(quest, "quest");
        this.playerQuest = playerQuest;
        this.back = Objects.requireNonNull(back, "back");
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
                context -> back.run()));
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

    /**
     * 目标物品。
     * <p>
     * 名称 =「目标类型显示名 + 当前/需求」，数量也按进度设置，让图标本身就能看出推进程度；
     * 描述 = 该次配置用到的字段说明 + 配置原文。
     * <p>
     * 类型显示名优先取语言键 {@code objective.<type>}，缺失时退回类型自带的 displayName，
     * 绝不把 {@code break_block} 这种内部 id 摆到玩家面前。
     */
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

    /**
     * 奖励物品。
     * <p>
     * 名称 = 奖励类型显示名（同样走 {@code reward.<type>} 语言键）；
     * 描述 = 配置键值；类型不可用时把 {@code unavailableReason()} 写进描述，
     * 否则玩家会看到「有奖励却拿不到」而毫无线索。
     */
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

    /**
     * 目标图标：按字段声明的值域推导——方块/物品值域直接用那个材质（挖钻石矿就显示钻石矿），
     * 实体值域当刷怪蛋用（击杀僵尸 → 僵尸刷怪蛋），附魔给附魔书、自定义鱼给生鳕鱼，
     * 都推不出来才退回纸。
     * <p>
     * 这样任何新增的目标类型都能自动得到一个像样的图标，界面不必认识每一种类型 id；
     * 值域是同一个字段声明的（见 {@link ConfigField#kinds()}），因此图标与选择器列出的
     * 候选永远一致——不会再出现「选择器里是方块、图标却是苹果」这种各说各话。
     */
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

    /**
     * 材质名 → Material，未配置或非法返回 {@code null}。
     * <p>
     * 这里刻意不用 {@link MenuItem#material(String)}：那个方法把失败也归到 PAPER，
     * 而图标推导需要区分「没配材质」（继续找下一个字段）与「就配了 PAPER」。
     * <p>
     * 材质字段允许逗号分隔多值（如 {@code DIAMOND_ORE,DEEPSLATE_DIAMOND_ORE}），
     * 取第一个能识别的来当图标；整串丢给 {@code matchMaterial} 必然匹配不上。
     */
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
