package com.playerPlugin.playerTaskX.core.web;

import com.playerPlugin.playerTaskX.PlayerTaskX;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 物品/方块/实体译名的来源。
 *
 * <h2>英文名：不下载，直接读服务端自带的</h2>
 * 服务端 jar 里就有 {@code assets/minecraft/lang/en_us.json}，直接读它有两个好处：
 * 版本天然对齐（服务端是什么版本就给什么名字），且零网络依赖。
 * <p>
 * <b>插件不打包这份文件</b>：曾经为了让开发期离线可跑而复制进插件资源，
 * 结果是它永远停在复制那天——服务端升到新版本后，新方块会显示成推导出来的枚举名。
 * 实测 26.1.2 服务端 jar 内的该文件与当时那份副本字节相同、1.21.11 的则不同，
 * 正是这种漂移。
 *
 * <h2>中文名：服务端没有，需要去取</h2>
 * 中文译名只存在于<b>客户端</b>资源里，服务端 jar 只有 {@code en_us.json}
 * （实测 26.1.2 的服务端 jar 内 lang 文件数为 1）。因此中文必须另行获取，
 * 优先顺序：
 * <ol>
 *   <li>插件目录下的 {@code lang/zh_cn.json}（管理员手动放置，也用于离线服）；</li>
 *   <li>开启下载时，后台从 Mojang 资源 CDN 取一份并缓存到该路径；</li>
 *   <li>都拿不到就用英文名——<b>功能不受影响</b>，只是列表里没有中文。</li>
 * </ol>
 *
 * <h2>为什么下载走「版本清单 → 资源清单 → CDN」三步</h2>
 * 直接写死某个版本的 zh_cn.json 哈希，服务端换版本后就可能取到不匹配的文件。
 * 三步走的每一份都是小文件（清单约 0.5MB），且能自动跟随当前服务端版本。
 *
 * <h2>下载是尽力而为的</h2>
 * 不少服务端在受限网络里，出站请求会失败。因此：失败只记一条日志，
 * 不动用重试风暴、不影响插件启用。首次请求目录时会短暂等待下载完成，
 * 让管理员第一次打开编辑器就能看到中文名，而不是刷新一次才有。
 */
public class LangFileStore {

    /** 语言缓存目录，位于插件数据文件夹下；也是管理员手动放置文件的位置。 */
    private static final String CACHE_FOLDER = "lang";

    private static final String VERSION_MANIFEST =
            "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String RESOURCE_CDN = "https://resources.download.minecraft.net/";

    private static final Pattern VERSION_URL = Pattern.compile(
            "\"id\"\\s*:\\s*\"%s\"[^}]*?\"url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ASSET_INDEX_URL = Pattern.compile(
            "\"assetIndex\"\\s*:\\s*\\{[^}]*?\"url\"\\s*:\\s*\"([^\"]+)\"");
    /** 资源清单里形如 {@code "minecraft/lang/zh_cn.json": {"hash":"…","size":…}}。 */
    private static final Pattern ZH_CN_ENTRY = Pattern.compile(
            "\"minecraft/lang/zh_cn\\.json\"\\s*:\\s*\\{[^}]*?\"hash\"\\s*:\\s*\"([0-9a-f]{40})\"");

    private final PlayerTaskX plugin;
    private final File cacheFile;

    /** 英文名（来自服务端 jar），按小写键索引。 */
    private volatile Map<String, String> english = Map.of();
    /** 中文名；未取得时为空表。 */
    private final AtomicReference<Map<String, String>> chinese = new AtomicReference<>(Map.of());

    /** 后台下载是否已启动，避免重复触发。 */
    private volatile boolean downloadStarted;

    public LangFileStore(PlayerTaskX plugin) {
        this.plugin = plugin;
        this.cacheFile = new File(new File(plugin.getDataFolder(), CACHE_FOLDER), "zh_cn.json");
    }

    /**
     * 准备译名数据。要尽量早调用，让下载在管理员打开编辑器之前就有机会完成。
     * <p>
     * 英文名同步加载（读本地资源，必然成功）；中文名本地有就同步读，没有则视配置后台下载。
     */
    public void initialize() {
        english = loadServerEnglish();

        Map<String, String> local = readLocalChinese();
        if (!local.isEmpty()) {
            chinese.set(local);
            plugin.getLogger().info("已载入中文译名 " + local.size() + " 条: " + cacheFile.getPath());
            return;
        }
        if (plugin.config().isEditorFetchChineseNames()) {
            startDownload();
        } else {
            plugin.getLogger().info("未启用中文译名下载，编辑器图标列表将显示英文名"
                    + "（如需中文，可把 zh_cn.json 放到 " + cacheFile.getParentFile().getPath() + "）");
        }
    }

