package com.tyler.ggvsgoons.teams

import com.tyler.ggvsgoons.GGvGPlugin
import net.md_5.bungee.api.ChatColor
import org.bukkit.Bukkit
import org.bukkit.ChatColor as BukkitChatColor
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team as BukkitTeam
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns team membership and the Bukkit Scoreboard Teams that back it.
 * This class holds everything in memory; TeamPersistence is responsible
 * for loading/saving it to teams.yml.
 */
class TeamManager(private val plugin: GGvGPlugin) {

    val teams: MutableMap<String, Team> = mutableMapOf(
        "gg" to Team("gg", "GG", "GG", BukkitChatColor.GOLD),
        "goons" to Team("goons", "Goons", "Goons", BukkitChatColor.DARK_BLUE)
    )

    // player UUID -> team id ("gg" / "goons")
    private val playerTeams = ConcurrentHashMap<UUID, String>()

    // player UUID -> millis timestamp of their last team switch (for the cooldown)
    private val lastSwitch = ConcurrentHashMap<UUID, Long>()

    /** Clear the only currently implemented gameplay cooldown. */
    fun resetSwitchCooldown(playerId: UUID) {
        lastSwitch.remove(playerId)
    }

    private val scoreboard: Scoreboard?
        get() = Bukkit.getScoreboardManager()?.mainScoreboard

    /** Creates (or reuses) the two Bukkit scoreboard teams and sets color / friendly-fire rules.
     *  Call this once on plugin enable. */
    fun setupScoreboardTeams() {
        val board = scoreboard ?: return
        for (team in teams.values) {
            var bukkitTeam = board.getTeam(team.bukkitName)
            if (bukkitTeam == null) {
                bukkitTeam = board.registerNewTeam(team.bukkitName)
            }
            bukkitTeam.color = team.color
            bukkitTeam.setAllowFriendlyFire(false)
            bukkitTeam.setOption(BukkitTeam.Option.NAME_TAG_VISIBILITY, BukkitTeam.OptionStatus.ALWAYS)
        }
    }

    /** A personal sidebar has its own scoreboard, so copy the main scoreboard's team entries. */
    fun mirrorTeamsTo(board: Scoreboard) {
        val main = scoreboard ?: return
        if (board === main) return
        for (team in teams.values) {
            val source = main.getTeam(team.bukkitName) ?: continue
            val target = board.getTeam(team.bukkitName) ?: board.registerNewTeam(team.bukkitName)
            target.color = source.color
            target.setAllowFriendlyFire(source.allowFriendlyFire())
            target.setOption(BukkitTeam.Option.NAME_TAG_VISIBILITY, source.getOption(BukkitTeam.Option.NAME_TAG_VISIBILITY))
            (target.entries - source.entries).forEach { target.removeEntry(it) }
            (source.entries - target.entries).forEach { target.addEntry(it) }
        }
    }

    private fun notifyScoreboardChanged() {
        runCatching { plugin.scoreboard.manager }.getOrNull()?.syncTeams()
    }

    /** Re-adds a player's Bukkit scoreboard entry after they (re)join, based on their saved team.
     *  Needed because teams.yml remembers membership, but the Bukkit scoreboard itself does not
     *  persist across restarts. */
    fun restoreScoreboardEntry(player: Player) {
        // An admin may have removed an offline member while their old scoreboard entry remained.
        scoreboard?.getEntryTeam(player.name)?.removeEntry(player.name)
        val team = getPlayerTeam(player.uniqueId)
        if (team != null) scoreboard?.getTeam(team.bukkitName)?.addEntry(player.name)
        notifyScoreboardChanged()
    }

    fun joinTeam(player: Player, teamId: String): Boolean {
        val id = teamId.lowercase()
        val team = teams[id] ?: return false
        if (playerTeams[player.uniqueId] == id) return false

        val allowSwitching = plugin.config.getBoolean("teams.allow-team-switching", true)
        if (!allowSwitching && playerTeams.containsKey(player.uniqueId)) {
            player.sendMessage("${ChatColor.RED}Team switching is disabled.")
            return false
        }

        val cooldown = plugin.config.getLong("teams.team-switch-cooldown", 300)
        if (cooldown > 0) {
            val last = lastSwitch[player.uniqueId]
            if (last != null) {
                val elapsedSeconds = (System.currentTimeMillis() - last) / 1000
                if (elapsedSeconds < cooldown) {
                    val remaining = cooldown - elapsedSeconds
                    player.sendMessage("${ChatColor.RED}You can switch teams again in ${remaining}s.")
                    return false
                }
            }
        }

        val factionResult = detachFaction(player.uniqueId)
        if (factionResult.isFailure) {
            player.sendMessage("${ChatColor.RED}${factionResult.exceptionOrNull()?.message}")
            return false
        }

        // Pull them off whatever Bukkit team they're currently in (handles switching sides)
        scoreboard?.getEntryTeam(player.name)?.removeEntry(player.name)

        playerTeams[player.uniqueId] = id
        lastSwitch[player.uniqueId] = System.currentTimeMillis()
        scoreboard?.getTeam(team.bukkitName)?.addEntry(player.name)
        notifyScoreboardChanged()

        return true
    }

    /** Used only when restoring saved state from teams.yml on startup.
     *  Skips cooldowns and doesn't touch the scoreboard directly (the player isn't online yet;
     *  restoreScoreboardEntry() handles that when they join). */
    fun setPlayerTeamSilently(uuid: UUID, teamId: String) {
        require(teamId in teams) { "Unknown team id $teamId" }
        playerTeams[uuid] = teamId
    }

    fun leaveTeam(player: Player): Boolean {
        val result = removePlayerFromTeam(player.uniqueId)
        if (result.isFailure) player.sendMessage("${ChatColor.RED}${result.exceptionOrNull()?.message}")
        return result.isSuccess
    }

    /** Remove an online or offline player without leaving a stale faction or scoreboard entry. */
    fun removePlayerFromTeam(playerId: UUID): Result<Unit> {
        if (!playerTeams.containsKey(playerId)) {
            return Result.failure(IllegalStateException("Player is not on a team"))
        }
        val factionResult = detachFaction(playerId)
        if (factionResult.isFailure) return factionResult
        playerTeams.remove(playerId)
        plugin.server.getPlayer(playerId)?.let { player ->
            scoreboard?.getEntryTeam(player.name)?.removeEntry(player.name)
        }
        notifyScoreboardChanged()
        return Result.success(Unit)
    }

    private fun detachFaction(playerId: UUID): Result<Unit> =
        if (plugin.factions.isRegistered) plugin.factions.manager.removeMemberForTeamChange(playerId)
        else Result.success(Unit)

    fun getPlayerTeam(playerId: UUID): Team? = playerTeams[playerId]?.let { teams[it] }

    fun areOpposingTeams(player1: UUID, player2: UUID): Boolean {
        val a = playerTeams[player1] ?: return false
        val b = playerTeams[player2] ?: return false
        return a != b
    }

    fun getTeamMembers(teamId: String): List<UUID> =
        playerTeams.filterValues { it == teamId.lowercase() }.keys.toList()

    fun setTeamSpawn(teamId: String, location: Location) {
        teams[teamId.lowercase()]?.spawn = location
    }

    fun getTeamSpawn(teamId: String): Location? = teams[teamId.lowercase()]?.spawn

    fun teleportToTeamSpawn(player: Player): Boolean {
        val team = getPlayerTeam(player.uniqueId) ?: return false
        val spawn = team.spawn ?: return false
        player.teleport(spawn)
        return true
    }
}
