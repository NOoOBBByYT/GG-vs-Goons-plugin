# GG vs Goons Plugin - Code Review Fixes Plan

## Overview
This plan addresses all issues identified in the comprehensive code review of the GG vs Goons Minecraft plugin. Issues are prioritized by severity and organized into logical implementation phases.

---

## Phase 1: Critical Fixes (High Priority)

### 1.1 Fix Reflection-Based Faction Access in ModerationManager
**File:** `src/main/kotlin/com/tyler/ggvsgoons/admin/ModerationManager.kt`
**Lines:** 156-196, 208-222

**Problem:**
- Using reflection to access factions module is fragile and error-prone
- Methods `kickFromFaction()` and `getPlayerInfo()` use `Class.forName()` and field reflection
- Will break if class names or field names change
- Poor performance due to reflection overhead

**Solution:**
1. Add `FactionManager?` as constructor parameter to `ModerationManager`
2. Update `AdminConfigModule` to pass faction manager when creating `ModerationManager`
3. Replace reflection code with direct method calls:
   ```kotlin
   // Instead of reflection:
   val faction = factionManager?.getFactionByPlayer(playerId)
   
   // For kicking:
   factionManager?.leaveFaction(playerId)
   ```
4. Handle null case when factions module is disabled

**Files to Modify:**
- `src/main/kotlin/com/tyler/ggvsgoons/admin/ModerationManager.kt`
- `src/main/kotlin/com/tyler/ggvsgoons/admin/AdminConfigModule.kt`

**Testing:**
- Test with factions enabled
- Test with factions disabled
- Verify kick functionality works correctly

---

### 1.2 Implement Actual Stats Tracking in PlayerInfo
**File:** `src/main/kotlin/com/tyler/ggvsgoons/admin/ModerationManager.kt`
**Lines:** 228-239

**Problem:**
- `getPlayerInfo()` returns hardcoded zeros for kills, deaths, and prisoners captured
- Actual stats are available from `StatsModule` but not being used

**Solution:**
1. Add `StatsModule` reference to `ModerationManager` constructor
2. Retrieve actual stats using `plugin.stats.manager.getStats(player)`
3. Update `PlayerInfo` creation:
   ```kotlin
   val stats = if (player.isOnline) {
       player.player?.let { statsModule.manager.getStats(it) }
   } else null
   
   return PlayerInfo(
       // ... other fields ...
       kills = stats?.kills ?: 0,
       deaths = stats?.deaths ?: 0,
       prisonersCaptured = stats?.prisonersCaptured ?: 0
   )
   ```

**Files to Modify:**
- `src/main/kotlin/com/tyler/ggvsgoons/admin/ModerationManager.kt`
- `src/main/kotlin/com/tyler/ggvsgoons/admin/AdminConfigModule.kt`

**Testing:**
- Verify stats display correctly for online players
- Verify graceful handling for offline players

---

### 1.3 Register ScoreboardRenderer as Event Listener
**File:** `src/main/kotlin/com/tyler/ggvsgoons/scoreboard/ScoreboardModule.kt`

**Problem:**
- `ScoreboardRenderer` implements `Listener` and has `@EventHandler` for combat tracking
- Never registered with Bukkit's event system
- Combat tracking feature is completely non-functional

**Solution:**
1. Register renderer as event listener in `ScoreboardModule.register()`:
   ```kotlin
   override fun register(plugin: GGvGPlugin) {
       manager = ScoreboardManager(plugin)
       plugin.server.pluginManager.registerEvents(manager.renderer, plugin)
       // ... rest of registration
   }
   ```

**Files to Modify:**
- `src/main/kotlin/com/tyler/ggvsgoons/scoreboard/ScoreboardModule.kt`

**Testing:**
- Verify combat tracking works (players marked as in combat when damaged)
- Verify scoreboard hides during combat if configured

---

### 1.4 Add Combat Tracking Cleanup Mechanism
**File:** `src/main/kotlin/com/tyler/ggvsgoons/scoreboard/ScoreboardRenderer.kt`
**Line:** 17

