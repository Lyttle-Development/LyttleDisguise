package com.lyttledev.lyttledisguise;

import com.lyttledev.lyttledisguise.commands.LyttleDisguiseCommand;
import com.lyttledev.lyttledisguise.commands.disquise.DisguiseCommand;
import com.lyttledev.lyttledisguise.types.Configs;
import com.lyttledev.lyttleutils.utils.communication.Console;
import com.lyttledev.lyttleutils.utils.communication.Message;
import com.lyttledev.lyttleutils.utils.storage.GlobalConfig;
import dev.iiahmed.disguise.DisguiseManager;
import dev.iiahmed.disguise.DisguiseProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.regex.Pattern;

public final class LyttleDisguise extends JavaPlugin {
    public Configs config;
    public Console console;
    public Message message;
    public GlobalConfig global;
    private DisguiseProvider disguiseProvider;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = new Configs(this);
        this.global = new GlobalConfig(this);

        migrateConfig();

        this.console = new Console(this);
        this.message = new Message(this, config.messages, global);

        new LyttleDisguiseCommand(this);
        initializeDisguiseAPI();
    }

    private void initializeDisguiseAPI() {
        DisguiseManager.initialize(this, true);

        disguiseProvider = DisguiseManager.getProvider();
        disguiseProvider.allowOverrideChat(false);
        disguiseProvider.setNameLength(16);
        disguiseProvider.setNamePattern(Pattern.compile("^[a-zA-Z0-9_]{1,16}$"));

        final DisguiseCommand disguiseCommand = new DisguiseCommand(this, disguiseProvider);
        getCommand("disguise").setExecutor(disguiseCommand);
        getCommand("disguise").setTabCompleter(disguiseCommand);
    }

    @Override
    public void saveDefaultConfig() {
        String configPath = "config.yml";
        if (!new File(getDataFolder(), configPath).exists())
            saveResource(configPath, false);

        String messagesPath = "messages.yml";
        if (!new File(getDataFolder(), messagesPath).exists())
            saveResource(messagesPath, false);

        String defaultPath = "#defaults/";
        String defaultGeneralPath =  defaultPath + configPath;
        saveResource(defaultGeneralPath, true);

        String defaultMessagesPath =  defaultPath + messagesPath;
        saveResource(defaultMessagesPath, true);
    }

    private void migrateConfig() {
        if (!config.general.contains("config_version")) {
            config.general.set("config_version", 0);
        }

        switch (config.general.get("config_version").toString()) {
            default:
                break;
        }
    }
}