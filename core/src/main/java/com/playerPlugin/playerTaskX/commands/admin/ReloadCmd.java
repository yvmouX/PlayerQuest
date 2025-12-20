package com.playerPlugin.playerTaskX.commands.admin;

import cn.yvmou.ylib.api.command.CommandComplete;
import cn.yvmou.ylib.api.command.CommandOptions;
import cn.yvmou.ylib.api.command.CompleteType;
import cn.yvmou.ylib.api.command.SubCommand;
import cn.yvmou.ylib.api.config.ConfigurationManager;
import com.playerPlugin.playerTaskX.configuration.EditorConfiguration;
import com.playerPlugin.playerTaskX.configuration.StorgeConfiguration;
import org.bukkit.command.CommandSender;

public class ReloadCmd implements SubCommand {
    private final ConfigurationManager configurationManager;

    public ReloadCmd(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }

    @Override
    @CommandOptions(name = "reload", permission = "playertaskx.admin.command.reload", onlyPlayer = false, alias = {}, register = true, usage = "/playertaskx reload editor")
    @CommandComplete({
            @CommandComplete.Tab(
                    type = CompleteType.CUSTOM,
                    customOptions = {"editor", "storage", "all"}
            )
    })
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("用法：/命令 reload [editor|storage|all]");
            return false;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (args.length < 2) {
                sender.sendMessage("参数不足！请指定要重载的配置：editor、storage、all");
                return false;
            }

            if (args[1].equalsIgnoreCase("editor")) {
                configurationManager.reloadConfiguration(EditorConfiguration.class);
                sender.sendMessage("已重载编辑器");
                return true;
            } else if (args[1].equalsIgnoreCase("storage")) {
                configurationManager.reloadConfiguration(StorgeConfiguration.class);
                sender.sendMessage("已重载存储");
                return true;
            } else if (args[1].equalsIgnoreCase("all")) {
                configurationManager.reloadAllConfigurations();
                sender.sendMessage("已重载所有配置");
                return true;
            } else {
                sender.sendMessage("参数错误！请指定要重载的配置：editor、storage、all");
                return false;
            }
        }
        sender.sendMessage("用法：/命令 reload [editor|storage|all]");
        return false;
    }
}
