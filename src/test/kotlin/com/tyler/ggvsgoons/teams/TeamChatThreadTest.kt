package com.tyler.ggvsgoons.teams

import com.tyler.ggvsgoons.GGvGPlugin
import org.bukkit.Bukkit
import org.bukkit.Server
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.scheduler.BukkitScheduler
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.io.File
import java.util.UUID
import kotlin.test.assertTrue

class TeamChatThreadTest {
    @Test
    fun `async prefixed chat is cancelled then delivered on the scheduled main thread`() {
        val plugin = Mockito.mock(GGvGPlugin::class.java)
        val config = YamlConfiguration()
        val server = Mockito.mock(Server::class.java)
        val scheduler = Mockito.mock(BukkitScheduler::class.java)
        Mockito.`when`(plugin.config).thenReturn(config)
        Mockito.`when`(plugin.dataFolder).thenReturn(File("."))
        Mockito.`when`(plugin.server).thenReturn(server)
        val module = TeamsModule(plugin)
        val sender = Mockito.mock(Player::class.java)
        val receiver = Mockito.mock(Player::class.java)
        val senderId = UUID.randomUUID()
        val receiverId = UUID.randomUUID()
        Mockito.`when`(sender.uniqueId).thenReturn(senderId)
        Mockito.`when`(sender.isOnline).thenReturn(true)
        Mockito.`when`(sender.name).thenReturn("Sender")
        Mockito.`when`(server.getPlayer(senderId)).thenReturn(sender)
        Mockito.`when`(server.getPlayer(receiverId)).thenReturn(receiver)
        module.manager.setPlayerTeamSilently(senderId, "gg")
        module.manager.setPlayerTeamSilently(receiverId, "gg")
        val event = Mockito.mock(AsyncPlayerChatEvent::class.java)
        Mockito.`when`(event.message).thenReturn("@team Hello")
        Mockito.`when`(event.player).thenReturn(sender)
        Mockito.`when`(event.isAsynchronous).thenReturn(true)
        val queued = ArgumentCaptor.forClass(Runnable::class.java)

        Mockito.mockStatic(Bukkit::class.java).use { bukkit ->
            bukkit.`when`<BukkitScheduler> { Bukkit.getScheduler() }.thenReturn(scheduler)
            module.onChat(event)
            Mockito.verify(event).isCancelled = true
            Mockito.verify(sender, Mockito.never()).sendMessage(Mockito.anyString())
            Mockito.verify(scheduler).runTask(Mockito.eq(plugin), queued.capture())
            queued.value.run()
        }
        Mockito.verify(receiver).sendMessage(Mockito.contains("Sender: Hello"))
    }
}
