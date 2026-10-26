package com.tyler.ggvsgoons

import com.tyler.ggvsgoons.admin.AdminConfigManager
import com.tyler.ggvsgoons.admin.AdminConfigModule
import com.tyler.ggvsgoons.admin.AuditLogger
import com.tyler.ggvsgoons.admin.ConfigValidator
import com.tyler.ggvsgoons.factions.Faction
import com.tyler.ggvsgoons.factions.FactionManager
import com.tyler.ggvsgoons.factions.FactionsModule
import com.tyler.ggvsgoons.persistence.TeamPersistence
import com.tyler.ggvsgoons.teams.TeamManager
import com.tyler.ggvsgoons.teams.TeamsModule
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.scheduler.BukkitScheduler
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import java.nio.file.Path
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegressionLifecycleTest {
    @TempDir lateinit var directory: Path
    private val logger = Logger.getLogger(RegressionLifecycleTest::class.java.name)

    private fun plugin(config: YamlConfiguration): GGvGPlugin = Mockito.mock(GGvGPlugin::class.java).also {
        Mockito.`when`(it.config).thenReturn(config)
        Mockito.`when`(it.dataFolder).thenReturn(directory.toFile())
        Mockito.`when`(it.logger).thenReturn(logger)
    }

    @Test
    fun `disabled factions load and save across a full manager restart`() {
        val leader = UUID.randomUUID()
        val member = UUID.randomUUID()
        val config = YamlConfiguration()
        config.set("factions.enabled", false)
        config.set("factions.enable-persistence", true)
        val first = plugin(config)
        val firstTeams = TeamManager(first)
        TeamPersistence(first, firstTeams).apply {
            load()
            firstTeams.setPlayerTeamSilently(leader, "gg")
            firstTeams.setPlayerTeamSilently(member, "gg")
            save()
        }
        val faction = Faction("gg_alpha", "Alpha", "gg", leader, mutableSetOf(leader, member))
        com.tyler.ggvsgoons.persistence.FactionPersistence.saveFactions(
            listOf(faction), directory.resolve("factions.yml").toFile(), logger
        )

        val restarted = plugin(config)
        val teamsModule = Mockito.mock(TeamsModule::class.java)
        val restoredTeams = TeamManager(restarted)
        Mockito.`when`(restarted.teams).thenReturn(teamsModule)
        Mockito.`when`(teamsModule.manager).thenReturn(restoredTeams)
        TeamPersistence(restarted, restoredTeams).load()
        val factionsModule = FactionsModule(restarted)
        val scheduler = Mockito.mock(BukkitScheduler::class.java)
        Mockito.mockStatic(Bukkit::class.java).use { bukkit ->
            bukkit.`when`<BukkitScheduler> { Bukkit.getScheduler() }.thenReturn(scheduler)
            factionsModule.register(restarted)
            assertTrue(factionsModule.isRegistered)
            assertEquals(faction.id, factionsModule.manager.getFactionByPlayer(member)?.id)
            assertFalse(config.getBoolean("factions.enabled"))
            factionsModule.onDisable()
        }
        val nextRestart = com.tyler.ggvsgoons.persistence.FactionPersistence.loadFactions(
            directory.resolve("factions.yml").toFile(), logger
        )
        assertEquals(faction.members, nextRestart.single().members)
        assertEquals("gg", restoredTeams.getPlayerTeam(member)?.id)
    }

    @Test
    fun `faction toggle and config reload report actual states and refresh the manager`() {
        val config = YamlConfiguration()
        config.set("factions.enabled", true)
        val plugin = plugin(config)
        val factions = Mockito.mock(FactionsModule::class.java)
        val factionManager = Mockito.mock(FactionManager::class.java)
        Mockito.`when`(plugin.factions).thenReturn(factions)
        Mockito.`when`(factions.isRegistered).thenReturn(true)
        Mockito.`when`(factions.manager).thenReturn(factionManager)
        val adminModule = Mockito.mock(AdminConfigModule::class.java)
        Mockito.`when`(plugin.admin).thenReturn(adminModule)
        Mockito.`when`(adminModule.isRegistered).thenReturn(true)
        val manager = AdminConfigManager(plugin, ConfigValidator(), Mockito.mock(AuditLogger::class.java))
        val adminId = UUID.randomUUID()
        assertTrue(manager.setConfigValue("factions.enabled", false, adminId, "Admin").isSuccess)
        assertFalse(manager.listModules().getValue("factions").enabled)
        assertEquals(false, manager.getConfigValue("factions.enabled"))

        config.set("factions.enabled", true) // File content changed by an external editor before reload.
        assertTrue(manager.reloadConfig().isSuccess)
        assertTrue(manager.listModules().getValue("factions").enabled)
        assertEquals(true, manager.getConfigValue("factions.enabled"))
        Mockito.verify(factionManager, Mockito.times(2)).loadConfig()
    }
}
