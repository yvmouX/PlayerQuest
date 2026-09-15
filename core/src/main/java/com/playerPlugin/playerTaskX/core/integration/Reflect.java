package com.playerPlugin.playerTaskX.core.integration;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * 软依赖接入用的反射小工具：按名字取类取方法，取不到一律返回 {@code null}。
 *
 * <h2>为什么吞掉异常</h2>
 * 这几家的 API 都是「别人的插件 jar」，类名与方法签名随版本变。取不到只应表示
 * <b>这个功能不可用</b>（调用方各自降级并记一条 warn），不该让本插件起不来。
 * 包住 {@code Throwable} 而不是 {@code ReflectiveOperationException}：
 * {@code NoClassDefFoundError} 这类链接错误也会在这里冒出来。
 *
 * <h2>为什么设 accessible</h2>
 * 取到的是对方插件的公开方法，但它的类可能不在本插件的模块/类加载器可见性范围内；
 * 设一次比在热路径上碰运气便宜。
 */
final class Reflect {

    private Reflect() {
    }

    /** 按全限定名取类；不存在或链接失败时返回 {@code null}。 */
    @Nullable
    static Class<?> findClass(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 取公开方法；不存在或签名不符时返回 {@code null}。
     *
     * @param owner      方法所在类
     * @param name       方法名
     * @param parameters 形参类型；无参方法不传
     */
    @Nullable
    static Method method(Class<?> owner, String name, Class<?>... parameters) {
        try {
            Method method = owner.getMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (Throwable e) {
            return null;
        }
    }
}
