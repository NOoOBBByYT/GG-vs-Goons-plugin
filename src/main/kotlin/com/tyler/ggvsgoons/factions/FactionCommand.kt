package com.tyler.ggvsgoons.factions

import com.tyler.ggvsgoons.GGvGPlugin
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Handles all /faction commands
 */
class FactionCommand(
    private val plugin: GGvGPlugin,
    private val manager: FactionManager
) : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("${ChatColor.RED}This command can only be used by players")
            return true
        }
        
        // Check if factions are enabled
        if (!plugin.config.getBoolean("factions.enabled", true)) {
            sender.sendMessage("${ChatColor.RED}The faction system is currently disabled")
            return true
        }
        
        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }
        
        when (args[0].lowercase()) {
            "create" -> handleCreate(sender, args)
            "disband" -> handleDisband(sender)
            "info" -> handleInfo(sender, args)
            "list" -> handleList(sender)
            "invite" -> handleInvite(sender, args)
            "kick" -> handleKick(sender, args)
            "accept" -> handleAccept(sender, args)
            "decline" -> handleDecline(sender, args)
            "promote" -> handlePromote(sender, args)
            "demote" -> handleDemote(sender, args)
            "transfer" -> handleTransfer(sender, args)
            "leave" -> handleLeave(sender)
            "home" -> handleHome(sender)
            "sethome" -> handleSetHome(sender)
            "chat", "c" -> handleChat(sender, args)
            "help" -> sendHelp(sender)
            else -> {
                sender.sendMessage("${ChatColor.RED}Unknown subcommand. Use /faction help for a list of commands")
            }
        }
        
        return true
    }
    
    private fun handleCreate(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage("${ChatColor.RED}Usage: /faction create <name>")
            return
        }
        
        // Check permission
        if (!player.hasPermission("ggvgoons.faction.create")) {
            player.sendMessage("${ChatColor.RED}You don't have permission to create factions")
            return
        }
        
        // Check if player is on a team
        val team = plugin.teams.manager.getPlayerTeam(player.uniqueId)
        if (team == null) {
            player.sendMessage("${ChatColor.RED}You must be on a team to create a faction")
            return
        }
        
        val name = args[1]
        val result = manager.createFaction(name, player.uniqueId, team.id)
        
        result.onSuccess { faction ->
            player.sendMessage("${ChatColor.GREEN}Successfully created faction ${ChatColor.GOLD}${faction.name}${ChatColor.GREEN}!")
            player.sendMessage("${ChatColor.GRAY}You are now the leader of this faction")
        }.onFailure { error ->
            player.sendMessage("${ChatColor.RED}Failed to create faction: ${error.message}")
        }
    }
    
    private fun handleDisband(player: Player) {
        val faction = manager.getFactionByPlayer(player.uniqueId)
        if (faction == null) {
            player.sendMessage("${ChatColor.RED}You are not in a faction")
            return
        }
        
        val result = manager.disbandFaction(faction.id, player.uniqueId)
        
        result.onSuccess {
            // Notify all members
            faction.members.forEach { memberId ->
                Bukkit.getPlayer(memberId)?.sendMessage("${ChatColor.RED}Your faction ${ChatColor.GOLD}${faction.name}${ChatColor.RED} has been disbanded")
            }
        }.onFailure { error ->
            player.sendMessage("${ChatColor.RED}${error.message}")
        }
    }
    
    private fun handleInfo(player: Player, args: Array<out String>) {
        val faction = if (args.size > 1) {
            // Look up faction by name
            val factionName = args[1]
            manager.getAllFactions().find { it.name.equals(factionName, ignoreCase = true) }
        } else {
            // Show player's own faction
            manager.getFactionByPlayer(player.uniqueId)
        }
        
        if (faction == null) {
            player.sendMessage("${ChatColor.RED}Faction not found")
            return
        }
        
        player.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}=== Faction Info ===")
        player.sendMessage("${ChatColor.YELLOW}Name: ${ChatColor.WHITE}${faction.name}")
        player.sendMessage("${ChatColor.YELLOW}Team: ${ChatColor.WHITE}${faction.teamId.uppercase()}")
        
        val leader = Bukkit.getOfflinePlayer(faction.leaderId)
        player.sendMessage("${ChatColor.YELLOW}Leader: ${ChatColor.WHITE}${leader.name ?: "Unknown"}")
        
        player.sendMessage("${ChatColor.YELLOW}Members: ${ChatColor.WHITE}${faction.members.size}/${faction.maxMembers}")
        
        if (faction.officers.isNotEmpty()) {
            val officerNames = faction.officers.mapNotNull { Bukkit.getOfflinePlayer(it).name }
            player.sendMessage("${ChatColor.YELLOW}Officers: ${ChatColor.WHITE}${officerNames.joinToString(", ")}")
        }
        
        if (faction.description.isNotEmpty()) {
            player.sendMessage("${ChatColor.YELLOW}Description: ${ChatColor.WHITE}${faction.description}")
        }
        
        val stats = manager.getFactionStats(faction.id)
        if (stats != null) {
            player.sendMessage("${ChatColor.YELLOW}Kills: ${ChatColor.WHITE}${stats.totalKills} ${ChatColor.YELLOW}Deaths: ${ChatColor.WHITE}${stats.totalDeaths} ${ChatColor.YELLOW}K/D: ${ChatColor.WHITE}${"%.2f".format(stats.getKDR())}")
            player.sendMessage("${ChatColor.YELLOW}Prisoners Captured: ${ChatColor.WHITE}${stats.prisonersCaptured}")
        }
        
        val createdDate = java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(faction.createdAt))
        player.sendMessage("${ChatColor.YELLOW}Created: ${ChatColor.WHITE}$createdDate")
    }
    
    private fun handleList(player: Player) {
        val team = plugin.teams.manager.getPlayerTeam(player.uniqueId)
        if (team == null) {
            player.sendMessage("${ChatColor.RED}You must be on a team to view factions")
            return
        }
        
        val factions = manager.getFactionsByTeam(team.id)
        
        if (factions.isEmpty()) {
            player.sendMessage("${ChatColor.YELLOW}No factions exist on your team yet")
            return
        }
        
        player.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}=== ${team.id.uppercase()} Factions ===")
        factions.forEach { faction ->
            val memberCount = faction.members.size
            val leaderName = Bukkit.getOfflinePlayer(faction.leaderId).name ?: "Unknown"
            player.sendMessage("${ChatColor.YELLOW}${faction.name} ${ChatColor.GRAY}($memberCount members) ${ChatColor.WHITE}- Leader: $leaderName")
        }
    }
    
    private fun handleInvite(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage("${ChatColor.RED}Usage: /faction invite <player>")
            return
        }
        
        val faction = manager.getFactionByPlayer(player.uniqueId)
        if (faction == null) {
            player.sendMessage("${ChatColor.RED}You are not in a faction")
            return
        }
        
        val targetName = args[1]
        val target = Bukkit.getPlayer(targetName)
        if (target == null) {
            player.sendMessage("${ChatColor.RED}Player not found or not online")
            return
        }
        
        val result = manager.invitePlayer(faction.id, player.uniqueId, target.uniqueId)
        
        result.onSuccess {
            player.sendMessage("${ChatColor.GREEN}Invited ${ChatColor.GOLD}${target.name}${ChatColor.GREEN} to your faction")
            target.sendMessage("${ChatColor.GREEN}You have been invited to join ${ChatColor.GOLD}${faction.name}")
            target.sendMessage("${ChatColor.GRAY}Use ${ChatColor.WHITE}/faction accept ${faction.name}${ChatColor.GRAY} to join")
        }.onFailure { error ->
            player.sendMessage("${ChatColor.RED}${error.message}")
        }
    }
    
    private fun handleKick(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage("${ChatColor.RED}Usage: /faction kick <player>")
            return
        }
        
        val faction = manager.getFactionByPlayer(player.uniqueId)
        if (faction == null) {
            player.sendMessage("${ChatColor.RED}You are not in a faction")
            return
        }
        
        val targetName = args[1]
        val target = Bukkit.getOfflinePlayer(targetName)
        
        val result = manager.kickMember(faction.id, player.uniqueId, target.uniqueId)
        
        result.onSuccess {
            player.sendMessage("${ChatColor.GREEN}Kicked ${ChatColor.GOLD}${target.name}${ChatColor.GREEN} from the faction")
            Bukkit.getPlayer(target.uniqueId)?.sendMessage("${ChatColor.RED}You have been kicked from ${ChatColor.GOLD}${faction.name}")
        }.onFailure { error ->
            player.sendMessage("${ChatColor.RED}${error.message}")
        }
    }
    
    private fun handleAccept(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage("${ChatColor.RED}Usage: /faction accept <faction>")
            return
        }
        
        val factionName = args[1]
        val faction = manager.getAllFactions().find { it.name.equals(factionName, ignoreCase = true) }
        
        if (faction == null) {
            player.sendMessage("${ChatColor.RED}Faction not found")
            return
        }
        
        val result = manager.acceptInvite(player.uniqueId, faction.id)
        
        result.onSuccess {
            player.sendMessage("${ChatColor.GREEN}You have joined ${ChatColor.GOLD}${faction.name}${ChatColor.GREEN}!")
            
            // Notify faction members
            faction.members.forEach { memberId ->
                Bukkit.getPlayer(memberId)?.sendMessage("${ChatColor.GOLD}${player.name}${ChatColor.GREEN} has joined the faction")
            }
        }.onFailure { error ->
            player.sendMessage("${ChatColor.RED}${error.message}")
        }
    }
    
    private fun handleDecline(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage("${ChatColor.RED}Usage: /faction decline <faction>")
            return
        }
        
        val factionName = args[1]
        val faction = manager.getAllFactions().find { it.name.equals(factionName, ignoreCase = true) }
        
        if (faction == null) {
            player.sendMessage("${ChatColor.RED}Faction not found")
            return
        }
        
        val result = manager.declineInvite(player.uniqueId, faction.id)
        
        result.onSuccess {
            player.sendMessage("${ChatColor.YELLOW}Declined invitation to ${ChatColor.GOLD}${faction.name}")
        }.onFailure { error ->
            player.sendMessage("${ChatColor.RED}${error.message}")
        }
    }
    
    private fun handlePromote(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage("${ChatColor.RED}Usage: /faction promote <player>")
            return
        }
        
        val faction = manager.getFactionByPlayer(player.uniqueId)
        if (faction == null) {
            player.sendMessage("${ChatColor.RED}You are not in a faction")
            return
        }
        
        val targetName = args[1]
        val target = Bukkit.getOfflinePlayer(targetName)
        
        val result = manager.promoteMember(faction.id, player.uniqueId, target.uniqueId)
        
        result.onSuccess {
            player.sendMessage("${ChatColor.GREEN}Promoted ${ChatColor.GOLD}${target.name}${ChatColor.GREEN} to officer")
            Bukkit.getPlayer(target.uniqueId)?.sendMessage("${ChatColor.GREEN}You have been promoted to officer in ${ChatColor.GOLD}${faction.name}")
        }.onFailure { error ->
            player.sendMessage("${ChatColor.RED}${error.message}")
        }
    }
    
    private fun handleDemote(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage("${ChatColor.RED}Usage: /faction demote <player>")
            return
        }
        
        val faction = manager.getFactionByPlayer(player.uniqueId)
        if (faction == null) {
            player.sendMessage("${ChatColor.RED}You are not in a faction")
            return
        }
        
        val targetName = args[1]
        val target = Bukkit.getOfflinePlayer(targetName)
        
        val result = manager.demoteMember(faction.id, player.uniqueId, target.uniqueId)
        
        result.onSuccess {
            player.sendMessage("${ChatColor.GREEN}Demoted ${ChatColor.GOLD}${target.name}${ChatColor.GREEN} to member")
            Bukkit.getPlayer(target.uniqueId)?.sendMessage("${ChatColor.YELLOW}You have been demoted to member in ${ChatColor.GOLD}${faction.name}")
        }.onFailure { error ->
            player.sendMessage("${ChatColor.RED}${error.message}")
        }
    }
    
    private fun handleTransfer(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage("${ChatColor.RED}Usage: /faction transfer <player>")
            return
        }
        
        val faction = manager.getFactionByPlayer(player.uniqueId)
        if (faction == null) {
            player.sendMessage("${ChatColor.RED}You are not in a faction")
            return
        }
        
        val targetName = args[1]
        val target = Bukkit.getOfflinePlayer(targetName)
        
        val result = manager.transferLeadership(faction.id, player.uniqueId, target.uniqueId)
        
        result.onSuccess {
            player.sendMessage("${ChatColor.GREEN}Transferred leadership to ${ChatColor.GOLD}${target.name}")
            Bukkit.getPlayer(target.uniqueId)?.sendMessage("${ChatColor.GREEN}You are now the leader of ${ChatColor.GOLD}${faction.name}")
            
            // Notify other members
            faction.members.forEach { memberId ->
                if (memberId != player.uniqueId && memberId != target.uniqueId) {
                    Bukkit.getPlayer(memberId)?.sendMessage("${ChatColor.GOLD}${target.name}${ChatColor.YELLOW} is now the faction leader")
                }
            }
        }.onFailure { error ->
            player.sendMessage("${ChatColor.RED}${error.message}")
        }
    }
    
    private fun handleLeave(player: Player) {
        val result = manager.leaveFaction(player.uniqueId)
        
        result.onSuccess {
            player.sendMessage("${ChatColor.YELLOW}You have left your faction")
        }.onFailure { error ->
            player.sendMessage("${ChatColor.RED}${error.message}")
        }
    }
    
    private fun handleHome(player: Player) {
        if (!plugin.config.getBoolean("factions.homes.enabled", true)) {
            player.sendMessage("${ChatColor.RED}Faction homes are disabled")
            return
        }
        
        val faction = manager.getFactionByPlayer(player.uniqueId)
        if (faction == null) {
            player.sendMessage("${ChatColor.RED}You are not in a faction")
            return
        }
        
        val home = faction.homeLocation
        if (home == null) {
            player.sendMessage("${ChatColor.RED}Your faction has not set a home location")
            return
        }
        
        player.teleport(home)
        player.sendMessage("${ChatColor.GREEN}Teleported to faction home")
    }
    
    private fun handleSetHome(player: Player) {
        if (!plugin.config.getBoolean("factions.homes.enabled", true)) {
            player.sendMessage("${ChatColor.RED}Faction homes are disabled")
            return
        }
        
        val faction = manager.getFactionByPlayer(player.uniqueId)
        if (faction == null) {
            player.sendMessage("${ChatColor.RED}You are not in a faction")
            return
        }
        
        val result = manager.setFactionHome(faction.id, player.uniqueId, player.location)
        
        result.onSuccess {
            player.sendMessage("${ChatColor.GREEN}Faction home set to your current location")
        }.onFailure { error ->
            player.sendMessage("${ChatColor.RED}${error.message}")
        }
    }
    
    private fun handleChat(player: Player, args: Array<out String>) {
        if (!plugin.config.getBoolean("factions.chat.enabled", true)) {
            player.sendMessage("${ChatColor.RED}Faction chat is disabled")
            return
        }
        
        if (args.size < 2) {
            player.sendMessage("${ChatColor.RED}Usage: /faction chat <message>")
            return
        }
        
        val faction = manager.getFactionByPlayer(player.uniqueId)
        if (faction == null) {
            player.sendMessage("${ChatColor.RED}You are not in a faction")
            return
        }
        
        val message = args.drop(1).joinToString(" ")
        val prefix = plugin.config.getString("factions.chat.prefix", "[FACTION] ")
        val formattedMessage = "$prefix${faction.color}${player.name}: ${ChatColor.WHITE}$message"
        
        // Send to all faction members
        faction.members.forEach { memberId ->
            Bukkit.getPlayer(memberId)?.sendMessage(formattedMessage)
        }
    }
    
    private fun sendHelp(player: Player) {
        player.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}=== Faction Commands ===")
        player.sendMessage("${ChatColor.YELLOW}/faction create <name> ${ChatColor.GRAY}- Create a new faction")
        player.sendMessage("${ChatColor.YELLOW}/faction disband ${ChatColor.GRAY}- Disband your faction (leader only)")
        player.sendMessage("${ChatColor.YELLOW}/faction info [faction] ${ChatColor.GRAY}- View faction information")
        player.sendMessage("${ChatColor.YELLOW}/faction list ${ChatColor.GRAY}- List all factions on your team")
        player.sendMessage("${ChatColor.YELLOW}/faction invite <player> ${ChatColor.GRAY}- Invite a player")
        player.sendMessage("${ChatColor.YELLOW}/faction kick <player> ${ChatColor.GRAY}- Remove a member")
        player.sendMessage("${ChatColor.YELLOW}/faction accept <faction> ${ChatColor.GRAY}- Accept an invitation")
        player.sendMessage("${ChatColor.YELLOW}/faction decline <faction> ${ChatColor.GRAY}- Decline an invitation")
        player.sendMessage("${ChatColor.YELLOW}/faction promote <player> ${ChatColor.GRAY}- Promote to officer")
        player.sendMessage("${ChatColor.YELLOW}/faction demote <player> ${ChatColor.GRAY}- Demote from officer")
        player.sendMessage("${ChatColor.YELLOW}/faction transfer <player> ${ChatColor.GRAY}- Transfer leadership")
        player.sendMessage("${ChatColor.YELLOW}/faction leave ${ChatColor.GRAY}- Leave your faction")
        player.sendMessage("${ChatColor.YELLOW}/faction home ${ChatColor.GRAY}- Teleport to faction home")
        player.sendMessage("${ChatColor.YELLOW}/faction sethome ${ChatColor.GRAY}- Set faction home")
        player.sendMessage("${ChatColor.YELLOW}/faction chat <message> ${ChatColor.GRAY}- Send faction chat")
    }
    
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String>? {
        if (sender !is Player) return null
        
        return when (args.size) {
            1 -> listOf("create", "disband", "info", "list", "invite", "kick", "accept", "decline", 
                       "promote", "demote", "transfer", "leave", "home", "sethome", "chat", "help")
                .filter { it.startsWith(args[0].lowercase()) }
            
            2 -> when (args[0].lowercase()) {
                "invite", "kick", "promote", "demote", "transfer" -> 
                    Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }
                "accept", "decline", "info" -> 
                    manager.getAllFactions().map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }
                else -> null
            }
            
            else -> null
        }
    }
}
