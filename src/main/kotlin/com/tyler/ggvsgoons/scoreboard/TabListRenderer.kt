package com.tyler.ggvsgoons.scoreboard

import com.tyler.ggvsgoons.GGvGPlugin
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player

class TabListRenderer(private val plugin: GGvGPlugin) {
    fun renderHeader(viewer: Player, config: TabListConfig) {
        val teamSection = if (config.separateByTeam) {
            "${config.teamHeaders["gg"] ?: ""}\n${config.teamHeaders["goons"] ?: ""}\n${config.teamHeaders["none"] ?: ""}\n"
        } else ""
        val header = teamSection + replace(config.header, viewer)
        val footer = replace(config.footer, viewer)
        if (viewer.playerListHeader != header || viewer.playerListFooter != footer) {
            viewer.setPlayerListHeaderFooter(header, footer)
        }
    }

    fun renderNames(online: List<Player>, config: TabListConfig) {
        online.forEach { p ->
            val formatted = formatPlayer(p, config)
            if (p.playerListName != formatted) p.setPlayerListName(formatted)
        }
    }

    private fun formatPlayer(player: Player, config: TabListConfig): String {
        val teamId = teamId(player)
        val color = config.playerFormat.teamColors[teamId] ?: ChatColor.GRAY
        val faction = factionFor(player)
        var result = color.toString()
        if (config.playerFormat.showStatusIndicators) {
            val indicator = when {
                plugin.warPrisoner.manager.isPrisoner(player.uniqueId) -> config.playerFormat.statusIndicators["prisoner"]
                runCatching { plugin.admin.moderationManager.isPlayerFrozen(player.uniqueId) }.getOrDefault(false) -> config.playerFormat.statusIndicators["frozen"]
                faction?.isLeader(player.uniqueId) == true -> config.playerFormat.statusIndicators["leader"]
                faction?.isOfficer(player.uniqueId) == true -> config.playerFormat.statusIndicators["officer"]
                else -> ""
            }
            result += indicator ?: ""
        }
        if (config.playerFormat.showFactionTags && faction != null) {
            result += ChatColor.translateAlternateColorCodes('&', config.playerFormat.factionTagFormat).replace("{faction}", faction.name)
        }
        result += player.name
        if (config.ping.showPing) {
            val ping = player.ping.coerceAtLeast(0)
            val pingColor = when {
                ping <= 50 -> config.ping.colors["excellent"]
                ping <= 100 -> config.ping.colors["good"]
                ping <= 200 -> config.ping.colors["fair"]
                else -> config.ping.colors["poor"]
            } ?: ChatColor.GRAY
            result += pingColor.toString() + ChatColor.translateAlternateColorCodes('&', config.ping.format).replace("{ping}", ping.toString())
        }
        return result.take(80)
    }

    private fun replace(text: String, player: Player): String {
        val team = plugin.teams.manager.getPlayerTeam(player.uniqueId)
        val faction = factionFor(player)
        val stats = plugin.stats.manager.getStats(player)
        return ChatColor.translateAlternateColorCodes('&', text)
            .replace("{player}", player.name)
            .replace("{team}", team?.label ?: "None")
            .replace("{faction}", faction?.name ?: "None")
            .replace("{kills}", stats.kills.toString())
            .replace("{deaths}", stats.deaths.toString())
            .replace("{kdr}", "%.2f".format(stats.getKDR()))
            .replace("{prisoners}", stats.prisonersCaptured.toString())
            .replace("{online}", Bukkit.getOnlinePlayers().size.toString())
            .replace("{max}", Bukkit.getMaxPlayers().toString())
            .replace("{tps}", "%.1f".format(Bukkit.getTPS().firstOrNull() ?: 20.0))
            .replace("{time}", java.time.LocalTime.now().withNano(0).toString())
            .replace("{team_online}", team?.let { plugin.teams.manager.getTeamMembers(it.id).count { id -> Bukkit.getPlayer(id) != null }.toString() } ?: "0")
            .replace("{status}", if (plugin.warPrisoner.manager.isPrisoner(player.uniqueId)) "Prisoner" else "Active")
    }

    private fun teamId(p: Player) = plugin.teams.manager.getPlayerTeam(p.uniqueId)?.id ?: "none"
    private fun factionFor(p: Player) = if (plugin.config.getBoolean("factions.enabled", true) && plugin.factions.isRegistered)
        plugin.factions.manager.getFactionByPlayer(p.uniqueId) else null
}
