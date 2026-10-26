package com.tyler.ggvsgoons.admin

import com.tyler.ggvsgoons.GGvGPlugin
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import com.tyler.ggvsgoons.scoreboard.ScoreboardManager
import java.io.File

/**
 * Main admin command executor for /ggadmin
 */
class AdminCommand(
    private val plugin: GGvGPlugin,
    private val configManager: AdminConfigManager,
    private val moderationManager: ModerationManager,
    private val permissionManager: PermissionManager,
    private val backupManager: BackupManager,
    private val auditLogger: AuditLogger,
    private val scoreboardManagerProvider: () -> ScoreboardManager? = { null }
) : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        // Check if admin system is enabled
        if (!plugin.config.getBoolean("admin.enabled", true)) {
            sender.sendMessage("${ChatColor.RED}The admin system is currently disabled")
            return true
        }
        
        // Check permission
        if (!sender.hasPermission("ggvgoons.admin.use")) {
            sender.sendMessage("${ChatColor.RED}You don't have permission to use admin commands")
            return true
        }
        
        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }
        
        when (args[0].lowercase()) {
            "module" -> handleModule(sender, args)
            "config" -> handleConfig(sender, args)
            "command" -> handleCommand(sender, args)
            "permission", "perm" -> handlePermission(sender, args)
            "player" -> handlePlayer(sender, args)
            "stats" -> handleStats(sender, args)
            "scoreboard" -> handleScoreboard(sender, args)
            "tablist" -> handleTabList(sender, args)
            "audit" -> handleAudit(sender, args)
            "backup" -> handleBackup(sender, args)
            "help" -> sendHelp(sender)
            else -> sender.sendMessage("${ChatColor.RED}Unknown subcommand. Use /ggadmin help")
        }
        
        return true
    }
    
    private fun handleModule(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("ggvgoons.admin.module")) {
            sender.sendMessage("${ChatColor.RED}You don't have permission to manage modules")
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage("${ChatColor.RED}Usage: /ggadmin module <list|enable|disable|reload|info> [module]")
            return
        }
        
        when (args[1].lowercase()) {
            "list" -> {
                val modules = configManager.listModules()
                sender.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}=== Modules ===")
                modules.forEach { (name, info) ->
                    val status = if (info.enabled) "${ChatColor.GREEN}✓ Enabled" else "${ChatColor.RED}✗ Disabled"
                    sender.sendMessage("${ChatColor.YELLOW}$name: $status ${ChatColor.GRAY}- ${info.description}")
                }
            }
            "enable" -> {
                if (args.size < 3) {
                    sender.sendMessage("${ChatColor.RED}Usage: /ggadmin module enable <module>")
                    return
                }
                val moduleName = args[2]
                val adminId = if (sender is Player) sender.uniqueId else java.util.UUID.randomUUID()
                val result = configManager.enableModule(moduleName, adminId, sender.name)
                result.onSuccess {
                    sender.sendMessage("${ChatColor.GREEN}Enabled module: $moduleName")
                }.onFailure { error ->
                    sender.sendMessage("${ChatColor.RED}${error.message}")
                }
            }
            "disable" -> {
                if (args.size < 3) {
                    sender.sendMessage("${ChatColor.RED}Usage: /ggadmin module disable <module>")
                    return
                }
                val moduleName = args[2]
                val adminId = if (sender is Player) sender.uniqueId else java.util.UUID.randomUUID()
                val result = configManager.disableModule(moduleName, adminId, sender.name)
                result.onSuccess {
                    sender.sendMessage("${ChatColor.GREEN}Disabled module: $moduleName")
                    if (moduleName.equals("admin", ignoreCase = true)) {
                        sender.sendMessage("${ChatColor.YELLOW}Admin commands are now unavailable. Re-enable admin.enabled in config.yml and restart the server to restore access.")
                    }
                }.onFailure { error ->
                    sender.sendMessage("${ChatColor.RED}${error.message}")
                }
            }
            "reload" -> {
                if (args.size < 3) {
                    sender.sendMessage("${ChatColor.RED}Usage: /ggadmin module reload <module>")
                    return
                }
                val moduleName = args[2]
                val result = configManager.reloadModule(moduleName)
                result.onSuccess {
                    sender.sendMessage("${ChatColor.GREEN}Reloaded module: $moduleName")
                }.onFailure { error ->
                    sender.sendMessage("${ChatColor.RED}${error.message}")
                }
            }
            "info" -> {
                if (args.size < 3) {
                    sender.sendMessage("${ChatColor.RED}Usage: /ggadmin module info <module>")
                    return
                }
                val moduleName = args[2]
                val info = configManager.getModuleInfo(moduleName)
                if (info == null) {
                    sender.sendMessage("${ChatColor.RED}Module not found: $moduleName")
                    return
                }
                sender.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}=== Module Info: $moduleName ===")
                sender.sendMessage("${ChatColor.YELLOW}Name: ${ChatColor.WHITE}${info.name}")
                sender.sendMessage("${ChatColor.YELLOW}Status: ${if (info.enabled) "${ChatColor.GREEN}Enabled" else "${ChatColor.RED}Disabled"}")
                sender.sendMessage("${ChatColor.YELLOW}Description: ${ChatColor.WHITE}${info.description}")
                sender.sendMessage("${ChatColor.YELLOW}Version: ${ChatColor.WHITE}${info.version}")
                if (info.dependencies.isNotEmpty()) {
                    sender.sendMessage("${ChatColor.YELLOW}Dependencies: ${ChatColor.WHITE}${info.dependencies.joinToString(", ")}")
                }
            }
        }
    }
    
    private fun handleConfig(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("ggvgoons.admin.config")) {
            sender.sendMessage("${ChatColor.RED}You don't have permission to manage configuration")
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage("${ChatColor.RED}Usage: /ggadmin config <get|set|list|reset|reload|save> [args]")
            return
        }
        
        when (args[1].lowercase()) {
            "get" -> {
                if (args.size < 3) {
                    sender.sendMessage("${ChatColor.RED}Usage: /ggadmin config get <path>")
                    return
                }
                val path = args[2]
                val value = configManager.getConfigValue(path)
                if (value == null) {
                    sender.sendMessage("${ChatColor.RED}Configuration path not found or invalid: $path")
                } else {
                    sender.sendMessage("${ChatColor.YELLOW}$path: ${ChatColor.WHITE}$value")
                }
            }
            "set" -> {
                if (args.size < 4) {
                    sender.sendMessage("${ChatColor.RED}Usage: /ggadmin config set <path> <value>")
                    return
                }
                val path = args[2]
                val value = args[3]
                val adminId = if (sender is Player) sender.uniqueId else java.util.UUID.randomUUID()
                val result = configManager.setConfigValue(path, value, adminId, sender.name)
                result.onSuccess {
                    sender.sendMessage("${ChatColor.GREEN}Set $path = $value")
                    sender.sendMessage("${ChatColor.GRAY}Note: Some changes may require a reload")
                }.onFailure { error ->
                    sender.sendMessage("${ChatColor.RED}${error.message}")
                }
            }
            "list" -> {
                val section = if (args.size > 2) args[2] else null
                val options = configManager.listConfigOptions(section)
                sender.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}=== Configuration ${section ?: "Root"} ===")
                options.forEach { (path, value) ->
                    sender.sendMessage("${ChatColor.YELLOW}$path: ${ChatColor.WHITE}$value")
                }
            }
            "reset" -> {
                if (args.size < 3) {
                    sender.sendMessage("${ChatColor.RED}Usage: /ggadmin config reset <path>")
                    return
                }
                val path = args[2]
                val adminId = if (sender is Player) sender.uniqueId else java.util.UUID.randomUUID()
                val result = configManager.resetConfigValue(path, adminId, sender.name)
                result.onSuccess {
                    sender.sendMessage("${ChatColor.GREEN}Reset $path to default value")
                }.onFailure { error ->
                    sender.sendMessage("${ChatColor.RED}${error.message}")
                }
            }
            "reload" -> {
                val result = configManager.reloadConfig()
                result.onSuccess {
                    sender.sendMessage("${ChatColor.GREEN}Configuration reloaded from file")
                }.onFailure { error ->
                    sender.sendMessage("${ChatColor.RED}Failed to reload: ${error.message}")
                }
            }
            "save" -> {
                val result = configManager.saveConfig()
                result.onSuccess {
                    sender.sendMessage("${ChatColor.GREEN}Configuration saved to file")
                }.onFailure { error ->
                    sender.sendMessage("${ChatColor.RED}Failed to save: ${error.message}")
                }
            }
        }
    }
    
    private fun handleCommand(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("ggvgoons.admin.command")) {
            sender.sendMessage("${ChatColor.RED}You don't have permission to manage commands")
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage("${ChatColor.RED}Usage: /ggadmin command <disable|enable|status|list> [command] [player]")
            return
        }
        
        when (args[1].lowercase()) {
            "disable" -> {
                if (args.size < 3) {
                    sender.sendMessage("${ChatColor.RED}Usage: /ggadmin command disable <command> [player]")
                    return
                }
                val commandName = args[2]
                if (args.size > 3) {
                    // Disable for specific player
                    val playerName = args[3]
                    val target = Bukkit.getOfflinePlayer(playerName)
                    val result = moderationManager.disableCommandForPlayer(commandName, target.uniqueId, "Admin disabled")
                    result.onSuccess {
                        sender.sendMessage("${ChatColor.GREEN}Disabled command $commandName for $playerName")
                    }.onFailure { error ->
                        sender.sendMessage("${ChatColor.RED}${error.message}")
                    }
                } else {
                    // Disable globally
                    val result = moderationManager.disableCommand(commandName, "Admin disabled")
                    result.onSuccess {
                        sender.sendMessage("${ChatColor.GREEN}Disabled command $commandName globally")
                    }.onFailure { error ->
                        sender.sendMessage("${ChatColor.RED}${error.message}")
                    }
                }
            }
            "enable" -> {
                if (args.size < 3) {
                    sender.sendMessage("${ChatColor.RED}Usage: /ggadmin command enable <command> [player]")
                    return
                }
                val commandName = args[2]
                if (args.size > 3) {
                    // Enable for specific player
                    val playerName = args[3]
                    val target = Bukkit.getOfflinePlayer(playerName)
                    val result = moderationManager.enableCommandForPlayer(commandName, target.uniqueId)
                    result.onSuccess {
                        sender.sendMessage("${ChatColor.GREEN}Enabled command $commandName for $playerName")
                    }.onFailure { error ->
                        sender.sendMessage("${ChatColor.RED}${error.message}")
                    }
                } else {
                    // Enable globally
                    val result = moderationManager.enableCommand(commandName)
                    result.onSuccess {
                        sender.sendMessage("${ChatColor.GREEN}Enabled command $commandName globally")
                    }.onFailure { error ->
                        sender.sendMessage("${ChatColor.RED}${error.message}")
                    }
                }
            }
            "status" -> {
                if (args.size < 3) {
                    sender.sendMessage("${ChatColor.RED}Usage: /ggadmin command status <command>")
                    return
                }
                val commandName = args[2]
                val restriction = moderationManager.getCommandRestriction(commandName)
                if (restriction == null) {
                    sender.sendMessage("${ChatColor.YELLOW}Command $commandName has no restrictions")
                } else {
                    sender.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}=== Command Status: $commandName ===")
                    sender.sendMessage("${ChatColor.YELLOW}Globally Disabled: ${if (restriction.disabled) "${ChatColor.RED}Yes" else "${ChatColor.GREEN}No"}")
                    if (restriction.disabledFor.isNotEmpty()) {
                        sender.sendMessage("${ChatColor.YELLOW}Disabled for ${restriction.disabledFor.size} player(s)")
                    }
                    if (restriction.reason != null) {
                        sender.sendMessage("${ChatColor.YELLOW}Reason: ${ChatColor.WHITE}${restriction.reason}")
                    }
                }
            }
            "list" -> {
                val restrictions = moderationManager.getAllCommandRestrictions()
                if (restrictions.isEmpty()) {
                    sender.sendMessage("${ChatColor.YELLOW}No command restrictions active")
                } else {
                    sender.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}=== Command Restrictions ===")
                    restrictions.forEach { restriction ->
                        val status = if (restriction.disabled) "${ChatColor.RED}Disabled" else "${ChatColor.YELLOW}Partial"
                        sender.sendMessage("${ChatColor.YELLOW}${restriction.command}: $status")
                    }
                }
            }
        }
    }
    
    private fun handlePermission(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("ggvgoons.admin.permission")) {
            sender.sendMessage("${ChatColor.RED}You don't have permission to manage permissions")
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage("${ChatColor.RED}Usage: /ggadmin permission <grant|revoke|check|list> <player> [permission]")
            return
        }
        
        when (args[1].lowercase()) {
            "grant" -> {
                if (args.size < 4) {
                    sender.sendMessage("${ChatColor.RED}Usage: /ggadmin permission grant <player> <permission>")
                    return
                }
                val playerName = args[2]
                val permission = args[3]
                val target = Bukkit.getOfflinePlayer(playerName)
                val result = permissionManager.grantPermission(target.uniqueId, permission)
                result.onSuccess {
                    sender.sendMessage("${ChatColor.GREEN}Granted permission $permission to $playerName")
                }.onFailure { error ->
                    sender.sendMessage("${ChatColor.RED}${error.message}")
                }
            }
            "revoke" -> {
                if (args.size < 4) {
                    sender.sendMessage("${ChatColor.RED}Usage: /ggadmin permission revoke <player> <permission>")
                    return
                }
                val playerName = args[2]
                val permission = args[3]
                val target = Bukkit.getOfflinePlayer(playerName)
                val result = permissionManager.revokePermission(target.uniqueId, permission)
                result.onSuccess {
                    sender.sendMessage("${ChatColor.GREEN}Revoked permission $permission from $playerName")
                }.onFailure { error ->
                    sender.sendMessage("${ChatColor.RED}${error.message}")
                }
            }
            "check" -> {
                if (args.size < 4) {
                    sender.sendMessage("${ChatColor.RED}Usage: /ggadmin permission check <player> <permission>")
                    return
                }
                val playerName = args[2]
                val permission = args[3]
                val target = Bukkit.getOfflinePlayer(playerName)
                if (!target.isOnline) {
                    sender.sendMessage("${ChatColor.RED}Effective permissions can only be checked for online players")
                    return
                }
                val has = permissionManager.checkPermission(target.uniqueId, permission)
                sender.sendMessage("${ChatColor.YELLOW}$playerName ${if (has) "${ChatColor.GREEN}has" else "${ChatColor.RED}does not have"} ${ChatColor.YELLOW}permission: $permission")
            }
            "list" -> {
                if (args.size < 3) {
                    sender.sendMessage("${ChatColor.RED}Usage: /ggadmin permission list <player>")
                    return
                }
                val playerName = args[2]
                val target = Bukkit.getOfflinePlayer(playerName)
                val permissions = permissionManager.listPermissions(target.uniqueId)
                sender.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}=== This plugin's runtime grants for $playerName ===")
                if (permissions.isEmpty()) {
                    sender.sendMessage("${ChatColor.GRAY}No custom permissions")
                } else {
                    permissions.forEach { perm ->
                        sender.sendMessage("${ChatColor.YELLOW}- $perm")
                    }
                }
            }
        }
    }
    
    private fun handlePlayer(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("ggvgoons.admin.moderate")) {
            sender.sendMessage("${ChatColor.RED}You don't have permission to moderate players")
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage("${ChatColor.RED}Usage: /ggadmin player <freeze|unfreeze|freeprisoner|resetcooldowns|kickteam|kickfaction|info> <player>")
            return
        }
        
        when (args[1].lowercase()) {
            "freeze" -> {
                if (args.size < 3) {
                    sender.sendMessage("${ChatColor.RED}Usage: /ggadmin player freeze <player> [reason]")
                    return
                }
                val playerName = args[2]
                val reason = if (args.size > 3) args.drop(3).joinToString(" ") else "No reason provided"
                val target = Bukkit.getOfflinePlayer(playerName)
                val adminId = if (sender is Player) sender.uniqueId else java.util.UUID.randomUUID()
                val result = moderationManager.freezePlayer(target.uniqueId, adminId, reason)
                result.onSuccess {
                    sender.sendMessage("${ChatColor.GREEN}Frozen player: $playerName")
                }.onFailure { error ->
                    sender.sendMessage("${ChatColor.RED}${error.message}")
                }
            }
            "unfreeze" -> {
                if (args.size < 3) {
                    sender.sendMessage("${ChatColor.RED}Usage: /ggadmin player unfreeze <player>")
                    return
                }
                val playerName = args[2]
                val target = Bukkit.getOfflinePlayer(playerName)
                val result = moderationManager.unfreezePlayer(target.uniqueId)
                result.onSuccess {
                    sender.sendMessage("${ChatColor.GREEN}Unfrozen player: $playerName")
                }.onFailure { error ->
                    sender.sendMessage("${ChatColor.RED}${error.message}")
                }
            }
            "freeprisoner" -> {
                if (args.size < 3) {
                    sender.sendMessage("${ChatColor.RED}Usage: /ggadmin player freeprisoner <player>")
                    return
                }
                val playerName = args[2]
                val target = Bukkit.getOfflinePlayer(playerName)
                val adminId = if (sender is Player) sender.uniqueId else java.util.UUID.randomUUID()
                val result = moderationManager.forceFreePrisoner(target.uniqueId, adminId, sender.name)
                result.onSuccess {
                    sender.sendMessage("${ChatColor.GREEN}Force-freed prisoner: $playerName")
                }.onFailure { error ->
                    sender.sendMessage("${ChatColor.RED}${error.message}")
                }
            }
            "resetcooldowns" -> {
                if (args.size < 3) {
                    sender.sendMessage("${ChatColor.RED}Usage: /ggadmin player resetcooldowns <player>")
                    return
                }
                val playerName = args[2]
                val target = Bukkit.getOfflinePlayer(playerName)
                val adminId = if (sender is Player) sender.uniqueId else java.util.UUID.randomUUID()
                val result = moderationManager.resetCooldowns(target.uniqueId, adminId, sender.name)
                result.onSuccess {
                    sender.sendMessage("${ChatColor.GREEN}Reset team-switch cooldown for: $playerName")
                }.onFailure { error ->
                    sender.sendMessage("${ChatColor.RED}${error.message}")
                }
            }
            "kickteam" -> {
                if (args.size < 3) {
                    sender.sendMessage("${ChatColor.RED}Usage: /ggadmin player kickteam <player>")
                    return
                }
                val playerName = args[2]
                val target = Bukkit.getOfflinePlayer(playerName)
                val adminId = if (sender is Player) sender.uniqueId else java.util.UUID.randomUUID()
                val result = moderationManager.kickFromTeam(target.uniqueId, adminId, sender.name)
                result.onSuccess {
                    sender.sendMessage("${ChatColor.GREEN}Kicked $playerName from their team")
                }.onFailure { error ->
                    sender.sendMessage("${ChatColor.RED}${error.message}")
                }
            }
            "kickfaction" -> {
                if (args.size < 3) {
                    sender.sendMessage("${ChatColor.RED}Usage: /ggadmin player kickfaction <player>")
                    return
                }
                val playerName = args[2]
                val target = Bukkit.getOfflinePlayer(playerName)
                val adminId = if (sender is Player) sender.uniqueId else java.util.UUID.randomUUID()
                val result = moderationManager.kickFromFaction(target.uniqueId, adminId, sender.name)
                result.onSuccess {
                    sender.sendMessage("${ChatColor.GREEN}Kicked $playerName from their faction")
                }.onFailure { error ->
                    sender.sendMessage("${ChatColor.RED}${error.message}")
                }
            }
            "info" -> {
                if (args.size < 3) {
                    sender.sendMessage("${ChatColor.RED}Usage: /ggadmin player info <player>")
                    return
                }
                val playerName = args[2]
                val target = Bukkit.getOfflinePlayer(playerName)
                val info = moderationManager.getPlayerInfo(target.uniqueId)
                
                sender.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}=== Player Info: $playerName ===")
                sender.sendMessage("${ChatColor.YELLOW}Team: ${ChatColor.WHITE}${info.teamId ?: "None"}")
                sender.sendMessage("${ChatColor.YELLOW}Faction: ${ChatColor.WHITE}${info.factionId ?: "None"}")
                sender.sendMessage("${ChatColor.YELLOW}Prisoner: ${if (info.isPrisoner) "${ChatColor.RED}Yes" else "${ChatColor.GREEN}No"}")
                sender.sendMessage("${ChatColor.YELLOW}Frozen: ${if (info.isFrozen) "${ChatColor.RED}Yes" else "${ChatColor.GREEN}No"}")
                if (target.isOnline) {
                    sender.sendMessage("${ChatColor.YELLOW}K/D: ${ChatColor.WHITE}${info.kills}/${info.deaths}")
                    sender.sendMessage("${ChatColor.YELLOW}Prisoners Captured: ${ChatColor.WHITE}${info.prisonersCaptured}")
                } else sender.sendMessage("${ChatColor.GRAY}Statistics require the player to be online")
            }
        }
    }
    
    private fun handleStats(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("ggvgoons.admin.monitor")) {
            sender.sendMessage("${ChatColor.RED}You don't have permission to view statistics")
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage("${ChatColor.RED}Usage: /ggadmin stats <server|module|player|team> [name]")
            return
        }
        
        when (args[1].lowercase()) {
            "server" -> {
                sender.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}=== Server Statistics ===")
                sender.sendMessage("${ChatColor.YELLOW}Online Players: ${ChatColor.WHITE}${Bukkit.getOnlinePlayers().size}/${Bukkit.getMaxPlayers()}")
                sender.sendMessage("${ChatColor.YELLOW}TPS: ${ChatColor.WHITE}${String.format("%.2f", Bukkit.getTPS()[0])}")
                val runtime = Runtime.getRuntime()
                val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                val maxMemory = runtime.maxMemory() / 1024 / 1024
                sender.sendMessage("${ChatColor.YELLOW}Memory: ${ChatColor.WHITE}${usedMemory}MB / ${maxMemory}MB")
            }
            "module" -> {
                if (args.size < 3) {
                    sender.sendMessage("${ChatColor.RED}Usage: /ggadmin stats module <module>")
                    return
                }
                val moduleName = args[2]
                val info = configManager.getModuleInfo(moduleName)
                if (info == null) {
                    sender.sendMessage("${ChatColor.RED}Module not found: $moduleName")
                } else {
                    sender.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}=== Module Stats: $moduleName ===")
                    sender.sendMessage("${ChatColor.YELLOW}Status: ${if (info.enabled) "${ChatColor.GREEN}Enabled" else "${ChatColor.RED}Disabled"}")
                    sender.sendMessage("${ChatColor.YELLOW}Version: ${ChatColor.WHITE}${info.version}")
                }
            }
        }
    }
    
    private fun handleAudit(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("ggvgoons.admin.audit")) {
            sender.sendMessage("${ChatColor.RED}You don't have permission to view audit logs")
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage("${ChatColor.RED}Usage: /ggadmin audit <view|search|export> [args]")
            return
        }
        
        when (args[1].lowercase()) {
            "view" -> {
                val page = if (args.size > 2) args[2].toIntOrNull() ?: 0 else 0
                val entries = auditLogger.getEntriesByPage(page, 10)
                sender.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}=== Audit Log (Page ${page + 1}) ===")
                entries.forEach { entry ->
                    sender.sendMessage("${ChatColor.GRAY}${entry.format()}")
                }
            }
            "search" -> {
                if (args.size < 3) {
                    sender.sendMessage("${ChatColor.RED}Usage: /ggadmin audit search <query>")
                    return
                }
                val query = args.drop(2).joinToString(" ")
                val entries = auditLogger.search(query)
                sender.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}=== Audit Search: $query ===")
                entries.take(10).forEach { entry ->
                    sender.sendMessage("${ChatColor.GRAY}${entry.format()}")
                }
                if (entries.size > 10) {
                    sender.sendMessage("${ChatColor.GRAY}... and ${entries.size - 10} more results")
                }
            }
            "export" -> {
                val format = if (args.size > 2) args[2] else "txt"
                val outputFile = File(plugin.dataFolder, "audit_export_${System.currentTimeMillis()}.$format")
                val success = auditLogger.export(outputFile, format)
                if (success) {
                    sender.sendMessage("${ChatColor.GREEN}Exported audit log to: ${outputFile.name}")
                } else {
                    sender.sendMessage("${ChatColor.RED}Failed to export audit log")
                }
            }
        }
    }
    
    private fun handleBackup(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("ggvgoons.admin.backup")) {
            sender.sendMessage("${ChatColor.RED}You don't have permission to manage backups")
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage("${ChatColor.RED}Usage: /ggadmin backup <create|list|restore|delete> [name]")
            return
        }
        
        when (args[1].lowercase()) {
            "create" -> {
                val name = if (args.size > 2) args[2] else null
                val createdBy = if (sender is Player) sender.uniqueId else null
                val result = backupManager.createBackup(name, createdBy, "Manual backup")
                result.onSuccess { info ->
                    sender.sendMessage("${ChatColor.GREEN}Created backup: ${info.name}")
                }.onFailure { error ->
                    sender.sendMessage("${ChatColor.RED}Failed to create backup: ${error.message}")
                }
            }
            "list" -> {
                val backups = backupManager.listBackups()
                sender.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}=== Backups ===")
                backups.forEach { backup ->
                    val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(backup.timestamp))
                    val sizeMB = backup.size / 1024 / 1024
                    sender.sendMessage("${ChatColor.YELLOW}${backup.name} ${ChatColor.GRAY}- $date (${sizeMB}MB)")
                }
            }
            "restore" -> {
                if (args.size < 3) {
                    sender.sendMessage("${ChatColor.RED}Usage: /ggadmin backup restore <name>")
                    return
                }
                val name = args[2]
                sender.sendMessage("${ChatColor.YELLOW}Restoring backup: $name...")
                val result = backupManager.restoreBackup(name)
                result.onSuccess {
                    sender.sendMessage("${ChatColor.GREEN}Backup validated and queued for restore")
                    sender.sendMessage("${ChatColor.YELLOW}Fully stop and restart the server to apply it before plugin data loads. Do not use /reload.")
                }.onFailure { error ->
                    sender.sendMessage("${ChatColor.RED}Failed to restore backup: ${error.message}")
                }
            }
            "delete" -> {
                if (args.size < 3) {
                    sender.sendMessage("${ChatColor.RED}Usage: /ggadmin backup delete <name>")
                    return
                }
                val name = args[2]
                val result = backupManager.deleteBackup(name)
                result.onSuccess {
                    sender.sendMessage("${ChatColor.GREEN}Deleted backup: $name")
                }.onFailure { error ->
                    sender.sendMessage("${ChatColor.RED}Failed to delete backup: ${error.message}")
                }
            }
        }
    }
    
    private fun handleScoreboard(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("ggvgoons.scoreboard.admin")) {
            sender.sendMessage("${ChatColor.RED}You don't have permission to manage the scoreboard")
            return
        }
        val manager = scoreboardManagerProvider() ?: run {
            sender.sendMessage("${ChatColor.RED}Scoreboard module is not available.")
            return
        }
        when (args.getOrNull(1)?.lowercase()) {
            "reload" -> { plugin.reloadConfig(); manager.reload(); sender.sendMessage("${ChatColor.GREEN}Scoreboard configuration reloaded.") }
            "toggle" -> {
                val enabled = plugin.config.getBoolean("scoreboard.enabled", true)
                plugin.config.set("scoreboard.enabled", !enabled)
                plugin.saveConfig()
                manager.reload()
                sender.sendMessage("${ChatColor.GREEN}Scoreboard globally ${if (!enabled) "enabled" else "disabled"}.")
            }
            "update" -> { manager.forceUpdateAll(); sender.sendMessage("${ChatColor.GREEN}All scoreboards updated.") }
            else -> sender.sendMessage("${ChatColor.RED}Usage: /ggadmin scoreboard <reload|toggle|update>")
        }
    }

    private fun handleTabList(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("ggvgoons.tablist.admin")) {
            sender.sendMessage("${ChatColor.RED}You don't have permission to manage the tab list")
            return
        }
        val manager = scoreboardManagerProvider() ?: run {
            sender.sendMessage("${ChatColor.RED}Scoreboard module is not available.")
            return
        }
        when (args.getOrNull(1)?.lowercase()) {
            "reload" -> { plugin.reloadConfig(); manager.reload(); sender.sendMessage("${ChatColor.GREEN}Tab list configuration reloaded.") }
            "toggle" -> {
                val enabled = plugin.config.getBoolean("tablist.enabled", true)
                plugin.config.set("tablist.enabled", !enabled)
                plugin.saveConfig()
                manager.reload()
                sender.sendMessage("${ChatColor.GREEN}Tab list globally ${if (!enabled) "enabled" else "disabled"}.")
            }
            else -> sender.sendMessage("${ChatColor.RED}Usage: /ggadmin tablist <reload|toggle>")
        }
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}=== GGvGoons Admin Commands ===")
        sender.sendMessage("${ChatColor.YELLOW}/ggadmin module <list|enable|disable|reload|info> ${ChatColor.GRAY}- Manage modules")
        sender.sendMessage("${ChatColor.YELLOW}/ggadmin config <get|set|list|reset|reload|save> ${ChatColor.GRAY}- Manage configuration")
        sender.sendMessage("${ChatColor.YELLOW}/ggadmin command <disable|enable|status|list> ${ChatColor.GRAY}- Manage commands")
        sender.sendMessage("${ChatColor.YELLOW}/ggadmin permission <grant|revoke|check|list> ${ChatColor.GRAY}- Manage permissions")
        sender.sendMessage("${ChatColor.YELLOW}/ggadmin player <freeze|unfreeze|info|etc> ${ChatColor.GRAY}- Moderate players")
        sender.sendMessage("${ChatColor.YELLOW}/ggadmin stats <server|module|player|team> ${ChatColor.GRAY}- View statistics")
        sender.sendMessage("${ChatColor.YELLOW}/ggadmin scoreboard <reload|toggle|update> ${ChatColor.GRAY}- Manage scoreboard")
        sender.sendMessage("${ChatColor.YELLOW}/ggadmin tablist <reload|toggle> ${ChatColor.GRAY}- Manage tab list")
        sender.sendMessage("${ChatColor.YELLOW}/ggadmin audit <view|search|export> ${ChatColor.GRAY}- View audit logs")
        sender.sendMessage("${ChatColor.YELLOW}/ggadmin backup <create|list|restore|delete> ${ChatColor.GRAY}- Manage backups")
    }
    
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String>? {
        if (!sender.hasPermission("ggvgoons.admin.use")) return null
        
        return when (args.size) {
            1 -> listOf("module", "config", "command", "permission", "player", "stats", "scoreboard", "tablist", "audit", "backup", "help")
                .filter { it.startsWith(args[0].lowercase()) }
            
            2 -> when (args[0].lowercase()) {
                "module" -> listOf("list", "enable", "disable", "reload", "info")
                "config" -> listOf("get", "set", "list", "reset", "reload", "save")
                "command" -> listOf("disable", "enable", "status", "list")
                "permission" -> listOf("grant", "revoke", "check", "list")
                "player" -> listOf("freeze", "unfreeze", "freeprisoner", "resetcooldowns", "kickteam", "kickfaction", "info")
                "stats" -> listOf("server", "module", "player", "team")
                "audit" -> listOf("view", "search", "export")
                "backup" -> listOf("create", "list", "restore", "delete")
                "scoreboard" -> listOf("reload", "toggle", "update")
                "tablist" -> listOf("reload", "toggle")
                else -> null
            }?.filter { it.startsWith(args[1].lowercase()) }
            
            3 -> when (args[0].lowercase()) {
                "module" -> when (args[1].lowercase()) {
                    "enable", "disable", "reload", "info" ->
                        listOf("warprisoner", "teams", "factions", "admin")
                            .filter { it.startsWith(args[2].lowercase()) }
                    else -> null
                }
                "player" -> Bukkit.getOnlinePlayers().map { it.name }
                    .filter { it.startsWith(args[2], ignoreCase = true) }
                "permission" -> when (args[1].lowercase()) {
                    "grant", "revoke", "check", "list" ->
                        Bukkit.getOnlinePlayers().map { it.name }
                            .filter { it.startsWith(args[2], ignoreCase = true) }
                    else -> null
                }
                "backup" -> when (args[1].lowercase()) {
                    "restore", "delete" ->
                        backupManager.listBackups().map { it.name }
                            .filter { it.startsWith(args[2], ignoreCase = true) }
                    else -> null
                }
                else -> null
            }
            
            else -> null
        }
    }
}
