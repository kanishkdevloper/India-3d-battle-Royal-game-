## Phase 42 (new): the HUD editor now moves + resizes EVERYTHING, and nothing can be made small
- Settings > DISPLAY > EDIT HUD LAYOUT now also lists the info blocks that were fixed before: **ALIVE / KILLS** pill (id 48), **ZONE TIMER** bar (49), **HP / SHIELD / EP bars** with their badges (50),
  **AMMO counter** `30 / 90` (51) and the **COORDS / FPS** text (52). They are in all three modes (ON FOOT / DRIVING / PLANE+CHUTE), together with every button, joystick, minimap, EXIT, gear, weapon chip, scope chip and PICK UP bar.
- Drag = move, SIZE -/+ = resize (whole block incl. text scales), RESET (this mode), RESET HUD LAYOUT in Settings (everything). Saved on the device.
- No control is small: the minimum size is now 1.0 x the default (was 0.85), max 2.5 x. Older saved layouts are clamped. Smallest defaults made bigger: gear, scope chip, SIT, FPP/TPP, CANCEL, BAG, SOUND (r 30-34).
- Default ammo counter moved to the right of the weapon chip so it no longer sits under JUMP.
- NOT compiled in the session that made it (no Kotlin compiler there): send the Gradle error text if any.
Files changed: HudLayout.kt, HudEditView.kt, GameScreen.kt, README.md.

## Phase 41 (new): EVERY control can be moved + resized in EDIT HUD LAYOUT, and none is small
- The editor (Settings > DISPLAY > EDIT HUD LAYOUT) now lists ALL controls of each mode, not only the round buttons: joystick (STICK), minimap (MAP), EXIT, settings gear, GET OFF (driving), weapon chip (WEAPON), scope chip (SCOPE) and the PICK UP bar,
  plus every round button (incl. the new FPP/TPP and CANCEL). Modes: ON FOOT / DRIVING / PLANE+CHUTE. Drag = move, SIZE -/+ = resize, RESET, DONE saves. RESET HUD LAYOUT in Settings resets everything.
- All positions / sizes come from one place (`HudLayout.base / circle / rectOf`, ids 40-47 = new controls), the match and the editor use the same numbers. Text inside the wide controls grows with the size.
- No control is small: the smallest buttons were made bigger (SIT, EAT, HEAL, NADE, RUN, BAG, SOUND, VIEW, CANCEL, the driving buttons, gear) and a control can never be shrunk below 0.85 x its default size (max 2.2 x). Older saved layouts are clamped to that range.
- The joystick can be placed anywhere: a touch on it starts the stick even outside the left 45 % of the screen.
- Default positions were re-checked for overlaps on 5 screen sizes (1600x720 ... 2576x1178: none overlap, nothing off-screen): BAG + SCOPE now sit left of the weapon chip, PICK UP bar above the crosshair, FPP/TPP under the gear.
- NOT compiled in the session that made it (no Kotlin compiler there): send the Gradle error text if any.
Files changed: HudLayout.kt, GameScreen.kt, HudEditView.kt, README.md.

## Phase 40 (new): HUD buttons are now the PNG icons of the icon sheet
- Every icon of the sheet was cut out one by one (square 256 px, transparent corners) into `app/src/main/assets/ui/hud/*.png` (39 files, listed in `manifest.txt`). New class `HudIcons.kt` loads + draws them.
- **Context buttons:** FIRE = bullet icon while a gun is in the hand; with EMPTY HANDS (FISTS slot) the same button becomes the fist icon (PUNCH) and RELOAD disappears (it also stops reacting to touch).
- Icons on the real buttons: JUMP, RUN, SIT/STAND (kneel), FIRE/PUNCH, RELOAD, HEAL (medkit + count), AIM (crosshair), NADE (grenade + count), EAT (heart + mushroom count), BAG, SOUND (speaker, red slash when muted), USE (steering wheel),
  DROP / door, SKATE / DIVE (air), GET OFF + EXIT (door), settings gear, pick-up hand, airdrop crate, HP + shield badges at the left of the bars, joystick base + knob (knob follows the finger).
- Driving: LEFT / RIGHT = steering wheel turned, AUTO = car, BOOST = bullet icon + timer, MED = medkit. GAS, BRAKE, HORN, CHUTE have no matching picture on the sheet: they use the blank round button of the same style + text.
- **Two new REAL buttons** (movable in the HUD layout editor, ids 29 / 31): FPP / TPP (switches `Prefs.thirdPerson` live, same switch as in Settings) and CANCEL (visible only while a medkit heal runs, `GameRenderer.cancelHeal()` stops it, nothing is used up).
- Icons on the sheet that have NO feature in the game yet (prone, voice, text chat, ping, revive, helmet / vest buttons, throw-grenade) are cut and stored in `assets/ui/hud/` but are NOT shown as buttons (no fake buttons).
- A missing PNG never breaks the HUD: that button falls back to the old round text button.
- NOT compiled in the session that made it (no Kotlin compiler there): send the Gradle error text if any.
Files changed: HudIcons.kt (new), GameScreen.kt, HudLayout.kt, HudEditView.kt, GameRenderer.kt, manifest.txt, README.md + 39 PNGs in assets/ui/hud/.

> **Play Store packaging (added after Phase 28):** target/compile SDK 36, AGP 8.13.2, Gradle 8.13, Kotlin 2.2.20, back-button handler (Android 16),
> adaptive icon, release signing hook, GitHub Actions workflow, privacy policy, store listing and graphics.
> Start with `PLAY_STORE/BUILD_AND_UPLOAD.md`. Still NOT compiled in the session that prepared it (no Android SDK / internet there).

# Dustline - native Kotlin port (Phase 1 - 28)

## Phase 37 (new): STORE = same layout as the reference screen
- Layout now follows the screenshots: left FASHION / PRESET (PRESET directly under FASHION), a column of 7 white icons (cap, glasses, mask, t-shirt, jeans, shoes, full bundle),
  two boxes on top of the grid (ALL (BY GENDER) and DEFAULT + sort arrow, both really filter / sort), a small 2-column scrolling grid (with fling),
  BUNDLE CONTAINS list + info / BUY box right of the grid, 3D avatar further right (`LobbyAvatarView.panelFrac`, STORE 36 %, VAULT unchanged 30 %).
- Each icon lists ONLY its own kind of item. `store.json` items may carry `"cat"` (cap / glasses / mask / top / pants / shoes / bundle), `"gender"` (m / f, else unisex), `"price"`, `"new": true`, `"contains": [ids]` (bundle);
  without `"cat"` the section is guessed from whole words of id / name (cap, hat, glass, mask, shoe, boot, sandal, chappal, jean, pant, tracksuit ...), else it is a T-SHIRT. Only items that exist in the game are shown (today: the 4 t-shirts).
