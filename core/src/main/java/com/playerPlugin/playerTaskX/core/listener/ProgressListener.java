package com.playerPlugin.playerTaskX.core.listener;

import com.playerPlugin.playerTaskX.api.objective.ProgressContext;
import com.playerPlugin.playerTaskX.core.engine.ApplyResult;
import com.playerPlugin.playerTaskX.core.engine.ProgressService;

import java.util.function.Consumer;

/** 监听器基类：只做「事件 → {@link ProgressContext}」的翻译与投递，判定逻辑一律不写在这里。 */
public abstract class ProgressListener {

    protected final ProgressService progress;
    private final Consumer<ApplyResult> onProgress;

    protected ProgressListener(ProgressService progress, Consumer<ApplyResult> onProgress) {
        this.progress = progress;
        this.onProgress = onProgress;
    }

    /**
     * 投递一次动作。
     * <p>
     * 异常在此兜底：某个任务配置错误不应该让玩家的一次挖掘事件抛到服务端控制台。
     */
    protected void push(ProgressContext context) {
        ApplyResult result;
        try {
            result = progress.apply(context);
        } catch (RuntimeException e) {
            org.bukkit.Bukkit.getLogger().warning("[PlayerTaskX] 进度处理失败: " + e.getMessage());
            return;
        }
        if (result.changed() && onProgress != null) {
            onProgress.accept(result);
        }
    }
}
