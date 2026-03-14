package com.nophubbing.presenceai.analytics

/**
 * AppCategoryClassifier.kt
 *
 * Maps package names to one of 7 attention categories.
 * Pure object — no IO, no Context.
 *
 * Categories:
 *  SOCIAL_MEDIA    — feeds, messaging apps with social graphs
 *  ENTERTAINMENT   — video, music, games, streaming
 *  INFORMATION     — news, browsers, search, productivity reading
 *  COMMUNICATION   — 1:1 messaging, email, calling
 *  PRODUCTIVITY    — work, study, utility apps
 *  SHOPPING        — e-commerce, food delivery
 *  OTHER           — anything not matched above
 *
 * Add more packages freely — follow the existing pattern.
 * Unknown packages fall through to OTHER automatically.
 */
object AppCategoryClassifier {

    enum class Category(val displayName: String, val emoji: String) {
        SOCIAL_MEDIA("Social Media",   "📱"),
        ENTERTAINMENT("Entertainment", "🎬"),
        INFORMATION("Information",     "📰"),
        COMMUNICATION("Communication", "💬"),
        PRODUCTIVITY("Productivity",   "⚡"),
        SHOPPING("Shopping",           "🛒"),
        OTHER("Other",                 "📦")
    }

    /** Time breakdown per category for one observation window. */
    data class CategoryBreakdown(
        val category: Category,
        val totalTimeMs: Long,
        val sessionCount: Int,
        val microSessionCount: Int,
        val packages: List<String>          // distinct packages seen in this window
    ) {
        val totalTimeS: Float get() = totalTimeMs / 1_000f
        val totalTimeMin: Float get() = totalTimeMs / 60_000f
        val microRatio: Float get() = if (sessionCount > 0) microSessionCount.toFloat() / sessionCount else 0f
    }

    // ── Package → Category map ────────────────────────────────────────────────

