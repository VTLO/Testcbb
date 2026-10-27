package com.shuvonsec.dorkrunner

import android.net.Uri

/**
 * Dork templates and report generation, ported 1:1 from scripts/dork_runner.py
 * (Google Dork Runner by Shuvonsec). Generates Google search URLs for a target
 * domain — the same links the Python script writes into its HTML report.
 */
object Dorks {

    /** Ordered so the UI spinner and the "all" category match the Python order. */
    val CATEGORIES: LinkedHashMap<String, List<String>> = linkedMapOf(
        "credentials" to listOf(
            "site:{target} ext:env",
            "site:{target} ext:env \"DB_PASSWORD\"",
            "site:{target} ext:env \"API_KEY\"",
            "site:{target} \"api_key\" OR \"apikey\"",
            "site:{target} \"secret_key\" OR \"SECRET_KEY\"",
            "site:{target} \"password\" filetype:log",
            "site:{target} \"password\" filetype:txt",
            "site:{target} ext:yaml \"password:\"",
            "site:{target} ext:json \"private_key\"",
            "site:{target} inurl:\".git/config\"",
            "site:{target} ext:pem \"BEGIN RSA PRIVATE KEY\"",
            "site:{target} \"aws_secret_access_key\"",
            "site:{target} \"AKIA\" intext:AKIA",
            "site:{target} ext:json \"type: service_account\"",
        ),
        "pii" to listOf(
            "site:{target} ext:csv intext:\"email\" intext:\"phone\"",
            "site:{target} ext:xls intext:\"ssn\"",
            "site:{target} ext:xlsx intext:\"date of birth\"",
            "site:{target} ext:csv \"first name\" \"last name\" \"email\"",
            "site:{target} filetype:csv \"password\" \"username\"",
            "site:{target} intitle:\"index of\" \"users.csv\"",
            "site:{target} intitle:\"index of\" \"customers.csv\"",
            "site:{target} ext:log intext:\"email\"",
            "site:{target} filetype:xls \"employee\" \"salary\"",
        ),
        "admin" to listOf(
            "site:{target} inurl:admin",
            "site:{target} inurl:/admin/login",
            "site:{target} inurl:/phpmyadmin",
            "site:{target} inurl:/jenkins",
            "site:{target} inurl:/grafana",
            "site:{target} inurl:/kibana",
            "site:{target} inurl:/actuator",
            "site:{target} inurl:/swagger-ui",
            "site:{target} inurl:/api-docs",
            "site:{target} intitle:\"admin panel\"",
            "site:{target} intitle:\"control panel\"",
            "site:{target} inurl:\"/wp-login.php\"",
        ),
        "errors" to listOf(
            "site:{target} \"SQL syntax\" OR \"mysql_fetch\"",
            "site:{target} \"Warning: mysql_\"",
            "site:{target} \"Fatal error:\" filetype:php",
            "site:{target} \"Stack trace:\" filetype:html",
            "site:{target} \"Traceback (most recent call last)\"",
            "site:{target} \"NullPointerException\"",
            "site:{target} \"DEBUG = True\" filetype:py",
            "site:{target} \"APP_DEBUG=true\" ext:env",
            "site:{target} inurl:phpinfo.php",
            "site:{target} ext:log \"error\"",
            "site:{target} intitle:\"index of\" \"error.log\"",
        ),
        "cloud" to listOf(
            "\"{target}\" site:s3.amazonaws.com",
            "\"{target}\" site:blob.core.windows.net",
            "\"{target}\" site:storage.googleapis.com",
            "\"{target}\" site:firebaseio.com",
            "{target}.s3.amazonaws.com",
            "intitle:\"index of\" site:{target}",
        ),
        "subdomains" to listOf(
            "site:*.{target}",
            "site:*.*.{target}",
            "site:*.{target} inurl:login",
            "site:*.{target} inurl:admin",
            "site:*.{target} inurl:api",
            "site:*.{target} inurl:staging",
            "site:*.{target} inurl:dev",
            "site:*.{target} inurl:test",
        ),
        "params" to listOf(
            "site:{target} inurl:url=http",
            "site:{target} inurl:redirect=http",
            "site:{target} inurl:next=http",
            "site:{target} inurl:?id=",
            "site:{target} inurl:?user_id=",
            "site:{target} inurl:search=",
            "site:{target} inurl:q=",
            "site:{target} inurl:file=",
            "site:{target} inurl:path=",
            "site:{target} inurl:include=",
            "site:{target} inurl:page=",
            "site:{target} inurl:debug=",
        ),
        "leaks" to listOf(
            "site:pastebin.com \"{target}\"",
            "site:pastebin.com \"{target}\" \"password\"",
            "site:github.com \"{target}\" \"password\"",
            "site:github.com \"{target}\" \"api_key\"",
            "site:github.com \"{target}\" \".env\"",
            "site:gist.github.com \"{target}\"",
            "site:notion.so \"{target}\"",
            "site:docs.google.com \"{target}\"",
            "site:trello.com \"{target}\"",
        ),
        "github" to listOf(
            "site:github.com \"{target}\" \"password\"",
            "site:github.com \"{target}\" \"api_key\"",
            "site:github.com \"{target}\" \"secret\"",
            "site:github.com \"{target}\" \"token\"",
            "site:github.com \"{target}\" extension:env",
            "site:github.com \"{target}\" filename:config.yml",
            "site:github.com \"{target}\" filename:.env",
            "site:github.com \"{target}\" \"BEGIN RSA PRIVATE KEY\"",
        ),
        "juicy" to listOf(
            "site:{target} intitle:\"index of\" \"backup\"",
            "site:{target} intitle:\"index of\" \"sql\"",
            "site:{target} intitle:\"index of\" \"dump\"",
            "site:{target} ext:sql",
            "site:{target} ext:bak",
            "site:{target} ext:old",
            "site:{target} inurl:backup",
            "site:{target} filetype:pdf \"confidential\"",
            "site:{target} filetype:pdf \"internal use only\"",
        ),
    )

