/**
 * 事件监听层：把 Bukkit 事件翻译成 {@code ProgressContext} 投递给引擎，仅此而已。
 *
 * <h2>为什么按事件域分组，而不是一个动作一个类</h2>
 * 「这个动作算不算命中、记多少进度」已经按动作组织在
 * {@code com.playerPlugin.playerTaskX.core.objective} 的各 ObjectiveType 里，
 * 编辑器与 GUI 的表单也由它的 schema 自动生成。监听器若再按动作拆一套，
 * 就会出现两套平行的按动作结构需要同步维护（新增动作要同时动两处）；
 * 按 Bukkit 事件域分组则与 {@code org.bukkit.event} 的包分类一致，
 * 找 handler 凭直觉即可。
 *
 * <h2>分组清单</h2>
 * <ul>
 *   <li>{@link BlockListener} —— 方块域：挖掘、放置、对方块交互</li>
 *   <li>{@link EntityListener} —— 实体域：击杀、垂钓、剪切、繁殖、驯服、与实体交互</li>
 *   <li>{@link ItemListener} —— 物品域：合成、消耗、附魔</li>
 *   <li>{@link TextListener} —— 文本输入：发言、执行命令</li>
 *   <li>{@link PlayerListener} —— 会话生命周期（登录/登出），不推进度</li>
 * </ul>
 *
 * <h2>约定</h2>
 * <ul>
 *   <li>判定逻辑不写在这里：不允许出现针对具体目标类型的分支（见 {@link ProgressListener}）；</li>
 *   <li>每个动作的 handler 必须带 javadoc，写清动作语义与该事件特有的坑
 *       （异步、双触发、数量口径之类），这是移植与排查时的第一手资料；</li>
 *   <li>新增动作先按事件域归位，确实没有归属再新建域类，而不是默认新建动作类。</li>
 * </ul>
 */
package com.playerPlugin.playerTaskX.core.listener;
