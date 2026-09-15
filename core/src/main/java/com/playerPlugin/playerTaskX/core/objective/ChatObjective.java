package com.playerPlugin.playerTaskX.core.objective;

import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.api.objective.Trigger;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 目标：发言。{@code target} 为消息需包含的关键词，可留空表示任意发言。 */
public final class ChatObjective implements ObjectiveType {

    @Override
    public String id() {
        return "chat";
    }

    @Override
    public String displayName() {
        return "发言";
    }

    @Override
    public Trigger trigger() {
        return Trigger.CHAT;
    }

    @Override
    public List<ConfigField> schema() {
        return List.of(
                ConfigField.text("target", "关键词",
                        "消息需包含的关键词，可用英文逗号分隔多个（命中任意一个即可）；留空或 * 表示任意发言"),
                ConfigField.amount()
        );
    }

    /** 命中关键词时返回本次数量，否则返回 0；用包含匹配而非相等匹配（因此不能复用 {@link ObjectiveType#targetMatches}），关键词与消息都忽略大小写。 */
    @Override
    public int match(ProgressContext context, Map<String, Object> properties) {
        if (!keywordMatches(context, properties)) {
            return 0;
        }
        return context.amount();
    }

    /** 关键词判定：配置留空或 {@code *} 表示任意发言；多个关键词用逗号分隔，命中任意一个即可。 */
    private static boolean keywordMatches(ProgressContext context, Map<String, Object> properties) {
        String configured = str(properties, "target").trim();
        if (configured.isEmpty() || "*".equals(configured)) {
            return true;
        }
        String message = context.target();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        for (String keyword : configured.split(",")) {
            String candidate = keyword.trim().toLowerCase(Locale.ROOT);
            if (!candidate.isEmpty() && normalized.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    /** 读取配置字符串，null 安全：缺失或空值一律返回空串。 */
    private static String str(Map<String, Object> properties, String key) {
        Object value = properties == null ? null : properties.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
