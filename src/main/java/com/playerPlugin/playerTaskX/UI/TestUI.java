package com.playerPlugin.playerTaskX.UI;

import me.devnatan.inventoryframework.View;
import me.devnatan.inventoryframework.ViewConfigBuilder;
import me.devnatan.inventoryframework.ViewType;
import me.devnatan.inventoryframework.context.OpenContext;
import org.bukkit.entity.Player;

public class TestUI extends View {
    @Override
    public void onInit(ViewConfigBuilder config) {
        config.title("TestUI");
        config.type(ViewType.CHEST);
        config.size(54);
    }

    @Override
    public void onOpen(OpenContext open) {
        final Player player = open.getPlayer();
        open.modifyConfig()
                .title("Hi, " + player.getName() + "!");
    }

}
