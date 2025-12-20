package com.playerPlugin.playerTaskX.configuration;

import cn.yvmou.ylib.api.config.AutoConfiguration;
import cn.yvmou.ylib.api.config.ConfigValue;

import java.util.List;

@AutoConfiguration(configFile = "editor.yml", version = "1.0.1")
public class EditorConfiguration {
    @ConfigValue(
            value = "enable",
            description = "是否启用编辑器"
    )
    private String enable = "true";

    @ConfigValue(
            value = "port",
            description = "编辑器端口",
            validation = "range:1-10000"
    )
    private int port = 8080;

    @ConfigValue(
            value = "token-expiry",
            description = "token有效期",
            validation = "range:1-10000"
    )
    private int tokenExpiry = 3600;

    @ConfigValue(
            value = "allowed-ips",
            description = "允许访问的ip"
    )
    private List<String> allowedIps = List.of("127.0.0.1");

    @ConfigValue(
            value = "sll.enable",
            description = "是否启用ssl"
    )
    private String sll_enable = "false";

    @ConfigValue(
            value = "sll.keystore-path",
            description = "ssl证书路径"
    )
    private String sll_keystorePath;

    @ConfigValue(
            value = "sll.keystore-password",
            description = "ssl证书密码"
    )
    private String sll_keystorePassword;

    public EditorConfiguration() {
    }

    public EditorConfiguration(String enable, int port, int tokenExpiry, List<String> allowedIps, String sll_enable, String sll_keystorePath, String sll_keystorePassword) {
        this.enable = enable;
        this.port = port;
        this.tokenExpiry = tokenExpiry;
        this.allowedIps = allowedIps;
        this.sll_enable = sll_enable;
        this.sll_keystorePath = sll_keystorePath;
        this.sll_keystorePassword = sll_keystorePassword;
    }

    /**
     * 获取
     * @return enable
     */
    public String getEnable() {
        return enable;
    }

    /**
     * 设置
     * @param enable
     */
    public void setEnable(String enable) {
        this.enable = enable;
    }

    /**
     * 获取
     * @return port
     */
    public int getPort() {
        return port;
    }

    /**
     * 设置
     * @param port
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * 获取
     * @return tokenExpiry
     */
    public int getTokenExpiry() {
        return tokenExpiry;
    }

    /**
     * 设置
     * @param tokenExpiry
     */
    public void setTokenExpiry(int tokenExpiry) {
        this.tokenExpiry = tokenExpiry;
    }

    /**
     * 获取
     * @return allowedIps
     */
    public List<String> getAllowedIps() {
        return allowedIps;
    }

    /**
     * 设置
     * @param allowedIps
     */
    public void setAllowedIps(List<String> allowedIps) {
        this.allowedIps = allowedIps;
    }

    /**
     * 获取
     * @return sll_enable
     */
    public String getSll_enable() {
        return sll_enable;
    }

    /**
     * 设置
     * @param sll_enable
     */
    public void setSll_enable(String sll_enable) {
        this.sll_enable = sll_enable;
    }

    /**
     * 获取
     * @return sll_keystorePath
     */
    public String getSll_keystorePath() {
        return sll_keystorePath;
    }

    /**
     * 设置
     * @param sll_keystorePath
     */
    public void setSll_keystorePath(String sll_keystorePath) {
        this.sll_keystorePath = sll_keystorePath;
    }

    /**
     * 获取
     * @return sll_keystorePassword
     */
    public String getSll_keystorePassword() {
        return sll_keystorePassword;
    }

    /**
     * 设置
     * @param sll_keystorePassword
     */
    public void setSll_keystorePassword(String sll_keystorePassword) {
        this.sll_keystorePassword = sll_keystorePassword;
    }

    public String toString() {
        return "EditorConfiguration{enable = " + enable + ", port = " + port + ", tokenExpiry = " + tokenExpiry + ", allowedIps = " + allowedIps + ", sll_enable = " + sll_enable + ", sll_keystorePath = " + sll_keystorePath + ", sll_keystorePassword = " + sll_keystorePassword + "}";
    }
}
