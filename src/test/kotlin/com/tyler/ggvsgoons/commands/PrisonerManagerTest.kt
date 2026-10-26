package com.tyler.ggvsgoons.commands

import com.tyler.ggvsgoons.GGvGPlugin
import com.tyler.ggvsgoons.stats.PlayerStatsManager
import com.tyler.ggvsgoons.stats.StatsModule
import com.tyler.ggvsgoons.teams.TeamManager
import com.tyler.ggvsgoons.teams.TeamsModule
import org.bukkit.GameMode
import org.bukkit.Server
import org.bukkit.entity.Player
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrisonerManagerTest {
    private val plugin = Mockito.mock(GGvGPlugin::class.java)
    private val server = Mockito.mock(Server::class.java)
    private val teams = Mockito.mock(TeamsModule::class.java)
    private val teamManager = Mockito.mock(TeamManager::class.java)
    private val captor = Mockito.mock(Player::class.java)
    private val target = Mockito.mock(Player::class.java)
    private val captorId = UUID.randomUUID()
    private val targetId = UUID.randomUUID()
    private val manager = PrisonerManager(plugin, 0)

    @BeforeEach
    fun setUp() {
        Mockito.`when`(plugin.server).thenReturn(server)
        Mockito.`when`(plugin.teams).thenReturn(teams)
        Mockito.`when`(teams.manager).thenReturn(teamManager)
        Mockito.`when`(captor.uniqueId).thenReturn(captorId)
        Mockito.`when`(target.uniqueId).thenReturn(targetId)
        Mockito.`when`(captor.isOnline).thenReturn(true)
        Mockito.`when`(target.isOnline).thenReturn(true)
        Mockito.`when`(server.getPlayer(captorId)).thenReturn(captor)
        Mockito.`when`(teamManager.areOpposingTeams(captorId, targetId)).thenReturn(true)
    }

    @Test
    fun `accepting after teams change does not capture the target`() {
        assertTrue(manager.createOffer(captor, target))
        Mockito.`when`(teamManager.areOpposingTeams(captorId, targetId)).thenReturn(false)
        assertNull(manager.acceptOffer(target, captorId))
        assertFalse(manager.isPrisoner(targetId))
        assertFalse(manager.hasPendingOffer(targetId))
    }

    @Test
    fun `accepting after the captor disconnects clears the offer`() {
        assertTrue(manager.createOffer(captor, target))
        Mockito.`when`(captor.isOnline).thenReturn(false)
        assertNull(manager.acceptOffer(target, captorId))
        assertFalse(manager.hasPendingOffer(targetId))
        assertFalse(manager.isPrisoner(targetId))
    }

    @Test
    fun `accepting a valid offer retains original gamemode and release restores it`() {
        val stats = Mockito.mock(StatsModule::class.java)
        val statsManager = Mockito.mock(PlayerStatsManager::class.java)
        Mockito.`when`(plugin.stats).thenReturn(stats)
        Mockito.`when`(stats.manager).thenReturn(statsManager)
        Mockito.`when`(target.gameMode).thenReturn(GameMode.CREATIVE, GameMode.ADVENTURE, GameMode.CREATIVE)
        Mockito.`when`(server.getPlayer(targetId)).thenReturn(target)
        assertTrue(manager.createOffer(captor, target))
        assertTrue(manager.acceptOffer(target, captorId) === captor)
        assertTrue(manager.isPrisoner(targetId))
        Mockito.verify(target).setGameMode(GameMode.ADVENTURE)
        assertTrue(manager.releasePrisoner(targetId))
        Mockito.verify(target).setGameMode(GameMode.CREATIVE)
        assertFalse(manager.isPrisoner(targetId))
    }

    @Test
    fun `offline release preserves custody until gamemode can be restored`() {
        manager.loadPrisoners(mapOf(targetId to Prisoner(captorId, GameMode.SURVIVAL)))
        assertFalse(manager.releasePrisoner(targetId))
        assertTrue(manager.isPrisoner(targetId))
    }

    @Test
    fun `failed gamemode switch does not mark a player captured`() {
        Mockito.`when`(target.gameMode).thenReturn(GameMode.SURVIVAL)
        assertTrue(manager.createOffer(captor, target))
        assertNull(manager.acceptOffer(target, captorId))
        assertFalse(manager.isPrisoner(targetId))
    }

    @Test
    fun `failed restoration keeps prisoner tracked for retry`() {
        manager.loadPrisoners(mapOf(targetId to Prisoner(captorId, GameMode.CREATIVE)))
        Mockito.`when`(server.getPlayer(targetId)).thenReturn(target)
        Mockito.`when`(target.gameMode).thenReturn(GameMode.ADVENTURE)
        assertFalse(manager.releasePrisoner(targetId))
        assertTrue(manager.isPrisoner(targetId))
    }

    @Test
    fun `joining prisoner is put in adventure mode and offers are cleared on quit`() {
        manager.loadPrisoners(mapOf(targetId to Prisoner(captorId, GameMode.SURVIVAL)))
        manager.enforcePrisonerState(target)
        Mockito.verify(target).setGameMode(GameMode.ADVENTURE)
        val anotherTarget = Mockito.mock(Player::class.java)
        val anotherId = UUID.randomUUID()
        Mockito.`when`(anotherTarget.uniqueId).thenReturn(anotherId)
        Mockito.`when`(teamManager.areOpposingTeams(captorId, anotherId)).thenReturn(true)
        assertTrue(manager.createOffer(captor, anotherTarget))
        manager.clearOffersForPlayer(captorId)
        assertFalse(manager.hasPendingOffer(anotherId))
    }
}