    /** Category keys for the UI, with "all" appended (mirrors the Python default). */
    val CATEGORY_KEYS: List<String> = CATEGORIES.keys.toList() + "all"

    private fun templatesFor(category: String): List<String> =
        if (category == "all") CATEGORIES.values.flatten() else (CATEGORIES[category] ?: emptyList())

    data class Dork(val category: String, val dork: String, val url: String)

    fun googleUrl(dork: String): String =
        "https://www.google.com/search?q=${Uri.encode(dork)}&num=50"

    fun generate(target: String, category: String): List<Dork> {
        val t = target.trim()
        val out = ArrayList<Dork>()
        if (category == "all") {
            for ((cat, templates) in CATEGORIES) {
                for (tpl in templates) {
                    val d = tpl.replace("{target}", t)
                    out.add(Dork(cat, d, googleUrl(d)))
                }
            }
        } else {
            for (tpl in templatesFor(category)) {
                val d = tpl.replace("{target}", t)
                out.add(Dork(category, d, googleUrl(d)))
            }
        }
        return out
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    /** Clickable HTML report, same look as generate_html_report() in the Python script. */
    fun htmlReport(target: String, results: List<Dork>, timestamp: String): String {
        val sb = StringBuilder()
        sb.append(
            """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Dork Report — ${esc(target)}</title>
            <style>
              body { font-family: monospace; background: #1a1a1a; color: #e0e0e0; padding: 20px; }
              h1 { color: #ff4444; }
              h2 { color: #44aaff; border-bottom: 1px solid #333; padding-bottom: 5px; }
              a { color: #44ff44; text-decoration: none; word-break: break-word; }
              a:hover { text-decoration: underline; }
              .dork { background: #2a2a2a; padding: 8px 12px; margin: 4px 0; border-radius: 4px; border-left: 3px solid #44aaff; }
              .count { color: #888; font-size: 12px; }
            </style>
            </head>
            <body>
            <h1>🔍 Dork Arsenal Report</h1>
            <p>Target: <strong>${esc(target)}</strong> | Generated: $timestamp</p>
            <p class="count">Total dorks: ${results.size}</p>
            """.trimIndent()
        )
        var currentCat = ""
        for (item in results) {
            if (item.category != currentCat) {
                currentCat = item.category
                sb.append("<h2>📁 ${esc(currentCat.uppercase())}</h2>\n")
            }
            sb.append("<div class=\"dork\"><a href=\"${esc(item.url)}\" target=\"_blank\">🔗 ${esc(item.dork)}</a></div>\n")
        }
        sb.append("</body></html>")
        return sb.toString()
    }

    /** Plain-text export, same format as the Python -o output. */
    fun textReport(results: List<Dork>): String {
        val sb = StringBuilder()
        for (item in results) {
            sb.append("# [${item.category}]\n")
            sb.append("DORK: ${item.dork}\n")
            sb.append("URL:  ${item.url}\n\n")
        }
        return sb.toString()
    }
}
