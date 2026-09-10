package com.playerPlugin.playerTaskX.core.command;

import cn.yvmou.ylib.command.annotation.Arg;
import cn.yvmou.ylib.command.annotation.Command;
import cn.yvmou.ylib.command.annotation.Optional;
import cn.yvmou.ylib.command.annotation.SubCommand;
import cn.yvmou.ylib.command.help.CommandHelp;
import cn.yvmou.ylib.command.help.HelpProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * YLib 统一 help API 的集成测试。
 *
 * <p>YLib 自身没有测试基建（无 test 目录与 JUnit 依赖），因此这套测试放在插件侧，
 * 覆盖「注解 → 扫描 → 渲染」的完整链路，相当于对 YLib 该功能的回归保护。</p>
 *
 * <p>被测的 {@link CommandHelp} 是库级 API：所有依赖 YLib 的插件都用它渲染帮助，
 * 因此这里的断言同时也是「插件帮助样式一致」的保证。</p>
 */
class YLibCommandHelpTest {

    // ---------- 被测用的命令类 ----------

    @Command(name = "demo", description = "演示命令")
    static class DemoCommand {

        @SubCommand(value = "", description = "打开界面")
        public void main() {
        }

        @SubCommand(value = "give", description = "给东西")
        public void give(@Arg("player") String player, @Arg("amount") @Optional int amount) {
        }

        @SubCommand(value = "group", description = "分组")
        static class Group {

            @SubCommand(value = "sub", description = "分组内的子命令")
            public void sub() {
            }
        }

        @SubCommand(value = "hidden", description = "带权限")
        public void hidden() {
        }
    }

    @Command(name = "many", description = "多命令")
    static class ManyCommand {

        @SubCommand(value = "c1", description = "命令一")
        public void c1() {
        }

        @SubCommand(value = "c2", description = "命令二")
        public void c2() {
        }

        @SubCommand(value = "c3", description = "命令三")
        public void c3() {
        }

        @SubCommand(value = "c4", description = "命令四")
        public void c4() {
        }

        @SubCommand(value = "c5", description = "命令五")
        public void c5() {
        }

        @SubCommand(value = "c6", description = "命令六")
        public void c6() {
        }

        @SubCommand(value = "c7", description = "命令七")
        public void c7() {
        }
    }

    // ---------- 扫描 ----------

    @Test
    @DisplayName("扫描注解生成条目：主命令、参数占位、可选参数、嵌套类分组")
    void scanBuildsEntriesFromAnnotations() {
        List<HelpProvider.Entry> entries = HelpProvider.scan(DemoCommand.class);

        List<String> usages = entries.stream()
                .filter(HelpProvider.Entry::isCommand)
                .map(HelpProvider.Entry::usage)
                .toList();

        // @SubCommand("") 渲染为命令本身
        assertTrue(usages.contains("/demo"), "应包含主命令: " + usages);
        // @Arg 追加为 <name>，@Optional 写为 [name]
        assertTrue(usages.contains("/demo give <player> [amount]"), "应包含带可选参数的语法: " + usages);
        // 嵌套类作为分组，其方法路径带前缀
        assertTrue(usages.contains("/demo group sub"), "嵌套类分组的路径应带前缀: " + usages);

        // 分组标题来自嵌套类的 description
        List<String> groups = entries.stream()
                .filter(entry -> !entry.isCommand())
                .map(HelpProvider.Entry::group)
                .toList();
        assertEquals(List.of("分组"), groups);
    }

    @Test
    @DisplayName("命令名取自 @Command(name)")
    void commandNameFromAnnotation() {
        assertEquals("demo", HelpProvider.commandName(DemoCommand.class));
    }

    @Test
    @DisplayName("没有 @Command 的类返回空条目而不是抛异常")
    void classWithoutAnnotationYieldsNoEntries() {
        assertTrue(HelpProvider.scan(String.class).isEmpty());
    }

    // ---------- 渲染 ----------

    @Test
    @DisplayName("渲染出统一样式：标题、条目、分组、描述")
    void renderProducesUnifiedStyle() {
        List<String> lines = CommandHelp.ofAnnotations("演示插件", DemoCommand.class).lines();

        // 标题行：删除线 + 插件名，且已完成 & → § 转换
        assertTrue(lines.get(0).contains("演示插件"), "首行应为标题: " + lines);
        assertTrue(lines.get(0).contains("\u00A7"), "应带颜色码: " + lines);

        String joined = String.join("\n", lines);
        assertTrue(joined.contains("/demo give <player> [amount]"), "应含完整语法: " + joined);
        assertTrue(joined.contains("给东西"), "应含注解里的描述: " + joined);
        assertTrue(joined.contains("分组"), "应含分组标题: " + joined);
        assertFalse(joined.contains("&"), "不应残留 & 颜色码字面量: " + joined);
    }

