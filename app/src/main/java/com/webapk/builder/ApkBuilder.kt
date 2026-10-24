package com.webapk.builder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import com.android.apksig.ApkSigner
import java.io.BufferedInputStream
import java.math.BigInteger
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.min

/**
 * Собирает APK прямо на телефоне:
 * берёт шаблонный APK (WebView-оболочка) из assets, подменяет сайт, иконку, название и пакет,
 * затем подписывает (v1+v2) ключом из assets/signing.
 */
class ApkBuilder(private val ctx: Context) {

    companion object {
        const val PKG_PLACEHOLDER = "com.webapk.pkgplaceholder00"
        val NAME_PLACEHOLDER = "APPNAME_PLACEHOLDER_" + "X".repeat(44)
        const val MAX_TOTAL = 300L * 1024 * 1024
    }

    fun build(appName: String, source: Uri, logo: Uri?, progress: (Float, String) -> Unit): File {
        AppLog.i("Сборка «$appName» начата")
        val work = File(ctx.cacheDir, "work").also { it.deleteRecursively(); it.mkdirs() }
        val www = File(work, "www").also { it.mkdirs() }

        progress(0.05f, "Читаю файлы сайта")
        importSource(source, www)
        normalizeIndex(www)
        val files = www.walkTopDown().filter { it.isFile }.toList()
        AppLog.i("Файлов сайта: ${files.size}, ${files.sumOf { it.length() } / 1024} КБ")

        progress(0.30f, "Готовлю иконку")
        val icon = makeIcon(appName, logo)

        progress(0.45f, "Собираю пакет")
        val unsigned = File(work, "unsigned.apk")
        assemble(appName, www, icon, unsigned)

        progress(0.75f, "Подписываю APK")
        val outDir = File(ctx.filesDir, "apk").also { it.mkdirs() }
        val out = File(outDir, safeFileName(appName) + ".apk")
        sign(unsigned, out)

        work.deleteRecursively()
        progress(1f, "Готово")
        AppLog.i("APK готов: ${out.name}, ${out.length() / 1024} КБ")
        return out
    }

    // ---------- 1. Импорт HTML / ZIP ----------

    private fun importSource(uri: Uri, www: File) {
        val input = ctx.contentResolver.openInputStream(uri) ?: error("Не удалось открыть файл")
        BufferedInputStream(input).use { bin ->
            bin.mark(8)
            val h = ByteArray(4)
            val n = bin.read(h)
            bin.reset()
            val isZip = n == 4 && h[0] == 'P'.code.toByte() && h[1] == 'K'.code.toByte() && h[2].toInt() == 3 && h[3].toInt() == 4
            if (isZip) {
                AppLog.i("Источник: ZIP-архив")
                extractZip(bin, www)
            } else {
                AppLog.i("Источник: одиночный HTML")
                AppLog.w("Одиночный HTML: внешние css/js/картинки не будут включены. Для сайта из нескольких файлов используйте ZIP.")
                File(www, "index.html").outputStream().use { bin.copyTo(it) }
            }
        }
    }

