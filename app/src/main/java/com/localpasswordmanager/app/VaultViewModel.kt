package com.localpasswordmanager.app

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import local.passwordmanager.vault.EntryInput
import local.passwordmanager.vault.VaultEntry
import local.passwordmanager.vault.VaultRepository
import local.passwordmanager.vault.VaultSession
import local.passwordmanager.vault.VaultException
import local.passwordmanager.vault.VaultFailure

enum class Page { AUTH, LIST, ADD, DETAIL, HELP }

data class VaultUi(
    val page: Page = Page.AUTH,
    val exists: Boolean = false,
    val epoch: Long = 0,
    val busy: Boolean = false,
    val entries: List<VaultEntry> = emptyList(),
    val selected: VaultEntry? = null,
    val query: String = "",
    val error: String? = null,
    val message: String? = null,
    val waitSeconds: Int = 0,
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VaultRepository(File(application.noBackupFilesDir, "vault/vault.kdbx"), directorySync = ::syncVaultDirectory)
    private var session: VaultSession? = null
    private var lastInteraction = SystemClock.elapsedRealtime()
    private val throttle = UnlockThrottle(SystemClock::elapsedRealtime)
    private var authenticationPending = false
    var ui by mutableStateOf(VaultUi(exists = repository.exists()))
        private set

    init {
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                val wait = throttle.remainingSeconds()
                if (ui.page == Page.AUTH && ui.waitSeconds != wait) ui = ui.copy(waitSeconds = wait)
                if (session != null && SystemClock.elapsedRealtime() - lastInteraction >= 300_000) lock()
            }
        }
    }

    fun touch() { lastInteraction = SystemClock.elapsedRealtime() }

    fun authenticate(password: String, confirmation: String) {
        if (authenticationPending || ui.busy || throttle.remainingSeconds() > 0 || ui.page != Page.AUTH) return
        val creating = !ui.exists
        if (creating && password != confirmation) {
            ui = ui.copy(error = "两次输入的主密码不一致")
            return
        }
        if (creating && password.codePointCount(0, password.length) !in 8..128) {
            ui = ui.copy(error = "请设置 8–128 个字符的主密码，空格也会保留")
            return
        }
        if (password.isEmpty()) {
            ui = ui.copy(error = "请输入主密码")
            return
        }
        val epoch = ui.epoch
        val secret = password.toCharArray()
        authenticationPending = true
        ui = ui.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try { runCatching { if (creating) repository.create(secret) else repository.unlock(secret) } }
                finally { secret.fill('\u0000') }
            }
            authenticationPending = false
            if (!creating && result.isFailure) throttle.failed()
            if (ui.epoch != epoch) {
                result.getOrNull()?.close()
                ui = ui.copy(exists = repository.exists(), busy = false, waitSeconds = throttle.remainingSeconds())
                return@launch
            }
            result.fold(onSuccess = {
                session = it
                throttle.succeeded()
                touch()
                ui = VaultUi(page = Page.LIST, exists = true, epoch = epoch, entries = it.listEntries())
            }, onFailure = {
                // Underlying exception messages can include decrypted values. Never display or log them.
                ui = ui.copy(busy = false, exists = repository.exists(), waitSeconds = throttle.remainingSeconds(), error = if (creating)
                    "创建未完成，请检查本机可用空间后重试。已有文件不会被覆盖。"
                else "主密码不正确或文件无法读取。请核对密码；原文件已保留。")
            })
        }
    }

    fun search(query: String) {
        val active = session ?: return
        touch()
        ui = ui.copy(query = query, entries = active.listEntries(query))
    }

    fun add() {
        if (session != null && !ui.busy) ui = ui.copy(page = Page.ADD, error = null, message = null)
    }

    fun detail(id: String) {
        val active = session ?: return
        ui = ui.copy(page = Page.DETAIL, selected = active.getEntry(id), error = null, message = null)
    }

    fun save(input: EntryInput) {
        val active = session ?: return
        if (ui.busy || ui.page != Page.ADD) return
        val fields = listOf(input.title to 100, input.username to 256, input.password to 1024,
            input.url to 2048, input.notes to 10000)
        if (input.title.isEmpty() || input.password.isEmpty()) {
            ui = ui.copy(error = "名称和密码不能为空；账号可以留空")
            return
        }
        if (fields.any { (text, limit) -> text.codePointCount(0, text.length) > limit }) {
            ui = ui.copy(error = "内容超过上限：名称 100、账号 256、密码 1024、网址 2048、备注 10000 个字符。请缩短后重试；输入未被截断。")
            return
        }
        val epoch = ui.epoch
        ui = ui.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { active.saveEntry(input) } }
            if (ui.epoch != epoch) return@launch
            result.fold(onSuccess = {
                ui = ui.copy(page = Page.DETAIL, busy = false, selected = it,
                    entries = active.listEntries(ui.query), message = "已保存")
            }, onFailure = {
                val message = when ((it as? VaultException)?.reason) {
                    VaultFailure.INVALID_INPUT -> "输入包含不支持的字符或超过上限，请核对后重试；输入未被截断。"
                    VaultFailure.CONFLICT -> "密码库已变化。请先锁定并重新解锁核对记录，避免重复保存；当前输入仍保留。"
                    else -> "无法确认保存结果。请检查可用空间，并先锁定重开核对记录；若记录尚未保存，再重试。当前输入仍保留。"
                }
                ui = ui.copy(busy = false, error = message)
            })
        }
    }

    fun list() {
        if (session != null && !ui.busy) ui = ui.copy(page = Page.LIST, selected = null, error = null, message = null)
    }

    fun help() { if (!ui.busy) ui = ui.copy(page = Page.HELP, error = null, message = null) }
    fun leaveHelp() { ui = ui.copy(page = if (session == null) Page.AUTH else Page.LIST) }
    fun message(value: String) { if (session != null) ui = ui.copy(message = value) }

    fun authorized(epoch: Long, id: String, version: Long): Boolean =
        session != null && ui.epoch == epoch && ui.page == Page.DETAIL &&
            ui.selected?.id == id && ui.selected?.version == version

    fun credential(epoch: Long, id: String, version: Long, includePassword: Boolean): String? {
        if (!authorized(epoch, id, version)) return null
        val entry = session?.getEntry(id)?.takeIf { it.version == version } ?: return null
        touch()
        return if (includePassword) entry.password else entry.username
    }

    fun lock() {
        // Revoke UI authorization before any disk operation completes. Old callbacks cannot restore it.
        val active = session
        session = null
        ui = VaultUi(exists = repository.exists(), epoch = ui.epoch + 1,
            busy = authenticationPending, waitSeconds = throttle.remainingSeconds())
        active?.close()
    }

    override fun onCleared() { session?.close() }
}
