package com.playerPlugin.playerTaskX.core.web;

import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 目标与奖励的预设。
 *
 * <h2>用途</h2>
 * 建任务时反复填写「挖掘 64 个石头」这类相同配置很费事。预设把这些配置存下来，
 * 在编辑器里点一下就能套用，也可以直接改一处、复用到多个任务。
 *
 * <h2>为什么不进数据库</h2>
 * 预设是编辑器的便利设施，不是运行时数据：引擎只认任务里的目标/奖励，
 * 不认识预设。放进插件的 JSON 文件即可——便于手工编辑、随配置一起备份，
 * 也避免为了一个辅助功能去动数据库表结构。
 *
 * <h2>与「类型 schema」的关系</h2>
 * 预设只是「类型 + 一组属性值」，因此新增目标/奖励类型时预设不必同步改动：
 * 老预设引用已删除的类型时会被前端标记为无效，但不会影响其它预设。
 */
public final class PresetStore {

    /** 文件中的顶层结构。 */
    private static final String KEY_VERSION = "version";
    private static final String KEY_OBJECTIVES = "objectives";
    private static final String KEY_REWARDS = "rewards";

    private final File file;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Map<String, Object>> objectives = new ArrayList<Map<String, Object>>();
    private final List<Map<String, Object>> rewards = new ArrayList<Map<String, Object>>();

    public PresetStore(File dataFolder) {
        this.file = new File(dataFolder, "presets.json");
        load();
    }

