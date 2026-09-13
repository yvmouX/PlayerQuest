package com.playerPlugin.playerTaskX.core.storage.yaml;

import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.core.storage.DefinitionReadOnlyException;
import com.playerPlugin.playerTaskX.core.storage.QuestRepository;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 任务定义的对外仓储：<b>数据库（可写） + quests/ 目录（只读，库优先）</b>。
 *
 * <p>装配点用这一份替换纯数据库实现，于是「任务从哪来」对上层完全透明：引擎、GUI、
 * 编辑器拿到的都是同一份合并结果；唯一多出来的信息是 {@link #isReadOnly(String)}，
 * 编辑器据此把文件里的定义标成只读。
 *
 * <p>写操作<b>永远落数据库</b>。写到「只由文件定义」的 id 会抛
 * {@link DefinitionReadOnlyException}，而不是在库里悄悄造一份同 id 记录——
 * 那会让文件里那份在下次重载时被忽略，而管理员不知道自己改的到底生效在哪。
 */
public final class MergedQuestRepository implements QuestRepository {

    private final QuestRepository database;
    private final MergedSources<Quest> merged;

    public MergedQuestRepository(QuestRepository database, YamlSources<Quest> files, Consumer<String> warner) {
        this.database = database;
        this.merged = new MergedSources<>(database, files, Quest::id, "任务", warner);
    }

    @Override
    public List<Quest> findAll() {
        return merged.all();
    }

    @Override
    public Optional<Quest> findById(String id) {
        return merged.find(id);
    }

    @Override
    public boolean isReadOnly(String id) {
        return merged.readOnly(id);
    }

    @Override
    public void save(Quest quest) {
        if (quest != null && merged.readOnly(quest.id())) {
            throw new DefinitionReadOnlyException(merged.readOnlyHint(quest.id()));
        }
        database.save(quest);
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
        // 口径是「插件实际能用多少」，不是「库里有几条」：列表、统计与示例预设的播种判断
        // 关心的都是这个数
        return merged.count();
    }

    @Override
    public boolean databaseEmpty() {
        return merged.databaseEmpty();
    }
}