    /** 英文名表（小写键）。 */
    public Map<String, String> english() {
        return english;
    }

    /**
     * 中文名表（小写键）。若下载仍在进行，短暂等待其结果。
     * <p>
     * 上限 3 秒：编辑器是唯一调用方，等一下能让首次打开就看到中文；
     * 但绝不无限等，网络不通时照常返回英文名。
     */
    public Map<String, String> chinese() {
        if (downloadStarted && chinese.get().isEmpty()) {
            waitForDownload();
        }
        return chinese.get();
    }

    /** 中文名是否可用（供状态展示与日志）。 */
    public boolean hasChinese() {
        return !chinese.get().isEmpty();
    }

    /** 语言缓存文件路径，供报错提示与「手动放置」说明使用。 */
    public File cacheFile() {
        return cacheFile;
    }

    // ------------------------------------------------------------------
    // 加载
    // ------------------------------------------------------------------

    /**
     * 读服务端 jar 内的 {@code assets/minecraft/lang/en_us.json}。
     * <p>
     * 插件类加载器的父级就是加载服务端的那一层，因此这个查询会落到服务端的 jar 上。
     * 取不到只影响英文名的可读性（退回枚举名推导），这里返回空表即可。
     */
    private Map<String, String> loadServerEnglish() {
        try (InputStream stream = LangFileStore.class
                .getResourceAsStream("/assets/minecraft/lang/en_us.json")) {
            if (stream == null) {
                plugin.getLogger().warning("服务端未提供 en_us.json，编辑器图标列表将退回枚举名");
                return Map.of();
            }
            Map<String, String> names = parse(stream);
            plugin.getLogger().info("已载入服务端英文译名 " + names.size() + " 条");
            return names;
        } catch (Exception e) {
            plugin.getLogger().warning("解析服务端 en_us.json 失败，图标列表将退回枚举名: " + e.getMessage());
            return Map.of();
        }
    }

    /** 读本地缓存的中文语言文件（管理员手动放置的那份也在这里）。 */
    private Map<String, String> readLocalChinese() {
        if (!cacheFile.isFile()) {
            return Map.of();
        }
        try (InputStream stream = Files.newInputStream(cacheFile.toPath())) {
            return parse(stream);
        } catch (Exception e) {
            plugin.getLogger().warning("读取 " + cacheFile.getName() + " 失败: " + e.getMessage());
            return Map.of();
        }
    }

    /**
     * 解析语言文件为「小写键 → 译名」。
     * <p>
     * 语言文件是扁平的 JSON 对象，用正则提取足够，不必为一个附属功能引入 JSON 依赖。
     * 收录 {@code item.} / {@code block.} / {@code entity.} 三类键，键统一去掉命名空间前缀。
     * 查表时实体枚举名会先剥掉 {@code MINECRAFT_} 前缀再小写，正好与 {@code entity.minecraft.*}
     * 的键形态一致（实测 157 个实体枚举全部可映射）。
     * <p>
     * 方块与物品可能同名（如 {@code stone}），此时<b>以物品为准</b>——物品译名更贴近
     * 「拿在手里的东西」。
     */
    static Map<String, String> parse(InputStream stream) throws IOException {
        String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> names = new LinkedHashMap<>();
        Matcher matcher = Pattern
                .compile("\"(item|block|entity)\\.minecraft\\.([a-z_0-9]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(text);
        while (matcher.find()) {
            String key = matcher.group(2);
            String value = unescape(matcher.group(3));
            if (!names.containsKey(key) || "item".equals(matcher.group(1))) {
                names.put(key, value);
            }
        }
        return names;
    }

    /** 还原 JSON 字符串里的转义；语言文件里以 {@code \\u0020} 这类空格转义最常见。 */
    private static String unescape(String raw) {
        if (raw.indexOf('\\') < 0) {
            return raw;
        }
        StringBuilder builder = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char current = raw.charAt(i);
            if (current != '\\' || i + 1 >= raw.length()) {
                builder.append(current);
                continue;
            }
            char next = raw.charAt(++i);
            switch (next) {
                case 'u' -> {
                    if (i + 4 < raw.length()) {
                        try {
                            builder.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException e) {
                            builder.append(next);
                        }
                    } else {
                        builder.append(next);
                    }
                }
                case 'n' -> builder.append('\n');
                case 't' -> builder.append('\t');
                case '"' -> builder.append('"');
                case '\\' -> builder.append('\\');
                default -> builder.append(next);
            }
        }
        return builder.toString();
    }

