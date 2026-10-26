package com.tyler.ggvsgoons.admin

import com.tyler.ggvsgoons.persistence.SafeYaml
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import java.util.logging.Logger
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Only the plugin's own known data files are eligible for backup and restore. */
class BackupManager(private val dataFolder: File, private val logger: Logger) {
    private val backupDirectory = File(dataFolder, "backups")
    private val pendingRestore = File(backupDirectory, "pending-restore.dat")
    private var maxBackups = 10
    private var autoBackupEnabled = false
    private var autoBackupInterval = 3600

    init {
        check(backupDirectory.isDirectory || backupDirectory.mkdirs()) { "Cannot create backup directory" }
    }

    fun updateConfig(maxBackups: Int, autoBackupEnabled: Boolean, autoBackupInterval: Int) {
        this.maxBackups = maxBackups.coerceAtLeast(1)
        this.autoBackupEnabled = autoBackupEnabled
        this.autoBackupInterval = autoBackupInterval
    }

    private fun backupFile(name: String): File {
        require(NAME.matches(name)) { "Backup name must be 1-64 letters, numbers, underscores, or dashes" }
        val file = File(backupDirectory, "$name.zip")
        require(file.canonicalFile.parentFile == backupDirectory.canonicalFile) { "Invalid backup path" }
        return file
    }

