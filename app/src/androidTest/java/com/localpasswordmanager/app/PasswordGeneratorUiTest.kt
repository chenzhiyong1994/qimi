package com.localpasswordmanager.app

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Bundle
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.view.inspector.WindowInspector
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import local.passwordmanager.vault.VaultRepository
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** 仅用于显式授权的专用合成设备；重建空库，绝不对用户库运行。 */
@RunWith(AndroidJUnit4::class)
class PasswordGeneratorUiTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(DedicatedSyntheticDeviceRule(resetVault = true)).around(compose)

    @Test
    fun defaultsAndPresetsProduceMatchingHiddenCandidates() {
        createVaultAndOpenGenerator()
        assertDefaults()
        captureSyntheticSheet("normal-default")
        assertCandidate(readCandidate(), 20, lowercase = true, uppercase = true, digits = true, symbols = false)
        // 驱动真实候选的 LaunchedEffect 定时器；只隐藏显示，候选仍须可用。
        compose.mainClock.advanceTimeBy(10_001)
        compose.waitForIdle()
        assertHiddenCandidate()
        compose.onNodeWithTag("generator_use").assertIsEnabled()

        click("preset_SIMPLE")
        assertCandidateInvalidated()
        click("generator_regenerate")
        assertHiddenCandidate()
        assertCandidate(readCandidate(), 12, lowercase = true, uppercase = false, digits = true, symbols = false)

        click("preset_COMPLEX")
        assertCandidateInvalidated()
        click("generator_regenerate")
        assertHiddenCandidate()
        assertCandidate(readCandidate(), 24, lowercase = true, uppercase = true, digits = true, symbols = true)
        assertSecureWindows()
    }

    @Test
    fun advancedOverridesPresetsAndRejectsInvalidLengthInLargeDarkLayout() {
        withLargeDarkLayout {
            createVaultAndOpenGenerator()
            click("preset_COMPLEX")
            click("generator_advanced")
            compose.onNodeWithTag("generator_advanced").assertIsOn()
            for (preset in listOf("SIMPLE", "NORMAL", "COMPLEX")) {
                compose.onNodeWithTag("preset_$preset").assertIsNotEnabled()
            }
            compose.onNodeWithTag("custom_DEFAULT").assertIsSelected()
            for (type in listOf("LOWERCASE", "UPPERCASE", "DIGITS", "SYMBOLS")) {
                compose.onNodeWithTag("type_$type").assertIsOff()
            }
            assertCandidateInvalidated()
            click("generator_regenerate")
            // 高级选项全部留空时使用普通规则，不能继承外层复杂预设。
            assertCandidate(readCandidate(), 20, lowercase = true, uppercase = true, digits = true, symbols = false)

            click("custom_SIMPLE")
            click("generator_regenerate")
            assertCandidate(readCandidate(), 12, lowercase = true, uppercase = false, digits = true, symbols = false)
            input("generator_length", "32")
            click("type_UPPERCASE")
            click("type_SYMBOLS")
            compose.onNodeWithTag("type_UPPERCASE").assertIsOn()
            compose.onNodeWithTag("type_SYMBOLS").assertIsOn()
            assertCandidateInvalidated()
            click("generator_regenerate")
            assertCandidate(readCandidate(), 32, lowercase = false, uppercase = true, digits = false, symbols = true)
            // 重新生成明确回到隐藏态，避免和自动隐藏定时器同时 toggle。
            click("generator_regenerate")
            reveal("generator_length")
            captureSyntheticSheet("dark-large-advanced")

            input("generator_length", "7")
            compose.onNodeWithText("密码长度须为 8–128 位").assertExists()
            assertCandidateInvalidated()
            compose.onNodeWithTag("generator_regenerate").assertIsNotEnabled()
            input("generator_length", "129")
            compose.onNodeWithTag("generator_regenerate").assertIsNotEnabled()
            input("generator_length", "abc")
            compose.onNodeWithText("请输入 8–128 的整数长度").assertExists()
            compose.onNodeWithTag("generator_regenerate").assertIsNotEnabled()
            hideKeyboard()

            // 关闭高级后必须忽略保留在自定义控件中的非法长度及字符选择。
            click("generator_advanced")
            compose.onNodeWithTag("generator_advanced").assertIsOff()
            compose.onNodeWithTag("preset_COMPLEX").assertIsEnabled().assertIsSelected()
            compose.onNodeWithTag("generator_length").assertDoesNotExist()
            compose.onNodeWithTag("generator_regenerate").assertIsEnabled()
            assertCandidateInvalidated()
            click("generator_regenerate")
            assertCandidate(readCandidate(), 24, lowercase = true, uppercase = true, digits = true, symbols = true)
        }
    }

    @Test
    fun dismissPreservesOriginalAndUsePersistsExactGeneratedPassword() {
        createVault()
        val originalFile = vaultFile().readBytes()
        click("new_entry")
        input("entry_title", SYNTHETIC_TITLE)
        input("entry_password", ORIGINAL_PASSWORD)
        hideKeyboard()
        // Compose 1.9 的 EditableText 是视觉变换后的文本；先在打开面板前建立同字段基线。
        assertHiddenFormPassword(ORIGINAL_PASSWORD.length)
        assertFormPassword(ORIGINAL_PASSWORD, "Baseline form input must preserve its original value.")
        click("open_password_generator")
        waitFor("generator_use")
        readCandidate()
        click("generator_close")
        assertOriginalPassword()

        click("open_password_generator")
        waitFor("generator_use")
        readCandidate()
        shell("input keyevent KEYCODE_BACK")
        compose.waitUntil(OPERATION_TIMEOUT) { compose.onAllNodesWithTag("generator_sheet").fetchSemanticsNodes().isEmpty() }
        assertOriginalPassword()
        compose.onNodeWithText("这些输入尚未保存").assertDoesNotExist()
        assertArrayEquals("Dismissing generated candidates must not write the vault.", originalFile, vaultFile().readBytes())

        click("open_password_generator")
        waitFor("generator_use")
        val candidate = readCandidate()
        click("generator_use")
        compose.onNodeWithTag("generator_sheet").assertDoesNotExist()
        assertFormPassword(candidate, "Use must replace the form password exactly.")
        assertArrayEquals("Using a candidate is not saving the entry.", originalFile, vaultFile().readBytes())
        click("save_entry")
        waitFor("show_password")
        compose.onNodeWithContentDescription("密码已隐藏").assertExists()
        click("show_password")
        assertTrue("Saved detail must preserve the generated value.", text("detail_password_text") == candidate)
        val master = S1UiTest.MASTER.toCharArray()
        try {
            VaultRepository(vaultFile()).unlock(master).use { session ->
                val entries = session.listEntries()
                assertEquals(1, entries.size)
                assertEquals(SYNTHETIC_TITLE, entries.single().title)
                assertTrue("Reopening KDBX must preserve the generated value.", entries.single().password == candidate)
            }
        } finally { master.fill('\u0000') }
    }

    @Test
    fun lockBackgroundAndRecreationDiscardCandidatesAndNeverSaveThemInBundle() {
        createVaultAndOpenGenerator()
        val originalFile = vaultFile().readBytes()
        val lockedCandidate = makeCustomCandidate()
        // 模态层遮住工具栏；直接触发同一会话锁定入口，核验面板立即随授权失效。
        compose.runOnIdle { ViewModelProvider(compose.activity)[VaultViewModel::class.java].lock() }
        assertLockedWithoutCandidate(lockedCandidate)
        reopenFreshGenerator()

        val backgroundCandidate = makeCustomCandidate()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertLockedWithoutCandidate(backgroundCandidate)
        reopenFreshGenerator()

        val recreatedCandidate = makeCustomCandidate()
        val savedState = recreateAndCaptureSavedState()
        assertBundleExcludes(savedState, listOf(S1UiTest.MASTER, lockedCandidate, backgroundCandidate, recreatedCandidate))
        assertLockedWithoutCandidate(recreatedCandidate)
        reopenFreshGenerator()
        assertArrayEquals("Generating or discarding must not persist candidates.", originalFile, vaultFile().readBytes())
    }

    private fun createVault() {
        waitFor("create_vault", requireEnabled = false)
        input("master_password", S1UiTest.MASTER)
        input("master_confirm", S1UiTest.MASTER)
        hideKeyboard()
        click("create_vault")
        waitFor("new_entry")
    }

    private fun createVaultAndOpenGenerator() {
        createVault()
        click("new_entry")
        click("open_password_generator")
        waitFor("generator_use")
    }

    private fun reopenFreshGenerator() {
        click("unlock_synthetic_vault")
        waitFor("new_entry")
        click("new_entry")
        assertTrue("Discarded form must not be restored.", editableText("entry_password").isEmpty())
        click("open_password_generator")
        waitFor("generator_use")
        assertDefaults()
        // 之前的候选有 37 位；默认 20 位证明没有恢复旧候选或自定义规则。
        assertCandidate(readCandidate(), 20, lowercase = true, uppercase = true, digits = true, symbols = false)
    }

    private fun makeCustomCandidate(): String {
        click("generator_advanced")
        input("generator_length", "37")
        hideKeyboard()
        click("generator_regenerate")
        return readCandidate().also {
            assertCandidate(it, 37, lowercase = true, uppercase = true, digits = true, symbols = false)
        }
    }

    private fun assertDefaults() {
        compose.onNodeWithTag("preset_NORMAL").assertIsSelected().assertIsEnabled()
        compose.onNodeWithTag("generator_advanced").assertIsOff()
        compose.onNodeWithTag("generator_length").assertDoesNotExist()
        assertHiddenCandidate()
        compose.onNodeWithTag("generator_use").assertIsEnabled()
    }

    private fun assertHiddenCandidate() {
        compose.onNodeWithContentDescription("生成的密码已隐藏").assertExists()
        val value = compose.onNodeWithTag("generator_candidate").fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text)
        assertTrue("Hidden candidate must not expose raw text to accessibility.", value.isNullOrEmpty())
    }

    private fun assertCandidateInvalidated() {
        compose.onNodeWithTag("generator_candidate").assertDoesNotExist()
        compose.onNodeWithTag("generator_use").assertIsNotEnabled()
        compose.onNodeWithTag("generator_reveal").assertIsNotEnabled()
    }

    private fun readCandidate(): String {
        click("generator_reveal")
        return text("generator_candidate")
    }

    private fun assertCandidate(value: String, length: Int, lowercase: Boolean, uppercase: Boolean, digits: Boolean, symbols: Boolean) {
        // 断言只输出规则或布尔值，失败报告也不打印合成生成密码。
        assertEquals("Generated length", length, value.length)
        val groups = listOf(
            lowercase to { c: Char -> c in 'a'..'z' },
            uppercase to { c: Char -> c in 'A'..'Z' },
            digits to { c: Char -> c in '0'..'9' },
            symbols to { c: Char -> c.code in 33..126 && !c.isLetterOrDigit() },
        )
        for ((selected, matches) in groups) assertEquals("Presence of character class", selected, value.any(matches))
        assertTrue("Every character must belong to a selected class.", value.all { c -> groups.any { (selected, matches) -> selected && matches(c) } })
    }

    private fun assertOriginalPassword() {
        compose.onNodeWithTag("generator_sheet").assertDoesNotExist()
        assertFormPassword(ORIGINAL_PASSWORD, "Dismiss must preserve original form password.")
    }

    private fun assertFormPassword(expected: String, message: String) {
        reveal("entry_password")
        assertHiddenFormPassword(expected.length)
        val showPassword = hasContentDescription("显示密码")
        reveal(showPassword)
        compose.onNode(showPassword).performClick()
        try {
            // 使用用户的显示按钮后比较原值，绝不把遮蔽字符当成实际表单密码。
            assertTrue(message, editableText("entry_password") == expected)
        } finally {
            val hidePassword = hasContentDescription("隐藏密码")
            if (compose.onAllNodes(hidePassword).fetchSemanticsNodes().isNotEmpty()) {
                reveal(hidePassword)
                compose.onNode(hidePassword).performClick()
            }
        }
        assertHiddenFormPassword(expected.length)
    }

    private fun assertHiddenFormPassword(expectedLength: Int) {
        val displayed = editableText("entry_password")
        assertEquals("Hidden form password retains its length.", expectedLength, displayed.length)
        assertTrue("Hidden EditableText must contain only the visual mask, not the raw password.",
            displayed.isNotEmpty() && displayed.all { it == '\u2022' })
    }

    private fun assertLockedWithoutCandidate(candidate: String) {
        waitFor("unlock_synthetic_vault")
        compose.onNodeWithTag("generator_sheet").assertDoesNotExist()
        compose.onNodeWithTag("entry_password").assertDoesNotExist()
        assertTrue("Locked UI must not expose the former candidate.",
            compose.onAllNodesWithText(candidate, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        compose.runOnIdle { assertEquals(Page.AUTH, ViewModelProvider(compose.activity)[VaultViewModel::class.java].ui.page) }
        assertSecureWindows()
    }

    private fun text(tag: String): String = compose.onNodeWithTag(tag).fetchSemanticsNode()
        .config[SemanticsProperties.Text].joinToString("") { it.text }

    private fun editableText(tag: String): String = compose.onNodeWithTag(tag).fetchSemanticsNode()
        .config[SemanticsProperties.EditableText].text

    private fun input(tag: String, value: String) {
        reveal(tag)
        compose.onNodeWithTag(tag).performTextReplacement(value)
    }

    private fun click(tag: String) {
        reveal(tag)
        compose.onNodeWithTag(tag).performClick()
    }

    private fun reveal(tag: String) = reveal(hasTestTag(tag))

    private fun reveal(matcher: SemanticsMatcher) {
        var previous: List<Any>? = null
        var stableSince = SystemClock.uptimeMillis()
        // Compose 空闲不代表原生 IME/窗口动画已结束；等待真实可见区域与布局稳定后再触摸。
        compose.waitUntil(OPERATION_TIMEOUT) {
            val semantics = compose.onAllNodes(matcher).fetchSemanticsNodes().singleOrNull()
            if (semantics == null) {
                previous = null
                return@waitUntil false
            }
            val bounds = semantics.boundsInRoot
            val position = semantics.positionOnScreen
            val size = semantics.size
            val window = compose.runOnIdle {
                val decor = WindowInspector.getGlobalWindowViews().lastOrNull { it.isShown && it.hasWindowFocus() }
                    ?: compose.activity.window.decorView
                val insets = ViewCompat.getRootWindowInsets(decor)
                TargetWindow(
                    Rect().also(decor::getWindowVisibleDisplayFrame),
                    insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0,
                    insets?.isVisible(WindowInsetsCompat.Type.ime()) == true,
                    decor.width, decor.height, decor.isLayoutRequested, decor.hasWindowFocus(),
                )
            }
            val fullyVisible = compose.onNode(matcher).isDisplayed() &&
                bounds.width >= size.width - 1f && bounds.height >= size.height - 1f &&
                position.x >= window.visible.left - 1f && position.y >= window.visible.top - 1f &&
                position.x + size.width <= window.visible.right + 1f &&
                position.y + size.height <= window.visible.bottom + 1f
            if (!fullyVisible) {
                previous = null
                // 固定底部操作没有滚动祖先；其可见性要等待窗口布局，不能伪造滚动或点击。
                if (generateSequence(semantics.parent) { it.parent }.any { it.config.contains(SemanticsActions.ScrollBy) }) {
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

    private data class TargetWindow(val visible: Rect, val imeBottom: Int, val imeVisible: Boolean,
        val width: Int, val height: Int, val layoutPending: Boolean, val focused: Boolean)

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

    private fun assertSecureWindows() {
        compose.runOnIdle {
            assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            for (root in WindowInspector.getGlobalWindowViews().filter { it.isShown }) {
                val attributes = root.layoutParams as? WindowManager.LayoutParams ?: continue
                assertTrue("Every visible application window, including the generator dialog, must be secure.",
                    attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
        }
    }

    private fun captureSyntheticSheet(name: String) {
        assertHiddenCandidate()
        val master = S1UiTest.MASTER.toCharArray()
        try {
            VaultRepository(vaultFile()).unlock(master).use { session ->
                check(session.listEntries().isEmpty()) { "Screenshots require a freshly created empty synthetic vault." }
            }
        } finally { master.fill('\u0000') }
        compose.runOnIdle { assertEquals(Page.ADD, ViewModelProvider(compose.activity)[VaultViewModel::class.java].ui.page) }
        for (tag in listOf("entry_title", "entry_username", "entry_password", "entry_url", "entry_notes")) {
            val nodes = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
            check(nodes.all { it.config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty().isEmpty() }) {
                "Screenshots require an empty synthetic form."
            }
        }
        hideKeyboard()
        compose.onNodeWithTag("generator_use").assertIsDisplayed()
        compose.onNodeWithTag("generator_regenerate").assertIsDisplayed()
        assertSecureWindows()
        compose.runOnIdle {
            val activity = compose.activity
            val roots = WindowInspector.getGlobalWindowViews().filter {
                it !== activity.window.decorView && it.isShown && it.hasWindowFocus()
            }
            check(roots.size == 1) { "Expected only the focused generator dialog for capture." }
            val view: View = roots.single()
            check(view.width > 0 && view.height > 0)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                // 仅软件绘制本进程的合成资料 Dialog；保持所有窗口 FLAG_SECURE。
                view.draw(Canvas(bitmap))
                val colors = mutableSetOf<Int>()
                for (x in 0 until bitmap.width step 17) for (y in 0 until bitmap.height step 19) colors += bitmap.getPixel(x, y)
                check(colors.size > 6) { "Dialog capture did not contain a rendered UI." }
                val root = activity.cacheDir.canonicalFile
                val directory = File(root, "password-generator-ui").canonicalFile
                check(directory.parentFile == root)
                check(directory.isDirectory || directory.mkdir())
                File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
        }
        assertSecureWindows()
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
            // shell 返回只表示设置写入；等待 Activity 完成配置恢复，避免影响下一个测试。
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

    private fun recreateAndCaptureSavedState(): Bundle {
        val activity = compose.activity
        var savedState: Bundle? = null
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPostSaveInstanceState(target: Activity, outState: Bundle) {
                if (target === activity) savedState = Bundle(outState)
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        compose.runOnIdle { activity.application.registerActivityLifecycleCallbacks(callbacks) }
        try { compose.activityRule.scenario.recreate() }
        finally { compose.runOnIdle { activity.application.unregisterActivityLifecycleCallbacks(callbacks) } }
        return checkNotNull(savedState) { "Recreation did not yield a saved-state Bundle." }
    }

    private fun assertBundleExcludes(bundle: Bundle, values: List<String>) {
        val parcel = Parcel.obtain()
        val bytes = try { parcel.writeBundle(bundle); parcel.marshall() } finally { parcel.recycle() }
        for (value in values) for (encoding in listOf(Charsets.UTF_8, Charsets.UTF_16LE)) {
            val secret = value.toByteArray(encoding)
            assertFalse("Generated passwords must not enter saved state.", bytes.indices.any { index ->
                index + secret.size <= bytes.size && secret.indices.all { bytes[index + it] == secret[it] }
            })
        }
    }

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    private fun vaultFile(): File = File(compose.activity.noBackupFilesDir, "vault/vault.kdbx")

    companion object {
        private const val OPERATION_TIMEOUT = 120_000L
        private const val SYNTHETIC_TITLE = "Synthetic generated account 合成资料"
        private const val ORIGINAL_PASSWORD = "  OriginalSynthetic!合成-2026  "
    }
}