    private fun extractZip(input: InputStream, www: File) {
        var total = 0L
        var count = 0
        ZipInputStream(input).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                val raw = e.name.replace('\\', '/')
                if (!e.isDirectory && !raw.startsWith("__MACOSX/") && !raw.endsWith(".DS_Store")) {
                    val parts = raw.split('/').filter { it.isNotEmpty() && it != "." }
                    if (parts.isEmpty() || parts.any { it == ".." }) {
                        AppLog.w("Пропущен небезопасный путь: $raw")
                    } else {
                        val f = File(www, parts.joinToString("/"))
                        f.parentFile?.mkdirs()
                        FileOutputStream(f).use { o ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val r = zin.read(buf)
                                if (r < 0) break
                                total += r
                                if (total > MAX_TOTAL) error("Слишком большой архив (больше 300 МБ)")
                                o.write(buf, 0, r)
                            }
                        }
                        count++
                    }
                }
                zin.closeEntry()
                e = zin.nextEntry
            }
        }
        if (count == 0) error("Архив пуст")
    }

    /** Гарантирует наличие www/index.html: ищет в подпапке или берёт единственный html. */
    private fun normalizeIndex(www: File) {
        if (File(www, "index.html").isFile) return
        val files = www.walkTopDown().filter { it.isFile }.toList()
        val idx = files.filter { it.name.equals("index.html", true) }
            .minByOrNull { it.relativeTo(www).invariantSeparatorsPath.count { c -> c == '/' } }
        if (idx != null) {
            val root = idx.parentFile!!
            if (root != www) {
                val rel = root.relativeTo(www).invariantSeparatorsPath
                val tmp = File(www.parentFile, "www_tmp").also { it.deleteRecursively() }
                root.copyRecursively(tmp, true)
                www.deleteRecursively()
                tmp.renameTo(www)
                AppLog.i("Корень сайта в подпапке «$rel» — поднят на верхний уровень")
            }
            val f = File(www, idx.name)
            if (f.name != "index.html") f.renameTo(File(www, "index.html"))
            return
        }
        val htmls = files.filter { it.extension.lowercase() in setOf("html", "htm") }
        if (htmls.size == 1) {
            val h = htmls[0]
            AppLog.w("index.html не найден, главным назначен ${h.name}")
            h.renameTo(File(h.parentFile, "index.html"))
            normalizeIndex(www)
            return
        }
        // ZIP без HTML: создаём стартовую страницу со ссылками на файлы архива.
        val visibleFiles = files.filter { it.isFile }
            .sortedBy { it.relativeTo(www).invariantSeparatorsPath.lowercase() }
        if (visibleFiles.isEmpty()) error("В архиве нет файлов")
        val links = visibleFiles.joinToString("\n") { file ->
            val rel = file.relativeTo(www).invariantSeparatorsPath
            val href = rel.split('/').joinToString("/") { part ->
                java.net.URLEncoder.encode(part, "UTF-8").replace("+", "%20")
            }
            val label = rel.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            "<li><a href=\"$href\">$label</a></li>"
        }
        File(www, "index.html").writeText(
            """<!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Файлы приложения</title><style>body{font:16px system-ui;margin:0;padding:24px;background:#10131c;color:#f5f6fa}h1{font-size:24px}li{margin:12px 0}a{color:#8ba7ff;overflow-wrap:anywhere}</style></head><body><h1>Файлы приложения</h1><p>В архиве не найден HTML-сайт. Выберите файл:</p><ul>$links</ul></body></html>""",
            Charsets.UTF_8
        )
        AppLog.w("HTML не найден: создана стартовая страница со ссылками на ${visibleFiles.size} файлов")
    }

    // ---------- 2. Иконка ----------

    private fun makeIcon(name: String, logo: Uri?): ByteArray {
        val size = 512
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 112f, 112f, Path.Direction.CW) })
        var drawn = false
        if (logo != null) {
            try {
                val src = decodeBitmap(ctx, logo, size)
                val s = min(src.width, src.height)
                val r = Rect((src.width - s) / 2, (src.height - s) / 2, (src.width + s) / 2, (src.height + s) / 2)
                c.drawBitmap(src, r, Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                drawn = true
                AppLog.i("Логотип применён")
            } catch (t: Throwable) {
                AppLog.w("Не удалось прочитать логотип, использую стандартный", t)
            }
        }
        if (!drawn) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), 0xFF7C5CFF.toInt(), 0xFF22D3EE.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
            val letter = name.trim().firstOrNull { !it.isWhitespace() }?.uppercaseChar()?.toString() ?: "A"
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt(); textSize = 300f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
            }
            c.drawText(letter, size / 2f, size / 2f - (tp.ascent() + tp.descent()) / 2f, tp)
        }
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    // ---------- 3. Сборка пакета ----------

    private class CountingOut(out: OutputStream) : FilterOutputStream(out) {
        var count = 0L
        override fun write(b: Int) { out.write(b); count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); count += len }
    }

    private fun assemble(appName: String, www: File, icon: ByteArray, out: File) {
        val pkg = "com.webapk.a" + sha1Hex(appName.lowercase()).take(15)
        AppLog.i("Пакет: $pkg")
        val tpl = File(ctx.cacheDir, "template.apk")
        ctx.assets.open("template.apk").use { i -> tpl.outputStream().use { o -> i.copyTo(o) } }

        ZipFile(tpl).use { zf ->
            val cout = CountingOut(BufferedOutputStream(FileOutputStream(out), 64 * 1024))
            ZipOutputStream(cout).use { zos ->
                var iconReplaced = false
                for (e in zf.entries().asSequence()) {
                    val n = e.name
                    if (e.isDirectory || n.startsWith("META-INF/") || n.startsWith("assets/www/")) continue
                    var data = zf.getInputStream(e).use { it.readBytes() }
                    when {
                        n == "AndroidManifest.xml" ->
                            check(Patcher.replaceAscii(data, PKG_PLACEHOLDER, pkg) > 0) { "Шаблон повреждён: нет плейсхолдера пакета" }
                        n == "resources.arsc" -> {
                            Patcher.replaceAscii(data, PKG_PLACEHOLDER, pkg)
                            check(Patcher.replaceLabel(data, NAME_PLACEHOLDER, appName) > 0) { "Шаблон повреждён: нет плейсхолдера названия" }
                        }
                        n.startsWith("res/") && n.endsWith(".png") -> { data = icon; iconReplaced = true }
                    }
                    writeBytes(zos, cout, n, data, stored = n == "resources.arsc" || e.method == ZipEntry.STORED)
                }
                if (!iconReplaced) AppLog.w("В шаблоне не найден PNG иконки — иконка не заменена")

                for (f in www.walkTopDown().filter { it.isFile }.sortedBy { it.path }) {
                    val rel = f.relativeTo(www).invariantSeparatorsPath
                    zos.putNextEntry(ZipEntry("assets/www/$rel"))
                    f.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
        tpl.delete()
    }

    private fun writeBytes(zos: ZipOutputStream, cout: CountingOut, name: String, data: ByteArray, stored: Boolean) {
        val ze = ZipEntry(name)
        if (stored) {
            ze.method = ZipEntry.STORED
            ze.size = data.size.toLong()
            ze.compressedSize = data.size.toLong()
            ze.crc = CRC32().apply { update(data) }.value
            // Выравнивание данных по 4 байта (требование Android 11+ для resources.arsc)
            val off = cout.count + 30 + name.toByteArray(Charsets.UTF_8).size
            val rem = (off % 4).toInt()
            if (rem != 0) {
                val k = 4 - rem
                ze.extra = ByteArray(4 + k).also { it[0] = 0x35; it[1] = 0xD9.toByte(); it[2] = k.toByte() }
            }
        }
        zos.putNextEntry(ze)
        zos.write(data)
        zos.closeEntry()
    }

    // ---------- 4. Подпись ----------

    private fun sign(input: File, output: File) {
        val (key, cert) = loadOrCreateSigningMaterial()
        val cfg = ApkSigner.SignerConfig.Builder("WebApk", key, listOf(cert)).build()
        output.delete()
        ApkSigner.Builder(listOf(cfg))
            .setInputApk(input)
            .setOutputApk(output)
            .setMinSdkVersion(26)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(false)
            .build()
            .sign()
    }

    /**
     * Prefer an explicitly provisioned signing key, but never make APK creation depend
     * on private signing files being committed into the public repository. If they are
     * absent, create a self-signed key once and keep it in this app's private storage.
     */
    private fun loadOrCreateSigningMaterial(): Pair<java.security.PrivateKey, X509Certificate> {
        val assetKey = runCatching { ctx.assets.open("signing/key.pk8").use { it.readBytes() } }.getOrNull()
        val assetCert = runCatching { ctx.assets.open("signing/cert.der").use { it.readBytes() } }.getOrNull()
        if (assetKey != null && assetCert != null) {
            val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(assetKey))
            val cert = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(assetCert)) as X509Certificate
            return key to cert
        }

        val dir = File(ctx.filesDir, "signing").also { it.mkdirs() }
        val keyFile = File(dir, "key.pk8")
        val certFile = File(dir, "cert.der")
        if (keyFile.isFile && certFile.isFile) {
            val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
            val cert = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(certFile.readBytes())) as X509Certificate
            return key to cert
        }

        AppLog.w("Ключ подписи не найден в assets: создаю локальный ключ приложения")
        val generator = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val pair = generator.generateKeyPair()
        val now = System.currentTimeMillis()
        val name = X500Name("CN=WebApk Builder")
        val builder = JcaX509v3CertificateBuilder(
            name,
            BigInteger(160, SecureRandom()).abs().let { if (it == BigInteger.ZERO) BigInteger.ONE else it },
            Date(now - 24L * 60 * 60 * 1000),
            Date(now + 10L * 365 * 24 * 60 * 60 * 1000),
            name,
            pair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(pair.private)
        val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        keyFile.writeBytes(pair.private.encoded)
        certFile.writeBytes(cert.encoded)
        return pair.private to cert
    }

    private fun sha1Hex(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun safeFileName(s: String): String =
        s.trim().replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").trim('_').take(40).ifEmpty { "app" }
}
