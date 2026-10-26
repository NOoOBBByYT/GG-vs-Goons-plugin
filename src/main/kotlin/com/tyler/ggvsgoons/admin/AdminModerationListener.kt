package com.tyler.ggvsgoons.admin

import com.tyler.ggvsgoons.GGvGPlugin
import org.bukkit.ChatColor
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.entity.Player

/** All moderation checks run on the main thread; records are kept for reconnects during this session. */
class AdminModerationListener(
    private val plugin: GGvGPlugin,
    private val moderation: ModerationManager,
    private val permissions: PermissionManager
) : Listener {
    private fun frozen(player: Player) = moderation.isPlayerFrozen(player.uniqueId)

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        val to = event.to ?: return
        if (frozen(event.player) && (event.from.x != to.x || event.from.y != to.y || event.from.z != to.z)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) { if (frozen(event.player)) event.isCancelled = true }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInteractEntity(event: PlayerInteractEntityEvent) { if (frozen(event.player)) event.isCancelled = true }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) { if (frozen(event.player)) event.isCancelled = true }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) { if (frozen(event.player)) event.isCancelled = true }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) { if (frozen(event.player)) event.isCancelled = true }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPickup(event: EntityPickupItemEvent) {
        if (event.entity is Player && frozen(event.entity as Player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInventory(event: InventoryClickEvent) {
        if (event.whoClicked is Player && frozen(event.whoClicked as Player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.whoClicked is Player && frozen(event.whoClicked as Player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDamage(event: EntityDamageByEntityEvent) {
        if (event.damager is Player && frozen(event.damager as Player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        val label = event.message.substringBefore(' ').removePrefix("/")
        val canonical = moderation.resolveCommand(label).getOrNull()
        val adminCommand = plugin.getCommand(label.lowercase().let {
            if (it.substringBefore(':') == plugin.name.lowercase()) it.substringAfter(':') else it
        })?.name?.equals("ggadmin", ignoreCase = true) == true
        if (frozen(event.player) && !(adminCommand && event.player.hasPermission("ggvgoons.admin.use"))) {
            event.isCancelled = true
            event.player.sendMessage("${ChatColor.RED}You cannot use commands while frozen.")
            return
        }
        if (canonical == null || !moderation.isCommandDisabled(canonical, event.player.uniqueId)) return
        if (plugin.config.getBoolean("admin.commands.allow-admin-bypass", true) &&
            event.player.hasPermission("ggvgoons.admin.command")) return
        event.isCancelled = true
        val restriction = moderation.getCommandRestriction(canonical)
        val message = restriction?.reason ?: plugin.config.getString("admin.commands.custom-disable-message", "&cThis command is currently disabled.")!!
        event.player.sendMessage(ChatColor.translateAlternateColorCodes('&', message))
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        permissions.removePlayerAttachments(event.player.uniqueId)
    }
}
