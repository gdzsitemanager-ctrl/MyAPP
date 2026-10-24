package com.webapk.builder

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Журнал: хранится в файле, переживает перезапуск и аварийные закрытия. */
object AppLog {
    data class Entry(val time: Long, val level: Char, val msg: String)

    private const val MAX = 500
    private const val NL = '\u2028'
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()
    private var file: File? = null

    fun init(ctx: Context) {
        val f = File(ctx.filesDir, "journal.log")
        file = f
        _entries.value = runCatching { f.readLines().mapNotNull(::parse) }.getOrDefault(emptyList()).takeLast(MAX)
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, ex ->
            runCatching { e("Аварийное завершение (поток ${t.name})", ex) }
            prev?.uncaughtException(t, ex)
        }
    }

    fun i(msg: String) = add('I', msg)
    fun w(msg: String, t: Throwable? = null) = add('W', join(msg, t))
    fun e(msg: String, t: Throwable? = null) = add('E', join(msg, t))

    fun clear() {
        _entries.value = emptyList()
        runCatching { file?.writeText("") }
    }

    fun asText(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val head = "WebApk Builder · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n\n"
        return head + _entries.value.joinToString("\n") { "${fmt.format(Date(it.time))} [${it.level}] ${it.msg}" }
    }

    private fun join(msg: String, t: Throwable?) =
        if (t == null) msg else msg + "\n" + t.stackTraceToString().take(3000)

    private fun parse(line: String): Entry? {
        val p = line.split('|', limit = 3)
        if (p.size < 3) return null
        val time = p[0].toLongOrNull() ?: return null
        val level = p[1].firstOrNull() ?: return null
        return Entry(time, level, p[2].replace(NL, '\n'))
    }

    @Synchronized
    private fun add(level: Char, msg: String) {
        val en = Entry(System.currentTimeMillis(), level, msg)
        _entries.value = (_entries.value + en).takeLast(MAX)
        Log.println(when (level) { 'E' -> Log.ERROR; 'W' -> Log.WARN; else -> Log.INFO }, "WebApk", msg)
        runCatching {
            val f = file ?: return@runCatching
            f.appendText("${en.time}|${en.level}|${msg.replace('\n', NL)}\n")
            if (f.length() > 512 * 1024) {
                f.writeText(_entries.value.joinToString("") { "${it.time}|${it.level}|${it.msg.replace('\n', NL)}\n" })
            }
        }
    }
}
