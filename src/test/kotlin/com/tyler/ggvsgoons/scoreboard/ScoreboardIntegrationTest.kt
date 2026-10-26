package com.tyler.ggvsgoons.scoreboard

import com.tyler.ggvsgoons.GGvGPlugin
import com.tyler.ggvsgoons.commands.PrisonerManager
import com.tyler.ggvsgoons.commands.WarPrisonerModule
import com.tyler.ggvsgoons.teams.TeamManager
import com.tyler.ggvsgoons.teams.TeamsModule
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.ScoreboardManager as BukkitScoreboardManager
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Team
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScoreboardIntegrationTest {
    private val plugin = Mockito.mock(GGvGPlugin::class.java)

    @Test
    fun `personal scoreboard mirrors main team membership and removes stale entries`() {
        val manager = TeamManager(plugin)
        val bukkitManager = Mockito.mock(BukkitScoreboardManager::class.java)
        val main = Mockito.mock(Scoreboard::class.java)
        val personal = Mockito.mock(Scoreboard::class.java)
        val source = Mockito.mock(Team::class.java)
        val target = Mockito.mock(Team::class.java)
        val entries = mutableSetOf("Old")
        Mockito.`when`(bukkitManager.mainScoreboard).thenReturn(main)
        Mockito.`when`(main.getTeam("GG")).thenReturn(source)
        Mockito.`when`(personal.getTeam("GG")).thenReturn(target)
        Mockito.`when`(source.entries).thenReturn(setOf("Member"))
        Mockito.`when`(source.color).thenReturn(ChatColor.GOLD)
        Mockito.`when`(source.getOption(Team.Option.NAME_TAG_VISIBILITY)).thenReturn(Team.OptionStatus.ALWAYS)
        Mockito.`when`(target.entries).thenAnswer { entries.toSet() }
        Mockito.doAnswer { call -> entries.remove(call.getArgument<String>(0)); true }
            .`when`(target).removeEntry(Mockito.anyString())
        Mockito.doAnswer { call -> entries.add(call.getArgument<String>(0)); true }
            .`when`(target).addEntry(Mockito.anyString())

        Mockito.mockStatic(Bukkit::class.java).use { bukkit ->
            bukkit.`when`<BukkitScoreboardManager> { Bukkit.getScoreboardManager() }.thenReturn(bukkitManager)
            manager.mirrorTeamsTo(personal)
        }
        assertEquals(setOf("Member"), entries)
        Mockito.verify(target).color = ChatColor.GOLD
        Mockito.verify(target).setAllowFriendlyFire(false)
    }

    @Test
    fun `tab names are only updated when changed and only once per batch`() {
        val settings = YamlConfiguration()
        settings.set("factions.enabled", false)
        Mockito.`when`(plugin.config).thenReturn(settings)
        val teamsModule = Mockito.mock(TeamsModule::class.java)
        val teams = Mockito.mock(TeamManager::class.java)
        Mockito.`when`(plugin.teams).thenReturn(teamsModule)
        Mockito.`when`(teamsModule.manager).thenReturn(teams)
        val player = Mockito.mock(Player::class.java)
        Mockito.`when`(player.uniqueId).thenReturn(UUID.randomUUID())
        Mockito.`when`(player.name).thenReturn("Member")
        var displayed: String? = null
        Mockito.`when`(player.playerListName).thenAnswer { displayed }
        Mockito.doAnswer { call -> displayed = call.getArgument<String>(0); null }
            .`when`(player).setPlayerListName(Mockito.anyString())
        val config = TabListConfig(
            enabled = true, separateByTeam = false, header = "", footer = "", teamHeaders = emptyMap(),
            playerFormat = PlayerFormat(false, "", false, emptyMap(), mapOf("none" to ChatColor.GRAY)),
            ping = PingConfig(false, "", emptyMap())
        )
        val renderer = TabListRenderer(plugin)
        renderer.renderNames(listOf(player), config)
        renderer.renderNames(listOf(player), config)
        Mockito.verify(player, Mockito.times(1)).setPlayerListName(Mockito.anyString())
        assertTrue(displayed!!.contains("Member"))
    }

    @Test
    fun `hidden sidebar returns player to their previous scoreboard`() {
        val settings = YamlConfiguration()
        settings.set("scoreboard.enabled", true)
        settings.set("scoreboard.conditions.hide-when-prisoner", true)
        settings.set("tablist.enabled", false)
        Mockito.`when`(plugin.config).thenReturn(settings)
        val teamsModule = Mockito.mock(TeamsModule::class.java)
        val teamManager = Mockito.mock(TeamManager::class.java)
        Mockito.`when`(plugin.teams).thenReturn(teamsModule)
        Mockito.`when`(teamsModule.manager).thenReturn(teamManager)
        val prisonerModule = Mockito.mock(WarPrisonerModule::class.java)
        val prisonerManager = Mockito.mock(PrisonerManager::class.java)
        Mockito.`when`(plugin.warPrisoner).thenReturn(prisonerModule)
        Mockito.`when`(prisonerModule.manager).thenReturn(prisonerManager)

        val viewer = Mockito.mock(Player::class.java)
        val playerId = UUID.randomUUID()
        Mockito.`when`(viewer.uniqueId).thenReturn(playerId)
        Mockito.`when`(prisonerManager.isPrisoner(playerId)).thenReturn(true)
        val original = Mockito.mock(Scoreboard::class.java)
        val personal = Mockito.mock(Scoreboard::class.java)
        val objective = Mockito.mock(Objective::class.java)
        val bukkitManager = Mockito.mock(BukkitScoreboardManager::class.java)
        Mockito.`when`(bukkitManager.newScoreboard).thenReturn(personal)
        Mockito.`when`(personal.registerNewObjective(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
            .thenReturn(objective)
        var visible: Scoreboard = original
        Mockito.`when`(viewer.scoreboard).thenAnswer { visible }
        Mockito.doAnswer { call -> visible = call.getArgument(0); null }
            .`when`(viewer).scoreboard = Mockito.any(Scoreboard::class.java)
        val manager = ScoreboardManager(plugin)
        Mockito.mockStatic(Bukkit::class.java).use { bukkit ->
            bukkit.`when`<BukkitScoreboardManager> { Bukkit.getScoreboardManager() }.thenReturn(bukkitManager)
            bukkit.`when`<MutableCollection<out Player>> { Bukkit.getOnlinePlayers() }.thenReturn(mutableListOf(viewer))
            manager.setupPlayer(viewer)
            assertTrue(visible === original)
            Mockito.verify(teamManager).mirrorTeamsTo(personal)
            manager.stop()
            assertTrue(visible === original)
        }
    }
}
