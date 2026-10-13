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
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import local.passwordmanager.vault.VaultRepository
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * 主页真实界面导出入口，须显式指定专用合成模拟器；不在默认回归套件内。
 * 只在不存在密码库时，经正常 UI 创建公开夹具；已有库必须逐字段完全匹配。
 * 不重建、不删除、不覆盖已有库。截图阶段只读，保留 Activity/Dialog 的 FLAG_SECURE。
 */
@RunWith(AndroidJUnit4::class)
class WebsiteScreenshotProbe {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(DedicatedSyntheticDeviceRule(resetVault = false)).around(compose)

    @Test
    fun exportCurrentLightScreensWithOnlyPublicSyntheticData() = withLightLayout {
        check(InstrumentationRegistry.getArguments().getString("websiteScreenshotFixture") == "20261014") {
            "Explicit websiteScreenshotFixture=20261014 is required."
        }
        if (vaultFile().exists()) {
            verifyFixture()
            waitFor("unlock_vault")
            input("master_password", MASTER)
            hideKeyboard()
            click("unlock_vault")
            waitFor("new_entry")
        } else {
            val directory = vaultFile().parentFile!!
            check(!directory.exists() || directory.listFiles().orEmpty().isEmpty()) {
                "Refuse initialization when any existing vault files are present."
            }
            input("master_password", MASTER)
            input("master_confirm", MASTER)
            hideKeyboard()
            click("create_vault")
            waitFor("new_entry")
            // 经真实新增流程建立夹具；页面继续使用 App 自身的默认排序。
            for (fixture in FIXTURES.reversed()) {
                click("new_entry")
                input("entry_title", fixture.title)
                input("entry_username", fixture.username)
                input("entry_password", fixture.password)
                click("more_info")
                input("entry_url", fixture.url)
                input("entry_notes", fixture.notes)
                hideKeyboard()
                click("save_entry")
                waitFor("show_password")
                click("back")
                waitFor("new_entry")
            }
        }
        val original = verifyFixture()
        try {
            compose.onAllNodes(entryRow).assertCountEquals(3)
            compose.onAllNodes(hasContentDescription("密码已隐藏"), useUnmergedTree = true).assertCountEquals(3)
            for (fixture in FIXTURES) {
                compose.onAllNodes(hasText(fixture.username), useUnmergedTree = true).assertCountEquals(0)
                compose.onAllNodes(hasText(fixture.url), useUnmergedTree = true).assertCountEquals(0)
            }
            reveal(hasTestTag("search"))
            capture("qimi-list", Page.LIST, original)

            click(entryRow and hasText(FIXTURES.first().title))
            waitFor("show_password")
            compose.onNode(hasContentDescription("密码已隐藏"), useUnmergedTree = true).assertExists()
            capture("qimi-detail", Page.DETAIL, original)

            click("edit_entry")
            waitFor("save_entry", requireEnabled = false)
            compose.onNodeWithTag("save_entry").assertIsNotEnabled()
            assertEquals(FIXTURES.first().username, editableText("entry_username"))
            check(editableText("entry_password") == "•".repeat(FIXTURES.first().password.length))
            capture("qimi-edit", Page.EDIT, original)

            click("open_password_generator")
            waitFor("generator_use")
            click("generator_advanced")
            input("generator_length", "20")
            hideKeyboard()
            for (type in listOf("LOWERCASE", "UPPERCASE", "DIGITS", "SYMBOLS")) click("type_$type")
            click("generator_regenerate")
            compose.onNodeWithTag("generator_use").assertIsEnabled()
            compose.onNodeWithTag("generator_advanced").assertIsOn()
            for (preset in listOf("SIMPLE", "NORMAL", "COMPLEX")) compose.onNodeWithTag("preset_$preset").assertIsNotEnabled()
            for (type in listOf("LOWERCASE", "UPPERCASE", "DIGITS", "SYMBOLS")) compose.onNodeWithTag("type_$type").assertIsOn()
            assertEquals("20", editableText("generator_length"))
            compose.onNode(hasContentDescription("生成的密码已隐藏"), useUnmergedTree = true).assertExists()
            reveal(hasTestTag("generator_candidate"))
            capture("qimi-generator", Page.EDIT, original, dialog = true)
            click("generator_close")
            click("back")
            waitFor("show_password")
            click("lock_vault")
            waitFor("unlock_vault")
        } finally {
            assertArrayEquals("Screenshot navigation must preserve the complete KDBX bytes.", original, vaultFile().readBytes())
        }
    }

