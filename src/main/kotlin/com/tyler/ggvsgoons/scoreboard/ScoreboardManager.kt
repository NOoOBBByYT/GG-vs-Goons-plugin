package com.tyler.ggvsgoons.scoreboard

import com.tyler.ggvsgoons.GGvGPlugin
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Coordinates per-player scoreboards and the tab list renderer. */
class ScoreboardManager(private val plugin: GGvGPlugin) {
    private val scoreboards = ConcurrentHashMap<UUID, Scoreboard>()
    private val objectives = ConcurrentHashMap<UUID, Objective>()
    private val previousBoards = mutableMapOf<UUID, Scoreboard>()
    private var taskId = -1
    private var config = ScoreboardConfigLoader.load(plugin)
    private var tabConfig = ScoreboardConfigLoader.loadTabList(plugin)
    val renderer = ScoreboardRenderer(plugin)
    private val disabledPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val tabRenderer = TabListRenderer(plugin)

    fun start() {
        stop()
        config = ScoreboardConfigLoader.load(plugin)
        tabConfig = ScoreboardConfigLoader.loadTabList(plugin)
        if (config.enabled || tabConfig.enabled) {
            Bukkit.getOnlinePlayers().forEach { setupPlayer(it) }
            val interval = config.updateInterval.coerceAtLeast(1).toLong()
            taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
                refresh()
            }, 1L, interval)
        }
    }

    fun stop() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId)
        taskId = -1
        Bukkit.getOnlinePlayers().forEach { player ->
            if (player.scoreboard === scoreboards[player.uniqueId]) {
                previousBoards[player.uniqueId]?.let { player.scoreboard = it }
            }
        }
        scoreboards.clear()
        objectives.clear()
        previousBoards.clear()
    }

    fun reload() {
        stop()
        start()
    }

    fun isPersonalScoreboardEnabled(player: Player): Boolean = player.uniqueId !in disabledPlayers

    fun togglePersonalScoreboard(player: Player): Boolean {
        if (!disabledPlayers.add(player.uniqueId)) {
            disabledPlayers.remove(player.uniqueId)
        } else {
            if (player.scoreboard === scoreboards[player.uniqueId]) {
                previousBoards[player.uniqueId]?.let { player.scoreboard = it }
            }
        }
        update(player)
        return player.uniqueId !in disabledPlayers
    }

    fun setupPlayer(player: Player) {
        if (config.enabled && player.uniqueId !in disabledPlayers) {
            val board = Bukkit.getScoreboardManager()?.newScoreboard ?: return
            previousBoards.putIfAbsent(player.uniqueId, player.scoreboard)
            plugin.teams.manager.mirrorTeamsTo(board)
            val objective = board.registerNewObjective("ggvg", "dummy", config.title)
            objective.displaySlot = DisplaySlot.SIDEBAR
            scoreboards[player.uniqueId] = board
            objectives[player.uniqueId] = objective
            player.scoreboard = board
        }
        update(player)
    }

    fun removePlayer(player: Player) {
        scoreboards.remove(player.uniqueId)
        objectives.remove(player.uniqueId)
        previousBoards.remove(player.uniqueId)
    }

    fun syncTeams() = scoreboards.values.forEach { plugin.teams.manager.mirrorTeamsTo(it) }

    private fun refresh() {
        val online = Bukkit.getOnlinePlayers().toList()
        online.forEach { update(it) }
        if (tabConfig.enabled) tabRenderer.renderNames(online, tabConfig)
    }

    fun forceUpdateAll() = refresh()

    fun update(player: Player) {
        if (config.enabled && player.uniqueId !in disabledPlayers) {
            val board = scoreboards[player.uniqueId] ?: run {
                setupPlayer(player)
                scoreboards[player.uniqueId] ?: return
            }
            if (!shouldShowScoreboard(player)) {
                if (player.scoreboard === board) previousBoards[player.uniqueId]?.let { player.scoreboard = it }
            } else {
                val objective = objectives[player.uniqueId] ?: return
                renderer.render(player, objective)
                if (player.scoreboard !== board && player.scoreboard === previousBoards[player.uniqueId]) player.scoreboard = board
            }
        }
        if (tabConfig.enabled) tabRenderer.renderHeader(player, tabConfig)
    }

    private fun shouldShowScoreboard(player: Player): Boolean {
        val worlds = config.enabledWorlds
        if (worlds.isNotEmpty() && player.world.name !in worlds) return false
        if (config.hideWhenPrisoner && plugin.warPrisoner.manager.isPrisoner(player.uniqueId)) return false
        if (config.hideInCombat && renderer.isInCombat(player.uniqueId)) return false
        return true
    }
}