- PRESET: every `Characters.ALL` entry, price in COINS, owned ones show a soft OWNED. NOT compiled in the session that made it (no Kotlin compiler there): send the Gradle error text if any.
Files changed: StoreView.kt, LobbyAvatarView.kt, MainActivity.kt, README.md.


Open this folder in Android Studio (Koala or newer), let Gradle sync, run on a phone/emulator (landscape).

## Phase 28 (new): the 5 gaps of the Phase 27 audit are closed + red criminal removed
Everything below was ported from the HTML v13 (`dskAirPose` / `descend` / `dlMakeBoard`, `dxMakeCrate` / `dxCrates`, `dxNpcCars` / `reserveEscapeCar`, the PUBG pack, `playerShoot`).
- **Skate-stance freefall + skateboard** (`Phase28.kt` class `AirVis`, `CharacterModel.kt` pose mode `AIR`, `GameRenderer.drawSkateboard`): freefall now has TWO stances like the HTML.
  DIVE (default, head first, look down = faster) and SKATE (slow fall about 13 m/s, glide up to 30 m/s, stick = forward / back / sideways, the board banks into turns, body side-on, face looks ahead).
  New round button **SKATE / DIVE** (id 102, only visible in freefall, movable in the HUD layout editor; the HTML uses the V key). Freefall + parachute are now seen from behind like the HTML
  (camera 10 m behind, 8 m in skate stance, 0.8 m to the right, FOV 83 / 77 / 74) so you see your own character + board + canopy. Bots in the air use the same smoothed pose: they dive while more than 90 m
  from their landing spot and switch to skate stance close to it (steerBotDrop). Board = deck, grip tape, 2 orange rails, 2 kicktails, trucks, 4 cylinder wheels, cyan glow, spins in while it grows.
  The chute hang has the HTML flare (legs swing forward near the ground). The old DIVE / CHUTE modes had the arm Z sign mirrored against the HTML, the new AIR mode uses the HTML signs.
- **Loot crate** (`LootCrate`, `GameRenderer.dropFromBot / stepCrates / drawCrates`): a dead bot's loot is no longer dropped on the ground. A wooden crate (body, lid, orange straps, green bands, ring + light beam, exact sizes / colours of
  dxMakeCrate) pops out 0.8 s after the kill with a little overshoot. The items lie INSIDE it and can be taken (PICK UP or auto-pickup) only while you are within 3.4 m and the crate has grown;
  hint "LOOT BOX" once. An empty crate shrinks away after 1.8 s. At most 14 crates exist at the same time.
- **NPC cars** (`Bots.kt`, `Vehicles.drive(minHit)`): bots have driven and run people over since Phase 16. Now the rules are the ones of `dxNpcCars`: an NPC car only hurts at 4.5 m/s or more (slower cars are stopped by a bot in the way),
  damage to YOU = min(80, speed x 6), 0.9 s immunity per victim, footprint margin 0.39 m. Bots also run to the nearest free car within 38 m when they are outside the safe zone far from its centre (more than 48 m) or nearly dead
  and just hit (`reserveEscapeCar`), even while visible.
- **Small PUBG details**: breathing sway + hand drift of the gun (first-person gun and third-person arms, 40 % while aiming, bots after their first shot), idle breathing of your body (third person);
  the empty **magazine drops out on reload** at 24 % of the reload (not for shotguns): 3 x 13 x 7 cm box, 0.8 m/s down, tumbles, bounces, rests, shrinks the last 0.6 s, gone after 5 s;
  **soft contact shadows** under every fighter on foot (radial blob, 55 m range, smaller when crouched, new shader mode 5); the **head of your third-person body follows the aim pitch** (pitch x 0.5, max 0.42 rad).
- **Sprint + fire** (HTML `playerShoot` guard): FIRE is REFUSED while you sprint on the ground above 1.5 m/s - toast "CAN'T FIRE WHILE SPRINTING - STOP RUNNING OR JUMP" at most every 1.6 s. The sprint is NOT cancelled any more.
  Fists and grenades still work while sprinting.
- **Red criminal removed**: the CRIMINAL BUNDLE character (the only red-outfit criminal in the project, it was the default skin) is gone from the app: not selectable, not a bot skin, not loaded, not in the manifest.
  Default character is now SURVIVOR (saved profiles that had the criminal fall back to it). Its 3D files are NOT deleted: they are parked outside the app in `removed_models/criminal/` (crim.json, bin_04.bin, tex_01-04) - not built, not packaged.
- **Extra landmark models**: none of them is in the game or in the assets, and the old history text about them was removed from these notes. The web-code reading copy in `reference/` that still carried their code was removed too (your original HTML is the source).
- Version `28.0-native-phase28`. Files changed: Phase28.kt (new), CharacterModel.kt, GameRenderer.kt, Bots.kt, Vehicles.kt, Loot.kt, GameScreen.kt, HudLayout.kt, Characters.kt, Account.kt, LobbyAvatarView.kt, PortraitRenderer.kt, Cloth.kt, manifest.txt, build.gradle.kts.
### What to test first (NOT compiled here, never run - no Kotlin compiler / Android SDK in this session; brackets of every file are balanced)
1. Gradle sync + build. If the compiler complains, send the error text.
2. Plane > DROP > freefall: third-person view, tap SKATE (board appears under the feet, body turns side-on), tap DIVE; open the chute: dome over your head, arms up.
3. Kill a bot: a wooden crate pops out with a ring + beam, walk up (3 m) and tap PICK UP.
4. Reload a gun: the magazine falls and bounces. Look at the ground next to a bot / yourself (third person): soft shadow blob.
5. Run (RUN button) and hold FIRE: the toast appears and you keep running; jump or stop and it fires.
6. Lobby: only SURVIVOR is offered.
### Known simplifications (honest)
- Dropped magazines are for your own reloads only (bots have no reload animation). The recoil springs / turn inertia / camera punch of the HTML PUBG pack are still not ported (they were not in the audit list).
- The player's parachute is a plain coloured dome + one riser line (the HTML dome has stripes and live risers). The air camera is only clamped by the ground (no wall raycast).
- SKATE / DIVE is a screen button (no keyboard on the phone). Skate stance needs the Survivor rig only (all that is left).

