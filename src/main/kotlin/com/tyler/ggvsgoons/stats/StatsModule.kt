package com.tyler.ggvsgoons.stats

import com.tyler.ggvsgoons.GGvGModule
import com.tyler.ggvsgoons.GGvGPlugin
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

class PlayerStatsManager(plugin: GGvGPlugin) {
    private val killsKey = NamespacedKey(plugin, "kills")
    private val deathsKey = NamespacedKey(plugin, "deaths")
    private val prisonersKey = NamespacedKey(plugin, "prisoners_captured")

    fun getStats(player: Player): PlayerStats {
        val pdc = player.persistentDataContainer
        return PlayerStats(
            pdc.get(killsKey, PersistentDataType.INTEGER) ?: 0,
            pdc.get(deathsKey, PersistentDataType.INTEGER) ?: 0,
            pdc.get(prisonersKey, PersistentDataType.INTEGER) ?: 0
        )
    }

    fun addKill(player: Player) = increment(player, killsKey)
    fun addDeath(player: Player) = increment(player, deathsKey)
    fun addPrisonerCaptured(player: Player) = increment(player, prisonersKey)

    private fun increment(player: Player, key: NamespacedKey) {
        val pdc = player.persistentDataContainer
        pdc.set(key, PersistentDataType.INTEGER, (pdc.get(key, PersistentDataType.INTEGER) ?: 0) + 1)
    }
}

data class PlayerStats(val kills: Int = 0, val deaths: Int = 0, val prisonersCaptured: Int = 0) {
    fun getKDR(): Double = if (deaths == 0) kills.toDouble() else kills.toDouble() / deaths.toDouble()
}

class StatsModule(private val plugin: GGvGPlugin) : GGvGModule, Listener {
    lateinit var manager: PlayerStatsManager
        private set

    override fun register(plugin: GGvGPlugin) {
        manager = PlayerStatsManager(plugin)
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        manager.addDeath(event.entity)
        event.entity.killer?.let { manager.addKill(it) }
    }
}
