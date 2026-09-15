package com.playerPlugin.playerTaskX.core.storage.yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * YAML 定义源的读取与缓存：扫目录 → 解析 → 转模型 → 记 id 位置，任务与预设各持一个实例。
 * {@link #all()} 每次重新读盘（启动与 reload 正好需要最新内容），{@link #find(String)} / {@link #location(String)} 用缓存；文件之间重复的 id 按路径排序先到先得并告警。
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