    private val PACKAGE_MAP: Map<String, Category> = mapOf(

        // ── Social Media ──────────────────────────────────────────────────────
        "com.instagram.android"         to Category.SOCIAL_MEDIA,
        "com.instagram.lite"            to Category.SOCIAL_MEDIA,
        "com.facebook.katana"           to Category.SOCIAL_MEDIA,
        "com.facebook.lite"             to Category.SOCIAL_MEDIA,
        "com.twitter.android"           to Category.SOCIAL_MEDIA,
        "com.twitter.lite"              to Category.SOCIAL_MEDIA,
        "com.X.android"                 to Category.SOCIAL_MEDIA,
        "com.snapchat.android"          to Category.SOCIAL_MEDIA,
        "com.reddit.frontpage"          to Category.SOCIAL_MEDIA,
        "com.zhiliaoapp.musically"      to Category.SOCIAL_MEDIA,  // TikTok
        "com.ss.android.ugc.trill"      to Category.SOCIAL_MEDIA,  // TikTok alt
        "com.linkedin.android"          to Category.SOCIAL_MEDIA,
        "com.pinterest"                 to Category.SOCIAL_MEDIA,
        "com.tumblr"                    to Category.SOCIAL_MEDIA,
        "com.quora.android"             to Category.SOCIAL_MEDIA,
        "com.sharechat.app"             to Category.SOCIAL_MEDIA,  // India
        "com.moj.app"                   to Category.SOCIAL_MEDIA,  // India
        "com.roposo.android"            to Category.SOCIAL_MEDIA,  // India
        "in.mohalla.video"              to Category.SOCIAL_MEDIA,  // ShareChat
        "com.meesho.supply"             to Category.SOCIAL_MEDIA,
        "com.koo.app"                   to Category.SOCIAL_MEDIA,

        // ── Entertainment ─────────────────────────────────────────────────────
        "com.google.android.youtube"    to Category.ENTERTAINMENT,
        "com.youtube.music"             to Category.ENTERTAINMENT,
        "com.netflix.mediaclient"       to Category.ENTERTAINMENT,
        "com.amazon.avod.thirdpartyclient" to Category.ENTERTAINMENT, // Prime Video
        "com.hotstar"                   to Category.ENTERTAINMENT,    // Disney+ Hotstar
        "com.viu.android"               to Category.ENTERTAINMENT,
        "com.sonyliv"                   to Category.ENTERTAINMENT,
        "com.zee5.android"              to Category.ENTERTAINMENT,
        "in.startv.hotstar"             to Category.ENTERTAINMENT,
        "com.jio.media.ondemand"        to Category.ENTERTAINMENT,    // JioCinema
        "com.jioCinema"                 to Category.ENTERTAINMENT,
        "com.spotify.music"             to Category.ENTERTAINMENT,
        "com.apple.android.music"       to Category.ENTERTAINMENT,
        "com.gaana"                     to Category.ENTERTAINMENT,
        "com.wynk.music"                to Category.ENTERTAINMENT,    // Wynk
        "com.saavn.android"             to Category.ENTERTAINMENT,    // JioSaavn
        "com.twitch.android.app"        to Category.ENTERTAINMENT,
        "air.com.adobe.flashplayer"     to Category.ENTERTAINMENT,
        "com.google.android.play.games" to Category.ENTERTAINMENT,
        "com.king.candycrushsaga"       to Category.ENTERTAINMENT,
        "com.supercell.clashofclans"    to Category.ENTERTAINMENT,
        "com.supercell.clashroyale"     to Category.ENTERTAINMENT,
        "com.vng.pubgmobile"            to Category.ENTERTAINMENT,
        "com.tencent.ig"                to Category.ENTERTAINMENT,    // BGMI/PUBG
        "com.dts.freefireth"            to Category.ENTERTAINMENT,
        "com.activision.callofduty.shooter" to Category.ENTERTAINMENT,
        "com.mojang.minecraftpe"        to Category.ENTERTAINMENT,
        "com.vlcforandroid.vlcmediaplayer" to Category.ENTERTAINMENT,
        "org.videolan.vlc"              to Category.ENTERTAINMENT,
        "com.mxtech.videoplayer.ad"     to Category.ENTERTAINMENT,

        // ── Communication ─────────────────────────────────────────────────────
        "com.whatsapp"                  to Category.COMMUNICATION,
        "com.whatsapp.w4b"              to Category.COMMUNICATION,   // WhatsApp Business
        "com.whatsapp.lite"             to Category.COMMUNICATION,
        "org.telegram.messenger"        to Category.COMMUNICATION,
        "org.telegram.plus"             to Category.COMMUNICATION,
        "com.google.android.apps.messaging" to Category.COMMUNICATION,
        "com.google.android.talk"       to Category.COMMUNICATION,
        "com.discord"                   to Category.COMMUNICATION,
        "com.microsoft.teams"           to Category.COMMUNICATION,
        "com.slack"                     to Category.COMMUNICATION,
        "com.skype.raider"              to Category.COMMUNICATION,
        "com.viber.voip"                to Category.COMMUNICATION,
        "com.imo.android.imoim"         to Category.COMMUNICATION,
        "com.truecaller"                to Category.COMMUNICATION,
        "com.hike.chat.stickers"        to Category.COMMUNICATION,
        "com.facebook.orca"             to Category.COMMUNICATION,   // Messenger
        "com.facebook.mlite"            to Category.COMMUNICATION,

        // ── Information / News / Browser ──────────────────────────────────────
        "com.google.android.googlequicksearchbox" to Category.INFORMATION,
        "com.android.chrome"            to Category.INFORMATION,
        "org.mozilla.firefox"           to Category.INFORMATION,
        "com.microsoft.bing"            to Category.INFORMATION,
        "com.opera.browser"             to Category.INFORMATION,
        "com.brave.browser"             to Category.INFORMATION,
        "com.duckduckgo.mobile.android" to Category.INFORMATION,
        "com.sec.android.app.sbrowser"  to Category.INFORMATION,     // Samsung Browser
        "com.UCMobile.intl"             to Category.INFORMATION,
        "com.uc.browser.en"             to Category.INFORMATION,
        "com.google.android.apps.magazines" to Category.INFORMATION, // Google News
        "com.inshorts.news"             to Category.INFORMATION,
        "com.dailyhunt"                 to Category.INFORMATION,
        "com.ndtv.news"                 to Category.INFORMATION,
        "com.abplive"                   to Category.INFORMATION,
        "in.news18"                     to Category.INFORMATION,
        "com.thehindu.app"              to Category.INFORMATION,
        "com.timesgroup.android"        to Category.INFORMATION,     // TOI
        "air.in.bhaskar.dainik"         to Category.INFORMATION,
        "com.aajtak.notnow"             to Category.INFORMATION,
        "com.wattpad.app"               to Category.INFORMATION,
        "com.medium.reader"             to Category.INFORMATION,
        "com.pocket"                    to Category.INFORMATION,

        // ── Productivity ──────────────────────────────────────────────────────
        "com.google.android.apps.docs"  to Category.PRODUCTIVITY,
        "com.google.android.apps.sheets" to Category.PRODUCTIVITY,
        "com.google.android.apps.slides" to Category.PRODUCTIVITY,
        "com.google.android.apps.drive" to Category.PRODUCTIVITY,
        "com.google.android.gm"         to Category.PRODUCTIVITY,    // Gmail
        "com.microsoft.office.word"     to Category.PRODUCTIVITY,
        "com.microsoft.office.excel"    to Category.PRODUCTIVITY,
        "com.microsoft.office.powerpoint" to Category.PRODUCTIVITY,
        "com.microsoft.office.outlook"  to Category.PRODUCTIVITY,
        "com.microsoft.onedrive"        to Category.PRODUCTIVITY,
        "com.notion.id"                 to Category.PRODUCTIVITY,
        "com.todoist.android"           to Category.PRODUCTIVITY,
        "com.ticktick.task"             to Category.PRODUCTIVITY,
        "com.evernote"                  to Category.PRODUCTIVITY,
        "com.google.android.keep"       to Category.PRODUCTIVITY,
        "com.adobe.reader"              to Category.PRODUCTIVITY,
        "com.zoho.books.accountant"     to Category.PRODUCTIVITY,
        "com.freshdesk.helpdesk"        to Category.PRODUCTIVITY,
        "com.duolingo"                  to Category.PRODUCTIVITY,
        "org.khanacademy.android"       to Category.PRODUCTIVITY,
        "com.byju.learning"             to Category.PRODUCTIVITY,
        "com.unacademy"                 to Category.PRODUCTIVITY,
        "com.toppr.learner"             to Category.PRODUCTIVITY,
        "com.calculator"                to Category.PRODUCTIVITY,
        "com.google.android.calculator" to Category.PRODUCTIVITY,

        // ── Shopping ──────────────────────────────────────────────────────────
        "com.amazon.mShop.android.shopping" to Category.SHOPPING,
        "com.flipkart.android"          to Category.SHOPPING,
        "com.myntra.android"            to Category.SHOPPING,
        "com.meesho.consumer"           to Category.SHOPPING,
        "com.snapdeal.main"             to Category.SHOPPING,
        "com.zomato.android"            to Category.SHOPPING,
        "in.swiggy.android"             to Category.SHOPPING,
        "com.blinkit.consumer"          to Category.SHOPPING,
        "com.grofers.consumer"          to Category.SHOPPING,
        "in.bigbasket.android"          to Category.SHOPPING,
        "com.nykaa.android.user"        to Category.SHOPPING,
        "com.paytm.android"             to Category.SHOPPING,
        "com.phonepe.app"               to Category.SHOPPING,
        "com.google.android.apps.nbu.paisa.user" to Category.SHOPPING, // GPay
        "net.one97.paytm"               to Category.SHOPPING,
        "com.ubercabs.driver"           to Category.SHOPPING,
        "com.olacabs.customer"          to Category.SHOPPING,
        "in.rapido.driver"              to Category.SHOPPING
    )

