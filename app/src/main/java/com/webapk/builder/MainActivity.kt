package com.webapk.builder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { BuilderTheme { AppRoot() } }
    }
}

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val logs by AppLog.entries.collectAsState()
    val errors = logs.count { it.level == 'E' }
    val navColors = NavigationBarItemDefaults.colors(
        selectedIconColor = Color.White, selectedTextColor = Color.White, indicatorColor = Accent,
        unselectedIconColor = TextLo, unselectedTextColor = TextLo,
    )
    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = Surface1) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, colors = navColors,
                    icon = { Icon(Icons.Filled.Build, null) }, label = { Text("Сборка") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, colors = navColors,
                    icon = { Icon(Icons.Filled.Info, null) },
                    label = { Text(if (errors > 0) "Журнал ($errors)" else "Журнал") })
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (tab == 0) BuildScreen(vm) { tab = 1 } else JournalScreen()
        }
    }
}

// ---------------------------------------------------------------- Сборка

@Composable
fun BuildScreen(vm: MainViewModel, openLog: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val ctx = LocalContext.current
    val pickSource = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) vm.setSource(u) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u -> if (u != null) vm.setSource(u) }
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) vm.setLogo(u) }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")) { u -> if (u != null) vm.saveTo(u) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_logo), null, Modifier.size(60.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text("WebApk Builder", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TextHi)
                Text("HTML / ZIP / папка → APK", fontSize = 14.sp, color = TextLo)
            }
        }

        StepCard("1", "Название приложения") {
            OutlinedTextField(
                value = ui.appName, onValueChange = vm::setName, singleLine = true,
                placeholder = { Text("Например, Мой сайт") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextHi, unfocusedTextColor = TextHi,
                    focusedBorderColor = Accent2, unfocusedBorderColor = Surface2, cursorColor = Accent2,
                    focusedPlaceholderColor = TextLo, unfocusedPlaceholderColor = TextLo,
                ),
            )
        }

        StepCard("2", "Источник сайта: HTML, ZIP или папка") {
            FileRow(ui.sourceName ?: "Файл не выбран", if (ui.sourceName == null) "Выбрать файл / ZIP" else "Заменить") {
                pickSource.launch(arrayOf("*/*"))
            }
            SmallButton("Выбрать папку сайта", Modifier.fillMaxWidth()) { pickFolder.launch(null) }
            Text("Поддерживаются HTML, ZIP без index.html и целые папки сайта.", fontSize = 12.sp, color = TextLo)
        }

        StepCard("3", "Логотип (по желанию)") {
            val thumb by produceState<ImageBitmap?>(null, ui.logoUri) {
                value = ui.logoUri?.let { u ->
                    runCatching { withContext(Dispatchers.IO) { decodeBitmap(ctx, u, 256).asImageBitmap() } }.getOrNull()
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val m = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))
                if (thumb != null) Image(thumb!!, null, m, contentScale = ContentScale.Crop)
                else Image(painterResource(R.drawable.ic_logo), null, m.background(Surface2))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(if (ui.logoUri == null) "Выбрать картинку" else "Заменить") { pickLogo.launch(arrayOf("image/*")) }
                    if (ui.logoUri != null) SmallButton("Сбросить") { vm.setLogo(null) }
                    else Text("Без логотипа — буква на градиенте", fontSize = 12.sp, color = TextLo)
                }
            }
        }

        GradientButton(
            text = if (ui.building) "Собираю…" else "Собрать APK",
            enabled = !ui.building && ui.appName.isNotBlank() && ui.sourceUri != null,
            onClick = vm::build,
            modifier = Modifier.fillMaxWidth(),
        )

        AnimatedVisibility(ui.building) {
            Card {
                Text(ui.stage, color = TextHi, fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(
                    progress = { ui.progress }, color = Accent2, trackColor = Surface2,
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                )
            }
        }

        ui.result?.let { f ->
            Card(border = Ok) {
                Text("✓ APK готов", color = Ok, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("${f.name} · ${"%.1f".format(f.length() / 1048576.0)} МБ", color = TextLo, fontSize = 13.sp)
                GradientButton("Установить", onClick = { installApk(ctx, f) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallButton("Сохранить", Modifier.weight(1f)) { saveAs.launch(f.name) }
                    SmallButton("Поделиться", Modifier.weight(1f)) { shareApk(ctx, f) }
                }
            }
        }

        ui.error?.let { msg ->
            Card(border = Err) {
                Text("Не получилось собрать", color = Err, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(msg, color = TextHi, fontSize = 14.sp)
                SmallButton("Открыть журнал", Modifier.fillMaxWidth(), onClick = openLog)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------- Журнал

@Composable
fun JournalScreen() {
    val logs by AppLog.entries.collectAsState()
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val fmt = remember { SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text("Журнал", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = TextHi)
        Text("Записей: ${logs.size} · ошибок: ${logs.count { it.level == 'E' }}", color = TextLo, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("Копировать", Modifier.weight(1f)) {
                clip.setText(AnnotatedString(AppLog.asText()))
                Toast.makeText(ctx, "Скопировано", Toast.LENGTH_SHORT).show()
            }
            SmallButton("Отправить", Modifier.weight(1f)) {
                val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, AppLog.asText())
                ctx.startActivity(Intent.createChooser(i, "Журнал").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            SmallButton("Очистить", Modifier.weight(1f)) { AppLog.clear() }
        }
        Spacer(Modifier.height(12.dp))
        if (logs.isEmpty()) {
            Text("Пока пусто. Здесь появятся этапы сборки и подробности ошибок.", color = TextLo)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(logs.asReversed()) { en ->
                    val c = when (en.level) { 'E' -> Err; 'W' -> Warn; else -> Accent2 }
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(12.dp)).background(Surface1)) {
                        Box(Modifier.width(4.dp).fillMaxHeight().background(c))
                        Column(Modifier.padding(10.dp)) {
                            Text("${fmt.format(Date(en.time))} · ${when (en.level) { 'E' -> "ОШИБКА"; 'W' -> "ВНИМАНИЕ"; else -> "ИНФО" }}",
                                color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(en.msg, color = TextHi, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Компоненты

@Composable
fun Card(border: Color? = null, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    var m = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface1)
    if (border != null) m = m.background(border.copy(alpha = 0.10f))
    Column(m.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
fun StepCard(num: String, title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(Brand), contentAlignment = Alignment.Center) {
                Text(num, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.width(10.dp))
            Text(title, color = TextHi, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        content()
    }
}

@Composable
fun FileRow(name: String, button: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = TextHi, fontSize = 14.sp, maxLines = 2, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        SmallButton(button, onClick = onClick)
    }
}

@Composable
fun SmallButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Surface2, contentColor = TextHi),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, fontSize = 13.sp, maxLines = 1) }
}

@Composable
fun GradientButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.height(56.dp).clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Brand else SolidColor(Surface2))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) Color.White else TextLo, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

// ---------------------------------------------------------------- Установка / отправка

private fun apkUri(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)

fun installApk(ctx: Context, f: File) {
    try {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(ctx, "Разрешите установку из этого приложения и нажмите «Установить» снова", Toast.LENGTH_LONG).show()
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(apkUri(ctx, f), "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        AppLog.i("Запущена установка ${f.name}")
    } catch (t: Throwable) {
        AppLog.e("Не удалось запустить установку", t)
        Toast.makeText(ctx, "Ошибка установки — см. журнал", Toast.LENGTH_LONG).show()
    }
}

fun shareApk(ctx: Context, f: File) {
    try {
        val i = Intent(Intent.ACTION_SEND).setType("application/vnd.android.package-archive")
            .putExtra(Intent.EXTRA_STREAM, apkUri(ctx, f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Отправить APK").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (t: Throwable) {
        AppLog.e("Не удалось отправить APK", t)
    }
}
