package com.tyler.ggvsgoons.scoreboard

import com.tyler.ggvsgoons.GGvGPlugin
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.entity.Entity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scoreboard.Objective
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ScoreboardRenderer(private val plugin: GGvGPlugin) : Listener {
    private val combatUntil = ConcurrentHashMap<UUID, Long>()

    fun render(player: Player, objective: Objective) {
        val config = ScoreboardConfigLoader.load(plugin)
        objective.displayName = config.title
        // Remove previous entries without recreating the Objective (the manager keeps its reference).
        objective.scoreboard?.entries?.toList()?.forEach { entry -> objective.scoreboard?.resetScores(entry) }

        val lines = mutableListOf<String>()
        val team = plugin.teams.manager.getPlayerTeam(player.uniqueId)
        val faction = if (plugin.config.getBoolean("factions.enabled", true) && plugin.factions.isRegistered)
            plugin.factions.manager.getFactionByPlayer(player.uniqueId) else null
        val stats = plugin.stats.manager.getStats(player)
        val status = when {
            plugin.warPrisoner.manager.isPrisoner(player.uniqueId) -> "Prisoner"
            runCatching { plugin.admin.moderationManager.isPlayerFrozen(player.uniqueId) }.getOrDefault(false) -> "Frozen"
            else -> "Active"
        }
        fun add(label: String, value: String, valueColor: String = config.colors.values.toString()) {
            lines += "${config.colors.labels}$label ${valueColor}$value"
        }
        if (config.elements.serverName) lines += "${config.colors.neutral}${org.bukkit.Bukkit.getServer().name}"
        if (config.elements.team) add(config.labels.team, team?.label ?: "None", if (team?.id == "gg") config.colors.teamGG.toString() else if (team?.id == "goons") config.colors.teamGoons.toString() else config.colors.neutral.toString())
        if (config.elements.faction) add(config.labels.faction, faction?.name ?: "None")
        if (config.elements.kills) add(config.labels.kills, stats.kills.toString(), config.colors.positive.toString())
        if (config.elements.deaths) add(config.labels.deaths, stats.deaths.toString(), config.colors.negative.toString())
        if (config.elements.kdr) add(config.labels.kdr, "%.2f".format(stats.getKDR()))
        if (config.elements.prisonersCaptured) add(config.labels.prisoners, stats.prisonersCaptured.toString())
        if (config.elements.playerStatus) add(config.labels.status, status)
        if (config.elements.teamOnline) add(config.labels.teamOnline, team?.let { plugin.teams.manager.getTeamMembers(it.id).count { id -> org.bukkit.Bukkit.getPlayer(id) != null } }?.toString() ?: "0")
        if (config.elements.teamPrisoners) {
            val teamPrisoners = plugin.warPrisoner.manager.getPrisoners().values.count { prisoner ->
                plugin.teams.manager.getPlayerTeam(prisoner.captorId)?.id == team?.id
            }
            add(config.labels.teamPrisoners, teamPrisoners.toString())
        }
        if (config.elements.onlinePlayers) add(config.labels.online, "${org.bukkit.Bukkit.getOnlinePlayers().size}/${org.bukkit.Bukkit.getMaxPlayers()}")
        if (config.elements.tps) add("TPS:", "%.1f".format(org.bukkit.Bukkit.getTPS().firstOrNull() ?: 20.0))
        if (config.elements.currentTime) add("Time:", java.time.LocalTime.now().withNano(0).toString())

        lines.take(15).forEachIndexed { index, line ->
            val suffix = ChatColor.COLOR_CHAR.toString() + "${index.toString(16)}"
            val maxLen = (40 - suffix.length).coerceAtLeast(1)
            objective.getScore(line.take(maxLen) + suffix).score = 15 - index
        }
    }

    fun isInCombat(playerId: UUID): Boolean = (combatUntil[playerId] ?: 0L) > System.currentTimeMillis()

    @EventHandler
    fun onDamage(event: EntityDamageByEntityEvent) {
        val now = System.currentTimeMillis() + 10_000L
        (event.entity as? Player)?.let { combatUntil[it.uniqueId] = now }
        (event.damager as? Player)?.let { combatUntil[it.uniqueId] = now }
    }
}
