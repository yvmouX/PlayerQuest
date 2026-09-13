package com.playerPlugin.playerTaskX.core.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * JSON 文件目录的读写。
 *
 * <h2>写入必须是原子的</h2>
 * {@link #write} 一律「写临时文件 → 原子改名」，绝不原地覆盖目标文件。
 * 原地写有两个致命后果：写一半崩溃会留下半截 JSON（用户的全部任务因此读不出来），
 * 以及读取方可能看到写了一半的内容。数据库方案下这是事务白送的保证，
 * 文件方案必须自己补上。
 *
 * <h2>读取必须分级容错</h2>
 * {@link #read} 解析失败返回 {@code null} 而不是抛异常，由调用方决定「跳过这一条」
 * 还是「保留上一次的好数据」。一条脏数据不该让整个任务列表消失——
 * 这与既有 {@code QuestAdminService.reload()} 的策略一致。
 *
 * <h2>文件与 id 的关系</h2>
 * {@link #fileFor} 由 id 生成文件名；{@link #idFromFile} 只用于「目录里有哪些文件」这类
 * 批量场景。<b>权威来源始终是文件内的 {@code id} 字段</b>，文件名只作约定——
 * 用文件名反推 id 会在用户重命名文件时产生幽灵任务。
 */
public final class JsonFileStore {

    /** 写入中的临时文件后缀；启动时会被清理，避免崩溃留下的碎片堆积。 */
    private static final String TEMP_SUFFIX = ".tmp";

    private final Path folder;

    public JsonFileStore(Path folder) {
        this.folder = folder;
    }

    public Path folder() {
        return folder;
    }

    /** id 对应的文件路径。id 中的路径分隔符等非法字符会被替换，避免越出目录。 */
    public Path fileFor(String id) {
        return folder.resolve(sanitize(id) + ".json");
    }

    /**
     * 原子写入一个 JSON 文件。
     *
     * @throws StorageException 目录不可创建或写入失败（磁盘满、只读挂载、权限不足）
     */
    public void write(String id, String json) {
        try {
            Files.createDirectories(folder);
            Path target = fileFor(id);
            // 临时文件必须与目标同目录：跨文件系统的 rename 不是原子操作
            Path temp = target.resolveSibling(target.getFileName() + TEMP_SUFFIX);
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            move(temp, target);
        } catch (IOException e) {
            throw new StorageException("写入失败: " + fileFor(id), e);
        }
    }

    /** 读取一个文件；不存在或解析失败返回 null（由调用方决定如何处理）。 */
    public String read(String id) {
        return readFile(fileFor(id));
    }

    /** 读取指定路径；失败返回 null。 */
    public String readFile(Path path) {
        try {
            if (!Files.isRegularFile(path)) {
                return null;
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** 删除文件；返回是否确实删掉了。 */
    public boolean delete(String id) {
        try {
            return Files.deleteIfExists(fileFor(id));
        } catch (IOException e) {
            throw new StorageException("删除失败: " + fileFor(id), e);
        }
    }

    /** 目录下全部 {@code .json} 文件，按文件名排序（保证载入顺序稳定）。 */
    public List<Path> listFiles() {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(folder)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new StorageException("读取目录失败: " + folder, e);
        }
    }

    /**
     * 清理上次崩溃残留的临时文件。
     * <p>
     * 临时文件只可能在写入过程中存在，启动时还在的都是残骸；留着会让用户困惑
     * （"为什么多了个 .tmp"），也可能被误当成数据文件。
     */
    public int cleanTempFiles() {
        if (!Files.isDirectory(folder)) {
            return 0;
        }
        int removed = 0;
        try (Stream<Path> stream = Files.list(folder)) {
            List<Path> temps = new ArrayList<>();
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(TEMP_SUFFIX))
                    .forEach(temps::add);
            for (Path temp : temps) {
                if (Files.deleteIfExists(temp)) {
                    removed++;
                }
            }
        } catch (IOException ignored) {
            // 清理失败不影响功能，只是残留文件多留一会儿
        }
        return removed;
    }

    /** 优先原子改名；文件系统不支持时退回普通改名（仍是"先写完再替换"，不会留半截文件）。 */
    private void move(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 把 id 里不适合做文件名的字符替换掉：{@code / \ : * ? " < > |} 与空白。 */
    static String sanitize(String id) {
        if (id == null || id.isBlank()) {
            return "_";
        }
        StringBuilder builder = new StringBuilder(id.length());
        for (char c : id.toCharArray()) {
            builder.append(isUnsafe(c) ? '_' : c);
        }
        return builder.toString();
    }

    private static boolean isUnsafe(char c) {
        return c == '/' || c == '\\' || c == ':' || c == '*' || c == '?'
                || c == '"' || c == '<' || c == '>' || c == '|' || Character.isWhitespace(c);
    }
}
