package com.playerPlugin.playerTaskX.core.placeholder;

import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestStatus;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import cn.yvmou.ylib.text.TextRenderer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.UUID;

/**
 * PlaceholderAPI 变量扩展（标识符 {@code playertaskx}）：任务数量、进度、状态等变量，清单见 {@code docs/placeholders.md}。
 * 只能由 {@link PlaceholderHook} 反射加载：在主类直接引用它，会让未装 PlaceholderAPI 的服务端在类加载阶段抛 {@code NoClassDefFoundError}。
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
            case "claimable" -> String.valueOf(plugin.rewardService().claimableCount(playerId));
            case "active" -> String.valueOf(plugin.progressService().activeQuests(playerId).size());
            default -> {
                String periodic = handlePeriodicVariable(playerId, key);
                yield periodic != null ? periodic : handleQuestVariable(playerId, key);
            }
        };
    }

    /** 处理 {@code <周期>_<字段>} 变量；四种周期共用一套字段名，加周期不必补分支，服务器没启用的周期照样能查、结果为空（比「变量不存在」好排查）。 */
    private String handlePeriodicVariable(UUID playerId, String key) {
        for (QuestType type : QuestType.values()) {
            if (!type.isPeriodic()) {
                continue;
            }
            String prefix = type.name().toLowerCase(Locale.ROOT) + "_";
            if (!key.startsWith(prefix)) {
                continue;
            }
            String field = key.substring(prefix.length());
            return switch (field) {
                case "count" -> String.valueOf(plugin.periodicService().currentQuests(playerId, type).size());
                case "active" -> String.valueOf(countPeriodic(playerId, type, QuestStatus.IN_PROGRESS));
                case "completed" -> String.valueOf(countPeriodic(playerId, type, QuestStatus.COMPLETED));
                case "claimed" -> String.valueOf(countPeriodic(playerId, type, QuestStatus.CLAIMED));
                case "refresh_left" ->
                        String.valueOf(plugin.periodicService().remainingRefreshes(playerId, type));
                case "refresh_cost" -> formatCost(type);
                default -> null;
            };
        }
        return null;
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

    private long countPeriodic(UUID playerId, QuestType type, QuestStatus status) {
        return plugin.periodicService().currentQuests(playerId, type).stream()
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

    private String formatCost(QuestType type) {
        double cost = plugin.periodicService().settings(type).refreshCost();
        // 去掉多余小数位，避免变量里出现 1000.0 这种不美观的输出
        if (cost == Math.floor(cost)) {
            return String.valueOf((long) cost);
        }
        return String.valueOf(cost);
    }
}
