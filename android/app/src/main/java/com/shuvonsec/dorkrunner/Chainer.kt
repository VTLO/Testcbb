package com.shuvonsec.dorkrunner

import android.net.Uri

/**
 * Chaining: given a dork that returned results (a finding), derive follow-up
 * dorks that dig deeper into the same lead — the "further investigation" step.
 *
 * The pivot is driven by the host/path of the top result link:
 *  - a discovered subdomain  -> re-run high-value categories scoped to that host
 *  - a cloud bucket host      -> enumerate that bucket's listings
 *  - a URL with a parameter   -> probe injectable-parameter patterns on that host
 * Everything stays within the originally supplied target's scope.
 */
object Chainer {

    data class Link(val label: String, val dork: String, val url: String)

    fun chainFrom(target: String, topLink: String): List<Link> {
        val host = hostOf(topLink)
        val scope = host?.takeIf { it.endsWith(target) || it.contains(target) } ?: target
        val follow = LinkedHashSet<String>()

        // Dig into the exact host that produced the hit.
        follow.add("site:$scope intitle:\"index of\"")
        follow.add("site:$scope ext:env")
        follow.add("site:$scope ext:sql OR ext:bak OR ext:old")
        follow.add("site:$scope inurl:admin OR inurl:login")

        // Cloud bucket pivot.
        val bucketHosts = listOf("s3.amazonaws.com", "storage.googleapis.com", "blob.core.windows.net")
        if (host != null && bucketHosts.any { host.endsWith(it) }) {
            follow.add("site:$host \"$target\"")
            follow.add("site:$host intitle:\"index of\"")
        }

        // Parameterized URL pivot (possible SSRF/redirect/LFI leads).
        if (topLink.contains("?") && topLink.contains("=")) {
            follow.add("site:$scope inurl:redirect= OR inurl:url= OR inurl:next=")
            follow.add("site:$scope inurl:file= OR inurl:path= OR inurl:include=")
        }

        return follow.map { Link(it, it, Dorks.googleUrl(it)) }
    }

    private fun hostOf(link: String): String? = try {
        Uri.parse(link).host?.lowercase()
    } catch (e: Exception) {
        null
    }
}