    // ── Prefix-based fallback (catches sub-packages) ──────────────────────────
    private val PREFIX_MAP: List<Pair<String, Category>> = listOf(
        "com.instagram"     to Category.SOCIAL_MEDIA,
        "com.facebook"      to Category.SOCIAL_MEDIA,
        "com.twitter"       to Category.SOCIAL_MEDIA,
        "com.snapchat"      to Category.SOCIAL_MEDIA,
        "com.reddit"        to Category.SOCIAL_MEDIA,
        "com.tiktok"        to Category.SOCIAL_MEDIA,
        "com.whatsapp"      to Category.COMMUNICATION,
        "org.telegram"      to Category.COMMUNICATION,
        "com.google.android.youtube" to Category.ENTERTAINMENT,
        "com.netflix"       to Category.ENTERTAINMENT,
        "com.spotify"       to Category.ENTERTAINMENT,
        "com.hotstar"       to Category.ENTERTAINMENT,
        "com.jio"           to Category.ENTERTAINMENT,
        "com.android.chrome" to Category.INFORMATION,
        "org.mozilla"       to Category.INFORMATION,
        "com.google.android.apps.docs" to Category.PRODUCTIVITY,
        "com.microsoft"     to Category.PRODUCTIVITY,
        "com.amazon"        to Category.SHOPPING,
        "com.flipkart"      to Category.SHOPPING,
        "com.zomato"        to Category.SHOPPING,
        "in.swiggy"         to Category.SHOPPING
    )

    /** Classify a single package name. Never returns null — falls back to OTHER. */
    fun classify(packageName: String): Category {
        PACKAGE_MAP[packageName]?.let { return it }
        PREFIX_MAP.firstOrNull { (prefix, _) -> packageName.startsWith(prefix) }?.let { return it.second }
        return Category.OTHER
    }

    /**
     * Build a per-category breakdown from a list of sessions.
     * Sessions are the merged, deduped list from FeatureExtractor.
     * Returns only categories with at least one session, sorted by total time descending.
     */
    fun buildBreakdown(
        sessions: List<FeatureExtractor.Session>
    ): List<CategoryBreakdown> {
        data class Acc(
            var totalMs: Long = 0,
            var sessions: Int = 0,
            var micro: Int = 0,
            val packages: MutableSet<String> = mutableSetOf()
        )
        val accMap = mutableMapOf<Category, Acc>()

        sessions.forEach { s ->
            val cat = classify(s.packageName)
            val acc = accMap.getOrPut(cat) { Acc() }
            acc.totalMs   += s.durationMs
            acc.sessions  += 1
            if (s.durationMs < 20_000) acc.micro += 1
            acc.packages.add(s.packageName)
        }

        return accMap.map { (cat, acc) ->
            CategoryBreakdown(
                category          = cat,
                totalTimeMs       = acc.totalMs,
                sessionCount      = acc.sessions,
                microSessionCount = acc.micro,
                packages          = acc.packages.toList()
            )
        }.sortedByDescending { it.totalTimeMs }
    }

    /** Top category by time spent. Null if no sessions. */
    fun dominantCategory(sessions: List<FeatureExtractor.Session>): Category? =
        buildBreakdown(sessions).firstOrNull()?.category
}
