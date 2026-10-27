package com.shuvonsec.dorkrunner

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.shuvonsec.dorkrunner.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var db: DorkDb
    private lateinit var generator: DorkGenerator
    private val io = Executors.newSingleThreadExecutor()

    private var target: String = ""
    private val dorks = ArrayList<Dorks.Dork>()
    private val results = HashMap<Int, SearchClient.Result>()

    private val prefs by lazy { getSharedPreferences("settings", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = DorkDb(this)
        generator = DorkGenerator(db)

        binding.categorySpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, Dorks.CATEGORY_KEYS
        )
        binding.categorySpinner.setSelection(Dorks.CATEGORY_KEYS.indexOf("all"))

        binding.reportView.settings.javaScriptEnabled = false
        binding.reportView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false
                if (url.startsWith("dork://")) {
                    handleAction(url)
                    return true
                }
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    return true
                }
                return false
            }
        }

        binding.generateButton.setOnClickListener { generate() }
        binding.autoCheckButton.setOnClickListener { autoCheckAll() }
        binding.generateNewButton.setOnClickListener { generateNewDorks() }
        binding.settingsButton.setOnClickListener { showSettings() }
        binding.shareButton.setOnClickListener { shareText() }

        val (total, gen, _) = db.stats()
        binding.statusText.text = getString(R.string.status_db, total, gen)
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    // ── Generate from DB (seed + learned/generated) ───────────────────────────
    private fun generate() {
        val t = binding.domainInput.text.toString().trim()
        if (t.isEmpty()) {
            toast("Enter a target domain"); return
        }
        target = t
        val category = Dorks.CATEGORY_KEYS[binding.categorySpinner.selectedItemPosition]
        val rows = db.allTemplates(category)
        dorks.clear(); results.clear()
        for (row in rows) {
            val d = row.template.replace("{target}", t)
            dorks.add(Dorks.Dork(row.category, d, Dorks.googleUrl(d)))
        }
        render()
        binding.shareButton.isEnabled = true
        binding.statusText.text = getString(R.string.status_generated, dorks.size, t)
    }

    // ── Auto-check: which dorks actually return results (official Search API) ──
    private fun autoCheckAll() {
        if (dorks.isEmpty()) { toast("Generate dorks first"); return }
        val client = searchClient()
        if (client == null || !client.isConfigured()) {
            promptConfigureKey(); return
        }
        val cap = minOf(dorks.size, MAX_AUTOCHECK)
        binding.statusText.text = getString(R.string.status_checking, cap)
        setBusy(true)
        io.execute {
            var hits = 0
            for (i in 0 until cap) {
                val d = dorks[i]
                val res = client.check(d.dork)
                results[i] = res
                db.recordRun(templateOf(d), res.count)
                if (res.count > 0 && res.topLink.isNotEmpty()) {
                    db.addFinding(target, d.dork, res.count, res.topLink)
                    hits++
                }
                try { Thread.sleep(REQUEST_SPACING_MS) } catch (_: InterruptedException) { return@execute }
            }
            val finalHits = hits
            runOnUiThread {
                setBusy(false)
                render()
                binding.statusText.text = getString(R.string.status_checked, finalHits, cap)
            }
        }
    }

    // ── Generate brand-new dorks via the feedback-weighted engine ─────────────
    private fun generateNewDorks() {
        setBusy(true)
        binding.statusText.text = getString(R.string.status_generating)
        io.execute {
            val created = generator.generate(GENERATE_BATCH)
            val t = target.ifEmpty { "example.com" }
            runOnUiThread {
                setBusy(false)
                if (created.isEmpty()) {
                    toast("No new unique dorks this round — try again")
                    return@runOnUiThread
                }
                dorks.clear(); results.clear()
                target = t
                for ((cat, tpl) in created) {
                    val d = tpl.replace("{target}", t)
                    dorks.add(Dorks.Dork(cat, d, Dorks.googleUrl(d)))
                }
                render()
                binding.shareButton.isEnabled = true
                val (total, gen, _) = db.stats()
                binding.statusText.text =
                    getString(R.string.status_newdorks, created.size, total, gen)
            }
        }
    }

    // ── Chaining: pivot from a hit into follow-up dorks ───────────────────────
    private fun handleAction(url: String) {
        val path = url.removePrefix("dork://")
        val parts = path.split("/")
        when (parts.getOrNull(0)) {
            "checkall" -> autoCheckAll()
            "check" -> {
                val i = parts.getOrNull(1)?.toIntOrNull() ?: return
                checkOne(i)
            }
            "chain" -> {
                val i = parts.getOrNull(1)?.toIntOrNull() ?: return
                chainFrom(i)
            }
        }
    }

    private fun checkOne(i: Int) {
        val client = searchClient()
        if (client == null || !client.isConfigured()) { promptConfigureKey(); return }
        if (i !in dorks.indices) return
        val d = dorks[i]
        setBusy(true)
        io.execute {
            val res = client.check(d.dork)
            db.recordRun(templateOf(d), res.count)
            if (res.count > 0 && res.topLink.isNotEmpty()) db.addFinding(target, d.dork, res.count, res.topLink)
            runOnUiThread {
                results[i] = res
                setBusy(false)
                render()
            }
        }
    }

    private fun chainFrom(i: Int) {
        val res = results[i] ?: run { toast("Check this dork first to chain from its result"); return }
        if (res.topLink.isEmpty()) { toast("No result link to chain from"); return }
        val links = Chainer.chainFrom(target, res.topLink)
        if (links.isEmpty()) { toast("No follow-up leads"); return }
        // Insert follow-ups right after the source dork.
        val insertAt = i + 1
        val newDorks = links.map { Dorks.Dork("chain", it.dork, it.url) }
        dorks.addAll(insertAt, newDorks)
        // Re-key results map for shifted indices.
        val shifted = HashMap<Int, SearchClient.Result>()
        for ((k, v) in results) shifted[if (k >= insertAt) k + newDorks.size else k] = v
        results.clear(); results.putAll(shifted)
        render()
        toast("Added ${links.size} chained dork(s)")
    }

    // ── Report rendering ──────────────────────────────────────────────────────
    @SuppressLint("SetJavaScriptEnabled")
    private fun render() {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val configured = searchClient()?.isConfigured() == true
        val html = buildReportHtml(ts, configured)
        binding.reportView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private fun buildReportHtml(ts: String, configured: Boolean): String {
        val sb = StringBuilder()
        sb.append(
            """
            <!DOCTYPE html><html lang="en"><head><meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
              body { font-family: monospace; background:#1a1a1a; color:#e0e0e0; padding:16px; }
              h1 { color:#ff4444; font-size:20px; }
              h2 { color:#44aaff; border-bottom:1px solid #333; padding-bottom:4px; margin-top:18px; }
              a { color:#44ff44; text-decoration:none; word-break:break-word; }
              a:hover { text-decoration:underline; }
              .dork { background:#2a2a2a; padding:8px 12px; margin:6px 0; border-radius:4px; border-left:3px solid #44aaff; }
              .dork.hit { border-left-color:#44ff44; }
              .dork.miss { border-left-color:#555; opacity:.7; }
              .actions { margin-top:6px; font-size:12px; }
              .actions a { color:#ffcc44; margin-right:14px; }
              .badge { font-size:11px; padding:1px 6px; border-radius:8px; margin-left:6px; }
              .badge.hit { background:#154a15; color:#8f8; }
              .badge.miss { background:#3a3a3a; color:#aaa; }
              .top { color:#9cf; font-size:11px; display:block; margin-top:3px; }
              .bar { background:#222; padding:10px 12px; border-radius:6px; margin-bottom:10px; }
              .count { color:#888; font-size:12px; }
            </style></head><body>
            <h1>🔍 Dork Arsenal Report</h1>
            <p>Target: <strong>${esc(target)}</strong> | ${esc(ts)}</p>
            <p class="count">Total dorks: ${dorks.size}</p>
            """.trimIndent()
        )
        if (configured && dorks.isNotEmpty()) {
            sb.append("<div class=\"bar\"><a href=\"dork://checkall\">⚡ Auto-check all (official Search API)</a></div>")
        } else if (dorks.isNotEmpty()) {
            sb.append("<div class=\"bar count\">Tip: set a Google Custom Search API key in Settings to auto-check which dorks return results.</div>")
        }

        var currentCat = ""
        dorks.forEachIndexed { i, item ->
            if (item.category != currentCat) {
                currentCat = item.category
                sb.append("<h2>📁 ${esc(currentCat.uppercase())}</h2>\n")
            }
            val res = results[i]
            val cls = when {
                res == null -> "dork"
                res.count > 0 -> "dork hit"
                else -> "dork miss"
            }
            sb.append("<div class=\"$cls\">")
            sb.append("<a href=\"${esc(item.url)}\" target=\"_blank\">🔗 ${esc(item.dork)}</a>")
            if (res != null) {
                if (res.error != null) {
                    sb.append("<span class=\"badge miss\">${esc(res.error)}</span>")
                } else if (res.count > 0) {
                    sb.append("<span class=\"badge hit\">${res.count} hit(s)</span>")
                    if (res.topLink.isNotEmpty()) {
                        sb.append("<a class=\"top\" href=\"${esc(res.topLink)}\">${esc(res.topLink)}</a>")
                    }
                } else {
                    sb.append("<span class=\"badge miss\">0</span>")
                }
            }
            sb.append("<div class=\"actions\">")
            sb.append("<a href=\"dork://check/$i\">✓ check</a>")
            if (res != null && res.count > 0 && res.topLink.isNotEmpty()) {
                sb.append("<a href=\"dork://chain/$i\">⛓ chain</a>")
            }
            sb.append("</div></div>\n")
        }
        sb.append("</body></html>")
        return sb.toString()
    }

    // ── Settings (API key + CSE id) ───────────────────────────────────────────
    private fun showSettings() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val keyInput = EditText(this).apply {
            hint = "Google API key"
            inputType = InputType.TYPE_CLASS_TEXT
            setText(prefs.getString("api_key", ""))
        }
        val cxInput = EditText(this).apply {
            hint = "Search engine id (cx)"
            inputType = InputType.TYPE_CLASS_TEXT
            setText(prefs.getString("cx", ""))
        }
        layout.addView(keyInput)
        layout.addView(cxInput)

        AlertDialog.Builder(this)
            .setTitle("Search API credentials")
            .setMessage(
                "Auto-check uses Google's Programmable Search (Custom Search JSON API) with " +
                    "your own key + engine id. Free tier: 100 queries/day. Use only against " +
                    "in-scope targets you are authorized to test."
            )
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit()
                    .putString("api_key", keyInput.text.toString().trim())
                    .putString("cx", cxInput.text.toString().trim())
                    .apply()
                toast("Saved")
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptConfigureKey() {
        AlertDialog.Builder(this)
            .setTitle("API key required")
            .setMessage("Auto-checking which dorks return results needs your own Google Custom Search API key and engine id. Open Settings to add them?")
            .setPositiveButton("Settings") { _, _ -> showSettings() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Share ─────────────────────────────────────────────────────────────────
    private fun shareText() {
        if (dorks.isEmpty()) return
        val text = Dorks.textReport(dorks)
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Dork report — $target")
                    putExtra(Intent.EXTRA_TEXT, text)
                }, "Share dorks"
            )
        )
    }

    // ── helpers ───────────────────────────────────────────────────────────────
    private fun searchClient(): SearchClient? {
        val k = prefs.getString("api_key", "") ?: ""
        val cx = prefs.getString("cx", "") ?: ""
        return SearchClient(k, cx)
    }

    /** Turn a concrete dork back into its {target} template for DB stat updates. */
    private fun templateOf(d: Dorks.Dork): String =
        if (target.isNotEmpty()) d.dork.replace(target, "{target}") else d.dork

    private fun setBusy(busy: Boolean) {
        binding.generateButton.isEnabled = !busy
        binding.autoCheckButton.isEnabled = !busy
        binding.generateNewButton.isEnabled = !busy
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    companion object {
        private const val MAX_AUTOCHECK = 50        // respect the free-tier daily quota
        private const val REQUEST_SPACING_MS = 300L // be gentle on the API
        private const val GENERATE_BATCH = 12
    }
}
