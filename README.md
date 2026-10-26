<<<<<<< HEAD
# GG vs Goons
=======

# Development Paused for a week
# GG-vs-Goons-plugin
>>>>>>> 52a70a973bcd4c705989acdc26f792457831e352

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

<<<<<<< HEAD
 Ransom/trading, Discord bridge and bot, territory systems, and faction alliances are not implemented. On-server regression checks remain unrun; follow the [staging-server checklist](plans/on-server-regression-checklist.md) before deployment. The Java unit suite cannot prove behavior against every Paper/Arclight build or combat mod. Legacy `AsyncPlayerChatEvent` team chat is scheduled onto the main thread for Bukkit access, but server-specific chat plugins may still require compatibility testing.
=======
**Permissions:**
- `ggvgoons.team.use` - Basic team commands (default: true)
- `ggvgoons.team.admin` - Admin commands like setspawn (default: op)

**Arclight Note**: Scoreboard teams work reliably on Arclight

#### War Prisoner System
Capture defeated enemies as prisoners with a consent-based system.

**Commands:**
- `/warprisoner <player>` - Send a capture offer to a player (requires `ggvgoons.warprisoner.capture`)
- `/freeprisoner <player>` - Release your prisoner, restores their original gamemode (requires `ggvgoons.warprisoner.free`)
- `/executeprisoner <player>` - End imprisonment without restoring gamemode (requires `ggvgoons.warprisoner.execute`)
- `/listprisoners` - View all active prisoners and their captors (requires `ggvgoons.warprisoner.list`)

**How it works:**
1. Captor uses `/warprisoner <target>` to send a clickable capture offer
2. Target sees: `[Accept]` or `[Decline]` buttons in chat
3. **Accept** → Target becomes a prisoner, switched to Adventure mode
4. **Decline** → Nothing happens; it's on the captor to kill or free them
5. Offers automatically expire after 60 seconds (configurable in `config.yml`)
6. Captor can later `/freeprisoner` to release or `/executeprisoner` to end the arrangement
7. Prisoner state persists across server restarts

**Team Integration:**
- ✅ Players must be on a team to capture others
- ✅ Can only capture members of the opposing team
- ✅ Cannot capture teammates or players without a team

**Current Limitations:**
- No movement restrictions (prisoners can walk away - future event listeners will add boundaries)

### Planned Modules

#### Ransom/Trading Module (Phase 3 - High Priority)
Prisoner ransom system with item trading for release.

**Features:**
- Chest GUI with green/red confirmation panes
- Both captors and prisoners can initiate trades
- Configurable item restrictions (blacklist/whitelist)
- Auto-release prisoner on successful trade
- Trade timeout and persistence

**Commands:**
- `/ransom offer <player>` - Initiate a ransom trade
- `/ransom cancel` - Cancel your pending trade
- `/ransom list` - View all active trades

**Detailed Plan**: [`plans/phase3-ransom-trading-system-plan.md`](plans/phase3-ransom-trading-system-plan.md)

#### Factions Module (Phase 4 - Medium Priority)
Sub-groups within teams (2-3 factions per team) with optional enable/disable.

**Features:**
- Factions as sub-groups within GG/Goons teams
- Leadership hierarchy (leader, officers, members)
- Faction homes and teleportation
- Faction chat channels
- Optional alliance system
- Complete enable/disable toggle

**Commands:**
- `/faction create|disband|info|list|leave`
- `/faction invite|kick|promote|demote`
- `/faction home|sethome`
- `/faction chat <message>` or `/fc <message>`

**Detailed Plan**: [`plans/phase4-factions-system-plan.md`](plans/phase4-factions-system-plan.md)

#### Discord Integration Module (Phase 5 - High Priority)
Full Discord bot integration for communication and event management.

**Features:**
- War scheduling with RSVP system
- Real-time prisoner notifications
- Bidirectional team/faction chat bridge
- Statistics and leaderboards
- Server status monitoring
- Event management system

**Discord Commands:**
- `/war schedule|list|cancel` - War event management
- `/stats player|team|faction` - View statistics
- `/leaderboard kills|captures|ransoms` - Leaderboards
- `/server status|players` - Server information

**Detailed Plan**: [`plans/phase5-discord-integration-plan.md`](plans/phase5-discord-integration-plan.md)

#### Admin Configuration Module (Phase 6 - High Priority)
Command-based admin system for real-time configuration and moderation.

**Features:**
- Runtime configuration changes (no restart needed)
- Module enable/disable
- Command management (disable/enable commands)
- Permission management
- Player moderation tools (freeze, force-free, etc.)
- Comprehensive audit logging
- Backup/restore system

