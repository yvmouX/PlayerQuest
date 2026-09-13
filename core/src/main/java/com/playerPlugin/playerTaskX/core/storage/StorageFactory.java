package com.playerPlugin.playerTaskX.core.storage;

import com.playerPlugin.playerTaskX.core.config.PluginConfig;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * 存储后端的统一选择入口——也就是「兼容层」：
 * 不管用户选 JSON、SQLite 还是 MySQL，上层拿到的都是同一组接口。
 *
 * <h2>为什么分两次选择</h2>
 * 任务定义/预设（内容类）与玩家数据（状态类）的最优后端并不相同：
 * 前者适合文件（可手改、可 diff、可随配置分发），后者适合数据库（高频写、事务、跨服共享）。
 * 因此两者各自独立选后端，而不是一刀切。默认组合是
 * {@code definitions.type=JSON} + {@code storage.type=SQLITE}。
 *
 * <h2>后端与接口的对应关系</h2>
 * <pre>
 * definitions.type = JSON    → QuestFileRepository  + PresetFileRepository
 * definitions.type = SQLITE  → JdbcQuestRepository  + JdbcPresetRepository（本地库）
 * definitions.type = MYSQL   → 同上（共享库）
 * storage.type     = SQLITE / MYSQL → JdbcPlayerQuestRepository
 * </pre>
 * SQLite 与 MySQL 共用同一套 JDBC 实现，差异全部由 {@link Dialect} 承担——
 * 所以「支持三种存储」实际只需两份实现（文件 + JDBC），不必写三套。
 *
 * <h2>未知类型回退而非失败</h2>
 * 配置里写错一个词（{@code MYSQL5}）时按默认值继续，并记一条告警：
 * 让插件带着可用的存储起来，比直接启动失败更符合使用者预期。
 */
public final class StorageFactory {

    /** 存储类型标识。 */
    public static final String TYPE_JSON = "JSON";
    public static final String TYPE_SQLITE = "SQLITE";
    public static final String TYPE_MYSQL = "MYSQL";

    private final QuestRepository quests;
    private final PresetRepository presets;
    private final String description;

    private StorageFactory(QuestRepository quests, PresetRepository presets, String description) {
        this.quests = quests;
        this.presets = presets;
        this.description = description;
    }

    /** 任务定义仓储（JSON 文件或 JDBC，取决于配置）。 */
    public QuestRepository quests() {
        return quests;
    }

    /** 预设仓储（与任务定义同后端）。 */
    public PresetRepository presets() {
        return presets;
    }

    /** 人类可读描述，如 {@code JSON: plugins/playerTaskX/quests}。 */
    public String description() {
        return description;
    }

    /**
     * 按配置装配定义侧仓储。
     *
     * @param config     插件配置，为 null 时按 JSON 处理
     * @param dataFolder 插件数据目录，文件后端相对它解析
     * @param database   已打开的数据库门面；选 SQL 后端时使用，可为 null（仅 JSON 时）
     * @param warn       告警输出
     * @param info       信息输出
     */
    public static StorageFactory create(PluginConfig config, File dataFolder, Database database,
                                        Consumer<String> warn, Consumer<String> info) {
        String type = config == null ? null : config.getDefinitionsType();
        String normalized = type == null ? TYPE_JSON : type.trim().toUpperCase(java.util.Locale.ROOT);

        if (TYPE_SQLITE.equals(normalized) || TYPE_MYSQL.equals(normalized)) {
            if (database == null) {
                warn.accept("definitions.type=" + normalized + " 但数据库不可用，已回退 JSON 文件");
            } else {
                info.accept("任务定义与预设使用 " + normalized + " 后端");
                return new StorageFactory(
                        new com.playerPlugin.playerTaskX.core.storage.jdbc.JdbcQuestRepository(database),
                        new com.playerPlugin.playerTaskX.core.storage.jdbc.JdbcPresetRepository(database),
                        normalized + " (与玩家数据同一数据库)");
            }
        } else if (!TYPE_JSON.equals(normalized) && type != null && !type.isBlank()) {
            warn.accept("未知的 definitions.type '" + type + "'，已回退 JSON 文件");
        }

        String folderName = config == null || config.getDefinitionsFolder() == null
                || config.getDefinitionsFolder().isBlank()
                ? "quests"
                : config.getDefinitionsFolder().trim();
        File base = dataFolder == null ? new File(".") : dataFolder;
        Path folder = new File(base, folderName).toPath();

        info.accept("任务定义与预设使用 JSON 文件后端，目录: " + folder.toAbsolutePath());
        return new StorageFactory(
                new QuestFileRepository(new JsonFileStore(folder), warn),
                new PresetFileRepository(base, warn),
                "JSON: " + folderName + "/");
    }
}
