package com.webapk.builder

/**
 * Подмена строк-плейсхолдеров прямо в бинарных файлах APK (AndroidManifest.xml, resources.arsc).
 * Длина строки не меняется, поэтому смещения внутри файла остаются корректными.
 */
object Patcher {
    private fun indexOf(h: ByteArray, n: ByteArray, from: Int): Int {
        if (n.isEmpty()) return -1
        var i = from
        val last = h.size - n.size
        outer@ while (i <= last) {
            for (j in n.indices) if (h[i + j] != n[j]) { i++; continue@outer }
            return i
        }
        return -1
    }

    private fun utf16le(s: String): ByteArray {
        val b = ByteArray(s.length * 2)
        for ((k, c) in s.withIndex()) { b[k * 2] = c.code.toByte(); b[k * 2 + 1] = (c.code shr 8).toByte() }
        return b
    }

    /** Замена ASCII-строки равной длины (в кодировках UTF-8 и UTF-16LE). Возвращает число замен. */
    fun replaceAscii(data: ByteArray, from: String, to: String): Int {
        require(from.length == to.length) { "Длины должны совпадать" }
        var count = 0
        for ((p, r) in listOf(from.toByteArray(Charsets.UTF_8) to to.toByteArray(Charsets.UTF_8), utf16le(from) to utf16le(to))) {
            var i = indexOf(data, p, 0)
            while (i >= 0) {
                System.arraycopy(r, 0, data, i, r.size)
                count++
                i = indexOf(data, p, i + r.size)
            }
        }
        return count
    }

    /** Подмена названия приложения (любые символы, до длины плейсхолдера), хвост добивается пробелами. */
    fun replaceLabel(data: ByteArray, placeholder: String, label: String): Int {
        val n = placeholder.length
        var count = 0

        // UTF-8 пул строк: [длина в символах][длина в байтах][байты]
        val p8 = placeholder.toByteArray(Charsets.UTF_8)
        var i = indexOf(data, p8, 0)
        while (i >= 0) {
            if (i >= 2 && (data[i - 1].toInt() and 0xFF) == n && (data[i - 2].toInt() and 0xFF) == n) {
                val fitted = fitUtf8(label, n)
                val bytes = fitted.toByteArray(Charsets.UTF_8)
                val pad = n - bytes.size
                for (k in 0 until n) data[i + k] = if (k < bytes.size) bytes[k] else 0x20.toByte()
                data[i - 2] = (fitted.length + pad).toByte()
                count++
            }
            i = indexOf(data, p8, i + n)
        }

        // UTF-16 пул строк: [длина u16][символы u16]
        val p16 = utf16le(placeholder)
        i = indexOf(data, p16, 0)
        while (i >= 0) {
            if (i >= 2 && (data[i - 2].toInt() and 0xFF) == n && data[i - 1].toInt() == 0) {
                var t = label.take(n)
                if (t.isNotEmpty() && Character.isHighSurrogate(t.last())) t = t.dropLast(1)
                val r = utf16le(t.padEnd(n, ' '))
                System.arraycopy(r, 0, data, i, r.size)
                count++
            }
            i = indexOf(data, p16, i + p16.size)
        }
        return count
    }

    private fun fitUtf8(s: String, maxBytes: Int): String {
        val sb = StringBuilder()
        var bytes = 0
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val b = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
            if (bytes + b > maxBytes) break
            sb.appendCodePoint(cp)
            bytes += b
            i += Character.charCount(cp)
        }
        return sb.toString()
    }
}
