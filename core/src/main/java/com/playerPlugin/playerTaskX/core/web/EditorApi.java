package com.playerPlugin.playerTaskX.core.web;

import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.reward.RewardType;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;
import com.playerPlugin.playerTaskX.api.schema.ConfigurableType;
import com.playerPlugin.playerTaskX.core.storage.JsonCodec;
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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

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
 */
final class EditorApi {

    private final PlayerTaskX plugin;
    private final MaterialCatalog catalog;

    EditorApi(PlayerTaskX plugin, MaterialCatalog catalog) {
        this.plugin = plugin;
        this.catalog = catalog;
    }

    void register(Javalin app) {
        questRoutes(app);
        playerRoutes(app);
        schemaRoutes(app);
        presetRoutes(app);
        langRoutes(app);
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
            for (Quest quest : plugin.quests().all()) {
                Map<String, Object> json = QuestJson.toJson(quest);
                // 顺带把校验问题给出，编辑器可直接标红
                json.put("problems", plugin.questAdmin().validate(quest));
                list.add(json);
            }
            ctx.result(json(list));
        });

        app.get("/api/quests/export", ctx -> {
            List<Map<String, Object>> exported = new ArrayList<>();
            for (Quest quest : plugin.quests().all()) {
                // 导出不含 problems（派生信息，导入时会重新计算）
                exported.add(QuestJson.toJson(quest));
            }
            ctx.result(json(Map.of("version", 1, "quests", exported)));
        });

        app.post("/api/quests/import", ctx -> withBody(ctx, body -> {
            if (!(body.get("quests") instanceof List<?> list)) {
                badRequest(ctx, "缺少 quests 数组");
                return;
            }
            // replace=true 时先清空再导入，用于「用备份覆盖当前数据」
            if (Boolean.TRUE.equals(body.get("replace"))) {
                for (Quest existing : plugin.quests().all()) {
                    plugin.questAdmin().delete(existing.id());
                }
            }
            int imported = 0;
            List<String> skipped = new ArrayList<>();
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
            ctx.result(json(Map.of("ok", true, "imported", imported, "skipped", skipped,
                    "total", plugin.quests().all().size())));
        }));

        app.get("/api/quests/{id}", ctx -> {
            Quest quest = plugin.quests().find(ctx.pathParam("id")).orElse(null);
            if (quest == null) {
                notFound(ctx, "任务不存在: " + ctx.pathParam("id"));
                return;
            }
            Map<String, Object> json = QuestJson.toJson(quest);
            json.put("problems", plugin.questAdmin().validate(quest));
            ctx.result(json(json));
        });

        app.post("/api/quests", ctx -> withBody(ctx, body -> {
            Quest quest = QuestJson.fromJson(body);
            if (quest.id() == null || quest.id().isBlank()) {
                badRequest(ctx, "任务 id 不能为空");
                return;
            }
            plugin.questAdmin().save(quest);
            ctx.result(json(Map.of("ok", true, "id", quest.id(),
                    "problems", plugin.questAdmin().validate(quest))));
        }));

        app.delete("/api/quests/{id}", ctx -> {
            String id = ctx.pathParam("id");
            ctx.result(json(Map.of("ok", plugin.questAdmin().delete(id), "id", id)));
        });
    }

    // ------------------------------------------------------------------
    // 玩家进度：管理员排查「某玩家为什么没进度」
    // ------------------------------------------------------------------

    private void playerRoutes(Javalin app) {
        app.get("/api/players", ctx -> {
            // 只列出有任务记录的玩家，避免遍历全服离线玩家
            List<Map<String, Object>> players = new ArrayList<>();
            for (UUID playerId : plugin.playerQuestRepository().distinctPlayerIds()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("uuid", playerId.toString());
                entry.put("name", playerName(playerId));
                entry.put("online", Bukkit.getPlayer(playerId) != null);
                entry.put("quests", plugin.playerQuestRepository().findByPlayer(playerId).size());
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
            schema.put("objectives", typeSchemas(plugin.objectiveTypes().all(), type -> Map.of()));
            schema.put("rewards", typeSchemas(plugin.rewardTypes().all(), EditorApi::rewardExtra));
            ctx.result(json(schema));
        });

        // 素材目录：内容来自服务端自己的 Material / EntityType 枚举，天然只含当前版本支持的项
        app.get("/api/catalog", ctx -> ctx.result(json(catalog.build())));
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
        app.get("/api/presets", ctx -> ctx.result(json(PresetJson.toGrouped(plugin.presets().findAll()))));

        app.post("/api/presets/{kind}", ctx -> withBody(ctx, body -> {
            Preset preset = PresetJson.fromJson(ctx.pathParam("kind"), body);
            if (preset == null) {
                badRequest(ctx, "预设缺少 type");
                return;
            }
            plugin.presets().save(preset);
            ctx.result(json(Map.of("ok", true, "preset", PresetJson.toJson(preset))));
        }));

        app.delete("/api/presets/{kind}/{id}", ctx -> {
            String id = ctx.pathParam("id");
            ctx.result(json(Map.of("ok", plugin.presets().delete(id), "id", id)));
        });
    }

    // ------------------------------------------------------------------
    // 语言文件
    // ------------------------------------------------------------------

    private void langRoutes(Javalin app) {
        app.get("/api/langs", ctx -> {
            Map<String, Object> result = new LinkedHashMap<>();
            for (String code : plugin.config().getLanguageAvailable()) {
                result.put(code, readLang(code));
            }
            ctx.result(json(result));
        });

        app.put("/api/langs/{code}", ctx -> withBody(ctx, body -> {
            String code = ctx.pathParam("code");
            Object content = body.get("content");
            writeLang(code, content == null ? "" : String.valueOf(content));
            plugin.messages().reload();
            ctx.result(json(Map.of("ok", true, "code", code)));
        }));
    }

    private String readLang(String code) {
        File file = langFile(code);
        if (file.isFile()) {
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
        File file = langFile(code);
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("无法创建语言目录: " + parent);
        }
        try {
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("写入语言文件失败: " + e.getMessage(), e);
        }
    }

    private File langFile(String code) {
        return new File(plugin.getDataFolder(), "lang/" + code + ".yml");
    }

    // ------------------------------------------------------------------
    // 统计与重载
    // ------------------------------------------------------------------

    private void miscRoutes(Javalin app) {
        app.get("/api/stats", ctx -> {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("quests", plugin.quests().all().size());
            stats.put("dailyQuests", plugin.quests().daily().size());
            stats.put("objectives", plugin.objectiveTypes().all().size());
            stats.put("rewards", plugin.rewardTypes().all().size());
            stats.put("players", plugin.playerQuestRepository().countPlayers());
            stats.put("storage", plugin.describeStorage());
            stats.put("categories", plugin.quests().categories());
            ctx.result(json(stats));
        });

        app.post("/api/reload", ctx -> {
            plugin.questAdmin().reload();
            ctx.result(json(Map.of("ok", true, "quests", plugin.quests().all().size())));
        });
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