## Phase 27 (new): audit of the HTML v13 DX PATCH - loot only in houses, roadkill knockback, bot loot extras
Audit (HTML `reference/Dustline_v13_code_only.html` vs the Kotlin project) found these gaps. Fixed in this zip:
- **Loot only INSIDE houses** (port of `dxInHouse` / `dxStockHouses`): `MapData.houseSpots` = footprints of every enterable house of `bermudaData.bin` (flag 0, 5-95 m wide, moved with the road-clear moves) + the 32 replacement houses.
  `MapData.houseSpotTry()`: 65 % a point inside a house, 35 % on the porch strip 1.4-3 m outside a wall, rejected in water / inside walls. `GameRenderer.houseSpot()`: up to 90 tries, items at least 1.3 m apart.
  ALL ground loot (weapons, ammo, vests, plates, medkits, scopes, grenades, mushrooms, starter ring, village loot) now uses it - nothing lies in open fields any more. Airdrops and dead-bot drops are not affected.
- **Roadkill knockback** (port of `dxRoadkill` + `dxKnock`): a bot hit by a car (yours or a bot car) is flung away from the car: ground speed 6-19 m/s, upward 4.5 + speed x .22, gravity 14, stopped by walls / cliffs, lands, lies stunned
  for a moment before it gets up (no thinking / shooting meanwhile, `BotSystem.knock / stepKnock`). Damage rules unchanged (speed x 7.5, under 35 % HP = dead). Before this it was only a 1-frame shove.
- **Dead bot = fuller loot** (`dropFromBot`, port of `dxMakeCrate`): besides gun + supply item the body also leaves an armor plate (35 %), a grenade (30 %) and a scope (22 %, 34 / 30 / 22 / 14 % for 1X / 2X / 4X / 8X).
- Version `27.0-native-phase27`. NOT compiled here (no Kotlin compiler / Android SDK in this session): brackets of MapData / Bots / GameRenderer checked, the first Gradle sync may show small errors - send the Build error text.
  Files changed: MapData.kt, Bots.kt, GameRenderer.kt, build.gradle.kts.

## Phase 39 (new): NEW PROFILE SCREEN (replaces the old profile system completely)
- `ProfileView.kt` rewritten from scratch after the reference screenshot: top bar (avatar, name, LV, coins, diamonds + , title PROFILE, gold X),
  left menu PROFILE / OVERVIEW > / STATS / ACHIEVEMENTS / HISTORY / FRIENDS / ALBUM / SETTINGS, gold framed card (avatar + edit pen, name, language, UID + copy, LV tag).
- All data is the REAL player data (name, UID, level, XP, coins, diamonds, matches, wins, kills, history ...). Background = the game's own `lobby_bg.jpg`, the 3D character (LobbyAvatarView) stands on the right half and can be turned by dragging.
- Every menu entry works: OVERVIEW (level + XP bar + 9 stat tiles), STATS (stats / performance / coins / honor score), ACHIEVEMENTS (13 badges with progress), HISTORY (last 20 matches),
  FRIENDS (your UID, copy, invite via share sheet), ALBUM (TAKE SNAPSHOT = real screenshot, tap a photo to delete), SETTINGS (opens the game settings on top of the profile).
- Back button: a sub page returns to PROFILE first, PROFILE returns to the lobby. Pen on the avatar = change name (3-16 characters).
- Files changed: ProfileView.kt (new), MainActivity.kt. NOT compiled here (no Kotlin compiler / Android SDK in this session) - if the first Gradle build shows an error, send the error text.

## Phase 26 (new): what was still missing from the HTML, now added
Audit of `reference/Dustline_v13_code_only.html` against the Kotlin project found these gaps - all added here:
- **Profile screen** (`ProfileView.kt`, port of renderProfile / PF_MENU): tap the profile card in the lobby. GALLERY (badges + days with Dustline), OVERVIEW > STATS / PERFORMANCE / COINS,
  ROLE > CHARACTER / OUTFIT, HISTORY (last 20 matches), FRIENDS, ALBUM (camera button saves a picture of the screen, tap a picture to delete), HONOR SCORE (100 - 5 per early exit), WISHLIST (heart per character).
  Buttons: change name (3-16 characters, pencil), copy UID, profile code, share (Android share sheet), level reward, next tab. Stats / history / wishlist / created date are saved in the account (`Account.record`, called after every match).
- **PRE-MATCH warm-up** (flow `preMatch`): every START / DROP IN AGAIN first gives 30 s on the ground with all 35 bots nearby, NO damage for anyone (you, bots, zone, grenades, roadkill), banner "PRE-MATCH: n / WARM-UP / NO DAMAGE".
  After 30 s a fresh round starts in the plane. EXIT during warm-up gives no XP.
- **FISTS** (weapons[4] of the HTML): the weapon chip < > now cycles through your guns and FISTS. Punch every 0.55 s, 22 damage (x1.5 head), reach 2.4 m, cancels healing, two fists in the first-person view.
- **BAG** button: backpack panel (guns per slot with ammo, reserve ammo, medkits, mushrooms, armor, grenades). **SND** button: real sound mute on / off. Both can be moved in the HUD layout editor.
- **Bot difficulty** EASY / NORMAL / HARD (Settings > IN-MATCH): spread .042 / .028 / .016 and damage 7 / 9 / 11 exactly like botShoot() of the HTML. Saved on the device.
- **35 bots** (36 fighters) like the HTML, was 30.
- **HUD layout: 3 contexts** like the HTML (on foot / driving / plane + parachute). The editor MODE button now cycles ON FOOT -> DRIVING -> PLANE/CHUTE. In the plane, freefall and parachute only DROP-CHUTE and SOUND are shown (the rest is hidden like `body.hud-airborne` in the HTML) and they have their own saved position and size.
- **Fists in third person**: the body now uses the relaxed pose (arms down) instead of holding an invisible rifle.
- Not copied on purpose: the HTML stamina bar (it is a hidden element, sprint never uses it), multiplayer / party (the HTML only has SOLO).
- NOT compiled here (no Kotlin compiler / Android SDK in this session): brackets of every file were checked, the first Gradle sync may show small errors to fix - send me the Build error text and I fix it.
  Files changed: Account.kt, Prefs.kt, Bots.kt, SettingsView.kt, GameRenderer.kt, GameScreen.kt, HudLayout.kt, MainActivity.kt, ProfileView.kt (new), build.gradle.kts.

## Phase 25 (new): spectate, SIT / crouch, blood FX, bloom + AO, bot car shooting, casings, hair styles + compile fix
- **Compile fix**: `GameRenderer.kt` declared `drops` twice (airdrops and swim splashes) - the swim list is now `splashDrops`. This would have failed the Gradle build.
- **Spectate after death** (port of SPEC / specBegin / specCamera of the HTML): no debrief card any more. Your body falls over (0.85 s, slides 0.8 m, stays), the camera lifts up and back, then glides behind the bot that
  eliminated you (or the nearest bot). Banner "ELIMINATED BY NAME" + `#place / ELIM / time`, bottom bar `<  SPECTATING / NAME  >  BACK (10)` (PREV / NEXT player, drag to look around, camera is pulled in by walls),
  10 s countdown then back to the lobby. When 1 or 0 bots are left the banner changes to MATCH OVER / WINNER and PREV / NEXT disappear. XP / coins are still paid at the moment you die. A win still shows the victory card.
