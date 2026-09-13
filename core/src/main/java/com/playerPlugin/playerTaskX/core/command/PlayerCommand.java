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
import com.playerPlugin.playerTaskX.api.model.QuestStatus;
import com.playerPlugin.playerTaskX.core.gui.DailyQuestMenu;
import com.playerPlugin.playerTaskX.core.text.Texts;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * 玩家命令 {@code /playertaskx}（别名 {@code /ptx}）。
 *
 * <h2>子命令</h2>
 * <ul>
 *   <li>{@code ""} / {@code menu} / {@code gui}：打开每日任务界面；</li>
 *   <li>{@code list}：聊天中列出当前每日任务与完成度；</li>
 *   <li>{@code refresh}：消耗货币重抽自己的每日任务；</li>
 *   <li>{@code claim <id>}：领取已完成任务的奖励；</li>
 *   <li>{@code progress}：列出所有进行中任务的进度。</li>
 * </ul>
 * 这份清单只出现在帮助里（由 {@link CommandHelp#ofAnnotations} 从注解自动生成），
 * 不在这里手写第二份——手写清单迟早会和代码不一致。
 *
 * <h2>为什么是无参构造</h2>
 * 装配方只需 {@code register(new PlayerCommand())}：命令对象不持有服务引用，
 * 需要时通过 {@link PlayerTaskX#getInstance()} 取。这样命令类不参与依赖装配顺序，
 * 也不会在插件重载后攥着过期的服务实例。
 *
 * <h2>YLib 约束（照抄官方文档会踩的坑）</h2>
 * <ul>
 *   <li>权限只有 {@code @Command(permission=...)} / {@code @SubCommand(permission=...)} 两种写法，
 *       <b>不存在 {@code @Permission} 注解</b>；</li>
 *   <li>参数类型只支持 {@code String/int/Integer/double/Double/boolean/Boolean/Player/World/枚举}，
 *       {@code long}、{@code Material}、{@code OfflinePlayer} 等会被当 String 注入并在反射调用时炸掉，
 *       所以本类只用 {@code String} 与 {@code Player}；</li>
 *   <li>补全方法签名必须是 {@code List<String> f(CommandSender, CommandContext, String)}，
 *       并且必须声明在本类中——YLib 用 {@code getDeclaredMethod} 精确查找，抽到父类或工具类会静默失效；</li>
 *   <li>Tab 补全时 YLib 传的是空 context，<b>拿不到已解析的前置参数</b>，补全只能基于 sender 推断。</li>
 * </ul>
 */
@Command(name = "playertaskx", aliases = {"ptx"}, description = "PlayerTaskX 玩家命令")
public class PlayerCommand {

    /** 行内分隔符；标签写法不依赖「{@code &} 后恰好是合法颜色字符」这个前提。 */
    private static final String SEPARATOR = "<gray> - <white>";

    /** 无参构造：装配方只做 {@code register(new PlayerCommand())}，服务在执行时现取。 */
    public PlayerCommand() {
    }

    // ---------- 打开界面 ----------

    /**
     * 主命令：玩家直接打开界面；控制台显示帮助。
     * <p>
     * 控制台没有背包界面，回一句「只能由玩家执行」既没告诉他能做什么、也找不到帮助入口，
     * 因此改为直接把帮助打出来。
     */
    @SubCommand(value = "", description = "打开每日任务界面")
    public void menu(CommandSender sender) {
        if (!(sender instanceof Player)) {
            showHelp(sender, 1);
            return;
        }
        new DailyQuestMenu((Player) sender, messages()).open();
    }

    /** {@code menu}：与主命令等价，显式写出来是为了让玩家在 Tab 补全里看得到入口。 */
    @SubCommand(value = "menu", description = "打开每日任务界面")
    public void menuExplicit(CommandSender sender) {
        open(sender);
    }

    /** {@code gui}：{@code menu} 的别名，照顾不同玩家的习惯叫法。 */
    @SubCommand(value = "gui", description = "打开每日任务界面（同 menu）")
    public void gui(CommandSender sender) {
        open(sender);
    }

    // ---------- 帮助 ----------

    /**
     * {@code help}：玩家命令清单，条目与说明全部来自注解。
     * <p>
     * 页码可省略（缺省第 1 页）；聊天页脚翻页按钮执行的 {@code /ptx help N} 走的也是这里。
     */
    @SubCommand(value = "help", description = "显示玩家命令清单")
    public void help(CommandSender sender, @Arg(value = "页码") @Optional int page) {
        showHelp(sender, page);
    }

    /** 帮助正文；标题固定为插件名（命令帮助不该跟某个语言键的措辞绑在一起）。 */
    private static void showHelp(CommandSender sender, int page) {
        MessageService messages = messages();
        CommandHelp.ofAnnotations("PlayerTaskX", PlayerCommand.class)
                .subtitle("&8" + messages.raw(sender, "command.player-help"))
                // 页脚的提示与翻页按钮用短名 ptx，而不是注解里的全名 playertaskx
                .commandLabel("ptx")
                .page(page)
                .send(sender);
    }

    // ---------- 列表 ----------

    /**
     * {@code list}：列出玩家当前的每日任务，每行「任务名 - 完成度百分比」。
     * <p>
     * 已完成的追加一行状态提示，玩家才知道下一步该去领取而不是继续刷。
     */
    @SubCommand(value = "list", description = "列出当前任务与完成度")
    public void list(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        MessageService messages = plugin.messages();

        List<PlayerQuest> current = plugin.dailyService().currentQuests(player.getUniqueId());
        if (current.isEmpty()) {
            messages.send(player, "daily.none");
            return;
        }

        messages.sendRaw(player, Texts.render(messages.raw(player, "quest.progress")));
        for (PlayerQuest playerQuest : current) {
            Quest quest = plugin.quests().find(playerQuest.questId()).orElse(null);
            if (quest == null) {
                // 任务定义已被删除但玩家记录还在：跳过，不让一条脏数据把整条命令带崩
                continue;
            }
            StringBuilder builder = new StringBuilder(TextRenderer.render(quest.name()));
            builder.append(SEPARATOR).append(Math.round(playerQuest.completionRatio(quest) * 100)).append('%');
            String status = statusHint(messages, player, playerQuest.status());
            if (!status.isEmpty()) {
                builder.append(SEPARATOR).append(status);
            }
            messages.sendRaw(player, Texts.render(builder.toString()));
        }
    }

    /**
     * 状态提示：只为「已完成待领取」「已领取」追加。
     * <p>
     * 文案复用 GUI 的语言键，避免为聊天再硬编码一份同义句。
     */
    private static String statusHint(MessageService messages, Player player, QuestStatus status) {
        String key = switch (status) {
            case COMPLETED -> "gui.completed";
            case CLAIMED -> "gui.claimed";
            default -> null;
        };
        return key == null ? "" : TextRenderer.strip(messages.raw(player, key));
    }

    // ---------- 刷新 ----------

    /** {@code refresh}：消耗货币重抽自己的每日任务。 */
    @SubCommand(value = "refresh", description = "刷新每日任务（消耗货币）")
    public void refresh(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        plugin.dailyService().refresh(player).report(plugin.messages(), player);
    }

    // ---------- 领取 ----------

    /**
     * {@code claim <id>}：领取指定任务的奖励。
     * <p>
     * 「为什么领不到」（未接取 / 未完成 / 已领过 / 前置未满足）由
     * {@code RewardService.ClaimOutcome} 一处给出，命令与 GUI 因此不可能出现两种说法。
     */
    @SubCommand(value = "claim", description = "领取已完成任务的奖励")
    public void claim(CommandSender sender, @Arg(value = "id", suggestion = "suggestQuestIds") String id) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        MessageService messages = plugin.messages();

        Quest quest = plugin.quests().find(id).orElse(null);
        if (quest == null) {
            messages.send(player, "quest.not-found", id);
            return;
        }
        plugin.rewardService().claim(player, id).report(messages, player);
    }

    // ---------- 进度 ----------

    /** {@code progress}：列出所有进行中任务的进度（含进度条与逐目标计数）。 */
    @SubCommand(value = "progress", description = "查看当前任务的进度详情")
    public void showProgress(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        List<PlayerQuest> active = plugin.progressService().activeQuests(player.getUniqueId());
        if (active.isEmpty()) {
            plugin.messages().send(player, "daily.none");
            return;
        }
        for (PlayerQuest playerQuest : active) {
            Quest quest = plugin.quests().find(playerQuest.questId()).orElse(null);
            if (quest == null) {
                continue;
            }
            // render 返回的是「已渲染的任务名 + & 颜色码」混合文本，统一过一遍渲染才不会被当字面量显示
            plugin.messages().sendRaw(player,
                    Texts.render(plugin.progressDisplay().render(quest, playerQuest)));
        }
    }

    // ---------- 补全 ----------

    /**
     * {@code claim} 的 id 补全：该玩家<b>未领取</b>的任务 id。
     * <p>
     * 用全部记录而不是 {@code activeQuests}：进行中集合不含「已完成待领取」，
     * 而那恰恰是最需要补全出来让玩家敲的状态。已领取与已放弃的没必要再提示。
     * <p>
     * 方法必须写在本类中（YLib 用 {@code getDeclaredMethod} 查找），因此两个命令类各有一份。
     */
    @SuppressWarnings("unused") // 由 YLib 反射调用
    public List<String> suggestQuestIds(CommandSender sender, CommandContext context, String currentInput) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        if (plugin == null) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (PlayerQuest playerQuest : plugin.playerQuestRepository().findByPlayer(player.getUniqueId())) {
            if (playerQuest.status() == QuestStatus.CLAIMED || playerQuest.status() == QuestStatus.ABANDONED) {
                continue;
            }
            if (Texts.startsWith(playerQuest.questId(), currentInput)) {
                ids.add(playerQuest.questId());
            }
        }
        return ids;
    }

    // ---------- 内部工具 ----------

    /** 打开每日任务界面。 */
    private static void open(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        new DailyQuestMenu(player, messages()).open();
    }

    /** 取消息服务：命令对象不缓存服务引用，统一在执行时向插件实例索取。 */
    private static MessageService messages() {
        return PlayerTaskX.getInstance().messages();
    }

    /** 玩家专属命令的前置判断：控制台/命令方块执行时提示并返回 null。 */
    private static Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        messages().send(sender, "command.player-only");
        return null;
    }
}
