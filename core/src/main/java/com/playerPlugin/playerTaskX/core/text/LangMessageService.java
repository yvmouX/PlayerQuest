package com.playerPlugin.playerTaskX.core.text;

import cn.yvmou.ylib.message.MessageService;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * 消息服务包装器：把 YLib 多语言服务的输出统一交给 {@link TextRenderer} 渲染。
 *
 * <h2>为什么必须包一层</h2>
 * YLib 的 {@code MessageService} 只做 {@code &} → {@code §} 的转换，<b>不解析 MiniMessage</b>。
 * 而本项目要求「MiniMessage 为主、兼容传统 {@code &}」——若直接使用原始服务，
 * 语言文件里写的 {@code <gray>} 会被原样发给玩家（显示成标签文本）。
 * <p>
 * 这里在出口处统一渲染，于是语言文件与任务配置可以用同一套文本语法，
 * 管理员写 {@code &a} 或 {@code <green>} 都能正常工作。
 *
 * <h2>为什么不用装饰器模式实现全部方法转发</h2>
 * 实现 {@link MessageService} 接口而非仅暴露少量方法：漏掉一个方法会在<b>编译期</b>暴露，
 * 而不是等到某个冷门调用点运行时才发现消息没被渲染。
 */
public final class LangMessageService implements MessageService {

    private final MessageService delegate;

    public LangMessageService(MessageService delegate) {
        this.delegate = delegate;
    }

    /** 底层服务，供需要原始行为的场景使用（例如已经是纯文本的日志）。 */
    public MessageService delegate() {
        return delegate;
    }

    @Override
    public String getLanguage() {
        return delegate.getLanguage();
    }

    @Override
    public boolean setLanguage(String languageCode) {
        return delegate.setLanguage(languageCode);
    }

    @Override
    public List<String> getAvailableLanguages() {
        return delegate.getAvailableLanguages();
    }

    @Override
    public boolean has(String key) {
        return delegate.has(key);
    }

    /** 取渲染后的文本（含 § 颜色码）。 */
    @Override
    public String raw(String key, Object... args) {
        return TextRenderer.render(delegate.raw(key, args));
    }

    @Override
    public String raw(CommandSender sender, String key, Object... args) {
        return TextRenderer.render(delegate.raw(sender, key, args));
    }

    @Override
    public void send(CommandSender sender, String key, Object... args) {
        delegate.sendRaw(sender, raw(sender, key, args));
    }

    /** 已渲染的文本直接发送；这里也过一遍渲染以容忍调用方传入未渲染的原文。 */
    @Override
    public void sendRaw(CommandSender sender, String message) {
        delegate.sendRaw(sender, TextRenderer.render(message));
    }

    @Override
    public String prefix() {
        return TextRenderer.render(delegate.prefix());
    }

    @Override
    public String getMinecraftLanguageCode(String languageCode) {
        return delegate.getMinecraftLanguageCode(languageCode);
    }

    @Override
    public void reload() {
        delegate.reload();
    }
}
