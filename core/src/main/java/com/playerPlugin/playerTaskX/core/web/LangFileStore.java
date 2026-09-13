package com.playerPlugin.playerTaskX.core.web;

import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.core.storage.JsonCodec;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
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
 *   <li>插件目录下的 {@code editor/zh_cn.json}（管理员手动放置，也用于离线服）；</li>
 *   <li>开启下载时，后台从 Mojang 资源 CDN 取一份并缓存到该路径；</li>
 *   <li>都拿不到就用英文名——<b>功能不受影响</b>，只是列表里没有中文。</li>
 * </ol>
 *
 * <h2>为什么放在 editor/ 而不是 lang/</h2>
 * {@code lang/} 放的是<b>插件自己的语言文件</b>（{@code zh_CN.yml}），是发给玩家看的文案；
 * 这份 {@code zh_cn.json} 是 <b>Minecraft 的译名数据</b>，只服务编辑器图标列表，
 * 两者除了名字像以外毫无关系。混在一个目录里既容易误删，也容易让人以为改它能换插件语言。
 * 放在 {@code editor/} 下还有个好处：不叫 {@code cache/}，
 * 就不会被「缓存可以随时清」的直觉带走——离线服管理员放进去的那份是唯一的中文来源。
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

    /**
     * 中文译名文件的目录（插件数据文件夹下），也是管理员手动放置文件的位置。
     * <p>
     * 用 {@code editor} 而不是 {@code lang} 或 {@code cache}，理由见类注释。
     */
    private static final String CHINESE_FOLDER = "editor";

    private static final String VERSION_MANIFEST =
            "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String RESOURCE_CDN = "https://resources.download.minecraft.net/";

    private final PlayerTaskX plugin;
    private final File chineseFile;

    /** 英文名（来自服务端 jar），按小写键索引。 */
    private volatile Map<String, String> english = Map.of();
    /** 中文名；未取得时为空表。 */
    private final AtomicReference<Map<String, String>> chinese = new AtomicReference<>(Map.of());

    /** 后台下载是否已启动，避免重复触发。 */
    private volatile boolean downloadStarted;

    public LangFileStore(PlayerTaskX plugin) {
        this.plugin = plugin;
        this.chineseFile = new File(new File(plugin.getDataFolder(), CHINESE_FOLDER), "zh_cn.json");
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
            plugin.getLogger().info("已载入中文译名 " + local.size() + " 条: " + chineseFile.getPath());
            return;
        }
        if (plugin.config().isEditorFetchChineseNames()) {
            startDownload();
        } else {
            plugin.getLogger().info("未启用中文译名下载，编辑器图标列表将显示英文名"
                    + "（如需中文，可把 zh_cn.json 放到 " + chineseFile.getParentFile().getPath() + "）");
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

    // ------------------------------------------------------------------
    // 加载
    // ------------------------------------------------------------------

    /**
     * 读服务端 jar 内的 {@code assets/minecraft/lang/en_us.json}。
     *
     * <h2>为什么用 {@link Bukkit} 的类加载器，而不是本类的</h2>
     * 插件类加载器<b>不会</b>把资源查询委派到服务端 jar 上（实测：从插件类发出查询会
     * 直接落空，而 {@code Bukkit} 与其同源的类加载器能查到）。因此必须借服务端自己的
     * 类加载器来查这个资源——{@code org.bukkit.Bukkit} 与 CraftBukkit 实现由同一个
     * 加载器加载，它认得服务端 jar 里的条目。
     * <p>
     * 取不到只影响英文名的可读性（退回枚举名推导），返回空表即可。
     */
    private Map<String, String> loadServerEnglish() {
        try (InputStream stream = Bukkit.class.getClassLoader()
                .getResourceAsStream("assets/minecraft/lang/en_us.json")) {
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
        if (!chineseFile.isFile()) {
            return Map.of();
        }
        try (InputStream stream = Files.newInputStream(chineseFile.toPath())) {
            return parse(stream);
        } catch (Exception e) {
            plugin.getLogger().warning("读取 " + chineseFile.getName() + " 失败: " + e.getMessage());
            return Map.of();
        }
    }

    /**
     * 解析语言文件为「小写键 → 译名」。
     * <p>
     * 语言文件是扁平的 JSON 对象（翻译值里含大量 {@code \u0020} 之类的转义），
     * 因此交给 {@link JsonCodec} 解析——手写正则 + 手工反转义曾经在这里躺了 60 行，
     * 而项目本来就带 Jackson。
     * <p>
     * 收录 {@code item.} / {@code block.} / {@code entity.} 三类键，键去掉命名空间前缀。
     * 查表时实体枚举名会先剥掉 {@code MINECRAFT_} 前缀再小写，正好与 {@code entity.minecraft.*}
     * 的键形态一致（实测 157 个实体枚举全部可映射）。
     * <p>
     * 方块与物品可能同名（如 {@code stone}），此时<b>以物品为准</b>——物品译名更贴近
     * 「拿在手里的东西」。带点号的子键（{@code entity.minecraft.tropical_fish.predefined.0}）
     * 不是实体本身的名字，一律跳过。
     */
    static Map<String, String> parse(InputStream stream) throws IOException {
        String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> names = new LinkedHashMap<>();
        JsonCodec.readMap(text).forEach((key, value) -> {
            int namespaceEnd = key.indexOf(".minecraft.");
            if (namespaceEnd < 0 || value == null) {
                return;
            }
            String group = key.substring(0, namespaceEnd);
            if (!"item".equals(group) && !"block".equals(group) && !"entity".equals(group)) {
                return;
            }
            String name = key.substring(namespaceEnd + ".minecraft.".length());
            if (name.isEmpty() || name.indexOf('.') >= 0) {
                return;
            }
            // 物品优先：同名时覆盖掉方块那条
            if ("item".equals(group) || !names.containsKey(name)) {
                names.put(name, String.valueOf(value));
            }
        });
        return names;
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
                    + " 条并缓存到 " + chineseFile.getPath());
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

    /**
     * 版本清单 → 版本元数据 → 资源清单 → zh_cn.json 的下载地址。
     * <p>
     * 三份清单都是 JSON，因此用 {@link JsonCodec} 逐层取值，而不是拿正则去捞 URL：
     * 正则版本还得小心「26.1 别匹配到 26.1.2」这类问题，交给 JSON 解析后它自然消失。
     *
     * @return 下载地址；任何一步取不到都返回 {@code null}（调用方只记日志，不重试）
     */
    private String resolveZhCnUrl(String version) {
        String manifest = HttpText.get(VERSION_MANIFEST);
        if (manifest == null) {
            return null;
        }
        String meta = HttpText.get(versionUrl(manifest, version));
        if (meta == null) {
            return null;
        }
        // 版本元数据：{"assetIndex":{"url":"…"}}
        Object assetIndex = JsonCodec.readMap(meta).get("assetIndex");
        if (!(assetIndex instanceof Map<?, ?> index)) {
            return null;
        }
        String indexJson = HttpText.get(text(index.get("url")));
        if (indexJson == null) {
            return null;
        }
        // 资源清单：{"objects":{"minecraft/lang/zh_cn.json":{"hash":"…"}}}
        Object objects = JsonCodec.readMap(indexJson).get("objects");
        if (!(objects instanceof Map<?, ?> map)) {
            return null;
        }
        if (!(map.get("minecraft/lang/zh_cn.json") instanceof Map<?, ?> file)) {
            return null;
        }
        String hash = text(file.get("hash"));
        return hash == null || hash.length() < 2 ? null : RESOURCE_CDN + hash.substring(0, 2) + "/" + hash;
    }

    /** 从版本清单里取该版本元数据的地址；版本号精确匹配（{@code id} 字段）。 */
    private static String versionUrl(String manifest, String version) {
        if (!(JsonCodec.readMap(manifest).get("versions") instanceof List<?> versions)) {
            return null;
        }
        for (Object item : versions) {
            if (item instanceof Map<?, ?> entry && version.equals(text(entry.get("id")))) {
                return text(entry.get("url"));
            }
        }
        return null;
    }

    /** 取 JSON 里的字符串值；缺失返回 {@code null}（区别于「空串」）。 */
    private static String text(Object value) {
        return value instanceof String string && !string.isBlank() ? string : null;
    }

    /** 写入缓存文件；失败只影响下次启动要重新下载，不影响本次使用。 */
    private void saveToCache(String content) {
        try {
            File folder = chineseFile.getParentFile();
            if (folder != null && !folder.isDirectory() && !folder.mkdirs()) {
                return;
            }
            Files.write(chineseFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("写入 " + chineseFile.getName() + " 失败: " + e.getMessage());
        }
    }
}
