package com.playerPlugin.playerTaskX.core.config;

import cn.yvmou.ylib.config.AutoConfiguration;
import cn.yvmou.ylib.config.ConfigValue;
import com.playerPlugin.playerTaskX.api.model.QuestType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 插件主配置（config.yml）。
 * <p>
 * 注意 YLib 配置的约束：字段类型只支持标量 / List / Map&lt;String,V&gt;，
 * 且**顶层不能是自定义 POJO**（会抛 IllegalArgumentException），
 * 因此嵌套结构一律用 {@code Map<String, Pojo>} 或点分路径表达。
 * <p>
 * {@code version} 变化会让 YLib 备份旧 config.yml 并按新结构重新生成
 * （只迁移仍然存在的字段），旧键因此自动清理——删配置项时要一并 bump 它。
 */
@AutoConfiguration(configFile = "config.yml", version = "2.1.0")
public class PluginConfig {

    @ConfigValue(value = "language.default", description = "默认语言（语言文件位于 lang/ 目录）")
    private String languageDefault = "zh_CN";

    @ConfigValue(value = "language.available", description = "可用语言列表")
    private java.util.List<String> languageAvailable = java.util.List.of("zh_CN", "en");

    @ConfigValue(value = "language.use-client-locale", description = "是否优先使用玩家客户端语言")
    private boolean languageUseClientLocale = true;

    @ConfigValue(value = "storage.type",
            description = "存储后端：SQLITE（默认，本地文件库、开箱即用）或 MYSQL（多服共享时必须）。"
                    + "任务定义、预设与玩家数据都存在这一个库里")
    private String storageType = "SQLITE";

    @ConfigValue(value = "storage.sqlite.file", description = "SQLite 数据库文件名（相对插件数据目录）")
    private String sqliteFile = "data/playerTaskX.db";

    @ConfigValue(value = "storage.mysql", description = "MySQL 连接配置（storage.type=MYSQL 时生效）")
    private Map<String, MysqlSettings> mysql = defaultMysql();

    @ConfigValue(value = "definitions.read-files",
            description = "是否读取插件目录下 quests/ 与 presets/ 里的 YAML 定义（默认开启）。"
                    + "一个文件一个定义，文件名即 id（文件内容里写了 id 则以内容为准）；"
                    + "与数据库里同 id 的定义冲突时以数据库为准，文件里的那份会被忽略并记入日志。"
                    + "注意：文件里的定义不写进数据库，因此游戏内与网页编辑器的修改只对数据库里的定义生效，"
                    + "文件里的那些在编辑器里是只读的（要改就去改文件，或用导出/导入把它搬进数据库）。"
                    + "⚠ 用 MySQL 多服共享时，把任务写在 YAML 文件里会导致各服定义不一致——文件不会跨服同步；"
                    + "只有「有意让不同服务器的任务存在差异」时才这样用，否则请把定义放进数据库。")
    private boolean definitionsReadFiles = true;

    @ConfigValue(value = "progress.actionbar", description = "是否用 actionbar 推送任务进度")
    private boolean actionbarEnabled = true;

    @ConfigValue(value = "progress.actionbar-interval", description = "actionbar 进度刷新间隔（tick，20 = 1 秒）")
    private int actionbarInterval = 20;

    @ConfigValue(value = "progress.title-on-complete", description = "任务完成时是否发送 title 提醒")
    private boolean titleOnComplete = true;

    @ConfigValue(value = "refresh-currency",
            description = "周期任务的刷新费用使用哪种货币，按顺序取第一个可用的：MONEY（金币，需经济插件）、"
                    + "POINTS（点券/PlayerPoints）。想优先扣点券就写成 [POINTS, MONEY]；"
                    + "只写 [POINTS] 表示完全不碰经济插件。两者都不可用时刷新直接不可用并提示玩家，"
                    + "不会白送刷新。四种周期共用这一处配置——它说的是「这台服务器有什么货币」，"
                    + "不是某个周期的属性")
    private java.util.List<String> refreshCurrency = defaultCurrencyOrder();

    private static java.util.List<String> defaultCurrencyOrder() {
        return java.util.List.of("MONEY", "POINTS");
    }

    /**
     * 刷新费用使用的货币顺序。
     * <p>
     * 列表为空时退回内置顺序，避免用户清空后刷新功能失效。
     */
    public java.util.List<String> getRefreshCurrency() {
        if (refreshCurrency == null || refreshCurrency.isEmpty()) {
            return defaultCurrencyOrder();
        }
        return refreshCurrency;
    }

    @ConfigValue(value = "periodic", description = "周期任务：daily / weekly / monthly / custom 各一段配置。"
            + "enabled 决定这种周期是否启用；amount 是每位玩家每周期抽取的数量；"
            + "reset-hour 是重置时刻（早于它算上一个周期）；"
            + "weekly 用 reset-weekday（MONDAY…SUNDAY），monthly 用 reset-month-day（1-28），"
            + "custom 用 period（周期长度，如 3d / 12h，按固定锚点取整、跨服一致）；"
            + "refresh-cost / refresh-limit 是玩家刷新的费用与次数上限；pool 留空则取该类型的全部任务")
    private Map<String, PeriodSettings> periodic = defaultPeriodic();

