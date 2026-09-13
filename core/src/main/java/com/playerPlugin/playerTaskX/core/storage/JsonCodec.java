package com.playerPlugin.playerTaskX.core.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON 编解码工具：{@code properties} / {@code description} / {@code progress} 三类列的统一出入口。
 * <p>
 * 设计取舍：
 * <ul>
 *   <li>静态单例 {@link ObjectMapper}：ObjectMapper 构造代价高但读写线程安全，
 *       仓储每次读写都新建一个纯属浪费。</li>
 *   <li><b>所有 read 方法绝不抛异常</b>：数据库里一条脏数据（手工改库、旧版本格式、
 *       写入中途崩溃）不应该让插件启动失败，也不应该让整个任务列表读不出来。
 *       解析失败一律降级为空集合，并往 stderr 打一条记录便于排查——
 *       存储层的容错优先级高于严格性。</li>
 *   <li>统一返回 {@link LinkedHashMap}/{@link ArrayList} 而非 HashMap：保留字段原始顺序，
 *       网页编辑器与 GUI 展示的字段顺序才稳定。</li>
 * </ul>
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

    /**
     * 反序列化进度表。
     * <p>
     * 键既可能是 JSON 字符串（{@code {"0":5}}，正常路径），也可能被写成数字或浮点文本
     * （{@code {"0":5.0}}、{@code {" 1 ":2}}），因此统一走宽松转换；
     * 无法转换的键值对直接丢弃而不是让整张表读不出来。
     */
    public static Map<Integer, Integer> readIntMap(String json) {
        Map<String, Object> raw = readMap(json);
        Map<Integer, Integer> result = new LinkedHashMap<>(raw.size());
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
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
    // 任意结构：编辑器响应体（List / Map 混排）
    // ------------------------------------------------------------------

    /**
     * 序列化任意结构。
     * <p>
     * 与 {@link #write(Map)} 的区别：那个把失败降级成 {@code "{}"}（数据库列不能写 NULL），
     * 而 HTTP 响应体写坏了应当让调用方看见，因此这里失败返回 {@code null}。
     */
    public static String writeAny(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            warn("JSON 序列化失败", e);
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 严格读取：区分「解析失败」与「合法空对象」
    // ------------------------------------------------------------------

    /**
     * 解析 JSON 对象，<b>失败时抛出异常</b>而不是降级为空表。
     * <p>
     * {@link #readMap(String)} 是为数据库列设计的：一条脏数据不该让整张表读不出来，
     * 所以它静默降级。但文件方案需要区分两种情况——「文件内容坏了」必须跳过该文件并报错，
     * 而「合法但为空的对象」是正常数据。静默降级会把前者伪装成后者，导致用户的文件
     * 明明写错了却只看到一个空任务。
     *
     * @return 解析出的对象；输入为 null/空白时返回 null（表示"没有内容"）
     * @throws Exception 内容不是合法 JSON 对象
     */
    public static Map<String, Object> readMapStrict(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return null;
        }
        Map<String, Object> parsed = MAPPER.readValue(json, MAP_TYPE);
        return parsed == null ? null : new LinkedHashMap<>(parsed);
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
                // 再试一次浮点形式：某些旧数据/编辑器会把整数写成 5.0
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