- **SIT / STAND button** (new round button, can be moved in the HUD layout editor): crouch speed 3.4, steadier aim (spread x0.6), eye 0.5 m lower, character crouch pose; RUN or JUMP stand you up (if there is room - ceilings are checked).
- **Movement numbers like DLM of the HTML**: walk 6.2, sprint 10 (not while crouching / aiming / healing), crouch 3.4, aim 4.2, heal 2.8. Pressing FIRE stops sprinting.
- **Blood / bone hit FX**: dark-red blobs + bone-white chips fly from every bullet hit on a bot (9 + 6 for headshots, 6 + 4 for body).
- **Bloom + ambient occlusion** (`PostFx.kt`, port of the ULTRA PIPELINE of the HTML): the scene is drawn into an off-screen colour + depth texture, then bright-pass + 2x separable blur (quarter res) = bloom, a 12-tap noise-rotated screen-space AO
  at half res, composited onto the screen. Settings -> Graphics: "Bloom (MID / HIGH)" and "Ambient occlusion (HIGH)" (both on by default). AO only on HIGH and not in the plane / freefall. Auto-performance like the HTML governor:
  if the frame time stays above ~32 ms for 2.5 s, AO is shed first, then bloom (not restored inside that match). If the device cannot create the framebuffer, the game silently draws straight to the screen as before.
- **Bots shoot out of cars** (`Bots.driveBot`): inside 48 m with a clear line of sight, slower fire rate (0.8-1.5 s) and a wider spread that grows with the car speed.
- **Brass casings + muzzle smoke**: every shot throws a brass casing (red shell for shotguns) out to the right, it bounces on the ground for ~1.8 s; a short grey puff of smoke at the muzzle.
- **Hair style + hair colour** (wardrobe row on the CHARACTER screen): SHORT / BUZZ / TIED / BALD and the 7 HAIRS colours of the HTML, saved with the profile (`Profile.hairStyle`, `Profile.hair`) and shown in the portraits (character screen, lobby, store).
  Like in the HTML, the textured 3D characters (survivor) have their hair baked into the model, so the 3D body does not change; the choice is stored and drawn on the portrait exactly like the HTML wardrobe preview.
- NOT compiled here (no Android SDK in this session): first Gradle sync may show small errors to fix. Brackets of every file were checked. Files changed: GameRenderer.kt, GameScreen.kt, HudLayout.kt, Bots.kt, PostFx.kt (new), Prefs.kt, SettingsView.kt, Account.kt, Characters.kt, PortraitRenderer.kt, CharacterSelectView.kt, LobbyView.kt, StoreView.kt, MainActivity.kt.

## Phase 24 (new): real-time sun shadows + swimming in deep water
**Swimming** (port of BERM.waterLevelAt / waterDepth / swimSpeed / swimFloor / swim mode and the DSW block of the HTML):
- `MapData.buildWater()` makes the water-level grid: sea cells (ground <= 0) = 0, lakes / rivers from the water meshes (same triangle raster as the HTML). `waterDepth`, `swimSpeed` (wading > 0.45 m = 0.8, deep > 1.25 m = 0.55), `swimFloor` (feet float 1.25 m under the surface, small bobbing), `terrainBlocked(..., swim = true)` lets the player walk into water cells (still stopped by cliffs and by the island circle of 790 x 4/3 m).
- Deep water: you float with your head above the surface, slower, **no sprint**, no footstep sounds; toast "SWIMMING / slower, no sprint...". Water landings with the parachute are no longer moved to land - you land in the water and swim to the shore.
- Effects: ripple rings (14 max) and splash drops (48 max) when you enter / leave deep water and while you wade or swim (same numbers as the HTML), new sounds `splash` / `swim`. Only the player swims (bots do not, like the HTML).
**Real-time shadows** (`ShadowMap.kt`, hooks in `GameRenderer`, `NatureGl`):
- One sun (same direction as the HTML light), orthographic depth map around the camera that snaps to whole texels (no shimmer), polygon offset + normal offset against acne, hardware PCF + 5 taps, soft fade at the edge of the box.
- Casters: houses / baked buildings / bridge (the groups with cast = 1), pines, oaks, palms, boulders (low-poly LOD in the depth pass, same wind sway), cars (box), bots (3-box body), your own body in third person. Bushes do not cast (like the HTML).
- Receivers: terrain, roads, buildings, loot, cars, trees (main shader + nature shader). The rigged characters and the gun in your hands do not receive shadows (they use their own shader), the gun is switched off on purpose.
- Settings > Graphics: new switch **Real-time shadows** (default ON). LOW = 1024 map / 80 m, MID = 1024 / 100 m, HIGH = 2048 / 115 m (HTML: 230 m box, 1024 on phones). Off in the plane and above 120 m in freefall.
- Version `24.0-native-phase24`. NOT compiled here (no Kotlin compiler / Android SDK in this session): brackets checked, first Gradle sync may show small errors to fix. If a phone shows a black screen on start, switch the shadows off in Settings and tell me the Logcat line "Shadow shader error".

## Phase 23 (new): ULTRA NATURE - trees, bushes, rocks, pebbles (the biggest feature that was still missing)
Port of `DLF` ("ULTRA NATURE") of the HTML v13. Files: `Nature.kt` (models + placement), `NatureGl.kt` (instanced drawing), hooks in `MapData.kt` / `GameRenderer.kt`.
- **Models** (same shapes + colours as the HTML): pine (7 cone tiers, wobble, low-poly LOD), broadleaf oak (7 lumpy leaf blobs + branches, LOD), palm (bent trunk + 9 fronds), bush (3 blobs), 3 faceted boulders (moss / strata tint). Vertex colour gradient bottom -> top, per-tree tint + size + tilt.
- **Placement** (same seed 20261013, same noise, phone caps): up to 1000 pines, 700 oaks, 1500 bushes, 260 palms, 420 boulders, 600 pebbles on the real green-ground mask (`H.grass` of `bermudaData.bin`, now parsed by `MapData.grassOK`).
  v13 ROAD-FREE rule: pine >= 4.5 m, oak >= 4 m, palm >= 3 m, bush >= 1.8 m, rock >= 2.2 m, pebble >= 1.2 m from the real asphalt (distance transform of the road triangles, 1 m cells). Nothing grows inside houses, on the viaduct or in water / cliffs. Spawn point is chosen after the trees, so nobody starts inside one.
- **Collision**: trunks and big boulders are collision boxes (same sizes as `BERM.addBlocker`): you, bots, cars, bullets, grenades and sight lines are stopped by them.
  To keep this fast `MapData` now has the HTML's 24 m collision grid (`boxesAt`); `blocked / resolve / rayWorld`, car pose checks and grenade bounce use it instead of scanning every box.
