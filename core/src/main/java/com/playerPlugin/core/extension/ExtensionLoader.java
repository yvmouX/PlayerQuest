package com.playerPlugin.core.extension;

/**
 * - 支持运行时读取 `extensions` 目录并以独立 ClassLoader 加载扩展 jar；
 * - 也支持普通的 SPI（`META-INF/services`）机制，方便打包为插件依赖或内置扩展。
 */
public class ExtensionLoader {
    private final TaskTypeRegistry registry;
    private final Path extensionsDir; // plugins/yourplugin/extensions

    public ExtensionLoader(TaskTypeRegistry registry, Path dir) {
        this.registry = registry;
        this.extensionsDir = dir;
    }

    public void loadAll() {
        // 1. load jars in directory
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(extensionsDir, "*.jar")) {
            for (Path jar : ds) loadJar(jar);
        } catch (IOException e) { /* log */ }

        // 2. load SPI from classpath
        ServiceLoader<TaskType> loader = ServiceLoader.load(TaskType.class);
        for (TaskType t : loader) {
            try {
                registry.register(t);
            } catch (Exception ex) { /* log compatibility */ }
        }
    }

    private void loadJar(Path jar) {
        try {
            URLClassLoader cl = new URLClassLoader(new URL[]{jar.toUri().toURL()}, this.getClass().getClassLoader());
            ServiceLoader<TaskType> loader = ServiceLoader.load(TaskType.class, cl);
            for (TaskType t : loader) {
                try {
                    registry.register(t);
                } catch (Exception ex) { /* log */ }
            }
        } catch (MalformedURLException e) { /* log */ }
    }
}