**Commands:**
- `/ggadmin module <list|enable|disable|reload>`
- `/ggadmin config <get|set|list|reset>`
- `/ggadmin command <disable|enable|status>`
- `/ggadmin permission <grant|revoke|check>`
- `/ggadmin player <freeze|freeprisoner|resetcooldowns>`
- `/ggadmin audit <view|search|export>`
- `/ggadmin backup <create|restore|list>`

**Detailed Plan**: [`plans/phase6-admin-config-system-plan.md`](plans/phase6-admin-config-system-plan.md)

#### Additional Future Ideas
- Scoreboard module with live statistics
- Territory control system
- Bounty system
- Combat logging and analytics
- Economy system
- Achievement system
- War zones with special rules
- Prisoner minigames

**Master Plan**: [`plans/phase3-6-master-plan.md`](plans/phase3-6-master-plan.md)

## Installation

### Prerequisites
- Arclight 1.21.x server (or Paper 1.21.x)
- Java 21 or higher
- (Optional) LuckPerms for permission management

### Steps
1. Download the latest release or build from source (see Build section)
2. Place `GGvGoons-1.0.0.jar` in your server's `plugins/` folder
3. Restart the server
4. (Optional) Configure permissions using LuckPerms or your permission plugin
5. Test with `/warprisoner <player>` command

### Arclight-Specific Setup
- Ensure Arclight is fully loaded before testing the plugin
- Check console for any Bukkit API compatibility warnings
- Test prisoner capture with your combat mods active to verify event handling
- If clickable chat messages don't work, check for conflicting Forge chat mods

## Configuration

### Permission Nodes
The plugin now includes a full permission system. All permissions default to OP status but can be managed via LuckPerms or any permission plugin:

```yaml
permissions:
  # War Prisoner System
  ggvgoons.warprisoner.capture:
    description: Allows capturing players as prisoners
    default: op
  ggvgoons.warprisoner.free:
    description: Allows freeing prisoners
    default: op
  ggvgoons.warprisoner.execute:
    description: Allows executing prisoners
    default: op
  ggvgoons.warprisoner.list:
    description: Allows listing all active prisoners
    default: op
  
  # Teams System
  ggvgoons.team.use:
    description: Allows using basic team commands
    default: true
  ggvgoons.team.admin:
    description: Allows using team admin commands (setspawn)
    default: op
  
  # Admin Permission (grants all)
  ggvgoons.admin:
    description: Full administrative access (grants all permissions)
    default: op
```

**Note:** Permission enforcement can be disabled in `config.yml` by setting `permissions.enabled: false` for OP-only mode.

### Plugin Configuration
The plugin generates a `config.yml` on first run with the following options:

```yaml
# GGvGoons Configuration

# Prisoner System Settings
prisoner:
  # How long capture offers remain valid (seconds)
  # Set to 0 to disable expiry
  offer-expiry-timeout: 60
  
  # Whether to persist prisoner state across restarts
  enable-persistence: true

# Team System Settings
teams:
  # Allow players to switch teams after joining
  allow-team-switching: true
  
  # Cooldown between team switches (seconds)
  # Set to 0 to disable cooldown
  team-switch-cooldown: 300
  
  # Team chat settings
  chat:
    enabled: true
    prefix: "[TEAM] "
    format: "{player}: {message}"

# Permission Settings
permissions:
  # Whether to enforce permissions (false = OP-only mode)
  enabled: true
```

**Configuration Options:**

**Prisoner System:**
- `prisoner.offer-expiry-timeout`: Time in seconds before capture offers expire (default: 60, set to 0 to disable)
- `prisoner.enable-persistence`: Whether to save prisoner state to `prisoners.yml` on shutdown (default: true)

**Teams System:**
- `teams.allow-team-switching`: Whether players can switch teams after joining (default: true)
- `teams.team-switch-cooldown`: Cooldown in seconds between team switches (default: 300, set to 0 to disable)
- `teams.chat.enabled`: Enable team chat channels (default: true)
- `teams.chat.prefix`: Prefix for team chat messages (default: "[TEAM] ")
- `teams.chat.format`: Format string for team messages (default: "{player}: {message}")

**General:**
- `permissions.enabled`: Whether to enforce permission nodes or use OP-only mode (default: true)

## For Developers

### Module System Architecture

```
graph TB
    A[GGvGPlugin] --> B[Module System]
    B --> C[WarPrisonerModule]
    B --> D[TeamsModule]
    B --> E[ScoreboardModule - Planned]
    B --> F[TerritoryModule - Planned]
    
    C --> G[PrisonerManager]
    G --> H[Pending Offers Map]
    G --> I[Active Prisoners Map]
    
    C --> J[Command Executors]
    J --> K[/warprisoner]
    J --> L[/freeprisoner]
    J --> M[/executeprisoner]
    J --> N[/warprisoneraccept]
    J --> O[/warprisonerdecline]
    
    D --> P[TeamManager]
    D --> Q[TeamCommand]
    D --> R[TeamPersistence]
    P --> S[Scoreboard Teams]
    P --> T[Team Validation]
    
    style C fill:#90EE90
    style D fill:#90EE90
    style E fill:#FFB6C1
    style F fill:#FFB6C1
```

