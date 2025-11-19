package com.playerPlugin.infra.UI;

import me.devnatan.inventoryframework.View;
import me.devnatan.inventoryframework.ViewConfigBuilder;
import me.devnatan.inventoryframework.context.RenderContext;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class MainUI extends View {
    // TODO 使其在配置文件 中自定义
    @Override
    public void onInit(ViewConfigBuilder config) {
        config.title("主界面")
                .cancelOnClick()
                .layout(
                        "#########",
                        "#   -   #",
                        "# +     #",
                        "#       #",
                        "#       #",
                        "#########"
                );
    }

    @Override
    public void onFirstRender(RenderContext render) {
        render.layoutSlot('#')
                .renderWith(() -> new ItemStack(
                        Material.GOLD_INGOT,
                        1
                ));

        render.layoutSlot('-')
                .renderWith(() -> createItem(
                        Material.PLAYER_HEAD,
                        1,
                        "插件信息",
                        List.of("插件信息。。。")
                ));
        render.layoutSlot('+')
                .renderWith(() -> createItem(
                   Material.OAK_BOAT,
                   2,
                   "任务1",
                   List.of("任务。。。")
                ));
    }

    /**
     * 创建项目
     *
     * @param material 材料
     * @param number   数
     * @param name     名字
     * @param lore     博学
     * @return {@link ItemStack }
     */
    private ItemStack createItem(Material material, int number, String name, List<String> lore) {
        ItemStack item = new ItemStack(material, number);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.setDisplayName(name);
            }
            if (lore != null) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