    @Test
    @DisplayName("权限以 [节点] 形式追加在描述之后")
    void renderShowsPermission() {
        List<String> lines = CommandHelp.builder("t")
                .entry("/demo hidden", "带权限", "demo.admin")
                .lines();
        String joined = String.join("\n", lines);
        assertTrue(joined.contains("[demo.admin]"), "应显示权限节点: " + joined);
    }

    @Test
    @DisplayName("分页：超出每页条数时显示页脚，翻页内容不同")
    void paginationCutsEntriesAndShowsFooter() {
        List<String> firstPage = CommandHelp.ofAnnotations("分页", ManyCommand.class)
                .pageSize(3)
                .page(1)
                .lines();
        String first = String.join("\n", firstPage);

        // 组内按语法字典序：c1、c2、c3 在第一页
        assertTrue(first.contains("c1") && first.contains("c3"), "第一页应含前三条: " + first);
        assertFalse(first.contains("c4 "), "第一页不应含第四条: " + first);
        assertTrue(first.contains("1/3"), "应显示总页数: " + first);
        // 页脚用真实命令名提示如何翻页
        assertTrue(first.contains("/many help 2"), "页脚应提示下一页命令: " + first);

        String second = String.join("\n", CommandHelp.ofAnnotations("分页", ManyCommand.class)
                .pageSize(3).page(2).lines());
        assertTrue(second.contains("c4") && second.contains("c6"), "第二页应含第四到第六条: " + second);
        assertFalse(second.contains("c1 "), "第二页不应含第一条: " + second);
    }

    @Test
    @DisplayName("顺序确定：分组先于本类命令，本类命令按语法字典序")
    void entriesAreOrderedDeterministically() {
        List<String> usages = HelpProvider.scan(DemoCommand.class).stream()
                .filter(HelpProvider.Entry::isCommand)
                .map(HelpProvider.Entry::usage)
                .toList();

        // 分组（嵌套类）先输出，便于把相关命令放在一起
        assertTrue(usages.get(0).startsWith("/demo group"), "分组命令应排在最前: " + usages);

        // 本类命令必须按语法字典序——Class#getDeclaredMethods() 的顺序不保证，
        // 这个断言把「顺序可预测」固化下来，避免不同 JVM 上帮助顺序漂移
        List<String> own = usages.stream().filter(u -> !u.startsWith("/demo group")).toList();
        List<String> sorted = new java.util.ArrayList<>(own);
        sorted.sort(String::compareToIgnoreCase);
        assertEquals(sorted, own, "本类命令应按语法字典序排列: " + usages);
    }

    @Test
    @DisplayName("页码越界时夹到有效范围，不抛异常也不显示空页")
    void pageOutOfRangeIsClamped() {
        String over = String.join("\n", CommandHelp.ofAnnotations("分页", ManyCommand.class)
                .pageSize(3).page(99).lines());
        assertTrue(over.contains("c7"), "越界页码应显示最后一页: " + over);

        String under = String.join("\n", CommandHelp.ofAnnotations("分页", ManyCommand.class)
                .pageSize(3).page(-5).lines());
        assertTrue(under.contains("c1"), "过小页码应显示第一页: " + under);
    }

    @Test
    @DisplayName("条目数不超过一页时不显示页脚")
    void noFooterWhenSinglePage() {
        String joined = String.join("\n", CommandHelp.ofAnnotations("短", DemoCommand.class).lines());
        assertFalse(joined.contains("页"), "单页不应显示分页页脚: " + joined);
    }

    @Test
    @DisplayName("紧凑模式：perLine 把多条命令并入一行")
    void compactLayoutMergesEntries() {
        List<String> lines = CommandHelp.builder("紧凑")
                .perLine(2)
                .entry("/a", "命令A")
                .entry("/b", "命令B")
                .lines();

        // 标题 1 行 + 合并后的 1 行
        assertEquals(2, lines.size(), "两条命令应并成一行: " + lines);
        assertTrue(lines.get(1).contains("/a") && lines.get(1).contains("/b"), "同一行应含两条: " + lines);
    }
}
