package com.playerPlugin.playerTaskX.core.storage;

/** 数据库方言：集中处理 SQLite 与 MySQL 的语法差异；约定所有 SQL 字符串只允许出现在本包内，其它包不得出现。 */
public enum Dialect {

    SQLITE {
        @Override
        public String textType() {
            return "TEXT";
        }

        @Override
        public String upsert(String table, String keyColumns, String columns) {
            return "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders(columns)
                    + ") ON CONFLICT(" + keyColumns + ") DO UPDATE SET "
                    // SQLite 的 DO UPDATE 只能用 excluded.<col> 引用待插入行；
                    // VALUES(col) 是 MySQL 语法，在 SQLite 中会被解析为复合 SELECT 从而语法错误。
                    + assignments(columns, keyColumns, "excluded.", "");
        }

        @Override
        public String tableOption() {
            return "";
        }
    },

    MYSQL {
        @Override
        public String textType() {
            return "TEXT";
        }

        @Override
        public String upsert(String table, String keyColumns, String columns) {
            return "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders(columns)
                    + ") ON DUPLICATE KEY UPDATE " + assignments(columns, keyColumns, "VALUES(", ")");
        }

        @Override
        public String tableOption() {
            return " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        }
    };

    /** 长文本列类型。 */
    public abstract String textType();

    /** 生成 upsert 语句：SQLite 用 {@code ON CONFLICT}，MySQL 用 {@code ON DUPLICATE KEY}。 */
    public abstract String upsert(String table, String keyColumns, String columns);

    /** 建表尾部选项（MySQL 需要指定引擎与字符集）。 */
    public abstract String tableOption();

    /**
     * 安全引用标识符。
     * <p>
     * 必需而非可选：{@code quest_objective} 的索引列名为 {@code idx}，
     * 这在 MySQL 8 中是保留字，裸写会导致语法错误。
     */
    public String quote(String identifier) {
        return "`" + identifier + "`";
    }

    private static String placeholders(String columns) {
        int count = columns.split(",").length;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) builder.append(", ");
            builder.append('?');
        }
        return builder.toString();
    }

    private static String assignments(String columns, String keyColumns, String valuePrefix, String valueSuffix) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (String key : keyColumns.split(",")) {
            keys.add(key.trim());
        }
        StringBuilder builder = new StringBuilder();
        for (String column : columns.split(",")) {
            String name = column.trim();
            if (keys.contains(name)) continue;
            if (builder.length() > 0) builder.append(", ");
            // SQLite 用 excluded.name，MySQL 用 VALUES(name)——由调用方给出前后缀
            builder.append(name).append(" = ").append(valuePrefix).append(name).append(valueSuffix);
        }
        return builder.toString();
    }
}