- **Drawing**: one instanced draw per 170 m tile and kind, frustum culled, 3 LOD tiers (full -> low-poly -> hidden, distances follow fog + scope zoom), wind sway in the vertex shader. Settings > Graphics (low / mid / high) scales the vegetation draw distance (0.6 / 0.85 / 1.0); the number of trees is always the same so gameplay is identical.
- Version `23.0-native-phase23`. NOT compiled here (no Kotlin compiler / Android SDK in this session): brackets checked, first Gradle sync may show small errors to fix.
- Still NOT done (needs a device to tune): bloom / AO post-processing, spectate after death, party / wardrobe hair styles. (Shadows + swimming: see Phase 24.)

## Phase 22 (new): ROAD-CLEAR houses + minimap that turns with you
- **Houses off the road** (`MapData.applyRoadClear`, `assets/maps/roadclear.bin`): the HTML's own `DLCLEAR.run` (v13 ROAD-FREE: 7 m clearance, porch / roof edge included) was run offline on this exact `bermudaData.bin`:
  80 of 221 original houses moved, 0 failed (same numbers as the HTML README). Collision boxes of each moved house (1146 boxes) move with it, the baked atlas / house triangles are cloned to the new spot
  (same rule as `moveGeo`). Checked: no moved house touches the 32 replacement houses. If the file is missing or damaged the original layout is used.
- **Minimap** (`GameScreen.drawMiniMap`): shows about 190 m around you since Phase 47 (was 330 m in v41 minimap of the HTML) and rotates so the way you look is always UP, red **N** marks north. In the plane it shows the whole island north-up.
  Map picture is now 512 px (was 256) so the zoomed view stays sharp. The full-screen map is unchanged (north-up).
- Version `22.0-native-phase22`. NOT compiled here (no Kotlin compiler / Android SDK in this session): brackets checked, first Gradle sync may show small errors to fix.

## Done in this zip (Phase 1)
- Native Kotlin project (no WebView, no HTML at runtime).
- ALL assets of the HTML decoded back to real files in `app/src/main/assets` (47 MB, byte-exact):
  maps (bermuda island, viaduct, houses), gun models + textures, vehicle models + textures,
  character textures/meshes, store items, loading-screen art. `manifest.txt` lists every file.
- Native loading screen (art + real progress bar + RETRY on error).
- Native lobby: profile card (name, ID, level, XP bar), coins/diamonds, settings button,
  STORE / CHARACTER / VAULT, START with pulse + shine, message toast.
- CREATE YOUR ID screen, character select (3 characters), account + XP table saved on device.

## Phase 2a (new): 3D Bermuda map in native OpenGL ES 3.0
- `MapData.kt` parses the original `bermudaData.bin` (terrain, roads, water, atlas buildings, bridge, houses,
  height-field, 3240 collision boxes) - format checked against the data (all indices/offsets valid).
- `GameRenderer.kt`: textured terrain/atlas, vertex-coloured houses, roads, sea, sun + hemisphere light, fog.
- Walk around in first person: left stick = move, drag right side = look, JUMP, RUN, EXIT.
  Water/cliffs block you, walls push you back, ground follows the height-field.
- Lobby START -> loading screen (real map-parse progress) -> map.

## Phase 3 (new): 3D guns + shooting
- All 13 guns (MP5, UMP, MP40, Vector, Bizon, P90, VSS, M590, VSK-94, Kar98k, M24, AWM, Barrett M82) converted
  from `gun3d.json` to `assets/models/guns/<id>.gun` (positions, normals, UV/vertex colours, indices, textures).
  The 3 vests (vest1-3) are converted too, ready for the armor phase. Original gun3d.json is kept untouched.
- Stats copied from the HTML GUNS table (magazine, damage, fire delay, reload, spread, range, recoil, semi-auto,
  head/body damage, shotgun = 8 pellets).
- First-person gun with bob, recoil kick, reload animation, muzzle flash; FIRE (hold = auto), RELOAD, JUMP, RUN,
  weapon chip (tap < or >) to cycle guns, crosshair, ammo counter, hit marker + damage / HEADSHOT numbers.
- Hit-scan with spread: bullets hit terrain / walls (bullet marks) and 6 training targets that respawn.

## Phase 4 (new): bots (enemy AI) + player health
- `Bots.kt`: 12 bots ported from botUpdate()/botShoot() (normal difficulty): 200 HP, see 53 m with line of sight,
  chase you, strafe inside 17 m, back off inside 8 m, walk 3.65 m/s (2.1 while fighting), fire every 0.45-0.9 s,
  spread 0.028 x gun factor, 9 damage, 64 m range, tracers. Each bot carries a random real 3D gun.
- Bots are box-built humanoids (like the HTML fallback avatar) with walking animation, fall over when dead,
  respawn after 8 s far from you. Health bar shows for 3 s after you hit one.
- You: 200 HP, red damage flash, ELIMINATED screen + respawn after 4 s, HUD shows ALIVE / KILLS / health.
- Your bullets hit bots (head = headshot damage), walls and terrain; training targets were replaced by bots.

## Phase 5 (new): loot, ammo, armor, medkits
- ~200 ground items on the map (60 weapons, ammo SMG/Sniper/SG, vests Lv.1-3 with their real 3D models,
  armor plates, medkits) + a starter ring around your spawn. Dead bots drop their gun and a supply item.
- You start with an MP5 + reserve ammo (SMG 90 / SG 16 / Sniper 20; caps 300 / 40 / 60, boxes 60 / 8 / 10 like the HTML).
- PICK UP button appears within 3 m of an item. 3 weapon slots (long / short / sniper): a new gun replaces the one in its
  slot and drops it. Weapon chip < > cycles your guns. Reload now uses reserve ammo.
- Armor: vests set armor to 100 and the vest level; armor absorbs 65% of every hit (same formula as damage() in the HTML).
- HEAL button: 2.2 s, uses a medkit, +100 HP (value assumed), cancelled if you are hit.
- HUD: reserve ammo, armor bar + vest level, medkit count, item prompt, pick-up toasts.

## Phase 6 (new): zone + full match (battle-royale loop)
- Shrinking safe zone with the HTML timeline (20 s wait, shrink to 75 s, wait to 95 s, shrink to 150 s, wait to 170 s,
  final shrink to 215 s, collapse). Radii are 8x the HTML values because Bermuda here is ~8x larger. Blue zone wall in 3D,
  HUD timer + metres to the edge. Outside: 8 HP/s (18 HP/s after 215 s), ignores armor - same for bots, who run for the circle.
