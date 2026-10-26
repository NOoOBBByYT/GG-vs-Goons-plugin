package com.tyler.ggvsgoons.admin

import com.tyler.ggvsgoons.GGvGPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages runtime configuration changes and module control
 */
class AdminConfigManager(
    private val plugin: GGvGPlugin,
    private val validator: ConfigValidator,
    private val auditLogger: AuditLogger
) {
    
    private val configCache = ConcurrentHashMap<String, Any>()
    
    /**
     * Get a configuration value
     */
    fun getConfigValue(path: String): Any? {
        if (!validator.isValidPath(path)) {
            return null
        }
        
        // Check cache first
        if (configCache.containsKey(path)) {
            return configCache[path]
        }
        
        // Get from config
        val value = plugin.config.get(path)
        if (value != null) {
            configCache[path] = value
        }
        
        return value
    }
    
    /**
     * Set a configuration value with validation
     */
    fun setConfigValue(
        path: String, 
        value: Any, 
        adminId: UUID, 
        adminName: String, 
        reason: String? = null
    ): Result<Unit> {
        // Validate path
        if (!validator.isValidPath(path)) {
            return Result.failure(IllegalArgumentException("Invalid configuration path: $path"))
        }
        
        // Validate value
        val validationResult = validator.validate(path, value)
        if (validationResult is ValidationResult.Invalid) {
            return Result.failure(IllegalArgumentException(validationResult.reason))
        }
        
        val validatedValue = (validationResult as ValidationResult.Valid).value
        if (path in setOf(
                "prisoner.offer-expiry-timeout", "prisoner.enable-persistence",
                "factions.enable-persistence", "admin.permissions.use-luckperms",
                "admin.audit.retention-days", "admin.audit.log-player-actions",
                "admin.audit.log-config-changes", "admin.audit.log-mod-actions",
                "admin.backups.auto-backup-interval", "admin.backups.max-backups"
            )) {
            return Result.failure(UnsupportedOperationException("$path is initialized at startup; edit config.yml and fully restart the server"))
        }
        
        // Get old value for audit log
        val oldValue = getConfigValue(path)
        
        // Set the value
        plugin.config.set(path, validatedValue)
        configCache[path] = validatedValue
        
        // Log the change
        val change = ConfigChange(
            adminId = adminId,
            adminName = adminName,
            path = path,
            oldValue = oldValue,
            newValue = validatedValue,
            reason = reason
        )
        auditLogger.logConfigChange(change)
        
        // Notify relevant modules of the change
        notifyConfigChange(path, validatedValue)
        
        return Result.success(Unit)
    }
    
    /**
     * Notify modules of configuration changes
     */
    private fun notifyConfigChange(path: String, value: Any) {
        // Notify specific modules based on the path
        when {
            path.startsWith("factions.") -> {
                if (plugin.factions.isRegistered) plugin.factions.manager.loadConfig()
                plugin.logger.info("Faction configuration changed: $path = $value")
            }
            path.startsWith("teams.") -> {
                plugin.logger.info("Team configuration changed: $path = $value")
            }
            path.startsWith("prisoner.") -> {
                plugin.logger.info("Prisoner configuration changed: $path = $value")
            }
        }
    }
    
    /**
     * Reset a configuration value to its default
     */
    fun resetConfigValue(path: String, adminId: UUID, adminName: String): Result<Unit> {
        if (!validator.isValidPath(path)) {
            return Result.failure(IllegalArgumentException("Invalid configuration path: $path"))
        }
        
        // Get default value from defaults
        plugin.config.addDefaults(plugin.config.getDefaults() ?: return Result.failure(
            IllegalStateException("No default configuration available")
        ))
        
        val defaultValue = plugin.config.getDefaults()?.get(path)
            ?: return Result.failure(IllegalArgumentException("No default value for path: $path"))
        
        return setConfigValue(path, defaultValue, adminId, adminName, "Reset to default")
    }
    
    /**
     * List all configuration options in a section
     */
    fun listConfigOptions(section: String?): Map<String, Any> {
        val config = plugin.config
        val result = mutableMapOf<String, Any>()
        
        if (section == null) {
            // List all top-level sections
            config.getKeys(false).forEach { key ->
                result[key] = config.get(key) ?: "null"
            }
        } else {
            // List options in specific section
            val configSection = config.getConfigurationSection(section)
            if (configSection != null) {
                configSection.getKeys(true).forEach { key ->
                    val fullPath = "$section.$key"
                    result[fullPath] = config.get(fullPath) ?: "null"
                }
            }
        }
        
        return result
    }
    
    /**
     * Reload configuration from file
     */
    fun reloadConfig(): Result<Unit> {
        return try {
            plugin.reloadConfig()
            configCache.clear()
            
            if (plugin.factions.isRegistered) plugin.factions.manager.loadConfig()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Save current configuration to file
     */
    fun saveConfig(): Result<Unit> {
        return try {
            plugin.saveConfig()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * List all modules and their states
     */
    fun listModules(): Map<String, ModuleInfo> {
        val modules = mutableMapOf<String, ModuleInfo>()
        
        modules["warprisoner"] = ModuleInfo(
            name = "War Prisoner",
            enabled = true,
            description = "Capture and manage war prisoners",
            dependencies = listOf("teams"),
            version = "1.0.0"
        )
        
        modules["teams"] = ModuleInfo(
            name = "Teams",
            enabled = true,
            description = "GG vs Goons team system",
            dependencies = emptyList(),
            version = "1.0.0"
        )
        
        modules["factions"] = ModuleInfo(
            name = "Factions",
            enabled = plugin.factions.isRegistered && plugin.config.getBoolean("factions.enabled", true),
            description = "Sub-groups within teams",
            dependencies = listOf("teams"),
            version = "1.0.0"
        )
        
        modules["admin"] = ModuleInfo(
            name = "Admin Configuration",
            enabled = plugin.admin.isRegistered && plugin.config.getBoolean("admin.enabled", true),
            description = "Runtime configuration and moderation",
            dependencies = emptyList(),
            version = "1.0.0"
        )
        
        return modules
    }
    
    /**
     * Enable a module
     */
    fun enableModule(moduleName: String, adminId: UUID, adminName: String): Result<Unit> {
        val configPath = when (moduleName.lowercase()) {
            "factions" -> "factions.enabled"
            "admin" -> "admin.enabled"
            else -> return Result.failure(IllegalArgumentException("Module '$moduleName' is not toggleable or not implemented"))
        }
        
        val result = setConfigValue(configPath, true, adminId, adminName, "Module enabled")
        if (result.isSuccess) {
            // Log the action
            val action = ModerationAction(
                adminId = adminId,
                targetId = null,
                actionType = ModActionType.MODULE_ENABLE,
                reason = "Enabled module: $moduleName"
            )
            auditLogger.logModAction(action, adminName, moduleName, true)
        }
        
        return result
    }
    
    /**
     * Disable a module
     */
    fun disableModule(moduleName: String, adminId: UUID, adminName: String): Result<Unit> {
        val configPath = when (moduleName.lowercase()) {
            "factions" -> "factions.enabled"
            "admin" -> "admin.enabled"
            else -> return Result.failure(IllegalArgumentException("Module '$moduleName' is not toggleable or not implemented"))
        }
        
        val result = setConfigValue(configPath, false, adminId, adminName, "Module disabled")
        if (result.isSuccess) {
            // Log the action
            val action = ModerationAction(
                adminId = adminId,
                targetId = null,
                actionType = ModActionType.MODULE_DISABLE,
                reason = "Disabled module: $moduleName"
            )
            auditLogger.logModAction(action, adminName, moduleName, true)
        }
        
        return result
    }
    
    /**
     * Reload a specific module
     */
    fun reloadModule(moduleName: String): Result<Unit> {
        return when (moduleName.lowercase()) {
            "factions" -> {
                if (!plugin.factions.isRegistered) return Result.failure(IllegalStateException("Factions module is not registered"))
                plugin.factions.manager.loadConfig()
                Result.success(Unit)
            }
            else -> Result.failure(UnsupportedOperationException("Module '$moduleName' does not support a runtime reload; use /ggadmin config reload for configuration changes"))
        }
    }
    
    /**
     * Get module information
     */
    fun getModuleInfo(moduleName: String): ModuleInfo? {
        return listModules()[moduleName.lowercase()]
    }
    
    /**
     * Check if a module is enabled
     */
    fun isModuleEnabled(moduleName: String): Boolean {
        return listModules()[moduleName.lowercase()]?.enabled ?: false
    }
}
