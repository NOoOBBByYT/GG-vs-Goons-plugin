# On-server regression checklist (not run)

Status: **all on-server checks below are pending**. Unit tests are not a substitute for testing on Paper. Run on an isolated Paper 1.21.1 / Java 21 staging server with the current build, three non-op test accounts (two on GG and one on Goons), an op admin, and a disposable copy of the plugin data directory. Record the server version, plugin build, tester, date, pass/fail, and relevant logs for each section. Never perform the corruption or restore exercises on production data.

Before starting: fully stop the server, copy `plugins/GGvGoons/` somewhere safe, and keep the copy outside the plugin directory. Run `gradlew.bat build` on Windows (or `./gradlew build` elsewhere), install the jar from `build/libs/`, and fully restart. Check for startup exceptions and unexpected data-file changes. Use full stop/start rather than `/reload` for every persistence or restore check.

## 1. Startup flags and module lifecycle — pending

- With `factions.enabled: false` and `admin.enabled: false` in the generated `config.yml`, fully start the server. Expect no startup/shutdown exceptions; `/faction list` must report disabled, `/ggadmin module list` must not grant admin access, and the saved `factions.yml` must not be replaced by an empty file. Restore both flags to true and restart; expect faction membership and admin commands to work again.
- With `scoreboard.enabled: false` and `tablist.enabled: false`, start and join with both accounts; expect no plugin sidebar or tab formatting. Re-enable and restart; verify both accounts see the configured sidebar/tab formatting and team-colored nametags.
- Temporarily set `prisoner.enable-persistence: false` and `factions.enable-persistence: false` with copies of existing data files. Restart and stop without changing the data. Expect the existing files not to be overwritten. Restore both settings to true and restart; confirm the prior data loads.
- On a disposable data copy only, introduce malformed YAML in a persisted prisoner, team, or faction file, then start. Expect startup to abort with a clear log error without rewriting the malformed file. Restore the good copy before continuing.

## 2. Runtime flags and reloads — pending

- As op, run `/ggadmin module list`, `/ggadmin module disable factions`, `/faction list`, `/ggadmin module enable factions`, `/faction list`. Expect the list to reflect each flag, faction commands to deny while disabled, then work again without deleting members. Stop/restart and verify the state persists.
- Edit `factions.enabled` in the generated config, run `/ggadmin config reload`, and compare `/ggadmin config get factions.enabled`, `/ggadmin module list`, and `/faction list`. Expect them to agree. Run `/ggadmin module reload factions`; expect success without erasing members. Unsupported module reload requests must fail rather than claim success.
- Change `scoreboard.title` on disk and run `/ggadmin scoreboard reload`; expect the sidebar title to update. Change `tablist.header` and run `/ggadmin tablist reload`; expect the displayed header to update without replacing players' main team scoreboard. Use `/scoreboard toggle` and confirm only the requesting player's sidebar changes.
- Try `/ggadmin config set prisoner.enable-persistence false`; expect rejection as startup-only. Edit on disk and fully restart for startup-only options instead of interpreting a config reload as a manager rebuild. Do not disable the admin flag until other admin checks are finished; restoring it after admin commands become unavailable requires editing the generated config and restarting.

## 3. Restart persistence — pending

- Join two players to GG and one to Goons with `/team join gg` and `/team join goons`; have a GG player create a faction with `/faction create Alpha`, invite the other GG player using `/faction invite <player>`, and have them accept with `/faction accept Alpha`. Check `/team info`, `/faction info`, and `/faction list` before and after a full stop/start. Expect team and faction memberships to match. Repeat a restart with factions disabled, then re-enable and restart; expect the same saved faction members.
- Capture an opposing player using `/warprisoner <player>` and the target's accept action. Check `/listprisoners`, stop/restart, and have the captive rejoin; expect the prisoner to remain listed and be in Adventure mode. Use `/freeprisoner <player>` with the target online; expect the prior game mode to return and the prisoner entry to disappear, including after another restart. `/executeprisoner <player>` ends captivity; it should not kill the target.

## 4. Gameplay transitions and presentation — pending

- Test same-team capture and a target changing to the captor's team or disconnecting before accepting; expect denial without a new prisoner. Verify that an expired offer cannot be accepted. Capture/release an opposing player and repeat with a respawn/rejoin; verify Adventure mode is enforced until release.
- Invite a teammate, then move them to the opposing team before acceptance; expect the invitation to fail. Switch or leave a team while a faction member; expect no cross-team faction membership. A faction leader should have to transfer leadership or disband first. Check faction chat and `@team` messages go only to intended recipients, even if another chat plugin is present.
- With both players online, verify team nametag colors and friendly-fire behavior remain correct when personal sidebar display is toggled, on respawn, and after reconnect. Record any behavior specific to the staging server's other plugins or mods.

## 5. Permissions and moderation — pending

- As a non-op, try `/ggadmin`, `/ggadmin backup list`, `/ggadmin permission grant ...`, and `/warprisoner ...`; expect denial absent the declared permissions. As op, `/ggadmin permission grant <online-non-op> ggvgoons.warprisoner.capture`, then `check` and attempt capture; expect it to work. Revoke and check again; expect denial. Test an offline grant and expect an explicit failure. Log the player out/restart and verify that temporary Bukkit grants do not persist.
- As op, run `/ggadmin player freeze <online-player>`; verify movement, inventory interaction, and non-recovery commands are blocked. Run `/ggadmin player unfreeze <player>`; verify those actions work again. Test `/ggadmin command disable team <online-player>` and `/team list` for that player, then re-enable; expect the restriction to affect this plugin's command only. Test an unsupported external command name; expect rejection instead of a misleading success.
- Verify a non-op without `ggvgoons.admin.moderate` cannot freeze players and one without `ggvgoons.admin.backup` cannot restore backups. Confirm audit entries do not falsely record rejected actions as successes.

## 6. Backup and recovery (disposable staging copy only) — pending

- Save a known team/faction/prisoner snapshot and run `/ggadmin backup create regression_before`; verify `/ggadmin backup list` includes it and the archive is present under `plugins/GGvGoons/backups/`. Change a team/faction record via gameplay, then run `/ggadmin backup restore regression_before`. Before stopping, expect **no live overwrite**; a pending restore should be queued. Fully stop and restart (never `/reload`); expect the snapshot to load, the queued marker to disappear, and a `pre_restore_...` safety backup to exist.
- On another disposable copy, queue a valid restore, stop, then corrupt only `plugins/GGvGoons/backups/pending-restore.dat` before startup. Expect the plugin to disable before loading modules, retain existing live data, and leave the marker for investigation. Fully stop, retain copies of logs/marker, remove the corrupt marker **only after diagnosing it**, restore clean data from your independent copy, and start again. Verify the server loads cleanly.
- Try an invalid backup name such as `../bad`; expect rejection without writing outside the backup directory. Avoid hand-editing ZIP entries on any live server.

When recording results, distinguish **not run**, **pass**, **fail**, and **blocked** for each section. Any failed data-safety or permission check blocks production deployment until investigated.
