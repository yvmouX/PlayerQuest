package com.playerPlugin.playerTaskX.core.storage.yaml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * 一个 YAML 定义文件夹（{@code quests/} 或 {@code presets/}）。
 *
 * <h2>目录约定</h2>
 * <ul>
 *   <li>只认 {@code .yml} / {@code .yaml}，其它文件忽略（放 README 或备份不会出事）；</li>
 *   <li>会递归扫描子目录，方便按主题分组；</li>
 *   <li><b>文件名（去掉扩展名）就是默认 id</b>；文件内容里写了 {@code id} 则以内容为准——
 *       这样「一个定义一个文件」时不必重复写 id，而复制文件改内容时也不会撞 id；</li>
 *   <li>单个文件解析失败只跳过它并记一条告警：一份写坏的 YAML 不该让整个插件起不来。</li>
 * </ul>
 *
 * <p>解析结果的缓存由调用方（{@code Yaml*Repository}）持有：本类每次 {@link #load()} 都重新读盘，
 * 于是「重载」天然能拿到文件的最新内容，而编辑器的逐条查询走上层缓存、不会反复读盘。
 *
 * <p><b>写入只有「铺一次示例」这一条路</b>（{@link #isEmpty()} + {@link #writeOnce}）：
 * 目录空着时插件放一份示例进去，之后再也不动它。定义本身永远不进这里——
 * 编辑器与游戏内命令的保存都只写数据库。
 */
public final class DefinitionFolder {

    private final Path directory;
    /** 日志前缀，形如 {@code quests}，让告警一眼看出是哪个目录的问题。 */
    private final String label;
    private final Consumer<String> warner;

    public DefinitionFolder(Path directory, String label, Consumer<String> warner) {
        this.directory = directory;
        this.label = label;
        this.warner = warner == null ? message -> { } : warner;
    }

    /** 插件数据目录下的定义文件夹（{@code quests} / {@code presets}）。 */
    public static DefinitionFolder of(File dataFolder, String name, Consumer<String> warner) {
        File base = dataFolder == null ? new File(".") : dataFolder;
        return new DefinitionFolder(new File(base, name).toPath(), name, warner);
    }

    /**
     * 目录不存在时创建它。
     * <p>
     * 里面写什么由调用方决定：插件只在目录<b>空着</b>时铺一份示例（见 {@link #writeOnce}），
     * 已经有任何定义时一律不动，免得把管理员自己删掉的示例又变回来。
     *
     * @return 是否真的创建了目录（调用方可以据此提示管理员）
     */
    public boolean ensureExists() {
        if (Files.isDirectory(directory)) {
            return false;
        }
        try {
            Files.createDirectories(directory);
            return true;
        } catch (IOException e) {
            warner.accept("创建目录 " + label + "/ 失败: " + e.getMessage());
            return false;
        }
    }

    public Path directory() {
        return directory;
    }

    /** 告警出口，供上层（{@code YamlSources}）复用同一份日志约定。 */
    Consumer<String> warnSink() {
        return warner;
    }

    /** 目录名（{@code quests} / {@code presets}），用于提示「去哪个目录改」。 */
    String label() {
        return label;
    }

    /**
     * 扫描并解析整个目录。
     *
     * @return 按文件名排序的文档列表；解析失败的文件不在其中（已记告警）
     */
    public List<Document> load() {
        List<Path> files = yamlFiles();
        List<Document> documents = new ArrayList<>(files.size());
        for (Path file : files) {
            Document document = parse(file);
            if (document != null) {
                documents.add(document);
            }
        }
        return documents;
    }

    /**
     * 目录里是否一个 YAML 定义文件都没有（目录不存在也算空）。
     * <p>
     * 铺设示例文件前用它判断：只要管理员已经放过任何一个定义，就不该再往里塞东西。
     */
    public boolean isEmpty() {
        return yamlFiles().isEmpty();
    }

    /** 目录下某个定义文件的位置（文件名不带扩展名，固定写 {@code .yml}）。 */
    public Path pathOf(String fileName) {
        return directory.resolve(fileName + ".yml");
    }

    /**
     * 写一个文件，<b>已存在则什么都不做</b>。
     * <p>
     * 这两个目录平时是管理员的领地，插件只有「铺示例」这一次写机会，
     * 因此这里不提供覆盖语义：想改示例就改文件，想重新拿到示例就先把目录清空。
     *
     * @return 是否真的写了（false = 已存在或写失败，后者已记告警）
     */
    public boolean writeOnce(Path file, String text) {
        if (Files.exists(file)) {
            return false;
        }
        try {
            Files.createDirectories(directory);
            Files.writeString(file, text, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            warner.accept("写入 " + relative(file) + " 失败: " + e.getMessage());
            return false;
        }
    }

    /** 目录下所有 YAML 文件，按绝对路径排序。 */
    private List<Path> yamlFiles() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.filter(Files::isRegularFile).filter(DefinitionFolder::isYaml).forEach(files::add);
        } catch (IOException e) {
            warner.accept("读取 " + label + "/ 目录失败: " + e.getMessage());
            return List.of();
        }
        // 排序保证「同一份文件每次得到同样的加载顺序」，否则注册表顺序会随文件系统变化
        files.sort(Comparator.comparing(path -> path.toAbsolutePath().toString()));
        return files;
    }

    /** 解析单个文件；失败返回 null 并记告警。 */
    private Document parse(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            warner.accept(relative(file) + " 读取失败，已跳过: " + e.getMessage());
            return null;
        }
        Map<String, Object> values;
        try {
            values = YamlText.readMap(text);
        } catch (RuntimeException e) {
            // 语法错误（缩进、缺失引号…）：只说清是哪个文件，具体位置交给 SnakeYAML 的消息
            warner.accept(relative(file) + " 不是合法的 YAML，已跳过: " + e.getMessage());
            return null;
        }
        if (values == null || values.isEmpty()) {
            // 只有注释、或内容为空：不告警。留一个空文件写笔记是合理用法，
            // 每次启动都刷一条警告只会教人忽略警告
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>(values);
        String fileId = fileId(file);
        // 内容里的 id 优先；没有才用文件名
        copy.putIfAbsent("id", fileId);
        return new Document(relative(file), fileId, copy, parentName(file));
    }

    /** 直接父目录名；预设用它兜底判断类别（{@code presets/rewards/x.yml}）。 */
    private static String parentName(Path file) {
        Path parent = file.getParent();
        return parent == null || parent.getFileName() == null ? "" : parent.getFileName().toString();
    }

    /** 文件名（去掉扩展名）；它就是默认 id。 */
    private static String fileId(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static boolean isYaml(Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    /** 相对路径形式的可读位置，日志里比绝对路径更好认。 */
    private String relative(Path file) {
        try {
            return label + "/" + directory.relativize(file).toString().replace(File.separatorChar, '/');
        } catch (IllegalArgumentException e) {
            return file.toString();
        }
    }

    /**
     * 一个 YAML 文件的解析结果。
     *
     * @param location   相对路径（日志与「只读定义来自哪个文件」提示用）
     * @param fileId     文件名推出来的 id（内容里没写 id 时用的就是它）
     * @param values     内容（已保证有 {@code id}）
     * @param parentName 直接父目录名；预设用它兜底判断类别
     */
    public record Document(String location, String fileId, Map<String, Object> values, String parentName) {
    }
}
