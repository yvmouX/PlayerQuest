package com.playerPlugin.playerTaskX.core.reward;

import com.playerPlugin.playerTaskX.api.model.QuestReward;
import com.playerPlugin.playerTaskX.api.reward.RewardType;
import com.playerPlugin.playerTaskX.api.schema.ConfigField;
import cn.yvmou.ylib.text.TextRenderer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;

/**
 * 奖励：物品。
 * <p>
 * 背包满时把多余物品掉落在玩家脚下，而不是静默丢弃——
 * 玩家做完任务却什么也没拿到是最不可接受的失败方式。
 */
public final class ItemReward implements RewardType {

    public static final String ID = "item";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "物品";
    }

    @Override
    public List<ConfigField> schema() {
        return List.of(
                ConfigField.items("material", "物品", "DIAMOND"),
                ConfigField.integer("amount", "数量", 1, "发放数量"),
                ConfigField.text("name", "显示名", "", "留空则用物品默认名，支持 MiniMessage 与 & 颜色码"),
                ConfigField.text("lore", "描述", "", "多行用 | 分隔，支持颜色码")
        );
    }

    @Override
    public void grant(Player player, QuestReward reward) {
        ItemStack stack = build(reward);
        if (stack == null) {
            return;
        }
        var leftover = player.getInventory().addItem(stack);
        for (ItemStack remainder : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), remainder);
        }
    }

    /** 构建物品，材质非法时返回 null。 */
    public ItemStack build(QuestReward reward) {
        String materialName = reward.string("material", "").trim().toUpperCase(Locale.ROOT);
        Material material = Material.matchMaterial(materialName);
        if (material == null || material.isAir()) {
            return null;
        }
        int amount = Math.max(1, reward.integer("amount", 1));
        ItemStack stack = new ItemStack(material, Math.min(amount, material.getMaxStackSize() * 64));

        String name = reward.string("name", "");
        String lore = reward.string("lore", "");
        if (TextRenderer.isBlank(name) && TextRenderer.isBlank(lore)) {
            return stack;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (!TextRenderer.isBlank(name)) {
                meta.setDisplayName(TextRenderer.render(name));
            }
            if (!TextRenderer.isBlank(lore)) {
                meta.setLore(java.util.Arrays.stream(lore.split("\\|"))
                        .map(String::trim)
                        .filter(line -> !line.isEmpty())
                        .map(TextRenderer::render)
                        .toList());
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
