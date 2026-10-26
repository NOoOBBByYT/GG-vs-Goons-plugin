package com.tyler.ggvsgoons.scoreboard

import org.bukkit.ChatColor

/**
 * Configuration for the scoreboard system
 */
data class ScoreboardConfig(
    val enabled: Boolean,
    val title: String,
    val updateInterval: Int,
    val elements: ScoreboardElements,
    val labels: ScoreboardLabels,
    val colors: ScoreboardColors,
    val enabledWorlds: List<String> = emptyList(),
    val hideInCombat: Boolean = false,
    val hideWhenPrisoner: Boolean = false
)

/**
 * Which elements to show on the scoreboard
 */
data class ScoreboardElements(
    val serverName: Boolean,
    val team: Boolean,
    val faction: Boolean,
    val kills: Boolean,
    val deaths: Boolean,
    val kdr: Boolean,
    val prisonersCaptured: Boolean,
    val playerStatus: Boolean,
    val onlinePlayers: Boolean,
    val teamOnline: Boolean,
    val teamPrisoners: Boolean,
    val tps: Boolean,
    val currentTime: Boolean
)

/**
 * Custom labels for scoreboard elements
 */
data class ScoreboardLabels(
    val team: String,
    val faction: String,
    val kills: String,
    val deaths: String,
    val kdr: String,
    val prisoners: String,
    val status: String,
    val online: String,
    val teamOnline: String,
    val teamPrisoners: String
)

/**
 * Colors for different scoreboard elements
 */
data class ScoreboardColors(
    val title: ChatColor,
    val labels: ChatColor,
    val values: ChatColor,
    val teamGG: ChatColor,
    val teamGoons: ChatColor,
    val positive: ChatColor,
    val negative: ChatColor,
    val neutral: ChatColor
)

/**
 * Configuration for the tab list system
 */
data class TabListConfig(
    val enabled: Boolean,
    val separateByTeam: Boolean,
    val header: String,
    val footer: String,
    val teamHeaders: Map<String, String>,
    val playerFormat: PlayerFormat,
    val ping: PingConfig
)

/**
 * Player name format configuration
 */
data class PlayerFormat(
    val showFactionTags: Boolean,
    val factionTagFormat: String,
    val showStatusIndicators: Boolean,
    val statusIndicators: Map<String, String>,
    val teamColors: Map<String, ChatColor>
)

/**
 * Ping display configuration
 */
data class PingConfig(
    val showPing: Boolean,
    val format: String,
    val colors: Map<String, ChatColor>
)
