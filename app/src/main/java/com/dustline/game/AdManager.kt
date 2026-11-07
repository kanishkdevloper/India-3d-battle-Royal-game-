package com.dustline.game

import android.app.Activity
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlin.random.Random

/**
 * Lobby "WATCH VIDEO" rewarded ad (AdMob). The ad is preloaded in the background, so a tap shows it at once.
 * The ad is a separate full-screen Activity of the SDK: MainActivity only pauses (never restarts), and when the
 * player closes / back-presses the ad he is simply back in the lobby.
 *
 * LIVE jaane se pehle: REWARDED_UNIT_ID ko apni asli AdMob rewarded unit ID se badlo (aur AndroidManifest me APPLICATION_ID).
 */
class AdManager(private val act: Activity) {
    companion object {
        /** Google ki TEST rewarded ad unit ID (sirf testing ke liye). */
        const val REWARDED_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
        private const val EXPIRE_MS = 50L * 60_000L     // a loaded rewarded ad expires after 1 h
        private const val RETRY_MIN_MS = 15_000L
        private const val RETRY_MAX_MS = 5L * 60_000L
        private const val COOLDOWN_MS = 30_000L         // pause after a watched video, so the button cannot be spammed

        /** 1..5 diamonds, but mostly 3 / 4 / 5:  1 = 4%, 2 = 8%, 3 = 28%, 4 = 30%, 5 = 30%. */
        fun rollDiamonds(rnd: Random = Random.Default): Int {
            val weights = intArrayOf(4, 8, 28, 30, 30)
            var r = rnd.nextInt(weights.sum())
            for (i in weights.indices) { if (r < weights[i]) return i + 1; r -= weights[i] }
            return 4
        }
    }

    interface Listener {
        /** The player watched the video to the end: give the diamonds now (called before onClosed). */
        fun onReward(diamonds: Int)
        /** The ad screen was closed (back / X). rewarded = the reward was earned. */
        fun onClosed(rewarded: Boolean)
        /** Short text for the lobby (not ready / no internet / failed). */
        fun onMessage(text: String)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var ad: RewardedAd? = null
    private var loadedAt = 0L
    private var loading = false
    private var showing = false
    private var sdkReady = false
    private var started = false
    private var retryMs = RETRY_MIN_MS
    private var cooldownUntil = 0L

    /** true only when a fresh ad is loaded, none is showing and the cooldown is over -> the lobby shows the WATCH VIDEO button only then. */
    fun isReady(): Boolean = sdkReady && !showing && fresh() && System.currentTimeMillis() >= cooldownUntil

    /** Call once (after boot). Initialises the SDK off the UI thread and preloads the first ad. */
    fun start() {
        if (started) return
        started = true
        Thread {
            try {
                MobileAds.initialize(act) { sdkReady = true; act.runOnUiThread { load() } }
            } catch (_: Throwable) { }
        }.start()
    }

    private fun fresh() = ad != null && System.currentTimeMillis() - loadedAt < EXPIRE_MS

    fun load() {
        if (!sdkReady || loading || showing || fresh() || act.isDestroyed || act.isFinishing) return
        ad = null; loading = true
        try {
            RewardedAd.load(act, REWARDED_UNIT_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
                override fun onAdLoaded(a: RewardedAd) { ad = a; loadedAt = System.currentTimeMillis(); loading = false; retryMs = RETRY_MIN_MS }
                override fun onAdFailedToLoad(e: LoadAdError) {
                    ad = null; loading = false
                    // no fill / no internet: try again later, with growing pauses
                    handler.postDelayed({ load() }, retryMs)
                    retryMs = (retryMs * 2).coerceAtMost(RETRY_MAX_MS)
                }
            })
        } catch (_: Throwable) { loading = false }
    }

    private fun online(): Boolean = try {
        val cm = act.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: Throwable) { true }

    fun show(l: Listener) {
        if (showing) return
        val a = ad
        if (a == null || !fresh()) {
            ad = null
            if (!online()) { l.onMessage("NO INTERNET - CONNECT TO WATCH ADS"); return }
            retryMs = RETRY_MIN_MS; load()
            l.onMessage("AD LOADING... TRY AGAIN IN A FEW SECONDS")
            return
        }
        var rewarded = false
        a.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                showing = false; ad = null
                if (rewarded) cooldownUntil = System.currentTimeMillis() + COOLDOWN_MS
                l.onClosed(rewarded)
                load()                       // next ad is ready for the next tap
            }
            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                showing = false; ad = null
                l.onMessage("AD FAILED - TRY AGAIN")
                load()
            }
        }
        showing = true
        try {
            a.show(act, OnUserEarnedRewardListener { _ ->
                if (!rewarded) { rewarded = true; l.onReward(rollDiamonds()) }
            })
        } catch (t: Throwable) {
            showing = false; ad = null
            l.onMessage("AD FAILED - TRY AGAIN"); load()
        }
    }
}
