package com.tyler.ggvsgoons.persistence

import com.tyler.ggvsgoons.commands.Prisoner
import com.tyler.ggvsgoons.factions.Faction
import org.bukkit.GameMode
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class PersistenceSafetyTest {
    @TempDir lateinit var directory: Path
    private val logger = Logger.getLogger(PersistenceSafetyTest::class.java.name)

    @Test
    fun `valid prisoner data survives a save and load`() {
        val file = directory.resolve("prisoners.yml").toFile()
        val prisonerId = UUID.randomUUID()
        val data = mapOf(prisonerId to Prisoner(UUID.randomUUID(), GameMode.SURVIVAL))
        PrisonerPersistence.savePrisoners(data, file, logger)
        assertEquals(data, PrisonerPersistence.loadPrisoners(file, logger))
    }

    @Test
    fun `invalid yaml is rejected without modifying the original file`() {
        val file = directory.resolve("prisoners.yml").toFile()
        val original = "prisoners: [not: valid: yaml"
        file.writeText(original)
        assertFailsWith<IllegalStateException> { PrisonerPersistence.loadPrisoners(file, logger) }
        assertEquals(original, file.readText())
    }

    @Test
    fun `invalid prisoner entry is rejected rather than partially loaded`() {
        val file = directory.resolve("prisoners.yml").toFile()
        val id = UUID.randomUUID()
        val original = "prisoners:\n  $id:\n    captor: invalid-uuid\n    previousGameMode: SURVIVAL\n"
        file.writeText(original)
        assertFailsWith<IllegalStateException> { PrisonerPersistence.loadPrisoners(file, logger) }
        assertEquals(original, file.readText())
    }

    @Test
    fun `valid faction membership survives a save and load`() {
        val file = directory.resolve("factions.yml").toFile()
        val leader = UUID.randomUUID()
        val member = UUID.randomUUID()
        val faction = Faction("gg_alpha", "Alpha", "gg", leader, mutableSetOf(leader, member))
        FactionPersistence.saveFactions(listOf(faction), file, logger)
        val restored = FactionPersistence.loadFactions(file, logger).single()
        assertEquals(faction.id, restored.id)
        assertEquals(faction.members, restored.members)
    }

    @Test
    fun `invalid faction member does not erase the saved file`() {
        val file = directory.resolve("factions.yml").toFile()
        val original = "factions:\n  0:\n    id: gg_alpha\n    name: Alpha\n    teamId: gg\n    leaderId: ${UUID.randomUUID()}\n    members: [invalid]\n    officers: []\n"
        file.writeText(original)
        assertFailsWith<IllegalStateException> { FactionPersistence.loadFactions(file, logger) }
        assertEquals(original, file.readText())
    }

    @Test
    fun `successful yaml save replaces existing data and leaves no temporary files`() {
        val file = directory.resolve("teams.yml").toFile()
        file.writeText("teams: preserved\n")
        val yaml = YamlConfiguration()
        yaml.set("teams", mapOf("gg" to "replacement"))
        SafeYaml.save(yaml, file)
        assertEquals("replacement", SafeYaml.load(file).getString("teams.gg"))
        assertFalse(directory.toFile().listFiles()?.any { it.name.endsWith(".tmp") } ?: true)
    }

    @Test
    fun `unexpected root data cannot be mistaken for empty persistence`() {
        val file = directory.resolve("prisoners.yml").toFile()
        file.writeText("unexpected: data\n")
        assertFailsWith<IllegalStateException> { PrisonerPersistence.loadPrisoners(file, logger) }
        assertEquals("unexpected: data\n", file.readText())
    }
}
