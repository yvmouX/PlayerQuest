package com.playerPlugin.playerTaskX.core.storage.yaml;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * YAML 文本 ⇄ 普通对象（{@code Map} / {@code List} / 标量）的编解码。
 *
 * <h2>这里只做「文本 ⇄ 对象」，不做字段映射</h2>
 * 字段名与形状由 {@code QuestJson} / {@code PresetJson} 定义（它们就是编辑器 JSON 契约），
 * 本类只负责把同一份对象写成 YAML、或从 YAML 读回来。这样「YAML 文件 / 导出文件 /
 * 编辑器 JSON」三处的字段名永远只有一份来源。
 *
 * <h2>写：把「看起来像别的类型」的字符串加引号</h2>
 * YAML 1.1 把裸写的 {@code yes} / {@code no} / {@code on} / {@code off} / {@code ~}
 * 读成布尔或 null，把 {@code 1.20} 读成浮点、把 {@code 012} 读成八进制。
 * 任务里这些值完全可能合法（发言关键词 "yes"、材质名 "NO"、命令名 "on"…），
 * 一旦失去引号，读回来就变成了另一个值——本项目的语言文件已经在同一类陷阱上栽过一次
 * （{@code common.yes} 从未生效）。因此写出时凡是命中这些模式的字符串一律加单引号，
 * 并由 {@code YamlTextTest} 钉住往返不变。
 */
public final class YamlText {

    /**
     * 需要加引号的字符串：YAML 1.1 的布尔/null 字面量、纯数字、以及有特殊含义的首字符。
     * <p>
     * 保守一些没有坏处——多一对引号只是不好看，少一对引号是数据变了。
     */
    private static final Pattern AMBIGUOUS = Pattern.compile(
            "(?i)^(y|yes|n|no|on|off|true|false|null|~)$"
                    + "|^[+-]?(\\d[\\d_]*)(\\.\\d*)?([eE][+-]?\\d+)?$"
                    + "|^[+-]?\\.(inf|nan)$"
                    + "|^0[xX][0-9a-fA-F]+$"
                    + "|^0[oO]?[0-7]+$");

    /** 首字符有特殊含义（指示符、注释、锚点、流式语法等）的字符串。 */
    private static final String SPECIAL_FIRST = "-?:,[]{}#&*!|>'\"%@` ";

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

    /**
     * 读取用的 {@link Yaml}：SafeConstructor + {@link StrictResolver}。
     * <p>
     * 只用这一个解析器读**所有** YAML（文件、导入、示例），因此「哪些写法会被当成字符串」
     * 只有一处定义。
     */
    private static Yaml reader() {
        LoaderOptions loaderOptions = new LoaderOptions();
        DumperOptions dumperOptions = new DumperOptions();
        return new Yaml(new CoreSchemaConstructor(loaderOptions), new Representer(dumperOptions),
                dumperOptions, loaderOptions, new StrictResolver());
    }

    /**
     * 按 YAML 1.2 core 的口径解析标量，只保留我们真正需要的隐式类型：
     * <ul>
     *   <li>{@code true/false} → 布尔；</li>
     *   <li>十进制整数 → 整数，小数/科学计数 → 浮点；</li>
     *   <li>{@code null/~/空} → null；</li>
     *   <li><b>其它一律是字符串</b>。</li>
     * </ul>
     *
     * <h2>为什么必须自定义</h2>
     * SnakeYAML 默认是 YAML 1.1 语义：{@code yes/no/on/off/y/n} 是布尔、
     * {@code 012} 是八进制、{@code 1:30} 是六十进制、{@code 2024-01-01} 是日期。
     * 任务配置里这些值全都有可能是合法字符串（材质名 {@code NO}、发言关键词 {@code yes}、
     * 命令名 {@code on}、鱼 id、任务 id…），一旦被隐式转换，读回来就变成了别的类型——
     * <b>实测 {@code target: NO} 会变成布尔 false</b>，而玩家只会看到「这个任务不涨进度」。
     * 本项目的语言文件已经在同一类陷阱上栽过一次（{@code common.yes} 从未生效）。
     */
    private static final class StrictResolver extends Resolver {

        private static final Pattern BOOL = Pattern.compile("^(?:true|True|TRUE|false|False|FALSE)$");
        /**
         * 整数：十进制（含前导零）、{@code 0x} 十六进制、{@code 0o} 八进制——与 YAML 1.2 core
         * 及前端的 js-yaml 一致。刻意不接受 {@code 1_000}（js-yaml 也当字符串）。
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

    /**
     * 按 YAML 1.2 core 解释整数。
     * <p>
     * 必须换掉内置实现：SnakeYAML 用的是 1.1 规则，会把 {@code 012} 读成八进制 10，
     * 而 1.2 core（以及前端的 js-yaml）认为它是十进制的 12。同一份文件在编辑器和加载器里
     * 得到不同的数字，正是最难查的那类问题。这里替换 {@code Tag.INT} 的构造器即可，
     * 其余标量行为仍由 {@link SafeConstructor} 负责。
     */
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