**Problem:**
- `combatUntil` ConcurrentHashMap never removes entries for logged-off players
- Memory leak that grows over time
- Old player UUIDs accumulate indefinitely

**Solution:**
1. Add player quit event handler to clean up combat tracking:
   ```kotlin
   @EventHandler
   fun onPlayerQuit(event: PlayerQuitEvent) {
       combatUntil.remove(event.player.uniqueId)
   }
   ```
2. Optionally add periodic cleanup task for expired entries:
   ```kotlin
   fun cleanupExpiredCombat() {
       val now = System.currentTimeMillis()
       combatUntil.entries.removeIf { it.value < now }
   }
   ```

**Files to Modify:**
- `src/main/kotlin/com/tyler/ggvsgoons/scoreboard/ScoreboardRenderer.kt`

**Testing:**
- Verify map is cleaned when players log out
- Monitor memory usage over time

---

## Phase 2: Medium Priority Fixes

### 2.1 Fix Team Kick Implementation
**File:** `src/main/kotlin/com/tyler/ggvsgoons/admin/ModerationManager.kt`
**Line:** 134

**Problem:**
- Uses `setPlayerTeamSilently(playerId, "")` which passes empty string
- Empty string is not a valid team ID
- Won't properly remove player from team

**Solution:**
1. Check if `TeamManager` has a proper removal method
2. If not, add one to `TeamManager`:
   ```kotlin
   fun removePlayerFromTeam(playerId: UUID): Boolean {
       val had = playerTeams.remove(playerId) != null
       if (had) {
           val player = Bukkit.getPlayer(playerId)
           if (player != null) {
               scoreboard?.getEntryTeam(player.name)?.removeEntry(player.name)
           }
       }
       return had
   }
   ```
3. Use the proper method in `ModerationManager`:
   ```kotlin
   plugin.teams.manager.removePlayerFromTeam(playerId)
   ```

**Files to Modify:**
- `src/main/kotlin/com/tyler/ggvsgoons/teams/TeamManager.kt`
- `src/main/kotlin/com/tyler/ggvsgoons/admin/ModerationManager.kt`

**Testing:**
- Verify admin can kick players from teams
- Verify player is properly removed from scoreboard team
- Verify player can rejoin a team after being kicked

---

### 2.2 Fix Null Safety in TabListRenderer
**File:** `src/main/kotlin/com/tyler/ggvsgoons/scoreboard/TabListRenderer.kt`
**Line:** 82

**Problem:**
- `.replace("{team_online}", team?.let { ... }.toString())` will print "null" string
- Should display "0" instead

**Solution:**
```kotlin
.replace("{team_online}", team?.let { 
    plugin.teams.manager.getTeamMembers(it.id)
        .count { id -> Bukkit.getPlayer(id) != null }
        .toString()
} ?: "0")
```

**Files to Modify:**
- `src/main/kotlin/com/tyler/ggvsgoons/scoreboard/TabListRenderer.kt`

**Testing:**
- Verify displays "0" for players without teams
- Verify displays correct count for players with teams

---

### 2.3 Implement Faction Home Teleport Warmup/Cooldown System
**Files:** 
- `src/main/kotlin/com/tyler/ggvsgoons/factions/FactionCommand.kt` (Line 392)
- `src/main/kotlin/com/tyler/ggvsgoons/factions/FactionEventListener.kt` (Lines 45-52)

**Problem:**
- Config has warmup/cooldown settings but they're not implemented
- Teleport happens instantly without warmup
- No cooldown tracking

**Solution:**
1. Create teleport tracking system in `FactionManager`:
   ```kotlin
   private val teleportWarmups = ConcurrentHashMap<UUID, TeleportWarmup>()
   private val teleportCooldowns = ConcurrentHashMap<UUID, Long>()
   
   data class TeleportWarmup(
       val playerId: UUID,
       val destination: Location,
       val startTime: Long,
       val taskId: Int
   )
   ```

