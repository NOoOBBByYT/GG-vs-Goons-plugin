package com.tyler.ggvsgoons.admin

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.logging.Logger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupManagerTest {
    @TempDir lateinit var directory: Path
    private val logger = Logger.getLogger(BackupManagerTest::class.java.name)

    @Test
    fun `restore stages validated data without changing running plugin files`() {
        val data = directory.resolve("config.yml").toFile()
        data.writeText("before: true\n")
        val manager = BackupManager(directory.toFile(), logger)
        val backup = manager.createBackup("safe").getOrThrow()
        assertEquals("safe", backup.name)
        data.writeText("after: true\n")

        manager.restoreBackup("safe").getOrThrow()
        assertEquals("after: true\n", data.readText())
        assertTrue(directory.resolve("backups/pending-restore.dat").toFile().isFile)

        manager.applyPendingRestore()
        assertEquals("before: true\n", data.readText())
        assertFalse(directory.resolve("backups/pending-restore.dat").toFile().exists())
        assertTrue(manager.listBackups().any { it.name.startsWith("pre_restore_") })
    }

    @Test
    fun `backup names cannot escape the backup directory or overwrite existing backups`() {
        val manager = BackupManager(directory.toFile(), logger)
        directory.resolve("config.yml").toFile().writeText("original: true\n")
        val created = manager.createBackup("snapshot").getOrThrow()
        assertTrue(manager.createBackup("snapshot").isFailure)
        assertEquals(created.size, directory.resolve("backups/snapshot.zip").toFile().length())
        for (name in listOf("../escape", "..\\escape", "/absolute", ".", "some file")) {
            assertTrue(manager.createBackup(name).isFailure)
            assertTrue(manager.restoreBackup(name).isFailure)
            assertTrue(manager.deleteBackup(name).isFailure)
        }
    }

    @Test
    fun `zip slip and unknown entries are rejected before any restore is queued`() {
        val manager = BackupManager(directory.toFile(), logger)
        val archive = directory.resolve("backups/unsafe.zip").toFile()
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("../outside.yml"))
            zip.write("injected: true".toByteArray())
            zip.closeEntry()
        }
        assertTrue(manager.restoreBackup("unsafe").isFailure)
        assertFalse(directory.resolve("backups/pending-restore.dat").toFile().exists())
        assertFalse(directory.resolve("outside.yml").toFile().exists())
    }

    @Test
    fun `invalid yaml is rejected before restore is queued`() {
        val manager = BackupManager(directory.toFile(), logger)
        val archive = directory.resolve("backups/invalid.zip").toFile()
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("teams.yml"))
            zip.write("teams: [not: valid: yaml".toByteArray())
            zip.closeEntry()
        }
        assertTrue(manager.restoreBackup("invalid").isFailure)
        assertFalse(directory.resolve("backups/pending-restore.dat").toFile().exists())
    }

    @Test
    fun `oversized entries are rejected before restore is queued`() {
        val manager = BackupManager(directory.toFile(), logger)
        val archive = directory.resolve("backups/oversized.zip").toFile()
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("audit.log"))
            val chunk = ByteArray(8192)
            repeat(2049) { zip.write(chunk) }
            zip.closeEntry()
        }
        assertTrue(manager.restoreBackup("oversized").isFailure)
        assertFalse(directory.resolve("backups/pending-restore.dat").toFile().exists())
    }

    @Test
    fun `a second restore cannot replace a pending restore`() {
        directory.resolve("config.yml").toFile().writeText("valid: true\n")
        val manager = BackupManager(directory.toFile(), logger)
        manager.createBackup("first").getOrThrow()
        manager.restoreBackup("first").getOrThrow()
        assertTrue(manager.restoreBackup("first").isFailure)
    }

    @Test
    fun `corrupted queued restore aborts startup without overwriting current data`() {
        val config = directory.resolve("config.yml").toFile()
        config.writeText("original: true\n")
        val manager = BackupManager(directory.toFile(), logger)
        manager.createBackup("good").getOrThrow()
        manager.restoreBackup("good").getOrThrow()
        val pending = directory.resolve("backups/pending-restore.dat").toFile()
        pending.writeBytes(byteArrayOf(1, 2, 3, 4))

        assertTrue(runCatching { manager.applyPendingRestore() }.isFailure)
        assertEquals("original: true\n", config.readText())
        assertTrue(pending.exists())
    }
}
