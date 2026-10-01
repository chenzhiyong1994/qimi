package com.localpasswordmanager.app

import android.os.Bundle
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import com.localpasswordmanager.app.ui.VaultTheme

class MainActivity : ComponentActivity() {
    private val vault: VaultViewModel by viewModels()
    private lateinit var clipboard: SensitiveClipboard

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.decorView.importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        clipboard = SensitiveClipboard(this)
        enableEdgeToEdge()
        setContent {
            VaultTheme {
                InterceptPlatformTextInput(interceptor = { request, nextHandler ->
                    nextHandler.startInputMethod(PlatformTextInputMethodRequest { attributes ->
                        request.createInputConnection(attributes).also {
                            attributes.imeOptions = attributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                        }
                    })
                }) {
                    VaultScreen(vault, clipboard, onBack = { onBackPressedDispatcher.onBackPressed() })
                }
            }
        }
    }

    override fun onResume() { super.onResume(); clipboard.resumed() }
    override fun onPause() { vault.lock(); clipboard.paused(); super.onPause() }
    override fun onUserInteraction() { vault.touch(); super.onUserInteraction() }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::clipboard.isInitialized) clipboard.resumed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Compose text widgets can save their own state. Sensitive UI state must never reach disk.
        outState.clear()
    }
}
