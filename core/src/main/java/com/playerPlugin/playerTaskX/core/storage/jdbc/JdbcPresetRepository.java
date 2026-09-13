package com.playerPlugin.playerTaskX.core.storage.jdbc;

import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.core.storage.Database;
import com.playerPlugin.playerTaskX.core.storage.JsonCodec;
import com.playerPlugin.playerTaskX.core.storage.PresetRepository;
import com.playerPlugin.playerTaskX.core.storage.RowMapper;
import com.playerPlugin.playerTaskX.core.storage.StorageException;

import java.util.List;
import java.util.Optional;

/**
 * {@link PresetRepository} 的 JDBC 实现，SQLite 与 MySQL 共用（差异由 {@link com.playerPlugin.playerTaskX.core.storage.Dialect} 承担）。
 *
 * <p>为什么预设也要有 SQL 实现：既然存储方式可插拔，用户把 {@code definitions.type}
 * 设成 {@code SQLITE} 或 {@code MYSQL} 时，预设必须跟着走同一个后端——
 * 否则会出现「任务定义在库里、预设在文件里」这种自相矛盾的组合，
 * 备份与迁移都要记得两处。
 *
 * <p>表结构见 {@link Schema}：{@code preset} 一张表，{@code properties} 存 JSON 文本。
 * 类别（objectives / rewards）是一列，不是两张表——两者的字段完全一致，
 * 拆表只会让读取多一次查询。
 */
public final class JdbcPresetRepository implements PresetRepository {

    private static final String COLUMNS = "kind,id,name,type,properties,description";

    private static final RowMapper<Preset> MAPPER = rs -> new Preset(
            rs.getString("kind"),
            rs.getString("id"),
            rs.getString("name"),
            rs.getString("type"),
            JsonCodec.readMap(rs.getString("properties")),
            rs.getString("description"));

    private final Database database;

    public JdbcPresetRepository(Database database) {
        this.database = database;
    }

    @Override
    public List<Preset> findAll() {
        return database.query("SELECT " + COLUMNS + " FROM preset ORDER BY kind, id", MAPPER);
    }

    @Override
    public Optional<Preset> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(database.queryOne(
                "SELECT " + COLUMNS + " FROM preset WHERE id = ?", MAPPER, id));
    }

    @Override
    public void save(Preset preset) {
        if (preset == null || preset.id() == null || preset.id().isBlank()) {
            throw new StorageException("预设必须有 id 才能保存");
        }
        database.execute(
                database.dialect().upsert("preset", "id", COLUMNS),
                preset.kind(), preset.id(), preset.name(), preset.type(),
                JsonCodec.write(preset.properties()), preset.description());
    }

    @Override
    public boolean delete(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        boolean[] removed = new boolean[1];
        database.transaction(() -> {
            removed[0] = database.count("SELECT COUNT(*) FROM preset WHERE id = ?", id) > 0;
            database.execute("DELETE FROM preset WHERE id = ?", id);
        });
        return removed[0];
    }

    @Override
    public long count() {
        return database.count("SELECT COUNT(*) FROM preset");
    }

    /** 供首次接入 SQL 后端时写入内置默认预设。 */
    public void seedIfEmpty(List<Preset> defaults) {
        if (count() > 0 || defaults == null || defaults.isEmpty()) {
            return;
        }
        database.transaction(() -> {
            for (Preset preset : defaults) {
                save(preset);
            }
        });
    }

    /** 便于日志与编辑器展示的后端描述。 */
    @Override
    public String toString() {
        return "JdbcPresetRepository[" + database.dialect().name() + "]";
    }
}
