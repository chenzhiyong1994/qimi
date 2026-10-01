package com.localpasswordmanager.app

import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** 仅编入 debug：使用公开的合成测试口令，仍经过正常认证与会话边界。 */
@Composable
internal fun DevelopmentUnlockAction(state: VaultUi, authenticate: (String, String) -> Unit) {
    val emulator = Build.HARDWARE in setOf("ranchu", "goldfish") &&
        (Build.PRODUCT.startsWith("sdk_gphone") || Build.PRODUCT.startsWith("sdk_phone") ||
            Build.FINGERPRINT.startsWith("generic"))
    if (!state.exists || !emulator) return

    OutlinedButton(
        onClick = { authenticate("  Synthetic-Master-合成-2026  ", "") },
        enabled = !state.busy && state.waitSeconds == 0,
        modifier = Modifier.fillMaxWidth().testTag("unlock_synthetic_vault"),
    ) {
        Text("一键解锁测试库")
    }
}
