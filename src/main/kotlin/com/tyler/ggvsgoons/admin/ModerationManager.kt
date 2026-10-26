package com.tyler.ggvsgoons.admin

import com.tyler.ggvsgoons.GGvGPlugin
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages player moderation actions
 */
class ModerationManager(
    private val plugin: GGvGPlugin,
    private val auditLogger: AuditLogger
) {
    
    private val frozenPlayers = ConcurrentHashMap<UUID, FrozenPlayer>()
    private val commandRestrictions = ConcurrentHashMap<String, CommandRestriction>()
    
    /**
     * Freeze a player (prevent movement and interaction)
     */
    fun freezePlayer(playerId: UUID, adminId: UUID, reason: String): Result<Unit> {
        if (frozenPlayers.containsKey(playerId)) {
            return Result.failure(IllegalStateException("Player is already frozen"))
        }
        
        val frozen = FrozenPlayer(
            playerId = playerId,
            frozenBy = adminId,
            reason = reason
        )
        
        frozenPlayers[playerId] = frozen
        
        // Notify player
        Bukkit.getPlayer(playerId)?.sendMessage("§c§lYou have been frozen by an administrator")
        Bukkit.getPlayer(playerId)?.sendMessage("§7Reason: $reason")
        
        return Result.success(Unit)
    }
    
    /**
     * Unfreeze a player
     */
    fun unfreezePlayer(playerId: UUID): Result<Unit> {
        val frozen = frozenPlayers.remove(playerId)
            ?: return Result.failure(IllegalArgumentException("Player is not frozen"))
        
        // Notify player
        Bukkit.getPlayer(playerId)?.sendMessage("§aYou have been unfrozen")
        
        return Result.success(Unit)
    }
    
    /**
     * Check if a player is frozen
     */
    fun isPlayerFrozen(playerId: UUID): Boolean {
        return frozenPlayers.containsKey(playerId)
    }
    
    /**
     * Get frozen player info
     */
    fun getFrozenPlayer(playerId: UUID): FrozenPlayer? {
        return frozenPlayers[playerId]
    }
    
    /**
     * Get all frozen players
     */
    fun getAllFrozenPlayers(): List<FrozenPlayer> {
        return frozenPlayers.values.toList()
    }
    
    /**
     * Force free a prisoner
     */
    fun forceFreePrisoner(playerId: UUID, adminId: UUID, adminName: String): Result<Unit> {
        // Check if player is a prisoner
        val isPrisoner = plugin.warPrisoner.manager.isPrisoner(playerId)
        if (!isPrisoner) {
            return Result.failure(IllegalArgumentException("Player is not a prisoner"))
        }
        
        // Free the prisoner
        val success = plugin.warPrisoner.manager.releasePrisoner(playerId)
        if (success) {
            
            // Log the action
            val action = ModerationAction(
                adminId = adminId,
                targetId = playerId,
                actionType = ModActionType.FORCE_FREE_PRISONER,
                reason = "Admin force-freed prisoner"
            )
            val playerName = Bukkit.getOfflinePlayer(playerId).name ?: playerId.toString()
            auditLogger.logModAction(action, adminName, playerName, true)
            
            return Result.success(Unit)
        }
        
        return Result.failure(IllegalStateException("Prisoner must be online before their game mode can be safely restored"))
    }
    
    /**
     * Reset all cooldowns for a player
     */
    fun resetCooldowns(playerId: UUID, adminId: UUID, adminName: String): Result<Unit> {
        plugin.teams.manager.resetSwitchCooldown(playerId)
        
        val action = ModerationAction(
            adminId = adminId,
            targetId = playerId,
            actionType = ModActionType.RESET_COOLDOWN,
            reason = "Admin reset team-switch cooldown"
        )
        val playerName = Bukkit.getOfflinePlayer(playerId).name ?: playerId.toString()
        auditLogger.logModAction(action, adminName, playerName, true)
        
        return Result.success(Unit)
    }
    
    /**
     * Kick a player from their team
     */
    fun kickFromTeam(playerId: UUID, adminId: UUID, adminName: String): Result<Unit> {
        val team = plugin.teams.manager.getPlayerTeam(playerId)
            ?: return Result.failure(IllegalArgumentException("Player is not on a team"))

        val removal = plugin.teams.manager.removePlayerFromTeam(playerId)
        if (removal.isFailure) return removal
        
        // Log the action
        val action = ModerationAction(
            adminId = adminId,
            targetId = playerId,
            actionType = ModActionType.KICK_FROM_TEAM,
            reason = "Admin kicked from team: ${team.id}"
        )
        val playerName = Bukkit.getOfflinePlayer(playerId).name ?: playerId.toString()
        auditLogger.logModAction(action, adminName, playerName, true)
        
        // Notify player
        Bukkit.getPlayer(playerId)?.sendMessage("§cYou have been removed from your team by an administrator")
        
        return Result.success(Unit)
    }
    
    /**
     * Kick a player from their faction
     */
    fun kickFromFaction(playerId: UUID, adminId: UUID, adminName: String): Result<Unit> {
        if (!plugin.factions.isRegistered) {
            return Result.failure(IllegalStateException("Factions module is not loaded"))
        }
        val removal = plugin.factions.manager.forceRemoveMember(playerId)
        if (removal.isFailure) return removal

        val action = ModerationAction(
            adminId = adminId,
            targetId = playerId,
            actionType = ModActionType.KICK_FROM_FACTION,
            reason = "Admin kicked from faction"
        )
        val playerName = Bukkit.getOfflinePlayer(playerId).name ?: playerId.toString()
        auditLogger.logModAction(action, adminName, playerName, true)
        Bukkit.getPlayer(playerId)?.sendMessage("§cYou have been removed from your faction by an administrator")
        return Result.success(Unit)
    }
    
    /**
     * Get player information
     */
    fun getPlayerInfo(playerId: UUID): PlayerInfo {
        val player = Bukkit.getOfflinePlayer(playerId)
        val team = plugin.teams.manager.getPlayerTeam(playerId)
        val isPrisoner = plugin.warPrisoner.manager.isPrisoner(playerId)
        val isFrozen = isPlayerFrozen(playerId)
        
        val factionId = if (plugin.factions.isRegistered) {
            plugin.factions.manager.getFactionByPlayer(playerId)?.id
        } else null
        
        val onlineSince = if (player.isOnline) {
            player.player?.let { System.currentTimeMillis() - (it.firstPlayed) }
        } else null
        val stats = player.player?.let { plugin.stats.manager.getStats(it) }
        
        return PlayerInfo(
            playerId = playerId,
            playerName = player.name ?: "Unknown",
            teamId = team?.id,
            factionId = factionId,
            isPrisoner = isPrisoner,
            isFrozen = isFrozen,
            onlineSince = onlineSince,
            totalPlaytime = player.firstPlayed.let { System.currentTimeMillis() - it },
            kills = stats?.kills ?: 0,
            deaths = stats?.deaths ?: 0,
            prisonersCaptured = stats?.prisonersCaptured ?: 0
        )
    }
    
    /**
     * Disable a command globally
     */
    fun disableCommand(command: String, reason: String? = null): Result<Unit> {
        val key = resolveCommand(command).getOrElse { return Result.failure(it) }
        val restriction = commandRestrictions[key] ?: CommandRestriction(command = key, disabled = false)
        commandRestrictions[key] = restriction.copy(disabled = true, reason = reason)
        return Result.success(Unit)
    }
    
    /**
     * Enable a command globally
     */
    fun enableCommand(command: String): Result<Unit> {
        val key = resolveCommand(command).getOrElse { return Result.failure(it) }
        val restriction = commandRestrictions[key]
            ?: return Result.failure(IllegalArgumentException("Command is not restricted"))
        if (restriction.disabledFor.isEmpty()) commandRestrictions.remove(key)
        else commandRestrictions[key] = restriction.copy(disabled = false, reason = null)
        return Result.success(Unit)
    }
    
    /**
     * Disable a command for a specific player
     */
    fun disableCommandForPlayer(command: String, playerId: UUID, reason: String? = null): Result<Unit> {
        val key = resolveCommand(command).getOrElse { return Result.failure(it) }
        val restriction = commandRestrictions[key] ?: CommandRestriction(command = key, disabled = false)
        commandRestrictions[key] = restriction.copy(disabledFor = (restriction.disabledFor + playerId).toMutableSet(), reason = reason)
        return Result.success(Unit)
    }
    
    /**
     * Enable a command for a specific player
     */
    fun enableCommandForPlayer(command: String, playerId: UUID): Result<Unit> {
        val key = resolveCommand(command).getOrElse { return Result.failure(it) }
        val restriction = commandRestrictions[key]
            ?: return Result.failure(IllegalArgumentException("Command is not restricted"))
        if (playerId !in restriction.disabledFor) return Result.failure(IllegalArgumentException("Command is not restricted for this player"))
        val remaining = (restriction.disabledFor - playerId).toMutableSet()
        if (remaining.isEmpty() && !restriction.disabled) commandRestrictions.remove(key)
        else commandRestrictions[key] = restriction.copy(disabledFor = remaining)
        return Result.success(Unit)
    }
    
    /**
     * Check if a command is disabled for a player
     */
    fun isCommandDisabled(command: String, playerId: UUID): Boolean {
        val key = resolveCommand(command).getOrNull() ?: return false
        val restriction = commandRestrictions[key] ?: return false
        return restriction.disabled || restriction.disabledFor.contains(playerId)
    }

    /** Resolve aliases and namespaced labels to this plugin's registered command. */
    fun resolveCommand(command: String): Result<String> {
        val label = command.trim().removePrefix("/").lowercase()
        val key = if (':' in label && label.substringBefore(':') == plugin.name.lowercase()) label.substringAfter(':') else label
        val registered = plugin.getCommand(key)
            ?: return Result.failure(IllegalArgumentException("Only registered GGvGoons commands can be restricted: $command"))
        if (registered.name.equals("ggadmin", ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("The admin command cannot be restricted (recovery access)"))
        }
        return Result.success(registered.name.lowercase())
    }
    
    /**
     * Get command restriction
     */
    fun getCommandRestriction(command: String): CommandRestriction? {
        return resolveCommand(command).getOrNull()?.let { commandRestrictions[it] }
    }
    
    /**
     * Get all command restrictions
     */
    fun getAllCommandRestrictions(): List<CommandRestriction> {
        return commandRestrictions.values.toList()
    }
}