- 30 bots spread over the island, no respawn. You win when every bot is dead, you lose when you die (bots / zone).
- Result screen: place, kills, time, +XP, +COINS with breakdown, DROP IN AGAIN (map is not re-parsed) or LOBBY.
- Rewards use the exact XP / coin formula of __dlXp (cap 1000 XP, level-up +100 coins per level) and are saved to your
  account, so the lobby profile card (level, XP bar, coins) updates. EXIT after 30 s counts as a quit (half XP).

## Phase 7 (new): scopes + aim down sights
- 38 scope items on the map (1X / 2X / 4X / 8X, same odds as rollScope(): 34 / 30 / 22 / 14 %) + 2 near your spawn.
  Picking one attaches it; the scope tag (next to the weapon chip) cycles AIM -> your scopes.
- AIM button (toggle): smooth zoom with the HTML values - iron sights 48 deg, 1X 44, 2X 34, 3X+ 25, hip fire 70 deg;
  look sensitivity scales down like dlSensScale (8X = 0.12, 4X = 0.24, 2X = 0.5, 1X = 0.8). Gun slides to the centre.
- Snipers / DMR / AMR with a 2X+ scope get a full sniper-scope overlay (lens mask, cross-hair, mil dots) with the
  HTML scope FOVs (8X = 8 deg, 4X = 16 deg, 2X = 34 deg). Aiming also tightens spread by 45 % (assumed value).
- Aim is cancelled automatically when you reload, heal, sprint or switch gun.

## Phase 8 (new): grenades
- NADE button throws a grenade (you start with 2 - assumed - max 6; 16 grenade pick-ups on the map, drops from loot).
  Throw = 15 m/s along your aim + upward arc, 0.85 s cooldown, 2.6 s fuse, like throwGrenade().
- Physics copied from updateGrenades(): gravity 14, bounce 0.42, floor friction 0.67, comes to rest below 1 m/s,
  collides with terrain and every building collision box (swept in small steps).
- Explosion: 9 m radius, damage 125 * (1 - d/9)^1.15, only with a clear line from the blast (like detonate()),
  hurts you too (armor absorbs), bots, and other bots. Kills by your grenade count as kills and drop loot.
  Orange expanding fireball effect.
- Bots carry 1 grenade (assumed) and throw it at you from 10-28 m when they can see you (ballistic arc, small error).

## Phase 9 (new): plane -> freefall -> parachute (the match now starts in the air)
- Every round starts inside the aircraft. The route is RANDOM each round (any direction, random sideways offset),
  speed 47.3 m/s at 520 m altitude (FLIGHT / AIR_Y of the HTML). Chase camera around the plane (26 m back, look around by dragging).
- DROP button (the old JUMP circle) ejects you after 2 s; automatic jump at 36 s. Freefall = head-first dive: look down = faster fall,
  look at the horizon = glide further, stick forward/back = speed (dlFreefall formulas). From 300 m AGL the button turns into CHUTE; the
  parachute also opens by itself after 5 s or below 90 m. Under the canopy: stick steers (17 m/s, flare near the ground), same numbers as descend().
- Landing starts the match clock / zone timer (like `elapsed = 0` on landing in the HTML), weapons unlock, water landings are moved to land.
- Bots ride the same plane: they jump one after another while the plane is over the island, dive toward a landing spot near the route,
  open the chute below 90 m (48 m when not diving) and then switch to the normal bot AI. Parachuting bots have a visible canopy.
- Clear sky while in the plane / freefall (fog pushed far away), fog fades back in near the ground. Box-built plane model drawn along the route.
- Bots / loot / route now use a fresh random seed every round (the old fixed seed gave the same bots and loot every match).
- Assumed / not 1:1 yet: first-person view in freefall and chute (no player character model yet), no skate-stance freefall toggle (V key),
  no plane intro camera swing, no propeller / baked plane model (the HTML plane is a baked GLB-style mesh; here boxes are used).

## Phase 10 (new)
- **Zone v39** (exact ZP numbers of the HTML): radii 720/460/270/150/70/25 m, zone 1 shown at 60 s, first shrink at 150 s, wait/shrink times,
  random on-land targets inside the previous circle, white "next zone" wall + blue wall that moves, damage 1..6 HP per 2 s (8 after the final wait). Bots run to the moving centre.
- **Sound engine** (SoundFx.kt): every effect of audio.event() (shots per slot, reload, step, jump, land, chute, throw, bounce, blast, hit, hurt, pickup, heal)
  synthesised with the same frequencies / gains, distance + pan for bot shots and blasts, wind loop (freefall / parachute), aircraft motor loop. Vibration on hits.
- **Settings screen** (lobby gear + in-match gear, pauses the match): sensitivity (general, red dot, 2x, 4x, 8x, free camera, vertical ratio, invert Y, Low/Default/High presets),
  volume / effects / vibration, auto-pickup switches + FAST, graphics (render resolution), mini-map on/off, account. Saved on the device.
- **Auto pickup** for medkits, mushrooms, ammo, armor, grenades (1.5 m, 2.6 m FAST) like S.pick in the HTML.
- **Mushrooms + EP**: red mushroom items, EAT button (+60 EP, max 250), EP converts to HP 1/s, gold EP bar.
- **Airdrops** at 75 / 240 / 420 s: cargo plane, parachute crate, gold beam, crate opens on landing with Lv4 vest + Lv4 helmet, weapons, ammo, medkit, grenade.
  Lv4 gear absorbs 40 % (vest) / 22 % (helmet) of damage first (HTML damage() wrapper). HUD tag "AIRDROP · n m".
- **Mini-map** (top-left, island picture from the height-field, zone circles, airdrops, your arrow) + **full-screen map** (tap the mini-map) with tap-to-place **pin** (distance in the zone bar).
- **Kill feed**, EXIT moved top-right.
- **STORE** (4 tops, 99 diamonds, BUY/WEAR/REMOVE) and **VAULT** (outfits, my clothes, power, others/reset) screens, saved in the account.
  Diamonds only come from top-up in the HTML (not available offline); Settings > Account has "+100 DIAMONDS (OFFLINE TEST)" so the store can be tried.

## Phase 11 (new): vehicles (5 real 3D models, drivable)
- `Vehicles.kt`: loads `assets/models/vehicles/vehicles.json` (FIELD SUV, FF CAR, LAMBORGHINI, MONSTER TRUCK, FIELD JEEP: body + 4 separate wheels + textures).
- ~44 cars spawn like dlCarSpawns() (24 fixed map spawns + seeded random road / land spawns, same LCG seed, car footprint check carOK, overlap check).
  `MapData` now parses `cars`, builds the road mask and carOK().
