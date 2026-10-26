package com.tyler.ggvsgoons.admin

import com.tyler.ggvsgoons.GGvGPlugin
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigRuntimeSafetyTest {
    @Test
    fun `startup settings cannot be changed by the runtime config command`() {
        val plugin = Mockito.mock(GGvGPlugin::class.java)
        val config = YamlConfiguration()
        Mockito.`when`(plugin.config).thenReturn(config)
        val manager = AdminConfigManager(plugin, ConfigValidator(), Mockito.mock(AuditLogger::class.java))
        val admin = UUID.randomUUID()
        for ((path, value) in listOf(
            "prisoner.offer-expiry-timeout" to 25,
            "factions.enable-persistence" to false,
            "admin.permissions.use-luckperms" to true,
            "admin.backups.max-backups" to 15,
            "admin.audit.log-mod-actions" to false
        )) {
            val result = manager.setConfigValue(path, value, admin, "Admin")
            assertTrue(result.isFailure, path)
            assertEquals(null, config.get(path), path)
        }
    }
}
