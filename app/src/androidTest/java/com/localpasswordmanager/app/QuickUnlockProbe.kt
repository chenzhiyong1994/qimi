package com.localpasswordmanager.app

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** 单独执行的只读探针，保留模拟器当前合成库，不进入会重建夹具的 S1 套件。 */
class QuickUnlockProbe {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(DedicatedSyntheticDeviceRule()).around(compose)

    @Test
    fun oneTapUnlockAndLockAgainPreservesExistingVault() {
        val file = File(compose.activity.noBackupFilesDir, "vault/vault.kdbx")
        assertTrue("A vault using the synthetic fixture password must already exist.", file.isFile)
        val originalFile = file.readBytes()

        // 不输入主密码；按钮必须走真实 KDBX 解锁，而不是跳过认证显示列表。
        unlockWithoutTyping()
        compose.onNodeWithTag("lock_vault").performClick()
        compose.onNodeWithTag("master_password").assertExists()
        compose.onNodeWithTag("new_entry").assertDoesNotExist()
        unlockWithoutTyping()
        assertArrayEquals("Quick unlock must not recreate, change the password, or rewrite the vault.",
            originalFile, file.readBytes())
    }

    private fun unlockWithoutTyping() {
        compose.onNodeWithTag("unlock_synthetic_vault").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(120_000) {
            compose.onAllNodesWithTag("new_entry").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
