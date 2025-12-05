package com.playerPlugin.playerTaskX.web;

import cn.yvmou.ylib.tools.LoggerTools;
import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.gson.JsonParseException;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.Enum.PTXTaskType;
import com.playerPlugin.playerTaskX.model.Task.TaskDefinition;
import com.playerPlugin.playerTaskX.storage.TaskRepository;
import io.javalin.Javalin;
import io.javalin.http.ContentType;
import io.javalin.json.JsonMapper;
import io.javalin.plugin.bundled.CorsPluginConfig;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EditorServer {
    private final PlayerTaskX plugin;
    private final TaskRepository taskRepo;
    private final LoggerTools log;
    private final ObjectMapper objectMapper;
    private Javalin app;

    public EditorServer(PlayerTaskX plugin, TaskRepository taskRepo, LoggerTools log) {
        this.plugin = plugin;
        this.taskRepo = taskRepo;
        this.log = log;

        // Configure the Jackson
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    public void start() {
        // 静态文件配置
        app = Javalin.create(config -> {
            // 静态文件
            config.staticFiles.add("/web");

            // 启用CORS
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(CorsPluginConfig.CorsRule::anyHost); //TODO 允许任意主机（Any Host） 发起跨域请求。 仅开发时使用，生产环境应限制
            });

            // 调试日志
            config.bundledPlugins.enableDevLogging();

            // 使用 fastjson2 作为 json 映射器
            config.http.defaultContentType = ContentType.JSON;
            config.jsonMapper(new JsonMapper() {
                @NotNull
                @Override
                public String toJsonString(@NotNull Object obj, @NotNull Type type) {
                    return JSON.toJSONString(obj);
                };

                @NotNull
                @Override
                public InputStream toJsonStream(@NotNull Object obj, @NotNull Type type) {
                    byte[] jsonBytes = JSON.toJSONBytes(obj);
                    return new ByteArrayInputStream(jsonBytes);
                }

                @NotNull
                @Override
                public <T> T fromJsonString(@NotNull String json, @NotNull Type targetType) {
                    return JSON.parseObject(json, targetType);
                }

                @NotNull
                @Override
                public <T> T fromJsonStream(@NotNull InputStream stream, @NotNull Type targetType) {
                    return JSON.parseObject(stream, targetType);
                }
            });
        }).start(1145);

        // 全局异常处理
        app.exception(Exception.class, (e, ctx) -> {
            if (e instanceof NumberFormatException || e instanceof JsonParseException) {
                ctx.status(4001).json(Map.of(
                        "code", 4001,
                        "msg", "请求参数错误"
                ));
            } else if (e instanceof NotFoundTaskDefinitionException) {
                ctx.status(4002).json(Map.of(
                        "code", 4002,
                        "msg", "任务不存在"
                ));
            } else if (e instanceof UnauthorizedException) {
                ctx.status(114514).json(Map.of(
                        "code", 114514,
                        "msg", "未授权"
                ));
            } else {
                ctx.status(500).json(Map.of(
                        "code", 500,
                        "msg", "服务器内部错误"
                ));
            }
        });

        // 404处理
        app.error(404, ctx -> {
            ctx.status(404).json(Map.of(
                    "code", 404,
                    "msg", "请求的接口不存在"
            ));
        });

        // 令牌验证
        app.before("/api/*", ctx -> {
            // TODO token 开始包含 Bearer
            String token = ctx.header("Authorization");

            if (token == null || !token.startsWith("Bearer ")) {
                throw new UnauthorizedException();
            }
        });

        // 服务器状态检测
        app.get("/health", ctx -> {
            ctx.json(Map.of(
                    "status", "ok",
                    "plugin", plugin.getName(),
                    "version", plugin.getDescription().getVersion(),
                    "players", plugin.getServer().getOnlinePlayers().size()
            ));
        });

        // API路由
        setupApiRoutes();

        // WebSocket路由（用于实时更新）
        // setupWebSocketRoutes();  // 暂时注释掉

        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }


    private void setupApiRoutes() {
        // ***************** GET *****************
        // 获取任务列表
        app.get("/api/taskDefList", ctx -> {
            List<TaskDefinition> taskDefList = taskRepo.loadAll();
            ctx.json(Map.of(
                    "data", taskDefList
            ));
        });

        // 获取单个任务
        app.get("/api/taskDef/{id}", ctx -> {
            String questId = ctx.pathParam("id");
            TaskDefinition taskDef = taskRepo.findById(questId).orElse(null);

            if (taskDef == null) throw new NotFoundTaskDefinitionException();

            ctx.json(Map.of("data", taskDef));
        });

//        // ***************** POST *****************
//        // 创建任务
//        app.post("/api/quests", ctx -> {
//            TaskDefinition taskDef = ctx.bodyAsClass(TaskDefinition.class);
//
//            String taskId = UUID.randomUUID().toString(); // TODO 通过网页生成的任务ID为随机值
//            PTXTaskType type = PTXTaskType.fromString(ctx.queryParam("type"));
//            String name = ctx.queryParam("name");
//            // TODO
//
//
//            Quest quest = ctx.bodyAsClass(Quest.class);
//            new TaskDefinition(taskId, type, name, tatgets, trigger);
//
//            boolean success = plugin.getQuestManager().createQuest(quest);
//
//            if (success) {
//                ctx.status(201).json(Map.of(
//                        "success", true,
//                        "data", quest,
//                        "message", "任务创建成功"
//                ));
//            } else {
//                ctx.status(400).json(Map.of(
//                        "success", false,
//                        "message", "任务创建失败"
//                ));
//            }
//        });
//
//        // 更新任务
//        app.put("/api/quests/{id}", ctx -> {
//            String questId = ctx.pathParam("id");
//            Quest updates = ctx.bodyAsClass(Quest.class);
//            updates.setId(questId);
//            updates.setModifiedAt(System.currentTimeMillis());
//
//            boolean success = plugin.getQuestManager().updateQuest(updates);
//
//            ctx.json(Map.of(
//                    "success", success,
//                    "message", success ? "任务更新成功" : "任务更新失败"
//            ));
//        });
//
//        // 删除任务
//        app.delete("/api/quests/{id}", ctx -> {
//            String questId = ctx.pathParam("id");
//            boolean success = plugin.getQuestManager().deleteQuest(questId);
//
//            ctx.json(Map.of(
//                    "success", success,
//                    "message", success ? "任务删除成功" : "任务删除失败"
//            ));
//        });
//
//        // ========== 玩家进度 API ==========
//
//        // 批量获取玩家进度
//        app.get("/api/players/progress", ctx -> {
//            String questId = ctx.queryParam("questId");
//            int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(1);
//            int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(50);
//            String status = ctx.queryParam("status");
//
//            Map<String, Object> result = plugin.getProgressManager()
//                    .getPlayerProgress(questId, page, size, status);
//            ctx.json(result);
//        });
//
//        // 批量更新玩家进度
//        app.post("/api/players/progress/batch", ctx -> {
//            BatchProgressRequest request = ctx.bodyAsClass(BatchProgressRequest.class);
//
//            // 异步处理，避免阻塞网络线程
//            CompletableFuture.runAsync(() -> {
//                plugin.getProgressManager().batchUpdateProgress(
//                        request.getPlayerIds(),
//                        request.getQuestId(),
//                        request.getProgress(),
//                        request.getCompleted(),
//                        request.getReset()
//                );
//            });
//
//            ctx.json(Map.of(
//                    "success", true,
//                    "message", "进度更新已提交处理"
//            ));
//        });
//
//        // 重置玩家进度
//        app.post("/api/players/progress/reset", ctx -> {
//            ResetProgressRequest request = ctx.bodyAsClass(ResetProgressRequest.class);
//
//            int count = plugin.getProgressManager().resetProgress(
//                    request.getQuestId(),
//                    request.getPlayerIds()
//            );
//
//            ctx.json(Map.of(
//                    "success", true,
//                    "count", count,
//                    "message", "已重置 " + count + " 个玩家的进度"
//            ));
//        });
//
//        // ========== 统计数据 API ==========
//
//        // 获取任务统计
//        app.get("/api/stats/quests", ctx -> {
//            String questId = ctx.queryParam("questId");
//            String period = ctx.queryParam("period", "7d"); // 7天
//
//            Statistics stats = plugin.getQuestManager().getQuestStatistics(questId, period);
//            ctx.json(Map.of("data", stats));
//        });
//
//        // 获取玩家统计
//        app.get("/api/stats/players", ctx -> {
//            String period = ctx.queryParam("period", "30d");
//            Map<String, Object> stats = plugin.getProgressManager().getPlayerStatistics(period);
//            ctx.json(stats);
//        });
//
//        // 获取奖励统计
//        app.get("/api/stats/rewards", ctx -> {
//            String startDate = ctx.queryParam("startDate");
//            String endDate = ctx.queryParam("endDate");
//
//            Map<String, Object> rewards = plugin.getQuestManager()
//                    .getRewardStatistics(startDate, endDate);
//            ctx.json(rewards);
//        });
//
//        // ========== 系统 API ==========
//
//        // 获取语言包
//        app.get("/api/locales/{lang}", ctx -> {
//            String lang = ctx.pathParam("lang");
//            Map<String, String> locale = plugin.getConfigManager().getLocale(lang);
//            ctx.json(locale);
//        });
//
//        // 重新加载配置
//        app.post("/api/system/reload", ctx -> {
//            plugin.reloadConfig();
//            plugin.getQuestManager().reload();
//            plugin.getConfigManager().reloadLocales();
//
//            ctx.json(Map.of(
//                    "success", true,
//                    "message", "配置重载成功"
//            ));
//        });
//
//        // 导出数据
//        app.get("/api/system/export", ctx -> {
//            String type = ctx.queryParam("type", "json");
//            String data = plugin.getQuestManager().exportData(type);
//
//            ctx.header("Content-Disposition", "attachment; filename=quests_export." + type);
//            ctx.result(data);
//        });
//    }

//    private void setupWebSocketRoutes() {
//        app.ws("/ws/editor", ws -> {
//            ws.onConnect(session -> {
//                String token = session.queryParam("token").stream().findFirst().orElse(null);
//                if (token != null && plugin.validateToken(token)) {
//                    UUID playerId = plugin.getTokenManager().getPlayerIdByToken(token);
//                    session.attribute("playerId", playerId);
//
//                    log.info("WebSocket连接建立: " + playerId);
//                    session.send(Map.of("type", "connected", "message", "Connected to editor"));
//                } else {
//                    session.close(1008, "Invalid token"); // 令牌无效
//                }
//            });
//
//            ws.onMessage((session, message) -> {
//                // 处理实时编辑消息
//                // 例如：多人同时编辑时的协同
//                UUID playerId = session.attribute("playerId");
//                if (playerId != null) {
//                    // 广播给其他连接的用户
//                    ws.getConnectedSessions().forEach(s -> {
//                        if (!s.equals(session)) {
//                            s.send(Map.of(
//                                    "type", "edit",
//                                    "playerId", playerId.toString(),
//                                    "data", message
//                            ));
//                        }
//                    });
//                }
//            });
//
//            ws.onClose((session, statusCode, reason) -> {
//                UUID playerId = session.attribute("playerId");
//                if (playerId != null) {
//                    log.info("WebSocket连接关闭: " + playerId);
//                }
//            });
//
//            ws.onError((session, throwable) -> {
//                log.warning("WebSocket错误: " + throwable.getMessage());
//            });
//        });
    }

    public void stop() {
        if (app != null) {
            app.stop();
            log.info("编辑器服务器已停止");
        }
    }

}