    /** 全部预设，供编辑器读取。 */
    public Map<String, Object> all() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put(KEY_OBJECTIVES, copyList(objectives));
        result.put(KEY_REWARDS, copyList(rewards));
        return result;
    }

    /**
     * 保存一个预设（同 id 覆盖）。
     *
     * @param kind      {@code objectives} 或 {@code rewards}
     * @param preset    预设内容；id 为空时自动生成
     * @return 保存后的预设，参数非法时返回 null
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> save(String kind, Map<String, Object> preset) {
        List<Map<String, Object>> target = listFor(kind);
        if (target == null || preset == null) {
            return null;
        }
        String type = String.valueOf(preset.get("type") == null ? "" : preset.get("type")).trim();
        if (type.isEmpty()) {
            return null;
        }
        Map<String, Object> entry = new LinkedHashMap<String, Object>();
        String id = String.valueOf(preset.get("id") == null ? "" : preset.get("id")).trim();
        entry.put("id", id.isEmpty() ? UUID.randomUUID().toString().substring(0, 8) : id);
        entry.put("name", nameOf(preset, type));
        entry.put("type", type);
        entry.put("description", String.valueOf(preset.get("description") == null ? "" : preset.get("description")));
        Object properties = preset.get("properties");
        entry.put("properties", properties instanceof Map ? new LinkedHashMap<String, Object>((Map<String, Object>) properties)
                : new LinkedHashMap<String, Object>());

        // 同 id 覆盖：先移除旧的同名条目，保证文件里不出现重复 id
        target.removeIf(existing -> entry.get("id").equals(existing.get("id")));
        target.add(entry);
        persist();
        return entry;
    }

    /** 删除预设，返回是否删到了。 */
    public boolean remove(String kind, String id) {
        List<Map<String, Object>> target = listFor(kind);
        if (target == null || id == null) {
            return false;
        }
        boolean removed = target.removeIf(entry -> id.equals(entry.get("id")));
        if (removed) {
            persist();
        }
        return removed;
    }

    /** 把预设转成任务目标（供前端预览与后端校验用）。 */
    public static QuestObjective toObjective(Map<String, Object> preset) {
        return QuestObjective.of(String.valueOf(preset.get("type")), asMap(preset.get("properties")));
    }

    /** 把预设转成任务奖励。 */
    public static QuestReward toReward(Map<String, Object> preset) {
        return QuestReward.of(String.valueOf(preset.get("type")), asMap(preset.get("properties")));
    }

    // ------------------------------------------------------------------
    // 持久化
    // ------------------------------------------------------------------

    private List<Map<String, Object>> listFor(String kind) {
        if (KEY_OBJECTIVES.equalsIgnoreCase(kind)) {
            return objectives;
        }
        if (KEY_REWARDS.equalsIgnoreCase(kind)) {
            return rewards;
        }
        return null;
    }

    private static String nameOf(Map<String, Object> preset, String fallback) {
        String name = String.valueOf(preset.get("name") == null ? "" : preset.get("name")).trim();
        return name.isEmpty() ? fallback : name;
    }

    /** 读取文件；不存在或损坏时退回内置默认预设。 */
    @SuppressWarnings("unchecked")
    private void load() {
        if (file.exists()) {
            try {
                Map<String, Object> parsed = mapper.readValue(
                        new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8), Map.class);
                readInto(parsed.get(KEY_OBJECTIVES), objectives);
                readInto(parsed.get(KEY_REWARDS), rewards);
                if (!objectives.isEmpty() || !rewards.isEmpty()) {
                    return;
                }
            } catch (Exception ignored) {
                // 文件损坏：用默认预设覆盖，避免编辑器因为一个坏文件完全不可用
            }
        }
        seedDefaults();
        persist();
    }

    @SuppressWarnings("unchecked")
    private void readInto(Object raw, List<Map<String, Object>> target) {
        if (!(raw instanceof List)) {
            return;
        }
        for (Object item : (List<Object>) raw) {
            if (item instanceof Map) {
                target.add(new LinkedHashMap<String, Object>((Map<String, Object>) item));
            }
        }
    }

    private void persist() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return;
            }
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            out.put(KEY_VERSION, 1);
            out.put(KEY_OBJECTIVES, objectives);
            out.put(KEY_REWARDS, rewards);
            Files.write(file.toPath(), mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(out));
        } catch (IOException ignored) {
            // 写失败只影响预设的持久化，不影响编辑器其它功能
        }
    }

    /**
     * 内置默认预设。
     * <p>
     * 只放几个任务里最高频的组合，让管理员一进编辑器就能看到预设怎么用；
     * 多余的可自行删除或修改（文件是纯 JSON，可手工编辑）。
     */
    private void seedDefaults() {
        objectives.add(preset("mine-stone", "挖 64 个石头", "break_block",
                props("target", "STONE", "amount", 64), "最基础的挖掘目标"));
        objectives.add(preset("mine-diamond", "挖 16 个钻石矿", "break_block",
                props("target", "DIAMOND_ORE,DEEPSLATE_DIAMOND_ORE", "amount", 16), "两种钻石矿都计入"));
        objectives.add(preset("kill-zombie", "击杀 20 只僵尸", "kill",
                props("target", "ZOMBIE", "amount", 20), ""));
        objectives.add(preset("kill-any", "击杀任意生物 30 只", "kill",
                props("target", "", "amount", 30), "target 留空表示任意生物"));
        objectives.add(preset("craft-torch", "合成 16 个火把", "craft",
                props("target", "TORCH", "amount", 16), ""));
        objectives.add(preset("fish-any", "钓 10 条鱼", "fish",
                props("target", "", "amount", 10), ""));
        objectives.add(preset("chat-hello", "发言一次", "chat",
                props("target", "", "amount", 1), "任意发言都算"));

        rewards.add(preset("reward-money", "奖励 500 金币", "money",
                props("amount", 500), "需要 Vault"));
        rewards.add(preset("reward-exp", "奖励 100 经验", "exp",
                props("amount", 100), "无需任何依赖"));
        rewards.add(preset("reward-diamond", "奖励 3 个钻石", "item",
                props("material", "DIAMOND", "amount", 3), ""));
        rewards.add(preset("reward-points", "奖励 100 点券", "points",
                props("amount", 100), "需要 PlayerPoints"));
    }

    private static Map<String, Object> preset(String id, String name, String type,
                                             Map<String, Object> properties, String description) {
        Map<String, Object> entry = new LinkedHashMap<String, Object>();
        entry.put("id", id);
        entry.put("name", name);
        entry.put("type", type);
        entry.put("properties", properties);
        entry.put("description", description);
        return entry;
    }

    private static Map<String, Object> props(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object raw) {
        return raw instanceof Map ? new LinkedHashMap<String, Object>((Map<String, Object>) raw)
                : new LinkedHashMap<String, Object>();
    }

    private static List<Map<String, Object>> copyList(List<Map<String, Object>> source) {
        List<Map<String, Object>> copy = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> entry : source) {
            copy.add(new LinkedHashMap<String, Object>(entry));
        }
        return copy;
    }
}
