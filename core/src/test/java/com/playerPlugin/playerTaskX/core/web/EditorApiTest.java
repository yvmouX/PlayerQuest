package com.playerPlugin.playerTaskX.core.web;

import cn.yvmou.ylib.message.MessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.model.PlayerQuest;
import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.model.QuestType;
import com.playerPlugin.playerTaskX.core.config.PluginConfig;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;
import com.playerPlugin.playerTaskX.core.quest.QuestAdminService;
import com.playerPlugin.playerTaskX.core.registry.BuiltIns;
import com.playerPlugin.playerTaskX.core.registry.ObjectiveRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.QuestRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.RewardRegistryImpl;
import com.playerPlugin.playerTaskX.core.reward.RewardService;
import com.playerPlugin.playerTaskX.core.storage.DefinitionReadOnlyException;
import com.playerPlugin.playerTaskX.core.storage.PlayerQuestRepository;
import com.playerPlugin.playerTaskX.core.storage.PresetRepository;
import com.playerPlugin.playerTaskX.core.storage.QuestRepository;
import com.playerPlugin.playerTaskX.core.storage.yaml.YamlDefinitions;
import io.javalin.Javalin;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * {@link EditorApi} 的接口级测试：真起 Javalin，用 JDK 自带的 {@link HttpClient} 打真 HTTP。
 *
 * <h2>为什么值得真起服务</h2>
 * 这一层是「前端唯一的数据来源」，出错的方式几乎都不报错：状态码从 400 变成 500、
 * 响应字段改名、{@code /api/quests/export} 被 {@code /api/quests/{id}} 抢先匹配而 404、
 * 坏请求体不再被拦下——编译期与普通断言都看不见，只有前端静默失效。
 * 因此这里不打桩路由，而是把整套注册（含异常映射）跑起来，按前端实际会发的请求逐个钉住。
 *
 * <h2>假宿主</h2>
 * {@link EditorServices} 由 {@link FakeEditorServices} 提供内存实现。任务维护走<b>真实</b>
 * {@link QuestAdminService}（只把 {@code reload()} 换成内存实现，理由见该类注释），
 * 因此「POST 保存后 GET 能读回来」是端到端成立的，而不是「打了桩所以成立」。
 *
 * <h2>测不到的部分</h2>
 * {@code /api/players}（要 Bukkit 的离线玩家名与在线状态）与 {@code /api/catalog} 的<b>内容</b>
 * （要枚举服务端的 {@code Material} / {@code EntityType}）不在覆盖范围内：
 * 前者只能靠 {@code Bukkit.setServer} 装桩，而那是一次性的全局单例（重复调用直接抛异常，
 * 且会把装桩的那个测试类一起弄挂），已被 {@code QuestAdminServiceTest} 占用。
 * 这里只钉住 catalog 的<b>路由与直通</b>（假身返回什么就下发什么）；两条路由的对外形状均未变，
 * 这一点如实写在这里，避免下一轮误以为「编辑器接口都测过了」。
 */
class EditorApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TOKEN = "s3cret-token";

    private final HttpClient http = HttpClient.newHttpClient();

    @TempDir
    Path tempDir;

    private FakeEditorServices services;
    private MaterialCatalog catalog;
    private Javalin app;
    private String base;

    @BeforeEach
    void startApi() {
        services = new FakeEditorServices(tempDir.toFile());
        // 素材目录用假身：这里只钉住 /api/catalog 的路由与直通，内容要枚举服务端材质（见类注释）
        catalog = mock(MaterialCatalog.class);

        app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            // 与 EditorServer 保持一致：编辑器前端按 JSON 解析响应
            config.http.defaultContentType = "application/json; charset=utf-8";
        });
        new EditorApi(services, catalog).register(app);
        // 端口 0 让操作系统分配，避免与开发机上正在跑的编辑器抢 28080
        app.start(0);
        base = "http://127.0.0.1:" + app.port();
    }

    @AfterEach
    void stopApi() {
        if (app != null) {
            app.stop();
        }
    }

    // ------------------------------------------------------------------
    // 任务 CRUD
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/quests：列表带上 problems，编辑器才能标红")
    void questsListCarriesProblems() throws Exception {
        services.seed(quest("ok_quest"), brokenQuest("bad_quest"));

        JsonNode list = json(send("GET", "/api/quests", null));

        assertEquals(2, list.size());
        JsonNode ok = itemById(list, "ok_quest");
        assertTrue(ok.get("problems").isArray(), "problems 必须是数组，前端按数组遍历标红");
        assertEquals(0, ok.get("problems").size(), "合法任务不该有问题");
        // 顺带钉住 QuestJson 的字段名：前端直接读这些键
        assertEquals("任务 ok_quest", ok.get("name").asText());
        assertEquals("NORMAL", ok.get("type").asText());
        assertTrue(ok.get("enabled").asBoolean());
        assertEquals(1, ok.get("objectives").size());

        JsonNode broken = itemById(list, "bad_quest");
        assertTrue(problemsText(broken).contains("no_such_objective"),
                "未知目标类型必须出现在 problems 里，实际: " + broken.get("problems"));
        assertTrue(problemsText(broken).contains("no_such_reward"),
                "未知奖励类型必须出现在 problems 里，实际: " + broken.get("problems"));
    }

    @Test
    @DisplayName("GET /api/quests/{id}：命中返回任务，未命中 404 且带 error 文案")
    void questByIdHitAndMiss() throws Exception {
        services.seed(quest("q1"));

        JsonNode found = json(send("GET", "/api/quests/q1", null));
        assertEquals("q1", found.get("id").asText());
        assertEquals(0, found.get("problems").size());

        HttpResponse<String> missing = send("GET", "/api/quests/no_such_id", null);
        assertTrue(error(missing, 404).contains("任务不存在"),
                "错误体要能直接展示给管理员，实际: " + missing.body());
    }

    @Test
    @DisplayName("POST /api/quests：保存后能原样读回（编辑器请求体与模型之间的往返）")
    void postQuestSavesAndReadsBack() throws Exception {
        String body = """
                {
                  "id": "new_quest",
                  "name": "新任务",
                  "icon": "DIAMOND_PICKAXE",
                  "type": "DAILY",
                  "category": "mining",
                  "refreshCost": 250.0,
                  "objectives": [{"type": "break_block", "properties": {"target": "STONE", "amount": 3}}],
                  "rewards": [{"type": "exp", "properties": {"amount": 50}}]
                }
                """;

        HttpResponse<String> saved = send("POST", "/api/quests", body);
        assertEquals(200, saved.statusCode(), "保存应成功，实际: " + saved.body());
        JsonNode savedJson = json(saved);
        assertTrue(savedJson.get("ok").asBoolean());
        assertEquals("new_quest", savedJson.get("id").asText());
        assertEquals(0, savedJson.get("problems").size());

        JsonNode readBack = json(send("GET", "/api/quests/new_quest", null));
        assertEquals("DAILY", readBack.get("type").asText());
        assertEquals("mining", readBack.get("category").asText());
        assertEquals("DIAMOND_PICKAXE", readBack.get("icon").asText());
        assertEquals(3, readBack.get("objectives").get(0).get("properties").get("amount").asInt(),
                "目标数量必须原样往返，编辑器保存后再打开不能变样");
        assertEquals(250.0, readBack.get("refreshCost").asDouble());
    }

    @Test
    @DisplayName("POST /api/quests：缺 id 回 400，且不落库")
    void postQuestWithoutIdIsRejected() throws Exception {
        HttpResponse<String> response = send("POST", "/api/quests", "{\"name\":\"没有 id\"}");

        assertTrue(error(response, 400).contains("id"));
        assertEquals(0, services.quests().all().size(), "被拒的请求不能留下半条记录");
    }

    @Test
    @DisplayName("请求体不是合法 JSON 对象：400 而不是 500，也不是「当成空对象」")
    void malformedBodyIsBadRequest() throws Exception {
        HttpResponse<String> broken = send("POST", "/api/quests", "{ 这不是 JSON");
        assertTrue(error(broken, 400).contains("请求体"),
                "要说清是请求体的问题，实际: " + broken.body());
        assertEquals(0, services.quests().all().size());

        // 空体同样按「没有请求体」处理：否则一个手滑的空 POST 会静默建出空任务
        assertEquals(400, send("POST", "/api/quests", "").statusCode());
        assertEquals(400, send("POST", "/api/presets/objectives", "{").statusCode());
    }

    @Test
    @DisplayName("DELETE /api/quests/{id}：ok 反映是否真删掉，删不存在的返回 false")
    void deleteQuestReportsWhetherItExisted() throws Exception {
        services.seed(quest("q1"));

        JsonNode deleted = json(send("DELETE", "/api/quests/q1", null));
        assertTrue(deleted.get("ok").asBoolean());
        assertEquals("q1", deleted.get("id").asText());
        assertEquals(0, services.quests().all().size(), "删除必须同步清理内存注册表");

        // 再删一次：前端据此区分「删掉了」与「本来就没有」
        assertFalse(json(send("DELETE", "/api/quests/q1", null)).get("ok").asBoolean());
    }

    @Test
    @DisplayName("GET /api/quests/export 优先于 /api/quests/{id} 匹配，且单个任务导出成一份 yml")
    void exportRouteWinsOverIdRoute() throws Exception {
        services.seed(quest("q1"));

        HttpResponse<String> response = send("GET", "/api/quests/export?ids=q1", null);

        assertEquals(200, response.statusCode(),
                "若 export 被当成「id 为 export 的任务」就会 404，备份导出随之失效");
        // 文件名单条时用任务 id：下载下来就是一个能直接放进 quests/ 的文件
        assertTrue(response.headers().firstValue("Content-Disposition").orElse("").contains("q1.yml"),
                "单条导出应当是一个以 id 命名的 yml");
        String yaml = response.body();
        assertTrue(yaml.contains("id: q1"), "导出的应当是 YAML，实际: " + yaml);
        assertFalse(yaml.contains("problems"), "导出不含派生信息 problems（导入时会重新算）");
        // 能被自己的导入解析回来：导出格式与导入格式必须是一份
        assertEquals("q1", YamlDefinitions.readQuest(yaml).id());
    }

    @Test
    @DisplayName("导出多个 / 全部任务：打包成 zip，一个任务一个文件")
    void exportMultiplePacksZip() throws Exception {
        services.seed(quest("q1"), quest("q2"), quest("q3"));

        HttpResponse<byte[]> all = sendBytes("GET", "/api/quests/export", null);
        assertEquals(200, all.statusCode());
        assertTrue(all.headers().firstValue("Content-Type").orElse("").contains("zip"));
        Map<String, String> files = unzip(all.body());
        assertEquals(Set.of("q1.yml", "q2.yml", "q3.yml"), files.keySet(),
                "一个任务一个文件，文件名是 id");
        assertEquals("q1", YamlDefinitions.readQuest(files.get("q1.yml")).id());

        // 只选两条：仍然打包（不是把两条塞进一个 yml）
        Map<String, String> picked = unzip(sendBytes("GET", "/api/quests/export?ids=q1,q3", null).body());
        assertEquals(Set.of("q1.yml", "q3.yml"), picked.keySet());

        // 单条：直接给 yml，不是装着一个文件的 zip
        HttpResponse<byte[]> single = sendBytes("GET", "/api/quests/export?ids=q2", null);
        assertFalse(single.headers().firstValue("Content-Type").orElse("").contains("zip"));
        assertEquals("q2", YamlDefinitions.readQuest(new String(single.body(), StandardCharsets.UTF_8)).id());

        // 不存在的 id：404 且说明是哪一个
        HttpResponse<String> missing = send("GET", "/api/quests/export?ids=nope", null);
        assertEquals(404, missing.statusCode());
        assertTrue(error(missing, 404).contains("nope"), error(missing, 404));
    }

    @Test
    @DisplayName("POST /api/quests/import：接受单个任务的 yml 与 zip；顶层是列表则明确拒绝")
    void importAcceptsSingleFileAndZip() throws Exception {
        // 单个任务的 yml（带 id）
        JsonNode single = json(send("POST", "/api/quests/import", """
                id: imported
                name: 导入的
                objectives:
                  - type: break_block
                    properties: { amount: 1 }
                """));
        assertTrue(single.get("ok").asBoolean());
        assertEquals(1, single.get("imported").asInt());
        assertTrue(services.quests().find("imported").isPresent());

        // zip：两个文件两条任务，文件名与内容 id 不一致时以内容为准
        HttpResponse<String> zipped = sendBytesAs("POST", "/api/quests/import",
                zipOf(Map.of("a.yml", """
                        id: from_zip_a
                        objectives:
                          - type: break_block
                            properties: { amount: 1 }
                        """, "b.yml", """
                        id: from_zip_b
                        objectives:
                          - type: break_block
                            properties: { amount: 1 }
                        """)));
        assertEquals(200, zipped.statusCode(), zipped.body());
        assertEquals(2, json(zipped).get("imported").asInt());
        assertTrue(services.quests().find("from_zip_a").isPresent());
        assertTrue(services.quests().find("from_zip_b").isPresent());

        // 压缩包里坏掉的那个只跳过它自己，其余照常导入
        HttpResponse<String> partial = sendBytesAs("POST", "/api/quests/import",
                zipOf(Map.of("good.yml", """
                        id: good_one
                        objectives:
                          - type: break_block
                            properties: { amount: 1 }
                        """, "bad.yml", "id: [\n")));
        assertEquals(1, json(partial).get("imported").asInt());
        assertEquals(1, json(partial).get("skipped").size());
        assertTrue(json(partial).get("skipped").get(0).asText().contains("bad.yml"),
                "跳过的原因要说清是压缩包里哪个文件: " + json(partial).get("skipped"));

        // 顶层是列表（旧的多任务清单）：明确 400 并指出该用 zip —— 一个文件只能放一个任务
        HttpResponse<String> listed = send("POST", "/api/quests/import", """
                - id: oops
                  objectives:
                    - type: break_block
                      properties: { amount: 1 }
                """);
        assertEquals(400, listed.statusCode());
        assertTrue(error(listed, 400).contains("zip"), error(listed, 400));

        // 语法错误、空体、非映射顶层
        HttpResponse<String> broken = send("POST", "/api/quests/import", "id: [");
        assertEquals(400, broken.statusCode());
        assertEquals(400, send("POST", "/api/quests/import", "").statusCode());
        assertEquals(400, send("POST", "/api/quests/import", "就一句话").statusCode());
    }

    @Test
    @DisplayName("导入 replace=true 只清数据库里的定义：YAML 文件里的（只读）不受影响")
    void importReplaceKeepsReadOnlyDefinitions() throws Exception {
        services.seed(quest("db_quest"), quest("file_quest"));
        services.markReadOnly("file_quest");

        JsonNode result = json(send("POST", "/api/quests/import?replace=true", """
                id: fresh
                objectives:
                  - type: break_block
                    properties: { amount: 1 }
                """));

        assertEquals(1, result.get("imported").asInt());
        assertFalse(services.quests().find("db_quest").isPresent(), "replace 应清掉数据库里的旧定义");
        assertTrue(services.quests().find("file_quest").isPresent(),
                "只读定义（YAML 文件）不在替换范围内");
        assertTrue(services.quests().find("fresh").isPresent());
    }

    @Test
    @DisplayName("来源标记：文件定义的任务是 source=file 且写入被拒（409）")
    void fileDefinedQuestIsReadOnly() throws Exception {
        services.seed(quest("from_file"), quest("db_quest"));
        services.markReadOnly("from_file");

        assertEquals("file", json(send("GET", "/api/quests/from_file", null)).get("source").asText(),
                "编辑器据此禁用保存/删除");
        assertEquals("database", json(send("GET", "/api/quests/db_quest", null)).get("source").asText());

        HttpResponse<String> save = send("POST", "/api/quests", """
                {"id": "from_file", "name": "改名", "type": "NORMAL",
                 "objectives": [{"type": "break_block", "properties": {"amount": 1}}]}
                """);
        assertEquals(409, save.statusCode(), "写到只读 id 必须是 409 而不是静默成功");
        assertTrue(error(save, 409).contains("from_file"), error(save, 409));

        assertEquals(409, send("DELETE", "/api/quests/from_file", null).statusCode(),
                "删除只读定义同样要拒绝");
    }

    // ------------------------------------------------------------------
    // schema / 预设
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/schema：目标与奖励的字段结构，前端据此生成表单")
    void schemaDescribesFields() throws Exception {
        JsonNode schema = json(send("GET", "/api/schema", null));

        JsonNode breakBlock = schema.get("objectives").get("break_block");
        assertNotNull(breakBlock, "内置目标类型必须出现在 schema 里");
        assertFalse(breakBlock.get("displayName").asText().isBlank(), "类型要有人看的名字");

        JsonNode target = fieldOf(breakBlock, "target");
        assertEquals("PICKER", target.get("type").asText(), "要弹选择器，而不是让管理员手打枚举名");
        assertEquals(List.of("block"), kindsOf(target), "挖掘方块只能选方块——值域写宽了选择器就会列出苹果");
        assertTrue(target.get("required").asBoolean());
        assertFalse(target.get("defaultValue").isNull(), "编辑器新建任务时要预填默认值");
        // 八个键一个都不能少：前端是逐键读取的，少一个就是静默的空控件
        for (String key : List.of("key", "label", "type", "required", "defaultValue", "options", "hint", "kinds")) {
            assertTrue(target.has(key), "字段描述缺 " + key + ": " + target);
        }
        assertEquals("INTEGER", fieldOf(breakBlock, "amount").get("type").asText(),
                "每个目标都要有 amount，编辑器据此渲染数量输入框");

        JsonNode exp = schema.get("rewards").get("exp");
        assertTrue(exp.get("available").asBoolean(), "原版经验奖励恒可用");
        assertEquals("", exp.get("unavailableReason").asText());
        JsonNode money = schema.get("rewards").get("money");
        assertFalse(money.get("available").asBoolean(),
                "单测环境没有 Vault，金币奖励必须如实报「不可用」而不是假装可用");
        assertTrue(money.get("unavailableReason").asText().contains("经济插件"),
                "原因要说清缺的是经济插件，而不是含糊的「不可用」");
        // 目标类型同样要报可用性：没装 CustomFishing 时「自定义钓鱼」配了也不会涨进度
        JsonNode customFish = schema.get("objectives").get("custom_fish");
        assertNotNull(customFish, "内置目标类型必须出现在 schema 里");
        assertFalse(customFish.get("available").asBoolean());
        assertTrue(customFish.get("unavailableReason").asText().contains("CustomFishing"));
        JsonNode kill = schema.get("objectives").get("kill");
        assertTrue(kill.get("available").asBoolean(), "击杀目标不依赖任何插件，恒可用");
    }

    @Test
    @DisplayName("预设：GET 按类别分组，POST 落库并回显，DELETE 报是否删掉")
    void presetRoundTrip() throws Exception {
        JsonNode empty = json(send("GET", "/api/presets", null));
        assertEquals(0, empty.get("objectives").size());
        assertEquals(0, empty.get("rewards").size());

        JsonNode saved = json(send("POST", "/api/presets/objectives", """
                {"id": "p1", "type": "break_block", "name": "挖矿", "properties": {"target": "STONE"}}
                """));
        assertTrue(saved.get("ok").asBoolean());
        assertEquals("p1", saved.get("preset").get("id").asText());
        assertEquals("break_block", saved.get("preset").get("type").asText());
        assertEquals("挖矿", saved.get("preset").get("name").asText());
        assertEquals("STONE", saved.get("preset").get("properties").get("target").asText(),
                "属性要原样存回，否则套用预设时配置是空的");

        // 未指定 id 时后端生成一个：预设必须可被删除与覆盖
        JsonNode generated = json(send("POST", "/api/presets/rewards", """
                {"type": "exp", "properties": {"amount": 50}}
                """));
        String generatedId = generated.get("preset").get("id").asText();
        assertFalse(generatedId.isBlank());

        JsonNode grouped = json(send("GET", "/api/presets", null));
        assertEquals(1, grouped.get("objectives").size(), "URL 路径里的 objectives 决定分组");
        assertEquals(1, grouped.get("rewards").size(), "rewards 类别同样按路径分组");
        assertEquals("exp", grouped.get("rewards").get(0).get("type").asText());

        JsonNode deleted = json(send("DELETE", "/api/presets/objectives/p1", null));
        assertTrue(deleted.get("ok").asBoolean());
        assertEquals("p1", deleted.get("id").asText());
        assertFalse(json(send("DELETE", "/api/presets/objectives/p1", null)).get("ok").asBoolean());

        HttpResponse<String> noType = send("POST", "/api/presets/objectives", "{\"name\":\"缺 type\"}");
        assertTrue(error(noType, 400).contains("type"));
    }

    @Test
    @DisplayName("GET /api/catalog：原样透出素材目录（形状由前端依赖）")
    void catalogIsPassedThrough() throws Exception {
        Map<String, Object> built = new LinkedHashMap<>();
        built.put("materials", List.of(Map.of("id", "STONE", "en", "Stone", "zh", "石头")));
        built.put("entities", List.of());
        built.put("hasChinese", true);
        when(catalog.build()).thenReturn(built);

        JsonNode response = json(send("GET", "/api/catalog", null));

        assertEquals("STONE", response.get("materials").get(0).get("id").asText());
        assertTrue(response.get("hasChinese").asBoolean());
    }

    // ------------------------------------------------------------------
    // 重载
    // ------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/reload：重新读库，新任务随即出现在列表里")
    void reloadRereadsStorage() throws Exception {
        // 只落库、不进注册表：模拟「库里有、内存里还没有」的重载前状态
        services.storeOnly(quest("from_disk"));
        assertEquals(0, services.quests().all().size());

        JsonNode reloaded = json(send("POST", "/api/reload", null));

        assertTrue(reloaded.get("ok").asBoolean());
        assertEquals(1, reloaded.get("quests").asInt());
        assertEquals(1, json(send("GET", "/api/quests", null)).size());
    }

    @Test
    @DisplayName("GET /api/stats：计数、存储描述与分类")
    void statsAggregatesCounts() throws Exception {
        services.seed(quest("normal", QuestType.NORMAL, "mining"),
                quest("daily", QuestType.DAILY, "daily"));

        JsonNode stats = json(send("GET", "/api/stats", null));

        assertEquals(2, stats.get("quests").asInt());
        assertEquals(1, stats.get("periodicQuests").asInt(), "周期任务数 = 四种周期里已启用的任务");
        assertEquals(1, stats.get("questsByType").get("DAILY").asInt());
        assertEquals(1, stats.get("questsByType").get("NORMAL").asInt());
        assertEquals(0, stats.get("questsByType").get("WEEKLY").asInt());
        assertEquals(BuiltIns.objectives().size(), stats.get("objectives").asInt(),
                "数量取自注册表，硬编码会随新增类型漂移");
        assertEquals(BuiltIns.rewards().size(), stats.get("rewards").asInt());
        assertEquals(0, stats.get("players").asInt());
        assertEquals(FakeEditorServices.STORAGE_DESCRIPTION, stats.get("storage").asText());
        List<String> categories = new ArrayList<>();
        stats.get("categories").forEach(node -> categories.add(node.asText()));
        assertEquals(List.of("daily", "mining"), categories);
    }

    // ------------------------------------------------------------------
    // 访问令牌：它在 EditorServer 上（服务本身的事），因此这里真起一个 EditorServer
    // ------------------------------------------------------------------

    @Test
    @DisplayName("配置了 editor.token 时：缺令牌 401，带对令牌放行")
    void tokenGateRejectsUnauthorizedRequests() throws Exception {
        PlayerTaskX plugin = mock(PlayerTaskX.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("EditorApiTest"));
        PluginConfig config = mock(PluginConfig.class);
        when(config.getEditorToken()).thenReturn(TOKEN);
        when(plugin.config()).thenReturn(config);
        when(plugin.messages()).thenReturn(mock(MessageService.class));

        // 业务层与服务层分开拿：EditorServer 用插件实例做端口/令牌/资源，用 EditorServices 做 /api/*
        EditorServer server = new EditorServer(plugin, services);
        // start() 会向控制台发一条「编辑器已启动」的消息，这要 Bukkit 单例；而单测里装不了
        // （详见 FakeEditorServices），于是只在启动这一瞬间把它静态桩掉。静态桩是线程局部的，
        // 处理请求的线程不受影响——那条路径本来也不碰 Bukkit。
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getConsoleSender).thenReturn(null);
            server.start(freePort());
        }
        try {
            assertTrue(server.port() > 0, "编辑器应已开始监听，端口: " + server.port());
            String origin = "http://127.0.0.1:" + server.port();

            HttpResponse<String> rejected = http.send(
                    HttpRequest.newBuilder(URI.create(origin + "/api/quests")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(401, rejected.statusCode(), "没有令牌就不该看到任何任务数据");
            assertTrue(rejected.body().contains("令牌"), "401 要说清缺什么，实际: " + rejected.body());

            HttpResponse<String> allowed = http.send(
                    HttpRequest.newBuilder(URI.create(origin + "/api/quests"))
                            .header("X-Editor-Token", TOKEN).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, allowed.statusCode(), "带对令牌必须放行，否则前端整个用不了");
        } finally {
            server.stop();
        }
    }

    // ------------------------------------------------------------------
    // 请求辅助
    // ------------------------------------------------------------------

    /** 发一个请求；{@code body} 为 null 表示不带请求体（GET / DELETE）。 */
    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path));
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json; charset=utf-8");
            request.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** 断言 200 并解析响应体；状态码不对时把响应体带进失败信息，省一次复现。 */
    private JsonNode json(HttpResponse<String> response) throws Exception {
        assertEquals(200, response.statusCode(), () -> "响应体: " + response.body());
        return JSON.readTree(response.body());
    }

    /**
     * 断言错误响应的状态码，并返回 {@code error} 文案。
     * <p>
     * 与 {@link #json} 分开而不是复用：4xx 的响应体恰恰是要检查的对象，
     * 混用会写出「先断言 200，再看 400 的文案」这种自相矛盾的测试。
     */
    private String error(HttpResponse<String> response, int expectedStatus) throws Exception {
        assertEquals(expectedStatus, response.statusCode(), () -> "响应体: " + response.body());
        return JSON.readTree(response.body()).get("error").asText();
    }

    /** 收字节的请求：导出 zip 时响应体不是文本，必须按字节收。 */
    private HttpResponse<byte[]> sendBytes(String method, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path));
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    /** 发字节请求体、按文本收响应：导入 zip 走这条。 */
    private HttpResponse<String> sendBytesAs(String method, String path, byte[] body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/zip")
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** 把响应体当 zip 解开：文件名 → 内容。 */
    private static Map<String, String> unzip(byte[] archive) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                files.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    private static byte[] zipOf(Map<String, String> files) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                out.putNextEntry(new ZipEntry(file.getKey()));
                out.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return buffer.toByteArray();
    }

    private static JsonNode itemById(JsonNode array, String id) {
        for (JsonNode node : array) {
            if (id.equals(node.get("id").asText())) {
                return node;
            }
        }
        throw new AssertionError("列表里没有任务 " + id + ": " + array);
    }

    private static JsonNode fieldOf(JsonNode typeSchema, String key) {
        for (JsonNode field : typeSchema.get("fields")) {
            if (key.equals(field.get("key").asText())) {
                return field;
            }
        }
        throw new AssertionError("类型 " + typeSchema.get("id") + " 缺字段 " + key);
    }

    /** 字段声明的值域（小写 id 列表）——编辑器据此决定选择器里列什么。 */
    private static List<String> kindsOf(JsonNode field) {
        List<String> kinds = new ArrayList<>();
        for (JsonNode kind : field.get("kinds")) {
            kinds.add(kind.asText());
        }
        return kinds;
    }

    private static String problemsText(JsonNode quest) {
        List<String> problems = new ArrayList<>();
        quest.get("problems").forEach(node -> problems.add(node.asText()));
        return String.join(" | ", problems);
    }

    /**
     * 取一个当前空闲的端口。
     * <p>
     * 这里不能传 0 让系统分配：{@link EditorServer#port()} 报的是它自己记下的绑定端口，
     * 传 0 会得到 0，测试就无从拼出请求地址。探测式取端口有个极小的竞态窗口，
     * 但即使真的被抢占，EditorServer 会自己 +1 重试并如实报出实际端口，
     * {@code port() > 0} 的断言仍然成立。
     */
    private static int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    // ------------------------------------------------------------------
    // 假宿主与内存仓储
    // ------------------------------------------------------------------

    private static Quest quest(String id) {
        return quest(id, QuestType.NORMAL, "");
    }

    private static Quest quest(String id, QuestType type, String category) {
        return new Quest(id, "任务 " + id, List.of(), "PAPER", category, type,
                List.of(QuestObjective.of("break_block", Map.of("target", "STONE", "amount", 1))),
                List.of(QuestReward.of("exp", Map.of("amount", 10))), 0.0, true);
    }

    /** 引用了不存在的目标与奖励类型：校验必须报出来，编辑器据此标红。 */
    private static Quest brokenQuest(String id) {
        return new Quest(id, "坏任务", List.of(), "PAPER", "", QuestType.NORMAL,
                List.of(QuestObjective.of("no_such_objective", Map.of("amount", 1))),
                List.of(QuestReward.of("no_such_reward", Map.of("amount", 1))), 0.0, true);
    }

    /**
     * {@link EditorServices} 的内存假身：把「插件已启用 + 服务端在跑」压缩成几个内存对象。
     * <p>
     * 两个类型注册表与任务注册表用真实实现——它们本来就不碰 Bukkit，用假的等于白扔覆盖率；
     * 三个仓储用内存实现，任务维护用真实的 {@link QuestAdminService}。
     */
    private static final class FakeEditorServices implements EditorServices {

        static final String STORAGE_DESCRIPTION = "内存假存储";

        private final QuestRegistryImpl quests = new QuestRegistryImpl();
        private final ObjectiveRegistryImpl objectiveTypes = new ObjectiveRegistryImpl();
        private final RewardRegistryImpl rewardTypes = new RewardRegistryImpl();
        private final FakeQuestRepository stored = new FakeQuestRepository();
        private final FakePresetRepository presets = new FakePresetRepository();
        private final EmptyPlayerQuestRepository playerQuests = new EmptyPlayerQuestRepository();
        private final QuestAdminService questAdmin;

        FakeEditorServices(File dataFolder) {
            BuiltIns.objectives().forEach(objectiveTypes::register);
            BuiltIns.rewards().forEach(rewardTypes::register);

            // 用真实 QuestAdminService：保存/删除/校验的语义（三处同步、未知类型上报）
            // 不该在测试里再抄一遍——抄出来的假身一旦与实现漂移，测试会一边倒地绿。
            QuestAdminService real = new QuestAdminService(stored, quests, objectiveTypes,
                    new RewardService(quests, rewardTypes, playerQuests),
                    mock(ProgressService.class), List::of,
                    id -> presets.findById(id).orElse(null));
            // 只替换 reload()：它会经 Bukkit.getLogger() 写日志，而单测里装不了 Server 单例
            // ——Bukkit.setServer 只允许调用一次，已被 QuestAdminServiceTest 占用，
            // 再调一次会直接抛异常并连带把那个测试类弄挂。
            questAdmin = spy(real);
            doAnswer(invocation -> {
                quests.replaceAll(stored.findAll());
                return null;
            }).when(questAdmin).reload();
        }

        /** 预置任务：走真实保存路径，保证「存储 + 注册表」两边一致。 */
        void seed(Quest... quests) {
            for (Quest quest : quests) {
                questAdmin.save(quest);
            }
        }

        /** 只写存储、不进注册表：用于验证重载确实重新读库。 */
        void storeOnly(Quest quest) {
            stored.save(quest);
        }

        @Override
        public QuestRegistryImpl quests() {
            return quests;
        }

        @Override
        public QuestAdminService questAdmin() {
            return questAdmin;
        }

        /** 编辑器问「这条是不是只读定义」用；测试的假仓储全是可写的数据库侧。 */
        @Override
        public QuestRepository questDefinitions() {
            return stored;
        }

        /** 把某个任务标成「来自 YAML 文件的只读定义」。 */
        void markReadOnly(String id) {
            stored.markReadOnly(id);
        }

        @Override
        public PresetRepository presets() {
            return presets;
        }

        @Override
        public PlayerQuestRepository playerQuestRepository() {
            return playerQuests;
        }

        @Override
        public ObjectiveRegistryImpl objectiveTypes() {
            return objectiveTypes;
        }

        @Override
        public RewardRegistryImpl rewardTypes() {
            return rewardTypes;
        }

        @Override
        public String describeStorage() {
            return STORAGE_DESCRIPTION;
        }
    }

    /** 内存版任务定义仓储（定义侧不需要事务与索引查询）。 */
    private static final class FakeQuestRepository implements QuestRepository {

        private final Map<String, Quest> data = new LinkedHashMap<>();
        /** 模拟「来自 YAML 文件的只读定义」：写入必须被拒（与合并仓储同行为）。 */
        private final Set<String> readOnly = new LinkedHashSet<>();

        void markReadOnly(String id) {
            readOnly.add(id);
        }

        @Override
        public List<Quest> findAll() {
            return new ArrayList<>(data.values());
        }

        @Override
        public Optional<Quest> findById(String id) {
            return Optional.ofNullable(data.get(id));
        }

        @Override
        public boolean isReadOnly(String id) {
            return readOnly.contains(id);
        }

        @Override
        public void save(Quest quest) {
            if (quest != null && readOnly.contains(quest.id())) {
                throw new DefinitionReadOnlyException("任务 " + quest.id() + " 由 YAML 文件定义，只读");
            }
            data.put(quest.id(), quest);
        }

        @Override
        public boolean delete(String id) {
            if (readOnly.contains(id)) {
                throw new DefinitionReadOnlyException("任务 " + id + " 由 YAML 文件定义，只读");
            }
            return data.remove(id) != null;
        }

        @Override
        public long count() {
            return data.size();
        }
    }

    private static final class FakePresetRepository implements PresetRepository {

        private final Map<String, Preset> data = new LinkedHashMap<>();

        @Override
        public List<Preset> findAll() {
            return new ArrayList<>(data.values());
        }

        @Override
        public Optional<Preset> findById(String id) {
            return Optional.ofNullable(data.get(id));
        }

        @Override
        public void save(Preset preset) {
            data.put(preset.id(), preset);
        }

        @Override
        public boolean delete(String id) {
            return data.remove(id) != null;
        }

        @Override
        public long count() {
            return data.size();
        }

        @Override
        public void seedIfEmpty(List<Preset> defaults) {
            if (data.isEmpty()) {
                defaults.forEach(this::save);
            }
        }
    }

    /**
     * 空的玩家仓储：{@code /api/players} 不在本文件的覆盖范围内（见类注释），
     * 只有 {@code /api/stats} 会向它要一个玩家数，因此不做内存实现——留着没人读的数据结构
     * 只会让人误以为它被测过。
     */
    private static final class EmptyPlayerQuestRepository implements PlayerQuestRepository {

        @Override
        public List<PlayerQuest> findByPlayer(UUID playerId) {
            return List.of();
        }

        @Override
        public List<PlayerQuest> findActiveByPlayer(UUID playerId) {
            return List.of();
        }

        @Override
        public Optional<PlayerQuest> find(UUID playerId, String questId) {
            return Optional.empty();
        }

        @Override
        public void save(PlayerQuest playerQuest) {
        }

        @Override
        public void transaction(Runnable work) {
            work.run();
        }

        @Override
        public void delete(UUID playerId, String questId) {
        }

        @Override
        public void deleteByPlayerAndType(UUID playerId, QuestType type) {
        }

        @Override
        public long countPlayers() {
            return 0;
        }

        @Override
        public List<UUID> distinctPlayerIds() {
            return List.of();
        }

        @Override
        public com.playerPlugin.playerTaskX.core.storage.PlayerQuestRepository.PeriodState findPeriodState(
                UUID playerId, QuestType type) {
            return null;
        }

        @Override
        public void savePeriodState(UUID playerId, QuestType type, String period, int refreshCount, long assignedAt) {
        }
    }
}