### Adding a New Module

1. Create a new class under `src/main/kotlin/com/tyler/ggvsgoons/commands/` implementing [`GGvGModule`](src/main/kotlin/com/tyler/ggvsgoons/GGvGPlugin.kt:11):

```kotlin
class TerritoryModule(private val plugin: GGvGPlugin) : GGvGModule {
    val manager = TerritoryManager(plugin)
    
    override fun register(plugin: GGvGPlugin) {
        plugin.getCommand("claimterritory")?.setExecutor(ClaimTerritoryCommand(this, plugin))
        // Register other commands...
    }
}
```

2. In [`GGvGPlugin.onEnable()`](src/main/kotlin/com/tyler/ggvsgoons/GGvGPlugin.kt:22), instantiate it and add it to `modules`:

```kotlin
val territory = TerritoryModule(this)
modules += territory
```

3. Add your commands to [`plugin.yml`](src/main/resources/plugin.yml:1)

4. Build and drop the jar back in `plugins/`

**That's it!** No shared state to wire up unless two modules need to talk to each other (e.g., blocking territory claims while someone's a prisoner), in which case just expose what you need as a public property, the way [`WarPrisonerModule.manager`](src/main/kotlin/com/tyler/ggvsgoons/commands/WarPrisonerModule.kt:22) is exposed.

### Testing on Arclight

1. Set up an Arclight 1.21.x test server
2. Install your combat/PvP mods
3. Build the plugin: `./gradlew build`
4. Copy `build/libs/GGvGoons-1.0.0.jar` to `plugins/` folder
5. Start server and test all commands
6. Verify prisoner capture works with mod combat
7. Check console for Bukkit API warnings
8. Test that Adventure mode restrictions work with your mods

### Event Handling Best Practices

For Arclight compatibility:
- Always check if events are cancellable before cancelling
- Use `@EventHandler(priority = EventPriority.HIGH)` for important listeners
- Add null checks for player objects
- Log warnings when Forge events might be needed instead
- Test with actual Forge mods to verify events fire correctly

## Build

Requires JDK 21.

```bash
./gradlew build
```

On Windows: `gradlew.bat build`

Output jar: `build/libs/GGvGoons-1.0.0.jar` — drop it into your Paper or Arclight server's `plugins/` folder and restart.

## Roadmap & TODO

### Current Limitations
- [x] ~~**No persistence**~~ - ✅ **IMPLEMENTED** - prisoner state now persists across restarts via YAML
- [x] ~~**No team validation**~~ - ✅ **IMPLEMENTED** - players can only capture opposing team members
- [ ] **No movement restrictions** - prisoners can walk away in Adventure mode
- [x] ~~**No offer expiry**~~ - ✅ **IMPLEMENTED** - capture offers now expire after configurable timeout
- [x] ~~**No permission system**~~ - ✅ **IMPLEMENTED** - all commands now have permission nodes

### Development Phases

#### Phase 1: Core Stability ✅ **COMPLETE**
- [x] Add YAML persistence for prisoner state across restarts
- [x] Implement offer expiry with configurable timeout
- [x] Add permission nodes to all commands
- [x] Create `config.yml` for customization
- [x] Add `/listprisoners` command to see all active prisoners

#### Phase 2: Teams System ✅ **COMPLETE**
- [x] Implement Teams module with Bukkit Scoreboard integration
- [x] Add team validation to capture system (only capture opposing team)
- [x] Create team management commands (`/team join`, `/team leave`, etc.)
- [x] Add team-based spawn points
- [x] Implement team chat channels

#### Phase 3: Ransom/Trading System 📋 **PLANNED**
- [ ] Create RansomModule with chest GUI interface
- [ ] Implement trade initiation (both captor and prisoner)
- [ ] Add green/red confirmation pane system
- [ ] Configure item restrictions (blacklist/whitelist)
- [ ] Implement auto-release on successful trade
- [ ] Add trade persistence and timeout
- [ ] Create `/ransom` commands

**Estimated Time**: 2-3 weeks
**Detailed Plan**: [`plans/phase3-ransom-trading-system-plan.md`](plans/phase3-ransom-trading-system-plan.md)

#### Phase 4: Factions System 📋 **PLANNED**
- [ ] Create FactionsModule as sub-groups within teams
- [ ] Implement faction creation (2-3 per team limit)
- [ ] Add leadership hierarchy (leader/officers/members)
- [ ] Create faction home and teleportation system
- [ ] Implement faction chat channels
- [ ] Add enable/disable toggle
- [ ] Create `/faction` commands

