package me.z3dyy.endervault;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * SparkQ main plugin class.
 * Registers all commands and the new HatEffectListener.
 *
 * NOTE: The original compiled classes (EnderVaultCommand, UtilityCommand,
 * PlayerUtilCommand, RecipeCommand, SparkQCommand) are kept from the
 * original JAR — only EnderVault.java and HatEffectListener.java are new.
 */
public class EnderVault extends JavaPlugin {

    @Override
    public void onEnable() {
        // ── Original command registrations (unchanged) ──────────────────────
        RecipeCommand recipeCommand = new RecipeCommand(this);
        PlayerUtilCommand playerUtil = new PlayerUtilCommand(this);

        getCommand("endervauit").setExecutor(new EnderVaultCommand(this));
        getCommand("sparkq").setExecutor(new SparkQCommand(this));

        getCommand("heal").setExecutor(new UtilityCommand(this));
        getCommand("feed").setExecutor(new UtilityCommand(this));
        getCommand("repair").setExecutor(new UtilityCommand(this));
        getCommand("hat").setExecutor(new UtilityCommand(this));

        getCommand("recipe").setExecutor(recipeCommand);

        getCommand("ping").setExecutor(playerUtil);
        getCommand("iteminfo").setExecutor(playerUtil);
        getCommand("coords").setExecutor(playerUtil);
        getCommand("compass").setExecutor(playerUtil);
        getCommand("playtime").setExecutor(playerUtil);
        getCommand("clearinv").setExecutor(playerUtil);
        getCommand("nightvision").setExecutor(playerUtil);
        getCommand("suicide").setExecutor(playerUtil);

        // ── NEW: Hat Effect Listener ─────────────────────────────────────────
        HatEffectListener hatEffectListener = new HatEffectListener(this);
        getServer().getPluginManager().registerEvents(hatEffectListener, this);

        getLogger().info("SparkQ v1.0.0 by Quentix enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("SparkQ disabled.");
    }
}
