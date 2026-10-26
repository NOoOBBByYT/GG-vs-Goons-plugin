package com.tyler.ggvsgoons.factions

import com.tyler.ggvsgoons.GGvGPlugin
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages all faction operations including creation, member management, and data persistence.
 */
class FactionManager(private val plugin: GGvGPlugin) {
    
    private val factions = ConcurrentHashMap<String, Faction>()
    private val playerFactions = ConcurrentHashMap<UUID, String>() // playerId -> factionId
    private val pendingInvites = ConcurrentHashMap<UUID, MutableList<FactionInvite>>() // inviteeId -> invites
    private val factionStats = ConcurrentHashMap<String, FactionStats>()
    
    // Configuration values
    private var maxFactionsPerTeam: Int = 3
    private var defaultMaxMembers: Int = 10
    private var inviteExpirySeconds: Int = 60
    
    /**
     * Load configuration values
     */
    fun loadConfig() {
        maxFactionsPerTeam = plugin.config.getInt("factions.max-factions-per-team", 3)
        defaultMaxMembers = plugin.config.getInt("factions.members.default-max-members", 10)
        inviteExpirySeconds = plugin.config.getInt("factions.invite-expiry-seconds", 60)
    }
    
    /**
     * Create a new faction
     */
    fun createFaction(name: String, leaderId: UUID, teamId: String): Result<Faction> {
        if (plugin.teams.manager.getPlayerTeam(leaderId)?.id != teamId) {
            return Result.failure(IllegalStateException("Faction leader must belong to the faction's team"))
        }
        // Validate faction name
        if (name.length < 3 || name.length > 16) {
            return Result.failure(IllegalArgumentException("Faction name must be between 3 and 16 characters"))
        }
        
        if (!name.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            return Result.failure(IllegalArgumentException("Faction name can only contain letters, numbers, and underscores"))
        }
        
        // Check if player is already in a faction
        if (playerFactions.containsKey(leaderId)) {
            return Result.failure(IllegalStateException("You are already in a faction"))
        }
        
        // Check faction limit for team
        val teamFactionCount = factions.values.count { it.teamId == teamId }
        if (teamFactionCount >= maxFactionsPerTeam) {
            return Result.failure(IllegalStateException("Team has reached maximum faction limit ($maxFactionsPerTeam)"))
        }
        
        // Check if faction name already exists for this team
        val nameExists = factions.values.any { 
            it.teamId == teamId && it.name.equals(name, ignoreCase = true) 
        }
        if (nameExists) {
            return Result.failure(IllegalArgumentException("A faction with this name already exists on your team"))
        }
        
        // Create faction
        val factionId = "${teamId}_${name.lowercase()}_${System.currentTimeMillis()}"
        val faction = Faction(
            id = factionId,
            name = name,
            teamId = teamId,
            leaderId = leaderId,
            maxMembers = defaultMaxMembers
        )
        
        factions[factionId] = faction
        playerFactions[leaderId] = factionId
        factionStats[factionId] = FactionStats(factionId)
        
        return Result.success(faction)
    }
    
    /**
     * Disband a faction
     */
    fun disbandFaction(factionId: String, requesterId: UUID): Result<Unit> {
        val faction = factions[factionId] 
            ?: return Result.failure(IllegalArgumentException("Faction not found"))
        
        if (faction.leaderId != requesterId) {
            return Result.failure(IllegalStateException("Only the faction leader can disband the faction"))
        }
        
        // Remove all members from player faction map
        faction.members.forEach { playerId ->
            playerFactions.remove(playerId)
        }
        
        // Remove faction
        factions.remove(factionId)
        factionStats.remove(factionId)
        
        // Clear any pending invites for this faction
        pendingInvites.values.forEach { invites ->
            invites.removeIf { it.factionId == factionId }
        }
        
        return Result.success(Unit)
    }
    
    /**
     * Get a faction by ID
     */
    fun getFaction(factionId: String): Faction? = factions[factionId]
    
    /**
     * Get a faction by player UUID
     */
    fun getFactionByPlayer(playerId: UUID): Faction? {
        val factionId = playerFactions[playerId] ?: return null
        return factions[factionId]
    }
    
