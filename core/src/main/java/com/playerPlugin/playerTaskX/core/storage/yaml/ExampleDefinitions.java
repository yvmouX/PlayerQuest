package com.playerPlugin.playerTaskX.core.storage.yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * 出厂示例定义：随插件发布（jar 内的 {@code quests/} 与 {@code presets/}），只在数据目录空着时铺一份过去。
 * 从 classpath 读而不是数据目录——资源必须一直完好，「清空目录再启动」才能拿回干净的示例；已存在的文件一律不覆盖。
 */
public final class ExampleDefinitions {

    /** 两个示例目录（既是资源路径，也是数据目录名）。 */
    private static final String QUESTS = "quests";
    private static final String PRESETS = "presets";

    private ExampleDefinitions() {
    }

    /** 目标目录空着时铺一份示例任务，返回写入的文件数。 */
    public static int seedQuests(DefinitionFolder folder) {
        return seed(folder, QUESTS);
    }

    /** 预设目录空着时铺一份示例预设，返回写入的文件数。 */
    public static int seedPresets(DefinitionFolder folder) {
        return seed(folder, PRESETS);
    }

    private static int seed(DefinitionFolder folder, String resourceFolder) {
        if (!folder.isEmpty()) {
            return 0;
        }
        return copyInto(folder, read(resourceFolder));
    }

    /**
     * 把「文件名（不带扩展名）→ 文本」写进目录。
     *
     * @return 实际写入的文件数（已存在的文件不算）
     */
    static int copyInto(DefinitionFolder folder, Map<String, String> files) {
        int written = 0;
        for (Map.Entry<String, String> file : files.entrySet()) {
            if (folder.writeOnce(folder.pathOf(file.getKey()), file.getValue())) {
                written++;
            }
        }
        return written;
    }

    /**
     * 读取 classpath 上某个目录里的全部 YAML。
     *
     * @return 文件名（去掉扩展名）→ 文本，按文件名排序；目录不存在时为空表
     */
    static Map<String, String> read(String folder) {
        Map<String, String> files = new TreeMap<>();
        try {
            Enumeration<URL> roots = ExampleDefinitions.class.getClassLoader().getResources(folder);
            while (roots.hasMoreElements()) {
                URL root = roots.nextElement();
                if ("file".equals(root.getProtocol())) {
                    readDirectory(Path.of(root.toURI()), files);
                } else {
                    readArchive(root, folder, files);
                }
            }
        } catch (IOException | URISyntaxException e) {
            // 示例只是「开箱有个参照」，读不到就当没有：不影响任何功能，也会被测试拦住
            return Map.of();
        }
        return files;
    }

    /** 开发与单测：资源是磁盘上的目录。 */
    private static void readDirectory(Path directory, Map<String, String> files) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        List<Path> yamlFiles;
        try (Stream<Path> entries = Files.list(directory)) {
            yamlFiles = entries.filter(ExampleDefinitions::isYaml).sorted().toList();
        }
        for (Path file : yamlFiles) {
            files.put(baseName(file.getFileName().toString()), Files.readString(file, StandardCharsets.UTF_8));
        }
    }

    /** 生产：资源在插件 jar 内（jar 里有目录条目，因此 {@code getResources("quests")} 找得到）。 */
    private static void readArchive(URL root, String folder, Map<String, String> files) throws IOException {
        URL archive;
        try {
            archive = ((JarURLConnection) root.openConnection()).getJarFileURL();
        } catch (IOException | ClassCastException e) {
            return;
        }
        String prefix = folder + "/";
        // 自己开一份 JarFile 并在这里关掉：JarURLConnection.getJarFile() 返回的是类加载器共享的那个，
        // 关掉它会让后续的类加载失败
        try (JarFile jar = new JarFile(new File(archive.toURI()))) {
            for (JarEntry entry : jar.stream().filter(ExampleDefinitions::isYaml).toList()) {
                if (entry.isDirectory() || !entry.getName().startsWith(prefix)) {
                    continue;
                }
                try (InputStream stream = jar.getInputStream(entry)) {
                    files.put(baseName(entry.getName().substring(prefix.length())),
                            new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    private static boolean isYaml(JarEntry entry) {
        String name = entry.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private static boolean isYaml(Path path) {
        return isYaml(path.getFileName().toString());
    }

    private static boolean isYaml(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".yml") || lower.endsWith(".yaml");
    }

    /** 文件名去掉扩展名：它就是要写进数据目录的文件名（也是这份定义的 id）。 */
    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