    private fun verifyFixture(): ByteArray {
        val file = vaultFile()
        check(file.isFile)
        val master = MASTER.toCharArray()
        try {
            VaultRepository(file).unlock(master).use { session ->
                val actual = session.listEntries().map { Fields(it.title, it.username, it.password, it.url, it.notes) }
                check(actual.size == FIXTURES.size && actual.toSet() == FIXTURES.toSet()) {
                    "Refuse export of unknown, altered, partial, or additional records."
                }
            }
        } finally { master.fill('\u0000') }
        return file.readBytes()
    }

    private fun capture(name: String, page: Page, original: ByteArray, dialog: Boolean = false) {
        assertArrayEquals(original, verifyFixture())
        hideKeyboard()
        compose.runOnIdle { assertEquals(page, ViewModelProvider(compose.activity)[VaultViewModel::class.java].ui.page) }
        val rawPasswordRendered = SemanticsMatcher("rendered synthetic password") { node ->
            FIXTURES.any { fixture ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.contains(fixture.password) } ||
                    node.config.getOrNull(SemanticsProperties.EditableText)?.text?.contains(fixture.password) == true
            }
        }
        check(compose.onAllNodes(rawPasswordRendered, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
            "Passwords must remain hidden in every exported frame."
        }
        compose.runOnIdle {
            val activity = compose.activity
            val decor = activity.window.decorView
            check(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            val roots = WindowInspector.getGlobalWindowViews().filter { it.isShown }
            for (root in roots) {
                val flags = (root.layoutParams as? WindowManager.LayoutParams)?.flags ?: error("Unknown window.")
                check(flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
            val overlays = roots.filter { it !== decor }
            check(if (dialog) overlays.size == 1 && overlays.single().hasWindowFocus() else overlays.isEmpty() && decor.hasWindowFocus())
            check(decor.width > 0 && decor.height > 0 && !decor.isLayoutRequested)
            val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            try {
                // 软件绘制同一时刻的真实窗口栈；无缩放、裁剪、UI 替换或安全标志变更。
                val canvas = Canvas(bitmap)
                decor.draw(canvas)
                val origin = IntArray(2).also(decor::getLocationOnScreen)
                for (overlay in overlays) {
                    val position = IntArray(2).also(overlay::getLocationOnScreen)
                    canvas.save()
                    canvas.translate((position[0] - origin[0]).toFloat(), (position[1] - origin[1]).toFloat())
                    overlay.draw(canvas)
                    canvas.restore()
                }
                val colors = mutableSetOf<Int>()
                for (x in 0 until bitmap.width step 17) for (y in 0 until bitmap.height step 19) colors += bitmap.getPixel(x, y)
                check(colors.size > 6)
                val root = activity.cacheDir.canonicalFile
                val directory = File(root, "website-refresh-20261014").canonicalFile
                check(directory.parentFile == root)
                check(directory.isDirectory || directory.mkdir())
                File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
            check(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
    }

    private fun withLightLayout(block: () -> Unit) {
        val originalConfiguration = compose.runOnIdle { Configuration(compose.activity.resources.configuration) }
        val scale = shell("settings get system font_scale").trim()
        check(scale == "null" || scale.matches(Regex("[0-9]+(?:\\.[0-9]+)?")))
        val night = Regex("Night mode: (auto|yes|no|custom)").find(shell("cmd uimode night"))?.groupValues?.get(1)
            ?: error("Cannot record night mode.")
        try {
            shell("settings put system font_scale 1.0")
            shell("cmd uimode night no")
            awaitConfiguration(1f, Configuration.UI_MODE_NIGHT_NO)
            block()
        } finally {
            if (scale == "null") shell("settings delete system font_scale") else shell("settings put system font_scale $scale")
            shell("cmd uimode night $night")
            awaitConfiguration(originalConfiguration.fontScale, originalConfiguration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
            check(shell("settings get system font_scale").trim() == scale)
            check(Regex("Night mode: (auto|yes|no|custom)").find(shell("cmd uimode night"))?.groupValues?.get(1) == night)
        }
    }

    private fun awaitConfiguration(scale: Float, night: Int) {
        var stableSince: Long? = null
        compose.waitUntil(OPERATION_TIMEOUT) {
            val stable = compose.runOnIdle {
                val activity = compose.activity
                val config = activity.resources.configuration
                kotlin.math.abs(config.fontScale - scale) < 0.01f &&
                    config.uiMode and Configuration.UI_MODE_NIGHT_MASK == night &&
                    !activity.isChangingConfigurations && !activity.window.decorView.isLayoutRequested
            }
            if (!stable) { stableSince = null; false } else {
                val now = SystemClock.uptimeMillis()
                if (stableSince == null) stableSince = now
                now - checkNotNull(stableSince) >= 250
            }
        }
        compose.waitForIdle()
    }

    private fun input(tag: String, value: String) {
        reveal(hasTestTag(tag))
        compose.onNodeWithTag(tag).performTextReplacement(value)
    }

    private fun click(tag: String) = click(hasTestTag(tag))
    private fun click(matcher: SemanticsMatcher) { reveal(matcher); compose.onNode(matcher).performClick() }

    private fun reveal(matcher: SemanticsMatcher) {
        var previous: List<Any>? = null
        var stableSince = SystemClock.uptimeMillis()
        var diagnosed = false
        compose.waitUntil(OPERATION_TIMEOUT) {
            val node = compose.onAllNodes(matcher).fetchSemanticsNodes().singleOrNull()
            if (node == null) { previous = null; return@waitUntil false }
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
            if (!diagnosed) {
                // 只记录控件几何与窗口状态；不记录输入、账号或密码内容。
                android.util.Log.i("WebsiteScreenshotProbe", "Target geometry: bounds=$bounds position=$position size=$size window=$window displayed=$fullyVisible")
                diagnosed = true
            }
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
                previous = snapshot; stableSince = now; false
            } else now - stableSince >= 250
        }
        compose.onNode(matcher).assertIsDisplayed()
    }

    private fun waitFor(tag: String, requireEnabled: Boolean = true) = compose.waitUntil(OPERATION_TIMEOUT) {
        val nodes = compose.onAllNodesWithTag(tag).fetchSemanticsNodes()
        nodes.size == 1 && (!requireEnabled || !nodes.single().config.contains(SemanticsProperties.Disabled))
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

    private fun editableText(tag: String): String = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text
    private fun vaultFile(): File = File(compose.activity.noBackupFilesDir, "vault/vault.kdbx")
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private val entryRow = SemanticsMatcher("entry_row_<uuid>") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("entry_row_") == true
    }
    private data class TargetWindow(val visible: Rect, val imeBottom: Int, val imeVisible: Boolean,
        val width: Int, val height: Int, val layoutPending: Boolean, val focused: Boolean)
    private data class Fields(val title: String, val username: String, val password: String, val url: String, val notes: String)

    companion object {
        private const val OPERATION_TIMEOUT = 120_000L
        private const val MASTER = "Website-Synthetic-Master-20261014!"
        // 公开的虚构展示资料，不对应真实服务账户。密码仅用于内部完整夹具核验，永不显露或复制。
        private val FIXTURES = listOf(
            Fields("示例邮箱", "hello@example.com", "PublicMailFixture!2026", "https://mail.example.com", "日常通信，轻松查找。"),
            Fields("灵感笔记", "ideas@example.com", "PublicNotesFixture!2026", "https://notes.example.com", "留住每一个好想法。"),
            Fields("工作空间", "work@example.com", "PublicWorkspaceFixture!2026", "https://work.example.com", "专注工作，从容开始。"),
        )
    }
}
