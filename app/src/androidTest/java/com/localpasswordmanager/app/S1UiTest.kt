package com.localpasswordmanager.app

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Bundle
import android.os.Build
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** AC-05/07/12 的 S1 子集；只在显式指定的合成资料模拟器上运行。 */
@RunWith(AndroidJUnit4::class)
class S1UiTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(DedicatedSyntheticDeviceRule(resetVault = true)).around(compose)

    @Test
    fun mismatchedConfirmationDoesNotCreateVault() {
        input("master_password", MASTER)
        input("master_confirm", "$MASTER-different")
        click("create_vault")

        compose.onNodeWithTag("master_confirm").assertExists()
        compose.onNodeWithText("两次输入的主密码不一致").assertExists()
        compose.onNodeWithTag("new_entry").assertDoesNotExist()
        assertFalse(vaultFile().exists())
        assertSecureScreen()
        assertSavedStateContainsNoSecrets(recreateAndCaptureSavedState())
    }

    @Test
    fun masterPasswordMinimumAllowsEightCharacters() {
        // 公开合成主密码，恰好 8 个 ASCII 字符；只用于专用测试模拟器。
        val eightCharacterMaster = "Synt8!ab"
        val sevenCharacterMaster = eightCharacterMaster.dropLast(1)
        input("master_password", sevenCharacterMaster)
        input("master_confirm", sevenCharacterMaster)
        click("create_vault")

        compose.onNodeWithText("请设置 8–128 个字符的主密码，空格也会保留").assertExists()
        compose.onNodeWithTag("master_confirm").assertExists()
        compose.onNodeWithTag("new_entry").assertDoesNotExist()
        assertFalse(vaultFile().exists())

        input("master_password", eightCharacterMaster)
        input("master_confirm", eightCharacterMaster)
        click("create_vault")
        waitFor("new_entry")
        assertTrue(vaultFile().isFile)
        addSyntheticEntry()
        click("lock_vault")
        assertLocked()

        unlock(eightCharacterMaster)
        waitFor("new_entry")
        openOnlyEntry()
        assertPasswordHidden()
        click("show_password")
        compose.onNodeWithTag("detail_password_text").assertTextEquals(RAW_ENTRY_FIXTURE)
    }

    @Test
    fun savedFieldsSurviveSearchBackgroundLockAndActivityRecreation() {
        createVaultAndEntry()
        assertPasswordHidden()
        click("show_password")
        compose.onNodeWithTag("detail_password_text").assertTextEquals(RAW_ENTRY_FIXTURE)
        click("back")

        // 派生搜索能匹配名称，但不能把密码或备注变成明文搜索索引。
        input("search", "sYnThEtIc")
        assertEntryCount(1)
        input("search", PASSWORD_SEARCH)
        assertEntryCount(0)
        input("search", NOTES_SEARCH)
        assertEntryCount(0)
        input("search", "合成资料")
        assertEntryCount(1)
        openOnlyEntry()
        assertPasswordHidden()

        val viewModel = viewModel()
        val previousAuthorization = compose.runOnIdle {
            val state = viewModel.ui
            val selected = checkNotNull(state.selected)
            Triple(state.epoch, selected.id, selected.version)
        }
        click("show_password")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertLocked()
        assertPreviousAuthorizationRejected(viewModel, previousAuthorization)

        unlock("incorrect-synthetic-master")
        assertLocked()
        compose.onNodeWithText("主密码不正确或文件无法读取。请核对密码；原文件已保留。").assertExists()
        unlock(MASTER)
        waitFor("new_entry")
        input("search", "")
        openOnlyEntry()
        assertPasswordHidden()
        assertPreviousAuthorizationRejected(viewModel, previousAuthorization)
        click("show_password")
        compose.onNodeWithTag("detail_password_text").assertTextEquals(RAW_ENTRY_FIXTURE)
        compose.onNodeWithText(URL).assertExists()
        compose.onNodeWithText(NOTES).assertExists()

        // 重新创建 Activity 强制由持久库重开，不能仅靠原 UI 的内存值通过。
        assertSavedStateContainsNoSecrets(recreateAndCaptureSavedState())
        assertLocked()
        unlock(MASTER)
        waitFor("new_entry")
        openOnlyEntry()
        assertPasswordHidden()
        click("show_password")
        compose.onNodeWithTag("detail_password_text").assertTextEquals(RAW_ENTRY_FIXTURE)
        assertClipboardValue("copy_username", USERNAME)
        assertClipboardValue("copy_password", RAW_ENTRY_FIXTURE)
        assertTrue(vaultFile().isFile)
    }

    @Test
    fun backgroundDuringUnlockDoesNotRestoreExpiredAuthorization() {
        createVaultAndEntry()
        click("lock_vault")
        input("master_password", MASTER)
        waitUntilEnabled("unlock_vault")
        val viewModel = compose.runOnIdle {
            ViewModelProvider(compose.activity)[VaultViewModel::class.java]
        }
        val existingJobs = compose.runOnIdle {
            viewModel.viewModelScope.coroutineContext[Job]!!.children.toSet()
        }
        click("unlock_vault")
        waitFor("operation_busy")
        val authenticationJob = compose.runOnIdle {
            viewModel.viewModelScope.coroutineContext[Job]!!.children
                .single { it !in existingJobs }
        }

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        // 只观察现有 coroutine 的完成，不增加生产测试开关或改变运算时序。
        compose.waitUntil(OPERATION_TIMEOUT) { authenticationJob.isCompleted }
        assertLocked()
        // 过期运算结束后仍需一次新的用户认证。
        unlock(MASTER)
        try {
            waitFor("new_entry")
        } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
            // 只记录门禁状态和已知合成输入的相等结果，不输出凭据或完整 UI state。
            val state = compose.runOnIdle { viewModel.ui }
            val inputMatches = compose.onAllNodesWithTag("master_password").fetchSemanticsNodes()
                .singleOrNull()?.config?.getOrNull(SemanticsProperties.EditableText)?.text == MASTER
            throw AssertionError("Retry authentication: page=${state.page}, epoch=${state.epoch}, " +
                "busy=${state.busy}, waitSeconds=${state.waitSeconds}, hasError=${state.error != null}, " +
                "syntheticInputMatches=$inputMatches", failure)
        }
        openOnlyEntry()
        assertPasswordHidden()
    }

    @Test
    fun clipboardKeepsRawSensitiveValueAndDoesNotEraseLaterEqualText() {
        createVaultAndEntry()
        assertClipboardValue("copy_username", USERNAME)
        assertClipboardValue("copy_password", RAW_ENTRY_FIXTURE)

        // 原值相同但来源不同：清理不能仅靠字符串相等判断所有权。
        val replacement = ClipData.newPlainText("synthetic later copy", RAW_ENTRY_FIXTURE)
        compose.runOnIdle { clipboard().setPrimaryClip(replacement) }
        Thread.sleep(31_000)
        compose.waitForIdle()
        assertLaterClipPreserved()

        // 整份 ClipData 被新来源复制时，自定义 ID 会保留，系统写入时间仍不同。
        assertClipboardValue("copy_password", RAW_ENTRY_FIXTURE)
        val original = compose.runOnIdle { checkNotNull(clipboard().primaryClip) }
        val originalId = original.description.extras!!.getString(CLIP_ID)
        val originalTimestamp = original.description.timestamp
        Thread.sleep(20)
        compose.runOnIdle { clipboard().setPrimaryClip(ClipData(original)) }
        val laterTimestamp = compose.runOnIdle {
            checkNotNull(clipboard().primaryClipDescription).timestamp
        }
        assertNotEquals(originalTimestamp, laterTimestamp)
        Thread.sleep(31_000)
        compose.waitForIdle()
        val laterClip = compose.runOnIdle { checkNotNull(clipboard().primaryClip) }
        assertEquals(RAW_ENTRY_FIXTURE, laterClip.getItemAt(0).text.toString())
        assertEquals(originalId, laterClip.description.extras!!.getString(CLIP_ID))
        assertEquals(laterTimestamp, laterClip.description.timestamp)

        // 锁定清理也须保留后来复制的相同文本。
        assertClipboardValue("copy_password", RAW_ENTRY_FIXTURE)
        compose.runOnIdle { clipboard().setPrimaryClip(replacement) }
        click("lock_vault")
        assertLocked()
        assertLaterClipPreserved()
        unlock(MASTER)
        waitFor("new_entry")
        openOnlyEntry()
        assertClipboardValue("copy_password", RAW_ENTRY_FIXTURE)
        click("lock_vault")
        assertLocked()
        assertClipboardEmpty()
    }

    @Test
    fun clipboardClearsOwnedCopyAtItsDeadline() {
        createVaultAndEntry()
        assertClipboardValue("copy_password", RAW_ENTRY_FIXTURE)
        Thread.sleep(31_000)
        compose.waitForIdle()
        assertClipboardEmpty()
    }

    @Test
    fun clipboardDoesNotLeaveOwnedContentAfterReturningFromBackgroundPastDeadline() {
        createVaultAndEntry()
        assertClipboardValue("copy_password", RAW_ENTRY_FIXTURE)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        executeShell("input keyevent KEYCODE_HOME")
        compose.waitUntil(10_000) {
            compose.activityRule.scenario.state == Lifecycle.State.CREATED
        }
        Thread.sleep(31_000)
        // NEW_TASK | REORDER_TO_FRONT 返回现有 Activity，验证重新获得焦点时的核验。
        executeShell("am start -W -f 0x10020000 -n ${instrumentation.targetContext.packageName}/${MainActivity::class.java.name}")
        compose.waitUntil(10_000) {
            compose.activityRule.scenario.state == Lifecycle.State.RESUMED
        }
        assertLocked()
        compose.waitUntil(10_000) {
            compose.runOnIdle { clipboard().primaryClip == null }
        }
        assertClipboardEmpty()
    }

    @Test
    fun secureScreenDoesNotSaveSecretsAndUnsavedReturnRequiresExplicitDiscard() {
        createVaultAndEntry()
        assertSecureScreen()
        val originalFile = vaultFile().readBytes()
        click("back")
        input("search", "SearchBundleProbe23")
        assertSecureScreen()
        input("search", "")
        click("new_entry")
        input("entry_title", "尚未保存的合成条目")
        input("entry_username", USERNAME)
        input("entry_password", "UnsavedSecretProbe95")
        click("more_info")
        input("entry_url", URL)
        input("entry_notes", NOTES)
        assertSecureScreen()
        click("back")
        compose.onNodeWithText("这些输入尚未保存").assertExists()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithTag("entry_password").assertTextContains("UnsavedSecretProbe95")
        assertArrayEquals(originalFile, vaultFile().readBytes())
        assertSavedStateContainsNoSecrets(recreateAndCaptureSavedState())
        assertLocked()
        unlock(MASTER)
        waitFor("new_entry")
        assertEntryCount(1)
        assertArrayEquals(originalFile, vaultFile().readBytes())
        click("new_entry")
        input("entry_title", "尚未保存的合成条目")
        input("entry_password", "UnsavedSecretProbe95")
        click("back")
        compose.onNodeWithText("丢弃输入").performClick()
        waitFor("new_entry")
        assertEntryCount(1)
        assertArrayEquals(originalFile, vaultFile().readBytes())
    }

    private fun createVaultAndEntry() {
        input("master_password", MASTER)
        input("master_confirm", MASTER)
        click("create_vault")
        waitFor("new_entry")
        addSyntheticEntry()
    }

    private fun addSyntheticEntry() {
        click("new_entry")
        input("entry_title", TITLE)
        input("entry_username", USERNAME)
        input("entry_password", RAW_ENTRY_FIXTURE)
        click("more_info")
        input("entry_url", URL)
        input("entry_notes", NOTES)
        click("save_entry")
        waitFor("show_password")
    }

    private fun input(tag: String, value: String) {
        compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
    }

    private fun click(tag: String) {
        // 顶部工具栏不属于正文滚动容器。
        if (tag == "lock_vault") compose.onNodeWithTag(tag).performClick()
        else {
            compose.onNodeWithTag(tag).performScrollTo()
            if (tag == "unlock_vault") waitForStableUnlockTarget()
            compose.onNodeWithTag(tag).performClick()
        }
    }

    private fun waitForStableUnlockTarget() {
        // Compose 虚拟时钟不会驱动系统 IME 动画。真实触摸前核对完整可见性与原生窗口，
        // 不关动画或键盘，也不以语义 OnClick 替代触摸；原认证断言与超时仍保留。
        var previous: List<Any>? = null
        var stableSince = SystemClock.uptimeMillis()
        compose.waitUntil(OPERATION_TIMEOUT) {
            val node = compose.onAllNodesWithTag("unlock_vault").fetchSemanticsNodes().singleOrNull()
            if (node == null || node.config.contains(SemanticsProperties.Disabled)) {
                previous = null
                return@waitUntil false
            }
            val bounds = node.boundsInRoot
            val position = node.positionOnScreen
            val size = node.size
            val window = compose.runOnIdle {
                val decor = compose.activity.window.decorView
                val visible = android.graphics.Rect().also(decor::getWindowVisibleDisplayFrame)
                val insets = ViewCompat.getRootWindowInsets(decor)
                UnlockWindow(visible, insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0,
                    insets?.isVisible(WindowInsetsCompat.Type.ime()) == true,
                    decor.width, decor.height, decor.isLayoutRequested)
            }
            val fullyVisible = bounds.width >= size.width - 1f && bounds.height >= size.height - 1f &&
                position.x >= window.visible.left - 1f && position.y >= window.visible.top - 1f &&
                position.x + size.width <= window.visible.right + 1f &&
                position.y + size.height <= window.visible.bottom + 1f
            if (!fullyVisible) {
                previous = null
                compose.onNodeWithTag("unlock_vault").performScrollTo()
                return@waitUntil false
            }
            val snapshot = listOf(bounds, position, size, window)
            val now = SystemClock.uptimeMillis()
            if (snapshot != previous || window.layoutPending) {
                previous = snapshot
                stableSince = now
                false
            } else now - stableSince >= 250
        }
    }

    private data class UnlockWindow(val visible: android.graphics.Rect, val imeBottom: Int,
        val imeVisible: Boolean, val width: Int, val height: Int, val layoutPending: Boolean)

    private fun unlock(masterPassword: String) {
        input("master_password", masterPassword)
        waitUntilEnabled("unlock_vault")
        click("unlock_vault")
        compose.waitUntil(OPERATION_TIMEOUT) {
            val nodes = compose.onAllNodesWithTag("unlock_vault").fetchSemanticsNodes()
            nodes.isEmpty() || !nodes.single().config.contains(SemanticsProperties.Disabled)
        }
    }

    private fun waitUntilEnabled(tag: String) {
        compose.waitUntil(OPERATION_TIMEOUT) {
            val nodes = compose.onAllNodesWithTag(tag).fetchSemanticsNodes()
            nodes.isNotEmpty() && !nodes.single().config.contains(SemanticsProperties.Disabled)
        }
    }

    private fun waitFor(tag: String) {
        compose.waitUntil(OPERATION_TIMEOUT) { tagExists(tag) }
    }

    private fun tagExists(tag: String): Boolean =
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun assertEntryCount(expected: Int) {
        compose.onAllNodes(entryRow).assertCountEquals(expected)
    }

    private fun openOnlyEntry() {
        assertEntryCount(1)
        compose.onNode(entryRow).performScrollTo().performClick()
        waitFor("show_password")
    }

    private fun assertPasswordHidden() {
        compose.onNodeWithTag("detail_password_text").assertExists()
        compose.onNodeWithContentDescription("密码已隐藏").assertExists()
        compose.onAllNodesWithText(RAW_ENTRY_FIXTURE, useUnmergedTree = true).assertCountEquals(0)
    }

    private fun assertLocked() {
        waitFor("unlock_vault")
        compose.onNodeWithTag("new_entry").assertDoesNotExist()
        compose.onNodeWithTag("detail_password_text").assertDoesNotExist()
        compose.onAllNodesWithText(TITLE, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText(USERNAME, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText(RAW_ENTRY_FIXTURE, useUnmergedTree = true).assertCountEquals(0)
    }

    private fun assertClipboardValue(copyTag: String, expected: String) {
        click(copyTag)
        val clip = compose.runOnIdle { clipboard().primaryClip }
        assertNotNull(clip)
        assertEquals(expected, clip!!.getItemAt(0).text.toString())
        assertTrue(clip.description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true)
        assertNotNull(clip.description.extras?.getString(CLIP_ID))
    }

    private fun assertLaterClipPreserved() {
        val clip = compose.runOnIdle { clipboard().primaryClip }
        assertNotNull(clip)
        assertEquals(RAW_ENTRY_FIXTURE, clip!!.getItemAt(0).text.toString())
        assertEquals("synthetic later copy", clip.description.label.toString())
        assertNull(clip.description.extras?.getString(CLIP_ID))
    }

    private fun assertClipboardEmpty() {
        val clip = compose.runOnIdle { clipboard().primaryClip }
        assertTrue(clip == null || clip.itemCount == 0 || clip.getItemAt(0).text.isNullOrEmpty())
    }

    private fun clipboard(): ClipboardManager =
        compose.activity.getSystemService(ClipboardManager::class.java)

    private fun viewModel(): VaultViewModel = compose.runOnIdle {
        ViewModelProvider(compose.activity)[VaultViewModel::class.java]
    }

    private fun assertPreviousAuthorizationRejected(viewModel: VaultViewModel, authorization: Triple<Long, String, Long>) {
        compose.runOnIdle {
            assertFalse(viewModel.authorized(authorization.first, authorization.second, authorization.third))
            assertNull(viewModel.credential(authorization.first, authorization.second, authorization.third, includePassword = true))
            assertNull(viewModel.credential(authorization.first, authorization.second, authorization.third, includePassword = false))
        }
    }

    private fun assertSecureScreen() {
        compose.runOnIdle {
            assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
    }

    private fun recreateAndCaptureSavedState(): Bundle {
        val originalActivity = compose.activity
        var savedState: Bundle? = null
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPostSaveInstanceState(activity: Activity, outState: Bundle) {
                if (activity === originalActivity) savedState = Bundle(outState)
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        compose.runOnIdle { originalActivity.application.registerActivityLifecycleCallbacks(callbacks) }
        try {
            // 通过真实 lifecycle 捕获最终 Bundle；直接调用 onSaveInstanceState 会改变 lifecycle。
            compose.activityRule.scenario.recreate()
        } finally {
            compose.runOnIdle { originalActivity.application.unregisterActivityLifecycleCallbacks(callbacks) }
        }
        return checkNotNull(savedState) { "Activity recreation did not produce a saved-state Bundle." }
    }

    private fun assertSavedStateContainsNoSecrets(bundle: Bundle) {
        val parcel = Parcel.obtain()
        val stateBytes = try { parcel.writeBundle(bundle); parcel.marshall() } finally { parcel.recycle() }
        val sensitiveValues = listOf(MASTER, TITLE, USERNAME, RAW_ENTRY_FIXTURE, URL, NOTES,
            "SearchBundleProbe23", "UnsavedSecretProbe95", "尚未保存的合成条目")
        for (value in sensitiveValues) {
            for (encoding in listOf(Charsets.UTF_8, Charsets.UTF_16LE)) {
                val secretBytes = value.toByteArray(encoding)
                assertFalse("Sensitive UI state must not be serialized into the saved Bundle.",
                    stateBytes.indices.any { index ->
                        index + secretBytes.size <= stateBytes.size &&
                            secretBytes.indices.all { stateBytes[index + it] == secretBytes[it] }
                    })
            }
        }
    }

    private fun executeShell(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private fun vaultFile(): File = File(compose.activity.noBackupFilesDir, "vault/vault.kdbx")

    private val entryRow = SemanticsMatcher("entry_row_<uuid>") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("entry_row_") == true
    }

    // 以下值是公开合成测试夹具，不是真实账号或凭据；空格、换行和 Unicode 原值均用于保真断言。
    companion object {
        internal const val MASTER = "  Synthetic-Master-合成-2026  "
        internal const val TITLE = "Synthetic 合成资料"
        internal const val USERNAME = "  合成User e\u0301\nsecond@example.test  "
        private const val PASSWORD_SEARCH = "SecretProbe71"
        internal const val RAW_ENTRY_FIXTURE = "  SecretProbe71!合成e\u0301\nsecond-line  "
        private const val NOTES_SEARCH = "NotesProbe84"
        private const val URL = "https://synthetic.example.test/login"
        private const val NOTES = "合成测试备注：NotesProbe84\n第二行；并非真实账号。"
        private const val CLIP_ID = "com.localpasswordmanager.clip_id"
        private const val OPERATION_TIMEOUT = 120_000L
    }
}

/** 清理发生于 Activity 启动前；误选真实设备或漏传合成测试参数时直接拒绝。 */
internal class DedicatedSyntheticDeviceRule(private val resetVault: Boolean = false) : ExternalResource() {
    override fun before() {
        check(InstrumentationRegistry.getArguments().getString("dedicatedSyntheticDevice") == "true") {
            "Require dedicatedSyntheticDevice=true and a dedicated synthetic emulator."
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".dev")) { "Synthetic tests require the debug applicationId." }
        check(Build.FINGERPRINT.startsWith("generic") || Build.MODEL.contains("sdk") || Build.MODEL.contains("Emulator")) {
            "Synthetic reset is restricted to an emulator."
        }
        if (!resetVault) return
        val root = context.noBackupFilesDir.canonicalFile
        val vaultDirectory = File(root, "vault").canonicalFile
        check(vaultDirectory.parentFile == root && vaultDirectory.name == "vault")
        check(!vaultDirectory.exists() || vaultDirectory.deleteRecursively()) {
            "Could not reset the dedicated synthetic vault."
        }
    }
}
