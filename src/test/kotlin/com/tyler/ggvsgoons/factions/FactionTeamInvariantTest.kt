package com.tyler.ggvsgoons.factions

import com.tyler.ggvsgoons.GGvGPlugin
import com.tyler.ggvsgoons.admin.AuditLogger
import com.tyler.ggvsgoons.admin.ModerationManager
import com.tyler.ggvsgoons.teams.TeamManager
import com.tyler.ggvsgoons.teams.TeamsModule
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.Server
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitScheduler
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FactionTeamInvariantTest {
    private val plugin = Mockito.mock(GGvGPlugin::class.java)
    private val server = Mockito.mock(Server::class.java)
    private val teamsModule = Mockito.mock(TeamsModule::class.java)
    private val factionsModule = Mockito.mock(FactionsModule::class.java)
    private val teams = TeamManager(plugin)
    private val factions = FactionManager(plugin)
    private val leader = UUID.randomUUID()
    private val member = UUID.randomUUID()
    private val officer = UUID.randomUUID()
    private val admin = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        Mockito.`when`(plugin.server).thenReturn(server)
        Mockito.`when`(plugin.config).thenReturn(YamlConfiguration())
        Mockito.`when`(plugin.teams).thenReturn(teamsModule)
        Mockito.`when`(teamsModule.manager).thenReturn(teams)
        Mockito.`when`(plugin.factions).thenReturn(factionsModule)
        Mockito.`when`(factionsModule.isRegistered).thenReturn(true)
        Mockito.`when`(factionsModule.manager).thenReturn(factions)
        teams.setPlayerTeamSilently(leader, "gg")
        teams.setPlayerTeamSilently(member, "gg")
        teams.setPlayerTeamSilently(officer, "gg")
    }

    private fun faction(): Faction {
        val faction = factions.createFaction("Example", leader, "gg").getOrThrow()
        faction.members.addAll(listOf(member, officer))
        factions.loadFactions(listOf(faction))
        return faction
    }

    @Test
    fun `switching teams removes member and officer from both faction indexes`() {
        val faction = faction()
        faction.officers.add(member)
        val player = Mockito.mock(Player::class.java)
        Mockito.`when`(player.uniqueId).thenReturn(member)
        Mockito.`when`(player.name).thenReturn("Member")

        Mockito.mockStatic(Bukkit::class.java).use {
            assertTrue(teams.joinTeam(player, "goons"))
        }
        assertEquals("goons", teams.getPlayerTeam(member)?.id)
        assertFalse(faction.isMember(member))
        assertFalse(faction.isOfficer(member))
        assertNull(factions.getFactionByPlayer(member))
    }

    @Test
    fun `leader cannot switch or leave team until transferring leadership`() {
        val faction = faction()
        val player = Mockito.mock(Player::class.java)
        Mockito.`when`(player.uniqueId).thenReturn(leader)

        assertFalse(teams.joinTeam(player, "goons"))
        assertFalse(teams.leaveTeam(player))
        assertEquals("gg", teams.getPlayerTeam(leader)?.id)
        assertEquals(faction.id, factions.getFactionByPlayer(leader)?.id)
        assertTrue(factions.transferLeadership(faction.id, leader, member).isSuccess)
        assertTrue(teams.removePlayerFromTeam(leader).isSuccess)
        assertNull(factions.getFactionByPlayer(leader))
        assertEquals(member, factions.getFaction(faction.id)?.leaderId)
    }

    @Test
    fun `admin removals retain indexes and reject removing leader`() {
        val faction = faction()
        val moderation = ModerationManager(plugin, Mockito.mock(AuditLogger::class.java))
        val offlinePlayer = Mockito.mock(OfflinePlayer::class.java)
        Mockito.mockStatic(Bukkit::class.java).use { bukkit ->
            bukkit.`when`<OfflinePlayer> { Bukkit.getOfflinePlayer(member) }.thenReturn(offlinePlayer)
            bukkit.`when`<OfflinePlayer> { Bukkit.getOfflinePlayer(officer) }.thenReturn(offlinePlayer)
            assertTrue(moderation.kickFromFaction(leader, admin, "Admin").isFailure)
            assertTrue(moderation.kickFromFaction(member, admin, "Admin").isSuccess)
            assertNull(factions.getFactionByPlayer(member))
            assertFalse(faction.isMember(member))
            assertTrue(moderation.kickFromTeam(leader, admin, "Admin").isFailure)
            assertTrue(moderation.kickFromTeam(officer, admin, "Admin").isSuccess)
        }
        assertNull(teams.getPlayerTeam(officer))
        assertNull(factions.getFactionByPlayer(officer))
        assertFalse(faction.isMember(officer))
    }

    @Test
    fun `invite is revalidated when either side changes team or loses invite privilege`() {
        val faction = faction()
        faction.members.remove(officer)
        factions.loadFactions(listOf(faction))
        val scheduler = Mockito.mock(BukkitScheduler::class.java)
        Mockito.mockStatic(Bukkit::class.java).use { bukkit ->
            bukkit.`when`<BukkitScheduler> { Bukkit.getScheduler() }.thenReturn(scheduler)
            assertTrue(factions.invitePlayer(faction.id, leader, officer).isSuccess)
            teams.setPlayerTeamSilently(officer, "goons") // Simulates team changing before acceptance
            assertTrue(factions.acceptInvite(officer, faction.id).isFailure)
            assertNull(factions.getFactionByPlayer(officer))

            teams.setPlayerTeamSilently(officer, "gg")
            assertTrue(factions.invitePlayer(faction.id, leader, officer).isSuccess)
            assertTrue(factions.transferLeadership(faction.id, leader, member).isSuccess)
            assertTrue(teams.removePlayerFromTeam(leader).isSuccess)
            assertTrue(factions.acceptInvite(officer, faction.id).isFailure)
            assertNull(factions.getFactionByPlayer(officer))
        }
    }

    @Test
    fun `loaded faction is rejected if its member belongs to another team`() {
        val faction = faction()
        teams.setPlayerTeamSilently(member, "goons")
        assertTrue(runCatching { factions.loadFactions(listOf(faction)) }.isFailure)
        assertEquals(faction.id, factions.getFactionByPlayer(member)?.id)
    }

    @Test
    fun `resetting switch cooldown allows another team change`() {
        val player = Mockito.mock(Player::class.java)
        Mockito.`when`(player.uniqueId).thenReturn(admin)
        Mockito.`when`(player.name).thenReturn("Admin")
        Mockito.mockStatic(Bukkit::class.java).use {
            assertTrue(teams.joinTeam(player, "gg"))
            assertFalse(teams.joinTeam(player, "goons"))
            teams.resetSwitchCooldown(admin)
            assertTrue(teams.joinTeam(player, "goons"))
        }
    }
}
