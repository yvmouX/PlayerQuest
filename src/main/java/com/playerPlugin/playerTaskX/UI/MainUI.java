package com.playerPlugin.playerTaskX.UI;

import me.devnatan.inventoryframework.View;
import me.devnatan.inventoryframework.ViewConfigBuilder;
import me.devnatan.inventoryframework.context.RenderContext;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class MainUI extends View {
    // TODO 使其在配置文件 中自定义
    @Override
    public void onInit(ViewConfigBuilder config) {
        config.title("主界面")
                .cancelOnClick()
                .layout(
                        "---------",
                        "-       -",
                        "-       -",
                        "-       -",
                        "-       -",
                        "---------"
                );
    }

    @Override
    public void onFirstRender(RenderContext render) {
        render.layoutSlot('-')
                .renderWith(() -> new ItemStack(
                        Material.GOLD_INGOT,
                        1
                ));
    }
}
