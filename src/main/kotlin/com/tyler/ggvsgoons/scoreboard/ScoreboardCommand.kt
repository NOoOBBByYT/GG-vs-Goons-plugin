package com.tyler.ggvsgoons.scoreboard

import com.tyler.ggvsgoons.GGvGPlugin
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ScoreboardCommand(private val plugin: GGvGPlugin) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("${ChatColor.RED}Only players can use the scoreboard command.")
            return true
        }
        if (args.firstOrNull()?.equals("toggle", true) == true) {
            if (!sender.hasPermission("ggvgoons.scoreboard.toggle")) {
                sender.sendMessage("${ChatColor.RED}You don't have permission to toggle the scoreboard.")
                return true
            }
            val enabled = plugin.scoreboard.manager.togglePersonalScoreboard(sender)
            sender.sendMessage(if (enabled) "${ChatColor.GREEN}Scoreboard enabled." else "${ChatColor.YELLOW}Scoreboard disabled.")
            return true
        }
        sender.sendMessage("${ChatColor.GOLD}/scoreboard toggle ${ChatColor.GRAY}- Toggle your scoreboard")
        return true
    }
}
