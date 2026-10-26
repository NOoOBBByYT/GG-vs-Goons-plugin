# GG vs Goons

A Kotlin plugin for Paper 1.21.1 (Java 21). The plugin builds against the Paper API. Arclight/Forge compatibility has **not** been verified; test modded combat and chat on a staging server before deploying.

## Build and install

Run `gradlew.bat build` on Windows or `./gradlew build` on other platforms. Put the resulting `build/libs/GGvGoons-1.2.0.jar` in the server's `plugins` directory, then fully restart. Run `gradlew.bat test` (or `./gradlew test`) to run the unit tests. The authoritative defaults are in [`config.yml`](src/main/resources/config.yml); server-specific settings belong in the plugin's generated config file.

## Implemented features

- **Teams:** `/team join <gg|goons>`, `leave`, `info`, `list`, `spawn`, `setspawn`, and `chat`. Membership and spawn locations persist. Teams use Bukkit scoreboard nametag colors (GG gold, Goons dark blue), disable teammate friendly fire, and support `/team chat` and `@team <message>`. Switching has a configurable cooldown. A faction leader must transfer leadership or disband before leaving their team.
- **War prisoners:** `/warprisoner <player>` offers consensual capture of an online opponent; the target accepts or declines in chat. Offers expire and are rechecked on acceptance. Captured players use Adventure mode, including on rejoin/respawn. `/freeprisoner <player>` and `/executeprisoner <player>` both end captivity and restore the previous mode; **execute does not kill**. Release requires the target to be online. `/listprisoners` displays current captives. There is no confinement radius or inventory lock for prisoners.
- **Factions:** `/faction create|disband|info|list|invite|kick|accept|decline|promote|demote|transfer|leave|home|sethome|chat`, plus `/fc`. Factions are subgroups of teams; home teleports are **immediate**. There are no alliances, home warmups/cooldowns, or faction-size expansion.
- **Scoreboard and tab list:** Configurable sidebar and tab formatting. Team entries are mirrored onto personal scoreboards. Team headings in the tab header do **not** sort or group player entries. `/scoreboard toggle` toggles the personal sidebar; `/ggadmin scoreboard reload` and `/ggadmin tablist reload` refresh their configuration.
- **Admin:** `/ggadmin` offers module flags for factions/admin, supported runtime config changes, player freeze/unfreeze, team/faction removal, team-switch cooldown reset, command restrictions for this plugin, audit logs, and backups. Freeze blocks movement, interaction, inventory actions, and non-admin-recovery commands for the current server session. Command restrictions are in memory only. Admin backup restore validates and **queues** a restore for the **next full server start**, never a live overwrite; do not use `/reload` for restoration.

For the full command/permission declaration see [`plugin.yml`](src/main/resources/plugin.yml). Permission nodes are enforced by Bukkit even if an older generated config contains `permissions.enabled: false`; that old option is ignored.

### Runtime permissions

`/ggadmin permission grant|revoke` manages **this plugin's temporary Bukkit attachments for online players only**; grants disappear on logout/restart, and revoke only removes grants made by this plugin. `check` needs the player online and `list` reports only this plugin's runtime grants. Group assignment and editing LuckPerms data are **not implemented**. With `admin.permissions.use-luckperms: true` and LuckPerms installed, edits explicitly fail rather than claim success; leave the default `false` for online-only Bukkit grants, or use LuckPerms commands directly for persistent changes.

## Configuration and persistence

The keys in [`config.yml`](src/main/resources/config.yml) are the implemented defaults. Old keys such as `teams.chat.format`, `factions.creation.min-team-members`, faction expansion/home timers, `tablist.sort-order`, `admin.audit.enabled`, `admin.monitoring.*`, and `discord.*` are not implemented and are ignored if left in an existing generated config. Delete them manually if desired; upgrading does not erase existing configuration.

Most command and gameplay toggles read config at use time, but settings initialized during startup require editing the config file and a **full restart**: offer expiry, prisoner/faction persistence, audit retention/log flags, backup interval/max count, and permission backend selection. `/ggadmin config set` rejects those startup-only options rather than claiming an immediate change. Sidebar and tab settings can be refreshed with their respective `/ggadmin ... reload` commands. Disabling the faction module blocks its commands but retains its saved data.

Prisoners, teams and factions are saved as YAML in the plugin data directory. A malformed file aborts startup rather than silently replacing the original. Back up the directory before upgrades or manual edits. The historic design documents in [`plans/`](plans/) describe intentions, not guaranteed current functionality.

## Not implemented / caveats

 Ransom/trading, Discord bridge and bot, territory systems, and faction alliances are not implemented. On-server regression checks remain unrun; follow the [staging-server checklist](plans/on-server-regression-checklist.md) before deployment. The Java unit suite cannot prove behavior against every Paper/Arclight build or combat mod. Legacy `AsyncPlayerChatEvent` team chat is scheduled onto the main thread for Bukkit access, but server-specific chat plugins may still require compatibility testing.