    /**
     * 解析「一份或多份定义」的通用形状，三种写法都接受：
     * <ul>
     *   <li>顶层是列表 → 多项（导出的整份清单就是这个形状）；</li>
     *   <li>顶层是映射且含 {@code wrapperKey}（如 {@code quests:}）→ 取它的列表；</li>
     *   <li>顶层是映射且不含 wrapperKey → 当成单项（<b>一个文件一个定义</b>就是这个形状）。</li>
     * </ul>
     * 宽松是刻意的：管理员手写时不该先记住「导入要列表、单文件要映射」。
     */
    public static List<Map<String, Object>> readDocuments(String text, String wrapperKey) {
        Object loaded = read(text);
        if (loaded instanceof Map<?, ?> map) {
            Map<String, Object> asMap = stringKeyed(map);
            if (asMap.containsKey(wrapperKey)) {
                Object wrapped = asMap.get(wrapperKey);
                if (!(wrapped instanceof List<?> list)) {
                    // 「有 wrapper 键但不是列表」几乎一定是写错了，明确报出来比当成单条定义强
                    throw new IllegalArgumentException(wrapperKey + " 必须是一个列表");
                }
                return mapEntries(list);
            }
            return List.of(asMap);
        }
        if (loaded instanceof List<?> list) {
            return mapEntries(list);
        }
        return List.of();
    }

    /** 列表里非映射的项直接跳过（注释、分隔线之类不该让整份导入失败）。 */
    private static List<Map<String, Object>> mapEntries(List<?> list) {
        java.util.List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> entry) {
                result.add(stringKeyed(entry));
            }
        }
        return result;
    }

    /** 键统一成字符串：YAML 里裸写 {@code 1: x} 之类会得到非字符串键，映射阶段才好处理。 */
    private static Map<String, Object> stringKeyed(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 写
    // ------------------------------------------------------------------

    /**
     * 对象 → YAML 文本（块状、缩进 2、不折行）。
     * <p>
     * 不折行很重要：折行会把一个长字符串拆成多行，读回来时换行位置变成空格，
     * 而任务描述、命令奖励里的引号与空格都是有意义的。
     */
    public static String write(Object document) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setWidth(Integer.MAX_VALUE);
        options.setSplitLines(false);
        options.setAllowUnicode(true);
        Yaml yaml = new Yaml(new QuotingRepresenter(options), options);
        return yaml.dump(document);
    }

    /**
     * 会为「看起来像别的类型」的字符串加引号的 Representer。
     * <p>
     * 这是本类存在的核心理由：默认 Representer 认为 {@code yes}、{@code 1.20}
     * 这类字符串可以裸写（YAML 1.2 语义下确实可以），但读的一方若按 1.1 解析就会变值。
     */
    private static final class QuotingRepresenter extends Representer {

        QuotingRepresenter(DumperOptions options) {
            super(options);
        }

        @Override
        protected org.yaml.snakeyaml.nodes.Node representScalar(Tag tag, String value, DumperOptions.ScalarStyle style) {
            DumperOptions.ScalarStyle chosen = style;
            if (Tag.STR.equals(tag) && needsQuoting(value)) {
                chosen = DumperOptions.ScalarStyle.SINGLE_QUOTED;
            }
            return super.representScalar(tag, value, chosen);
        }

        private static boolean needsQuoting(String value) {
            if (value.isEmpty()) {
                // 空串裸写会变成 null
                return true;
            }
            if (AMBIGUOUS.matcher(value).matches()) {
                return true;
            }
            char first = value.charAt(0);
            if (SPECIAL_FIRST.indexOf(first) >= 0) {
                return true;
            }
            // 行尾空格、冒号+空格、井号前有空格都会改变解析结果
            return value.endsWith(" ")
                    || value.contains(": ")
                    || value.contains(" #")
                    || value.indexOf('\n') >= 0
                    || value.indexOf('\t') >= 0;
        }
    }

    /**
     * 数字：{@code 64.0} 写成 {@code 64}，避免导出的 YAML 里出现一堆 {@code 64.0}。
     * <p>
     * 返回 {@code Long} 而不是 {@code BigDecimal}：SnakeYAML 会给 BigDecimal 打
     * {@code !!float} 标签（形如 {@code refreshCost: !!float '1000'}），既不好看也不像给
     * 人读的配置；整数值用 Long 就会写成裸的 {@code 1000}。
     */
    public static Object number(Object value) {
        if (value instanceof Double d && !d.isInfinite() && !d.isNaN() && d == Math.rint(d)
                && d >= Long.MIN_VALUE && d <= Long.MAX_VALUE) {
            return d.longValue();
        }
        if (value instanceof Float f && !f.isInfinite() && !f.isNaN() && f == Math.rint(f)) {
            return f.longValue();
        }
        return value;
    }

    /**
     * 按 key 排序后的属性表：导出的文件要能进版本控制，就得让同一份数据每次写出同样的字节。
     * <p>
     * 属性之间的顺序本来没有语义（每个字段由自己的 key 定位），而 {@code Map.of} 的迭代顺序
     * 不保证稳定，排序是唯一能让 diff 干净的做法。
     */
    public static Map<String, Object> sortedProperties(Map<String, Object> properties) {
        Map<String, Object> sorted = new java.util.TreeMap<>();
        if (properties != null) {
            sorted.putAll(properties);
        }
        return sorted;
    }
}
