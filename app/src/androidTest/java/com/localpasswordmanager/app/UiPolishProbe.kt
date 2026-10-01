package com.localpasswordmanager.app

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import local.passwordmanager.vault.VaultRepository
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * UI 打磨后的独立只读探针。必须显式指定专用合成资料模拟器；不建库、不保存、不删库。
 * 不进入默认会重建夹具的 S1 套件，由调用者显式选择本类。
 * cache/ui-polish 截图仅接受完整已知 S1 夹具，始终保留 FLAG_SECURE。
 */
@RunWith(AndroidJUnit4::class)
class UiPolishProbe {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(DedicatedSyntheticDeviceRule(resetVault = false)).around(compose)

    private var fixtureBytes: ByteArray? = null

    @Test
    fun polishedFlowPreservesTheExistingSyntheticVault() = withReadOnlyFixture {
        captureSyntheticFrame("light-auth", Page.AUTH)
        quickUnlock()
        compose.onNode(entryRow).assertExists()
        captureSyntheticFrame("light-list", Page.LIST)

        input("search", "合成资料")
        compose.onAllNodes(entryRow).assertCountEquals(1)
        openEntry()
        assertDefaultHiddenDetail()
        captureSyntheticFrame("light-detail", Page.DETAIL)
        click("back")
        waitFor("new_entry")
        compose.onNodeWithTag("search").assertTextContains("合成资料")
        compose.onAllNodes(entryRow).assertCountEquals(1)

        input("search", "UiPolishNoMatchSynthetic2026")
        compose.onAllNodes(entryRow).assertCountEquals(0)
        click("clear_search")
        compose.onNodeWithTag("search").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onAllNodes(entryRow).assertCountEquals(1)

        click("new_entry")
        captureSyntheticFrame("light-add", Page.ADD)
        input("entry_title", "尚未保存的合成界面资料")
        input("entry_password", "UiPolishUnsavedSynthetic2026!")
        click("back")
        compose.onNodeWithText("这些输入尚未保存").assertExists()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithTag("entry_title").assertTextContains("尚未保存的合成界面资料")
        click("back")
        compose.onNodeWithText("丢弃输入").performClick()
        waitFor("new_entry")
        compose.onAllNodes(entryRow).assertCountEquals(1)

        openHelp()
        captureSyntheticFrame("light-help", Page.HELP)
        click("back")
        waitFor("new_entry")

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertLocked()
        assertSecureWindow()
    }

