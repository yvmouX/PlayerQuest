package com.playerPlugin.playerTaskX.core.command;

import cn.yvmou.ylib.command.annotation.Arg;
import cn.yvmou.ylib.command.annotation.Command;
import cn.yvmou.ylib.command.annotation.SubCommand;
import cn.yvmou.ylib.command.context.CommandContext;
import cn.yvmou.ylib.command.help.CommandHelp;
import cn.yvmou.ylib.message.MessageService;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.reward.RewardType;
import com.playerPlugin.playerTaskX.core.daily.DailyService;
import com.playerPlugin.playerTaskX.core.gui.AdminQuestMenu;
import com.playerPlugin.playerTaskX.core.text.TextRenderer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 管理员命令 {@code /playertaskxadmin}（别名 {@code /ptxa}），权限 {@code playertaskx.admin}。
 *
 * <h2>子命令</h2>
 * <ul>
 *   <li>{@code ""} / {@code help}：管理员命令清单；</li>
 *   <li>{@code menu}：打开任务管理界面；</li>
 *   <li>{@code reload}：重载配置与任务定义；</li>
 *   <li>{@code list}：列出全部任务及校验问题；</li>
 *   <li>{@code info <id>}：单个任务的完整信息；</li>
 *   <li>{@code enable <id>} / {@code disable <id>}：切换启用状态并持久化；</li>
 *   <li>{@code setobjective <玩家> <任务> <目标序号> <进度>}：直接设定目标进度（调试）；</li>
 *   <li>{@code grant <玩家> <任务>}：只发奖励不改状态（调试）；</li>
 *   <li>{@code reroll <玩家>}：重抽某玩家的每日任务（调试）。</li>
 * </ul>
 *
 * <h2>命名约定</h2>
 * 子命令一律用「动词/名词」表达真实动作，不用含糊的通用词：
 * 发放奖励叫 {@code grant} 而不是 {@code give}（后者在插件语境里通常指给物品）；
 * 改进度叫 {@code setobjective} 而不是 {@code progress}（后者看起来像「查看进度」）；
 * 重抽每日任务叫 {@code reroll} 而不是 {@code daily}（后者看起来像「查看每日任务」）。
 * 旧名字保留为隐藏别名，避免已有的管理脚本失效。
 *
 * <h2>权限为什么只写在类上</h2>
 * {@code @Command(permission=...)} 挂在根节点，而 {@code CommandDispatcher} 每次执行都会先校验根节点，
 * 因此所有子命令天然被同一道门禁覆盖，不需要逐个 {@code @SubCommand} 再写一遍权限。
 * <p>
 * {@code permissionDefault = "op"} 让 YLib 在注册时把该权限节点注册进 Bukkit（默认 op），
 * 免得依赖「未声明的权限默认给 op」这种隐式行为。
 *
 * <h2>无参构造</h2>
 * 装配方只做 {@code register(new AdminCommand())}，服务在执行时通过 {@link PlayerTaskX#getInstance()} 取。
 *
 * <h2>YLib 约束</h2>
 * 参数类型只支持 {@code String/int/Integer/double/Double/boolean/Boolean/Player/World/枚举}，
 * 因此玩家用 {@code Player}（框架自带在线玩家补全），其余一律用 {@code String} 接；
 * 补全方法必须声明在本类中，签名为 {@code List<String> f(CommandSender, CommandContext, String)}。
 */
@Command(name = "playertaskxadmin", aliases = {"ptxa"}, description = "PlayerTaskX 管理员命令",
        permission = "playertaskx.admin", permissionDefault = "op")
public class AdminCommand {

    /** 子命令帮助行的前缀记号（结构字符，不属于正文文案）。 */
    private static final String HELP_PREFIX = TextRenderer.translateAmpersand("&8» &f");

    /** 行内分隔符：预先转成 {@code §} 形式，避免拼接后再渲染时被 MiniMessage 当普通字符留下。 */
    private static final String SEPARATOR = TextRenderer.translateAmpersand(" &7| &f");

    /** 无参构造：装配方只做 {@code register(new AdminCommand())}，服务在执行时现取。 */
    public AdminCommand() {
    }

    // ---------- 总览 ----------

    /** 主命令：直接显示帮助（管理员最常需要的入口）。 */
    @SubCommand(value = "", description = "显示管理员命令清单")
    public void help(CommandSender sender) {
        showHelp(sender);
    }

    /** {@code help}：显式帮助；与无参调用等价，满足「命令名可发现」的直觉。 */
    @SubCommand(value = "help", description = "显示管理员命令清单")
    public void helpCommand(CommandSender sender) {
        showHelp(sender);
    }

    /**
     * 帮助正文：按用途分组，每组带小节标题。
     * <p>
     * 子命令的一句话说明没有对应的语言键（语言文件不在本类改动范围内），
     * 因此这里直写中文简述——这是本类唯一硬编码的玩家可见文案，
     * 与 {@code @SubCommand(description=...)} 里的内容保持一致。
     */
    private void showHelp(CommandSender sender) {
        MessageService messages = messages();
        CommandHelp.builder(messages.raw(sender, "gui.admin-title"))
                .subtitle("&8任务 id 可用 Tab 补全")
                .group("任务管理")
                .entry("/ptxa list", "列出全部任务与校验问题")
                .entry("/ptxa info <id>", "查看单个任务的完整信息")
                .entry("/ptxa enable <id>", "启用任务")
                .entry("/ptxa disable <id>", "禁用任务")
                .entry("/ptxa reload", "从存储重载任务定义")
                .group("界面与编辑")
                .entry("/ptxa menu", "打开任务管理界面")
                .entry("/ptxa editor", "查看网页编辑器地址与访问令牌")
                .group("调试与修复")
                .entry("/ptxa setobjective <玩家> <任务> <序号> <进度>", "直接设定目标进度")
                .entry("/ptxa grant <玩家> <任务>", "直接发放奖励（不改状态）")
                .entry("/ptxa reroll <玩家>", "重抽每日任务（含次数与扣费）")
                .send(sender);
    }

    /**
     * {@code editor}：输出网页编辑器地址与令牌。
     * <p>
     * 管理员常在服务器上而非本机浏览器操作，因此把 URL 直接发到聊天里，
     * 不必去翻 config.yml。
     */
    @SubCommand(value = "editor", description = "显示网页编辑器地址")
    public void editor(CommandSender sender) {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        MessageService messages = plugin.messages();
        if (!plugin.config().isEditorEnabled()) {
            messages.send(sender, "editor.disabled");
            return;
        }
        messages.send(sender, "editor.started", "127.0.0.1:" + plugin.config().getEditorPort());
        String token = plugin.config().getEditorToken();
        if (token != null && !token.isBlank()) {
            messages.sendRaw(sender, render("&7访问令牌: &f" + token));
        } else {
            messages.send(sender, "editor.token-required");
        }
    }

    // ---------- 界面 ----------

    /** {@code menu}：打开管理界面（只有玩家能开箱子界面，控制台提示后返回）。 */
    @SubCommand(value = "menu", description = "打开任务管理界面")
    public void menu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages().send(sender, "command.player-only");
            return;
        }
        new AdminQuestMenu(player, messages()).open();
    }

    // ---------- 重载 ----------

    /**
     * {@code reload}：从存储重载任务定义。
     * <p>
     * {@code reloadQuests()} 会读库并重建注册表，数据库异常必须显式捕获：
     * 让它冒到框架层的话，管理员只会看到一句「内部错误」，看不到真正原因（比如库被占用）。
     */
    @SubCommand(value = "reload", description = "从存储重载任务定义")
    public void reload(CommandSender sender) {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        try {
            plugin.reloadQuests();
        } catch (RuntimeException e) {
            PlayerTaskX.log().error("重载任务失败: " + e.getMessage(), e);
            messages().send(sender, "command.reload-failed", describe(e));
            return;
        }
        // 语言键是 command.reloaded（带任务数量占位符），不是 command.reload
        messages().send(sender, "command.reloaded", plugin.quests().all().size());
    }

    // ---------- 列表与详情 ----------

    /** {@code list}：列出全部任务，每行后紧跟该任务的校验问题。 */
    @SubCommand(value = "list", description = "列出全部任务与校验问题")
    public void list(CommandSender sender) {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        MessageService messages = messages();
        Collection<Quest> quests = plugin.quests().all();
        if (quests.isEmpty()) {
            messages.send(sender, "daily.none");
            return;
        }
        messages.sendRaw(sender, render("&8&m-----&r "
                + messages.raw(sender, "gui.admin-title") + " &7(" + quests.size() + ") &8&m-----"));
        for (Quest quest : quests) {
            messages.sendRaw(sender, questLine(plugin, sender, quest));
            for (String problem : problems(plugin, quest)) {
                messages.sendRaw(sender, render("&8  ! &c" + problem));
            }
        }
    }

    /**
     * {@code info <id>}：单个任务的完整信息。
     * <p>
     * 目标与奖励都按「类型显示名 + 配置 + 数量」展开：管理员排查「任务为什么不涨进度」时，
     * 需要看到的目标字段就是这些，不必再去翻数据库。
     */
    @SubCommand(value = "info", description = "查看单个任务的完整信息")
    public void info(CommandSender sender, @Arg(value = "id", suggestion = "suggestQuestIds") String id) {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        MessageService messages = plugin.messages();

        Quest quest = plugin.quests().find(id).orElse(null);
        if (quest == null) {
            messages.send(sender, "quest.not-found", id);
            return;
        }

        messages.sendRaw(sender, render("&8&m-----&r "
                + messages.raw(sender, "gui.quest-detail-title", TextRenderer.render(quest.name()))
                + " &8&m-----"));
        messages.sendRaw(sender, render("&7ID: &f" + quest.id()));
        messages.sendRaw(sender, render("&7名称: &f" + TextRenderer.render(quest.name())));
        messages.sendRaw(sender, render("&7类型: &f" + quest.type() + SEPARATOR
                + "&7分类: &f" + (TextRenderer.isBlank(quest.category())
                ? messages.raw(sender, "common.none") : quest.category())));
        messages.sendRaw(sender, render("&7图标: &f" + quest.icon()));
        messages.sendRaw(sender, render("&7刷新费用: &f"
                + (quest.refreshCost() > 0
                ? formatNumber(quest.refreshCost())
                : messages.raw(sender, "common.none"))));
        messages.sendRaw(sender, render("&7启用: &f"
                + messages.raw(sender, quest.enabled() ? "common.yes" : "common.no")));

        messages.sendRaw(sender, render("&7目标:"));
        for (int slot = 0; slot < quest.objectives().size(); slot++) {
            QuestObjective objective = quest.objectives().get(slot);
            messages.sendRaw(sender, render("&8  " + (slot + 1) + ". &f"
                    + objectiveName(plugin, sender, objective.type())
                    + " &7x" + objective.amount()
                    + " &8" + formatProperties(objective.properties())));
        }

        messages.sendRaw(sender, render("&7奖励:"));
        if (quest.rewards().isEmpty()) {
            messages.sendRaw(sender, render("&8  " + messages.raw(sender, "common.none")));
        }
        for (QuestReward reward : quest.rewards()) {
            messages.sendRaw(sender, render("&8  - &f"
                    + rewardName(plugin, sender, reward.type())
                    + " &8" + formatProperties(reward.properties())));
        }

        List<String> problems = problems(plugin, quest);
        if (problems.isEmpty()) {
            messages.sendRaw(sender, render("&7校验: &a" + messages.raw(sender, "common.none")));
        } else {
            messages.sendRaw(sender, render("&7校验:"));
            for (String problem : problems) {
                messages.sendRaw(sender, render("&8  ! &c" + problem));
            }
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

    // ---------- 调试：进度 / 发奖 / 每日任务 ----------

    /**
     * {@code setobjective <玩家> <任务> <目标序号> <进度>}：直接设定某玩家某目标下标上的进度。
     * <p>
     * 命名沿用「动词 + 宾语」：{@code progress} 看起来像「查看进度」，
     * 而它实际是写操作，因此改名为 {@code setobjective}。
     * 参数顺序即声明顺序；目标序号是任务定义里 objectives 的下标（从 0 开始），
     * 与 {@code PlayerQuest} 存进度的键一致。
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
            if (!plugin.quests().contains(id)) {
                messages.send(sender, "quest.not-found", id);
            } else {
                // 该玩家当前没有这条任务（或序号越界），沿用「没有任务」的既有提示
                messages.send(sender, "daily.none");
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
        messages.sendRaw(sender, render(messages.raw(sender, "quest.progress") + " &8» &f"
                + plugin.progressDisplay().render(quest, playerQuest)));
    }

    /**
     * {@code grant <玩家> <任务>}：直接发放奖励，<b>不改状态</b>（调试用）。
     * <p>
     * 原名为 {@code give}，但那在插件语境里通常指「给玩家物品」，容易误解；
     * 这里发放的是任务定义的奖励，因此用 {@code grant}。
     * {@code RewardService.grant} 内部会隔离单个奖励的失败并写日志，故此处没有失败分支。
     */
    @SubCommand(value = "grant", description = "直接发放任务奖励（不改状态）")
    public void grant(CommandSender sender,
                      @Arg("player") Player target,
                      @Arg(value = "id", suggestion = "suggestQuestIds") String id) {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        MessageService messages = plugin.messages();

        Quest quest = plugin.quests().find(id).orElse(null);
        if (quest == null) {
            messages.send(sender, "quest.not-found", id);
            return;
        }
        plugin.rewardService().grant(target, quest);
        messages.send(sender, "quest.claimed", TextRenderer.render(quest.name()));
    }

    /**
     * {@code reroll <玩家>}：重新抽取某玩家的每日任务（调试用）。
     * <p>
     * 原名为 {@code daily}，看起来像「查看每日任务」，实际是重抽，因此改名。
     * 走的是与玩家 {@code /ptx refresh} 完全相同的一条路径（含次数上限与扣费），
     * 否则调试出来的行为与真实刷新不一致。
     */
    @SubCommand(value = "reroll", description = "重抽玩家的每日任务")
    public void reroll(CommandSender sender, @Arg("player") Player target) {
        feedback(PlayerTaskX.getInstance(), sender, PlayerTaskX.getInstance().dailyService().refresh(target));
    }

    // ---------- 旧命令名别名（隐藏，不出现在帮助里） ----------

    /**
     * {@code progress} → {@link #setObjective} 的旧名字。
     * <p>
     * 保留是为了不让已有的管理脚本 / 快捷栏指令失效；
     * 但它不出现在帮助中，新用法一律以新名字为准。
     */
    @SubCommand(value = "progress", description = "（已改名）请使用 setobjective")
    public void legacyProgress(CommandSender sender,
                               @Arg("player") Player target,
                               @Arg(value = "id", suggestion = "suggestQuestIds") String id,
                               @Arg("slot") int slot,
                               @Arg("value") int value) {
        setObjective(sender, target, id, slot, value);
    }

    /** {@code give} → {@link #grant} 的旧名字，仅为兼容保留。 */
    @SubCommand(value = "give", description = "（已改名）请使用 grant")
    public void legacyGive(CommandSender sender,
                           @Arg("player") Player target,
                           @Arg(value = "id", suggestion = "suggestQuestIds") String id) {
        grant(sender, target, id);
    }

    /** {@code daily} → {@link #reroll} 的旧名字，仅为兼容保留。 */
    @SubCommand(value = "daily", description = "（已改名）请使用 reroll")
    public void legacyDaily(CommandSender sender, @Arg("player") Player target) {
        reroll(sender, target);
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
            if (startsWith(quest.id(), currentInput)) {
                ids.add(quest.id());
            }
        }
        return ids;
    }

    // ---------- 校验 ----------

    /**
     * 校验任务引用的目标与奖励类型，返回问题清单（空表示没问题）。
     * <p>
     * 奖励除了「类型是否存在」还看「是否可用」：Vault 未安装时金币奖励配置完全合法，
     * 但玩家一分钱也拿不到——这种情况必须在管理员视图里暴露出来。
     */
    private static List<String> problems(PlayerTaskX plugin, Quest quest) {
        List<String> problems = new ArrayList<>();
        if (quest.objectives().isEmpty()) {
            problems.add("任务没有配置任何目标");
        }
        for (QuestObjective objective : quest.objectives()) {
            if (!plugin.objectiveTypes().contains(objective.type())) {
                problems.add("未知目标类型 " + objective.type());
            }
        }
        for (QuestReward reward : quest.rewards()) {
            RewardType type = plugin.rewardTypes().find(reward.type()).orElse(null);
            if (type == null) {
                problems.add("未知奖励类型 " + reward.type());
            } else if (!type.available()) {
                problems.add("奖励类型 " + reward.type() + " 不可用（" + type.unavailableReason() + "）");
            }
        }
        return problems;
    }

    // ---------- 持久化 ----------

    /**
     * 切换任务的启用状态并落库。
     * <p>
     * {@link Quest} 是 record，没有 setter，所以按字段整份复制出一个新对象再保存；
     * 保存后必须 {@code reloadQuests()}：内存注册表里还是旧定义，不重载等于没改。
     */
    private static void setEnabled(CommandSender sender, String id, boolean enabled) {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        MessageService messages = plugin.messages();

        Quest quest = plugin.quests().find(id).orElse(null);
        if (quest == null) {
            messages.send(sender, "quest.not-found", id);
            return;
        }
        try {
            Quest updated = new Quest(quest.id(), quest.name(), quest.description(), quest.icon(), quest.category(),
                    quest.type(), quest.objectives(), quest.rewards(), quest.refreshCost(), enabled);
            plugin.questRepository().save(updated);
            plugin.reloadQuests();
        } catch (RuntimeException e) {
            PlayerTaskX.log().error("保存任务启用状态失败: " + e.getMessage(), e);
            messages.send(sender, "error.internal");
            return;
        }
        // 保存 + 重载就是这句话描述的动作，避免为 enable/disable 再硬编码一句同义提示
        messages.send(sender, "command.reloaded", plugin.quests().all().size());
    }

    // ---------- 内部工具 ----------

    /** 取消息服务：命令对象不缓存服务引用，统一在执行时向插件实例索取。 */
    private static MessageService messages() {
        return PlayerTaskX.getInstance().messages();
    }

    /** 列表行：id、类型、目标数、奖励数、启用状态（标签无对应语言键，故直写）。 */
    private static String questLine(PlayerTaskX plugin, CommandSender sender, Quest quest) {
        MessageService messages = plugin.messages();
        return render("&8- &f" + TextRenderer.render(quest.name())
                + " &8(" + quest.id() + ")" + SEPARATOR
                + "&7" + quest.type() + SEPARATOR
                + "&7目标 " + quest.objectives().size() + SEPARATOR
                + "&7奖励 " + quest.rewards().size() + SEPARATOR
                + "&7启用 &f" + messages.raw(sender, quest.enabled() ? "common.yes" : "common.no"));
    }

    /**
     * 渲染一行拼接文本：先按 MiniMessage 解析（任务数据可能是标签写法），
     * 再把遗留的 {@code &} 颜色码统一转成 {@code §}；玩家命令类里有一份同策略的实现。
     */
    private static String render(String raw) {
        return TextRenderer.translateAmpersand(TextRenderer.render(raw));
    }

    /** 把目标/奖励的配置拼成一行，值可能是字符串、数字、布尔或列表。 */
    private static String formatProperties(Map<String, Object> properties) {
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
     * 类型显示名：优先语言键（{@code objective.<id>} / {@code reward.<id>}，用户可自定义措辞），
     * 缺失时退回类型自带的显示名 —— 与 {@code ProgressDisplay} 的策略保持一致。
     */
    private static String localizedName(PlayerTaskX plugin, CommandSender sender,
                                        String group, String type, String fallback) {
        MessageService messages = plugin.messages();
        String key = group + "." + type;
        return messages.has(key) ? TextRenderer.strip(messages.raw(sender, key)) : fallback;
    }

    /** 目标类型显示名。 */
    private static String objectiveName(PlayerTaskX plugin, CommandSender sender, String type) {
        return localizedName(plugin, sender, "objective", type, plugin.objectiveTypes().displayName(type));
    }

    /** 奖励类型显示名。 */
    private static String rewardName(PlayerTaskX plugin, CommandSender sender, String type) {
        return localizedName(plugin, sender, "reward", type, plugin.rewardTypes().displayName(type));
    }

    /**
     * 按 {@link DailyService.RefreshResult} 反馈刷新结果。
     * <p>
     * 与玩家命令里的同名方法逻辑一致，但反馈对象是执行命令的管理员而不是被刷新的玩家，
     * 所以单独留一份：两个命令类各自独立，不互相调用私有工具。
     */
    private static void feedback(PlayerTaskX plugin, CommandSender receiver, DailyService.RefreshResult result) {
        MessageService messages = plugin.messages();
        if (result.success()) {
            messages.send(receiver, "quest.refreshed");
            if (result.cost() > 0) {
                messages.send(receiver, "quest.refresh-cost",
                        describeCost(plugin, receiver, result.cost(), result.currency()));
            }
            return;
        }
        if (result.limit() > 0) {
            messages.send(receiver, "quest.refresh-limit", result.limit());
            return;
        }
        messages.send(receiver, "quest.refresh-failed", result.error());
    }

    /** 费用文案：数字 + 货币显示名（货币 id 走 {@code reward.<id>} 语言键）。 */
    private static String describeCost(PlayerTaskX plugin, CommandSender receiver, double cost, String currency) {
        String amount = formatNumber(cost);
        if (currency == null || currency.isBlank()) {
            return amount;
        }
        return amount + " " + rewardName(plugin, receiver, currency);
    }

    /** 数字文案：去掉整数的小数尾巴（500.0 → 500），小数保持原样。 */
    private static String formatNumber(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /** 异常文案：兜住 getMessage() 为 null 的异常（NPE 等），否则玩家会看到「失败: null」。 */
    private static String describe(RuntimeException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    /** 补全前缀匹配：空输入表示不过滤；YLib 不会对返回值再过滤一次。 */
    private static boolean startsWith(String value, String prefix) {
        return prefix == null || prefix.isEmpty() || value.toLowerCase().startsWith(prefix.toLowerCase());
    }
}
