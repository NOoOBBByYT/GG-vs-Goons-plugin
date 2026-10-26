package com.tyler.ggvsgoons.admin

import com.tyler.ggvsgoons.GGvGModule
import com.tyler.ggvsgoons.GGvGPlugin
import org.bukkit.Bukkit
import java.io.File

/**
 * Module for the admin configuration and moderation system
 */
class AdminConfigModule(private val plugin: GGvGPlugin) : GGvGModule {
    
    lateinit var configManager: AdminConfigManager
        private set
    
    lateinit var moderationManager: ModerationManager
        private set
    
    lateinit var permissionManager: PermissionManager
        private set
    
    lateinit var backupManager: BackupManager
        private set
    
    lateinit var auditLogger: AuditLogger
        private set
    
    private lateinit var validator: ConfigValidator
    
    private var cleanupTaskId: Int = -1
    private var autoBackupTaskId: Int = -1
    val isRegistered: Boolean get() = ::configManager.isInitialized && ::auditLogger.isInitialized
    
    override fun register(plugin: GGvGPlugin) {
        // Initialize and register commands even if disabled; executors check the live flag.
        // This keeps scoreboard status checks and shutdown safe after runtime toggles.
        
        // Initialize validator
        validator = ConfigValidator()
        
        // Initialize audit logger
        val auditFile = File(plugin.dataFolder, plugin.config.getString("admin.audit.log-file", "audit.log") ?: "audit.log")
        auditLogger = AuditLogger(auditFile, plugin.logger)
        
        // Load audit configuration
        val retentionDays = plugin.config.getInt("admin.audit.retention-days", 90)
        val logPlayerActions = plugin.config.getBoolean("admin.audit.log-player-actions", true)
        val logConfigChanges = plugin.config.getBoolean("admin.audit.log-config-changes", true)
        val logModActions = plugin.config.getBoolean("admin.audit.log-mod-actions", true)
        auditLogger.updateConfig(retentionDays, logPlayerActions, logConfigChanges, logModActions)
        
        // Initialize config manager
        configManager = AdminConfigManager(plugin, validator, auditLogger)
        
        // Initialize moderation manager
        moderationManager = ModerationManager(plugin, auditLogger)
        
        // Initialize permission manager
        val useLuckPerms = plugin.config.getBoolean("admin.permissions.use-luckperms", false)
        permissionManager = PermissionManager(plugin, useLuckPerms)
        Bukkit.getPluginManager().registerEvents(AdminModerationListener(plugin, moderationManager, permissionManager), plugin)
        
        // Initialize backup manager
        backupManager = BackupManager(plugin.dataFolder, plugin.logger)
        val maxBackups = plugin.config.getInt("admin.backups.max-backups", 10)
        val autoBackup = plugin.config.getBoolean("admin.backups.auto-backup", true)
        val autoBackupInterval = plugin.config.getInt("admin.backups.auto-backup-interval", 3600)
        backupManager.updateConfig(maxBackups, autoBackup, autoBackupInterval)
        
        // Register admin command
        val adminCommand = AdminCommand(
            plugin,
            configManager,
            moderationManager,
            permissionManager,
            backupManager,
            auditLogger,
            scoreboardManagerProvider = { runCatching { plugin.scoreboard.manager }.getOrNull() }
        )
        plugin.getCommand("ggadmin")?.setExecutor(adminCommand)
        plugin.getCommand("ggadmin")?.tabCompleter = adminCommand
        
        // Schedule cleanup task for expired permissions (every 5 minutes)
        cleanupTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
            permissionManager.cleanupExpiredPermissions()
            auditLogger.cleanupOldEntries()
        }, 6000L, 6000L) // 5 minutes in ticks
        
        // Keep the task scheduled so enabling admin/auto-backups at runtime takes effect.
        val intervalTicks = autoBackupInterval.coerceAtLeast(60).toLong() * 20L
        autoBackupTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
            if (!plugin.config.getBoolean("admin.enabled", true) ||
                !plugin.config.getBoolean("admin.backups.auto-backup", true) ||
                !plugin.config.getBoolean("admin.backups.enabled", true)) return@scheduleSyncRepeatingTask
            plugin.logger.info("Running automatic backup...")
            val result = backupManager.createBackup(null, null, "Automatic backup")
            result.onSuccess { info ->
                plugin.logger.info("Automatic backup created: ${info.name}")
            }.onFailure { error ->
                plugin.logger.severe("Automatic backup failed: ${error.message}")
            }
        }, intervalTicks, intervalTicks)
        
        plugin.logger.info("Admin configuration module ${if (plugin.config.getBoolean("admin.enabled", true)) "enabled" else "disabled (managers initialized)"}")
    }
    
    /**
     * Called when the plugin is disabled
     */
    fun onDisable() {
        // Cancel scheduled tasks
        if (cleanupTaskId != -1) {
            Bukkit.getScheduler().cancelTask(cleanupTaskId)
        }
        if (autoBackupTaskId != -1) {
            Bukkit.getScheduler().cancelTask(autoBackupTaskId)
        }
        
        // Final cleanup
        if (::permissionManager.isInitialized) permissionManager.shutdown()
        if (::auditLogger.isInitialized) auditLogger.cleanupOldEntries()
        
        // Save configuration
        if (::configManager.isInitialized) configManager.saveConfig()
    }
}
