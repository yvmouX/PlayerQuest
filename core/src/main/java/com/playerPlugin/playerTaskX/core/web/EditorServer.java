package com.playerPlugin.playerTaskX.core.web;

import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.core.integration.CustomFishingHook;
import cn.yvmou.ylib.message.MessageService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * 内置网页编辑器：提供 REST 接口与静态前端资源。
 *
 * <h2>为什么用 Javalin 而不是自己写 HTTP</h2>
 * 编辑器需要静态资源、路由、JSON 三件事，Javalin 一并解决。
 *
 * <h2>职责边界</h2>
 * 本类只管<b>服务本身</b>：端口、启停、静态资源、访问令牌。
 * {@code /api/*} 的业务处理在 {@link EditorApi}——把两者混在一起时，
 * 「端口被占用要 +1 重试」这种运维逻辑和「任务保存后要重建索引」这种业务逻辑
 * 会挤在同一个文件里，改哪一头都要先把整个文件读一遍。
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
    private final LangFileStore langFiles;
    private Javalin app;
    private int port = -1;

    public EditorServer(PlayerTaskX plugin) {
        this.plugin = plugin;
        // 译名来源：英文读服务端自带的语言文件，中文必要时下载（见 LangFileStore）
        this.langFiles = new LangFileStore(plugin);
    }

    /**
     * 尽早准备译名数据（英文同步、中文后台下载）。
     * <p>
     * 与 {@link #start(int)} 分开：准备译名不依赖端口是否可用——即使编辑器没启动，
     * 下载好的中文语言文件也留在磁盘上。
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
                MessageService messages = plugin.messages();
                messages.send(org.bukkit.Bukkit.getConsoleSender(), "editor.started", "127.0.0.1:" + port);
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

        // ---- 业务接口 ----
        // 素材目录要把三个来源都带上：原版材质/实体（枚举）、MythicMobs 自定义怪、
        // ItemsAdder / CraftEngine 的自定义物品与方块。少传一个，选择器里就少一整类东西，
        // 而且只在「装了那个插件」的服务器上才看得出来（构造器因此不提供省略参数的版本）
        new EditorApi(plugin, new MaterialCatalog(langFiles, plugin.mythicMobs(), plugin.customContent(),
                CustomFishingHook::loot)).register(app);

        // 令牌被拒时 checkToken 已写好响应体，这里只需保持 401
        app.exception(TokenRejected.class, (e, ctx) -> ctx.status(401));
    }

    private void checkToken(Context ctx) {
        String token = plugin.config().getEditorToken();
        if (token == null || token.isBlank()) {
            return;
        }
        if (!token.equals(ctx.header("X-Editor-Token"))) {
            ctx.status(401).result(EditorApi.errorJson("无效或缺失的访问令牌（请求头 X-Editor-Token）"));
            throw new TokenRejected();
        }
    }

    /**
     * 用于中断请求的内部控制流异常。
     * <p>
     * 401 的状态码与响应体都由 {@link #checkToken} 一次写完，这里只负责让 Javalin
     * 停止执行后续处理器——异常本身不再是「错误的载体」，避免同一次拒绝的状态码与文案
     * 被拆到两个类里，改一处忘另一处。
     */
    private static final class TokenRejected extends RuntimeException {
    }

    private void serveIndex(Context ctx) {
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

    private static String contentTypeOf(String path) {
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
        if (external.isFile()) {
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
}
