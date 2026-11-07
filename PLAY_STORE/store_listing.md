# Dustline - Google Play store listing (copy / paste)

## Main store listing
**App name** (max 30): `Dustline`

**Short description** (max 80 - this one is 63):
```
Offline 3D battle royale. Drop in, loot up and outlast 35 bots.
```

**Full description** (max 4000 - this one is 1206):
```
DUSTLINE is a solo 3D battle royale that runs completely offline. No internet, no account, no waiting for a lobby - just you, 35 bots and a shrinking island.

DROP IN
Ride the plane, choose your moment and jump. Dive head-first, glide in a skate stance, then open your parachute and land next to the loot you want.

LOOT AND FIGHT
- Search houses, supply drops and loot boxes dropped by fallen bots
- 13 firearms across SMG, shotgun, marksman and sniper classes, plus scopes
- Vests, helmets, medkits, armor plates and grenades
- Sprint, crouch, swim and take cover - then out-aim the bots

SURVIVE THE ZONE
The safe zone keeps shrinking. Stay inside it, find a vehicle and be the last fighter standing.

YOUR GAME, YOUR RULES
- Pick Easy, Normal or Hard bots
- Move and resize every HUD button with the built-in HUD editor
- Customize your fighter and outfit in the in-game store
- Track your profile and stats, and keep a photo album of your best moments

PLAYS OFFLINE
Dustline needs no internet connection and asks for no account. It contains optional rewarded video ads (watch a video for free diamonds) and no in-app purchases. Your progress is saved on your device.

Dustline is a game with simulated firearm combat against computer-controlled opponents.
```

**App category:** Game > Action  
**Contact email:** `YOUR_EMAIL` (required, shown publicly)  
**Privacy policy URL:** the public link of `docs/privacy-policy.html` (see BUILD_AND_UPLOAD.md, step "Privacy policy")

## Graphics (already made - `PLAY_STORE/assets/`)
| Play Console field | File | Size |
|---|---|---|
| App icon | `icon-512.png` | 512 x 512 |
| Feature graphic | `feature-graphic-1024x500.png` | 1024 x 500 |
| Phone screenshots | **you take these** (min. 2, best 4-8) | landscape 1920 x 1080 (16:9), JPEG or 24-bit PNG, max 8 MB each |

Screenshots must be real pictures of the running game: play a match on your phone, press power + volume-down in the
lobby, in the plane, in a firefight and at the safe zone. Do not add text claims that are not in the game.

## App content declarations (Play Console > Policy > App content)
| Section | Answer |
|---|---|
| Privacy policy | the URL above |
| Ads | **Yes**, the app contains ads (AdMob rewarded video, optional) |
| App access | All functionality is available without login / special access |
| Content rating (IARC questionnaire) | Answer honestly: *Violence* - yes, simulated shooting with realistic firearms against computer-controlled characters; no blood/gore setting, no online interaction, no purchases, no gambling. Expect roughly Teen / 12+ - 16+ |
| Target audience and content | **13+** age groups only. Do NOT tick "under 13" (that triggers the Families policy). |
| Data safety | *Does your app collect or share any of the required user data types?* **Yes** - the AdMob SDK collects Device or other IDs / app interactions for advertising (declare it; progress data stays on the device). Add a privacy-policy link. Then tick the privacy-policy link. |
| Advertising ID | **No** |
| Government app / Financial features / Health / News | **No** to all |
| In-app purchases | **None** (the TOP-UP button only shows "not available offline") |

## Release details
- **Package name:** `com.dustline.game`  (cannot be changed after the first upload)
- **Version:** versionCode 28, versionName 1.0.0 (raise `versionCode` in `app/build.gradle.kts` for every new upload)
- **Permissions:** only `VIBRATE` (normal permission, no declaration needed)
- **Supported devices:** Android 7.0+ (API 24), OpenGL ES 3.0, landscape only
