package com.playerPlugin.playerTaskX.core.storage;

import com.playerPlugin.playerTaskX.api.model.Quest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * {@link QuestRepository} 的文件实现：一个任务一个 JSON 文件。
 *
 * <h2>为什么一任务一文件</h2>
 * <ul>
 *   <li><b>部分损坏只影响一个任务</b>：单文件方案里一处语法错误会让全部任务消失；</li>
 *   <li><b>多服同步粒度小</b>：改一个任务只动一个文件，分发与 diff 都干净；</li>
 *   <li><b>冲突面小</b>：两个人改不同任务是两个文件，不会互相覆盖。</li>
 * </ul>
 *
 * <h2>容错策略（与既有 QuestAdminService 一致，并补一条）</h2>
 * <ol>
 *   <li>单个文件解析失败 → <b>跳过该文件</b>并记日志（含文件名），其余任务照常载入；</li>
 *   <li>缺少 {@code id} → 跳过并记日志（不能凭空造 id）；</li>
 *   <li>文件名与 {@code id} 不一致 → <b>以 id 字段为准</b>并记警告；</li>
 *   <li><b>全部文件都读失败 → 返回空列表并明确告知</b>，由上层决定是否保留上一次的定义，
 *       而不是在这里悄悄清空。</li>
 * </ol>
 *
 * <h2>写入</h2>
 * 一律经 {@link JsonFileStore#write}（临时文件 + 原子改名），不原地覆盖。
 */
public final class QuestFileRepository implements QuestRepository {

    private final JsonFileStore files;
    private final Consumer<String> warn;

    public QuestFileRepository(JsonFileStore files, Consumer<String> warn) {
        this.files = files;
        this.warn = warn;
    }

    @Override
    public List<Quest> findAll() {
        files.cleanTempFiles();
        List<Quest> quests = new ArrayList<>();
        int unreadable = 0;
        for (Path path : files.listFiles()) {
            String json = files.readFile(path);
            if (json == null) {
                unreadable++;
                warn.accept("任务文件无法读取，已跳过: " + path.getFileName());
                continue;
            }
            Quest quest = parse(path, json);
            if (quest != null) {
                quests.add(quest);
            } else {
                unreadable++;
            }
        }
        if (unreadable > 0) {
            warn.accept("共 " + unreadable + " 个任务文件无法解析，已跳过；"
                    + "其余 " + quests.size() + " 个任务照常载入");
        }
        return quests;
    }

    @Override
    public Optional<Quest> findById(String id) {
        String json = files.read(id);
        if (json == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(parse(files.fileFor(id), json));
    }

    @Override
    public void save(Quest quest) {
        if (quest == null || quest.id() == null || quest.id().isBlank()) {
            throw new StorageException("任务必须有 id 才能保存");
        }
        files.write(quest.id(), JsonCodec.write(QuestJson.toJson(quest)));
    }

    @Override
    public boolean delete(String id) {
        return files.delete(id);
    }

    @Override
    public long count() {
        return files.listFiles().size();
    }
    /**
     * 解析一个任务文件。
     *
     * @return 解析出的任务；无法解析返回 null（已记日志）
     */
    private Quest parse(Path path, String json) {
        Map<String, Object> node;
        try {
            node = JsonCodec.readMapStrict(json);
        } catch (Exception e) {
            warn.accept("任务文件不是合法 JSON，已跳过: " + path.getFileName() + "（" + e.getMessage() + "）");
            return null;
        }
        if (node == null) {
            warn.accept("任务文件内容不是 JSON 对象，已跳过: " + path.getFileName());
            return null;
        }
        Object rawId = node.get("id");
        if (rawId == null || String.valueOf(rawId).isBlank()) {
            warn.accept("任务文件缺少 id 字段，已跳过（不会用文件名推断 id）: " + path.getFileName());
            return null;
        }
        String id = String.valueOf(rawId).trim();

        // 文件名只是约定，id 才是权威；不一致时按 id 处理并提醒
        String expected = JsonFileStore.sanitize(id) + ".json";
        String actual = path.getFileName().toString();
        if (!expected.equalsIgnoreCase(actual)) {
            warn.accept("任务文件 " + actual + " 内的 id 是 " + id
                    + "，与文件名不一致；已按 id=" + id + " 载入（建议把文件重命名为 " + expected + "）");
        }

        try {
            Quest quest = QuestJson.fromJson(node);
            if (quest.id() == null || quest.id().isBlank()) {
                warn.accept("任务文件解析后 id 为空，已跳过: " + actual);
                return null;
            }
            return quest;
        } catch (Exception e) {
            warn.accept("任务文件解析失败，已跳过: " + actual + "（" + e.getMessage() + "）");
            return null;
        }
    }

    /** 便于测试与日志展示的目录描述。 */
    @Override
    public String toString() {
        return "QuestFileRepository[" + files.folder().toAbsolutePath() + "]";
    }
}
