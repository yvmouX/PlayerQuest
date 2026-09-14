package com.playerPlugin.playerTaskX.core.storage.yaml;

import com.playerPlugin.playerTaskX.core.storage.DefinitionRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 「数据库优先 + YAML 文件补充」的合并读取规则——任务与预设共用这一份实现。
 *
 * <h2>为什么库优先</h2>
 * 数据库是<b>可写</b>的那一份：游戏内命令与网页编辑器都改它，玩家进度也挂在它的 id 上。
 * 文件只是「随插件一起发布 / 进版本控制」的只读来源。同一 id 两边都有时若让文件赢，
 * 管理员的每次编辑都会在重启后被打回，且毫无提示。因此：<b>库里有就以库为准</b>，
 * 文件里那份被忽略并记一条告警。
 *
 * <h2>只读是「没有库记录」的那一侧</h2>
 * 同 id 两边都有时，可写的是库里的那条（文件那份已被忽略），因此 {@link #readOnly} 要先问库。
 */
public final class MergedSources<T> {

    private final DefinitionRepository<T> database;
    private final YamlSources<T> files;
    private final Function<T, String> idOf;
    /** 「任务」/「预设」，只用于日志与提示措辞。 */
    private final String label;
    private final Consumer<String> warner;

    public MergedSources(DefinitionRepository<T> database, YamlSources<T> files,
                         Function<T, String> idOf, String label, Consumer<String> warner) {
        this.database = database;
        this.files = files;
        this.idOf = idOf;
        this.label = label;
        this.warner = warner == null ? message -> { } : warner;
    }

    /** 已经告警过的冲突 id：启动路径上 findAll 会被调用多次（播种判断 + 载入），不重复刷屏。 */
    private final Set<String> warnedConflicts = new HashSet<>();

    /** 合并全部：库在前，文件里不与库冲突的追加在后。 */
    public List<T> all() {
        List<T> fromDatabase = database.findAll();
        Set<String> ids = new HashSet<>();
        for (T item : fromDatabase) {
            ids.add(idOf.apply(item));
        }
        List<T> merged = new ArrayList<>(fromDatabase);
        for (T item : files.all()) {
            String id = idOf.apply(item);
            if (ids.contains(id)) {
                if (warnedConflicts.add(id)) {
                    String location = files.location(id).orElse("YAML 文件");
                    warner.accept(label + " " + id + " 同时定义在数据库与 " + location
                            + "，已忽略文件里的那份（库优先）");
                }
                continue;
            }
            merged.add(item);
        }
        return merged;
    }

    /** 按 id 取：库优先，库里没有才看文件。 */
    public Optional<T> find(String id) {
        Optional<T> fromDatabase = database.findById(id);
        return fromDatabase.isPresent() ? fromDatabase : files.find(id);
    }

    /** 该 id 是否只能改文件（库里没有、文件里有）。 */
    public boolean readOnly(String id) {
        return !database.exists(id) && files.isFileDefined(id);
    }

    /** 只读时的说明：带上文件名，管理员才知道该去改哪个文件。 */
    public String readOnlyHint(String id) {
        String location = files.location(id)
                .orElse(files.directoryLabel() + "/ 下的 YAML 文件");
        return label + " " + id + " 定义在 " + location + " 里，游戏内与网页编辑器只修改数据库中的定义；"
                + "请改文件，或先用编辑器的「导入 YAML」把它搬进数据库";
    }

    /** 合并后的总数（看的是「插件实际能用多少」，因此含文件里的那些）。 */
    public long count() {
        return all().size();
    }
}
