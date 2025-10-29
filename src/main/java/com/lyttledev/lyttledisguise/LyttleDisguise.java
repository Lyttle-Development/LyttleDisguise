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
            case "0":
                // Migrate config entries.
                 config.messages.set("disguise_started_other", config.defaultMessages.get("disguise_started_other"));
                 config.messages.set("disguise_cleared_other", config.defaultMessages.get("disguise_cleared_other"));

                // Update config version.
                config.general.set("config_version", 1);

                // Recheck if the config is fully migrated.
                migrateConfig();
                break;
            case "1":
            case "2": // Double update to fix skipped version.
                // Migrate config entries.
                 config.messages.set("disguise_usage", config.defaultMessages.get("disguise_usage"));

                // Update config version.
                config.general.set("config_version", 3);

                // Recheck if the config is fully migrated.
                migrateConfig();
                break;
            case "3":
                // Migrate new entity disguise messages.
                config.messages.set("disguise_usage", config.defaultMessages.get("disguise_usage"));
                config.messages.set("disguise_entity_applied", config.defaultMessages.get("disguise_entity_applied"));
                config.messages.set("disguise_entity_failed", config.defaultMessages.get("disguise_entity_failed"));
                config.messages.set("disguise_entity_error", config.defaultMessages.get("disguise_entity_error"));
                config.messages.set("disguise_invalid_entity", config.defaultMessages.get("disguise_invalid_entity"));

                // Update config version.
                config.general.set("config_version", 4);

                // Recheck if the config is fully migrated.
                migrateConfig();
                break;
            default:
                break;
        }
    }
}