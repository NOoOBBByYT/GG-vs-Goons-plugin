package com.tyler.ggvsgoons.persistence

import com.tyler.ggvsgoons.GGvGPlugin
import com.tyler.ggvsgoons.teams.TeamManager
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

/** Reads/writes plugins/GGvGoons/teams.yml — team membership and spawn points. */
class TeamPersistence(
    private val plugin: GGvGPlugin,
    private val manager: TeamManager
) {

    private val file = File(plugin.dataFolder, "teams.yml")
    private var loadedSuccessfully = false

    fun load() {
        val config = SafeYaml.load(file)
        val teamsSection = SafeYaml.requireSection(config, "teams", file)
        if (teamsSection == null) {
            require(!file.exists()) { "Existing ${file.name} has no teams; refusing to replace a potentially truncated file" }
            loadedSuccessfully = true
            return
        }

        var restoredMembers = 0

        for (teamId in teamsSection.getKeys(false)) {
            require(teamId in manager.teams) { "Unknown team '$teamId' in ${file.name}" }
            val section = requireNotNull(teamsSection.getConfigurationSection(teamId)) { "Invalid team entry $teamId" }
            require(section.isList("members") && section.getList("members")?.all { it is String } == true) {
                "Invalid members for team $teamId"
            }

            section.getStringList("members").forEach { idString ->
                val uuid = UUID.fromString(idString)
                require(manager.getPlayerTeam(uuid) == null) { "Duplicate team membership for $uuid" }
                manager.setPlayerTeamSilently(uuid, teamId)
                restoredMembers++
            }

            require(!section.contains("spawn") || section.isConfigurationSection("spawn")) { "Invalid spawn for $teamId" }
            val spawnSection = section.getConfigurationSection("spawn")
            if (spawnSection != null) {
                val worldName = requireNotNull(spawnSection.getString("world")) { "Missing spawn world for $teamId" }
                val world = Bukkit.getWorld(worldName)
                require(world != null) { "World '$worldName' is not loaded; refusing to lose the saved spawn for $teamId" }
                listOf("x", "y", "z", "yaw", "pitch").forEach { coordinate ->
                    require(spawnSection.get(coordinate) is Number) { "Invalid spawn $coordinate for $teamId" }
                }
                val loc = Location(
                    world,
                    spawnSection.getDouble("x"),
                    spawnSection.getDouble("y"),
                    spawnSection.getDouble("z"),
                    spawnSection.getDouble("yaw").toFloat(),
                    spawnSection.getDouble("pitch").toFloat()
                )
                manager.setTeamSpawn(teamId, loc)
            }
        }

        loadedSuccessfully = true
        plugin.logger.info("Loaded team data: $restoredMembers member(s) restored")
    }

    fun save() {
        if (!loadedSuccessfully) {
            plugin.logger.severe("Team data was not loaded successfully; refusing to overwrite teams.yml")
            return
        }
        val config = YamlConfiguration()

        for ((teamId, team) in manager.teams) {
            val base = "teams.$teamId"
            config.set("$base.members", manager.getTeamMembers(teamId).map { it.toString() })

            team.spawn?.let { loc ->
                config.set("$base.spawn.world", loc.world?.name)
                config.set("$base.spawn.x", loc.x)
                config.set("$base.spawn.y", loc.y)
                config.set("$base.spawn.z", loc.z)
                config.set("$base.spawn.yaw", loc.yaw.toDouble())
                config.set("$base.spawn.pitch", loc.pitch.toDouble())
            }
        }

        try {
            if (!plugin.dataFolder.exists()) plugin.dataFolder.mkdirs()
            SafeYaml.save(config, file)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to save teams.yml: ${e.message}")
        }
    }
}
