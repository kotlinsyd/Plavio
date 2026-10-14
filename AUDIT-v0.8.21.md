# Plavio Mobile v0.8.21 — Full Code Audit (2026-10-11)

Read-only audit. Nothing modified. 6 subagents: Player, Update flow, Navigation & screens, Data layer, Persistence, Robustness.

## 🔴 CRASHES
1. **M3U import crashes the app** — `StreamRepository.kt:723`: `addM3uPlaylist()` throws instead of returning `Result.failure`; caller has no try/catch → pasting `www.example.com/x.m3u` or stray text = force-close.
2. **Update tap can crash on malformed pointer URL** — `ApkInstaller.kt:29-36`: `dm.enqueue()` throws on non-http(s) URL, no try/catch. Low probability (needs hand-corrupted stable.json).

## 🔴 BROKEN FEATURES
3. **Live TV playback is DEAD — tapping any channel plays nothing.** `LiveScreen.kt:189,216` builds a PlaybackRequest but `MainActivity.kt:345` drops it (`onPlay = { navigate(PLAYER) }`); `pendingPlayback` never set; PlayerScreen reads only that. Symptom: "Nothing to play" — or the WRONG title plays (previously watched movie). Zapping equally dead. NOTE: contradicts the v0.8.21 builder's "done and functional" claim — its verification was compile-only.
4. **Bad streams hang forever on spinner.** No `Player.Listener` registered anywhere (grep-verified). 404/timeout/unsupported format → infinite "Finding source…" spinner, no error, no retry. Also strands the floating Sources switcher.
5. **Rapid zap/source-switch leaks players → double audio / wrong channel.** `replaceStream` (`PlayerScreen.kt:634-674`): two rapid calls capture the same old player; first fresh player orphaned with `playWhenReady`, decodes audio underneath; main blocks can complete out of order so the older zap wins.
6. **Long-press to pin never fires — Pinned row unreachable.** `LiveScreen.kt:~350` outer `combinedClickable(onLongClick)` vs inner `PosterCard` clickable (`Components.kt:~137`) consuming the press. `liveFavorites` stays empty forever.
7. **Addon share rejects its own share message; `plavio-addons://` links don't open the app.** `AddonShare.kt` decode only strips scheme if text *starts with* it; share sheet sends `"My Plavio addons (a, b):\nplavio-addons://…"` → Base64 fail → "doesn't look like a bundle". Manifest has NO intent-filter for the scheme → tapping a bundle link in WhatsApp/Discord does nothing.
8. **Live channels leak into Continue Watching, can never be cleared.** `savePosition` (`PlayerScreen.kt:952`) fires on ON_PAUSE/dispose with no `type == "tv"` guard; live duration TIME_UNSET → durationMs=0 → passes the 10s check → upserted; finish-detection needs durationMs>0 → never expires; `ContinueWatchingCard` has no delete affordance. Tapping one shows a Resume prompt on a live stream.
9. **Ghost audio after leaving player mid-zap/load.** `ioScope` never cancelled on dispose (`PlayerScreen.kt:302,660`); late `zapTo`/`playEpisode` builds a new ExoPlayer assigned to disposed state — never released. Audio keeps playing, no UI to stop (only killing the app).
10. **Backgrounding leaves audio playing with no stop.** `ON_PAUSE` only savePositions (`:1055`) — player never paused/released, no service/notification/MediaSession. Sound continues after leaving app or locking screen.
11. **DB-corruption self-heal permanently loses bundled addons.** `PulseApp.kt openDatabase()` deletes+recreates DB empty, but `preinstall_done_*` prefs survive → PlavioMeta/Cinemeta/OpenSubtitles/SubSense skipped forever. Silent; recovery = manual re-add or clear data.
12. **TV updater fallback URL malformed (latent).** `UpdateChecker.kt:96-100`: builds `Plavio-TV-v$tag.apk` with `$tag` still carrying `-tv` → `Plavio-TV-v0.8.23-tv.apk` (doesn't exist). Mobile asymmetry (`:107`): blank apkUrl → `apkUrl!!` throws → caught → no update ever offered on mobile, silently. Only on malformed pointer.
13. **Parser throws on wrong-typed fields.** `StremioParser.kt:24 str()`: `jsonPrimitive` throws when a field is object/array → aborts the ENTIRE manifest/catalog/stream parse. One odd field → whole catalog silently empty.
14. **Live tab re-downloads every M3U playlist sequentially on every visit; dead playlists vanish silently.** `StreamRepository.kt:740` + refetch on every ON_RESUME; sequential, 30s timeouts each, nothing cached; failures `getOrNull() ?: continue` → channels disappear with no explanation.
15. **Continue Watching replays stored URLs verbatim.** Resume plays the stored stream URL directly; for signed/expiring URLs (debrid/proxy) it's stale → first attempt fails; user must open Sources and re-pick.

## 🟠 UX BUGS (condensed)
16. Speed resets on source switch, pill keeps old label (1.5x shown, plays 1x).
17. Switching sources deletes resume position on the old source.
18. Swipe-brightness leaks into the rest of the app (Detail/Settings stay dimmed).
19. M3U channels re-zap onto themselves (double-tap CH+ rebuilds player twice).
20. Subtitle styling always overrides Android system captions — no "System default" option (regression vs pre-0.8.21).
21. Failed channel still lands in Recently Watched (pushed before resolution).
22. Search loses everything on rotation (plain `remember`).
23. Onboarding resets on rotation; system back exits the app (no BackHandler).
24. Source picker hides the real error ("No sources found yet" instead of actual error).
25. Failed update downloads fail silently — installer launched on garbage → system "problem parsing the package"; update dialog silently dismissed.
26. Manual update check can't distinguish offline from up-to-date (always "you're on the latest version").
27. Back with controls visible exits player instead of hiding controls first.
28. Media/channel keys die while a dialog is open (AlertDialogs are separate windows).
29. Side-loaded subtitles re-load the video mid-playback (visible rebuffer; refires after every source switch).
30. Search cache goes stale on addon remove/disable (in-memory catalogCache not cleared).
31. Visiting Live marks Home's cache "fresh" → suppresses Home's background refresh for 15 min.
32. Re-importing a bundle double-counts ("Installed N addons ✓" again, bumps sortOrder).
33. Watchlist has no remove affordance (must open Detail → tap bookmark).
34. Subtitle style dialog has no cancel — every tap persists immediately.
35. Related rows: one bad section kills all sections; duplicate section keys → Compose crash (server-controlled edge).

## 🟡 MINOR NITS (grouped)
- Update: `KEY_LAST_CHECK` write-only dead code (no throttle despite docstring); no scheme/host validation on apkUrl; no unknown-sources permission pre-check; `isNewer` edge cases (`-beta`→0, uppercase `V`→bogus filename); stale User-Agent `PulseStream/0.4.0`.
- Search/data: `search=` fired at catalogs not declaring search (wasted requests); no overall remote-search timeout (~25s worst case); manifest TTL 24h delays new search-capable addons; `getMetaFromAny` sequential, no per-addon timeout.
- M3U: no playlist size cap (OOM edge); `substringAfter(",")` breaks on quoted commas in group-title; non-UTF8 → mojibake; dedup exact-string only.
- Persistence: orphaned pin/recent keys on playlist removal; read-modify-write races on toggleWatchlist/double-tap; `subtitleLangMatches` prefix false-positives ("hi" matches "hil"); auto-backup restores `onboarding_done=true` → guide skipped on fresh install; Trakt/Simkl tokens plaintext in SharedPreferences+backups.
- Navigation: search type chip can't toggle off; dead `sourcesIndex`; related detail backstack stacks every title (no launchSingleTop); configChanges missing density/uiMode.

## ✅ VERIFIED CLEAN
Update flavor gating (every code path); version compare; fail-safe on bad pointer; dialog dismissal/rotation; FileProvider; extra→supportsSearch→prefer chain; search dedup; related failure paths; M3U malformed-input errors; preferred-audio wiring; subtitle styling application; watchlist↔Home isolation; addon share encode/decode round-trip; all 24 SettingsStore keys; Room migrations 1→7; coroutine scoping (only ioScope leaks); no hardcoded secrets; no GlobalScope.

## COULD NOT VERIFY (no device)
#3/#6/#4/#9/#10 rest on unambiguous static analysis but weren't watched fail on hardware. Physical KEYCODE_CHANNEL_UP/DOWN delivery and preferred-audio auto-select on real multi-audio streams need his device. Live stable.json copies could be stale vs GitLab.

## SUGGESTED FIX PRIORITY
1. #3 Live playback dead (core function, ~one-line fix)
2. #1 M3U import crash (force-close on trivial input)
3. #4 Stream error handling (Player.Listener + error/retry/next-source)
4. #5/#9/#10 Player lifecycle (release on dispose, cancel ioScope, pause on ON_PAUSE)
5. #8 Live channels in Continue Watching (type guard + delete affordance)
