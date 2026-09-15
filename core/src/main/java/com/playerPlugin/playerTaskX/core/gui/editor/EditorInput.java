package com.playerPlugin.playerTaskX.core.gui.editor;

import cn.yvmou.ylib.scheduler.UniversalScheduler;
import cn.yvmou.ylib.scheduler.UniversalTask;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 聊天栏字段输入：一问一答，每个玩家同时只等一个字段（并发收集反而会让管理员分不清哪句填的是哪项）。
 */
public final class EditorInput {

    /** 超时秒数。 */
    public static final int TIMEOUT_SECONDS = 90;

    /** 取消关键字。 */
    private static final String CANCEL_KEYWORD = "取消";

    /** Java 的 Map 允许 null 值，但那是坑；超时任务单独放在这里，没排上任务时为 null。 */
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private EditorInput() {
    }

    /** 让玩家在聊天栏输入一个字段值：给提示 → 回车提交；非法值保持等待并重新提示；输入 取消/cancel、超时、退服都会放弃。 */
    public static void ask(Player player, String label, ConfigField field, String current,
                           Consumer<String> onValue, Runnable onCancel) {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        if (plugin == null) {
            // 插件不在（单元测试、关服中）：没有调度器就没有超时兜底，宁可什么都不做
            return;
        }
        UniversalScheduler scheduler = plugin.scheduler();
        if (scheduler == null) {
            return;
        }
        forget(player);
        Pending pending = new Pending(label, field, current, onValue, onCancel);
        PENDING.put(player.getUniqueId(), pending);
        prompt(player, label, field, current);
        // 没人可能在 90 秒里输不完；不设超时的话玩家的正常聊天会被无限期吞掉
        pending.timeout.set(scheduler.runLater(player, () -> {
            // 用身份比较而不是 remove(uuid)：玩家中途重开输入时不该被旧任务踢掉
            if (PENDING.remove(player.getUniqueId(), pending)) {
                if (player.isOnline()) {
                    player.sendMessage("§c输入超时，已放弃修改「" + label + "」");
                }
                pending.onCancel.run();
            }
        }, TIMEOUT_SECONDS * 20L));
    }

    /** 该玩家是否正在等待输入（聊天监听器据此决定拦不拦消息）。 */
    public static boolean awaiting(Player player) {
        return PENDING.containsKey(player.getUniqueId());
    }

    /** 处理一次聊天输入（原样传入，内部判断取消关键字与合法性）；返回是否确实有待处理的输入。 */
    public static boolean finish(Player player, String raw) {
        UUID id = player.getUniqueId();
        Pending pending = PENDING.get(id);
        if (pending == null) {
            return false;
        }
        String text = raw == null ? "" : raw.trim();
        if (text.equals(CANCEL_KEYWORD) || text.equalsIgnoreCase("cancel")) {
            cancel(player);
            return true;
        }
        Object value;
        try {
            value = FieldValue.parse(pending.field, raw);
        } catch (IllegalArgumentException e) {
            // 填错不该消耗这次等待：状态留着，重发提示与原因，超时计时照旧
            onMain(player, () -> {
                player.sendMessage("§c" + e.getMessage());
                prompt(player, pending.label, pending.field, pending.current);
            });
            return true;
        }
        // 交给调用方的统一是 display 过的文本，界面与配置存的是同一份写法
        String next = FieldValue.display(value);
        PENDING.remove(id);
        cancelTimeout(pending);
        onMain(player, () -> {
            player.sendMessage("§a已设置「" + pending.label + "」为 §f" + next);
            pending.onValue.accept(next);
        });
        return true;
    }

    /** 放弃等待并执行 onCancel（超时、取消关键字用）。 */
    public static void cancel(Player player) {
        Pending pending = PENDING.remove(player.getUniqueId());
        if (pending == null) {
            return;
        }
        cancelTimeout(pending);
        onMain(player, () -> {
            player.sendMessage("§7已取消输入「" + pending.label + "」");
            pending.onCancel.run();
        });
    }

    /** 丢弃等待状态且不执行任何回调（玩家退服用）。 */
    public static void forget(Player player) {
        Pending pending = PENDING.remove(player.getUniqueId());
        if (pending == null) {
            return;
        }
        cancelTimeout(pending);
    }

    /** 回调与发消息都必须在主线程：聊天事件是异步的，Folia 上还会额外校验线程归属。 */
    private static void onMain(Player player, Runnable task) {
        PlayerTaskX plugin = PlayerTaskX.getInstance();
        if (plugin == null || plugin.scheduler() == null) {
            return;
        }
        plugin.scheduler().runLater(player, task, 1L);
    }

    private static void cancelTimeout(Pending pending) {
        UniversalTask task = pending.timeout.get();
        // 超时任务自己触发时无需再取消自己
        if (task != null) {
            task.cancel();
        }
    }

    /** 输入提示：说明填什么、当前值、格式与取消方式；一次发一行，不依赖客户端把 \n 当换行渲染。 */
    private static void prompt(Player player, String label, ConfigField field, String current) {
        String hint = field.hint();
        player.sendMessage("§e请在聊天栏输入「" + label + "」的值"
                + "§7（可填：" + (hint == null || hint.isBlank() ? "自由文本" : hint) + "）");
        player.sendMessage("§7当前值：§f" + (current == null || current.isBlank() ? "（无）" : current));
        player.sendMessage("§7直接回车提交；输入 §f取消§7 放弃（留空表示清除该字段）");
    }

    /** 一次等待中的输入：字段信息 + 两个回调 + 超时任务（排上之前是 null）。 */
    private static final class Pending {

        private final String label;
        private final ConfigField field;
        /** 提问时的值，用于非法输入后的重发提示。 */
        private final String current;
        private final Consumer<String> onValue;
        private final Runnable onCancel;
        private final AtomicReference<UniversalTask> timeout = new AtomicReference<>();

        private Pending(String label, ConfigField field, String current,
                        Consumer<String> onValue, Runnable onCancel) {
            this.label = label;
            this.field = field;
            this.current = current;
            this.onValue = onValue;
            this.onCancel = onCancel;
        }
    }
}
