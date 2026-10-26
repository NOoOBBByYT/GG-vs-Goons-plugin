package com.tyler.ggvsgoons.admin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConfigValidatorTest {
    private val validator = ConfigValidator()

    @Test
    fun `accepts configured prisoner offer timeout boundaries`() {
        assertEquals(0, assertIs<ValidationResult.Valid>(validator.validate("prisoner.offer-expiry-timeout", "0")).value)
        assertEquals(3600, assertIs<ValidationResult.Valid>(validator.validate("prisoner.offer-expiry-timeout", "3600")).value)
    }

    @Test
    fun `rejects out of range and nonnumeric offer timeouts`() {
        for (value in listOf("-1", "3601", "not a number")) {
            assertIs<ValidationResult.Invalid>(validator.validate("prisoner.offer-expiry-timeout", value))
        }
    }

    @Test
    fun `converts supported boolean spellings and rejects ambiguous values`() {
        assertEquals(true, assertIs<ValidationResult.Valid>(validator.validate("factions.enabled", "yes")).value)
        assertEquals(false, assertIs<ValidationResult.Valid>(validator.validate("factions.enabled", "off")).value)
        assertIs<ValidationResult.Invalid>(validator.validate("factions.enabled", "maybe"))
    }

    @Test
    fun `rejects unknown config paths`() {
        assertTrue(validator.isValidPath("factions.enabled"))
        assertFalse(validator.isValidPath("factions.unknown"))
        assertIs<ValidationResult.Invalid>(validator.validate("factions.unknown", "true"))
    }

    @Test
    fun `rejects legacy paths that had no implementation`() {
        for (path in listOf("permissions.enabled", "teams.chat.format", "factions.homes.teleport-warmup",
            "factions.members.allow-expansion", "admin.audit.enabled", "admin.permissions.fallback-to-internal",
            "discord.enabled")) {
            assertFalse(validator.isValidPath(path), path)
            assertIs<ValidationResult.Invalid>(validator.validate(path, "true"))
        }
        assertTrue(validator.isValidPath("admin.commands.custom-disable-message"))
    }
}
