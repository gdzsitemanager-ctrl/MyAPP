package com.webapk.builder

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UiState(
    val appName: String = "",
    val sourceUri: Uri? = null,
    val sourceName: String? = null,
    val logoUri: Uri? = null,
    val building: Boolean = false,
    val progress: Float = 0f,
    val stage: String = "",
    val result: File? = null,
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    fun setName(s: String) = _ui.update { it.copy(appName = s.take(40)) }

    fun setSource(uri: Uri) {
        val name = displayName(getApplication(), uri)
        AppLog.i("Выбран файл: $name")
        _ui.update {
            it.copy(
                sourceUri = uri, sourceName = name, result = null, error = null,
                appName = if (it.appName.isBlank()) name.substringBeforeLast('.').take(40) else it.appName,
            )
        }
    }

    fun setLogo(uri: Uri?) {
        if (uri != null) AppLog.i("Выбран логотип: ${displayName(getApplication(), uri)}")
        _ui.update { it.copy(logoUri = uri) }
    }

    fun build() {
        val s = _ui.value
        val src = s.sourceUri ?: return
        if (s.building) return
        _ui.update { it.copy(building = true, progress = 0f, stage = "Старт", result = null, error = null) }
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    ApkBuilder(getApplication()).build(s.appName.trim(), src, s.logoUri) { p, st ->
                        _ui.update { it.copy(progress = p, stage = st) }
                    }
                }
                _ui.update { it.copy(building = false, result = file) }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                AppLog.e("Сборка не удалась", t)
                _ui.update { it.copy(building = false, error = t.message ?: t.javaClass.simpleName) }
            }
        }
    }

    fun saveTo(uri: Uri) {
        val f = _ui.value.result ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri)!!.use { o -> f.inputStream().use { it.copyTo(o) } }
                }.onFailure { AppLog.e("Не удалось сохранить файл", it) }.isSuccess
            }
            if (ok) AppLog.i("APK сохранён пользователем")
            Toast.makeText(getApplication(), if (ok) "Сохранено" else "Ошибка сохранения — см. журнал", Toast.LENGTH_SHORT).show()
        }
    }
}
