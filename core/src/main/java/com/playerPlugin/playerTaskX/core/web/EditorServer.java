package com.playerPlugin.playerTaskX.core.web;

import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.registry.ObjectiveRegistry;
import com.playerPlugin.playerTaskX.api.registry.RewardRegistry;
import com.playerPlugin.playerTaskX.core.storage.QuestJson;
import com.playerPlugin.playerTaskX.core.storage.StorageException;
import cn.yvmou.ylib.text.TextRenderer;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 内置网页编辑器：提供 REST 接口与静态前端资源。
 *
 * <h2>为什么用 Javalin 而不是自己写 HTTP</h2>
 * 编辑器需要静态资源、路由、JSON 三件事，Javalin 一并解决；
 * 项目本来就依赖它（旧版编辑器遗留），没有引入新依赖。
 *
 * <h2>安全</h2>
 * 这是管理端接口，默认只应监听本机。若配置了 {@code editor.token}，
 * 所有 {@code /api/*} 请求都必须携带同名请求头，否则拒绝。
 * 未配置令牌时记录一条告警——因为那意味着任何能访问该端口的人都能改任务。
 */
public final class EditorServer {

    /** 首选端口被占用时，依次 +1 尝试的端口个数上限。 */
    private static final int PORT_ATTEMPTS = 10;

    private final PlayerTaskX plugin;
    private final MaterialCatalog catalog;
    private final LangFileStore langFiles;
    private Javalin app;
    private int port = -1;

    public EditorServer(PlayerTaskX plugin) {
        this.plugin = plugin;
        // 译名来源：英文读服务端自带的语言文件，中文必要时下载（见 LangFileStore）
        this.langFiles = new LangFileStore(plugin);
        this.catalog = new MaterialCatalog(langFiles);
    }

    /**
     * 尽早准备译名数据（英文同步、中文后台下载）。
     * <p>
     * 与 {@link #start(int)} 分开：启动必须在插件启用时立即调用，而准备译名可以更早、
     * 且不依赖端口是否可用——即使编辑器没启动，下载好的中文语言文件也留在磁盘上。
     */
    public void prepareCatalog() {
        try {
            langFiles.initialize();
        } catch (Throwable e) {
            // 译名只是编辑器的便利功能，任何意外都不该影响插件启用
            plugin.getLogger().warning("准备素材译名失败（编辑器将显示枚举名）: " + e);
        }
    }

    /** 实际监听的端口；未启动时为 -1。端口被占用自动 +1 后，这里与配置值可能不同。 */
    public int port() {
        return port;
    }

    /**
     * 启动 HTTP 服务；失败只记录日志，不影响插件其它功能。
     * <p>
     * 端口被占用时自动 +1 重试（最多 {@value #PORT_ATTEMPTS} 个端口）：
     * 8080 这类常用端口很容易被开发工具占掉，直接失败会让管理员以为插件坏了。
     * 但「配置端口 ≠ 实际端口」必须显眼，因此每次换口都记 warn，
     * 且 {@code /ptxa editor} 一律报告实际端口。
     */
    public void start(int preferredPort) {
        int port = preferredPort;
        for (int attempt = 0; attempt < PORT_ATTEMPTS; attempt++) {
            try {
                // 每次尝试用全新实例：上一次绑定失败的实例状态不可复用
                app = Javalin.create(config -> {
                    config.showJavalinBanner = false;
                    config.http.defaultContentType = "application/json; charset=utf-8";
                    // 素材目录与任务清单都是「一次传输几十上百 KB 的 JSON」，
                    // 编辑器又只在浏览器里用，开 gzip 收益明显且无兼容性风险
                    config.http.gzipOnlyCompression();
                });
                registerRoutes();
                app.start(port);
                this.port = port;

                if (port != preferredPort) {
                    plugin.getLogger().warning("网页编辑器端口 " + preferredPort + " 被占用，已自动改用 "
                            + port + "；如需固定端口，请修改 config.yml 的 editor.port 或释放被占端口。");
                }
                plugin.messages().send(org.bukkit.Bukkit.getConsoleSender(),
                        "editor.started", "127.0.0.1:" + port);
                if (plugin.config().getEditorToken().isBlank()) {
                    plugin.getLogger().warning("网页编辑器未设置访问令牌（editor.token），"
                            + "任何能访问该端口的人都可以修改任务；请仅在本机使用或配置令牌。");
                }
                return;
            } catch (io.javalin.util.JavalinBindException e) {
                closeQuietly();
                if (attempt + 1 < PORT_ATTEMPTS) {
                    plugin.getLogger().warning("网页编辑器端口 " + port + " 被占用，自动尝试 " + (port + 1));
                    port++;
                } else {
                    plugin.getLogger().warning("网页编辑器端口 " + port + " 也被占用，已达自动重试上限");
                }
            } catch (Throwable e) {
                // 捕获 Throwable 而不是 Exception：网页编辑器是附加功能，
                // Javalin/Kotlin 类缺失等都不应该让整个插件无法启用
                plugin.getLogger().severe("网页编辑器启动失败（端口 " + port + "）: " + e);
                closeQuietly();
                app = null;
                this.port = -1;
                return;
            }
        }
        plugin.getLogger().severe("网页编辑器未启动：从 " + preferredPort + " 起连续 "
                + PORT_ATTEMPTS + " 个端口都被占用。请修改 config.yml 的 editor.port 后重启。");
        app = null;
        this.port = -1;
    }

    /** 关闭 HTTP 服务。 */
    public void stop() {
        closeQuietly();
        port = -1;
    }

    private void closeQuietly() {
        Javalin instance = app;
        app = null;
        if (instance != null) {
            try {
                instance.stop();
            } catch (Throwable ignored) {
                // 关服阶段的异常无需上报
            }
        }
    }

    private void registerRoutes() {
        // ---- 令牌校验：只拦 /api/*，静态资源放行（前端本身不含敏感数据） ----
        app.before("/api/*", this::checkToken);

        // ---- 静态前端 ----
        app.get("/", ctx -> serveIndex(ctx));
        app.get("/assets/{file}", ctx -> serveAsset(ctx, "assets/" + ctx.pathParam("file")));
        app.get("/favicon.ico", ctx -> ctx.status(204));

        // ---- 任务 CRUD ----
        app.get("/api/quests", ctx -> {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Quest quest : plugin.quests().all()) {
                Map<String, Object> json = QuestJson.toJson(quest);
                // 顺带把校验问题给出，编辑器可直接标红
                json.put("problems", plugin.questAdmin().validate(quest));
                list.add(json);
            }
            ctx.result(toJson(list));
        });

        // ---- 批量接口必须注册在 {id} 之前 ----
        // 路由按注册顺序匹配，否则 /api/quests/export 会被 /api/quests/{id} 抢先匹配成
        // 「id 为 export 的任务」，直接返回 404。
        app.get("/api/quests/export", ctx -> {
            List<Map<String, Object>> exported = new ArrayList<>();
            for (Quest quest : plugin.quests().all()) {
                // 导出不含 problems（派生信息，导入时会重新计算）
                exported.add(QuestJson.toJson(quest));
            }
            ctx.result(toJson(Map.of("version", 1, "quests", exported)));
        });

        app.post("/api/quests/import", ctx -> {
            Map<String, Object> body = fromJson(ctx.body());
            if (body == null) {
                badRequest(ctx, "请求体不是合法的 JSON 对象");
                return;
            }
            Object rawQuests = body.get("quests");
            if (!(rawQuests instanceof List<?> list)) {
                badRequest(ctx, "缺少 quests 数组");
                return;
            }
            // replace=true 时先清空再导入，用于「用备份覆盖当前数据」
            boolean replace = Boolean.TRUE.equals(body.get("replace"));
            int imported = 0;
            List<String> skipped = new ArrayList<>();
            if (replace) {
                for (Quest existing : plugin.quests().all()) {
                    plugin.questAdmin().delete(existing.id());
                }
            }
            for (Object item : list) {
                Map<String, Object> node = asMap(item);
                if (node == null) {
                    continue;
                }
                Quest quest = QuestJson.fromJson(node);
                if (quest.id() == null || quest.id().isBlank()) {
                    skipped.add("(缺少 id)");
                    continue;
                }
                plugin.questAdmin().save(quest);
                imported++;
            }
            ctx.result(toJson(Map.of("ok", true, "imported", imported, "skipped", skipped,
                    "total", plugin.quests().all().size())));
        });

        app.get("/api/quests/{id}", ctx -> {
            Quest quest = plugin.quests().find(ctx.pathParam("id")).orElse(null);
            if (quest == null) {
                notFound(ctx, "任务不存在: " + ctx.pathParam("id"));
                return;
            }
            Map<String, Object> json = QuestJson.toJson(quest);
            json.put("problems", plugin.questAdmin().validate(quest));
            ctx.result(toJson(json));
        });

        app.post("/api/quests", ctx -> {
            Map<String, Object> body = fromJson(ctx.body());
            if (body == null) {
                badRequest(ctx, "请求体不是合法的 JSON 对象");
                return;
            }
            Quest quest = QuestJson.fromJson(body);
            if (quest.id() == null || quest.id().isBlank()) {
                badRequest(ctx, "任务 id 不能为空");
                return;
            }
            plugin.questAdmin().save(quest);
            ctx.result(toJson(Map.of("ok", true, "id", quest.id(),
                    "problems", plugin.questAdmin().validate(quest))));
        });

        app.delete("/api/quests/{id}", ctx -> {
            String id = ctx.pathParam("id");
            boolean removed = plugin.questAdmin().delete(id);
            ctx.result(toJson(Map.of("ok", removed, "id", id)));
        });

        // ---- 玩家进度：管理员排查「某玩家为什么没进度」 ----
        app.get("/api/players", ctx -> {
            // 只列出有任务记录的玩家，避免遍历全服离线玩家
            List<Map<String, Object>> players = new ArrayList<>();
            List<UUID> ids = plugin.playerQuestRepository().distinctPlayerIds();
            for (UUID playerId : ids) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("uuid", playerId.toString());
                String name = Bukkit.getOfflinePlayer(playerId).getName();
                entry.put("name", name == null ? "" : name);
                entry.put("online", Bukkit.getPlayer(playerId) != null);
                entry.put("quests", plugin.playerQuestRepository().findByPlayer(playerId).size());
                players.add(entry);
            }
            ctx.result(toJson(players));
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
            String name = Bukkit.getOfflinePlayer(playerId).getName();
            result.put("name", name == null ? "" : name);

            List<Map<String, Object>> records = new ArrayList<>();
            for (var record : plugin.playerQuestRepository().findByPlayer(playerId)) {
                Quest quest = plugin.quests().find(record.questId()).orElse(null);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("questId", record.questId());
                entry.put("questName", quest == null ? "" : TextRenderer.strip(quest.name()));
                entry.put("type", record.type().name());
                entry.put("status", record.status().name());
                entry.put("assignedAt", record.assignedAt());
                entry.put("expiresAt", record.expiresAt());
                entry.put("percent", quest == null ? 0 : Math.round(record.completionRatio(quest) * 100));
                // 逐目标给出「当前/需求」，管理员一眼能看出卡在哪一步
                List<Map<String, Object>> objectives = new ArrayList<>();
                if (quest != null) {
                    for (int i = 0; i < quest.objectives().size(); i++) {
                        var objective = quest.objectives().get(i);
                        Map<String, Object> slot = new LinkedHashMap<>();
                        slot.put("index", i);
                        slot.put("type", objective.type());
                        slot.put("current", record.progress(i));
                        slot.put("required", objective.amount());
                        objectives.add(slot);
                    }
                }
                entry.put("objectives", objectives);
                records.add(entry);
            }
            result.put("progress", records);
            ctx.result(toJson(result));
        });

        // ---- schema：目标与奖励类型的字段描述，前端据此动态生成表单 ----
        app.get("/api/schema", ctx -> {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("objectives", typeSchemas(plugin.objectiveTypes()));
            schema.put("rewards", typeSchemas(plugin.rewardTypes()));
            ctx.result(toJson(schema));
        });

        // ---- 素材目录：图标选择与材质字段搜索用 ----
        // 内容来自服务端自己的 Material / EntityType 枚举，因此天然只含当前版本支持的项
        app.get("/api/catalog", ctx -> ctx.result(toJson(catalog.build())));

        // ---- 目标 / 奖励预设 ----
        // 存储后端由 definitions.type 决定（JSON 文件或数据库），编辑器侧接口保持不变
        app.get("/api/presets", ctx -> ctx.result(toJson(
                PresetJson.toGrouped(plugin.presets().findAll()))));

        app.post("/api/presets/{kind}", ctx -> {
            Map<String, Object> body = fromJson(ctx.body());
            if (body == null) {
                badRequest(ctx, "请求体不是合法的 JSON 对象");
                return;
            }
            Preset preset = PresetJson.fromJson(ctx.pathParam("kind"), body);
            if (preset == null) {
                badRequest(ctx, "预设缺少 type");
                return;
            }
            plugin.presets().save(preset);
            ctx.result(toJson(Map.of("ok", true, "preset", PresetJson.toJson(preset))));
        });

        app.delete("/api/presets/{kind}/{id}", ctx -> {
            boolean removed = plugin.presets().delete(ctx.pathParam("id"));
            ctx.result(toJson(Map.of("ok", removed, "id", ctx.pathParam("id"))));
        });

        // ---- 语言文件 ----
        app.get("/api/langs", ctx -> {
            Map<String, Object> result = new LinkedHashMap<>();
            for (String code : plugin.config().getLanguageAvailable()) {
                result.put(code, readLang(code));
            }
            ctx.result(toJson(result));
        });

        app.put("/api/langs/{code}", ctx -> {
            String code = ctx.pathParam("code");
            Map<String, Object> body = fromJson(ctx.body());
            if (body == null) {
                badRequest(ctx, "请求体不是合法的 JSON 对象");
                return;
            }
            Object content = body.get("content");
            writeLang(code, content == null ? "" : String.valueOf(content));
            plugin.messages().reload();
            ctx.result(toJson(Map.of("ok", true, "code", code)));
        });

        // ---- 统计与重载 ----
        app.get("/api/stats", ctx -> {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("quests", plugin.quests().all().size());
            stats.put("dailyQuests", plugin.quests().daily().size());
            stats.put("objectives", plugin.objectiveTypes().all().size());
            stats.put("rewards", plugin.rewardTypes().all().size());
            stats.put("players", plugin.playerQuestRepository().countPlayers());
            stats.put("storage", plugin.describeStorage());
            stats.put("categories", plugin.quests().categories());
            ctx.result(toJson(stats));
        });

        app.post("/api/reload", ctx -> {
            plugin.questAdmin().reload();
            ctx.result(toJson(Map.of("ok", true, "quests", plugin.quests().all().size())));
        });

        // ---- 统一异常处理：把业务异常转成 4xx，而不是让前端只看到 500 ----
        app.exception(IllegalArgumentException.class, (e, ctx) ->
                badRequest(ctx, e.getMessage() == null ? "参数不合法" : e.getMessage()));
        app.exception(IllegalStateException.class, (e, ctx) ->
                ctx.status(409).result(toJson(Map.of("error",
                        e.getMessage() == null ? "状态冲突" : e.getMessage()))));
        app.exception(StorageException.class, (e, ctx) ->
                ctx.status(500).result(toJson(Map.of("error", "存储错误: " + e.getMessage()))));
        app.exception(TokenRejected.class, (e, ctx) -> {
            // checkToken 已写好响应体，这里只需保持 401
            ctx.status(401);
        });
    }

    /** 把目标/奖励类型集合序列化为「类型 → 字段列表」。 */
    private Map<String, Object> typeSchemas(Object registry) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<?> types = registry instanceof ObjectiveRegistry objectiveRegistry
                ? new ArrayList<>(objectiveRegistry.all())
                : registry instanceof RewardRegistry rewardRegistry
                        ? new ArrayList<>(rewardRegistry.all())
                        : List.of();
        for (Object type : types) {
            Map<String, Object> entry = new LinkedHashMap<>();
            String id;
            String displayName;
            List<?> fields;
            if (type instanceof com.playerPlugin.playerTaskX.api.objective.ObjectiveType objectiveType) {
                id = objectiveType.id();
                displayName = objectiveType.displayName();
                fields = objectiveType.schema();
            } else if (type instanceof com.playerPlugin.playerTaskX.api.reward.RewardType rewardType) {
                id = rewardType.id();
                displayName = rewardType.displayName();
                fields = rewardType.schema();
                entry.put("available", rewardType.available());
                entry.put("unavailableReason", rewardType.unavailableReason());
            } else {
                continue;
            }
            entry.put("id", id);
            entry.put("displayName", displayName);
            entry.put("fields", fields.stream().map(this::fieldToJson).toList());
            result.put(id, entry);
        }
        return result;
    }

    private Map<String, Object> fieldToJson(Object field) {
        if (!(field instanceof com.playerPlugin.playerTaskX.api.schema.ConfigField configField)) {
            return Map.of();
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("key", configField.key());
        json.put("label", configField.label());
        json.put("type", configField.type().name());
        json.put("required", configField.required());
        json.put("defaultValue", configField.defaultValue());
        json.put("options", configField.options());
        json.put("hint", configField.hint());
        return json;
    }

    private void checkToken(Context ctx) {
        String token = plugin.config().getEditorToken();
        if (token == null || token.isBlank()) {
            return;
        }
        String provided = ctx.header("X-Editor-Token");
        if (!token.equals(provided)) {
            ctx.status(401).result(toJson(Map.of("error", "无效或缺失的访问令牌（请求头 X-Editor-Token）")));
            // 抛出以中断后续处理器
            throw new TokenRejected();
        }
    }

    /** 用于中断请求的内部控制流异常。 */
    private static final class TokenRejected extends RuntimeException {
    }

    private void serveIndex(Context ctx) throws IOException {
        byte[] content = readResource("web/index.html");
        if (content == null) {
            ctx.status(404).contentType("text/html; charset=utf-8")
                    .result("<h1>PlayerTaskX 编辑器未构建</h1>"
                            + "<p>请先执行前端构建（task-editor-vue 目录下 npm run build），或关闭 editor.enabled。</p>");
            return;
        }
        ctx.contentType("text/html; charset=utf-8").result(new String(content, StandardCharsets.UTF_8));
    }

    /** 提供 Vite 产出的静态资源（js/css），带正确的内容类型。 */
    private void serveAsset(Context ctx, String resourcePath) {
        byte[] content = readResource("web/" + resourcePath);
        if (content == null) {
            ctx.status(404).result("");
            return;
        }
        ctx.contentType(contentTypeOf(resourcePath)).result(content);
    }

    private String contentTypeOf(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) return "text/javascript; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".html")) return "text/html; charset=utf-8";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        return "application/octet-stream";
    }

    /**
     * 读取资源：优先插件数据目录（便于用户自行替换前端），其次 jar 内。
     * <p>
     * 数据目录优先是有意为之——前端改版时不必重新打包插件。
     */
    private byte[] readResource(String resourcePath) {
        File external = new File(plugin.getDataFolder(), resourcePath);
        if (external.exists() && external.isFile()) {
            try {
                return Files.readAllBytes(external.toPath());
            } catch (IOException ignored) {
                // 读失败则退回 jar 内资源
            }
        }
        try (var stream = plugin.getResource(resourcePath)) {
            return stream == null ? null : stream.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private String readLang(String code) {
        File file = new File(plugin.getDataFolder(), "lang/" + code + ".yml");
        if (file.exists()) {
            try {
                return Files.readString(file.toPath(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        }
        try (var stream = plugin.getResource("lang/" + code + ".yml")) {
            return stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * 写入语言文件。
     * <p>
     * 先校验能解析成 YAML 再落盘：编辑器里一次手滑的缩进错误不应该让玩家看到
     * 「Missing message」。
     */
    private void writeLang(String code, String content) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.loadFromString(content);
        } catch (Exception e) {
            throw new IllegalArgumentException("语言文件不是合法的 YAML: " + e.getMessage(), e);
        }
        File file = new File(plugin.getDataFolder(), "lang/" + code + ".yml");
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("无法创建语言目录: " + parent);
        }
        try {
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("写入语言文件失败: " + e.getMessage(), e);
        }
    }

    private void notFound(Context ctx, String message) {
        ctx.status(404).result(toJson(Map.of("error", message)));
    }

    private void badRequest(Context ctx, String message) {
        ctx.status(400).result(toJson(Map.of("error", message)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            Object parsed = Json.MAPPER.readValue(body, Map.class);
            return parsed instanceof Map ? (Map<String, Object>) parsed : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 把任意对象转成 Map，非对象返回 null（导入时跳过非法条目而不是整体失败）。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private String toJson(Object value) {
        try {
            return Json.MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return "{\"error\":\"序列化失败\"}";
        }
    }

    /** 单例 ObjectMapper：构造开销大，且线程安全。 */
    private static final class Json {
        private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
                new com.fasterxml.jackson.databind.ObjectMapper();

        private Json() {
        }
    }

    /** 语言代码归一化，避免大小写不一致导致文件找不到。 */
    public static String normalizeLang(String code) {
        return code == null ? "" : code.toLowerCase(Locale.ROOT);
    }
}