- USE button near a car (3.6 m) -> you drive. Physics copied from stepVehicle(): 20 m/s (pickup 22), boost x1.65 for 5 s (20 s cycle), reverse 7 m/s,
  speed-dependent steering, sub-stepped collision with buildings / other cars / water / cliffs, wall impact sound, body tilts with the terrain, wheels roll and front wheels steer.
- Driving HUD: LEFT / RIGHT, GAS, BRAKE (reverse), AUTO (autopilot), BOOST (timer), HORN, MED, GET OFF (needs < 5 km/h and a free exit spot), speedometer.
- Third-person chase camera (9.5 m boom, drag to look), engine sound pitched by speed, roadkill on bots (speed x 7.5 damage, kills count), cars drawn on the mini-map / big map.
- On foot you cannot walk through cars (part boxes block you).
- Not 1:1 yet in this phase: bots do not drive cars, no player character shown inside the car, no knock-back ragdoll for run-over bots, bullets do not hit car bodies.

## Phase 13 (new): the rigged 3D characters (Survivor; the other skins were removed on request)
- `CharacterModel.kt`: loads the REAL character meshes (`models/character/bin_04/05/06.bin` + their textures) with the rig data of the HTML
  (`crim.json`, `survivor.json` = bones, bind pose, rifle pose, elbow offsets, mesh offsets, taken from CRIM_DATA / SV_DATA).
  GPU skinning (12 bones: the 11 of the HTML + the extra 'head' bone, verts above y=1.72 re-weighted exactly like crimInit()), same lighting / fog as the map shader.
- Animation = the HTML rig: walk swing 0.5 / sprint 1.1 rad, knee bend, rifle pose (upper arm quaternion + forearm E1/fq of the HTML), relaxed pose, driving pose, freefall and parachute poses.
- **Bots** are now drawn with these skins (Survivor). Distance LOD: beyond 85 m (Survivor 45 m, 84k vertices) the old box body is used. Dead bots fall over, parachuting bots use the dive / chute pose.
- **Driver**: you sit in the driver seat of the car (seat point of seatDriver(): -0.48, 0.48, -0.1), legs bent, hands forward, head follows the steering. Uses the character you picked.
- **3D lobby avatar** (`LobbyAvatarView.kt`): your picked character (real mesh + textures) stands behind the lobby UI, drag to turn it, slow turntable when idle.
- `assets/manifest.txt` lists the 3 new json files. All 3D models are untouched byte-for-byte (verified against the HTML data).
- Compile check: all Kotlin sources compile with kotlinc 1.9.24 against android.jar API 34 (androidx stubbed) with 0 errors. NOT run on a device.
- Assumed / not 1:1: the gun in the bot's hands keeps the Phase 3 offset (hand IK is not re-fitted to every gun), no third-person view for the player on foot yet, no store tops on the 3D body, no outfit colours (the 3 characters have their own textured outfit in the HTML as well).

Compile check: all Kotlin sources were compiled with kotlinc 1.9 against android.jar API 34 (androidx stubbed) without errors.
Phase 11 code was only syntax-checked (brace balance), NOT compiled and NOT run: first Gradle sync may show small errors to fix.
Not run on a device / emulator - first Gradle sync or runtime tuning may still need small fixes.

## Phase 15 (new): third-person view
- **Third-person view on foot**: Settings > Graphics > "Third-person view (on foot)". Camera 3.4 m behind / 0.55 m to the right, pulled in by walls and terrain;
  you see your picked rigged character (rifle pose, walk cycle) from behind. Saved on the device. First-person gun is hidden in this mode.

## Phase 16 (new): bots driving cars, smart-bot cover AI
- **HUD layout editor** (`HudEditView.kt`, `HudLayout.kt`): Settings > DISPLAY > EDIT HUD LAYOUT. Drag any round button (JUMP, RUN, FIRE, RELOAD, HEAL, AIM, NADE, EAT, USE and the 8 driving buttons)
  to where your thumb likes it, SIZE -/+ for the selected one (60 %..160 %), RESET, MODE switch ON FOOT / DRIVING, DONE saves. Position is stored as a fraction of the screen so it works on every phone;
  hit-testing in the match uses the same layout. RESET HUD LAYOUT is also in Settings. (Joystick keeps its floating behaviour.)
- **Bots drive cars**: a bot that is calm and far from you walks to a free car within 45 m (max 6 drivers at once), sits in the driver seat (its rigged character in DRIVE pose) and drives with the SAME `drive()` physics as you
  (20 m/s, steering, collisions with buildings / cars / water). Goal = safe zone when urgent, you when you are within 140 m, else a far wander point. It gets out within 26 m of you (to fight), when it arrives, or when it is stuck
  3 times (reverse + turn-away manoeuvre when blocked). Bot cars run over other bots (speed x 7.5) and over YOU (speed x 5, max 120, armor applies). Bots cannot be carjacked while driving; a killed driver frees the car.
- **Smart cover AI**: a bot that is hit while it can see you looks for a spot 3-11 m away where both head and chest line to you are blocked by walls / model buildings (ray test), sprints there, waits 2.5-4.5 s, then comes back out
  to fight. 9 s cooldown, falls back to normal behaviour if no cover exists. Works with the Phase 14 collision of the 4 model maps.
- NOT compiled here (no Kotlin compiler in this session): first Gradle sync may show small errors to fix. Driving bots do not shoot from the car, kills by bot cars drop no loot, bot-vs-bot fights are still missing.

## Phase 17 (new): bot-vs-bot fights
- Every grounded bot now looks for the nearest visible enemy BOT (53 m, line of sight through walls / model buildings) as well as the player; the closer one is the target
  (HTML: bots pick the nearest visible target, including the player). The bot walks to it, strafes inside 17 m, backs off inside 8 m, faces it and fires with the same
  spread / damage / fire delay as against the player (9 damage, 0.45-0.9 s, 64 m). Grenades are thrown at bot targets too (10-28 m).
- Bot kills: kill-feed line "A eliminated B [GUN]", the victim drops its gun + supply item (same as bots killed by you), bot cars that run over bots are credited too ([CAR]).
- Bots no longer board a car while an enemy bot is in sight.
- NOT compiled here (no Kotlin compiler in this session): first Gradle sync may show small errors to fix. Files changed: Bots.kt, GameRenderer.kt (1 hook line).