2. Implement warmup in `FactionCommand`:
   ```kotlin
   val warmupSeconds = plugin.config.getInt("factions.homes.teleport-warmup", 5)
   val cooldownSeconds = plugin.config.getInt("factions.homes.teleport-cooldown", 300)
   
   // Check cooldown
   val lastTeleport = factionManager.getLastTeleport(player.uniqueId)
   if (lastTeleport != null && (System.currentTimeMillis() - lastTeleport) < cooldownSeconds * 1000) {
       // Send cooldown message
       return true
   }
   
   // Start warmup
   player.sendMessage("${ChatColor.YELLOW}Teleporting in $warmupSeconds seconds...")
   val taskId = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
       player.teleport(home)
       factionManager.recordTeleport(player.uniqueId)
   }, warmupSeconds * 20L).taskId
   
   factionManager.startTeleportWarmup(player.uniqueId, home, taskId)
   ```

3. Implement damage cancellation in `FactionEventListener`:
   ```kotlin
   @EventHandler
   fun onPlayerDamage(event: EntityDamageEvent) {
       val player = event.entity as? Player ?: return
       
       if (plugin.config.getBoolean("factions.homes.cancel-on-damage", true)) {
           if (factionManager.isInTeleportWarmup(player.uniqueId)) {
               factionManager.cancelTeleportWarmup(player.uniqueId)
               player.sendMessage("${ChatColor.RED}Teleport cancelled due to damage!")
           }
       }
   }
   ```

**Files to Modify:**
- `src/main/kotlin/com/tyler/ggvsgoons/factions/FactionManager.kt`
- `src/main/kotlin/com/tyler/ggvsgoons/factions/FactionCommand.kt`
- `src/main/kotlin/com/tyler/ggvsgoons/factions/FactionEventListener.kt`

**Testing:**
- Verify warmup countdown works
- Verify teleport cancels on damage
- Verify cooldown prevents rapid teleports
- Verify cleanup on player logout

---

## Phase 3: Low Priority Improvements

### 3.1 Decide on LuckPerms Integration Approach
**File:** `src/main/kotlin/com/tyler/ggvsgoons/admin/PermissionManager.kt`
**Lines:** 158-187

**Problem:**
- All LuckPerms methods are stubs with TODO comments
- System claims to support LuckPerms but doesn't

**Options:**

**Option A: Implement Full LuckPerms Integration**
- Add LuckPerms API dependency to `build.gradle.kts`
- Implement all stub methods using LuckPerms API
- Requires understanding of LuckPerms API

**Option B: Remove LuckPerms Claims**
- Remove `useLuckPerms` parameter
- Remove all LuckPerms stub methods
- Update documentation to clarify only internal system is supported
- Simpler and more honest approach

**Option C: Hybrid Approach (Recommended)**
- Keep detection of LuckPerms
- If LuckPerms is present, delegate to it via commands
- If not present, use internal system
- Document that LuckPerms integration is basic

**Recommendation:** Option C - Keep basic detection but document limitations clearly

**Files to Modify:**
- `src/main/kotlin/com/tyler/ggvsgoons/admin/PermissionManager.kt`
- `README.md` (documentation)
- `build.gradle.kts` (if implementing full integration)

---

### 3.2 Standardize ChatColor Imports
**Files:** Multiple files throughout project

**Problem:**
- Some files use `org.bukkit.ChatColor`
- Some files use `net.md_5.bungee.api.ChatColor`
- `Team.kt` imports both with aliasing
- Inconsistent and confusing

**Solution:**
1. Choose one standard (recommend Bukkit ChatColor for consistency)
2. Create utility extension if needed:
   ```kotlin
   // In a utils file
   fun org.bukkit.ChatColor.toBungee(): net.md_5.bungee.api.ChatColor {
       return net.md_5.bungee.api.ChatColor.valueOf(this.name)
   }
   ```
