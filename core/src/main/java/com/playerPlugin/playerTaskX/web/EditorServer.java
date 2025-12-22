package com.playerPlugin.playerTaskX.web;

import cn.yvmou.ylib.api.services.LoggerService;
import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.gson.JsonParseException;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.TaskAPI;
import com.playerPlugin.playerTaskX.api.storage.TaskRepository;
import com.playerPlugin.playerTaskX.exception.NotFoundTaskDefinitionException;
import com.playerPlugin.playerTaskX.exception.UnauthorizedException;
import io.javalin.Javalin;
import io.javalin.http.ContentType;
import io.javalin.json.JsonMapper;
import io.javalin.openapi.JsonSchemaLoader;
import io.javalin.openapi.JsonSchemaResource;
import io.javalin.openapi.plugin.OpenApiPlugin;
import io.javalin.openapi.plugin.redoc.ReDocPlugin;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;
import io.javalin.plugin.bundled.CorsPluginConfig;
import io.javalin.security.RouteRole;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Map;

public class EditorServer {
    private final PlayerTaskX plugin;
    private final TaskRepository taskRepo;
    private final TaskAPI taskAPI;
    private final LoggerService log;
    private Javalin app;
    public EditorServer(PlayerTaskX plugin, TaskRepository taskRepo, TaskAPI taskAPI, LoggerService log) {
        this.plugin = plugin;
        this.taskRepo = taskRepo;
        this.taskAPI = taskAPI;
        this.log = log;
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
                }

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

            // doc
            // config.routing.contextPath = "/custom";
            String deprecatedDocsPath = "/api/openapi.json"; // by default it's /openapi
            config.registerPlugin(new OpenApiPlugin(openApiConfig -> {
                openApiConfig
                        .withDocumentationPath(deprecatedDocsPath)
                        .withRoles(Rules.ANONYMOUS) // TODO 添加权限控制
                        .withDefinitionConfiguration((version, openApiDefinition) -> {
                            openApiDefinition
                                    .withInfo(openApiInfo ->
                                            openApiInfo
                                                    .description("PlayerTaskX 任务管理系统")
                                                    .termsOfService("https://github.com/Findoutsider/PlayerTaskX")
                                                    .contact("API support", "https://github.com/Findoutsider/PlayerTaskX", "yvmoux@gmail.com")
                                                    .license("NONE", "https://github.com/Findoutsider/PlayerTaskX", "NONE")
                                    )
                                    .withServer(openApiServer ->
                                            openApiServer
                                                    .description("Server description goes here")
                                                    .url("http://localhost:{port}{basePath}/" + version + "/")
                                                    .variable("port", "Server's port", "8080", "8080", "7070")
                                                    .variable("basePath", "Base path of the server", "", "", "/v1")
                                    )
                                    // Based on official example: https://swagger.io/docs/specification/authentication/oauth2/
                                    .withSecurity(openApiSecurity ->
                                            openApiSecurity
                                                    .withBasicAuth()
                                                    .withBearerAuth()
                                                    .withApiKeyAuth("ApiKeyAuth", "X-Api-Key")
                                                    .withCookieAuth("CookieAuth", "JSESSIONID")
                                                    .withOpenID("OpenID", "https://example.com/.well-known/openid-configuration")
                                                    .withOAuth2("OAuth2", "This API uses OAuth 2 with the implicit grant flow.", oauth2 ->
                                                            oauth2
                                                                    .withClientCredentials("https://api.example.com/credentials/authorize")
                                                                    .withImplicitFlow("https://api.example.com/oauth2/authorize", flow ->
                                                                            flow
                                                                                    .withScope("read_pets", "read your pets")
                                                                                    .withScope("write_pets", "modify pets in your account")
                                                                    )
                                                    )
                                                    .withGlobalSecurity("OAuth2", globalSecurity ->
                                                            globalSecurity
                                                                    .withScope("write_pets")
                                                                    .withScope("read_pets")
                                                    )
                                                    .withGlobalSecurity("BearerAuth")
                                    )
                                    .withDefinitionProcessor(content -> { // you can add whatever you want to this document using your favourite json api
                                        content.set("test", new TextNode("Value"));
                                        return content.toPrettyString();
                                    });
                        });
            }));

            config.registerPlugin(new SwaggerPlugin(swaggerConfiguration -> {
                swaggerConfiguration.setDocumentationPath(deprecatedDocsPath);
            }));

            config.registerPlugin(new ReDocPlugin(reDocConfiguration -> {
                reDocConfiguration.setDocumentationPath(deprecatedDocsPath);
            }));

            for (JsonSchemaResource generatedJsonSchema : new JsonSchemaLoader().loadGeneratedSchemes()) {
                System.out.println(generatedJsonSchema.getName());
                System.out.println(generatedJsonSchema.getContentAsString());
            }

        }).start(2222);

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
//            // TODO token 开始包含 Bearer
//            String token = ctx.header("Authorization");
//
//            if (token == null || !token.startsWith("Bearer ")) {
//                throw new UnauthorizedException();
//            }
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
        new ApiRoutes().setupApiRoutes(app, taskRepo, taskAPI);

        // WebSocket路由（用于实时更新）
        // setupWebSocketRoutes();  // 暂时注释掉

        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }



    public void stop() {
        if (app != null) {
            app.stop();
            log.info("编辑器服务器已停止");
        }
    }

    enum Rules implements RouteRole {
        ANONYMOUS,
        USER,
    }

}
