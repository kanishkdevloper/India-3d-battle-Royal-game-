package com.dustline.game

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Flow (same order as setFlow() in the HTML):
 * initialLoading -> login (CREATE YOUR ID, first run only) -> lobby <-> customize(character)
 * lobby START -> preMatchLoading -> (match engine: phase 2)
 */
class MainActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var loading: LoadingView
    private lateinit var lobby: LobbyView
    private lateinit var charSel: CharacterSelectView
    private var idScreen: IdScreenView? = null
    private var account: Account? = null
    private lateinit var profile: Profile
    private var preloader: AssetPreloader? = null
    private var booted = false
    private var game: GameScreen? = null
    private var mapData: MapData? = null
    private var pendingMsg: String? = null
    private lateinit var storeView: StoreView
    private lateinit var settingsView: SettingsView
    private lateinit var hudEdit: HudEditView
    private lateinit var profileView: ProfileView
    private var sfx: SoundFx? = null
    private lateinit var avatar: LobbyAvatarView
    private lateinit var ads: AdManager
    private var adReward = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        root = FrameLayout(this)
        setContentView(root)

        account = Store.load(this)
        Prefs.load(this)
        sfx = SoundFx(this)
        profile = Store.loadProfile(this)
        ads = AdManager(this)

        lobby = LobbyView(this).apply {
            visibility = View.GONE
            onStart = { startMatchLoading() }
            onStore = { openStore(false) }
            onVault = { openStore(true) }
            onSettings = { openSettings(false) }
            onPlus = { setMessage("TOP-UP NOT AVAILABLE OFFLINE") }
            onWatchAd = { watchAd() }
            adReady = { ads.isReady() && game == null }
            onProfile = { openProfile() }
            onCharacter = { showCharacters() }
        }
        charSel = CharacterSelectView(this).apply {
            visibility = View.GONE
            onBack = { showLobby() }
            onPick = { ch -> profile.char = ch.id; Store.saveProfile(this@MainActivity, profile); this.profile = profile; invalidate() }
            onHair = { Store.saveProfile(this@MainActivity, profile); invalidate() }
        }
        storeView = StoreView(this).apply {
            visibility = View.GONE
            onClose = { visibility = View.GONE; showLobby() }
            onChanged = { account?.let { Store.save(this@MainActivity, it) }; Store.saveProfile(this@MainActivity, profile); avatar.charId = profile.char; invalidate() }
            onToast = { m -> lobby.setMessage(m) }
            onPreview = { id -> avatar.previewWear = id }
            onRotate = { dx -> avatar.rotateBy(dx) }
        }
        settingsView = SettingsView(this).apply {
            visibility = View.GONE
            onClose = { closeSettings() }
            onSoundTest = { sfx?.play("pickup") }
            onEditHud = { settingsView.visibility = View.GONE; hudEdit.bringToFront(); hudEdit.visibility = View.VISIBLE; hudEdit.invalidate() }
            onAccountChanged = { account?.let { Store.save(this@MainActivity, it) }; invalidate() }
        }
        hudEdit = HudEditView(this).apply {
            visibility = View.GONE
            onClose = { visibility = View.GONE; Prefs.save(this@MainActivity); settingsView.visibility = View.VISIBLE; settingsView.invalidate() }
        }
        profileView = ProfileView(this).apply {
            visibility = View.GONE
            onClose = { visibility = View.GONE; showLobby() }
            onEditName = { editName() }
            onSaved = { account?.let { Store.save(this@MainActivity, it) } }
            onSettings = { openSettings(false) }
            onRotate = { dx -> avatar.rotateBy(dx) }
        }
        loading = LoadingView(this).apply { onRetry = { beginBoot() } }
        val mp = ViewGroup.LayoutParams.MATCH_PARENT
        avatar = LobbyAvatarView(this).apply { visibility = View.GONE; charId = profile.char }
        root.addView(avatar, mp, mp)
        root.addView(lobby, mp, mp)
        root.addView(charSel, mp, mp)
        root.addView(storeView, mp, mp)
        root.addView(settingsView, mp, mp)
        root.addView(hudEdit, mp, mp)
        root.addView(profileView, mp, mp)
        root.addView(loading, mp, mp)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { handleBack() }
        })
        beginBoot()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val c = WindowInsetsControllerCompat(window, window.decorView)
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun beginBoot() {
        loading.clearError(); loading.setIndeterminate(); loading.visibility = View.VISIBLE
        preloader?.cancel()
        preloader = AssetPreloader(this).also {
            it.start(object : AssetPreloader.Listener {
                override fun onProgress(p: Float) = loading.setProgress(p)
                override fun onDone() { booted = true; loading.postDelayed({ afterBoot() }, 500) }
                override fun onError(msg: String) = loading.showError(msg)
            })
        }
    }

    private fun afterBoot() {
        loading.visibility = View.GONE
        if (account == null) showIdScreen() else showLobby()
        ads.start()   // AdMob init + preload of the rewarded video (once)
    }

    /** Lobby WATCH VIDEO: full-screen rewarded ad. Reward = 1..5 diamonds (mostly 3-5), saved at once. Closing the ad returns to the lobby - nothing restarts. */
    private fun watchAd() {
        if (game != null || lobby.visibility != View.VISIBLE || account == null) return
        adReward = 0
        ads.show(object : AdManager.Listener {
            override fun onReward(diamonds: Int) {
                val a = account ?: return
                a.diamonds += diamonds; Store.save(this@MainActivity, a)
                adReward = diamonds
            }
            override fun onClosed(rewarded: Boolean) {
                lobby.account = account; lobby.invalidate()
                if (rewarded && adReward > 0) lobby.setMessage("+$adReward FREE DIAMOND" + (if (adReward > 1) "S" else ""))
                else lobby.setMessage("WATCH THE FULL VIDEO TO GET DIAMONDS")
                adReward = 0
            }
            override fun onMessage(text: String) { lobby.setMessage(text) }
        })
    }

    private fun showIdScreen() {
        val v = IdScreenView(this)
        idScreen = v
        v.onConfirm = { name ->
            account = Store.create(this, name)
            root.removeView(v); idScreen = null
            showLobby()
        }
        root.addView(v, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        v.focusInput()
    }

    private fun showLobby() {
        charSel.visibility = View.GONE; storeView.visibility = View.GONE
        lobby.account = account; lobby.profile = profile
        avatar.panel = false; avatar.panelLeft = false; avatar.usePreview = false
        avatar.charId = profile.char; avatar.visibility = View.VISIBLE; avatar.onResume()
        lobby.visibility = View.VISIBLE; lobby.invalidate()
    }

    private fun hideAvatar() { avatar.visibility = View.GONE; avatar.onPause() }

    private fun openStore(vault: Boolean) {
        lobby.visibility = View.GONE
        // 3D preview of the top on your character (right panel of the store, left panel of the vault)
        avatar.panel = true; avatar.panelLeft = vault; avatar.panelFrac = if (vault) StoreView.VAULT_AV else 1f - StoreView.AVATAR_X; avatar.usePreview = true; avatar.previewWear = Wear.id; avatar.charId = profile.char
        avatar.visibility = View.VISIBLE; avatar.onResume()
        storeView.account = account; storeView.profile = profile; storeView.open(vault); storeView.visibility = View.VISIBLE
        // VAULT comes in from the opposite side of the STORE: the panel slides in from the right while the character stands on the left
        storeView.animate().cancel()
        if (vault) { storeView.translationX = root.width * 0.28f; storeView.alpha = 0f; storeView.animate().translationX(0f).alpha(1f).setDuration(260L).start() }
        else { storeView.translationX = 0f; storeView.alpha = 1f }
    }

    private fun openSettings(inMatch: Boolean) {
        settingsView.account = account; settingsView.inMatch = inMatch; settingsView.bringToFront(); settingsView.visibility = View.VISIBLE; settingsView.invalidate()
        if (inMatch) game?.setPaused(true)
    }

    private fun closeSettings() {
        settingsView.visibility = View.GONE; Prefs.save(this)
        game?.setPaused(false); game?.applyGfx()
        account?.let { lobby.account = it; lobby.invalidate() }
        if (profileView.visibility == View.VISIBLE) profileView.invalidate()
    }

    /** NEW profile screen: the game's lobby background + the 3D character (right half) stay behind the profile UI. */
    private fun openProfile() {
        lobby.visibility = View.GONE
        avatar.panel = true; avatar.panelLeft = false; avatar.panelFrac = 0.5f; avatar.usePreview = false; avatar.charId = profile.char
        avatar.visibility = View.VISIBLE; avatar.onResume()
        profileView.account = account; profileView.profile = profile; profileView.open()
    }

    /** pfGear / pfEdit of the HTML: change the name (3 - 16 characters). */
    private fun editName() {
        val a = account ?: return
        val input = android.widget.EditText(this).apply { setText(a.name); filters = arrayOf(android.text.InputFilter.LengthFilter(16)); setSelectAllOnFocus(true) }
        val dlg = android.app.AlertDialog.Builder(this).setTitle("CHANGE NAME").setView(input)
            .setPositiveButton("SAVE", null).setNegativeButton("CANCEL", null).create()
        dlg.setOnShowListener {
            dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val v = input.text.toString().replace(Regex("\\s+"), " ").trim()
                if (v.length < 3 || v.length > 16) { input.error = "NAME MUST BE 3 - 16 CHARACTERS"; return@setOnClickListener }
                a.name = v; Store.save(this, a); profileView.invalidate(); dlg.dismiss()
            }
        }
        dlg.show()
    }

    private fun showCharacters() {
        hideAvatar(); lobby.visibility = View.GONE
        charSel.account = account; charSel.profile = profile
        charSel.visibility = View.VISIBLE; charSel.invalidate()
    }

    /** START -> loading screen with REAL progress while the Bermuda map is parsed -> 3D match (OpenGL ES). */
    private fun startMatchLoading() {
        hideAvatar(); lobby.visibility = View.GONE
        loading.clearError(); loading.setLabel("Loading..."); loading.setProgress(0f); loading.visibility = View.VISIBLE
        val cached = mapData
        if (cached != null) { loading.setProgress(1f); loading.postDelayed({ beginMatch(cached); loading.visibility = View.GONE }, 500); return }
        Thread {
            try {
                val map = MapData(this) { p -> runOnUiThread { loading.setProgress(p * 0.9f) } }
                try { VehicleModels.load(this) } catch (e: Throwable) { }   // decode the 5 vehicle models off the UI thread
                runOnUiThread {
                    mapData = map
                    beginMatch(map)
                    loading.setProgress(1f)
                    loading.postDelayed({ loading.visibility = View.GONE }, 600)
                }
            } catch (e: Throwable) {
                runOnUiThread {
                    loading.showError("Map error: ${e.message ?: e.javaClass.simpleName}")
                    loading.onRetry = { loading.onRetry = { beginBoot() }; startMatchLoading() }
                }
            }
        }.start()
    }

    private fun beginMatch(map: MapData, warm: Boolean = true) {
        game?.let { it.onPause(); root.removeView(it) }
        val g = GameScreen(this, map, sfx, { r -> leaveMatch(r) }, { r -> applyResult(r) }, { restartMatch() }, { openSettings(true) }, warm, { mapData?.let { beginMatch(it, false) } })
        g.setCharacter(profile.char)
        game = g
        root.addView(g, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun restartMatch() { mapData?.let { beginMatch(it) } }

    /** XP / coins / level-up exactly like __dlXp in the HTML; saved on the device. */
    private fun applyResult(r: MatchResult) {
        val a = account ?: return
        val before = a.level
        a.xp += r.xp; a.coins += r.coins
        a.record(r, r.coins)
        val after = a.level
        if (after > before) { a.coins += 100L * (after - before); pendingMsg = "LEVEL UP!  LV.$after  +${100 * (after - before)} COINS" }
        Store.save(this, a)
    }

    /** EXIT / LOBBY. A quit result (after 30 s) gives half XP, no placement bonus. */
    private fun leaveMatch(quit: MatchResult?) {
        if (quit != null) applyResult(quit)
        exitMatch()
        pendingMsg?.let { lobby.setMessage(it); pendingMsg = null }
    }

    private fun exitMatch() {
        game?.let { it.onPause(); root.removeView(it) }
        game = null
        showLobby()
    }

    private var lastBackMs = 0L

    /**
     * Back button / back gesture. With targetSdk 36 (Android 16) predictive back is always on and onBackPressed() is no
     * longer called, so the screens are walked here, topmost first: a sub-screen goes one step back, a running match opens
     * the pause menu (settings), and loading / login / lobby need a second press within 2 s to leave the game.
     */
    private fun handleBack() {
        when {
            loading.visibility == View.VISIBLE || idScreen != null -> confirmExit()
            hudEdit.visibility == View.VISIBLE -> hudEdit.onClose?.invoke()
            settingsView.visibility == View.VISIBLE -> settingsView.onClose?.invoke()
            profileView.visibility == View.VISIBLE -> profileView.back()
            storeView.visibility == View.VISIBLE -> storeView.onClose?.invoke()
            charSel.visibility == View.VISIBLE -> charSel.onBack?.invoke()
            game != null -> openSettings(true)
            else -> confirmExit()
        }
    }

    private fun confirmExit() {
        val now = System.currentTimeMillis()
        if (now - lastBackMs < 2000L) {
            finish()
        } else {
            lastBackMs = now
            android.widget.Toast.makeText(this, "Press back again to exit", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() { Prefs.save(this); game?.onPause(); if (avatar.visibility == View.VISIBLE) avatar.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); game?.onResume(); if (avatar.visibility == View.VISIBLE) avatar.onResume() }

    override fun onDestroy() { preloader?.cancel(); sfx?.release(); super.onDestroy() }
}