    @Test
    fun largeFontAndDarkLayoutKeepControlsReachable() = withReadOnlyFixture {
        val originalFontScale = shell("settings get system font_scale").trim()
        check(originalFontScale == "null" || originalFontScale.matches(Regex("[0-9]+(?:\\.[0-9]+)?"))) {
            "Unsupported font-scale setting; refuse to modify it."
        }
        val originalNightMode = Regex("Night mode: (auto|yes|no|custom)")
            .find(shell("cmd uimode night"))?.groupValues?.get(1)
            ?: error("Could not record the system night mode; refuse to modify it.")
        try {
            shell("settings put system font_scale 1.5")
            shell("cmd uimode night yes")
            compose.waitUntil(OPERATION_TIMEOUT) {
                val configuration = compose.activity.resources.configuration
                configuration.fontScale >= 1.49f &&
                    (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
            waitFor("unlock_synthetic_vault")
            captureSyntheticFrame("dark-large-auth", Page.AUTH)
            quickUnlock()
            input("search", "合成资料")
            hideKeyboard()
            captureSyntheticFrame("dark-large-list", Page.LIST)
            openEntry()
            assertDefaultHiddenDetail()
            captureSyntheticFrame("dark-large-detail", Page.DETAIL)
            click("back")
            waitFor("new_entry")
            compose.onNodeWithTag("search").assertTextContains("合成资料")
            click("clear_search")

            click("new_entry")
            captureSyntheticFrame("dark-large-add", Page.ADD)
            reveal("entry_title")
            reveal("entry_username")
            reveal("entry_password")
            click("more_info")
            reveal("entry_url")
            reveal("entry_notes")
            reveal("save_entry")
            click("back")
            waitFor("new_entry")
            openHelp()
            captureSyntheticFrame("dark-large-help", Page.HELP)
            click("back")
            waitFor("new_entry")
            assertSecureWindow()
        } finally {
            // 系统设置只用于本次专用设备检查；原值恢复，包括原先没有 font_scale 的情况。
            if (originalFontScale == "null") shell("settings delete system font_scale")
            else shell("settings put system font_scale $originalFontScale")
            shell("cmd uimode night $originalNightMode")
        }
    }

    private fun withReadOnlyFixture(block: () -> Unit) {
        val file = vaultFile()
        assertTrue("A pre-existing known S1 synthetic vault is required; this probe never creates one.", file.isFile)
        val original = file.readBytes()
        fixtureBytes = original
        val password = S1UiTest.MASTER.toCharArray()
        try {
            // 只有完整已知夹具才允许视觉导出；未知条目即使在 debug 模拟器中也拒绝。
            VaultRepository(file).unlock(password).use { session ->
                val entries = session.listEntries()
                assertEquals("Refuse screenshots of unknown or additional entries.", 1, entries.size)
                val entry = entries.single()
                check(entry.title == S1UiTest.TITLE && entry.username == S1UiTest.USERNAME &&
                    entry.password == S1UiTest.RAW_ENTRY_FIXTURE && entry.url == "https://synthetic.example.test/login" &&
                    entry.notes == "合成测试备注：NotesProbe84\n第二行；并非真实账号。") {
                    "Refuse screenshots: the vault does not match the known synthetic fixture."
                }
            }
            block()
        } finally {
            password.fill('\u0000')
            assertArrayEquals("UI-only inspection must preserve the existing KDBX bytes.", original, file.readBytes())
            fixtureBytes = null
        }
    }

    private fun quickUnlock() {
        waitFor("unlock_synthetic_vault")
        click("unlock_synthetic_vault")
        waitFor("new_entry")
    }

    private fun openEntry() {
        compose.onAllNodes(entryRow).assertCountEquals(1)
        compose.onNode(entryRow).performScrollTo().performClick()
        waitFor("show_password")
    }

    private fun assertDefaultHiddenDetail() {
        compose.onNodeWithContentDescription("密码已隐藏").assertExists()
        compose.onAllNodesWithText(S1UiTest.RAW_ENTRY_FIXTURE, useUnmergedTree = true).assertCountEquals(0)
        reveal("copy_username")
        reveal("show_password")
        reveal("copy_password")
    }

    private fun assertLocked() {
        waitFor("unlock_vault")
        compose.onNodeWithTag("new_entry").assertDoesNotExist()
        compose.onNodeWithTag("detail_password_text").assertDoesNotExist()
        compose.onAllNodesWithText(S1UiTest.TITLE, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText(S1UiTest.RAW_ENTRY_FIXTURE, useUnmergedTree = true).assertCountEquals(0)
    }

    private fun openHelp() {
        if (compose.onAllNodesWithTag("open_help").fetchSemanticsNodes().isNotEmpty()) click("open_help")
        else compose.onNodeWithText("使用与存储说明").performScrollTo().performClick()
        compose.onNodeWithTag("help_title").assertExists()
    }

    private fun input(tag: String, value: String) {
        reveal(tag)
        compose.onNodeWithTag(tag).performTextReplacement(value)
    }

    private fun click(tag: String) {
        reveal(tag)
        compose.onNodeWithTag(tag).performClick()
    }

    private fun reveal(tag: String) {
        val node = compose.onNodeWithTag(tag)
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsDisplayed()
    }

    private fun waitFor(tag: String) {
        compose.waitUntil(OPERATION_TIMEOUT) {
            val nodes = compose.onAllNodesWithTag(tag).fetchSemanticsNodes()
            nodes.size == 1 && !nodes.single().config.contains(SemanticsProperties.Disabled)
        }
    }

    private fun hideKeyboard() {
        compose.runOnIdle {
            val activity = compose.activity
            activity.getSystemService(InputMethodManager::class.java)
                .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
            activity.currentFocus?.clearFocus()
        }
        compose.waitForIdle()
    }

    private fun assertSecureWindow() {
        compose.runOnIdle {
            assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
    }

    private fun captureSyntheticFrame(name: String, page: Page) {
        assertArrayEquals(checkNotNull(fixtureBytes), vaultFile().readBytes())
        compose.onAllNodesWithText(S1UiTest.RAW_ENTRY_FIXTURE, useUnmergedTree = true).assertCountEquals(0)
        val state = compose.runOnIdle { ViewModelProvider(compose.activity)[VaultViewModel::class.java].ui }
        assertEquals(page, state.page)
        check(state.query.isEmpty() || state.query == "合成资料") { "Refuse to export an unknown search input." }
        if (page == Page.ADD) {
            for (tag in listOf("entry_title", "entry_username", "entry_password", "entry_url", "entry_notes")) {
                val nodes = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
                check(nodes.all { it.config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty().isEmpty() }) {
                    "Only an empty new-entry form may be captured."
                }
            }
        }
        hideKeyboard()
        assertSecureWindow()
        compose.runOnIdle {
            val activity = compose.activity
            val view = activity.window.decorView
            check(view.width > 0 && view.height > 0)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                // 软件绘制本应用 View 树；不使用屏幕截图接口，不修改 FLAG_SECURE。
                view.draw(Canvas(bitmap))
                val sampledColors = mutableSetOf<Int>()
                for (x in 0 until bitmap.width step 17) {
                    for (y in 0 until bitmap.height step 19) sampledColors += bitmap.getPixel(x, y)
                }
                check(sampledColors.size > 6) { "View capture did not contain a rendered UI." }
                val root = activity.cacheDir.canonicalFile
                val directory = File(root, "ui-polish").canonicalFile
                check(directory.parentFile == root)
                check(directory.isDirectory || directory.mkdir())
                File(directory, "$name.png").outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { bitmap.recycle() }
        }
        assertSecureWindow()
    }

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    private fun vaultFile(): File = File(compose.activity.noBackupFilesDir, "vault/vault.kdbx")

    private val entryRow = SemanticsMatcher("entry_row_<uuid>") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("entry_row_") == true
    }

    companion object {
        private const val OPERATION_TIMEOUT = 120_000L
    }
}