    // ------------------------------------------------------------------
    // 下载
    // ------------------------------------------------------------------

    /** 在后台线程取一份中文语言文件；不阻塞插件启用。 */
    private void startDownload() {
        if (downloadStarted) {
            return;
        }
        downloadStarted = true;
        Thread worker = new Thread(this::download, "PlayerTaskX-lang-download");
        worker.setDaemon(true);
        worker.start();
    }

    /** 有界等待后台下载（最多 3 秒），让首次打开编辑器也能拿到中文名。 */
    private void waitForDownload() {
        long deadline = System.currentTimeMillis() + 3000L;
        while (chinese.get().isEmpty() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void download() {
        try {
            String version = serverVersion();
            if (version == null) {
                plugin.getLogger().warning("无法从服务端确定 Minecraft 版本，跳过中文译名下载");
                return;
            }
            String zhCnUrl = resolveZhCnUrl(version);
            if (zhCnUrl == null) {
                plugin.getLogger().warning("未能从 Mojang 资源清单定位 zh_cn.json（版本 " + version
                        + "），编辑器将显示英文名");
                return;
            }
            String content = HttpText.get(zhCnUrl);
            if (content == null || !content.contains("minecraft.")) {
                plugin.getLogger().warning("下载 zh_cn.json 失败（网络受限？），编辑器将显示英文名");
                return;
            }

            Map<String, String> names = parse(new java.io.ByteArrayInputStream(
                    content.getBytes(StandardCharsets.UTF_8)));
            if (names.isEmpty()) {
                plugin.getLogger().warning("下载到的 zh_cn.json 内容异常，编辑器将显示英文名");
                return;
            }
            chinese.set(names);
            saveToCache(content);
            plugin.getLogger().info("已下载中文译名 " + names.size()
                    + " 条并缓存到 " + cacheFile.getPath());
        } catch (Throwable e) {
            // 兜底：下载线程里任何意外都不该冒泡（守护线程崩溃虽不致命，但会静默失败）
            plugin.getLogger().warning("下载中文译名时出错（编辑器将显示英文名）: " + e);
        }
    }

    /**
     * 服务端版本号，如 {@code 26.1.2}；取不到返回 null。
     * <p>
     * 只能读 {@code getBukkitVersion()}：{@code getMinecraftVersion()} 是 Paper 的扩展，
     * spigot-api 里没有。返回值形如 {@code 26.1.2.build.8-stable} 或
     * {@code 1.21.11-R0.1-SNAPSHOT}，因此只取开头的数字点号部分。
     */
    private String serverVersion() {
        String raw = plugin.getServer().getBukkitVersion();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher matcher = Pattern.compile("^(\\d+(?:\\.\\d+)+)").matcher(raw.trim());
        return matcher.find() ? matcher.group(1) : null;
    }

    /** 版本清单 → 版本元数据 → 资源清单 → zh_cn.json 的下载地址。 */
    private String resolveZhCnUrl(String version) {
        String manifest = HttpText.get(VERSION_MANIFEST);
        if (manifest == null) {
            return null;
        }
        // 精确匹配版本号，避免把 "26.1" 匹配到 "26.1.2"
        Matcher versionMatcher = Pattern
                .compile(VERSION_URL.pattern().formatted(Pattern.quote(version)))
                .matcher(manifest);
        if (!versionMatcher.find()) {
            return null;
        }
        String meta = HttpText.get(versionMatcher.group(1));
        if (meta == null) {
            return null;
        }
        Matcher indexMatcher = ASSET_INDEX_URL.matcher(meta);
        if (!indexMatcher.find()) {
            return null;
        }
        String index = HttpText.get(indexMatcher.group(1));
        if (index == null) {
            return null;
        }
        Matcher hashMatcher = ZH_CN_ENTRY.matcher(index);
        if (!hashMatcher.find()) {
            return null;
        }
        String hash = hashMatcher.group(1);
        return RESOURCE_CDN + hash.substring(0, 2) + "/" + hash;
    }

    /** 写入缓存文件；失败只影响下次启动要重新下载，不影响本次使用。 */
    private void saveToCache(String content) {
        try {
            File folder = cacheFile.getParentFile();
            if (folder != null && !folder.isDirectory() && !folder.mkdirs()) {
                return;
            }
            Files.write(cacheFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("写入 " + cacheFile.getName() + " 失败: " + e.getMessage());
        }
    }
}
