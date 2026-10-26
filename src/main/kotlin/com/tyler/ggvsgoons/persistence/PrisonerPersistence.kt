package com.tyler.ggvsgoons.persistence

import com.tyler.ggvsgoons.commands.Prisoner
import org.bukkit.GameMode
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID
import java.util.logging.Logger

/**
 * Handles saving and loading prisoner state to/from YAML files.
 * Stores prisoner data in plugins/GGvGoons/prisoners.yml
 */
object PrisonerPersistence {

    /**
     * Save all active prisoners to the specified file.
     * Format:
     * prisoners:
     *   <prisonerId>:
     *     captor: <captorId>
     *     previousGameMode: <gamemode>
     */
    fun savePrisoners(prisoners: Map<UUID, Prisoner>, file: File, logger: Logger) {
        try {
            val yaml = YamlConfiguration()
            
            prisoners.forEach { (prisonerId, prisoner) ->
                val path = "prisoners.$prisonerId"
                yaml.set("$path.captor", prisoner.captorId.toString())
                yaml.set("$path.previousGameMode", prisoner.previousGameMode.name)
            }
            
            SafeYaml.save(yaml, file)
            logger.info("Saved ${prisoners.size} prisoner(s) to ${file.name}")
        } catch (e: Exception) {
            logger.severe("Failed to save prisoner data: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Load prisoners from the specified file.
     * Returns an empty map if the file doesn't exist or is corrupted.
     */
    fun loadPrisoners(file: File, logger: Logger): Map<UUID, Prisoner> {
        if (!file.exists()) {
            logger.info("No prisoner data file found - starting fresh")
            return emptyMap()
        }

        try {
            val yaml = SafeYaml.load(file)
            val prisoners = mutableMapOf<UUID, Prisoner>()
            
            val prisonersSection = SafeYaml.requireSection(yaml, "prisoners", file) ?: return emptyMap()

            prisonersSection.getKeys(false).forEach { prisonerIdStr ->
                try {
                    require(prisonersSection.isConfigurationSection(prisonerIdStr)) { "Invalid prisoner entry: $prisonerIdStr" }
                    val prisonerId = UUID.fromString(prisonerIdStr)
                    val captorIdStr = prisonersSection.getString("$prisonerIdStr.captor")
                    val gameModeStr = prisonersSection.getString("$prisonerIdStr.previousGameMode")
                    val captorId = UUID.fromString(requireNotNull(captorIdStr) { "Missing captor for $prisonerIdStr" })
                    val gameMode = GameMode.valueOf(requireNotNull(gameModeStr) { "Missing game mode for $prisonerIdStr" })
                    prisoners[prisonerId] = Prisoner(captorId, gameMode)
                } catch (e: Exception) {
                    throw IllegalStateException("Invalid prisoner entry $prisonerIdStr in ${file.name}", e)
                }
            }
            
            logger.info("Loaded ${prisoners.size} prisoner(s) from ${file.name}")
            return prisoners
        } catch (e: Exception) {
            throw IllegalStateException("Failed to load prisoner data from ${file.name}; original file was not modified", e)
        }
    }
}