object ScoreboardConfigLoader {
    fun load(plugin: GGvGPlugin): ScoreboardConfig {
        val c = plugin.config
        val elements = ScoreboardElements(
            c.getBoolean("scoreboard.elements.server-name", true),
            c.getBoolean("scoreboard.elements.team", true),
            c.getBoolean("scoreboard.elements.faction", true),
            c.getBoolean("scoreboard.elements.kills", true),
            c.getBoolean("scoreboard.elements.deaths", true),
            c.getBoolean("scoreboard.elements.kdr", true),
            c.getBoolean("scoreboard.elements.prisoners-captured", true),
            c.getBoolean("scoreboard.elements.player-status", true),
            c.getBoolean("scoreboard.elements.online-players", true),
            c.getBoolean("scoreboard.elements.team-online", true),
            c.getBoolean("scoreboard.elements.team-prisoners", true),
            c.getBoolean("scoreboard.elements.tps", false),
            c.getBoolean("scoreboard.elements.current-time", false)
        )
        val labels = ScoreboardLabels(
            colorize(c.getString("scoreboard.labels.team", "&eTeam:")!!),
            colorize(c.getString("scoreboard.labels.faction", "&eFaction:")!!),
            colorize(c.getString("scoreboard.labels.kills", "&eKills:")!!),
            colorize(c.getString("scoreboard.labels.deaths", "&eDeaths:")!!),
            colorize(c.getString("scoreboard.labels.kdr", "&eK/D:")!!),
            colorize(c.getString("scoreboard.labels.prisoners", "&ePrisoners:")!!),
            colorize(c.getString("scoreboard.labels.status", "&eStatus:")!!),
            colorize(c.getString("scoreboard.labels.online", "&eOnline:")!!),
            colorize(c.getString("scoreboard.labels.team-online", "&eTeam Online:")!!),
            colorize(c.getString("scoreboard.labels.team-prisoners", "&eTeam Prisoners:")!!)
        )
        fun color(path: String, fallback: ChatColor) = parseColor(c.getString(path, fallback.toString()), fallback)
        return ScoreboardConfig(
            c.getBoolean("scoreboard.enabled", true),
            colorize(c.getString("scoreboard.title", "&6&lGG vs Goons")!!),
            c.getInt("scoreboard.update-interval", 20).coerceAtLeast(1),
            elements,
            labels,
            ScoreboardColors(
                color("scoreboard.colors.title", ChatColor.GOLD), color("scoreboard.colors.labels", ChatColor.YELLOW),
                color("scoreboard.colors.values", ChatColor.WHITE), color("scoreboard.colors.team-gg", ChatColor.GOLD),
                color("scoreboard.colors.team-goons", ChatColor.DARK_BLUE), color("scoreboard.colors.positive", ChatColor.GREEN),
                color("scoreboard.colors.negative", ChatColor.RED), color("scoreboard.colors.neutral", ChatColor.GRAY)
            ),
            c.getStringList("scoreboard.conditions.enabled-worlds"),
            c.getBoolean("scoreboard.conditions.hide-in-combat", false),
            c.getBoolean("scoreboard.conditions.hide-when-prisoner", false)
        )
    }

    fun loadTabList(plugin: GGvGPlugin): TabListConfig {
        val c = plugin.config
        val teamHeaders = mapOf("gg" to c.getString("tablist.team-headers.gg", "&6&l▬▬▬▬ GG Team ▬▬▬▬")!!,
            "goons" to c.getString("tablist.team-headers.goons", "&1&l▬▬▬▬ Goons Team ▬▬▬▬")!!,
            "none" to c.getString("tablist.team-headers.none", "&7&l▬▬▬▬ No Team ▬▬▬▬")!!)
        val indicators = c.getConfigurationSection("tablist.player-format.status-indicators")?.getKeys(false)?.associateWith {
            c.getString("tablist.player-format.status-indicators.$it", "")!!
        } ?: emptyMap()
        val teamColors = mapOf("gg" to parseColor(c.getString("tablist.player-format.team-colors.gg", "&6"), ChatColor.GOLD),
            "goons" to parseColor(c.getString("tablist.player-format.team-colors.goons", "&1"), ChatColor.DARK_BLUE),
            "none" to parseColor(c.getString("tablist.player-format.team-colors.none", "&7"), ChatColor.GRAY))
        val pingColors = mapOf("excellent" to parseColor(c.getString("tablist.ping.colors.excellent", "&a"), ChatColor.GREEN),
            "good" to parseColor(c.getString("tablist.ping.colors.good", "&e"), ChatColor.YELLOW),
            "fair" to parseColor(c.getString("tablist.ping.colors.fair", "&6"), ChatColor.GOLD),
            "poor" to parseColor(c.getString("tablist.ping.colors.poor", "&c"), ChatColor.RED))
        return TabListConfig(
            c.getBoolean("tablist.enabled", true), c.getBoolean("tablist.separate-by-team", true),
            colorize(c.getString("tablist.header", "")!!), colorize(c.getString("tablist.footer", "")!!), teamHeaders.mapValues { colorize(it.value) },
            PlayerFormat(c.getBoolean("tablist.player-format.show-faction-tags", true), c.getString("tablist.player-format.faction-tag-format", "&7[{faction}] ")!!,
                c.getBoolean("tablist.player-format.show-status-indicators", true), indicators.mapValues { colorize(it.value) }, teamColors),
            PingConfig(c.getBoolean("tablist.ping.show-ping", true), c.getString("tablist.ping.format", " &7({ping}ms)")!!, pingColors)
        )
    }

    private fun colorize(value: String): String = ChatColor.translateAlternateColorCodes('&', value)
    private fun parseColor(value: String?, fallback: ChatColor): ChatColor = runCatching {
        ChatColor.getByChar(ChatColor.translateAlternateColorCodes('&', value ?: "&7").replace("§", "").firstOrNull() ?: '7') ?: fallback
    }.getOrDefault(fallback)
}
