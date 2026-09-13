package com.playerPlugin.playerTaskX.core.storage;

import com.playerPlugin.playerTaskX.api.model.Preset;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * {@link PresetRepository} 的文件实现。
 *
 * <h2>为什么预设是「单文件」而任务定义是「一任务一文件」</h2>
 * 两者都是内容类数据，但规模与使用方式不同：预设通常十来条、只在编辑器里成批调整，
 * 单文件便于整体查看与备份；任务定义可能有几十上百条、由不同人分别改，
 * 一任务一文件才能把损坏面与冲突面限制在单个任务上。
 *
 * <h2>文件形态</h2>
 * <pre>
 * {
 *   "version": 1,
 *   "objectives": [ { "id": "mine-stone", "name": "挖 64 个石头", "type": "break_block",
 *                     "properties": { "target": "STONE", "amount": 64 }, "description": "…" } ],
 *   "rewards": [ … ]
 * }
 * </pre>
 * 类别（objectives / rewards）由所在数组决定，因此读取时直接标到
 * {@link Preset#kind()} 上，不必让用户额外写一个字段。
 *
 * <h2>容错</h2>
 * <ul>
 *   <li>文件不存在 → 写入内置默认预设（让管理员一进编辑器就看得见怎么用）；</li>
 *   <li>文件损坏 → <b>保留内置默认预设并覆盖</b>，不让一个坏文件把编辑器整个打不开；</li>
 *   <li>缺少 {@code type} 的条目 → 跳过并记警告（没有 type 无法套用）；</li>
 *   <li>缺少 {@code id} → 自动生成，避免静默丢条目。</li>
 * </ul>
 * 写入一律经 {@link JsonFileStore}（临时文件 + 原子改名）。
 */
public final class PresetFileRepository implements PresetRepository {

    private static final String KEY_VERSION = "version";
    private static final String KEY_OBJECTIVES = Preset.OBJECTIVES;
    private static final String KEY_REWARDS = Preset.REWARDS;

    private final JsonFileStore files;
    private final File file;
    private final Consumer<String> warn;

    /** 已载入的预设，按 id 索引；顺序即文件中的顺序。 */
    private final Map<String, Preset> presets = new LinkedHashMap<>();

    public PresetFileRepository(File dataFolder, Consumer<String> warn) {
        File folder = dataFolder == null ? new File(".") : dataFolder;
        this.file = new File(folder, "presets.json");
        this.files = new JsonFileStore(folder.toPath());
        this.warn = warn;
        load();
    }

    /** 便捷构造：日志落到标准错误。 */
    public static PresetFileRepository of(Path dataFolder) {
        return new PresetFileRepository(dataFolder.toFile(), System.err::println);
    }

    @Override
    public List<Preset> findAll() {
        return List.copyOf(presets.values());
    }

    @Override
    public Optional<Preset> findById(String id) {
        return Optional.ofNullable(presets.get(id));
    }

    @Override
    public void save(Preset preset) {
        if (preset == null || preset.id() == null || preset.id().isBlank()) {
            throw new StorageException("预设必须有 id 才能保存");
        }
        // 覆盖时保持原有位置，避免每次保存都把条目挪到末尾
        presets.put(preset.id(), preset);
        persist();
    }

    @Override
    public boolean delete(String id) {
        if (presets.remove(id) == null) {
            return false;
        }
        persist();
        return true;
    }

    @Override
    public long count() {
        return presets.size();
    }

    /** 预设文件路径，供报错提示与「去哪改」的说明使用。 */
    public File file() {
        return file;
    }

    // ------------------------------------------------------------------
    // 读写
    // ------------------------------------------------------------------

    /** 读取文件；不存在或损坏时退回内置默认预设并写盘。 */
    @SuppressWarnings("unchecked")
    private void load() {
        String json = files.readFile(file.toPath());
        if (json != null) {
            try {
                Map<String, Object> root = JsonCodec.readMapStrict(json);
                if (root != null) {
                    readInto(root.get(KEY_OBJECTIVES), Preset.OBJECTIVES);
                    readInto(root.get(KEY_REWARDS), Preset.REWARDS);
                    if (!presets.isEmpty()) {
                        return;
                    }
                }
            } catch (Exception e) {
                warn.accept("预设文件不是合法 JSON，已改用内置默认预设: " + file.getName()
                        + "（" + e.getMessage() + "）");
            }
        }
        seedDefaults();
        persist();
    }

    /** 读一个类别数组；缺 type 的条目跳过，缺 id 的自动生成。 */
    private void readInto(Object raw, String kind) {
        if (!(raw instanceof List<?> list)) {
            return;
        }
        int index = 0;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?>)) {
                index++;
                continue;
            }
            Map<String, Object> node = JsonCodec.asMap(item);

            String type = JsonCodec.text(node.get("type"));
            if (type.isBlank()) {
                warn.accept("预设文件里第 " + (index + 1) + " 个 " + kind
                        + " 预设缺少 type，已跳过（没有 type 无法套用）");
                index++;
                continue;
            }
            String id = JsonCodec.text(node.get("id"));
            if (id.isBlank()) {
                // 自动生成而不是丢弃：用户可能只关心 type 与属性，不该因此少一条
                id = kind + "-" + index;
                warn.accept("预设文件里第 " + (index + 1) + " 个 " + kind
                        + " 预设缺少 id，已自动命名为 " + id);
            }
            String name = JsonCodec.text(node.get("name"));
            presets.put(id, new Preset(kind, id, name.isBlank() ? type : name,
                    type, JsonCodec.asMap(node.get("properties")), JsonCodec.text(node.get("description"))));
            index++;
        }
    }

    /** 按类别分组写盘。 */
    private void persist() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put(KEY_VERSION, 1);
        root.put(KEY_OBJECTIVES, toList(Preset.OBJECTIVES));
        root.put(KEY_REWARDS, toList(Preset.REWARDS));
        files.write("presets", JsonCodec.write(root));
    }

    private List<Map<String, Object>> toList(String kind) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Preset preset : presets.values()) {
            if (!kind.equals(preset.kind())) {
                continue;
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", preset.id());
            node.put("name", preset.name());
            node.put("type", preset.type());
            node.put("properties", preset.properties());
            node.put("description", preset.description());
            list.add(node);
        }
        return list;
    }

    /** 内置默认预设：让管理员一进编辑器就看得见预设怎么用。 */
    private void seedDefaults() {
        seed("mine-stone", Preset.OBJECTIVES, "挖 64 个石头", "break_block",
                props("target", "STONE", "amount", 64), "最基础的挖掘目标");
        seed("mine-diamond", Preset.OBJECTIVES, "挖 16 个钻石矿", "break_block",
                props("target", "DIAMOND_ORE,DEEPSLATE_DIAMOND_ORE", "amount", 16), "两种钻石矿都计入");
        seed("kill-zombie", Preset.OBJECTIVES, "击杀 20 只僵尸", "kill",
                props("target", "ZOMBIE", "amount", 20), "");
        seed("kill-any", Preset.OBJECTIVES, "击杀任意生物 30 只", "kill",
                props("target", "", "amount", 30), "target 留空表示任意生物");
        seed("craft-torch", Preset.OBJECTIVES, "合成 16 个火把", "craft",
                props("target", "TORCH", "amount", 16), "");
        seed("fish-any", Preset.OBJECTIVES, "钓 10 条鱼", "fish",
                props("target", "", "amount", 10), "");
        seed("chat-hello", Preset.OBJECTIVES, "发言一次", "chat",
                props("target", "", "amount", 1), "任意发言都算");

        seed("reward-money", Preset.REWARDS, "奖励 500 金币", "money",
                props("amount", 500), "需要 Vault");
        seed("reward-exp", Preset.REWARDS, "奖励 100 经验", "exp",
                props("amount", 100), "无需任何依赖");
        seed("reward-diamond", Preset.REWARDS, "奖励 3 个钻石", "item",
                props("material", "DIAMOND", "amount", 3), "");
        seed("reward-points", Preset.REWARDS, "奖励 100 点券", "points",
                props("amount", 100), "需要 PlayerPoints");
    }

    private void seed(String id, String kind, String name, String type,
                      Map<String, Object> properties, String description) {
        presets.put(id, new Preset(kind, id, name, type, properties, description));
    }

    private static Map<String, Object> props(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    /** 供日志展示。 */
    @Override
    public String toString() {
        return "PresetFileRepository[" + file.getAbsolutePath() + "]";
    }
}
