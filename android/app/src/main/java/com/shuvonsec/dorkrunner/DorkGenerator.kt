package com.shuvonsec.dorkrunner

import kotlin.random.Random

/**
 * Generative dork engine with a feedback-weighted "learning" loop.
 *
 * It is NOT a neural network. It is a grammar/recombination generator over the
 * Google search-operator space, biased by the effectiveness stats the DB has
 * accumulated: categories whose dorks have historically returned results get a
 * higher share of newly generated dorks (Laplace-smoothed hit-rate from
 * DorkDb.categoryHitRates()). Over repeated auto-check cycles this converges
 * toward the operator/keyword combinations that actually surface findings for
 * the targets you run — a simple online bandit over dork templates.
 *
 * Generated templates keep the {target} placeholder so they slot into the same
 * pipeline as the seed dorks, and are de-duplicated against the DB before use.
 */
class DorkGenerator(private val db: DorkDb) {

    // Operator building blocks per category. {kw} is filled from the keyword pool.
    private val patternsByCategory: Map<String, List<String>> = mapOf(
        "credentials" to listOf(
            "site:{target} ext:{ext} \"{kw}\"",
            "site:{target} \"{kw}\" filetype:{ext}",
            "site:{target} intext:\"{kw}\"",
            "site:{target} inurl:{kw}",
        ),
        "pii" to listOf(
            "site:{target} ext:{ext} intext:\"{kw}\"",
            "site:{target} filetype:{ext} \"{kw}\"",
            "site:{target} intitle:\"index of\" \"{kw}\"",
        ),
        "admin" to listOf(
            "site:{target} inurl:{kw}",
            "site:{target} intitle:\"{kw}\"",
            "site:{target} inurl:/{kw}/login",
        ),
        "errors" to listOf(
            "site:{target} \"{kw}\" filetype:{ext}",
            "site:{target} intext:\"{kw}\"",
        ),
        "cloud" to listOf(
            "\"{target}\" site:{host}",
            "site:{target} inurl:{kw}",
        ),
        "subdomains" to listOf(
            "site:*.{target} inurl:{kw}",
            "site:*.{target} intitle:\"{kw}\"",
        ),
        "params" to listOf(
            "site:{target} inurl:{kw}=",
            "site:{target} inurl:\"{kw}=\"",
        ),
        "leaks" to listOf(
            "site:{host} \"{target}\" \"{kw}\"",
            "site:{host} \"{target}\"",
        ),
        "github" to listOf(
            "site:github.com \"{target}\" \"{kw}\"",
            "site:github.com \"{target}\" filename:{kw}",
        ),
        "juicy" to listOf(
            "site:{target} intitle:\"index of\" \"{kw}\"",
            "site:{target} ext:{ext}",
            "site:{target} inurl:{kw}",
        ),
    )

    private val keywords: Map<String, List<String>> = mapOf(
        "credentials" to listOf(
            "client_secret", "access_token", "bearer", "jdbc:mysql", "DB_USER",
            "SMTP_PASSWORD", "REDIS_URL", "connectionstring", "private_token",
            "id_rsa", "htpasswd", "credentials", "vault_token", "GITHUB_TOKEN",
        ),
        "pii" to listOf(
            "passport", "national id", "dob", "home address", "credit card",
            "iban", "medical record", "patient", "tax id",
        ),
        "admin" to listOf(
            "dashboard", "console", "manager", "cpanel", "webmin", "portainer",
            "rundeck", "airflow", "superset", "adminer",
        ),
        "errors" to listOf(
            "undefined index", "ORA-00933", "psql: error", "unhandled exception",
            "whoops", "symfony profiler", "django.core.exceptions",
        ),
        "cloud" to listOf("backup", "dump", "export", "assets", "uploads"),
        "subdomains" to listOf("vpn", "jira", "gitlab", "mail", "internal", "uat", "qa"),
        "params" to listOf("return", "callback", "dest", "continue", "data", "doc", "template"),
        "leaks" to listOf("password", "api_key", "token", "secret", "dump", "leak"),
        "github" to listOf("config.json", "credentials", ".npmrc", "docker-compose.yml", "secrets"),
        "juicy" to listOf("db", "archive", "staging", "private", "secret"),
    )

    private val exts = listOf("env", "yml", "yaml", "ini", "conf", "config", "json", "txt", "log", "bak", "sql")
    private val hosts = listOf(
        "s3.amazonaws.com", "blob.core.windows.net", "storage.googleapis.com",
        "firebaseio.com", "digitaloceanspaces.com", "pastebin.com", "gitlab.com",
        "bitbucket.org", "gist.github.com",
    )

    /**
     * Generate up to [count] brand-new, unique dork templates, allocating more
     * to higher-performing categories. Returns (category, template) pairs that
     * were newly inserted into the DB.
     */
    fun generate(count: Int): List<Pair<String, String>> {
        val weights = db.categoryHitRates()
        val cats = patternsByCategory.keys.toList()
        val out = ArrayList<Pair<String, String>>()
        var attempts = 0
        val maxAttempts = count * 40
        while (out.size < count && attempts < maxAttempts) {
            attempts++
            val cat = weightedPick(cats, weights)
            val tpl = buildOne(cat) ?: continue
            if (db.templateExists(tpl)) continue
            if (db.insertGenerated(tpl, cat)) out.add(cat to tpl)
        }
        return out
    }

    private fun buildOne(cat: String): String? {
        val pattern = patternsByCategory[cat]?.randomOrNull() ?: return null
        val kw = keywords[cat]?.randomOrNull() ?: "config"
        return pattern
            .replace("{kw}", kw)
            .replace("{ext}", exts.random())
            .replace("{host}", hosts.random())
    }

    /**
     * Pick a category with probability proportional to its smoothed hit-rate so
     * that, as the DB learns which categories pay off, generation shifts toward
     * them. Categories with no history share the default smoothed weight.
     */
    private fun weightedPick(cats: List<String>, weights: Map<String, Double>): String {
        val scored = cats.map { it to (weights[it] ?: 0.5) }
        val total = scored.sumOf { it.second }
        var r = Random.nextDouble() * total
        for ((cat, w) in scored) {
            r -= w
            if (r <= 0) return cat
        }
        return scored.last().first
    }
}