3. Update all files to use consistent import
4. Update `Team.kt` to remove dual imports

**Files to Modify:**
- `src/main/kotlin/com/tyler/ggvsgoons/teams/Team.kt`
- `src/main/kotlin/com/tyler/ggvsgoons/teams/TeamManager.kt`
- Other files as needed

**Testing:**
- Verify all colors display correctly
- Verify no compilation errors

---

### 3.3 Add Config Validation Throughout Codebase
**Files:** Multiple config loaders

**Problem:**
- `ScoreboardManager` has good validation with `coerceAtLeast(1)`
- Other config loaders lack similar validation
- Invalid config values could cause runtime errors

**Solution:**
1. Create config validation utility:
   ```kotlin
   object ConfigValidator {
       fun validatePositiveInt(value: Int, default: Int, min: Int = 1): Int {
           return value.coerceAtLeast(min).coerceAtMost(Int.MAX_VALUE)
       }
       
       fun validateRange(value: Int, min: Int, max: Int, default: Int): Int {
           return value.coerceIn(min, max)
       }
       
       fun validateNonEmpty(value: String, default: String): String {
           return value.ifBlank { default }
       }
   }
   ```

2. Apply validation in all config loaders:
   - `FactionManager.loadConfig()`
   - `TeamManager` (for cooldowns)
   - `WarPrisonerModule` (for timeouts)
   - All other config loading points

**Files to Create:**
- `src/main/kotlin/com/tyler/ggvsgoons/utils/ConfigValidator.kt`

**Files to Modify:**
- `src/main/kotlin/com/tyler/ggvsgoons/factions/FactionManager.kt`
- `src/main/kotlin/com/tyler/ggvsgoons/teams/TeamManager.kt`
- `src/main/kotlin/com/tyler/ggvsgoons/commands/WarPrisonerModule.kt`
- Other config loading locations

**Testing:**
- Test with invalid config values (negative numbers, empty strings, etc.)
- Verify defaults are applied correctly
- Verify no crashes from bad config

---

## Implementation Order

### Sprint 1: Critical Fixes (Days 1-2)
1. Fix reflection-based faction access (1.1)
2. Implement stats tracking (1.2)
3. Register ScoreboardRenderer (1.3)
4. Add combat cleanup (1.4)

### Sprint 2: Medium Priority (Days 3-4)
5. Fix team kick implementation (2.1)
6. Fix null safety in TabListRenderer (2.2)
7. Implement teleport warmup/cooldown (2.3)

### Sprint 3: Polish (Day 5)
8. Decide on LuckPerms approach (3.1)
9. Standardize ChatColor imports (3.2)
10. Add config validation (3.3)

---

## Testing Strategy

### Unit Testing
- Test each fix in isolation
- Verify edge cases (null values, offline players, etc.)
- Test with modules enabled/disabled

### Integration Testing
- Test interactions between fixed components
- Verify no regressions in existing functionality
- Test with realistic server scenarios

### Performance Testing
- Monitor memory usage after combat cleanup fix
- Verify no performance degradation from changes
- Test with multiple players online

---

## Rollback Plan

For each change:
1. Create git branch before starting
2. Commit each fix separately
3. Tag working versions
4. Document any breaking changes
5. Keep backup of working build

If issues arise:
1. Identify problematic commit
2. Revert specific change
3. Re-test remaining fixes
4. Document issue for future fix

---

## Success Criteria

- ✅ All compilation errors resolved
- ✅ No reflection usage in production code
- ✅ All event listeners properly registered
- ✅ No memory leaks from tracking maps
- ✅ Stats display correctly in admin commands
- ✅ Teleport system fully functional
- ✅ Consistent code style throughout
- ✅ All config values validated
- ✅ Comprehensive testing completed

---

## Notes

- Prioritize fixes that affect functionality over style improvements
- Test each change thoroughly before moving to next
- Document any API changes for future reference
- Consider backward compatibility with existing data files
- Update README.md with any new features or changes
