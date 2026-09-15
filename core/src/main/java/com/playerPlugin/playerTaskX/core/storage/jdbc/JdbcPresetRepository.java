package com.playerPlugin.playerTaskX.core.storage.jdbc;

import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.core.storage.Database;
import com.playerPlugin.playerTaskX.core.storage.codec.JsonCodec;
import com.playerPlugin.playerTaskX.core.storage.PresetRepository;
import com.playerPlugin.playerTaskX.core.storage.RowMapper;
import com.playerPlugin.playerTaskX.core.storage.StorageException;

import java.util.List;
import java.util.Optional;

/**
 * {@link PresetRepository} 的 JDBC 实现，SQLite 与 MySQL 共用（差异由 {@link com.playerPlugin.playerTaskX.core.storage.Dialect} 承担）。
 * 预设与任务定义同库同后端，因此备份与迁移只需要记得一个数据库。
 */
public final class JdbcPresetRepository implements PresetRepository {

    private static final String COLUMNS = "kind,id,type,properties";

    private static final RowMapper<Preset> MAPPER = rs -> new Preset(
            rs.getString("kind"),
            rs.getString("id"),
            rs.getString("type"),
            JsonCodec.readMap(rs.getString("properties")));

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
                preset.kind(), preset.id(), preset.type(),
                JsonCodec.write(preset.properties()));
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

    /** 便于日志展示的后端描述。 */
    @Override
    public String toString() {
        return "JdbcPresetRepository[" + database.dialect().name() + "]";
    }
}
