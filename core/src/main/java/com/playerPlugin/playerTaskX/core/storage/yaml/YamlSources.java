package com.playerPlugin.playerTaskX.core.storage.yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * YAML 定义源的读取与缓存：把「扫目录 → 解析 → 转模型 → 记 id 位置」收在一处，
 * 任务与预设各持一个实例。
 *
 * <h2>缓存策略</h2>
 * {@link #all()} 每次调用都<b>重新读盘</b>——它只被启动与 {@code /ptxa reload} 调用，
 * 正好是「希望看到文件最新内容」的时机。{@link #find(String)} 与 {@link #location(String)}
 * 用上一次的结果，因此编辑器逐条问「这条是不是文件里的」不会变成磁盘风暴。
 *
 * <h2>文件之间的重复 id</h2>
 * 按文件路径排序后<b>先到先得</b>并告警：同一份目录树每次得到相同结果，
 * 不像「后加载覆盖先加载」那样取决于文件系统返回顺序。
 */
public final class YamlSources<T> {

    private final DefinitionFolder folder;
    private final Function<DefinitionFolder.Document, T> converter;
    private final Function<T, String> idOf;
    private final Consumer<String> warner;

    /** 上一次读取的结果；null 表示还没读过。 */
    private volatile Snapshot<T> snapshot;

    public YamlSources(DefinitionFolder folder,
                       Function<DefinitionFolder.Document, T> converter,
                       Function<T, String> idOf) {
        this.folder = folder;
        this.converter = converter;
        this.idOf = idOf;
        this.warner = folder.warnSink();
    }

    /** 重新读盘并返回全部定义（解析失败的条目已被跳过并告警）。 */
    public List<T> all() {
        Snapshot<T> loaded = read();
        this.snapshot = loaded;
        return loaded.items();
    }

    /** 按 id 取一条（用缓存）。 */
    public Optional<T> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return current().items().stream().filter(item -> id.equals(idOf.apply(item))).findFirst();
    }

    /** 该 id 来自哪个文件；不在文件里时为空。 */
    public Optional<String> location(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(current().locations().get(id));
    }

    /** 该 id 是否由 YAML 文件定义（= 只读）。 */
    public boolean isFileDefined(String id) {
        return location(id).isPresent();
    }

    /** 已解析的条目数（用缓存，不重新读盘）。 */
    public int size() {
        return current().items().size();
    }

    /** 目录名（{@code quests} / {@code presets}），用于提示「去哪个目录改」。 */
    public String directoryLabel() {
        return folder.label();
    }

    private Snapshot<T> current() {
        Snapshot<T> currentSnapshot = snapshot;
        return currentSnapshot != null ? currentSnapshot : (snapshot = read());
    }

    private Snapshot<T> read() {
        List<T> items = new ArrayList<>();
        Map<String, String> locations = new LinkedHashMap<>();
        for (DefinitionFolder.Document document : folder.load()) {
            T item;
            try {
                item = converter.apply(document);
            } catch (RuntimeException e) {
                // 单个文件的字段写坏不该拖垮整个目录
                warner.accept(document.location() + " 解析失败，已跳过: " + e.getMessage());
                continue;
            }
            if (item == null) {
                continue;
            }
            String id = idOf.apply(item);
            if (id == null || id.isBlank()) {
                warner.accept(document.location() + " 无法确定 id，已跳过");
                continue;
            }
            String previous = locations.putIfAbsent(id, document.location());
            if (previous != null) {
                warner.accept("定义 " + id + " 在 " + previous + " 与 " + document.location()
                        + " 里重复，已采用 " + previous + "（按文件路径排序取第一个）");
                continue;
            }
            items.add(item);
        }
        return new Snapshot<>(List.copyOf(items), Map.copyOf(locations));
    }

    private record Snapshot<T>(List<T> items, Map<String, String> locations) {
    }
}
