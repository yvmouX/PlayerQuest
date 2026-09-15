package com.playerPlugin.playerTaskX.core.integration;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/** 软依赖接入用的反射工具：按名字取类取方法，取不到（含 NoClassDefFoundError 这类链接错误）一律返回 {@code null}，由调用方降级。 */
public final class Reflect {

    private Reflect() {
    }

    /** 按全限定名取类；不存在或链接失败时返回 {@code null}。 */
    @Nullable
    public static Class<?> findClass(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable e) {
            return null;
        }
    }

    /** 取公开方法并设 accessible；不存在或签名不符时返回 {@code null}。 */
    @Nullable
    public static Method method(Class<?> owner, String name, Class<?>... parameters) {
        try {
            Method method = owner.getMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (Throwable e) {
            return null;
        }
    }
}
