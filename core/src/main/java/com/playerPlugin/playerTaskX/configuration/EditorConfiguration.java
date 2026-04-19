package com.playerPlugin.playerTaskX.configuration;

import cn.yvmou.ylib.api.config.AutoConfiguration;
import cn.yvmou.ylib.api.config.ConfigValue;

@AutoConfiguration(configFile = "editor.yml", version = "1.0.0")
public class EditorConfiguration {

    @ConfigValue(value = "editor.port", description = "编辑器端口")
    private int port = 8080;

    @ConfigValue(value = "editor.host", description = "编辑器主机地址")
    private String host = "127.0.0.1";

    public EditorConfiguration() {
    }

    public int getPort() {
        return port;
    }

    public String getHost() {
        return host;
    }
}
