package com.localpasswordmanager.app

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * 独立进程验证入口：先运行 S1 保存用例，再 force-stop 本 debug app，然后单独运行本类。
 * 没有合成库则失败；本类不清理、不写入密码库，也不进入普通 S1 套件。
 */
@RunWith(AndroidJUnit4::class)
class ProcessRestartProbe {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(DedicatedSyntheticDeviceRule()).around(compose)

    @Test
    fun opensPersistedVaultInANewProcess() {
        val file = File(compose.activity.noBackupFilesDir, "vault/vault.kdbx")
        assertTrue("Run the synthetic saved-fields fixture before this independent probe.", file.isFile)
        val originalFile = file.readBytes()
        compose.onNodeWithTag("unlock_vault").assertExists()
        compose.onNodeWithTag("master_password").performScrollTo().performTextReplacement(S1UiTest.MASTER)
        compose.onNodeWithTag("unlock_vault").performScrollTo().performClick()
        compose.waitUntil(120_000) {
            compose.onAllNodesWithTag("new_entry").fetchSemanticsNodes().isNotEmpty()
        }
        val row = SemanticsMatcher("persisted entry row") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("entry_row_") == true
        }
        compose.onNode(row).performScrollTo().performClick()
        compose.onNodeWithContentDescription("密码已隐藏").assertExists()
        compose.onNodeWithTag("detail_username_text").assertTextEquals(S1UiTest.USERNAME)
        compose.onNodeWithTag("show_password").performScrollTo().performClick()
        compose.onNodeWithTag("detail_password_text").assertTextEquals(S1UiTest.RAW_ENTRY_FIXTURE)
        compose.onNodeWithTag("back").performScrollTo().performClick()
        compose.onNodeWithTag("search").performScrollTo().performTextReplacement(S1UiTest.TITLE)
        compose.onNode(row).assertExists()
        assertArrayEquals("Read-only re-opening must preserve the synthetic vault file.", originalFile, file.readBytes())
    }
}
