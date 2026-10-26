package com.tyler.ggvsgoons.persistence

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Loading must fail rather than silently replacing damaged player data with an empty snapshot. */
internal object SafeYaml {
    fun load(file: File): YamlConfiguration {
        val yaml = YamlConfiguration()
        if (file.exists()) yaml.load(file) // Unlike loadConfiguration(), this propagates parse errors.
        return yaml
    }

    fun save(yaml: YamlConfiguration, file: File) {
        val destination = file.toPath().toAbsolutePath()
        Files.createDirectories(destination.parent)
        val temporary = Files.createTempFile(destination.parent, ".${file.name}.", ".tmp")
        try {
            Files.writeString(temporary, yaml.saveToString(), Charsets.UTF_8)
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    fun requireSection(yaml: YamlConfiguration, key: String, file: File) =
        yaml.getConfigurationSection(key) ?: if (yaml.getKeys(false).isEmpty()) null
        else throw IllegalStateException("Missing or invalid '$key' section in ${file.name}")
}
