package com.tyler.ggvsgoons

import com.tyler.ggvsgoons.admin.AdminConfigModule
import com.tyler.ggvsgoons.admin.BackupManager
import com.tyler.ggvsgoons.commands.WarPrisonerModule
import com.tyler.ggvsgoons.factions.FactionsModule
import com.tyler.ggvsgoons.persistence.PrisonerPersistence
import com.tyler.ggvsgoons.teams.TeamsModule
import com.tyler.ggvsgoons.stats.StatsModule
import com.tyler.ggvsgoons.scoreboard.ScoreboardModule
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * Module entry point. New modules must be instantiated below, registered in order,
 * and any commands they expose must also be declared in plugin.yml.
 */
interface GGvGModule {
    fun register(plugin: GGvGPlugin)
}

class GGvGPlugin : JavaPlugin() {

    lateinit var warPrisoner: WarPrisonerModule
        private set

    lateinit var teams: TeamsModule
        private set
    
    lateinit var factions: FactionsModule
        private set
    
    lateinit var admin: AdminConfigModule
        private set

    lateinit var stats: StatsModule
        private set

    lateinit var scoreboard: ScoreboardModule
        private set

    private val modules = mutableListOf<GGvGModule>()
    private var prisonerDataLoaded = false

    override fun onEnable() {
        // Apply an explicitly queued restore while no module has loaded or saved data.
        try {
            BackupManager(dataFolder, logger).applyPendingRestore()
        } catch (e: Exception) {
            logger.severe("Pending restore failed; plugin disabled to protect saved data: ${e.message}")
            server.pluginManager.disablePlugin(this)
            return
        }
        // Save default config if it doesn't exist
        saveDefaultConfig()
        
        // Load configuration values
        val offerExpiryTimeout = config.getInt("prisoner.offer-expiry-timeout", 60)
        val persistenceEnabled = config.getBoolean("prisoner.enable-persistence", true)
        
        logger.info("Configuration loaded:")
        logger.info("  - Offer expiry timeout: ${if (offerExpiryTimeout > 0) "$offerExpiryTimeout seconds" else "disabled"}")
        logger.info("  - Persistence: ${if (persistenceEnabled) "enabled" else "disabled"}")
        
        // Initialize modules
        stats = StatsModule(this)
        modules += stats

        warPrisoner = WarPrisonerModule(this, offerExpiryTimeout)
        modules += warPrisoner

        teams = TeamsModule(this)
        modules += teams
        
        // Initialize factions module
        factions = FactionsModule(this)
        modules += factions
        
        // Initialize admin configuration module
        admin = AdminConfigModule(this)
        modules += admin

        scoreboard = ScoreboardModule(this)
        modules += scoreboard

        // Register all modules
        try {
            modules.forEach { it.register(this) }
            // Load persisted prisoner data if enabled
            if (persistenceEnabled) {
                val dataFolder = dataFolder
                if (!dataFolder.exists()) {
                    dataFolder.mkdirs()
                }
                val prisonersFile = File(dataFolder, "prisoners.yml")
                val loadedPrisoners = PrisonerPersistence.loadPrisoners(prisonersFile, logger)
                warPrisoner.manager.loadPrisoners(loadedPrisoners)
            }
            // A persistence toggle enabled later must not overwrite a file we never loaded.
            prisonerDataLoaded = persistenceEnabled
        } catch (e: Exception) {
            logger.severe("Plugin startup aborted to protect saved data: ${e.message}")
            server.pluginManager.disablePlugin(this)
            return
        }
        
        logger.info("GGvGoons enabled - ${modules.size} module(s) loaded")
    }

    override fun onDisable() {
        // Cancel all pending expiry tasks
        if (::warPrisoner.isInitialized) warPrisoner.manager.cancelAllExpiryTasks()
        
        // Save prisoner data if persistence is enabled
        val persistenceEnabled = config.getBoolean("prisoner.enable-persistence", true)
        if (persistenceEnabled && prisonerDataLoaded && ::warPrisoner.isInitialized) {
            val prisonersFile = File(dataFolder, "prisoners.yml")
            val prisoners = warPrisoner.manager.getPrisoners()
            PrisonerPersistence.savePrisoners(prisoners, prisonersFile, logger)
        }

        // Save team membership and spawns
        if (::teams.isInitialized) teams.onDisable()
        
        // Save faction data if factions module is initialized
        if (::factions.isInitialized) {
            factions.onDisable()
        }
        
        // Clean up admin module if initialized
        if (::admin.isInitialized && admin.isRegistered) {
            admin.onDisable()
        }
        if (::scoreboard.isInitialized) {
            scoreboard.onDisable()
        }

        logger.info("GGvGoons disabled")
    }
}
