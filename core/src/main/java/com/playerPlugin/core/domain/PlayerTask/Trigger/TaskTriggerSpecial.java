package com.playerPlugin.core.domain.PlayerTask.Trigger;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import static com.playerPlugin.common.Common.ARMOR;
import static com.playerPlugin.common.Common.ARMOR_TOUGHNESS;
import static com.playerPlugin.common.Common.DAMAGE;
import static com.playerPlugin.common.Common.ENCHANTS;
import static com.playerPlugin.common.Common.EQUIPPED_DAMAGE;
import static com.playerPlugin.common.Common.EQUIPPED_HEALTH;
import static com.playerPlugin.common.Common.HEALTH;
import static com.playerPlugin.common.Common.LORE;
import static com.playerPlugin.common.Common.NAME;
import static com.playerPlugin.common.Common.UNBREAKABLE;
import static com.playerPlugin.core.utils.Help.log;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


public class TaskTriggerSpecial {
    /**
     * 处理杀戮
     *
     * @param player  选手
     * @param message 消息
     */
    protected static void handleKill(Player player, String message) {
        if (!message.isEmpty()) {
            player.sendMessage(message.replace("&", "§"));
        }
        player.setHealth(0);
    }

    /**
     * 手柄药水
     *
     * @param player 选手
     * @param args   参数
     */
    protected static void handlePotion(Player player, String args) {
        String[] parts = args.split(" ");
        if (parts.length >= 1) {
            String potionName = parts[0];
            String duration = parts.length >= 2 ? parts[1] : "30";
            String amplifier = parts.length >= 3 ? parts[2] : "1";
            boolean particleFlag = parts.length >= 4 && Boolean.parseBoolean(parts[3]);

            PotionEffectType potion = PotionEffectType.getByName(potionName);
            if (potion == null) {
                log.error("无效的药水类型：" + potionName);
                return;
            }

            player.addPotionEffect(new PotionEffect(potion,
                    Integer.parseInt(duration),
                    Integer.parseInt(amplifier), false, particleFlag, true));
        } else {
            log.error("无效的药水参数");
        }
    }

    /**
     * 处理项目
     *
     * @param player 选手
     * @param args   参数
     */
    protected static void handleItem(Player player, String args) {
        String[] parts = args.split(" ");
        if (parts.length >= 1) {
            Material itemName = Material.getMaterial(parts[0]);
            Material material = itemName == null ? Material.STONE : itemName;
            int amount = parts.length >= 2 && parts[1].matches("\\d+") ? Integer.parseInt(parts[1]) : 1;

            // 把所有剩余部分当特殊属性处理
            List<String> propertiesList = parts.length >= 6 ?
                    Arrays.stream(parts)
                            .skip(6)
                            .toList() : Collections.emptyList();

            Map<String, String> properties = propertiesList.stream()
                    .map(property -> property.split(":"))
                    .filter(property -> property.length == 2)
                    .collect(Collectors.toMap(
                            property -> property[0],
                            property -> property[1],
                            (existing, replacement) -> replacement
                    ));

            ItemMeta meta = new ItemStack(material).getItemMeta();
            ItemMeta itemMeta = getItemMeta(meta, properties);
            ItemStack item = new ItemStack(material, amount);
            item.setItemMeta(itemMeta);
            player.getInventory().addItem(item);
        } else {
            log.error("无效的物品参数");
        }
    }

    private static ItemMeta getItemMeta(ItemMeta meta, Map<String, String> properties) {
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            switch (entry.getKey().toUpperCase(Locale.ENGLISH)) {
                case NAME:
                    meta.setDisplayName(entry.getValue().replace("&", "§"));
                    continue;
                case LORE:
                    meta.setLore(Arrays.asList(entry.getValue().split("\n")));
                    continue;
                case ENCHANTS:
                    String[] enchants = entry.getValue().split(",");
                    for (String enchant : enchants) {
                        String[] enchantParts = enchant.split(":");
                        if (enchantParts.length == 2) {
                            String enchantName = enchantParts[0];
                            NamespacedKey enchantKey = NamespacedKey.fromString(enchantName, null);
                            if (enchantKey == null) {
                                log.error("无效的附魔类型：" + enchantName);
                                continue;
                            }
                            int enchantLevel = Integer.parseInt(enchantParts[1]);
                            meta.addEnchant(Objects.requireNonNull(Registry.ENCHANTMENT.get(enchantKey)), enchantLevel, true);
                        }
                    }
                    continue;
                case DAMAGE:

                    meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                            NamespacedKey.minecraft("attack_damage"),
                            Double.parseDouble(entry.getValue()),
                            AttributeModifier.Operation.ADD_NUMBER,
                            EquipmentSlotGroup.ANY
                    ));
                    continue;
                case HEALTH:
                    meta.addAttributeModifier(Attribute.MAX_HEALTH, new AttributeModifier(
                            NamespacedKey.minecraft("max_health"),
                            Double.parseDouble(entry.getValue()),
                            AttributeModifier.Operation.ADD_NUMBER,
                            EquipmentSlotGroup.ANY
                    ));
                    continue;
                case EQUIPPED_DAMAGE:
                    meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                            NamespacedKey.minecraft("attack_damage_main_hand"),
                            Double.parseDouble(entry.getValue()),
                            AttributeModifier.Operation.ADD_NUMBER,
                            EquipmentSlotGroup.MAINHAND
                    ));
                    continue;
                case EQUIPPED_HEALTH:
                    meta.addAttributeModifier(Attribute.MAX_HEALTH, new AttributeModifier(
                            NamespacedKey.minecraft("max_health_"),
                            Double.parseDouble(entry.getValue()),
                            AttributeModifier.Operation.ADD_NUMBER,
                            meta.getEquippable().getSlot().getGroup()
                    ));
                    continue;
                case UNBREAKABLE:
                    meta.setUnbreakable(true);
                    continue;
                case ARMOR:
                    meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(
                            NamespacedKey.minecraft("armor"),
                            Double.parseDouble(entry.getValue()),
                            AttributeModifier.Operation.ADD_NUMBER,
                            meta.getEquippable().getSlot().getGroup()
                    ));
                    continue;
                case ARMOR_TOUGHNESS:
                        meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(
                            NamespacedKey.minecraft("armor_toughness"),
                            Double.parseDouble(entry.getValue()),
                            AttributeModifier.Operation.ADD_NUMBER,
                            meta.getEquippable().getSlot().getGroup()
                    ));
                    continue;
                default:
                    String keyUpperCase = entry.getKey().toUpperCase(Locale.ENGLISH);
                    if (keyUpperCase.startsWith("HIDE_")) {
                        try {
                            meta.addItemFlags(ItemFlag.valueOf(keyUpperCase));
                        } catch (IllegalArgumentException ignored) {
                            log.warn("Invalid ItemFlag: " + keyUpperCase);
                        }
                    }

            }
        }
        return meta;
    }
}