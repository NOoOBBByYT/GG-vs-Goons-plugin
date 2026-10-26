package com.tyler.ggvsgoons.factions

import com.tyler.ggvsgoons.GGvGModule
import com.tyler.ggvsgoons.GGvGPlugin
import com.tyler.ggvsgoons.persistence.FactionPersistence
import org.bukkit.Bukkit
import java.io.File

/**
 * Module for the faction system
 */
class FactionsModule(private val plugin: GGvGPlugin) : GGvGModule {
    
    lateinit var manager: FactionManager
        private set
    val isRegistered: Boolean get() = ::manager.isInitialized
    
    private var cleanupTaskId: Int = -1
    private var persistenceLoaded = false
    
    override fun register(plugin: GGvGPlugin) {
        // Keep the manager and saved data available even while commands are disabled.
        // This avoids losing faction data and lets the feature be toggled at runtime.
        manager = FactionManager(plugin)
        manager.loadConfig()
        
        // Register command
        val command = FactionCommand(plugin, manager)
        plugin.getCommand("faction")?.setExecutor(command)
        plugin.getCommand("faction")?.tabCompleter = command
        
        // Register shorthand for faction chat
        val chatCommand = FactionChatCommand(plugin, manager)
        plugin.getCommand("fc")?.setExecutor(chatCommand)
        
        // Load persisted faction data
        if (plugin.config.getBoolean("factions.enable-persistence", true)) {
            loadFactions()
        }
        
        // Schedule cleanup task for expired invites (every 5 minutes)
        cleanupTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
            manager.cleanupExpiredInvites()
        }, 6000L, 6000L) // 5 minutes in ticks
        
        plugin.logger.info("Factions module ${if (plugin.config.getBoolean("factions.enabled", true)) "enabled" else "disabled (data retained)"}")
    }
    
    /**
     * Load factions from persistence
     */
    private fun loadFactions() {
        val dataFolder = plugin.dataFolder
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
        }
        
        val factionsFile = File(dataFolder, "factions.yml")
        val loadedFactions = FactionPersistence.loadFactions(factionsFile, plugin.logger)
        manager.loadFactions(loadedFactions)
        persistenceLoaded = true
        
        plugin.logger.info("Loaded ${loadedFactions.size} faction(s)")
    }
    
    /**
     * Save factions to persistence
     */
    fun saveFactions() {
        if (!plugin.config.getBoolean("factions.enable-persistence", true)) {
            return
        }
        if (!persistenceLoaded || !::manager.isInitialized) {
            plugin.logger.severe("Faction data was not loaded successfully; refusing to overwrite factions.yml")
            return
        }
        
        val factionsFile = File(plugin.dataFolder, "factions.yml")
        val factions = manager.getFactions()
        FactionPersistence.saveFactions(factions, factionsFile, plugin.logger)
    }
    
    /**
     * Called when the plugin is disabled
     */
    fun onDisable() {
        // Cancel cleanup task
        if (cleanupTaskId != -1) {
            Bukkit.getScheduler().cancelTask(cleanupTaskId)
        }
        
        // Save factions
        if (::manager.isInitialized) saveFactions()
    }
}
