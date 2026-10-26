package com.tyler.ggvsgoons.admin

import org.bukkit.Bukkit
import org.bukkit.permissions.PermissionAttachment
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Runtime grants are scoped to online players and this plugin's Bukkit attachments. */
class PermissionManager(
    private val plugin: Plugin,
    useLuckPerms: Boolean = true
) {
    private val permissionAttachments = ConcurrentHashMap<UUID, PermissionAttachment>()
    private val temporaryPermissions = ConcurrentHashMap<UUID, MutableMap<String, Long>>()
    private val luckPermsSelected = useLuckPerms && Bukkit.getPluginManager().getPlugin("LuckPerms") != null

    init {
        if (luckPermsSelected) {
            plugin.logger.warning("LuckPerms editing is not implemented; admin permission changes will report unsupported instead of claiming success")
        }
    }

    private fun unsupported(): Result<Unit> = Result.failure(
        UnsupportedOperationException("LuckPerms editing is not supported; set admin.permissions.use-luckperms to false to use online-only Bukkit grants")
    )

    fun grantPermission(playerId: UUID, permission: String, temporary: Boolean = false, duration: Long? = null): Result<Unit> {
        if (luckPermsSelected) return unsupported()
        if (permission.isBlank()) return Result.failure(IllegalArgumentException("Permission cannot be empty"))
        if (temporary && (duration == null || duration <= 0 ||
                duration > (Long.MAX_VALUE - System.currentTimeMillis()) / 1000L ||
                duration > Long.MAX_VALUE / 20L)) {
            return Result.failure(IllegalArgumentException("Temporary grants need a valid positive duration in seconds"))
        }
        val player = Bukkit.getPlayer(playerId)
            ?: return Result.failure(IllegalArgumentException("Player must be online for runtime permission grants"))
        return runCatching {
            if (temporary) {
                val expiry = System.currentTimeMillis() + duration!! * 1000L
                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    if (temporaryPermissions[playerId]?.get(permission) == expiry) revokePermission(playerId, permission)
                }, duration * 20L)
                val attachment = permissionAttachments.getOrPut(playerId) { player.addAttachment(plugin) }
                attachment.setPermission(permission, true)
                temporaryPermissions.getOrPut(playerId) { mutableMapOf() }[permission] = expiry
            } else {
                val attachment = permissionAttachments.getOrPut(playerId) { player.addAttachment(plugin) }
                attachment.setPermission(permission, true)
                temporaryPermissions[playerId]?.remove(permission)
            }
        }
    }

    fun revokePermission(playerId: UUID, permission: String): Result<Unit> {
        if (luckPermsSelected) return unsupported()
        val attachment = permissionAttachments[playerId]
            ?: return Result.failure(IllegalArgumentException("No runtime grants for this player"))
        if (attachment.permissions[permission] != true) {
            return Result.failure(IllegalArgumentException("This plugin did not grant that permission; cannot revoke another plugin's grant"))
        }
        return runCatching {
            attachment.unsetPermission(permission)
            temporaryPermissions[playerId]?.remove(permission)
        }
    }

    /** Effective permissions are only knowable while the player is online. */
    fun checkPermission(playerId: UUID, permission: String): Boolean =
        Bukkit.getPlayer(playerId)?.hasPermission(permission) ?: false

    /** Only the runtime grants owned by this plugin, not the full permission tree. */
    fun listPermissions(playerId: UUID): Set<String> =
        permissionAttachments[playerId]?.permissions?.filterValues { it }?.keys?.toSet() ?: emptySet()

    fun assignGroup(playerId: UUID, group: String): Result<Unit> = Result.failure(
        UnsupportedOperationException("Group assignment is not supported; use your permission plugin to manage groups")
    )

    fun cleanupExpiredPermissions() {
        val now = System.currentTimeMillis()
        temporaryPermissions.toMap().forEach { (playerId, permissions) ->
            permissions.toMap().filterValues { it <= now }.keys.forEach { revokePermission(playerId, it) }
        }
    }

    fun removePlayerAttachments(playerId: UUID) {
        permissionAttachments.remove(playerId)?.remove()
        temporaryPermissions.remove(playerId)
    }

    fun shutdown() {
        permissionAttachments.keys.toList().forEach(::removePlayerAttachments)
    }
}