## Phase 18 (new): store clothes on the 3D body + arch viaduct physics
- **Store tops on the 3D body** (`Cloth.kt`, port of storeInit / storeGeo / storeTubeGeo / storeCutGeo / storeApply). The 4 tops (RAGLAN TEE, OLIVE SWEATER, HENLEY TEE, WHITE TEE) come from
  `assets/store/store.json` (`bin`: indices, bone indices / weights, baked vertex colours and the offline fit for each of the 3 characters). They are skinned to the SAME 12 bone matrices as the body,
  so they follow walk / sprint / rifle / driving / freefall / parachute animations. While a top is worn: Survivor jacket trimmed (1.2 < y < 1.8),
  Survivor mesh 6 is hidden (STORE_CH of the HTML), and bare skin arms (12-sided tubes along the rig's arm, per-character skin colour + wrist point) are drawn under the sleeves.
  Shown on the lobby 3D avatar, your body in third-person view and while you drive. What you equip in STORE / VAULT is stored in the account and read through `Wear.id`. Bots do not wear store tops (same as the HTML).
- **Arch viaduct physics** (`Viaduct.kt`, port of mkBRG()). Raster data decoded byte-exact from the HTML into `assets/maps/viaduct.bin` (304 x 40 cells of 0.5 m: nothing / deck / open arch / pier + deck-top and underside heights).
  Walkable deck (you and bots stand on it, you can land on it with the parachute), open arches you can walk under with a head bump while jumping (`ceil`), solid piers, rails that stop you at the deck edge (`blocks`),
  and `reshape()` lowers the baked ground ridge under the arches to the valley floor before the walk grid is built. Hooked into `ModelMaps.floorAt / blocks` so every system that already uses the 4 map-models (player, bots, grenades, airdrops, loot spots) respects it.
- NOT compiled here (no Kotlin compiler in this session): first Gradle sync may show small errors to fix. Not 1:1 yet: preview of a top in the store before buying (the store screens show the item picture, the 3D body shows it once worn), bullets do not hit the viaduct deck / piers, cars do not use the deck.

## Phase 19 (new): 3D preview of a top before you buy it
- STORE and VAULT now keep the right 30 % of the screen free for the 3D avatar (`LobbyAvatarView` in panel mode, your picked character, real mesh + rig). Tap a t-shirt in the STORE: the avatar puts that top on
  immediately (caption "PREVIEW: name") - BUY / WEAR / REMOVE work as before and the preview follows. With nothing selected it shows the top you are wearing. Drag on the panel to turn the character.
- In the VAULT the avatar shows what you currently wear; changing the outfit (character tiles, MY CLOTHES, RESET OUTFIT) updates it at once.
- A preview never changes the account: `Wear.id` (used in the match) only changes when you WEAR / BUY. Files changed: StoreView.kt, LobbyAvatarView.kt, MainActivity.kt.
- NOT compiled here (no Kotlin compiler in this session): first Gradle sync may show small errors to fix.

## Phase 20 (new): bullets hit the viaduct + cars drive on the deck
- **Bullets / sight lines** (`Viaduct.ray`, hooked into `MapData.rayWorld`): the deck, the arch slabs (about 3 m thick) and the piers stop your bullets, bot bullets, bot line-of-sight (cover!), grenade line-of-sight and bullet marks.
  The ray is clipped to the viaduct box, marched in 0.2 m steps and refined by bisection (cheap: rays that miss the box cost nothing).
- **Cars on the deck**: `MapData.carY()` = deck height when the car is up on it, else the ground. Cars drive up from the hillside ends (ground there is ~20 m, deck 20.6 m), stay on the deck (rails + piers stop them,
  `Vehicles.poseClear`), tilt to the deck, drive over the arches and can also drive UNDER the arches on the valley floor. Bot drivers use the same code. Getting out of a car on the deck puts you on the deck (`exitSpot` uses `footY`),
  run-over bots on the deck stay on the deck.
- **Elevated bodies are not stopped by terrain below** (BERM.blocked() of the HTML: `y > 1.2 && y > ground + 1.5` -> no water / cliff check): `terrainBlocked(x, z, r, y)` has the new height argument. Without it you could not
  walk on the deck over the lowered valley. Used by player movement, bots, car pose checks, roadkill push.
- Grenades now bounce on the deck / model floors (`footY`, flat normal) instead of falling through to the valley.
- NOT compiled here (no Kotlin compiler in this session): first Gradle sync may show small errors to fix. Files changed: Viaduct.kt, MapData.kt, Vehicles.kt, Bots.kt, GameRenderer.kt.

## Open items
- Nothing from the HTML feature list is known to be missing any more (see Phase 24 - 28).
- Everything was written without a Kotlin compiler and never run on a device: see the Phase 28 test list for what to look at first.

## Phase 31 - Settings fix
- SettingsView: rows were rebuilt on every touch, so sliders / toggles / buttons never received touches. Rows are now cached per tab.
- Toggles: ON = yellow, OFF = white outline (no fill). Segmented buttons: selected = yellow, others = white outline.
- Sliders only react on the bar (scrolling the list no longer changes values); tap on the bar also sets the value.
- Free Camera slider now works (look speed while FIRE is held). Vibration toggle gives a test buzz. Notes wrap and update live.
- Bloom / AO / graphics changes apply when Settings closes (auto-performance is reset). Settings are saved on close and on app pause.
- RESET ALL SETTINGS now also resets HUD layout and third-person.
- Second pass: Volume slider / Sound effects ON play a test sound; saved values are range-checked on load; BOT DIFFICULTY label corrected (it applies instantly).
- Third pass: opening Settings in a match now releases every held finger (no stuck joystick / look / button afterwards).

## Phase 44 - WATCH VIDEO (AdMob rewarded ad)
- Lobby button `WATCH VIDEO +5 FREE DIAMONDS` (left of START). Tap -> full-screen AdMob rewarded video; close / back returns to the lobby (nothing restarts).
- Reward: random 1-5 diamonds, mostly 3-5 (1=4%, 2=8%, 3=28%, 4=30%, 5=30%), only when the video is watched to the end; saved at once.
- `AdManager.kt` preloads the next ad, handles "no internet / still loading / failed", and expires stale ads (1 h).
- The WATCH VIDEO button is shown only while an ad is ready (loaded + internet); it fades in/out by itself, and hides for 30 s after a watched video.
- TEST ids are used (unit `ca-app-pub-3940256099942544/5224354917`, app `ca-app-pub-3940256099942544~3347511713`). Before the Play Store release replace them in `AdManager.kt` and `AndroidManifest.xml`, and update Data safety / Ads declarations.


## Phase 47 - satellite minimap
- Minimap and full-screen map now show the real top-down satellite picture (`assets/textures/bermudaMini.jpg`, mirrored to the game's UV direction) instead of the height-field shading, so every building, road and tree line is visible.
- Houses that road-clear moved and the replacement houses (old landmark area) are redrawn from their real 3D roofs; their old spots are wiped (`MapImage.kt`).
- The round minimap shows only about 190 m of ground around the player (`MM_VIEW` in `GameScreen.kt`); in the plane it still shows the whole island.
- `MapData.loadExtraHouses`: the header counts triangles, not indices (3 indices each) - the old read cut the replacement houses short and misplaced their collision boxes.
