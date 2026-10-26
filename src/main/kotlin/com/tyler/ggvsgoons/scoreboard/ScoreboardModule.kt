package com.tyler.ggvsgoons.scoreboard

import com.tyler.ggvsgoons.GGvGModule
import com.tyler.ggvsgoons.GGvGPlugin
import org.bukkit.Bukkit

class ScoreboardModule(private val plugin: GGvGPlugin) : GGvGModule {
    lateinit var manager: ScoreboardManager
        private set

    override fun register(plugin: GGvGPlugin) {
        manager = ScoreboardManager(plugin)
        plugin.getCommand("scoreboard")?.setExecutor(ScoreboardCommand(plugin))
        plugin.server.pluginManager.registerEvents(manager.renderer, plugin)
        plugin.server.pluginManager.registerEvents(ScoreboardEventListener(plugin, manager), plugin)
        manager.start()
    }

    fun onDisable() {
        if (::manager.isInitialized) manager.stop()
    }
}
