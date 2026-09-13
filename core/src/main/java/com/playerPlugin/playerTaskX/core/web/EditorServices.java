package com.playerPlugin.playerTaskX.core.web;

import com.playerPlugin.playerTaskX.core.quest.QuestAdminService;
import com.playerPlugin.playerTaskX.core.registry.ObjectiveRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.QuestRegistryImpl;
import com.playerPlugin.playerTaskX.core.registry.RewardRegistryImpl;
import com.playerPlugin.playerTaskX.core.storage.PlayerQuestRepository;
import com.playerPlugin.playerTaskX.core.storage.PresetRepository;

import java.io.File;
import java.io.InputStream;
import java.util.List;

/**
 * {@link EditorApi} 需要的宿主能力——<b>为了能脱离服务端测这一层</b>，不是为了抽象而抽象。
 *
 * <h2>为什么要有这一层</h2>
 * 拆出本接口之前，{@code EditorApi} 直接持有插件单例 {@code PlayerTaskX}。后果是这 14 条
 * 路由<b>一条都无法在单测里跑</b>：单测造不出 {@code JavaPlugin}（它的构造与生命周期绑在
 * Bukkit 上），于是「拆分类、统一请求体样板、删未用字段」这类改动只能靠手工起服打请求验证——
 * 重复、易漏，而且从没进过一次 CI 回归。
 * <p>
 * 换成接口后，测试给一份内存实现即可真实启动 Javalin 打 HTTP，钉住状态码、响应字段名与
 * 错误映射这些<b>不会编译报错、只会让前端静默失效</b>的契约。
 *
 * <h2>为什么只有这些方法</h2>
 * 一个方法对应 {@code EditorApi} 里的一处真实调用。刻意<b>不</b>暴露 {@code config()} 与
 * {@code getServer()} 这类全量出口：接口越宽，越容易顺手把「别处也能拿」的服务塞进 REST 层，
 * 测试替身也就越难写。需要的具体值（可用语言、数据目录）由实现方自己取出后给出。
 * <p>
 * 玩家名与在线状态（{@code /api/players}）不在其中——那两条路由要的是 Bukkit 运行期数据，
 * 由 {@code Bukkit} 静态取用，没有可注入的余地（见 {@link EditorApi} 类注释）。
 *
 * <h2>谁实现</h2>
 * 插件主类 {@code PlayerTaskX} <b>直接</b>实现它，不做适配器类：那几个子系统本来就由它持有，
 * 转调一行即可；多一层包装只会多一处要同步维护的地方。
 */
public interface EditorServices {

    /** 任务注册表：列表、按 id 查询、分类与每日任务。 */
    QuestRegistryImpl quests();

    /**
     * 任务维护入口。
     * <p>
     * REST 层必须走它而不是直接改注册表——保存要「落库 + 更新注册表 + 重建玩家索引」成对发生，
     * 少了索引重建，玩家进度会按旧定义算。
     */
    QuestAdminService questAdmin();

    /** 目标/奖励预设仓储；只有编辑器读写它。 */
    PresetRepository presets();

    /** 玩家任务仓储：管理端据此排查「某玩家为什么没进度」。 */
    PlayerQuestRepository playerQuestRepository();

    /** 目标类型清单，{@code /api/schema} 据此生成动态表单。 */
    ObjectiveRegistryImpl objectiveTypes();

    /** 奖励类型清单；除字段结构外还要给出「当前是否可用」。 */
    RewardRegistryImpl rewardTypes();

    /** 可用语言代码列表，{@code /api/langs} 逐个读取。 */
    List<String> availableLanguages();

    /** 重载语言文件：编辑器写完语言文件后必须让新内容立刻生效。 */
    void reloadMessages();

    /** 存储描述，供 {@code /api/stats} 展示当前用的后端。 */
    String describeStorage();

    /** 插件数据目录：语言文件读写都落在它下面。 */
    File dataFolder();

    /** 读取插件内置资源（数据目录里没有语言文件时的兜底）；取不到返回 {@code null}。 */
    InputStream resource(String path);
}
