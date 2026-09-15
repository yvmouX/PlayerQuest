package com.playerPlugin.playerTaskX.core.storage.codec;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON 编解码工具：{@code properties} / {@code description} / {@code progress} 三类列的统一出入口。
 * 所有 read 方法绝不抛异常——库里一条脏数据只降级为空集合并记日志，不能让整个任务列表读不出来；返回值保序。
 */
public final class JsonCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final TypeReference<List<Object>> LIST_TYPE = new TypeReference<>() {
    };

    /** 打印脏数据时截断长度，避免把整列内容刷进日志。 */
    private static final int PREVIEW_LIMIT = 120;

    private JsonCodec() {
    }

    // ------------------------------------------------------------------
    // Map<String, Object>：任务的 properties 列
    // ------------------------------------------------------------------

    /** 序列化任意属性表；null/空表写成 {@code "{}"}，保证列里永远不出现 NULL。 */
    public static String write(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        try {
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            // 例如属性里塞了无法序列化的对象、嵌套过深
            warn("属性表序列化失败，已写为空对象", e);
            return "{}";
        }
    }

    /** 反序列化属性表；null/空白/非法 JSON 一律返回空表。 */
    public static Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = MAPPER.readValue(json, MAP_TYPE);
            return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
        } catch (Exception e) {
            warn("属性列不是合法 JSON 对象，已按空配置处理: " + preview(json), e);
            return new LinkedHashMap<>();
        }
    }

    // ------------------------------------------------------------------
    // Map<Integer, Integer>：玩家任务的 progress 列
    // ------------------------------------------------------------------

    /**
     * 序列化进度表；null/空表写成 {@code "{}"}。
     * <p>
     * JSON 的对象键只能是字符串，因此 {@code {0=5}} 会变成 {@code {"0":5}}，
     * 读取端必须能把字符串键转回 Integer（见 {@link #readIntMap(String)}）。
     */
    public static String writeIntMap(Map<Integer, Integer> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        try {
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            warn("进度表序列化失败，已写为空对象", e);
            return "{}";
        }
    }

    /** 反序列化进度表：键可能是字符串、数字或浮点文本（手工改库所致），统一宽松转换；转不出来的键值对直接丢弃，不让整张表读不出来。 */
    public static Map<Integer, Integer> readIntMap(String json) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : readMap(json).entrySet()) {
            // 键也用宽松转换：JSON 键只能是字符串，但手工改库时可能写成数字
            Integer key = toInteger(entry.getKey());
            Integer value = toInteger(entry.getValue());
            if (key == null || value == null) {
                continue;
            }
            result.put(key, value);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 各仓储共用的取值工具
    // ------------------------------------------------------------------

    /**
     * 取一个文本字段：null 与缺省一律给空串，并去掉首尾空白。
     * <p>
     * 手工编辑过的文件里 {@code " type ": "DAILY "} 这类写法很常见，
     * 直接拿去比枚举名会静默落空；统一在这里 trim，各调用点就不必各修一次。
     */
    public static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * 把解析出来的任意对象当作属性表，键统一转成字符串。
     * <p>
     * 只接受 Map：不是对象时返回空表而不是抛异常——存储层的一条脏数据
     * 不该让整个任务或预设读不出来。
     */
    public static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    // ------------------------------------------------------------------
    // List<String>：任务描述列
    // ------------------------------------------------------------------

    /** 序列化字符串列表；null/空列表写成 {@code "[]"}。 */
    public static String writeStringList(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(list);
        } catch (Exception e) {
            warn("字符串列表序列化失败，已写为空数组", e);
            return "[]";
        }
    }

    /** 反序列化字符串列表；null/空白/非法 JSON/非数组一律返回空列表。 */
    public static List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<Object> parsed = MAPPER.readValue(json, LIST_TYPE);
            if (parsed == null) {
                return new ArrayList<>();
            }
            List<String> result = new ArrayList<>(parsed.size());
            for (Object element : parsed) {
                // 元素为 null 时写成空行，避免把 null 传进 Quest 的 List.copyOf（会 NPE）
                result.add(element == null ? "" : String.valueOf(element));
            }
            return result;
        } catch (Exception e) {
            warn("字符串列表列不是合法 JSON 数组，已按空列表处理: " + preview(json), e);
            return new ArrayList<>();
        }
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /** 宽松转整数：支持 Number、整数字符串与浮点字符串（如 {@code "5.0"}），失败返回 null。 */
    private static Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            try {
                return Integer.valueOf(trimmed);
            } catch (NumberFormatException ignored) {
                // 再试一次浮点形式：某些旧数据会把整数写成 5.0
                try {
                    return (int) Double.parseDouble(trimmed);
                } catch (NumberFormatException alsoIgnored) {
                    return null;
                }
            }
        }
        return null;
    }

    /** 截断预览，避免日志被超长 JSON 淹没。 */
    private static String preview(String json) {
        String single = json.replace('\n', ' ').trim();
        return single.length() <= PREVIEW_LIMIT ? single : single.substring(0, PREVIEW_LIMIT) + "...";
    }

    private static void warn(String message, Exception cause) {
        System.err.println("[PlayerTaskX] " + message + " (" + cause.getClass().getSimpleName()
                + ": " + cause.getMessage() + ")");
    }
}
