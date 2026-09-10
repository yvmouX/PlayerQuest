package com.playerPlugin.playerTaskX.core.config;

import cn.yvmou.ylib.config.AutoConfiguration;
import cn.yvmou.ylib.config.ConfigValue;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 插件主配置（config.yml）。
 * <p>
 * 注意 YLib 配置的约束：字段类型只支持标量 / List / Map&lt;String,V&gt;，
 * 且**顶层不能是自定义 POJO**（会抛 IllegalArgumentException），
 * 因此嵌套结构一律用 {@code Map<String, Pojo>} 或点分路径表达。
 */
@AutoConfiguration(configFile = "config.yml", version = "2.0.0")
public class PluginConfig {

    @ConfigValue(value = "language.default", description = "默认语言（语言文件位于 lang/ 目录）")
    private String languageDefault = "zh_CN";

    @ConfigValue(value = "language.available", description = "可用语言列表")
    private java.util.List<String> languageAvailable = java.util.List.of("zh_CN", "en");

    @ConfigValue(value = "language.use-client-locale", description = "是否优先使用玩家客户端语言")
    private boolean languageUseClientLocale = true;

    @ConfigValue(value = "storage.type", description = "存储类型：SQLITE 或 MYSQL")
    private String storageType = "SQLITE";

    @ConfigValue(value = "storage.sqlite.file", description = "SQLite 数据库文件名（相对插件数据目录）")
    private String sqliteFile = "data/playerTaskX.db";

    @ConfigValue(value = "storage.mysql", description = "MySQL 连接配置（storage.type=MYSQL 时生效）")
    private Map<String, MysqlSettings> mysql = defaultMysql();

    @ConfigValue(value = "progress.actionbar", description = "是否用 actionbar 推送任务进度")
    private boolean actionbarEnabled = true;

    @ConfigValue(value = "progress.actionbar-interval", description = "actionbar 进度刷新间隔（tick，20 = 1 秒）")
    private int actionbarInterval = 20;

    @ConfigValue(value = "progress.title-on-complete", description = "任务完成时是否发送 title 提醒")
    private boolean titleOnComplete = true;

    @ConfigValue(value = "daily.enabled", description = "是否启用每日任务")
    private boolean dailyEnabled = true;

    @ConfigValue(value = "daily.pool", description = "每日任务池（任务 id 列表，留空则取所有 type=DAILY 的任务）")
    private java.util.List<String> dailyPool = java.util.List.of();

    @ConfigValue(value = "daily.amount", description = "每位玩家每日抽取的任务数量")
    private int dailyAmount = 3;

    @ConfigValue(value = "daily.reset-hour", description = "每日重置时间（小时，0-23）")
    private int dailyResetHour = 4;

    @ConfigValue(value = "daily.refresh-cost", description = "刷新每日任务的默认费用（任务可单独覆盖）")
    private double dailyRefreshCost = 1000.0;

    @ConfigValue(value = "daily.refresh-currency", description = "刷新费用使用的货币：MONEY（金币）、POINTS（点券）、QUEST_COIN（任务币）、AUTO（自动选择可用货币）")
    private String dailyRefreshCurrency = "AUTO";

    @ConfigValue(value = "daily.refresh-limit", description = "每日最多刷新次数")
    private int dailyRefreshLimit = 3;

    @ConfigValue(value = "editor.enabled", description = "是否启用内置网页编辑器")
    private boolean editorEnabled = true;

    @ConfigValue(value = "editor.port", description = "网页编辑器监听端口")
    private int editorPort = 8080;

    @ConfigValue(value = "editor.token", description = "编辑器访问令牌（留空表示不校验，仅建议本机使用）")
    private String editorToken = "";

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

    public boolean isActionbarEnabled() {
        return actionbarEnabled;
    }

    public int getActionbarInterval() {
        return Math.max(5, actionbarInterval);
    }

    public boolean isTitleOnComplete() {
        return titleOnComplete;
    }

    public boolean isDailyEnabled() {
        return dailyEnabled;
    }

    public java.util.List<String> getDailyPool() {
        return dailyPool;
    }

    public int getDailyAmount() {
        return Math.max(1, dailyAmount);
    }

    public int getDailyResetHour() {
        return Math.min(23, Math.max(0, dailyResetHour));
    }

    public double getDailyRefreshCost() {
        return dailyRefreshCost;
    }

    /**
     * 刷新费用使用的货币，取值 {@code MONEY} / {@code POINTS} / {@code QUEST_COIN} / {@code AUTO}。
     * <p>
     * {@code AUTO} 保持旧行为：优先金币，其次点券，都没有则报错提示装经济插件。
     */
    public String getDailyRefreshCurrency() {
        return dailyRefreshCurrency == null ? "AUTO" : dailyRefreshCurrency.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public int getDailyRefreshLimit() {
        return Math.max(0, dailyRefreshLimit);
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
