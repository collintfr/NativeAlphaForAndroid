package com.cylonid.nativealpha.browser

import java.net.IDN
import java.util.Locale

/**
 * Decides whether a host belongs to a web app's site. "Site" is approximated as the registrable
 * domain (eTLD+1): the last two labels, or three when the public suffix is a two-part ccTLD
 * such as co.uk / com.au. Extra allowed hosts also match their subdomains.
 */
object HostMatcher {

    @JvmStatic
    fun normalize(host: String): String {
        var h = host.trim().lowercase(Locale.ROOT).trimEnd('.')
        h = h.substringBefore(':') // drop port if present
        h = try { IDN.toASCII(h, IDN.ALLOW_UNASSIGNED) } catch (e: Exception) { h }
        return h.removePrefix("www.")
    }

    @JvmStatic
    fun registrableDomain(host: String): String {
        val h = normalize(host)
        if (h.isEmpty() || h.all { it.isDigit() || it == '.' } || h.contains(':')) return h // IP literal
        val labels = h.split('.')
        if (labels.size <= 2) return h
        val tld = labels.last()
        val second = labels[labels.size - 2]
        val twoPartSuffix = tld.length == 2 && (second in SECOND_LEVEL || second.length <= 2)
        val take = if (twoPartSuffix) 3 else 2
        return labels.takeLast(minOf(take, labels.size)).joinToString(".")
    }

    private val SECOND_LEVEL = setOf("co", "com", "net", "org", "gov", "edu", "ac", "or", "ne", "go", "gv", "mil", "nom", "sch", "ltd", "plc")

    private fun matchesDomainOrSub(host: String, domain: String): Boolean {
        val d = normalize(domain)
        return d.isNotEmpty() && (host == d || host.endsWith(".$d"))
    }

    @JvmStatic
    fun isSameSite(host: String, baseHost: String, allowedHosts: List<String>): Boolean {
        val h = normalize(host)
        if (h.isEmpty()) return true
        if (registrableDomain(h) == registrableDomain(baseHost)) return true
        return allowedHosts.any { matchesDomainOrSub(h, it) }
    }
}
