package io.github.javiernarvaezz.shura.core.auth

/**
 * Where the sign-in `WebView` may go: a closed list, never a wildcard. Google's sign-in redirects through its
 * country domain (seen: `accounts.google.com.co`), so `accounts.google.<domain>` is allowed for each of Google's
 * official domains, plus the Google and YouTube hosts the flow uses. Pages may load Google's static and image hosts;
 * ads and analytics stay blocked (the sign-in works without them, measured in the E3 spike).
 */
object GoogleSignInHosts {
    /**
     * Google's official domains, from https://www.google.com/supported_domains (fetched 2026-10-15,
     * 187 domains). Refresh from that page if Google adds a country.
     */
    val officialDomains: Set<String> =
        """
            google.ad google.ae google.al google.am google.as google.at google.az google.ba google.be google.bf
            google.bg google.bi google.bj google.bs google.bt google.by google.ca google.cat google.cd google.cf
            google.cg google.ch google.ci google.cl google.cm google.cn google.co.ao google.co.bw google.co.ck
            google.co.cr google.co.id google.co.il google.co.in google.co.jp google.co.ke google.co.kr google.co.ls
            google.com google.co.ma google.com.af google.com.ag google.com.ar google.com.au google.com.bd
            google.com.bh google.com.bn google.com.bo google.com.br google.com.bz google.com.co google.com.cu
            google.com.cy google.com.do google.com.ec google.com.eg google.com.et google.com.fj google.com.gh
            google.com.gi google.com.gt google.com.hk google.com.jm google.com.kh google.com.kw google.com.lb
            google.com.ly google.com.mm google.com.mt google.com.mx google.com.my google.com.na google.com.ng
            google.com.ni google.com.np google.com.om google.com.pa google.com.pe google.com.pg google.com.ph
            google.com.pk google.com.pr google.com.py google.com.qa google.com.sa google.com.sb google.com.sg
            google.com.sl google.com.sv google.com.tj google.com.tr google.com.tw google.com.ua google.com.uy
            google.com.vc google.com.vn google.co.mz google.co.nz google.co.th google.co.tz google.co.ug
            google.co.uk google.co.uz google.co.ve google.co.vi google.co.za google.co.zm google.co.zw google.cv
            google.cz google.de google.dj google.dk google.dm google.dz google.ee google.es google.fi google.fm
            google.fr google.ga google.ge google.gg google.gl google.gm google.gr google.gy google.hn google.hr
            google.ht google.hu google.ie google.im google.iq google.is google.it google.je google.jo google.kg
            google.ki google.kz google.la google.li google.lk google.lt google.lu google.lv google.md google.me
            google.mg google.mk google.ml google.mn google.mu google.mv google.mw google.ne google.nl google.no
            google.nr google.nu google.pl google.pn google.ps google.pt google.ro google.rs google.ru google.rw
            google.sc google.se google.sh google.si google.sk google.sm google.sn google.so google.sr google.st
            google.td google.tg google.tl google.tm google.tn google.to google.tt google.vu google.ws
        """.split(Regex("""\s+"""))
            .filter {
                it.isNotEmpty()
            }.toSet()

    private val navigationExact = setOf("accounts.youtube.com", "music.youtube.com", "www.youtube.com")

    private val resourceDomains =
        listOf("gstatic.com", "googleusercontent.com", "ytimg.com", "ggpht.com", "youtube.com")

    /** Whether the sign-in page may navigate (top level) to [host]. */
    fun navigationAllowed(host: String): Boolean {
        val h = host.lowercase()
        return h in navigationExact || isGoogleHost(h)
    }

    /** Whether a sign-in page may load a resource (script, style, image) from [host]. */
    fun resourceAllowed(host: String): Boolean {
        val h = host.lowercase()
        return isGoogleHost(h) || resourceDomains.any { h == it || h.endsWith(".$it") }
    }

    /** A host under one of Google's official domains (`google.com`, `google.com.co`...), at any depth. */
    private fun isGoogleHost(host: String): Boolean = officialDomains.any { host == it || host.endsWith(".$it") }
}