**Estimated Time**: 3-4 weeks
**Detailed Plan**: [`plans/phase4-factions-system-plan.md`](plans/phase4-factions-system-plan.md)

#### Phase 5: Discord Integration 📋 **PLANNED**
- [ ] Create Discord bot from scratch using JDA
- [ ] Implement WebSocket communication layer
- [ ] Add war scheduling with RSVP system
- [ ] Create prisoner notification system
- [ ] Implement bidirectional chat bridge
- [ ] Add statistics and leaderboards
- [ ] Create server monitoring features

**Estimated Time**: 4-5 weeks
**Detailed Plan**: [`plans/phase5-discord-integration-plan.md`](plans/phase5-discord-integration-plan.md)

#### Phase 6: Admin Configuration System 📋 **PLANNED**
- [ ] Create AdminConfigModule
- [ ] Implement runtime configuration changes
- [ ] Add module enable/disable commands
- [ ] Create command management system
- [ ] Implement permission management
- [ ] Add player moderation tools
- [ ] Create audit logging system
- [ ] Implement backup/restore functionality

**Estimated Time**: 2-3 weeks
**Detailed Plan**: [`plans/phase6-admin-config-system-plan.md`](plans/phase6-admin-config-system-plan.md)

**Master Plan**: [`plans/phase3-6-master-plan.md`](plans/phase3-6-master-plan.md)

**Total Estimated Timeline**: 1

## Troubleshooting

### Arclight-Specific Issues

**Problem**: Commands not registering on startup  
**Cause**: Arclight loaded plugin before Bukkit API was ready  
**Solution**: Restart the server. If issue persists, check Arclight version compatibility

**Problem**: Clickable chat messages not working  
**Cause**: Forge chat mod overriding Bukkit chat system  
**Solution**: Disable chat formatting in the conflicting mod, or the plugin will need a `/tellraw` fallback

**Problem**: GameMode changes not applying to prisoners  
**Cause**: Combat mod overriding gamemode changes  
**Solution**: Check your combat mod's config for gamemode protection settings and disable them

**Problem**: Prisoner state lost on server restart
**Cause**: Persistence is disabled in config.yml
**Solution**: Enable persistence by setting `prisoner.enable-persistence: true` in `config.yml`

**Problem**: Prisoners can still break blocks in Adventure mode  
**Cause**: Some Forge mods may bypass Adventure mode restrictions  
**Solution**: Test with your specific mods. May need additional event listeners (planned for Phase 3)

### General Issues

**Problem**: "Player not found" error  
**Solution**: Ensure the target player is online and the name is spelled correctly (case-sensitive)

**Problem**: "Permission denied" when running commands  
**Solution**: Ensure you have OP status (`/op <yourname>`) or proper permissions via LuckPerms

**Problem**: Can't capture someone who's already a prisoner  
**Solution**: This is intentional - use `/freeprisoner` first, or have their captor release them

**Problem**: Accepted capture offer but nothing happened  
**Solution**: The captor may have logged off. Offers are cleared when captors disconnect

## Notes / Future Improvements

- ✅ ~~**Permissions**~~ - **IMPLEMENTED**: Full permission system with configurable enforcement and LuckPerms integration support
- ✅ ~~**Team validation**~~ - **IMPLEMENTED**: Players can only capture members of the opposing team
- ✅ ~~**Persistence**~~ - **IMPLEMENTED**: Prisoner state persists across restarts via YAML storage in `plugins/GGvGoons/prisoners.yml`
- ✅ ~~**Offer expiry**~~ - **IMPLEMENTED**: Capture offers automatically expire after configurable timeout (default 60 seconds)
- **Movement/inventory limits for prisoners**: Adventure mode alone won't fully cage someone. Consider a `PlayerMoveEvent` listener to bound them to a radius, or `PlayerInteractEvent` for tighter control. (Planned for future enhancements)
- **Ransom/Trading System**: Comprehensive prisoner trading system planned for Phase 3 with chest GUI interface
- **Factions System**: Sub-group system within teams planned for Phase 4 with optional enable/disable
- **Discord Integration**: Full Discord bot integration planned for Phase 5 with war scheduling, notifications, and chat bridging
- **Admin Configuration**: Runtime configuration system planned for Phase 6 for managing all plugin settings without restarts
- **Forge event integration**: For full Arclight compatibility with combat mods, may need to listen to Forge events in addition to Bukkit events

## Contributing

Contributions are welcome! When adding new modules:
1. Follow the existing module pattern
2. Keep modules self-contained
3. Document any cross-module dependencies
4. Test on both Paper and Arclight if possible
5. Update this README with new features

## AI
AI was used to contribute this project. This is only made for a short and fun server and not a serious project.
>>>>>>> 52a70a973bcd4c705989acdc26f792457831e352
