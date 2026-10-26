package com.tyler.ggvsgoons.scoreboard

import com.tyler.ggvsgoons.GGvGPlugin
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class ScoreboardEventListener(private val plugin: GGvGPlugin, private val manager: ScoreboardManager) : Listener {
    @EventHandler fun onJoin(event: PlayerJoinEvent) = manager.setupPlayer(event.player)
    @EventHandler fun onQuit(event: PlayerQuitEvent) = manager.removePlayer(event.player)
}
