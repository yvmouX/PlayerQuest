package com.playerPlugin.playerTaskX.core.storage.yaml;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * YAML 文本 → 普通对象（{@code Map} / {@code List} / 标量）；字段映射不在这里，由 {@code QuestJson} / {@code PresetJson} 定义。
 * 解析按 YAML 1.2 core 语义：{@code yes}/{@code no}/{@code on}/{@code off} 一律是字符串、{@code 012} 是十进制 12；默认的 1.1 语义会把 {@code target: NO} 读成布尔 false，由 {@code YamlTextTest} 钉住。
 */
public final class YamlText {

    private YamlText() {
    }

    // ------------------------------------------------------------------
    // 读
    // ------------------------------------------------------------------

    /**
     * 解析一段 YAML 为普通对象（映射 / 列表 / 标量）。
     *
     * @throws org.yaml.snakeyaml.error.YAMLException 语法错误
     */
    public static Object read(String text) {
        // SafeConstructor：不允许 YAML 里的标签去实例化任意 Java 类。
        // 这些文件是管理员手写的，但「能写的文件」不该等于「能加载任意类」
        return reader().load(text);
    }

    /** 读取用的 {@link Yaml}（SafeConstructor + {@link StrictResolver}）：所有 YAML（定义文件与示例）都只经这一个解析器，因此「哪些写法会被当成字符串」只有一处定义。 */
    private static Yaml reader() {
        LoaderOptions loaderOptions = new LoaderOptions();
        DumperOptions dumperOptions = new DumperOptions();
        return new Yaml(new CoreSchemaConstructor(loaderOptions), new Representer(dumperOptions),
                dumperOptions, loaderOptions, new StrictResolver());
    }

    /** 标量解析：只认 {@code true/false}、整数、浮点与 {@code null}，其余一律当字符串（SnakeYAML 默认的 1.1 语义会把 {@code target: NO} 读成布尔 false）。 */
    private static final class StrictResolver extends Resolver {

        private static final Pattern BOOL = Pattern.compile("^(?:true|True|TRUE|false|False|FALSE)$");
        /**
         * 整数：十进制（含前导零）、{@code 0x} 十六进制、{@code 0o} 八进制——与 YAML 1.2 core
         * 及 YAML 1.2 的语义一致。刻意不接受 {@code 1_000}（同样当字符串）。
         */
        private static final Pattern INT = Pattern.compile(
                "^[-+]?(?:[0-9]+|0[xX][0-9a-fA-F]+|0[oO][0-7]+)$");
        /** 小数与科学计数：{@code 1.5}、{@code .5}、{@code 1e3}。{@code .inf}/{@code .nan} 留给字符串。 */
        private static final Pattern FLOAT = Pattern.compile(
                "^[-+]?(?:\\.[0-9]+|[0-9]+\\.[0-9]*|[0-9]+[eE][-+]?[0-9]+)$");
        private static final Pattern NULL = Pattern.compile("^(?:~|null|Null|NULL|)$");

        @Override
        protected void addImplicitResolvers() {
            // 顺序有讲究：布尔、null、整数、浮点依次匹配，其余落回字符串
            addImplicitResolver(Tag.BOOL, BOOL, "tTfF");
            addImplicitResolver(Tag.NULL, NULL, "~nN\u0000");
            addImplicitResolver(Tag.INT, INT, "-+0123456789");
            addImplicitResolver(Tag.FLOAT, FLOAT, "-+0123456789.");
            // 刻意不注册 MERGE（<<）与 TIMESTAMP：前者是 YAML 1.1 的合并键，
            // 后者会把 2024-01-01 这种字符串变成 java.util.Date
        }
    }

    /** 按 YAML 1.2 core 解释整数：SnakeYAML 的 1.1 规则会把 {@code 012} 读成八进制 10，这里换掉 {@code Tag.INT} 的构造器。 */
    private static final class CoreSchemaConstructor extends SafeConstructor {

        CoreSchemaConstructor(LoaderOptions loaderOptions) {
            super(loaderOptions);
            yamlConstructors.put(Tag.INT, new CoreIntConstruct());
        }
    }

    /** 整数构造：十进制（含前导零）、{@code 0x} 十六进制、{@code 0o} 八进制。 */
    private static final class CoreIntConstruct implements org.yaml.snakeyaml.constructor.Construct {

        @Override
        public Object construct(org.yaml.snakeyaml.nodes.Node node) {
            String value = ((org.yaml.snakeyaml.nodes.ScalarNode) node).getValue().replace("_", "");
            boolean negative = value.startsWith("-");
            if (negative || value.startsWith("+")) {
                value = value.substring(1);
            }
            int radix = 10;
            if (value.startsWith("0x") || value.startsWith("0X")) {
                radix = 16;
                value = value.substring(2);
            } else if (value.startsWith("0o") || value.startsWith("0O")) {
                radix = 8;
                value = value.substring(2);
            }
            java.math.BigInteger parsed = new java.math.BigInteger(value, radix);
            if (negative) {
                parsed = parsed.negate();
            }
            if (parsed.bitLength() < 32) {
                return parsed.intValue();
            }
            return parsed.bitLength() < 64 ? (Object) parsed.longValue() : parsed;
        }

        @Override
        public void construct2ndStep(org.yaml.snakeyaml.nodes.Node node, Object object) {
            // 标量没有第二步：接口要求实现，这里无事可做
        }
    }

    /** 解析成映射；顶层不是映射（列表、标量、空）时返回 {@code null}。 */
    public static Map<String, Object> readMap(String text) {
        Object loaded = read(text);
        if (!(loaded instanceof Map<?, ?> map)) {
            return null;
        }
        return stringKeyed(map);
    }

    /** 顶层是否是「键: 值」（一个文件一份定义）：用来区分「顶层写了列表」（要告警）与「空文件 / 只有注释」（静默跳过）。 */
    public static boolean isMapping(String text) {
        return read(text) instanceof Map<?, ?>;
    }

    /** 键统一成字符串：YAML 里裸写 {@code 1: x} 之类会得到非字符串键，映射阶段才好处理。 */
    static Map<String, Object> stringKeyed(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }
}
