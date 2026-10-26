package com.tyler.ggvsgoons.teams

import com.tyler.ggvsgoons.GGvGModule
import com.tyler.ggvsgoons.GGvGPlugin
import com.tyler.ggvsgoons.persistence.TeamPersistence
import net.md_5.bungee.api.ChatColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.EventPriority

/**
 * Team module. Exposes its manager for cross-module membership checks.
 */
class TeamsModule(private val plugin: GGvGPlugin) : GGvGModule, Listener {

    val manager = TeamManager(plugin)
    val persistence = TeamPersistence(plugin, manager)

    override fun register(plugin: GGvGPlugin) {
        plugin.getCommand("team")?.setExecutor(TeamCommand(this, plugin))
        plugin.server.pluginManager.registerEvents(this, plugin)

        manager.setupScoreboardTeams()
        persistence.load()
    }

    /** Call this from GGvGPlugin.onDisable() so team data is saved on shutdown,
     *  the same way PrisonerPersistence is. */
    fun onDisable() {
        persistence.save()
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        // Bukkit scoreboard team entries don't survive a restart on their own —
        // teams.yml remembers who's on which team, this puts them back on rejoin.
        manager.restoreScoreboardEntry(event.player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onChat(event: AsyncPlayerChatEvent) {
        val message = event.message
        if (!message.startsWith("@team ")) return
        event.isCancelled = true
        val player = event.player
        val teamMessage = message.substring(6).trim()
        if (event.isAsynchronous) {
            Bukkit.getScheduler().runTask(plugin, Runnable { sendTeamChat(player, teamMessage) })
        } else {
            sendTeamChat(player, teamMessage)
        }
    }

    private fun sendTeamChat(player: Player, teamMessage: String) {
        if (!player.isOnline) return
        if (!plugin.config.getBoolean("teams.chat.enabled", true)) {
            player.sendMessage("${ChatColor.RED}Team chat is disabled.")
            return
        }

        if (teamMessage.isEmpty()) {
            player.sendMessage("${ChatColor.RED}Usage: @team <message>")
            return
        }

        val senderTeam = manager.getPlayerTeam(player.uniqueId)
        if (senderTeam == null) {
            player.sendMessage("${ChatColor.RED}You're not on a team.")
            return
        }

        val prefix = plugin.config.getString("teams.chat.prefix", "[TEAM] ") ?: "[TEAM] "
        val formatted = "$prefix${senderTeam.displayName}${ChatColor.RESET} ${player.name}: $teamMessage"

        // Send to all team members
        manager.getTeamMembers(senderTeam.id).forEach { uuid ->
            plugin.server.getPlayer(uuid)?.sendMessage(formatted)
        }
    }
}
