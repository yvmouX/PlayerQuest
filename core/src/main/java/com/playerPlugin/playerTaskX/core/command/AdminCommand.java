package com.playerPlugin.playerTaskX.core.command;

import cn.yvmou.ylib.command.annotation.Arg;
import cn.yvmou.ylib.command.annotation.Command;
import cn.yvmou.ylib.command.annotation.Optional;
import cn.yvmou.ylib.command.annotation.SubCommand;
import cn.yvmou.ylib.command.context.CommandContext;
import cn.yvmou.ylib.command.help.CommandHelp;
import cn.yvmou.ylib.message.MessageService;
import cn.yvmou.ylib.text.TextRenderer;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import com.playerPlugin.playerTaskX.core.gui.editor.QuestBrowserMenu;
import com.playerPlugin.playerTaskX.core.storage.DefinitionReadOnlyException;
import com.playerPlugin.playerTaskX.core.text.Texts;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理员命令 {@code /ptxa}（权限 {@code playertaskx.admin}）；子命令清单由注解生成，见 {@code @SubCommand}。
 * 子命令用 {@code grant}、{@code setobjective}、{@code resetperiod} 与玩家命令刻意区分；参数只支持 String/Player，补全方法必须声明在本类。
 */
@Command(name = "playertaskxadmin", aliases = {"ptxa"}, description = "PlayerTaskX 管理员命令",
        permission = "playertaskx.admin", permissionDefault = "op")
public class AdminCommand {

    /** 行内分隔符；标签写法不依赖「{@code &} 后恰好是合法颜色字符」这个前提。 */
    private static final String SEPARATOR = " <gray>| <white>";

    /** 无参构造：装配方只做 {@code register(new AdminCommand())}，服务在执行时现取。 */
    public AdminCommand() {
    }

    // ---------- 总览 ----------

    /** 主命令：直接显示帮助（管理员最常需要的入口）。 */
    @SubCommand(value = "", description = "显示管理员命令清单")
    public void help(CommandSender sender) {
        showHelp(sender, 1);
    }

    /** {@code help}：显式帮助，与无参调用等价；页码可省略，聊天页脚翻页走的也是这里。 */
    @SubCommand(value = "help", description = "显示管理员命令清单")
    public void helpCommand(CommandSender sender, @Arg(value = "页码") @Optional int page) {
        showHelp(sender, page);
    }

    private static void showHelp(CommandSender sender, int page) {
        MessageService messages = messages();
        CommandHelp.ofAnnotations("PlayerTaskX", AdminCommand.class)
                .subtitle("&8" + messages.raw(sender, "command.admin-help"))
                .commandLabel("ptxa")
                .page(page)
                .send(sender);
    }

    // ---------- 界面 ----------

