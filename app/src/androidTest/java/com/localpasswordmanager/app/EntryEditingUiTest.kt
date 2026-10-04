package com.localpasswordmanager.app

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.view.inspector.WindowInspector
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import local.passwordmanager.vault.VaultEntry
import local.passwordmanager.vault.VaultRepository
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** 只可在显式指定的专用合成资料模拟器上执行，规则会重建测试密码库。 */
@RunWith(AndroidJUnit4::class)
class EntryEditingUiTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(DedicatedSyntheticDeviceRule(resetVault = true)).around(compose)

    @Test
    fun editPrefillsAllFieldsAndPersistsUnderTheSameIdentity() {
        createVaultAndEntry()
        val original = selected()
        // 首个 tracer 的红灯应定位到尚未实现的详情编辑入口。
        compose.onNodeWithTag("edit_entry").assertExists()
        click("edit_entry")
        assertForm(ORIGINAL)
        replaceForm(EDITED)
        click("save_entry")
        waitFor("show_password")
        assertSelected(EDITED, original.id, original.version + 1)
        compose.runOnIdle { assertEquals(1, viewModel().ui.entries.size) }

        click("lock_vault")
        waitFor("unlock_vault")
        unlock()
        compose.onAllNodes(entryRow).assertCountEquals(1)
        click(entryRow)
        waitFor("show_password")
        assertSelected(EDITED, original.id, original.version + 1)
        click("edit_entry")
        assertForm(EDITED)
    }

    @Test
    fun unchangedCancelAndInvalidEditsPreserveTheSavedEntry() {
        createVaultAndEntry()
        val original = selected()
        val bytes = vaultFile().readBytes()
        click("edit_entry")
        compose.onNodeWithTag("save_entry").assertIsNotEnabled()
        click("back")
        waitFor("show_password")
        compose.onNodeWithText("这些输入尚未保存").assertDoesNotExist()
        assertSelected(ORIGINAL, original.id, original.version)
        assertArrayEquals(bytes, vaultFile().readBytes())

        click("edit_entry")
        input("entry_title", "Unsaved Synthetic 修改未保存")
        hideKeyboard()
        compose.onNodeWithTag("save_entry").assertIsEnabled()
        click("back")
        compose.onNodeWithText("这些输入尚未保存").assertExists()
        click(hasText("继续编辑"))
        assertTrue("Continue editing must retain the pending title.", editableText("entry_title") == "Unsaved Synthetic 修改未保存")
        assertArrayEquals(bytes, vaultFile().readBytes())

        input("entry_title", "")
        hideKeyboard()
        click("save_entry")
        compose.onNodeWithText("名称和密码不能为空；账号可以留空").assertExists()
        assertForm(ORIGINAL.copy(title = ""))
        assertArrayEquals("Validation failure must not change persisted bytes.", bytes, vaultFile().readBytes())
        click("back")
        click(hasText("丢弃输入"))
        waitFor("show_password")
        assertSelected(ORIGINAL, original.id, original.version)
        assertArrayEquals("Discarding must not consume a revision or write the vault.", bytes, vaultFile().readBytes())
    }

    @Test
    fun generatedEditCommitsOnlyOnSaveAndBackgroundDropsLaterPendingChanges() {
        createVaultAndEntry()
        val original = selected()
        val originalBytes = vaultFile().readBytes()
        click("edit_entry")
        click("open_password_generator")
        waitFor("generator_use")
        assertArrayEquals(originalBytes, vaultFile().readBytes())
        click("generator_close")
        assertForm(ORIGINAL)
        compose.onNodeWithTag("save_entry").assertIsNotEnabled()

        click("open_password_generator")
        waitFor("generator_use")
        click("generator_reveal")
        val candidate = text("generator_candidate")
        assertEquals(20, candidate.length)
        click("generator_use")
        val saved = ORIGINAL.copy(password = candidate)
        assertForm(saved)
        assertArrayEquals("Using a candidate only changes the form.", originalBytes, vaultFile().readBytes())
        click("save_entry")
        waitFor("show_password")
        assertSelected(saved, original.id, original.version + 1)
        val savedBytes = vaultFile().readBytes()

        click("edit_entry")
        input("entry_title", "Background discarded 合成编辑")
        hideKeyboard()
        click("open_password_generator")
        waitFor("generator_use")
        click("generator_use")
        assertArrayEquals(savedBytes, vaultFile().readBytes())
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        waitFor("unlock_vault", requireEnabled = false)
        compose.onNodeWithTag("entry_password").assertDoesNotExist()
        compose.onNodeWithTag("generator_sheet").assertDoesNotExist()
        unlock()
        compose.onAllNodes(entryRow).assertCountEquals(1)
        click(entryRow)
        waitFor("show_password")
        assertSelected(saved, original.id, original.version + 1)
        assertArrayEquals("Background must discard only pending edits.", savedBytes, vaultFile().readBytes())
    }

    @Test
    fun listPrivacyAndSimplifiedPagesRemainUsableInNormalAndLargeDarkLayouts() {
        createVaultAndEntry()
        val original = selected()
        val fixtureBytes = verifyOriginalFixture()
        assertDetailHasNoRemovedCopy()
        captureSyntheticPage("normal-detail", Page.DETAIL, fixtureBytes)
        click("edit_entry")
        compose.onNodeWithText("账号密码").assertDoesNotExist()
        captureSyntheticPage("normal-edit", Page.EDIT, fixtureBytes)
        click("back")
        waitFor("show_password")
        click("back")
        waitFor("new_entry")
        assertPrivateList()
        captureSyntheticPage("normal-list", Page.LIST, fixtureBytes)
        click("new_entry")
        compose.onNodeWithText("账号密码").assertDoesNotExist()
        click("back")
        waitFor("new_entry")
        click("lock_vault")
        waitFor("unlock_vault", requireEnabled = false)
        compose.onNodeWithText(REMOVED_DEVELOPMENT_NOTICE).assertDoesNotExist()

        withLargeDarkLayout {
            waitFor("unlock_vault", requireEnabled = false)
            compose.onNodeWithText(REMOVED_DEVELOPMENT_NOTICE).assertDoesNotExist()
            unlock()
            assertPrivateList()
            captureSyntheticPage("dark-large-list", Page.LIST, fixtureBytes)
            click(entryRow)
            waitFor("show_password")
            assertDetailHasNoRemovedCopy()
            captureSyntheticPage("dark-large-detail", Page.DETAIL, fixtureBytes)
            click("edit_entry")
            assertForm(ORIGINAL)
            compose.onNodeWithTag("save_entry").assertIsNotEnabled()
            captureSyntheticPage("dark-large-edit", Page.EDIT, fixtureBytes)
            click("back")
            waitFor("show_password")
            assertSelected(ORIGINAL, original.id, original.version)
        }
        assertArrayEquals("Visual inspection must preserve the synthetic vault.", fixtureBytes, vaultFile().readBytes())
    }

    private fun createVaultAndEntry() {
        input("master_password", S1UiTest.MASTER)
        input("master_confirm", S1UiTest.MASTER)
        hideKeyboard()
        click("create_vault")
        waitFor("new_entry")
        click("new_entry")
        replaceForm(ORIGINAL)
        click("save_entry")
        waitFor("show_password")
    }

    private fun unlock() {
        input("master_password", S1UiTest.MASTER)
        hideKeyboard()
        waitFor("unlock_vault")
        click("unlock_vault")
        waitFor("new_entry")
    }

    private fun replaceForm(fields: Fields) {
        input("entry_title", fields.title)
        input("entry_username", fields.username)
        input("entry_password", fields.password)
        showMoreFields()
        input("entry_url", fields.url)
        input("entry_notes", fields.notes)
        hideKeyboard()
    }

    private fun assertForm(fields: Fields) {
        waitFor("save_entry", requireEnabled = false)
        for ((tag, expected) in listOf("entry_title" to fields.title, "entry_username" to fields.username)) {
            assertTrue("Prefilled field must preserve its exact value: $tag", editableText(tag) == expected)
        }
        assertFormPassword(fields.password)
        showMoreFields()
        for ((tag, expected) in listOf("entry_url" to fields.url, "entry_notes" to fields.notes)) {
            assertTrue("Prefilled field must preserve its exact value: $tag", editableText(tag) == expected)
        }
    }

    private fun showMoreFields() {
        if (compose.onAllNodesWithTag("entry_url").fetchSemanticsNodes().isEmpty()) click("more_info")
    }

    private fun assertFormPassword(expected: String) {
        reveal(hasTestTag("entry_password"))
        val masked = editableText("entry_password")
        assertTrue("Form password must initially be masked.", masked.length == expected.length && masked.all { it == '\u2022' })
        click(hasContentDescription("显示密码"))
        try {
            // Compose EditableText 在隐藏态包含视觉掩码；用户主动显示后才读取原值。
            assertTrue("Form password must preserve the exact original value.", editableText("entry_password") == expected)
        } finally {
            val hide = hasContentDescription("隐藏密码")
            if (compose.onAllNodes(hide).fetchSemanticsNodes().isNotEmpty()) click(hide)
        }
    }

    private fun assertSelected(expected: Fields, id: String, version: Long) {
        val entry = selected()
        assertEquals(id, entry.id)
        assertEquals(version, entry.version)
        assertTrue("Selected entry must preserve all five exact values.",
            entry.title == expected.title && entry.username == expected.username && entry.password == expected.password &&
                entry.url == expected.url && entry.notes == expected.notes)
    }

    private fun assertPrivateList() {
        compose.onAllNodes(entryRow).assertCountEquals(1)
        val row = compose.onNode(entryRow).fetchSemanticsNode()
        val labels = row.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        assertTrue("The list must show the entry title.", ORIGINAL.title in labels)
        assertTrue("List text must contain only the title and its avatar initial.",
            labels.all { it == ORIGINAL.title || it == ORIGINAL.title.take(1) })
        assertEquals(listOf("密码已隐藏"), row.config.getOrNull(SemanticsProperties.ContentDescription))
        compose.onNodeWithText(REMOVED_DEVELOPMENT_NOTICE).assertDoesNotExist()
        compose.onNodeWithText("账号密码").assertDoesNotExist()
        compose.onAllNodesWithText(ORIGINAL.username, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("editing-original.example.test", useUnmergedTree = true).assertCountEquals(0)
    }

    private fun assertDetailHasNoRemovedCopy() {
        compose.onNodeWithText("账号密码").assertDoesNotExist()
        compose.onNodeWithText("请核对目标应用或网站后再粘贴。剪贴板清理受系统与接收方限制。").assertDoesNotExist()
    }

    private fun verifyOriginalFixture(): ByteArray {
        val bytes = vaultFile().readBytes()
        val master = S1UiTest.MASTER.toCharArray()
        try {
            VaultRepository(vaultFile()).unlock(master).use { session ->
                val entries = session.listEntries()
                check(entries.size == 1) { "Screenshots require exactly one known synthetic entry." }
                val entry = entries.single()
                check(entry.title == ORIGINAL.title && entry.username == ORIGINAL.username && entry.password == ORIGINAL.password &&
                    entry.url == ORIGINAL.url && entry.notes == ORIGINAL.notes) { "Refuse screenshots of an unknown fixture." }
            }
        } finally { master.fill('\u0000') }
        return bytes
    }

    private fun captureSyntheticPage(name: String, page: Page, fixtureBytes: ByteArray) {
        assertArrayEquals(fixtureBytes, vaultFile().readBytes())
        compose.runOnIdle { assertEquals(page, viewModel().ui.page) }
        hideKeyboard()
        when (page) {
            Page.LIST -> reveal(entryRow)
            Page.DETAIL -> reveal(hasTestTag("edit_entry"))
            Page.EDIT -> {
                // 先确认底部操作可达，再回到编辑标题留下视觉证据。
                reveal(hasTestTag("save_entry"))
                reveal(hasTestTag("back"))
                reveal(hasText("编辑账号"))
            }
            else -> error("Unsupported screenshot page.")
        }
        val rawPasswordRendered = SemanticsMatcher("rendered synthetic password") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == ORIGINAL.password } ||
                node.config.getOrNull(SemanticsProperties.EditableText)?.text == ORIGINAL.password
        }
        check(compose.onAllNodes(rawPasswordRendered, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
            "Screenshots require the synthetic password to remain masked."
        }
        compose.runOnIdle {
            val activity = compose.activity
            check(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            val view = activity.window.decorView
            check(view.hasWindowFocus() && view.width > 0 && view.height > 0)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                // 仅绘制本应用已验证的合成资料；不使用系统截图、不移除 FLAG_SECURE。
                view.draw(Canvas(bitmap))
                val colors = mutableSetOf<Int>()
                for (x in 0 until bitmap.width step 17) for (y in 0 until bitmap.height step 19) colors += bitmap.getPixel(x, y)
                check(colors.size > 6) { "View capture did not contain a rendered UI." }
                val root = activity.cacheDir.canonicalFile
                val directory = File(root, "entry-editing-ui").canonicalFile
                check(directory.parentFile == root)
                check(directory.isDirectory || directory.mkdir())
                File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
            check(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
    }

    private fun withLargeDarkLayout(block: () -> Unit) {
        val originalConfiguration = compose.runOnIdle { Configuration(compose.activity.resources.configuration) }
        val originalScale = shell("settings get system font_scale").trim()
        check(originalScale == "null" || originalScale.matches(Regex("[0-9]+(?:\\.[0-9]+)?")))
        val originalNight = Regex("Night mode: (auto|yes|no|custom)").find(shell("cmd uimode night"))?.groupValues?.get(1)
            ?: error("Cannot record night mode; refusing to change it.")
        try {
            shell("settings put system font_scale 1.5")
            shell("cmd uimode night yes")
            compose.waitUntil(OPERATION_TIMEOUT) {
                val config = compose.activity.resources.configuration
                config.fontScale >= 1.49f && (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
            block()
        } finally {
            if (originalScale == "null") shell("settings delete system font_scale") else shell("settings put system font_scale $originalScale")
            shell("cmd uimode night $originalNight")
            var restoredSince: Long? = null
            compose.waitUntil(OPERATION_TIMEOUT) {
                val restored = compose.runOnIdle {
                    val activity = compose.activity
                    val config = activity.resources.configuration
                    kotlin.math.abs(config.fontScale - originalConfiguration.fontScale) < 0.01f &&
                        (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        (originalConfiguration.uiMode and Configuration.UI_MODE_NIGHT_MASK) &&
                        !activity.isChangingConfigurations && !activity.window.decorView.isLayoutRequested
                }
                if (!restored) {
                    restoredSince = null
                    false
                } else {
                    val now = SystemClock.uptimeMillis()
                    if (restoredSince == null) restoredSince = now
                    now - checkNotNull(restoredSince) >= 250
                }
            }
            compose.waitForIdle()
        }
    }

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    private fun vaultFile(): File = File(compose.activity.noBackupFilesDir, "vault/vault.kdbx")

    private fun text(tag: String): String = compose.onNodeWithTag(tag).fetchSemanticsNode()
        .config[SemanticsProperties.Text].joinToString("") { it.text }

    private fun selected(): VaultEntry = compose.runOnIdle { checkNotNull(viewModel().ui.selected) }

    private fun viewModel(): VaultViewModel = ViewModelProvider(compose.activity)[VaultViewModel::class.java]

    private fun editableText(tag: String): String = compose.onNodeWithTag(tag).fetchSemanticsNode()
        .config[SemanticsProperties.EditableText].text

    private fun input(tag: String, value: String) {
        reveal(hasTestTag(tag))
        compose.onNodeWithTag(tag).performTextReplacement(value)
    }

    private fun click(tag: String) = click(hasTestTag(tag))

    private fun click(matcher: SemanticsMatcher) {
        reveal(matcher)
        compose.onNode(matcher).performClick()
    }

    private fun reveal(matcher: SemanticsMatcher) {
        var previous: List<Any>? = null
        var stableSince = SystemClock.uptimeMillis()
        // 沿用生成器回归的真实窗口稳定等待，不关闭 IME 动画或替换真实触摸。
        compose.waitUntil(OPERATION_TIMEOUT) {
            val node = compose.onAllNodes(matcher).fetchSemanticsNodes().singleOrNull()
            if (node == null) {
                previous = null
                return@waitUntil false
            }
            val bounds = node.boundsInRoot
            val position = node.positionOnScreen
            val size = node.size
            val window = compose.runOnIdle {
                val decor = WindowInspector.getGlobalWindowViews().lastOrNull { it.isShown && it.hasWindowFocus() }
                    ?: compose.activity.window.decorView
                val insets = ViewCompat.getRootWindowInsets(decor)
                TargetWindow(Rect().also(decor::getWindowVisibleDisplayFrame),
                    insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0,
                    insets?.isVisible(WindowInsetsCompat.Type.ime()) == true,
                    decor.width, decor.height, decor.isLayoutRequested, decor.hasWindowFocus())
            }
            val fullyVisible = compose.onNode(matcher).isDisplayed() &&
                bounds.width >= size.width - 1f && bounds.height >= size.height - 1f &&
                position.x >= window.visible.left - 1f && position.y >= window.visible.top - 1f &&
                position.x + size.width <= window.visible.right + 1f &&
                position.y + size.height <= window.visible.bottom + 1f
            if (!fullyVisible) {
                previous = null
                if (generateSequence(node.parent) { it.parent }.any { it.config.contains(SemanticsActions.ScrollBy) }) {
                    compose.onNode(matcher).performScrollTo()
                }
                return@waitUntil false
            }
            val snapshot = listOf(bounds, position, size, window)
            val now = SystemClock.uptimeMillis()
            if (snapshot != previous || window.layoutPending || !window.focused) {
                previous = snapshot
                stableSince = now
                false
            } else now - stableSince >= 250
        }
        compose.onNode(matcher).assertIsDisplayed()
    }

    private fun waitFor(tag: String, requireEnabled: Boolean = true) {
        compose.waitUntil(OPERATION_TIMEOUT) {
            val nodes = compose.onAllNodesWithTag(tag).fetchSemanticsNodes()
            nodes.size == 1 && (!requireEnabled || !nodes.single().config.contains(SemanticsProperties.Disabled))
        }
    }

    private fun hideKeyboard() {
        compose.runOnIdle {
            val manager = compose.activity.getSystemService(InputMethodManager::class.java)
            for (root in WindowInspector.getGlobalWindowViews()) {
                manager.hideSoftInputFromWindow(root.windowToken, 0)
                root.findFocus()?.clearFocus()
            }
        }
        compose.waitForIdle()
    }

    private val entryRow = SemanticsMatcher("entry_row_<uuid>") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("entry_row_") == true
    }

    private data class TargetWindow(val visible: Rect, val imeBottom: Int, val imeVisible: Boolean,
        val width: Int, val height: Int, val layoutPending: Boolean, val focused: Boolean)

    private data class Fields(val title: String, val username: String, val password: String, val url: String, val notes: String)

    companion object {
        private const val OPERATION_TIMEOUT = 120_000L
        private const val REMOVED_DEVELOPMENT_NOTICE = "内部开发版 · 仅使用合成资料。备份恢复尚未完成，请勿存入真实密码。"
        // 以下均为公开合成资料；空格、组合 Unicode 和换行用于检验原值保真。
        private val ORIGINAL = Fields("Editing Synthetic 合成资料", "  original@example.test  ",
            "  EditOriginal!合成e\u0301\nline-two  ", "https://editing-original.example.test/login", "合成原备注\n保留尾空格  ")
        private val EDITED = Fields("Edited Synthetic 修改资料", "  changed@example.test  ",
            "  EditChanged!合成e\u0301\nline-three  ", "https://editing-changed.example.test/account", "合成新备注\n仍保留尾空格  ")
    }
}
