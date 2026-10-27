package com.shuvonsec.dorkrunner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.shuvonsec.dorkrunner.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var lastResults: List<Dorks.Dork> = emptyList()
    private var lastTarget: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.categorySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            Dorks.CATEGORY_KEYS
        )
        // Default to "all", like the Python script.
        binding.categorySpinner.setSelection(Dorks.CATEGORY_KEYS.indexOf("all"))

        // Clicking a generated dork opens it in the device browser.
        binding.reportView.settings.javaScriptEnabled = false
        binding.reportView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    return true
                }
                return false
            }
        }

        binding.generateButton.setOnClickListener { generate() }
        binding.shareButton.setOnClickListener { shareText() }
    }

    private fun generate() {
        val target = binding.domainInput.text.toString().trim()
        if (target.isEmpty()) {
            Toast.makeText(this, "Enter a target domain", Toast.LENGTH_SHORT).show()
            return
        }
        val category = Dorks.CATEGORY_KEYS[binding.categorySpinner.selectedItemPosition]
        val results = Dorks.generate(target, category)
        lastResults = results
        lastTarget = target

        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val html = Dorks.htmlReport(target, results, ts)
        binding.reportView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        binding.statusText.text = getString(R.string.status_generated, results.size, target)
        binding.shareButton.isEnabled = true
    }

    private fun shareText() {
        if (lastResults.isEmpty()) return
        val text = Dorks.textReport(lastResults)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Dork report — $lastTarget")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Share dorks"))
    }
}