    fun createBackup(name: String? = null, createdBy: UUID? = null, description: String? = null): Result<BackupInfo> = runCatching {
        val timestamp = System.currentTimeMillis()
        val date = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date(timestamp))
        val backupName = name ?: "backup_${date}_${UUID.randomUUID().toString().take(8)}"
        val file = backupFile(backupName)
        require(!file.exists()) { "Backup already exists: $backupName" }
        val temporary = Files.createTempFile(backupDirectory.toPath(), ".backup-", ".tmp")
        try {
            ZipOutputStream(Files.newOutputStream(temporary)).use { zip ->
                DATA_FILES.forEach { filename ->
                    val source = File(dataFolder, filename)
                    if (source.isFile) {
                        zip.putNextEntry(ZipEntry(filename))
                        Files.newInputStream(source.toPath()).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            require(!file.exists()) { "Backup already exists: $backupName" }
            replaceFile(temporary, file.toPath(), replace = false)
        } finally {
            Files.deleteIfExists(temporary)
        }
        val info = BackupInfo(backupName, timestamp, file.length(), createdBy, description)
        logger.info("Created backup: $backupName (${info.size} bytes)")
        cleanupOldBackups()
        info
    }.onFailure { logger.severe("Failed to create backup: ${it.message}") }

    fun listBackups(): List<BackupInfo> = backupDirectory.listFiles().orEmpty()
        .filter { it.isFile && it.extension == "zip" && NAME.matches(it.nameWithoutExtension) }
        .map { BackupInfo(it.nameWithoutExtension, it.lastModified(), it.length(), null) }
        .sortedByDescending { it.timestamp }

    /** Validate an entire archive first, then queue it; never overwrite files on a running server. */
    fun restoreBackup(name: String): Result<Unit> = runCatching {
        val file = backupFile(name)
        require(file.isFile) { "Backup not found: $name" }
        require(!pendingRestore.exists()) { "A restore is already queued; restart the server first" }
        readArchive(file) // Reject malformed, oversized, duplicate or unexpected entries before queuing.
        val temporary = Files.createTempFile(backupDirectory.toPath(), ".restore-", ".tmp")
        try {
            Files.copy(file.toPath(), temporary, StandardCopyOption.REPLACE_EXISTING)
            replaceFile(temporary, pendingRestore.toPath(), replace = false)
        } finally {
            Files.deleteIfExists(temporary)
        }
        logger.info("Queued backup $name for restore on the next plugin startup")
    }.onFailure { logger.severe("Failed to queue backup restore: ${it.message}") }

    /** Must run before modules load or write any data. A failed restore aborts startup. */
    fun applyPendingRestore() {
        if (!pendingRestore.exists()) return
        val restored = readArchive(pendingRestore)
        val safetyName = "pre_restore_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        createBackup(safetyName, description = "Automatic safety backup before restore").getOrThrow()

        // Retain an in-memory rollback snapshot too: replacing several files is not one atomic operation.
        val previous = restored.keys.associateWith { filename ->
            File(dataFolder, filename).takeIf { it.isFile }?.readBytes()
        }
        try {
            restored.forEach { (filename, contents) -> writeAtomically(File(dataFolder, filename), contents) }
        } catch (failure: Exception) {
            previous.forEach { (filename, contents) ->
                try {
                    val destination = File(dataFolder, filename)
                    if (contents == null) Files.deleteIfExists(destination.toPath())
                    else writeAtomically(destination, contents)
                } catch (rollbackError: Exception) {
                    failure.addSuppressed(rollbackError)
                }
            }
            throw IllegalStateException("Restore failed; check rollback errors and the pre-restore backup before restarting", failure)
        }
        // If removal fails, don't run the plugin against a restore that might be reapplied next startup.
        Files.delete(pendingRestore.toPath())
        logger.info("Applied queued restore before plugin data was loaded")
    }

    fun deleteBackup(name: String): Result<Unit> = runCatching {
        val file = backupFile(name)
        require(file.isFile) { "Backup not found: $name" }
        check(file.delete()) { "Failed to delete backup: $name" }
        logger.info("Deleted backup: $name")
    }

    private fun cleanupOldBackups() {
        listBackups().drop(maxBackups).forEach { deleteBackup(it.name).getOrThrow() }
    }

    fun isAutoBackupEnabled(): Boolean = autoBackupEnabled
    fun getAutoBackupInterval(): Int = autoBackupInterval

    private fun readArchive(file: File): Map<String, ByteArray> {
        val contents = linkedMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(Files.newInputStream(file.toPath())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && entry.name in DATA_FILES) { "Unexpected backup entry: ${entry.name}" }
                require(entry.name !in contents) { "Duplicate backup entry: ${entry.name}" }
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val length = zip.read(chunk)
                    if (length == -1) break
                    require(buffer.size() + length <= MAX_FILE_BYTES && total + length <= MAX_ARCHIVE_BYTES) {
                        "Backup exceeds size limit"
                    }
                    buffer.write(chunk, 0, length)
                    total += length
                }
                val bytes = buffer.toByteArray()
                if (entry.name.endsWith(".yml")) {
                    val yaml = YamlConfiguration()
                    yaml.loadFromString(String(bytes, Charsets.UTF_8))
                    val section = when (entry.name) {
                        "prisoners.yml" -> "prisoners"
                        "teams.yml" -> "teams"
                        "factions.yml" -> "factions"
                        else -> null
                    }
                    if (section != null) {
                        val root = SafeYaml.requireSection(yaml, section, File(dataFolder, entry.name))
                        require(root != null || entry.name != "teams.yml") { "Teams backup has no teams" }
                    }
                }
                contents[entry.name] = bytes
                zip.closeEntry()
            }
        }
        require(contents.isNotEmpty()) { "Backup contains no plugin data" }
        return contents
    }

    private fun writeAtomically(destination: File, contents: ByteArray) {
        val path = destination.toPath().toAbsolutePath()
        Files.createDirectories(path.parent)
        val temporary = Files.createTempFile(path.parent, ".${destination.name}.", ".tmp")
        try {
            Files.write(temporary, contents)
            replaceFile(temporary, path, replace = true)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun replaceFile(source: Path, target: Path, replace: Boolean) {
        val options = if (replace) arrayOf(StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            else arrayOf(StandardCopyOption.ATOMIC_MOVE)
        try {
            Files.move(source, target, *options)
        } catch (_: AtomicMoveNotSupportedException) {
            if (replace) Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
            else Files.move(source, target)
        }
    }

    companion object {
        private val NAME = Regex("[A-Za-z0-9_-]{1,64}")
        private val DATA_FILES = setOf("config.yml", "prisoners.yml", "teams.yml", "factions.yml", "audit.log")
        private const val MAX_FILE_BYTES = 16 * 1024 * 1024
        private const val MAX_ARCHIVE_BYTES = 64 * 1024 * 1024L
    }
}
