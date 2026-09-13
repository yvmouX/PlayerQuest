package com.playerPlugin.playerTaskX.core.integration;

import com.playerPlugin.playerTaskX.api.model.Quest;
import com.playerPlugin.playerTaskX.api.model.QuestObjective;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * MythicMobs 接入点：把「服务器上有没有 MythicMobs、怪物的内部名是什么」收在这一个文件里。
 *
 * <h2>目标语法：{@code mythic:<怪物内部名>}</h2>
 * 击杀任务不需要新的目标类型——{@code kill} 的 {@code target} 里直接写
 * {@code mythic:SkeletalKnight}（可以和原版名混写，如 {@code ZOMBIE,mythic:SkeletalKnight}）。
 * 前缀由本类定义，{@link #PREFIX} 是唯一出处。
 *
 * <h2>为什么实现类是反射加载的</h2>
 * {@link MythicMobs5Hook} 用反射调用 MythicMobs，本身不 import 任何它的类型，
 * 但它仍然只在确认「装了 5.x」之后才被创建：否则一次误判就会在日志里留下无意义的失败。
 * 本类自己不碰 MythicMobs 的任何类，因此没装该插件的服务端加载它是完全安全的。
 *
 * <h2>只支持 5.x</h2>
 * MythicMobs 4 与 5 是两套完全不同的 API（{@code io.lumine.xikage.mythicmobs} 与
 * {@code io.lumine.mythic}），而 4.x 不支持本插件要求的 1.21+，因此不为它留兼容分支：
 * 检测到 4.x 时明确记一条 warn，让管理员知道该升级什么。
 */
public interface MythicMobsHook {

    /** 目标配置里的前缀，如 {@code mythic:SkeletalKnight}。 */
    String PREFIX = "mythic:";

    /** 插件名（同时也是软依赖探测用的名字）。 */
    String PLUGIN = "MythicMobs";

    /**
     * 实体对应的 MythicMobs 怪物内部名；不是 MythicMobs 怪物时返回 {@code null}。
     * <p>
     * 返回值不带 {@link #PREFIX}：拼接前缀是展示与匹配层的事，这里只回答「叫什么」。
     */
    @Nullable
    String mobId(LivingEntity entity);

    /**
     * 全部已加载的怪物内部名（供编辑器的实体选择器列出）。
     * <p>
     * 取不到时返回空表而不是抛异常：这只是编辑器的便利功能。
     */
    List<String> mobIds();

    // ------------------------------------------------------------------
    // 可用性与探测
    // ------------------------------------------------------------------

    /** 服务端是否装了我们支持的 MythicMobs（5.x）。 */
    static boolean supported() {
        return SoftDependency.versionStartsWith(PLUGIN, "5.");
    }

    /**
     * 创建接入实例；未安装、版本不支持或加载失败时返回 {@code null}（都会记日志）。
     * <p>
     * {@code null} 是正常状态：原版击杀任务照常工作，只是 {@code mythic:} 目标不会被匹配。
     */
    @Nullable
    static MythicMobsHook create() {
        if (!SoftDependency.isPresent(PLUGIN)) {
            return null;
        }
        String version = SoftDependency.versionOf(PLUGIN);
        if (!supported()) {
            log(Level.WARNING, "检测到 MythicMobs " + version + "，本插件只支持 5.x："
                    + "mythic: 目标不会被匹配（MythicMobs 4.x 不支持 1.21+，建议升级）");
            return null;
        }
        try {
            MythicMobsHook hook = MythicMobs5Hook.create();
            log(Level.INFO, "已接入 MythicMobs " + version + "：击杀任务的目标可写 " + PREFIX + "<怪物id>");
            return hook;
        } catch (Throwable e) {
            // 反射解析失败（版本内部改名等）只该损失这一个功能
            log(Level.WARNING, "接入 MythicMobs " + version + " 失败，"
                    + PREFIX + " 目标将不可用: " + e);
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 目标语法校验
    // ------------------------------------------------------------------

    /** 该 target 值是否是 MythicMobs 目标（可能写在逗号分隔的多值里）。 */
    static boolean isMythicTarget(String target) {
        if (target == null || target.isBlank()) {
            return false;
        }
        for (String candidate : target.split(",")) {
            if (candidate.trim().toLowerCase(java.util.Locale.ROOT).startsWith(PREFIX)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 任务里写了 {@code mythic:} 目标但服务端不支持时的问题清单（没有则空表）。
     * <p>
     * 这类目标<b>永远</b>命中不了，玩家侧只表现为「杀了不涨进度」，因此必须让管理员看到。
     * 由 {@code QuestAdminService.validate} 调用，于是编辑器、{@code /ptxa list}、
     * 管理界面三处会同时标出来。
     */
    static List<String> targetProblems(Quest quest) {
        if (quest == null || supported()) {
            return List.of();
        }
        List<String> problems = new ArrayList<>();
        for (QuestObjective objective : quest.objectives()) {
            String target = objective.string("target", "");
            if (isMythicTarget(target)) {
                problems.add("目标 " + target + " 需要 MythicMobs 5.x（当前"
                        + (SoftDependency.isPresent(PLUGIN) ? "版本不受支持" : "未安装") + "）");
            }
        }
        return problems;
    }

    private static void log(Level level, String message) {
        try {
            if (Bukkit.getLogger() != null) {
                Bukkit.getLogger().log(level, "[PlayerTaskX] " + message);
            }
        } catch (Throwable ignored) {
            // 日志出口在引导阶段可能不可用，记不了日志不该影响启动
        }
    }
}
