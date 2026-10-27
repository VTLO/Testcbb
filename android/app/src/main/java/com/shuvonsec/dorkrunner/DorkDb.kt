package com.shuvonsec.dorkrunner

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local persistence for dork templates, their effectiveness stats, and findings.
 *
 * Plain SQLiteOpenHelper (no Room / annotation processor) to keep the build
 * self-contained. Three tables:
 *   dorks    — every template, seed or generated, with run/hit counters that
 *              drive the effectiveness-weighted generator (the "learning" loop).
 *   findings — dorks that returned results during an auto-check, for chaining.
 */
class DorkDb(context: Context) :
    SQLiteOpenHelper(context, "dorkrunner.db", null, 1) {

    data class DorkRow(
        val id: Long,
        val template: String,
        val category: String,
        val source: String,   // "seed" | "generated"
        val runs: Int,
        val hits: Int,
        val lastResults: Long  // -1 = never run
    )

    data class Finding(
        val id: Long,
        val target: String,
        val dork: String,
        val resultCount: Long,
        val topLink: String,
        val ts: Long
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE dorks(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                template TEXT UNIQUE NOT NULL,
                category TEXT NOT NULL,
                source TEXT NOT NULL,
                runs INTEGER NOT NULL DEFAULT 0,
                hits INTEGER NOT NULL DEFAULT 0,
                last_results INTEGER NOT NULL DEFAULT -1,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE findings(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                target TEXT NOT NULL,
                dork TEXT NOT NULL,
                result_count INTEGER NOT NULL,
                top_link TEXT NOT NULL,
                ts INTEGER NOT NULL
            )
            """.trimIndent()
        )
        seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 only so far.
    }

    /** Seed the DB with the 98 built-in templates (ported from dork_runner.py). */
    private fun seed(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            for ((cat, templates) in Dorks.CATEGORIES) {
                for (t in templates) {
                    val cv = ContentValues().apply {
                        put("template", t)
                        put("category", cat)
                        put("source", "seed")
                        put("created_at", now)
                    }
                    db.insertWithOnConflict("dorks", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun allTemplates(category: String): List<DorkRow> {
        val sql: String
        val args: Array<String>
        if (category == "all") {
            sql = "SELECT * FROM dorks ORDER BY category, id"
            args = emptyArray()
        } else {
            sql = "SELECT * FROM dorks WHERE category = ? ORDER BY id"
            args = arrayOf(category)
        }
        return readRows(sql, args)
    }

    fun generatedTemplates(limit: Int): List<DorkRow> =
        readRows(
            "SELECT * FROM dorks WHERE source = 'generated' ORDER BY id DESC LIMIT ?",
            arrayOf(limit.toString())
        )

    fun templateExists(template: String): Boolean {
        readableDatabase.rawQuery(
            "SELECT 1 FROM dorks WHERE template = ? LIMIT 1", arrayOf(template)
        ).use { return it.moveToFirst() }
    }

    fun insertGenerated(template: String, category: String): Boolean {
        val cv = ContentValues().apply {
            put("template", template)
            put("category", category)
            put("source", "generated")
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insertWithOnConflict(
            "dorks", null, cv, SQLiteDatabase.CONFLICT_IGNORE
        ) >= 0
    }

    /** Record the outcome of running a dork: increments runs, and hits if it returned results. */
    fun recordRun(template: String, resultCount: Long) {
        val hit = if (resultCount > 0) 1 else 0
        writableDatabase.execSQL(
            "UPDATE dorks SET runs = runs + 1, hits = hits + ?, last_results = ? WHERE template = ?",
            arrayOf(hit, resultCount, template)
        )
    }

    fun addFinding(target: String, dork: String, resultCount: Long, topLink: String) {
        val cv = ContentValues().apply {
            put("target", target)
            put("dork", dork)
            put("result_count", resultCount)
            put("top_link", topLink)
            put("ts", System.currentTimeMillis())
        }
        writableDatabase.insert("findings", null, cv)
    }

    /**
     * Effectiveness stats per category: Laplace-smoothed hit-rate used to weight
     * the generator toward operator/keyword patterns that actually return results.
     */
    fun categoryHitRates(): Map<String, Double> {
        val out = HashMap<String, Double>()
        readableDatabase.rawQuery(
            "SELECT category, SUM(runs) r, SUM(hits) h FROM dorks GROUP BY category", null
        ).use { c ->
            while (c.moveToNext()) {
                val cat = c.getString(0)
                val r = c.getLong(1)
                val h = c.getLong(2)
                out[cat] = (h + 1.0) / (r + 2.0) // Laplace smoothing
            }
        }
        return out
    }

    fun stats(): Triple<Int, Int, Int> {
        var total = 0; var generated = 0; var run = 0
        readableDatabase.rawQuery(
            "SELECT COUNT(*), SUM(source='generated'), SUM(runs>0) FROM dorks", null
        ).use { c ->
            if (c.moveToFirst()) {
                total = c.getInt(0); generated = c.getInt(1); run = c.getInt(2)
            }
        }
        return Triple(total, generated, run)
    }

    private fun readRows(sql: String, args: Array<String>): List<DorkRow> {
        val list = ArrayList<DorkRow>()
        readableDatabase.rawQuery(sql, args).use { c ->
            val iId = c.getColumnIndexOrThrow("id")
            val iTpl = c.getColumnIndexOrThrow("template")
            val iCat = c.getColumnIndexOrThrow("category")
            val iSrc = c.getColumnIndexOrThrow("source")
            val iRuns = c.getColumnIndexOrThrow("runs")
            val iHits = c.getColumnIndexOrThrow("hits")
            val iLast = c.getColumnIndexOrThrow("last_results")
            while (c.moveToNext()) {
                list.add(
                    DorkRow(
                        c.getLong(iId), c.getString(iTpl), c.getString(iCat),
                        c.getString(iSrc), c.getInt(iRuns), c.getInt(iHits), c.getLong(iLast)
                    )
                )
            }
        }
        return list
    }
}
