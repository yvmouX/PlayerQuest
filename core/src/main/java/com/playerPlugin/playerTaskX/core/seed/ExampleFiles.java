package com.playerPlugin.playerTaskX.core.seed;

import com.playerPlugin.playerTaskX.api.model.Preset;
import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.core.storage.yaml.DefinitionFolder;
import com.playerPlugin.playerTaskX.core.storage.yaml.YamlDefinitions;
import com.playerPlugin.playerTaskX.core.storage.yaml.YamlText;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把出厂示例也铺成 {@code quests/} 与 {@code presets/} 下的 YAML 文件。
 *
 * <h2>为什么库里那套之外还要一套文件</h2>
 * 库里那套（{@link ExampleQuests} / {@link ExamplePresets}）是<b>可编辑</b>的示例，
 * 管理员在编辑器里直接改、直接禁用。文件里这套解决的是另一个问题：<b>文件格式长什么样</b>。
 * 「文件名即 id」「顶层直接写字段、不必写 id」这些约定，看一份能跑的文件比读文档快得多，
 * 而且复制一份改改就是自己的任务。
 *
 * <h2>id 必须与库里那套不同</h2>
 * 同 id 两边都有时库优先、文件里那份被忽略并告警（见 {@code MergedSources}）。
 * 若沿用 {@code example_} 前缀，这一整套文件上线第一天就会变成「被忽略的重复定义」，
 * 还会刷十几条告警。因此文件里的示例统一加 {@value #PREFIX} 前缀：两套并存，互不干扰。
 *
 * <h2>只在文件夹为空时铺一次</h2>
 * 目录里只要已经存在任何一个 YAML 文件，就一个字节都不写：管理员删掉某几个示例、
 * 或者放了自己的定义，重启时插件不该把它们加回来。想重新拿到示例，清空目录再启动即可。
 */
public final class ExampleFiles {

    /** 文件里示例的 id 前缀；与写进数据库的 {@code example_*} 区分开是刻意的（见类注释）。 */
    static final String PREFIX = "example_file_";

    /** 库里那套示例的 id 前缀（{@link ExampleQuests} / {@link ExamplePresets} 的约定）。 */
    private static final String DATABASE_PREFIX = "example_";

    private static final List<String> QUEST_HEADER = List.of(
            "示例任务：文件名（去掉扩展名）就是 id，因此这里不必再写 id。",
            "改完执行 /ptxa reload 生效。文件里的定义是只读的：编辑器与游戏内命令只改数据库里的定义，",
            "要在这里改就改文件，想搬进数据库就用编辑器的「导出 / 导入 YAML」。",
            "不需要就删掉这个文件——只要 quests/ 里还有别的 YAML，插件就不会再补示例。");

    private static final List<String> PRESET_HEADER = List.of(
            "示例预设：文件名（去掉扩展名）就是 id，kind 决定它是目标预设还是奖励预设。",
            "预设只是编辑器的便利设施，引擎不认识它，删掉不影响任何已建好的任务。",
            "不需要就删掉这个文件——只要 presets/ 里还有别的 YAML，插件就不会再补示例。");

    private ExampleFiles() {
    }

    /**
     * 目录为空时把示例任务铺进去。
     *
     * @return 写入的文件数；目录里已经有定义时返回 0
     */
    public static int writeQuests(DefinitionFolder folder, List<Quest> examples) {
        if (folder.isEmpty()) {
            int written = 0;
            for (Quest example : examples) {
                Quest fileExample = asFileExample(example);
                Map<String, Object> document = YamlDefinitions.questDocument(fileExample);
                // 文件名就是 id：写出来只是重复一遍，删掉后复制文件改名即可得到一个新任务
                document.remove("id");
                if (folder.writeOnce(folder.pathOf(fileExample.id()), text(QUEST_HEADER, document))) {
                    written++;
                }
            }
            return written;
        }
        return 0;
    }

    /**
     * 目录为空时把示例预设铺进去。
     *
     * @return 写入的文件数；目录里已经有定义时返回 0
     */
    public static int writePresets(DefinitionFolder folder, List<Preset> examples) {
        if (folder.isEmpty()) {
            int written = 0;
            for (Preset example : examples) {
                Preset fileExample = asFileExample(example);
                Map<String, Object> document = new LinkedHashMap<>(YamlDefinitions.presetDocument(fileExample));
                document.remove("id");
                if (folder.writeOnce(folder.pathOf(fileExample.id()), text(PRESET_HEADER, document))) {
                    written++;
                }
            }
            return written;
        }
        return 0;
    }

    /** 注释头 + YAML 正文。注释只在这个文件里出现，读回来时 SnakeYAML 直接忽略。 */
    private static String text(List<String> header, Map<String, Object> document) {
        StringBuilder text = new StringBuilder();
        for (String line : header) {
            text.append("# ").append(line).append('\n');
        }
        text.append('\n').append(YamlText.write(document));
        return text.toString();
    }

    /**
     * 换成「文件里的那份」：改 id 前缀。
     * <p>
     * 库里那套与文件里那套示例必须完全独立：漏改 id 会让文件里的定义指向库里那条，
     * 两套示例就被悄悄串起来了——这类「看起来正常、实际连到别处」的错最难发现。
     */
    private static Quest asFileExample(Quest quest) {
        return new Quest(fileId(quest.id()), quest.name(), quest.description(), quest.icon(),
                quest.category(), quest.type(), quest.objectives(), quest.rewards(),
                quest.refreshCost(), quest.enabled());
    }

    private static Preset asFileExample(Preset preset) {
        return new Preset(preset.kind(), fileId(preset.id()), preset.name(), preset.type(),
                preset.properties(), preset.description());
    }

    /** 库里那套的 id → 文件里这套的 id（{@code example_daily_mine} → {@code example_file_daily_mine}）。 */
    static String fileId(String id) {
        // 库里那套统一是 example_ 开头：把 file_ 插在这之后，得到 example_file_xxx。
        // 直接拼前缀会变成 example_file_example_xxx——两套示例本来就是同一个东西的两种形态，
        // id 该让人看出这层关系，而不是把前缀叠两遍
        String base = id.startsWith(DATABASE_PREFIX) ? id.substring(DATABASE_PREFIX.length()) : id;
        return PREFIX + base;
    }
}
