package com.playerPlugin.playerTaskX.core.text;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文本渲染回归测试。
 *
 * <h2>这些用例来自一次真实的线上 bug</h2>
 * 玩家截图显示 actionbar 上直接暴露了 {@code &7&a&8||||} 颜色码，任务名也显示成
 * {@code <yellow>挖矿日常</yellow>} 标签原文。排查后发现三个相互叠加的事实：
 * <ul>
 *   <li>MiniMessage 遇到 {@code §} 码会抛异常，导致<b>整串</b>标签都不解析；</li>
 *   <li>{@code LegacyComponentSerializer} 的 round-trip 会把 {@code §} 码原样写回，
 *       无法用来「清洗」残留码；</li>
 *   <li>它对文本里的 {@code <yellow>} 一律当字面量。</li>
 * </ul>
 * 因此渲染必须先把 {@code §}/{@code &} 统一翻译成 MiniMessage 标签，再用 MiniMessage 渲染一次。
 * 下面的用例覆盖三种写法的任意混排，防止该问题再次出现。
 */
class TextRendererTest {

    @Test
    @DisplayName("& 颜色码被转换，不残留字面量")
    void ampersandCodesAreTranslated() {
        String out = TextRenderer.render("&a已完成 &7(3/64)");
        assertFalse(out.contains("&"), "不应残留 & 码: " + out);
        assertTrue(out.contains("\u00A7a"), "应含 §a 绿色码: " + out);
    }

    @Test
    @DisplayName("MiniMessage 标签被解析，不残留标签原文")
    void miniMessageTagsAreParsed() {
        String out = TextRenderer.render("<yellow>挖矿日常</yellow>");
        assertFalse(out.contains("<"), "不应残留标签: " + out);
        assertTrue(out.contains("\u00A7e"), "应含 §e 黄色码: " + out);
    }

    @Test
    @DisplayName("标签与 § 码混排时都能正确处理（本次 bug 的核心场景）")
    void mixedTagsAndSectionCodes() {
        String out = TextRenderer.render("<yellow>测试</yellow> \u00A78|");
        assertFalse(out.contains("<"), "标签不应残留: " + out);
        assertTrue(out.contains("\u00A7e"), "标签应被解析为颜色: " + out);
        assertTrue(out.contains("\u00A78"), "原有 §8 应保留: " + out);
    }

    @Test
    @DisplayName("标签与 & 码混排时都能正确处理")
    void mixedTagsAndAmpersandCodes() {
        String out = TextRenderer.render("<yellow>测试</yellow> &8| &f0%");
        assertFalse(out.contains("<"), "标签不应残留: " + out);
        assertFalse(out.contains("&"), "& 码不应残留: " + out);
        assertTrue(out.contains("\u00A7e") && out.contains("\u00A78"), "两种格式都应生效: " + out);
    }

    @Test
    @DisplayName("纯 § 码原样可用（不经过 MiniMessage 也不被破坏）")
    void pureSectionCodes() {
        String out = TextRenderer.render("\u00A78|");
        assertTrue(out.contains("\u00A78"), "§8 应保留: " + out);
    }

    @Test
    @DisplayName("非法标签退化为纯文本，不把标签抛给玩家")
    void invalidTagsDegradeToPlainText() {
        // 未闭合标签：MiniMessage 抛异常，应退化为纯文本而不是原样输出
        String out = TextRenderer.render("<red>未闭合");
        assertFalse(out.contains("<red>"), "非法标签不应原样输出: " + out);
    }

    @Test
    @DisplayName("strip 去掉全部格式，仅保留纯文本")
    void stripRemovesFormatting() {
        assertEquals("测试 0/64", TextRenderer.strip("<yellow>测试</yellow> &f0/64"));
        assertEquals("已完成", TextRenderer.strip("&a已完成"));
        assertEquals("", TextRenderer.strip(null));
    }

    @Test
    @DisplayName("translateAmpersand 只转换合法颜色码")
    void translateAmpersandOnlyValidCodes() {
        assertEquals("\u00A7a文本", TextRenderer.translateAmpersand("&a文本"));
        // 注意 &b 是合法颜色码（aqua），所以 URL 参数这类文本里的 &b 会被转换——
        // 这是传统颜色码机制的固有歧义，无法两全；这里把行为固化下来
        assertEquals("\u00A7b", TextRenderer.translateAmpersand("&b"));
        // 而 & 后跟非颜色码字符时原样保留，例如空格、等号、大写 Z
        assertEquals("A & B", TextRenderer.translateAmpersand("A & B"));
        assertEquals("a=1&z=2", TextRenderer.translateAmpersand("a=1&z=2"));
        assertEquals("100&%", TextRenderer.translateAmpersand("100&%"));
    }

    @Test
    @DisplayName("sectionToMiniMessage 覆盖颜色码与装饰码")
    void sectionToMiniMessageMapping() {
        assertEquals("<red>", TextRenderer.sectionToMiniMessage("\u00A7c"));
        assertEquals("<bold>", TextRenderer.sectionToMiniMessage("\u00A7l"));
        assertEquals("<reset>", TextRenderer.sectionToMiniMessage("\u00A7r"));
        // 非法码原样保留，避免吞掉正文里的 § 字符
        assertEquals("\u00A7z", TextRenderer.sectionToMiniMessage("\u00A7z"));
    }

    @Test
    @DisplayName("空值安全")
    void nullSafety() {
        assertEquals("", TextRenderer.render(null));
        assertEquals("", TextRenderer.strip(null));
        assertEquals("", TextRenderer.translateAmpersand(null));
        assertEquals("", TextRenderer.sectionToMiniMessage(null));
        assertTrue(TextRenderer.isBlank(null));
        assertTrue(TextRenderer.isBlank("   "));
    }

    @Test
    @DisplayName("交给服务端 actionbar 的文本必须是 § 色码，不能含 MiniMessage 标签")
    void upgradeToLegacyProducesSectionCodes() {
        // 线上 bug：Paper 的 sendActionBar(String) 不解析 MiniMessage，
        // 传标签过去玩家会看到 <italic><yellow> 这样的原文，因此必须输出 § 色码
        String legacy = TextUpgrader.toLegacy("&a已完成 &7(3/64)");
        assertFalse(legacy.contains("<"), "不应含 MiniMessage 标签: " + legacy);
        assertFalse(legacy.contains("&"), "不应含 & 码字面量: " + legacy);
        assertTrue(legacy.contains("\u00A7a"), "应含 §a 绿色码: " + legacy);
    }

    @Test
    @DisplayName("MiniMessage 写法同样被转成 § 色码（标签不能泄漏到 actionbar）")
    void upgradeToLegacyHandlesMiniMessageInput() {
        String legacy = TextUpgrader.toLegacy("<yellow>挖矿日常 <green>|");
        assertFalse(legacy.contains("<"), "标签不应泄漏: " + legacy);
        assertTrue(legacy.contains("\u00A7e"), "黄色应转成 §e: " + legacy);
        assertTrue(legacy.contains("\u00A7a"), "绿色应转成 §a: " + legacy);
    }

    @Test
    @DisplayName("含 § 码与标签混排的文本也能降级为纯 § 色码")
    void upgradeToLegacyHandlesMixedInput() {
        String legacy = TextUpgrader.toLegacy("<yellow>测试</yellow> \u00A78| &f0%");
        assertFalse(legacy.contains("<"), "标签不应泄漏: " + legacy);
        assertFalse(legacy.contains("&"), "& 码不应泄漏: " + legacy);
        assertTrue(legacy.contains("\u00A7e") && legacy.contains("\u00A78"), "颜色应保留: " + legacy);
    }
}