    /** {@code menu}：打开任务编辑器（只有玩家能开箱子界面，控制台提示后返回）。 */
    @SubCommand(value = "menu", description = "打开任务编辑器")
    public void menu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages().send(sender, "command.player-only");
            return;
        }
        new QuestBrowserMenu(player, messages()).open();
    }

    // ---------- 重载 ----------

    /**
     * {@code reload}：从存储重载任务定义。
     * <p>
     * 数据库异常必须显式捕获：让它冒到框架层的话，管理员只会看到一句「内部错误」，
     * 看不到真正原因（比如库被占用）。
     */
    @SubCommand(value = "reload", description = "从存储重载任务定义")
    public void reload(CommandSender sender) {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        try {
            plugin.questAdmin().reload();
        } catch (RuntimeException e) {
            PlayerTaskX.log().error("重载任务失败: " + e.getMessage(), e);
            messages().send(sender, "command.reload-failed", describe(e));
            return;
        }
        messages().send(sender, "command.reloaded", plugin.quests().all().size());
    }

    // ---------- 列表与详情 ----------

    /** {@code list}：列出全部任务，每行后紧跟该任务的校验问题。 */
    @SubCommand(value = "list", description = "列出全部任务与校验问题")
    public void list(CommandSender sender) {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        MessageService messages = messages();
        List<Quest> quests = plugin.quests().all();
        if (quests.isEmpty()) {
            messages.send(sender, "daily.none");
            return;
        }
        messages.sendRaw(sender, Texts.render("&8&m-----&r "
                + messages.raw(sender, "command.list-title") + " &7(" + quests.size() + ") &8&m-----"));
        for (Quest quest : quests) {
            messages.sendRaw(sender, Texts.render("&8- &f" + TextRenderer.render(quest.name())
                    + " &8(" + quest.id() + ")" + SEPARATOR
                    + "&7" + quest.type() + SEPARATOR
                    + "&7目标 " + quest.objectives().size() + SEPARATOR
                    + "&7奖励 " + quest.rewards().size() + SEPARATOR
                    + "&7启用 &f" + messages.raw(sender, quest.enabled() ? "common.yes" : "common.no")));
            for (String problem : plugin.questAdmin().validate(quest)) {
                messages.sendRaw(sender, Texts.render("&8  ! &c" + problem));
            }
        }
    }

    /** {@code info <id>}：单个任务的完整信息，目标与奖励按「类型显示名 + 配置 + 数量」展开；管理端文案与 {@code /ptxa list}、管理 GUI 同一套措辞，不走语言键。 */
    @SubCommand(value = "info", description = "查看单个任务的完整信息")
    public void info(CommandSender sender, @Arg(value = "id", suggestion = "suggestQuestIds") String id) {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        MessageService messages = plugin.messages();

        Quest quest = plugin.quests().find(id).orElse(null);
        if (quest == null) {
            messages.send(sender, "quest.not-found", id);
            return;
        }

        messages.sendRaw(sender, Texts.render("&8&m-----&r "
                + messages.raw(sender, "gui.quest-detail-title", TextRenderer.render(quest.name()))
                + " &8&m-----"));
        messages.sendRaw(sender, Texts.render("&7ID: &f" + quest.id()));
        messages.sendRaw(sender, Texts.render("&7名称: &f" + TextRenderer.render(quest.name())));
        messages.sendRaw(sender, Texts.render("&7类型: &f" + quest.type() + SEPARATOR
                + "&7分类: &f" + (TextRenderer.isBlank(quest.category())
                ? messages.raw(sender, "common.none") : quest.category())));
        messages.sendRaw(sender, Texts.render("&7图标: &f" + quest.icon()));
        messages.sendRaw(sender, Texts.render("&7刷新费用: &f" + (quest.refreshCost() > 0
                ? Texts.number(quest.refreshCost())
                : messages.raw(sender, "common.none"))));
        messages.sendRaw(sender, Texts.render("&7启用: &f"
                + messages.raw(sender, quest.enabled() ? "common.yes" : "common.no")
                + (plugin.questDefinitions().isReadOnly(quest.id())
                        ? SEPARATOR + "&7来源 &fYAML 文件（只读）"
                        : "")));

        messages.sendRaw(sender, Texts.render("&7目标:"));
        for (int slot = 0; slot < quest.objectives().size(); slot++) {
            QuestObjective objective = quest.objectives().get(slot);
            messages.sendRaw(sender, Texts.render("&8  " + (slot + 1) + ". &f"
                    + typeName(plugin, sender, "objective", objective.type(),
                    plugin.objectiveTypes().displayName(objective.type()))
                    + " &7x" + objective.amount()
                    + " &8" + Texts.properties(objective.properties())));
        }

        messages.sendRaw(sender, Texts.render("&7奖励:"));
        if (quest.rewards().isEmpty()) {
            messages.sendRaw(sender, Texts.render("&8  " + messages.raw(sender, "common.none")));
        }
        for (QuestReward reward : quest.rewards()) {
            messages.sendRaw(sender, Texts.render("&8  - &f"
                    + typeName(plugin, sender, "reward", reward.type(),
                    plugin.rewardTypes().displayName(reward.type()))
                    + " &8" + Texts.properties(reward.properties())));
        }

        List<String> problems = plugin.questAdmin().validate(quest);
        messages.sendRaw(sender, Texts.render("&7校验: " + (problems.isEmpty()
                ? "&a" + messages.raw(sender, "common.none")
                : "")));
        for (String problem : problems) {
            messages.sendRaw(sender, Texts.render("&8  ! &c" + problem));
        }
    }

    // ---------- 启用 / 禁用 ----------

    /** {@code enable <id>}：启用任务并持久化。 */
    @SubCommand(value = "enable", description = "启用任务")
    public void enable(CommandSender sender, @Arg(value = "id", suggestion = "suggestQuestIds") String id) {
        setEnabled(sender, id, true);
    }

    /** {@code disable <id>}：禁用任务并持久化。 */
    @SubCommand(value = "disable", description = "禁用任务")
    public void disable(CommandSender sender, @Arg(value = "id", suggestion = "suggestQuestIds") String id) {
        setEnabled(sender, id, false);
    }

    /** 切换启用状态；落库、注册表与玩家索引的同步由 {@code QuestAdminService} 一处担保。 */
    private static void setEnabled(CommandSender sender, String id, boolean enabled) {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        MessageService messages = plugin.messages();
        try {
            if (plugin.questAdmin().setEnabled(id, enabled) == null) {
                messages.send(sender, "quest.not-found", id);
                return;
            }
        } catch (DefinitionReadOnlyException e) {
            // 该任务定义在 quests/ 的 YAML 文件里：命令不该抛栈，直接把原因说清楚
            messages.sendRaw(sender, Texts.render("&c" + e.getMessage()));
            return;
        }
        // 「保存 + 同步」就是 command.reloaded 描述的动作，不另造一句同义提示
        messages.send(sender, "command.reloaded", plugin.quests().all().size());
    }

    // ---------- 调试：进度 / 发奖 / 每日任务 ----------

    /**
     * {@code setobjective <玩家> <任务> <目标序号> <进度>}：直接设定某玩家某目标下标上的进度。
     * <p>
     * 参数顺序即声明顺序；目标序号是任务定义里 {@code objectives} 的下标（从 0 开始），
     * 与 {@link PlayerQuest} 里存进度的键一致。
     */
    @SubCommand(value = "setobjective", description = "直接设定玩家的目标进度")
    public void setObjective(CommandSender sender,
                             @Arg("player") Player target,
                             @Arg(value = "id", suggestion = "suggestQuestIds") String id,
                             @Arg("slot") int slot,
                             @Arg("value") int value) {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        MessageService messages = plugin.messages();

        if (!plugin.progressService().setProgress(target.getUniqueId(), id, slot, value)) {
            // setProgress 返回 false 只有三种原因：任务不存在、目标下标越界、该玩家没有这条任务记录
            if (plugin.quests().contains(id)) {
                messages.send(sender, "daily.none");
            } else {
                messages.send(sender, "quest.not-found", id);
            }
            return;
        }

        Quest quest = plugin.quests().find(id).orElse(null);
        PlayerQuest playerQuest = plugin.playerQuestRepository().find(target.getUniqueId(), id).orElse(null);
        if (quest == null || playerQuest == null) {
            messages.send(sender, "error.internal");
            return;
        }
        // 回显渲染后的进度行：管理员能立刻确认改动生效，不必再切到玩家的视角看 actionbar
        messages.sendRaw(sender, Texts.render(messages.raw(sender, "quest.progress") + " &8» &f"
                + plugin.progressDisplay().render(quest, playerQuest)));
    }

    /**
     * {@code grant <玩家> <任务>}：直接发放奖励，<b>不改状态</b>（调试用）。
     * <p>
     * {@code RewardService.grant} 内部会隔离单个奖励的失败并写日志，故此处没有失败分支。
     */
    @SubCommand(value = "grant", description = "直接发放任务奖励（不改状态）")
    public void grant(CommandSender sender,
                      @Arg("player") Player target,
                      @Arg(value = "id", suggestion = "suggestQuestIds") String id) {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        Quest quest = plugin.quests().find(id).orElse(null);
        if (quest == null) {
            plugin.messages().send(sender, "quest.not-found", id);
            return;
        }
        plugin.rewardService().grant(target, quest);
        plugin.messages().send(sender, "quest.claimed", TextRenderer.render(quest.name()));
    }

    /** {@code resetperiod <玩家> [类型]}：重新抽取该玩家的周期任务；与玩家的 {@code /ptx refresh} 刻意区分——它是排障工具，不扣费也不消耗次数，不给类型就重置全部已启用周期。 */
    @SubCommand(value = "resetperiod", description = "重置玩家的周期任务（不扣费、不消耗次数）：/ptxa resetperiod <玩家> [daily|weekly|monthly|custom]")
    public void resetPeriod(CommandSender sender, @Arg("player") Player target,
                            @Arg(value = "类型", suggestion = "suggestPeriodTypes") @Optional String type) {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        List<QuestType> targets;
        if (type == null || type.isBlank()) {
            targets = plugin.periodicService().enabledTypes();
        } else {
            QuestType parsed = parsePeriodType(type);
            if (parsed == null) {
                plugin.messages().send(sender, "periodic.unknown-type", type);
                return;
            }
            targets = List.of(parsed);
        }
        if (targets.isEmpty()) {
            plugin.messages().send(sender, "periodic.none");
            return;
        }
        for (QuestType each : targets) {
            plugin.periodicService().resetPeriod(target, each).report(plugin.messages(), sender);
        }
    }

    /** 解析周期类型名；认不出来返回 null（由调用方提示，而不是抛异常）。 */
    private static QuestType parsePeriodType(String raw) {
        try {
            QuestType type = QuestType.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
            return type.isPeriodic() ? type : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---------- 补全 ----------

    /**
     * 任务 id 补全：全部任务 id（管理员命令不区分玩家状态）。
     * <p>
     * 必须声明在本类中：YLib 用 {@code getDeclaredMethod} 在命令实例的类上精确查找补全方法。
     */
    @SuppressWarnings("unused") // 由 YLib 反射调用
    public List<String> suggestQuestIds(CommandSender sender, CommandContext context, String currentInput) {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        if (plugin == null) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (Quest quest : plugin.quests().all()) {
            if (Texts.startsWith(quest.id(), currentInput)) {
                ids.add(quest.id());
            }
        }
        return ids;
    }

    /** 周期类型补全：已启用的周期（与玩家命令同名同形，YLib 按类查找补全方法）。 */
    @SuppressWarnings("unused") // 由 YLib 反射调用
    public List<String> suggestPeriodTypes(CommandSender sender, CommandContext context, String current) {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        if (plugin == null) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        for (QuestType type : plugin.periodicService().enabledTypes()) {
            options.add(type.name().toLowerCase(java.util.Locale.ROOT));
        }
        return options.stream()
                .filter(option -> option.startsWith(current == null ? "" : current.toLowerCase(java.util.Locale.ROOT)))
                .toList();
    }

    // ---------- 内部工具 ----------

    /** 取消息服务：命令对象不缓存服务引用，统一在执行时向插件实例索取。 */
    private static MessageService messages() {
        return PlayerTaskX.getInstance().messages();
    }

    /**
     * 类型显示名：优先语言键（{@code objective.<id>} / {@code reward.<id>}，用户可自定义措辞），
     * 缺失时退回类型自带的显示名 —— 与进度展示、GUI 的策略一致。
     */
    private static String typeName(PlayerTaskX plugin, CommandSender sender, String group,
                                   String type, String fallback) {
        return Texts.typeName(plugin.messages(), sender, group, type, fallback);
    }

    /** 异常文案：兜住 {@code getMessage()} 为 null 的异常（NPE 等），否则管理员会看到「失败: null」。 */
    private static String describe(RuntimeException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
