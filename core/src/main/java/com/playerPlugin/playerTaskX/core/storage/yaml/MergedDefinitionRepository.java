package com.playerPlugin.playerTaskX.core.storage.yaml;

import com.playerPlugin.playerTaskX.core.storage.DefinitionReadOnlyException;
import com.playerPlugin.playerTaskX.core.storage.DefinitionRepository;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 定义类仓储的通用实现：<b>数据库（可写） + YAML 目录（只读，库优先）</b>。
 *
 * <h2>为什么任务与预设共用这一份</h2>
 * 两者的合并规则、只读判定、写入拦截逐字相同，差别只有「元素类型」与日志里称呼它什么。
 * 各写一份的代价不是多敲 60 行，而是「库优先」这类规则改一处漏一处——
 * 漏掉的那一侧会出现「库里有记录但界面显示文件里那份」。
 *
 * <h2>写操作永远落数据库</h2>
 * 写到「只由文件定义」的 id 会抛 {@link DefinitionReadOnlyException}，而不是在库里悄悄造一份
 * 同 id 记录——那会让文件里那份在下次重载时被忽略，而管理员不知道自己改的到底生效在哪。
 *
 * @param <T> 定义元素类型（{@code Quest} / {@code Preset}）
 */
public class MergedDefinitionRepository<T> implements DefinitionRepository<T> {

    private final DefinitionRepository<T> database;
    private final MergedSources<T> merged;
    /** 元素 id：{@code save} 靠它问「这条是不是只读的」。 */
    private final Function<T, String> idOf;

    public MergedDefinitionRepository(DefinitionRepository<T> database, YamlSources<T> files,
                                     Function<T, String> idOf, String label, Consumer<String> warner) {
        this.database = database;
        this.idOf = idOf;
        this.merged = new MergedSources<>(database, files, idOf, label, warner);
    }

    @Override
    public List<T> findAll() {
        return merged.all();
    }

    @Override
    public Optional<T> findById(String id) {
        return merged.find(id);
    }

    @Override
    public boolean isReadOnly(String id) {
        return merged.readOnly(id);
    }

    @Override
    public void save(T element) {
        if (element != null && merged.readOnly(idOf.apply(element))) {
            throw new DefinitionReadOnlyException(merged.readOnlyHint(idOf.apply(element)));
        }
        database.save(element);
    }

    @Override
    public boolean delete(String id) {
        if (merged.readOnly(id)) {
            throw new DefinitionReadOnlyException(merged.readOnlyHint(id));
        }
        return database.delete(id);
    }

    @Override
    public long count() {
        // 口径是「插件实际能用多少」，不是「库里有几条」：列表与统计关心的都是这个数
        return merged.count();
    }
}
