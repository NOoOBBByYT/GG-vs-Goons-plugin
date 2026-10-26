package com.tyler.ggvsgoons.persistence

import com.tyler.ggvsgoons.factions.Faction
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID
import java.util.logging.Logger

/**
 * Handles saving and loading faction data to/from YAML files
 */
object FactionPersistence {
    
    /**
     * Save factions to a YAML file
     */
    fun saveFactions(factions: List<Faction>, file: File, logger: Logger) {
        try {
            val config = YamlConfiguration()
            
            factions.forEachIndexed { index, faction ->
                val path = "factions.$index"
                config.set("$path.id", faction.id)
                config.set("$path.name", faction.name)
                config.set("$path.teamId", faction.teamId)
                config.set("$path.leaderId", faction.leaderId.toString())
                config.set("$path.members", faction.members.map { it.toString() })
                config.set("$path.officers", faction.officers.map { it.toString() })
                config.set("$path.description", faction.description)
                config.set("$path.color", faction.color.name)
                config.set("$path.createdAt", faction.createdAt)
                config.set("$path.maxMembers", faction.maxMembers)
                
                // Save home location if set
                faction.homeLocation?.let { loc ->
                    config.set("$path.home.world", loc.world?.name)
                    config.set("$path.home.x", loc.x)
                    config.set("$path.home.y", loc.y)
                    config.set("$path.home.z", loc.z)
                    config.set("$path.home.yaw", loc.yaw)
                    config.set("$path.home.pitch", loc.pitch)
                }
            }
            
            SafeYaml.save(config, file)
            logger.info("Saved ${factions.size} faction(s) to ${file.name}")
        } catch (e: Exception) {
            logger.severe("Failed to save factions: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Load factions from a YAML file
     */
    fun loadFactions(file: File, logger: Logger): List<Faction> {
        if (!file.exists()) {
            logger.info("No faction data file found, starting fresh")
            return emptyList()
        }
        
        try {
            val config = SafeYaml.load(file)
            val factions = mutableListOf<Faction>()
            
            val factionsSection = SafeYaml.requireSection(config, "factions", file) ?: return emptyList()
            val seenMembers = mutableSetOf<UUID>()
            
            for (key in factionsSection.getKeys(false)) {
                val path = "factions.$key"
                
                try {
                    require(config.isConfigurationSection(path)) { "Invalid faction entry $key" }
                    val id = requireNotNull(config.getString("$path.id")) { "Missing faction id at $key" }
                    val name = requireNotNull(config.getString("$path.name")) { "Missing faction name at $key" }
                    val teamId = requireNotNull(config.getString("$path.teamId")) { "Missing team for $name" }
                    require(teamId == "gg" || teamId == "goons") { "Unknown team for $name" }
                    val leaderId = UUID.fromString(requireNotNull(config.getString("$path.leaderId")) { "Missing leader for $name" })
                    require(config.isList("$path.members") && config.getList("$path.members")?.all { it is String } == true) { "Invalid members for $name" }
                    require(config.isList("$path.officers") && config.getList("$path.officers")?.all { it is String } == true) { "Invalid officers for $name" }
                    
                    val members = config.getStringList("$path.members")
                        .map { UUID.fromString(it) }
                        .toMutableSet()
                    require(leaderId in members) { "Leader missing from members for $name" }
                    require(members.size == config.getStringList("$path.members").size) { "Duplicate members in $name" }
                    
                    val officers = config.getStringList("$path.officers")
                        .map { UUID.fromString(it) }
                        .toMutableSet()
                    require(officers.size == config.getStringList("$path.officers").size) { "Duplicate officers in $name" }
                    require((members + leaderId).none { it in seenMembers }) { "Duplicate faction membership in $name" }
                    seenMembers.addAll(members + leaderId)
                    require(officers.all { it in members }) { "Officer missing from members in $name" }
                    require(factions.none { it.id == id }) { "Duplicate faction id $id" }
                    
                    val description = config.getString("$path.description") ?: ""
                    val colorName = config.getString("$path.color") ?: "WHITE"
                    val color = try {
                        ChatColor.valueOf(colorName)
                    } catch (e: Exception) {
                        ChatColor.WHITE
                    }
                    
                    val createdAt = config.getLong("$path.createdAt", System.currentTimeMillis())
                    val maxMembers = config.getInt("$path.maxMembers", 10)
                    require(!config.contains("$path.createdAt") || config.get("$path.createdAt") is Number) { "Invalid creation time for $name" }
                    require(!config.contains("$path.maxMembers") || config.get("$path.maxMembers") is Number) { "Invalid capacity for $name" }
                    require(maxMembers >= members.size) { "Faction $name has more members than its saved capacity" }
                    
                    // Load home location if exists
                    var homeLocation: Location? = null
                    if (config.contains("$path.home")) {
                        require(config.isConfigurationSection("$path.home")) { "Invalid home for $name" }
                        val worldName = requireNotNull(config.getString("$path.home.world")) { "Missing home world for $name" }
                        val world = Bukkit.getWorld(worldName)
                        require(world != null) { "World '$worldName' is not loaded; refusing to lose $name home" }
                        listOf("x", "y", "z", "yaw", "pitch").forEach { coordinate ->
                            require(config.get("$path.home.$coordinate") is Number) { "Invalid home $coordinate for $name" }
                        }
                        val x = config.getDouble("$path.home.x")
                        val y = config.getDouble("$path.home.y")
                        val z = config.getDouble("$path.home.z")
                        val yaw = config.getDouble("$path.home.yaw").toFloat()
                        val pitch = config.getDouble("$path.home.pitch").toFloat()
                        homeLocation = Location(world, x, y, z, yaw, pitch)
                    }
                    
                    val faction = Faction(
                        id = id,
                        name = name,
                        teamId = teamId,
                        leaderId = leaderId,
                        members = members,
                        officers = officers,
                        description = description,
                        color = color,
                        homeLocation = homeLocation,
                        createdAt = createdAt,
                        maxMembers = maxMembers
                    )
                    
                    factions.add(faction)
                } catch (e: Exception) {
                    throw IllegalStateException("Invalid faction at index $key in ${file.name}", e)
                }
            }
            
            logger.info("Loaded ${factions.size} faction(s) from ${file.name}")
            return factions
        } catch (e: Exception) {
            throw IllegalStateException("Failed to load factions from ${file.name}; original file was not modified", e)
        }
    }
}
