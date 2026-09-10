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
import com.playerPlugin.playerTaskX.api.model.QuestStatus;
import com.playerPlugin.playerTaskX.core.daily.DailyService;
import com.playerPlugin.playerTaskX.core.gui.DailyQuestMenu;
import com.playerPlugin.playerTaskX.core.text.TextRenderer;
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

    /**
     * 行内分隔符。
     * <p>
     * 这里预先转成 {@code §} 形式而不是写 {@code &7 - &f}：拼接后的整行还会过一次
     * {@link #render(String)}，先转义可以避免颜色码被 MiniMessage 当成普通字符留在正文里。
     */
    private static final String SEPARATOR = TextRenderer.translateAmpersand(" &7- &f");

    /** 无参构造：装配方只做 {@code register(new PlayerCommand())}，服务在执行时现取。 */
    public PlayerCommand() {
    }

    // ---------- 打开界面 ----------

    /**
     * 主命令：玩家直接打开界面；控制台则显示帮助。
     * <p>
     * 控制台没有背包界面，之前会回一句「该命令只能由玩家执行」——对管理员来说
     * 这既没告诉他能做什么，也找不到帮助入口。改为直接把帮助打出来。
     */
    @SubCommand(value = "", description = "打开每日任务界面")
    public void menu(CommandSender sender) {
        if (!(sender instanceof Player)) {
            showHelp(sender);
            return;
        }
        open(sender);
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

    /** {@code help}：玩家命令清单。 */
    @SubCommand(value = "help", description = "显示玩家命令清单")
    public void help(CommandSender sender) {
        showHelp(sender);
    }

    /**
     * 帮助正文。
     * <p>
     * 条目与说明全部来自注解（{@code @Command(description)} / {@code @SubCommand(description)}），
     * 由 YLib 的 {@link CommandHelp} 统一渲染——不再手写清单，
     * 因此新增子命令时忘记改帮助的情况不会发生。样式也与其他 YLib 插件一致。
     */
    private static void showHelp(CommandSender sender) {
        CommandHelp.ofAnnotations(messages().raw(sender, "quest.progress"), PlayerCommand.class)
                .subtitle("&8任务 id 可用 Tab 补全")
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

        messages.sendRaw(player, render(messages.raw(player, "quest.progress")));
        for (PlayerQuest playerQuest : current) {
            Quest quest = plugin.quests().find(playerQuest.questId()).orElse(null);
            if (quest == null) {
                // 任务定义已被删除但玩家记录还在：跳过，不让一条脏数据把整条命令带崩
                continue;
            }
            StringBuilder builder = new StringBuilder(TextRenderer.render(quest.name()));
            builder.append(SEPARATOR).append(percent(playerQuest, quest)).append('%');
            String status = statusHint(messages, player, playerQuest.status());
            if (!status.isEmpty()) {
                builder.append(SEPARATOR).append(status);
            }
            messages.sendRaw(player, render(builder.toString()));
        }
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
        feedback(plugin, player, plugin.dailyService().refresh(player));
    }

    // ---------- 领取 ----------

    /**
     * {@code claim <id>}：领取指定任务的奖励。
     * <p>
     * {@code claim} 只返回布尔值，这里按玩家记录把「为什么领不到」还原出来：
     * 否则玩家无论未完成、已领过还是任务已删除，看到的都是同一句失败提示。
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
        if (plugin.rewardService().claim(player, id)) {
            messages.send(player, "quest.claimed", TextRenderer.render(quest.name()));
            return;
        }

        PlayerQuest playerQuest = plugin.playerQuestRepository().find(player.getUniqueId(), id).orElse(null);
        if (playerQuest == null) {
            messages.send(player, "quest.unavailable");
        } else if (playerQuest.status() == QuestStatus.CLAIMED) {
            messages.send(player, "quest.already-claimed");
        } else if (playerQuest.status() == QuestStatus.COMPLETED) {
            // 状态是「已完成待领取」却领取失败，说明发放链路本身出了问题，让玩家去催管理员
            messages.send(player, "error.internal");
        } else {
            messages.send(player, "quest.not-completed");
        }
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
        MessageService messages = plugin.messages();

        List<PlayerQuest> active = plugin.progressService().activeQuests(player.getUniqueId());
        if (active.isEmpty()) {
            messages.send(player, "daily.none");
            return;
        }
        for (PlayerQuest playerQuest : active) {
            Quest quest = plugin.quests().find(playerQuest.questId()).orElse(null);
            if (quest == null) {
                continue;
            }
            // render 返回的是「已渲染的任务名 + & 颜色码」混合文本，统一过一遍 render 才不会被当字面量显示
            messages.sendRaw(player, render(plugin.progressDisplay().render(quest, playerQuest)));
        }
    }

    // ---------- 补全 ----------

    /**
     * {@code claim} 的 id 补全：该玩家<b>未领取</b>的任务 id。
     * <p>
     * 用全部记录而不是 {@code activeQuests}：进行中集合不含「已完成待领取」，
     * 而那恰恰是最需要补全出来让玩家敲的状态。已领取与已放弃的没必要再提示。
     * <p>
     * 方法必须写在本类中（YLib 用 {@code getDeclaredMethod} 查找），因此两个命令类各有一份实现。
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
            if (startsWith(playerQuest.questId(), currentInput)) {
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

    /**
     * 渲染一行拼接文本。
     * <p>
     * 直接用 {@link TextRenderer#render(String)} 不够用：它的 {@code &} 转换只在 MiniMessage
     * 解析失败时才生效，而「任务名（MiniMessage）+ 颜色码」混排时 MiniMessage 会解析成功，
     * 于是 {@code &7} 被当成普通字符留在正文里。这里补上后一步转换，
     * 使任务数据与语言键两种写法都能正常上色。
     */
    private static String render(String raw) {
        return TextRenderer.translateAmpersand(TextRenderer.render(raw));
    }

    /** 完成度百分比（0~100，四舍五入）。 */
    private static long percent(PlayerQuest playerQuest, Quest quest) {
        return Math.round(playerQuest.completionRatio(quest) * 100);
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

    /**
     * 按 {@link DailyService.RefreshResult} 反馈刷新结果。
     * <p>
     * 三种情况互斥：成功（有消耗时追加一行费用）→ 次数用尽（{@code limit > 0}）→ 其余失败原因。
     *
     * @param receiver 反馈对象（玩家命令里就是玩家本人；管理员命令里是执行命令的管理员）
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

    /**
     * 费用文案：数字 + 货币显示名。
     * <p>
     * 货币 id（{@code money} / {@code points}）走 {@code reward.<id>} 语言键，
     * 缺失时退回奖励类型自带的显示名，最后才退回原始 id。
     */
    private static String describeCost(PlayerTaskX plugin, CommandSender receiver, double cost, String currency) {
        String amount = cost == Math.rint(cost) ? String.valueOf((long) cost) : String.valueOf(cost);
        if (currency == null || currency.isBlank()) {
            return amount;
        }
        MessageService messages = plugin.messages();
        String key = "reward." + currency;
        String name = messages.has(key)
                ? TextRenderer.strip(messages.raw(receiver, key))
                : plugin.rewardTypes().displayName(currency);
        return amount + " " + name;
    }

    /** 补全前缀匹配：空输入表示不过滤；YLib 不会对返回值再过滤一次。 */
    private static boolean startsWith(String value, String prefix) {
        return prefix == null || prefix.isEmpty() || value.toLowerCase().startsWith(prefix.toLowerCase());
    }
}
