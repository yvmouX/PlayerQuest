package com.playerPlugin.playerTaskX.core.web;

import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.objective.ObjectiveType;
import com.playerPlugin.playerTaskX.api.reward.RewardType;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;
import com.playerPlugin.playerTaskX.api.schema.ConfigurableType;
import com.playerPlugin.playerTaskX.core.storage.DefinitionReadOnlyException;
import com.playerPlugin.playerTaskX.core.storage.JsonCodec;
import com.playerPlugin.playerTaskX.core.storage.QuestJson;
import com.playerPlugin.playerTaskX.core.storage.StorageException;
import com.playerPlugin.playerTaskX.core.storage.yaml.YamlDefinitions;
import cn.yvmou.ylib.text.TextRenderer;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.bukkit.Bukkit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 网页编辑器的业务接口（{@code /api/*}）。
 *
 * <h2>路由顺序有坑</h2>
 * 批量接口（{@code /api/quests/export}）必须注册在 {@code /api/quests/{id}} 之前：
 * 路由按注册顺序匹配，否则 {@code export} 会被当成「id 为 export 的任务」而 404。
 *
 * <h2>错误约定</h2>
 * 业务异常统一转成 4xx 而不是让前端只看到 500：{@link IllegalArgumentException} → 400、
 * {@link IllegalStateException} → 409、{@link StorageException} → 500。
 *
 * <h2>写接口的请求体</h2>
 * POST/PUT 一律用 {@link #withBody} 取请求体：解析失败时它在返回前就写好 400，
 * 处理器体因此无需再判 null——漏判的代价是「坏请求体继续往下跑」，而这类漏判
 * 只在手写校验时才看得出，交给辅助方法后就不可能漏。
 *
 * <h2>它只认识 {@link EditorServices}，不认识插件单例</h2>
 * 原先本类持有 {@code PlayerTaskX}，于是每条路由都要求「插件已启用 + 服务端在跑」，
 * 单测无从下手——这一层过去六轮改动全部靠手工起服验证。改为依赖窄接口后，
 * 测试可以真实启动 Javalin 打 HTTP（见 {@code EditorApiTest}）。
 * <p>
 * 唯一的例外是 {@code /api/players}：玩家名与在线状态只能从 Bukkit 运行期取，
 * 没有可注入的余地，因此那两条路由仍是「单测外」的（{@code /api/catalog} 同理，
 * 它要枚举服务端的 {@code Material} / {@code EntityType}）。
 */
final class EditorApi {

    /** 导出/导入用的内容类型：YAML 没有官方 MIME，用最常见的 application/x-yaml。 */
    private static final String YAML_CONTENT_TYPE = "application/x-yaml; charset=utf-8";

    private final EditorServices services;
    private final MaterialCatalog catalog;

    EditorApi(EditorServices services, MaterialCatalog catalog) {
        this.services = services;
        this.catalog = catalog;
    }

    void register(Javalin app) {
        questRoutes(app);
        playerRoutes(app);
        schemaRoutes(app);
        presetRoutes(app);
        miscRoutes(app);

        app.exception(IllegalArgumentException.class, (e, ctx) ->
                badRequest(ctx, e.getMessage() == null ? "参数不合法" : e.getMessage()));
        app.exception(IllegalStateException.class, (e, ctx) ->
                ctx.status(409).result(errorJson(e.getMessage() == null ? "状态冲突" : e.getMessage())));
        app.exception(StorageException.class, (e, ctx) ->
                ctx.status(500).result(errorJson("存储错误: " + e.getMessage())));
    }

    // ------------------------------------------------------------------
    // 任务 CRUD
    // ------------------------------------------------------------------

    private void questRoutes(Javalin app) {
        app.get("/api/quests", ctx -> {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Quest quest : services.quests().all()) {
                list.add(questJson(quest));
            }
            ctx.result(json(list));
        });

        // 导出：一个任务一个 yml（导出的形状与 quests/<id>.yml 一致，可以直接放进去用）；
        // 选中多个或导出全部时打包成 zip —— 一个文件里塞十几条任务，导入时既不好 diff 也不好挑
        app.get("/api/quests/export", ctx -> {
            List<Quest> picked = pickQuests(ctx);
            if (picked == null) {
                return;
            }
            Map<String, String> files = new LinkedHashMap<>();
            for (Quest quest : picked) {
                files.put(quest.id(), YamlDefinitions.writeQuest(quest));
            }
            writeDownload(ctx, picked.size() == 1 ? safeFileName(picked.get(0).id()) : "playerTaskX-quests", files);
        });

        app.post("/api/quests/import", ctx -> {
            boolean replace = ctx.queryParam("replace") != null
                    && Boolean.parseBoolean(ctx.queryParam("replace"));
            // zip 与单文件走同一个入口：浏览器给 zip 的 MIME 五花八门（application/zip、
            // x-zip-compressed、甚至 octet-stream），因此认魔数而不是 Content-Type
            List<ImportEntry> entries = readImportEntries(ctx);
            if (entries == null) {
                return;
            }
            if (entries.isEmpty()) {
                badRequest(ctx, "压缩包里没有 .yml / .yaml 文件");
                return;
            }
            if (replace) {
                clearDatabase(services.quests().all(), Quest::id, services.questAdmin()::delete);
            }
            int imported = 0;
            List<String> skipped = new ArrayList<>();
            for (ImportEntry entry : entries) {
                Quest quest;
                try {
                    quest = YamlDefinitions.readQuest(entry.text());
                } catch (RuntimeException e) {
                    if (entry.file() == null) {
                        // 单文件导入：直接告诉用户哪里错了，而不是回一个「跳过了 1 条」的 200
                        badRequest(ctx, "解析失败: " + e.getMessage());
                        return;
                    }
                    // 压缩包里某一个文件写坏了不该让整包导入失败，逐条报告
                    skipped.add(describe(entry.file(), e.getMessage()));
                    continue;
                }
                if (quest.id() == null || quest.id().isBlank()) {
                    skipped.add(describe(entry.file(), "缺少 id"));
                    continue;
                }
                try {
                    services.questAdmin().save(quest);
                    imported++;
                } catch (DefinitionReadOnlyException e) {
                    // 只读定义（YAML 文件里的）不能被导入覆盖，逐条报告原因
                    skipped.add(quest.id() + "（" + e.getMessage() + "）");
                }
            }
            ctx.result(json(Map.of("ok", true, "imported", imported, "skipped", skipped,
                    "total", services.quests().all().size())));
        });

        app.get("/api/quests/{id}", ctx -> {
            Quest quest = services.quests().find(ctx.pathParam("id")).orElse(null);
            if (quest == null) {
                notFound(ctx, "任务不存在: " + ctx.pathParam("id"));
                return;
            }
            ctx.result(json(questJson(quest)));
        });

        app.post("/api/quests", ctx -> withBody(ctx, body -> {
            Quest quest = QuestJson.fromJson(body);
            if (quest.id() == null || quest.id().isBlank()) {
                badRequest(ctx, "任务 id 不能为空");
                return;
            }
            services.questAdmin().save(quest);
            ctx.result(json(Map.of("ok", true, "id", quest.id(),
                    "problems", services.questAdmin().validate(quest))));
        }));

        app.delete("/api/quests/{id}", ctx -> {
            String id = ctx.pathParam("id");
            ctx.result(json(Map.of("ok", services.questAdmin().delete(id), "id", id)));
        });
    }

    /**
     * 任务 → 编辑器 JSON。
     * <p>
     * 除字段与校验问题外还带上 {@code source}：{@code file} 表示这条定义来自
     * {@code quests/} 下的 YAML（只读，编辑器必须禁用保存与删除）。
     */
    private Map<String, Object> questJson(Quest quest) {
        Map<String, Object> json = QuestJson.toJson(quest);
        json.put("problems", services.questAdmin().validate(quest));
        json.put("source", services.questDefinitions().isReadOnly(quest.id()) ? "file" : "database");
        return json;
    }

    // ------------------------------------------------------------------
    // 玩家进度：管理员排查「某玩家为什么没进度」
    // ------------------------------------------------------------------

    private void playerRoutes(Javalin app) {
        app.get("/api/players", ctx -> {
            // 只列出有任务记录的玩家，避免遍历全服离线玩家
            List<Map<String, Object>> players = new ArrayList<>();
            for (UUID playerId : services.playerQuestRepository().distinctPlayerIds()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("uuid", playerId.toString());
                entry.put("name", playerName(playerId));
                entry.put("online", Bukkit.getPlayer(playerId) != null);
                entry.put("quests", services.playerQuestRepository().findByPlayer(playerId).size());
                players.add(entry);
            }
            ctx.result(json(players));
        });

        app.get("/api/players/{uuid}", ctx -> {
            UUID playerId;
            try {
                playerId = UUID.fromString(ctx.pathParam("uuid"));
            } catch (IllegalArgumentException e) {
                badRequest(ctx, "玩家 UUID 格式不正确");
                return;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("uuid", playerId.toString());
            result.put("name", playerName(playerId));

            List<Map<String, Object>> records = new ArrayList<>();
            for (var record : services.playerQuestRepository().findByPlayer(playerId)) {
                Quest quest = services.quests().find(record.questId()).orElse(null);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("questId", record.questId());
                entry.put("questName", quest == null ? "" : TextRenderer.strip(quest.name()));
                entry.put("type", record.type().name());
                entry.put("status", record.status().name());
                entry.put("assignedAt", record.assignedAt());
                entry.put("expiresAt", record.expiresAt());
                entry.put("percent", quest == null ? 0 : Math.round(record.completionRatio(quest) * 100));
                entry.put("objectives", objectiveProgress(quest, record));
                records.add(entry);
            }
            result.put("progress", records);
            ctx.result(json(result));
        });
    }

    /** 逐目标给出「当前/需求」，管理员一眼能看出卡在哪一步。 */
    private static List<Map<String, Object>> objectiveProgress(Quest quest, PlayerQuest record) {
        List<Map<String, Object>> objectives = new ArrayList<>();
        if (quest == null) {
            return objectives;
        }
        for (int i = 0; i < quest.objectives().size(); i++) {
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("index", i);
            slot.put("type", quest.objectives().get(i).type());
            slot.put("current", record.progress(i));
            slot.put("required", quest.objectives().get(i).amount());
            objectives.add(slot);
        }
        return objectives;
    }

    private static String playerName(UUID playerId) {
        String name = Bukkit.getOfflinePlayer(playerId).getName();
        return name == null ? "" : name;
    }

    // ------------------------------------------------------------------
    // schema：前端据此动态生成表单
    // ------------------------------------------------------------------

    private void schemaRoutes(Javalin app) {
        app.get("/api/schema", ctx -> {
            Map<String, Object> schema = new LinkedHashMap<>();
            // 目标与奖励都带 available/unavailableReason：缺软依赖的类型（Vault、CustomFishing…）
            // 在编辑器里要能一眼看出「选了也不会涨进度」，而不是让管理员去猜
            schema.put("objectives", typeSchemas(services.objectiveTypes().all(), EditorApi::objectiveExtra));
            schema.put("rewards", typeSchemas(services.rewardTypes().all(), EditorApi::rewardExtra));
            ctx.result(json(schema));
        });

        // 素材目录：内容来自服务端自己的 Material / EntityType 枚举，天然只含当前版本支持的项
        app.get("/api/catalog", ctx -> ctx.result(json(catalog.build())));
    }

    /** 目标类型多带两个字段（与奖励同一套字段名，前端不必分两套逻辑）。 */
    private static Map<String, Object> objectiveExtra(ConfigurableType type) {
        ObjectiveType objective = (ObjectiveType) type;
        return Map.of("available", objective.available(),
                "unavailableReason", objective.unavailableReason());
    }

    /** 奖励类型多带两个字段，编辑器据此标红 / 提示管理员。 */
    private static Map<String, Object> rewardExtra(ConfigurableType type) {
        RewardType reward = (RewardType) type;
        return Map.of("available", reward.available(), "unavailableReason", reward.unavailableReason());
    }

    /** 把类型集合序列化为「类型 id → 字段列表」；`extra` 补充类型自己的附加信息。 */
    private static Map<String, Object> typeSchemas(Collection<? extends ConfigurableType> types,
                                                   Function<ConfigurableType, Map<String, Object>> extra) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (ConfigurableType type : types) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", type.id());
            entry.put("displayName", type.displayName());
            entry.put("fields", type.schema().stream().map(EditorApi::fieldToJson).toList());
            entry.putAll(extra.apply(type));
            result.put(type.id(), entry);
        }
        return result;
    }

    private static Map<String, Object> fieldToJson(ConfigField field) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("key", field.key());
        json.put("label", field.label());
        json.put("type", field.type().name());
        json.put("required", field.required());
        json.put("defaultValue", field.defaultValue());
        json.put("options", field.options());
        json.put("hint", field.hint());
        return json;
    }

    // ------------------------------------------------------------------
    // 目标 / 奖励预设（存储后端由 definitions.type 决定，接口形状不变）
    // ------------------------------------------------------------------

    private void presetRoutes(Javalin app) {
        app.get("/api/presets", ctx -> ctx.result(json(presetGroups())));

        // 导出：一条预设一个 yml；选中多条或导出全部时打包成 zip（与任务导出同一套规则）
        app.get("/api/presets/export", ctx -> {
            List<Preset> picked = pickPresets(ctx);
            if (picked == null) {
                return;
            }
            Map<String, String> files = new LinkedHashMap<>();
            for (Preset preset : picked) {
                files.put(preset.id(), YamlDefinitions.writePreset(preset));
            }
            writeDownload(ctx, picked.size() == 1 ? safeFileName(picked.get(0).id()) : "playerTaskX-presets", files);
        });

        app.post("/api/presets/import", ctx -> {
            // kind 只是「文件里没写 kind 时」的兜底，导出的文件本身带着 kind
            String defaultKind = ctx.queryParam("kind") == null ? Preset.OBJECTIVES : ctx.queryParam("kind");
            boolean replace = ctx.queryParam("replace") != null
                    && Boolean.parseBoolean(ctx.queryParam("replace"));
            List<ImportEntry> entries = readImportEntries(ctx);
            if (entries == null) {
                return;
            }
            if (entries.isEmpty()) {
                badRequest(ctx, "压缩包里没有 .yml / .yaml 文件");
                return;
            }
            if (replace) {
                clearDatabase(services.presets().findAll(), Preset::id, services.presets()::delete);
            }
            int imported = 0;
            List<String> skipped = new ArrayList<>();
            for (ImportEntry entry : entries) {
                Preset preset;
                try {
                    preset = YamlDefinitions.readPreset(entry.text(), defaultKind);
                } catch (RuntimeException e) {
                    if (entry.file() == null) {
                        badRequest(ctx, "解析失败: " + e.getMessage());
                        return;
                    }
                    skipped.add(describe(entry.file(), e.getMessage()));
                    continue;
                }
                try {
                    services.presets().save(preset);
                    imported++;
                } catch (DefinitionReadOnlyException e) {
                    skipped.add(preset.id() + "（" + e.getMessage() + "）");
                }
            }
            ctx.result(json(Map.of("ok", true, "imported", imported, "skipped", skipped,
                    "total", services.presets().findAll().size())));
        });

        app.post("/api/presets/{kind}", ctx -> withBody(ctx, body -> {
            Preset preset = PresetJson.fromJson(ctx.pathParam("kind"), body);
            if (preset == null) {
                badRequest(ctx, "预设缺少 type");
                return;
            }
            services.presets().save(preset);
            Map<String, Object> saved = PresetJson.toJson(preset);
            saved.put("source", presetSource(preset.id()));
            ctx.result(json(Map.of("ok", true, "preset", saved)));
        }));

        app.delete("/api/presets/{kind}/{id}", ctx -> {
            String id = ctx.pathParam("id");
            ctx.result(json(Map.of("ok", services.presets().delete(id), "id", id)));
        });
    }

    /** 预设分组，每项带上 {@code source}（{@code file} = 来自 presets/ 的只读 YAML）。 */
    private Map<String, Object> presetGroups() {
        Map<String, Object> grouped = PresetJson.toGrouped(services.presets().findAll());
        for (Object value : grouped.values()) {
            if (!(value instanceof List<?> items)) {
                continue;
            }
            for (Object item : items) {
                if (item instanceof Map<?, ?> entry) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) entry;
                    map.put("source", presetSource(String.valueOf(map.get("id"))));
                }
            }
        }
        return grouped;
    }

    private String presetSource(String id) {
        return services.presets().isReadOnly(id) ? "file" : "database";
    }

    // ------------------------------------------------------------------
    // 统计与重载
    // ------------------------------------------------------------------

    private void miscRoutes(Javalin app) {
        app.get("/api/stats", ctx -> {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("quests", services.quests().all().size());
            stats.put("dailyQuests", services.quests().daily().size());
            stats.put("objectives", services.objectiveTypes().all().size());
            stats.put("rewards", services.rewardTypes().all().size());
            stats.put("players", services.playerQuestRepository().countPlayers());
            stats.put("storage", services.describeStorage());
            stats.put("categories", services.quests().categories());
            ctx.result(json(stats));
        });

        app.post("/api/reload", ctx -> {
            services.questAdmin().reload();
            ctx.result(json(Map.of("ok", true, "quests", services.quests().all().size())));
        });
    }

    // ------------------------------------------------------------------
    // 导出 / 导入的公共部分
    // ------------------------------------------------------------------

    /**
     * {@code ids=a,b,c} 指定要导出的任务；缺省表示全部。
     *
     * @return 要导出的任务；某个 id 不存在时已经写了 404 并返回 {@code null}
     */
    private List<Quest> pickQuests(Context ctx) {
        String raw = ctx.queryParam("ids");
        if (raw == null || raw.isBlank()) {
            List<Quest> all = new ArrayList<>(services.quests().all());
            all.sort(Comparator.comparing(Quest::id));
            return all;
        }
        List<Quest> picked = new ArrayList<>();
        for (String raw_id : raw.split(",")) {
            String id = raw_id.trim();
            if (id.isEmpty()) {
                continue;
            }
            Quest quest = services.quests().find(id).orElse(null);
            if (quest == null) {
                notFound(ctx, "任务不存在: " + id);
                return null;
            }
            picked.add(quest);
        }
        return picked;
    }

    /** 预设版的 {@link #pickQuests(Context)}。 */
    private List<Preset> pickPresets(Context ctx) {
        String raw = ctx.queryParam("ids");
        if (raw == null || raw.isBlank()) {
            List<Preset> all = new ArrayList<>(services.presets().findAll());
            all.sort(Comparator.comparing(Preset::id));
            return all;
        }
        List<Preset> picked = new ArrayList<>();
        for (String raw_id : raw.split(",")) {
            String id = raw_id.trim();
            if (id.isEmpty()) {
                continue;
            }
            Preset preset = services.presets().findById(id).orElse(null);
            if (preset == null) {
                notFound(ctx, "预设不存在: " + id);
                return null;
            }
            picked.add(preset);
        }
        return picked;
    }

    /**
     * 写下载响应：一条定义给 {@code <名字>.yml}，多条打包成 {@code <名字>.zip}。
     * <p>
     * 「一个文件一条定义」是刻意的：导出的文件可以直接丢进 {@code quests/} 用，
     * 也能一眼看出改了哪一条；把十几条塞进一个文件时，diff 与挑选都变得很难受。
     *
     * @param fileName 单条时用它的 id，多条时用一个固定前缀（后缀由这里补）
     */
    private static void writeDownload(Context ctx, String fileName, Map<String, String> files) {
        if (files.size() == 1) {
            ctx.contentType(YAML_CONTENT_TYPE)
                    .header("Content-Disposition", "attachment; filename=\"" + fileName + ".yml\"")
                    .result(files.values().iterator().next());
            return;
        }
        ctx.contentType("application/zip")
                .header("Content-Disposition", "attachment; filename=\"" + fileName + ".zip\"")
                .result(zip(files));
    }

    /** 一条待导入的定义；{@code file} 为 {@code null} 表示整个请求体就是这一条。 */
    private record ImportEntry(String file, String text) {
    }

    /**
     * 读导入请求体：zip 里一个文件一条定义，否则整个请求体就是一条定义。
     * <p>
     * 认 zip 的<b>魔数</b>而不是 {@code Content-Type}：浏览器给 zip 的类型五花八门
     * （{@code application/zip}、{@code x-zip-compressed}、甚至 {@code octet-stream}）。
     *
     * @return 待导入的条目；请求体为空或压缩包读不出来时已经写了 400 并返回 {@code null}
     */
    private static List<ImportEntry> readImportEntries(Context ctx) {
        byte[] body = ctx.bodyAsBytes();
        if (body == null || body.length == 0) {
            badRequest(ctx, "请求体为空");
            return null;
        }
        if (isZip(body)) {
            try {
                return unzip(body);
            } catch (IOException e) {
                badRequest(ctx, "读取压缩包失败: " + e.getMessage());
                return null;
            }
        }
        return List.of(new ImportEntry(null, new String(body, StandardCharsets.UTF_8)));
    }

    private static boolean isZip(byte[] body) {
        return body.length > 3 && body[0] == 'P' && body[1] == 'K';
    }

    /** 只取压缩包里的 .yml / .yaml 条目；子目录结构忽略（导入只看内容）。 */
    private static List<ImportEntry> unzip(byte[] body) throws IOException {
        List<ImportEntry> entries = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(body), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || !isYamlName(name)) {
                    continue;
                }
                entries.add(new ImportEntry(name, new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            }
        }
        return entries;
    }

    private static boolean isYamlName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".yml") || lower.endsWith(".yaml");
    }

    private static byte[] zip(Map<String, String> files) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                out.putNextEntry(new ZipEntry(safeFileName(file.getKey()) + ".yml"));
                out.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalStateException("打包失败: " + e.getMessage(), e);
        }
        return buffer.toByteArray();
    }

    /** id 是管理员填的，可能带路径分隔符或 Windows 不接受的字符，当文件名前先中和掉。 */
    private static String safeFileName(String id) {
        return id.replaceAll("[\\\\/:*?\"<>|\\s]", "_");
    }

    /**
     * 「替换」导入前清空数据库里的定义。
     * <p>
     * 只读的 YAML 定义<B>不在替换范围内</B>：它们在文件里，删不掉也不该删——
     * 否则一次「替换导入」就会把 quests/ 里的定义从视图里抹掉（下次重载又回来，更迷惑）。
     */
    private static <T> void clearDatabase(Collection<T> existing, Function<T, String> idOf,
                                          Consumer<String> delete) {
        for (T item : existing) {
            try {
                delete.accept(idOf.apply(item));
            } catch (DefinitionReadOnlyException ignored) {
                // 文件里的定义删不掉：跳过，导入照常继续
            }
        }
    }

    /** 压缩包条目出错时的措辞：带上文件名，否则不知道是哪一个坏了。 */
    private static String describe(String file, String reason) {
        return file == null ? reason : file + "（" + reason + "）";
    }

    // ------------------------------------------------------------------
    // 请求 / 响应
    // ------------------------------------------------------------------

    /**
     * 取请求体并交给处理器；不是合法 JSON 对象时回 400，处理器不会被调用。
     * <p>
     * 用「回调」而不是「返回 Map + 各调用点判 null」：后者每个写接口都要抄一遍
     * 「为 null 就 return」，漏一处就会拿着 null 往下走。
     */
    private static void withBody(Context ctx, Consumer<Map<String, Object>> handler) {
        Map<String, Object> parsed;
        try {
            // 空体也交给 JsonCodec 判（它对 null/空白返回 null），省掉这里的重复判空
            parsed = JsonCodec.readMapStrict(ctx.body());
        } catch (Exception e) {
            parsed = null;
        }
        // 只有解析失败才拦在这里；处理器自己抛的异常照旧冒泡给 register() 里注册的
        // 异常处理器，否则一个存储故障会被误报成「请求体不合法」
        if (parsed == null) {
            badRequest(ctx, "请求体不是合法的 JSON 对象");
            return;
        }
        handler.accept(parsed);
    }

    /**
     * 取纯文本请求体（YAML 导入用）并交给处理器；空体回 400。
     * <p>
     * 与 {@link #withBody} 分开是刻意的：导入的是 YAML 文本而不是 JSON，
     * 若复用同一个入口，一段语法错误的 YAML 会被报成「请求体不是合法的 JSON 对象」。
     */
    private static void withTextBody(Context ctx, Consumer<String> handler) {
        String body = ctx.body();
        if (body == null || body.isBlank()) {
            badRequest(ctx, "请求体为空");
            return;
        }
        handler.accept(body);
    }

    /** 把任意对象转成 Map，非对象返回 null（导入时跳过非法条目而不是整体失败）。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    static String json(Object value) {
        String text = JsonCodec.writeAny(value);
        return text == null ? errorJson("序列化失败") : text;
    }

    static String errorJson(String message) {
        return "{\"error\":" + JsonCodec.writeAny(String.valueOf(message)) + "}";
    }

    private static void notFound(Context ctx, String message) {
        ctx.status(404).result(errorJson(message));
    }

    private static void badRequest(Context ctx, String message) {
        ctx.status(400).result(errorJson(message));
    }
}
