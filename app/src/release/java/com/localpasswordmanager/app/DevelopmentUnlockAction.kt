package com.localpasswordmanager.app

import androidx.compose.runtime.Composable

/** Release 没有测试解锁控件或合成口令。 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun DevelopmentUnlockAction(state: VaultUi, authenticate: (String, String) -> Unit) = Unit
