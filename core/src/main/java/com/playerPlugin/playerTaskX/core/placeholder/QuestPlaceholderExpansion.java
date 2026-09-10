package com.playerPlugin.playerTaskX.core.placeholder;

import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestStatus;
import com.playerPlugin.playerTaskX.core.text.TextRenderer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * PlaceholderAPI 变量扩展。
 *
 * <h2>支持变量</h2>
 * <ul>
 *   <li>{@code %playertaskx_daily_count%} 今日每日任务总数</li>
 *   <li>{@code %playertaskx_daily_active%} 进行中的每日任务数</li>
 *   <li>{@code %playertaskx_daily_completed%} 已完成待领取数</li>
 *   <li>{@code %playertaskx_daily_claimed%} 已领取数</li>
 *   <li>{@code %playertaskx_daily_refresh_left%} 剩余刷新次数</li>
 *   <li>{@code %playertaskx_daily_refresh_cost%} 刷新费用</li>
 *   <li>{@code %playertaskx_claimable%} 全部可领取数量</li>
 *   <li>{@code %playertaskx_active%} 进行中任务总数（含普通任务）</li>
 *   <li>{@code %playertaskx_quest_name_<id>%} 任务显示名（去格式）</li>
 *   <li>{@code %playertaskx_quest_progress_<id>%} 形如 {@code 3/64}</li>
 *   <li>{@code %playertaskx_quest_percent_<id>%} 完成百分比（整数）</li>
 *   <li>{@code %playertaskx_quest_status_<id>%} 状态文案（取自语言文件）</li>
 * </ul>
 * <p>
 * <b>注册方式</b>：本类只应由 {@link PlaceholderHook} 反射加载——
 * 直接 import 并在主类引用它会让未安装 PlaceholderAPI 的服务端在类加载阶段
 * 抛 NoClassDefFoundError，整个插件都无法启动。
 */
public final class QuestPlaceholderExpansion extends PlaceholderExpansion {

    private final PlayerTaskX plugin;

    /**
     * 构造器形参声明为 {@code Plugin}：调用方 {@code PlaceholderHook} 做完全反射，
     * 不应依赖主类类型；这里再转回具体类型。
     */
    public QuestPlaceholderExpansion(org.bukkit.plugin.Plugin plugin) {
        this.plugin = (PlayerTaskX) plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "playertaskx";
    }

    @Override
    public @NotNull String getAuthor() {
        return "yvmouX";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /** 常驻扩展：插件卸载前不应被 PAPI 回收。 */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        UUID playerId = player.getUniqueId();
        String key = params.toLowerCase(Locale.ROOT);

        return switch (key) {
            case "daily_count" -> String.valueOf(plugin.dailyService().currentQuests(playerId).size());
            case "daily_active" -> String.valueOf(countDaily(playerId, QuestStatus.IN_PROGRESS));
            case "daily_completed" -> String.valueOf(countDaily(playerId, QuestStatus.COMPLETED));
            case "daily_claimed" -> String.valueOf(countDaily(playerId, QuestStatus.CLAIMED));
            case "daily_refresh_left" -> String.valueOf(plugin.dailyService().remainingRefreshes(playerId));
            case "daily_refresh_cost" -> formatCost();
            case "claimable" -> String.valueOf(plugin.rewardService().claimableCount(playerId));
            case "active" -> String.valueOf(plugin.progressService().activeQuests(playerId).size());
            default -> handleQuestVariable(playerId, key);
        };
    }

    /** 处理 {@code quest_<字段>_<任务id>} 形式的变量。 */
    private String handleQuestVariable(UUID playerId, String key) {
        int separator = key.indexOf('_', "quest_".length());
        if (!key.startsWith("quest_") || separator < 0) {
            return null;
        }
        String field = key.substring("quest_".length(), separator);
        String questId = key.substring(separator + 1);

        Quest quest = plugin.quests().find(questId).orElse(null);
        if (quest == null) {
            return "";
        }

        return switch (field) {
            case "name" -> TextRenderer.strip(quest.name());
            case "id" -> quest.id();
            case "type" -> quest.type().name();
            case "category" -> quest.category() == null ? "" : quest.category();
            case "progress" -> progressText(playerId, quest);
            case "percent" -> percentText(playerId, quest);
            case "status" -> statusText(playerId, quest);
            default -> null;
        };
    }

    private String progressText(UUID playerId, Quest quest) {
        PlayerQuest record = plugin.playerQuestRepository().find(playerId, quest.id()).orElse(null);
        if (record == null) {
            return "0/" + totalRequired(quest);
        }
        return doneRequired(record, quest) + "/" + totalRequired(quest);
    }

    private String percentText(UUID playerId, Quest quest) {
        PlayerQuest record = plugin.playerQuestRepository().find(playerId, quest.id()).orElse(null);
        if (record == null) {
            return "0";
        }
        return String.valueOf(Math.round(record.completionRatio(quest) * 100));
    }

    /** 状态文案取自语言文件，便于服务器自定义措辞。 */
    private String statusText(UUID playerId, Quest quest) {
        PlayerQuest record = plugin.playerQuestRepository().find(playerId, quest.id()).orElse(null);
        if (record == null) {
            return "";
        }
        String key = switch (record.status()) {
            case IN_PROGRESS -> "gui.in-progress";
            case COMPLETED -> "gui.completed";
            case CLAIMED -> "gui.claimed";
            case ABANDONED -> "quest.abandoned";
        };
        String raw = plugin.messages().has(key) ? plugin.messages().raw(key) : record.status().name();
        return TextRenderer.strip(raw);
    }

    private long countDaily(UUID playerId, QuestStatus status) {
        return plugin.dailyService().currentQuests(playerId).stream()
                .filter(record -> record.status() == status)
                .count();
    }

    private int totalRequired(Quest quest) {
        return quest.objectives().stream().mapToInt(objective -> objective.amount()).sum();
    }

    private int doneRequired(PlayerQuest record, Quest quest) {
        int done = 0;
        for (int i = 0; i < quest.objectives().size(); i++) {
            done += Math.min(quest.objectives().get(i).amount(), record.progress(i));
        }
        return done;
    }

    private String formatCost() {
        double cost = plugin.config().getDailyRefreshCost();
        // 去掉多余小数位，避免变量里出现 1000.0 这种不美观的输出
        if (cost == Math.floor(cost)) {
            return String.valueOf((long) cost);
        }
        return String.valueOf(cost);
    }

    /** 供调试：列出本扩展全部变量名。 */
    public List<String> knownVariables() {
        return List.of("daily_count", "daily_active", "daily_completed", "daily_claimed",
                "daily_refresh_left", "daily_refresh_cost", "claimable", "active",
                "quest_name_<id>", "quest_progress_<id>", "quest_percent_<id>", "quest_status_<id>");
    }
}
