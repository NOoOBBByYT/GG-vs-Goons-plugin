package com.tyler.ggvsgoons.admin

import com.tyler.ggvsgoons.GGvGPlugin
import com.tyler.ggvsgoons.teams.TeamManager
import com.tyler.ggvsgoons.teams.TeamsModule
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Server
import org.bukkit.command.PluginCommand
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.permissions.PermissionAttachment
import org.bukkit.plugin.PluginManager
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModerationEnforcementTest {
    private val plugin = Mockito.mock(GGvGPlugin::class.java)
    private val player = Mockito.mock(Player::class.java)
    private val playerId = UUID.randomUUID()
    private val adminId = UUID.randomUUID()
    private val config = YamlConfiguration()
    private val team = Mockito.mock(PluginCommand::class.java)
    private val admin = Mockito.mock(PluginCommand::class.java)
    private val moderation = ModerationManager(plugin, Mockito.mock(AuditLogger::class.java))
    private val permissions = PermissionManager(plugin, false)
    private val listener = AdminModerationListener(plugin, moderation, permissions)

    @BeforeEach
    fun setUp() {
        val server = Mockito.mock(Server::class.java)
        Mockito.`when`(player.server).thenReturn(server)
        Mockito.`when`(server.onlinePlayers).thenReturn(emptyList())
        Mockito.`when`(plugin.logger).thenReturn(java.util.logging.Logger.getAnonymousLogger())
        Mockito.`when`(plugin.config).thenReturn(config)
        Mockito.`when`(plugin.name).thenReturn("GGvGoons")
        Mockito.`when`(plugin.getCommand("team")).thenReturn(team)
        Mockito.`when`(plugin.getCommand("ggadmin")).thenReturn(admin)
        Mockito.`when`(plugin.getCommand("admin")).thenReturn(admin)
        Mockito.`when`(team.name).thenReturn("team")
        Mockito.`when`(admin.name).thenReturn("ggadmin")
        Mockito.`when`(player.uniqueId).thenReturn(playerId)
    }

    @Test
    fun `disabled plugin command is blocked through a namespaced label`() {
        assertTrue(moderation.disableCommand("/team", "Restricted").isSuccess)
        assertTrue(moderation.disableCommand("ggadmin").isFailure)
        assertTrue(moderation.disableCommand("otherPluginCommand").isFailure)
        val command = PlayerCommandPreprocessEvent(player, "/ggvgoons:TEAM join goons")
        listener.onCommand(command)
        assertTrue(command.isCancelled)
        assertTrue(moderation.enableCommand("team").isSuccess)
        val enabled = PlayerCommandPreprocessEvent(player, "/team list")
        listener.onCommand(enabled)
        assertFalse(enabled.isCancelled)
    }

    @Test
    fun `per player restriction and admin bypass are enforced`() {
        val another = UUID.randomUUID()
        assertTrue(moderation.disableCommandForPlayer("team", playerId).isSuccess)
        assertTrue(moderation.isCommandDisabled("team", playerId))
        assertFalse(moderation.isCommandDisabled("team", another))
        val restricted = PlayerCommandPreprocessEvent(player, "/team list")
        listener.onCommand(restricted)
        assertTrue(restricted.isCancelled)

        Mockito.`when`(player.hasPermission("ggvgoons.admin.command")).thenReturn(true)
        val bypass = PlayerCommandPreprocessEvent(player, "/team list")
        listener.onCommand(bypass)
        assertFalse(bypass.isCancelled)
        config.set("admin.commands.allow-admin-bypass", false)
        val noBypass = PlayerCommandPreprocessEvent(player, "/team list")
        listener.onCommand(noBypass)
        assertTrue(noBypass.isCancelled)
        assertTrue(moderation.enableCommandForPlayer("team", playerId).isSuccess)
        assertFalse(moderation.isCommandDisabled("team", playerId))
    }

    @Test
    fun `frozen player cannot move or use commands except admin recovery`() {
        Mockito.mockStatic(Bukkit::class.java).use {
            assertTrue(moderation.freezePlayer(playerId, adminId, "Investigation").isSuccess)
        }
        val from = Location(null, 0.0, 64.0, 0.0)
        val moved = PlayerMoveEvent(player, from, Location(null, 1.0, 64.0, 0.0))
        listener.onMove(moved)
        assertTrue(moved.isCancelled)
        val interaction = Mockito.mock(PlayerInteractEvent::class.java)
        Mockito.`when`(interaction.player).thenReturn(player)
        listener.onInteract(interaction)
        Mockito.verify(interaction).isCancelled = true
        val looked = PlayerMoveEvent(player, from, Location(null, 0.0, 64.0, 0.0, 50f, 0f))
        listener.onMove(looked)
        assertFalse(looked.isCancelled)
        val blocked = PlayerCommandPreprocessEvent(player, "/team list")
        listener.onCommand(blocked)
        assertTrue(blocked.isCancelled)
        Mockito.`when`(player.hasPermission("ggvgoons.admin.use")).thenReturn(true)
        val recovery = PlayerCommandPreprocessEvent(player, "/admin player unfreeze Me")
        listener.onCommand(recovery)
        assertFalse(recovery.isCancelled)
        Mockito.mockStatic(Bukkit::class.java).use {
            assertTrue(moderation.unfreezePlayer(playerId).isSuccess)
        }
    }

    @Test
    fun `team cooldown reset delegates to the real cooldown owner`() {
        val teams = Mockito.mock(TeamsModule::class.java)
        val manager = Mockito.mock(TeamManager::class.java)
        Mockito.`when`(plugin.teams).thenReturn(teams)
        Mockito.`when`(teams.manager).thenReturn(manager)
        Mockito.mockStatic(Bukkit::class.java).use { bukkit ->
            val offline = Mockito.mock(org.bukkit.OfflinePlayer::class.java)
            bukkit.`when`<org.bukkit.OfflinePlayer> { Bukkit.getOfflinePlayer(playerId) }.thenReturn(offline)
            assertTrue(moderation.resetCooldowns(playerId, adminId, "Admin").isSuccess)
        }
        Mockito.verify(manager).resetSwitchCooldown(playerId)
    }

    @Test
    fun `permission operations fail for offline players and unimplemented LuckPerms`() {
        Mockito.mockStatic(Bukkit::class.java).use { bukkit ->
            assertTrue(permissions.grantPermission(playerId, "ggvgoons.team.use").isFailure)
            assertTrue(permissions.revokePermission(playerId, "ggvgoons.team.use").isFailure)
            assertTrue(permissions.assignGroup(playerId, "admin").isFailure)
            val pluginManager = Mockito.mock(PluginManager::class.java)
            val luckPerms = Mockito.mock(org.bukkit.plugin.Plugin::class.java)
            bukkit.`when`<PluginManager> { Bukkit.getPluginManager() }.thenReturn(pluginManager)
            Mockito.`when`(pluginManager.getPlugin("LuckPerms")).thenReturn(luckPerms)
            val withLuckPerms = PermissionManager(plugin, true)
            assertTrue(withLuckPerms.grantPermission(playerId, "ggvgoons.team.use").isFailure)
        }
    }

    @Test
    fun `runtime grants can be removed only if this plugin owns them`() {
        val attachment = Mockito.mock(PermissionAttachment::class.java)
        val grants = mutableMapOf<String, Boolean>()
        Mockito.`when`(player.addAttachment(plugin)).thenReturn(attachment)
        Mockito.`when`(attachment.permissions).thenAnswer { grants }
        Mockito.doAnswer { invocation ->
            grants[invocation.getArgument(0)] = true
            null
        }.`when`(attachment).setPermission(Mockito.anyString(), Mockito.eq(true))
        Mockito.doAnswer { invocation ->
            grants.remove(invocation.getArgument<String>(0))
            null
        }.`when`(attachment).unsetPermission(Mockito.anyString())
        Mockito.mockStatic(Bukkit::class.java).use { bukkit ->
            bukkit.`when`<Player> { Bukkit.getPlayer(playerId) }.thenReturn(player)
            assertTrue(permissions.grantPermission(playerId, "ggvgoons.team.use").isSuccess)
            assertTrue(permissions.listPermissions(playerId).contains("ggvgoons.team.use"))
            assertTrue(permissions.revokePermission(playerId, "other.plugin.node").isFailure)
            assertTrue(permissions.revokePermission(playerId, "ggvgoons.team.use").isSuccess)
        }
        assertTrue(permissions.listPermissions(playerId).isEmpty())
    }
}