    /** 四种周期的默认配置：每日默认开启，其余默认关闭（想用再开，免得凭空多出一堆任务）。 */
    private static Map<String, PeriodSettings> defaultPeriodic() {
        Map<String, PeriodSettings> map = new LinkedHashMap<>();
        map.put("daily", PeriodSettings.daily());
        map.put("weekly", PeriodSettings.weekly());
        map.put("monthly", PeriodSettings.monthly());
        map.put("custom", PeriodSettings.custom());
        return map;
    }

    /**
     * 某种周期的配置；缺失时退回该类型的内置默认值。
     * <p>
     * 配置文件被删掉一段（或旧版本配置里没有这一项）时不能让周期任务静默失效，
     * 因此这里永远返回一个可用的对象。
     */
    public PeriodSettings periodic(QuestType type) {
        PeriodSettings settings = periodic == null ? null : periodic.get(type.name().toLowerCase(java.util.Locale.ROOT));
        return settings == null ? PeriodSettings.defaultsFor(type) : settings;
    }

    /** 是否有任何一种周期任务处于启用状态（没有的话连定时检查都不用起）。 */
    public boolean isAnyPeriodicEnabled() {
        for (QuestType type : QuestType.values()) {
            if (type.isPeriodic() && periodic(type).isEnabled()) {
                return true;
            }
        }
        return false;
    }

    @ConfigValue(value = "editor.enabled", description = "是否启用内置网页编辑器")
    private boolean editorEnabled = true;

    @ConfigValue(value = "editor.port", description = "网页编辑器监听端口；被占用时自动 +1 重试，以启动日志为准")
    private int editorPort = 28080;

    @ConfigValue(value = "editor.token", description = "编辑器访问令牌（留空表示不校验，仅建议本机使用）")
    private String editorToken = "";

    @ConfigValue(value = "editor.fetch-chinese-names",
            description = "编辑器图标列表是否下载中文译名（Minecraft 服务端不含中文语言文件）；"
                    + "关闭后仅显示英文名。也可手动把 zh_cn.json 放到 editor/ 目录，插件会优先使用本地文件")
    private boolean editorFetchChineseNames = true;

    private static Map<String, MysqlSettings> defaultMysql() {
        Map<String, MysqlSettings> map = new LinkedHashMap<>();
        MysqlSettings settings = new MysqlSettings();
        map.put("default", settings);
        return map;
    }

    public String getLanguageDefault() {
        return languageDefault;
    }

    public java.util.List<String> getLanguageAvailable() {
        return languageAvailable;
    }

    public boolean isLanguageUseClientLocale() {
        return languageUseClientLocale;
    }

    public String getStorageType() {
        return storageType;
    }

    public String getSqliteFile() {
        return sqliteFile;
    }

    /** 取 MySQL 配置，未配置时返回一份默认值，避免 NPE。 */
    public MysqlSettings getMysql() {
        MysqlSettings settings = mysql == null ? null : mysql.get("default");
        return settings == null ? new MysqlSettings() : settings;
    }

    /** 是否读取 quests/ 与 presets/ 里的 YAML 定义（只读来源，库优先）。 */
    public boolean isDefinitionsReadFiles() {
        return definitionsReadFiles;
    }

    public boolean isActionbarEnabled() {
        return actionbarEnabled;
    }

    public int getActionbarInterval() {
        return Math.max(5, actionbarInterval);
    }

    public boolean isTitleOnComplete() {
        return titleOnComplete;
    }

    public boolean isEditorEnabled() {
        return editorEnabled;
    }

    public int getEditorPort() {
        return editorPort;
    }

    public String getEditorToken() {
        return editorToken;
    }

    public boolean isEditorFetchChineseNames() {
        return editorFetchChineseNames;
    }

    /**
     * MySQL 连接设置（放在 Map 里以绕开 YLib 不支持顶层嵌套 POJO 的限制）。
     */
    public static class MysqlSettings {

        @ConfigValue("host")
        private String host = "127.0.0.1";

        @ConfigValue("port")
        private int port = 3306;

        @ConfigValue("database")
        private String database = "playerTaskX";

        @ConfigValue("username")
        private String username = "root";

        @ConfigValue("password")
        private String password = "";

        @ConfigValue("parameters")
        private String parameters = "useSSL=false&characterEncoding=utf8&serverTimezone=Asia/Shanghai";

        @ConfigValue("pool-size")
        private int poolSize = 8;

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public String getDatabase() {
            return database;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public String getParameters() {
            return parameters;
        }

        public int getPoolSize() {
            return Math.max(2, poolSize);
        }

        public String jdbcUrl() {
            String suffix = (parameters == null || parameters.isBlank()) ? "" : "?" + parameters;
            return "jdbc:mysql://" + host + ":" + port + "/" + database + suffix;
        }
    }
}
