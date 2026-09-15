package com.playerPlugin.playerTaskX.core.storage.yaml;

import com.playerPlugin.playerTaskX.core.storage.DefinitionReadOnlyException;
import com.playerPlugin.playerTaskX.core.storage.DefinitionRepository;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 定义类仓储的通用实现：数据库（可写） + YAML 目录（只读，库优先），任务与预设共用这一份规则。
 * 写「只由文件定义」的 id 会抛 {@link DefinitionReadOnlyException}，而不是在库里悄悄造一份同 id 记录。
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
}