    /**
     * Get all factions for a team
     */
    fun getFactionsByTeam(teamId: String): List<Faction> {
        return factions.values.filter { it.teamId == teamId }
    }
    
    /**
     * Get all factions
     */
    fun getAllFactions(): List<Faction> = factions.values.toList()
    
    /**
     * Invite a player to a faction
     */
    fun invitePlayer(factionId: String, inviterId: UUID, inviteeId: UUID): Result<FactionInvite> {
        val faction = factions[factionId]
            ?: return Result.failure(IllegalArgumentException("Faction not found"))
        
        // Check if inviter has permission
        val inviterRole = faction.getRole(inviterId)
        if (inviterRole == null || !inviterRole.canInvite()) {
            return Result.failure(IllegalStateException("You don't have permission to invite members"))
        }
        
        // Check if invitee is already in a faction
        if (playerFactions.containsKey(inviteeId)) {
            return Result.failure(IllegalStateException("Player is already in a faction"))
        }
        
        // Check if faction is full
        if (faction.isFull()) {
            return Result.failure(IllegalStateException("Faction is at maximum capacity"))
        }
        
        // Check if invitee is on the same team
        if (plugin.teams.manager.getPlayerTeam(inviterId)?.id != faction.teamId ||
            plugin.teams.manager.getPlayerTeam(inviteeId)?.id != faction.teamId) {
            return Result.failure(IllegalStateException("Both players must be on the faction's team"))
        }
        
        // Check if invite already exists
        val existingInvites = pendingInvites.getOrPut(inviteeId) { mutableListOf() }
        val hasExistingInvite = existingInvites.any { 
            it.factionId == factionId && !it.isExpired() 
        }
        if (hasExistingInvite) {
            return Result.failure(IllegalStateException("Player already has a pending invite from this faction"))
        }
        
        // Create invite
        val invite = FactionInvite(
            factionId = factionId,
            inviterId = inviterId,
            inviteeId = inviteeId,
            expiresAt = System.currentTimeMillis() + (inviteExpirySeconds * 1000L)
        )
        
        existingInvites.add(invite)
        
        // Schedule invite expiry
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            existingInvites.remove(invite)
        }, inviteExpirySeconds * 20L)
        
        return Result.success(invite)
    }
    
    /**
     * Accept a faction invite
     */
    fun acceptInvite(playerId: UUID, factionId: String): Result<Unit> {
        val invites = pendingInvites[playerId]
            ?: return Result.failure(IllegalArgumentException("No pending invites"))
        
        val invite = invites.find { it.factionId == factionId && !it.isExpired() }
            ?: return Result.failure(IllegalArgumentException("No valid invite from this faction"))
        
        val faction = factions[factionId]
            ?: return Result.failure(IllegalArgumentException("Faction no longer exists"))
        if (plugin.teams.manager.getPlayerTeam(playerId)?.id != faction.teamId) {
            invites.remove(invite)
            return Result.failure(IllegalStateException("You must still be on the faction's team"))
        }
        if (faction.getRole(invite.inviterId)?.canInvite() != true ||
            plugin.teams.manager.getPlayerTeam(invite.inviterId)?.id != faction.teamId) {
            invites.remove(invite)
            return Result.failure(IllegalStateException("The player who invited you can no longer invite"))
        }
        
        // Check if player is already in a faction
        if (playerFactions.containsKey(playerId)) {
            return Result.failure(IllegalStateException("You are already in a faction"))
        }
        
        // Check if faction is full
        if (faction.isFull()) {
            return Result.failure(IllegalStateException("Faction is now at maximum capacity"))
        }
        
        // Add player to faction
        faction.members.add(playerId)
        playerFactions[playerId] = factionId
        
        // Remove invite
        invites.remove(invite)
        
        return Result.success(Unit)
    }
    
    /**
     * Decline a faction invite
     */
    fun declineInvite(playerId: UUID, factionId: String): Result<Unit> {
        val invites = pendingInvites[playerId]
            ?: return Result.failure(IllegalArgumentException("No pending invites"))
        
        val invite = invites.find { it.factionId == factionId && !it.isExpired() }
            ?: return Result.failure(IllegalArgumentException("No valid invite from this faction"))
        
        invites.remove(invite)
        return Result.success(Unit)
    }
    
    /**
     * Get pending invites for a player
     */
    fun getPendingInvites(playerId: UUID): List<FactionInvite> {
        val invites = pendingInvites[playerId] ?: return emptyList()
        // Remove expired invites
        invites.removeIf { it.isExpired() }
        return invites.toList()
    }
    
    /**
     * Kick a member from a faction
     */
    fun kickMember(factionId: String, kickerId: UUID, targetId: UUID): Result<Unit> {
        val faction = factions[factionId]
            ?: return Result.failure(IllegalArgumentException("Faction not found"))
        
        // Check if kicker has permission
        val kickerRole = faction.getRole(kickerId)
        if (kickerRole == null || !kickerRole.canKick()) {
            return Result.failure(IllegalStateException("You don't have permission to kick members"))
        }
        
        // Can't kick the leader
        if (faction.isLeader(targetId)) {
            return Result.failure(IllegalStateException("Cannot kick the faction leader"))
        }
        
        // Officers can't kick other officers (only leader can)
        if (kickerRole == FactionRole.OFFICER && faction.isOfficer(targetId)) {
            return Result.failure(IllegalStateException("Officers cannot kick other officers"))
        }
        
        // Check if target is in faction
        if (!faction.isMember(targetId)) {
            return Result.failure(IllegalArgumentException("Player is not in this faction"))
        }
        
        // Remove member
        removeMember(faction, targetId)
        
        return Result.success(Unit)
    }
    
    /**
     * Leave a faction
     */
    fun leaveFaction(playerId: UUID): Result<Unit> {
        val factionId = playerFactions[playerId]
            ?: return Result.failure(IllegalStateException("You are not in a faction"))
        
        val faction = factions[factionId]
            ?: return Result.failure(IllegalArgumentException("Faction not found"))
        
        // Leader cannot leave, must transfer or disband
        if (faction.isLeader(playerId)) {
            return Result.failure(IllegalStateException("Leader must transfer leadership or disband the faction"))
        }
        
        // Remove member
        removeMember(faction, playerId)
        
        return Result.success(Unit)
    }

    /** Also used by admin removals and team changes to update both membership indexes. */
    fun removeMemberForTeamChange(playerId: UUID): Result<Unit> {
        val faction = getFactionByPlayer(playerId)
        if (faction != null) {
            if (faction.isLeader(playerId)) {
                return Result.failure(IllegalStateException("Transfer faction leadership or disband the faction before leaving the team"))
            }
            removeMember(faction, playerId)
        }
        pendingInvites.remove(playerId)
        return Result.success(Unit)
    }

    fun forceRemoveMember(playerId: UUID): Result<Unit> {
        val faction = getFactionByPlayer(playerId)
            ?: return Result.failure(IllegalArgumentException("Player is not in a faction"))
        if (faction.isLeader(playerId)) {
            return Result.failure(IllegalStateException("Transfer leadership or disband the faction before removing its leader"))
        }
        removeMember(faction, playerId)
        return Result.success(Unit)
    }

    private fun removeMember(faction: Faction, playerId: UUID) {
        faction.members.remove(playerId)
        faction.officers.remove(playerId)
        playerFactions.remove(playerId, faction.id)
        pendingInvites.remove(playerId)
    }
    
    /**
     * Promote a member to officer
     */
    fun promoteMember(factionId: String, leaderId: UUID, targetId: UUID): Result<Unit> {
        val faction = factions[factionId]
            ?: return Result.failure(IllegalArgumentException("Faction not found"))
        
        if (!faction.isLeader(leaderId)) {
            return Result.failure(IllegalStateException("Only the leader can promote members"))
        }
        
        if (!faction.isMember(targetId)) {
            return Result.failure(IllegalArgumentException("Player is not in this faction"))
        }
        if (faction.isLeader(targetId)) {
            return Result.failure(IllegalStateException("The leader cannot also be promoted to officer"))
        }
        
        if (faction.isOfficer(targetId)) {
            return Result.failure(IllegalStateException("Player is already an officer"))
        }
        
        faction.officers.add(targetId)
        return Result.success(Unit)
    }
    
    /**
     * Demote an officer to member
     */
    fun demoteMember(factionId: String, leaderId: UUID, targetId: UUID): Result<Unit> {
        val faction = factions[factionId]
            ?: return Result.failure(IllegalArgumentException("Faction not found"))
        
        if (!faction.isLeader(leaderId)) {
            return Result.failure(IllegalStateException("Only the leader can demote officers"))
        }
        
        if (!faction.isOfficer(targetId)) {
            return Result.failure(IllegalStateException("Player is not an officer"))
        }
        
        faction.officers.remove(targetId)
        return Result.success(Unit)
    }
    
    /**
     * Transfer faction leadership
     */
    fun transferLeadership(factionId: String, currentLeaderId: UUID, newLeaderId: UUID): Result<Unit> {
        val faction = factions[factionId]
            ?: return Result.failure(IllegalArgumentException("Faction not found"))
        
        if (!faction.isLeader(currentLeaderId)) {
            return Result.failure(IllegalStateException("Only the leader can transfer leadership"))
        }
        
        if (!faction.isMember(newLeaderId)) {
            return Result.failure(IllegalArgumentException("New leader must be a faction member"))
        }
        if (newLeaderId == currentLeaderId) {
            return Result.failure(IllegalArgumentException("That player is already the faction leader"))
        }
        if (plugin.teams.manager.getPlayerTeam(newLeaderId)?.id != faction.teamId) {
            return Result.failure(IllegalStateException("New leader must still be on the faction's team"))
        }
        
        // Create new faction with updated leader
        val updatedFaction = faction.copy(leaderId = newLeaderId)
        factions[factionId] = updatedFaction
        
        // Remove new leader from officers if they were one
        updatedFaction.officers.remove(newLeaderId)
        
        return Result.success(Unit)
    }
    
    /**
     * Set faction home location
     */
    fun setFactionHome(factionId: String, playerId: UUID, location: Location): Result<Unit> {
        val faction = factions[factionId]
            ?: return Result.failure(IllegalArgumentException("Faction not found"))
        
        val role = faction.getRole(playerId)
        if (role == null || !role.canSetHome()) {
            return Result.failure(IllegalStateException("You don't have permission to set faction home"))
        }
        
        faction.homeLocation = location
        return Result.success(Unit)
    }
    
    /**
     * Get faction statistics
     */
    fun getFactionStats(factionId: String): FactionStats? = factionStats[factionId]
    
    /**
     * Update faction statistics
     */
    fun updateStats(factionId: String, update: (FactionStats) -> Unit) {
        val stats = factionStats.getOrPut(factionId) { FactionStats(factionId) }
        update(stats)
    }
    
    /**
     * Load factions from persistence
     */
    fun loadFactions(loadedFactions: List<Faction>) {
        val seenMembers = mutableSetOf<UUID>()
        val seenIds = mutableSetOf<String>()
        loadedFactions.forEach { faction ->
            require(seenIds.add(faction.id)) { "Duplicate faction id ${faction.id}" }
            require(faction.leaderId in faction.members) { "Faction ${faction.name} has no leader in its membership" }
            require(faction.officers.all { it in faction.members && it != faction.leaderId }) {
                "Faction ${faction.name} has officers who are not eligible members"
            }
            faction.members.forEach { memberId ->
                require(seenMembers.add(memberId)) { "Player $memberId is in multiple factions" }
                require(plugin.teams.manager.getPlayerTeam(memberId)?.id == faction.teamId) {
                    "Player $memberId is not on the saved team for faction ${faction.name}"
                }
            }
        }
        factions.clear()
        playerFactions.clear()
        pendingInvites.clear()
        factionStats.clear()
        
        loadedFactions.forEach { faction ->
            factions[faction.id] = faction
            factionStats[faction.id] = FactionStats(faction.id)
            faction.members.forEach { memberId ->
                playerFactions[memberId] = faction.id
            }
        }
    }
    
    /**
     * Get all factions for persistence
     */
    fun getFactions(): List<Faction> = factions.values.toList()
    
    /**
     * Clean up expired invites
     */
    fun cleanupExpiredInvites() {
        pendingInvites.values.forEach { invites ->
            invites.removeIf { it.isExpired() }
        }
    }
}
