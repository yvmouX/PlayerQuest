package com.playerPlugin.playerTaskX.core.storage;

import com.playerPlugin.playerTaskX.core.config.PluginConfig;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * 一次性迁移：把旧版本存在数据库里的任务定义与预设导出到文件后端。
 *
 * <h2>触发条件（三条同时成立才动手）</h2>
 * <ol>
 *   <li>定义侧配置为文件后端（{@code definitions.type=JSON}）；</li>
 *   <li>文件目录里<b>一个任务都没有</b>；</li>
 *   <li>数据库里确实还有任务定义。</li>
 * </ol>
 * 三条都成立说明这是「从旧版本升级上来的第一次启动」，此时导出即可。
 *
 * <h2>为什么旧表不删</h2>
 * 迁移完成后数据库里的 {@code quest} / {@code quest_objective} / {@code quest_reward}
 * 三张表<b>原样保留、不再读取</b>。理由：迁移是单向且不可逆的，万一导出过程有偏差，
 * 管理员还能用 {@code sqlite3} 手查旧数据。自动删表属于「危险的不可逆操作」，
 * 不该由插件替用户决定。
 *
 * <h2>失败时不让插件挂掉</h2>
 * 导出失败只记错误并继续启动：文件后端此时是空的，管理员会看到「0 个任务」——
 * 这比插件无法启动要好，而且日志里已经写明原因与旧数据仍在何处。
 */
public final class DefinitionMigrator {

    private DefinitionMigrator() {
    }

    /**
     * 必要时执行迁移。
     *
     * @param config     插件配置
     * @param dataFolder 插件数据目录
     * @param handle     已打开的数据库句柄（旧数据来源）
     * @param info       信息输出
     * @param warn       告警输出
     * @return 是否真的执行了迁移
     */
    public static boolean migrateIfNeeded(PluginConfig config, File dataFolder,
                                          DatabaseFactory.Handle handle,
                                          Consumer<String> info, Consumer<String> warn) {
        if (config == null || handle == null || dataFolder == null) {
            return false;
        }
        String type = config.getDefinitionsType();
        if (type != null && !StorageFactory.TYPE_JSON.equalsIgnoreCase(type.trim())) {
            // 定义侧仍用数据库：不需要迁移
            return false;
        }

        String folderName = config.getDefinitionsFolder() == null || config.getDefinitionsFolder().isBlank()
                ? "quests"
                : config.getDefinitionsFolder().trim();
        Path folder = new File(dataFolder, folderName).toPath();

        QuestFileRepository target = QuestFileRepository.of(folder);
        if (target.count() > 0) {
            // 目录里已有任务：用户已经在用文件后端，不再从库里倒数据（否则会把删掉的任务倒回来）
            return false;
        }

        List<com.playerPlugin.playerTaskX.api.model.Quest> legacy;
        try {
            // 直接用 JDBC 读旧表；读不到（表不存在）说明本来就是新装环境
            legacy = new com.playerPlugin.playerTaskX.core.storage.jdbc.JdbcQuestRepository(
                    handle.database()).findAll();
        } catch (RuntimeException e) {
            warn.accept("检查旧任务数据失败（若为新装环境可忽略）: " + e.getMessage());
            return false;
        }
        if (legacy.isEmpty()) {
            return false;
        }

        info.accept("检测到数据库中有 " + legacy.size() + " 个任务定义，正在导出到 "
                + folder.toAbsolutePath());
        int exported = 0;
        for (com.playerPlugin.playerTaskX.api.model.Quest quest : legacy) {
            try {
                target.save(quest);
                exported++;
            } catch (RuntimeException e) {
                warn.accept("导出任务 " + quest.id() + " 失败: " + e.getMessage());
            }
        }
        info.accept("已导出 " + exported + "/" + legacy.size() + " 个任务定义到文件；"
                + "数据库中的旧表保留未删，确认无误后可自行清理");

        migratePresets(config, dataFolder, handle, info, warn);
        return true;
    }

    /** 预设同理：旧表有数据且文件不存在时导出。 */
    private static void migratePresets(PluginConfig config, File dataFolder,
                                       DatabaseFactory.Handle handle,
                                       Consumer<String> info, Consumer<String> warn) {
        File presetsFile = new File(dataFolder, "presets.json");
        if (presetsFile.isFile()) {
            return;
        }
        try {
            var jdbc = new com.playerPlugin.playerTaskX.core.storage.jdbc.JdbcPresetRepository(
                    handle.database());
            List<com.playerPlugin.playerTaskX.api.model.Preset> legacy = jdbc.findAll();
            if (legacy.isEmpty()) {
                return;
            }
            PresetFileRepository target = PresetFileRepository.of(dataFolder.toPath());
            int exported = 0;
            for (com.playerPlugin.playerTaskX.api.model.Preset preset : legacy) {
                try {
                    target.save(preset);
                    exported++;
                } catch (RuntimeException e) {
                    warn.accept("导出预设 " + preset.id() + " 失败: " + e.getMessage());
                }
            }
            info.accept("已导出 " + exported + " 个预设到 " + presetsFile.getName());
        } catch (RuntimeException e) {
            warn.accept("检查旧预设数据失败（若为新装环境可忽略）: " + e.getMessage());
        }
    }
}
