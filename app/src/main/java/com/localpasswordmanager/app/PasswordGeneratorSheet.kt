package com.localpasswordmanager.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy
import com.localpasswordmanager.app.ui.VaultIcon
import com.localpasswordmanager.app.ui.VaultIconKind
import kotlinx.coroutines.delay

/** 候选与选项仅留在当前表单的内存中，不进入 saved state、剪贴板或磁盘。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PasswordGeneratorSheet(onInteraction: () -> Unit, onDismiss: () -> Unit, onUse: (String) -> Unit) {
    var options by remember { mutableStateOf(PasswordGeneratorOptions()) }
    var candidate by remember { mutableStateOf<String?>(null) }
    var revealed by remember { mutableStateOf(false) }
    var generationError by remember { mutableStateOf(false) }
    val generator = remember { PasswordGenerator() }
    val focus = LocalFocusManager.current
    val rulesResult = remember(options) { runCatching { options.resolve() } }
    val rules = rulesResult.getOrNull()

    fun change(value: PasswordGeneratorOptions) {
        onInteraction()
        options = value
        candidate = null
        revealed = false
        generationError = false
    }
    fun regenerate() {
        onInteraction()
        focus.clearFocus()
        revealed = false
        candidate = null
        generationError = false
        if (rules != null) {
            try { candidate = generator.generate(rules) }
            catch (_: Exception) { generationError = true }
        }
    }
    LaunchedEffect(Unit) { regenerate() }
    LaunchedEffect(revealed, candidate) {
        if (revealed) { delay(10_000); revealed = false }
    }

    ModalBottomSheet(
        onDismissRequest = { onInteraction(); onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        properties = ModalBottomSheetProperties(securePolicy = SecureFlagPolicy.SecureOn),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f).imePadding().testTag("generator_sheet")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("生成密码", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { onInteraction(); onDismiss() }, modifier = Modifier.testTag("generator_close")) {
                    VaultIcon(VaultIconKind.Close, "关闭密码生成器")
                }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("密码候选", style = MaterialTheme.typography.labelLarge)
                        when {
                            candidate == null -> Text("设置好规则后，点击重新生成。", style = MaterialTheme.typography.bodyMedium)
                            revealed -> Text(candidate!!, fontFamily = FontFamily.Monospace,
                                modifier = Modifier.testTag("generator_candidate"), style = MaterialTheme.typography.titleMedium)
                            else -> Text("••••••••", style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.testTag("generator_candidate").clearAndSetSemantics {
                                    contentDescription = "生成的密码已隐藏"
                                })
                        }
                        TextButton(onClick = { onInteraction(); revealed = !revealed }, enabled = candidate != null,
                            modifier = Modifier.testTag("generator_reveal")) {
                            VaultIcon(if (revealed) VaultIconKind.EyeOff else VaultIconKind.Eye, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (revealed) "隐藏密码" else "临时显示密码")
                        }
                    }
                }
                Text("生成复杂度", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PasswordComplexity.entries.forEach { complexity ->
                        FilterChip(selected = options.complexity == complexity, enabled = !options.advanced,
                            onClick = { change(options.copy(complexity = complexity)) },
                            label = { Text(complexity.label()) }, modifier = Modifier.testTag("preset_$complexity"))
                    }
                }
                Text(if (options.advanced) "已启用高级自定义，上方复杂度暂不可选。" else "简单 12 位 · 普通 20 位 · 复杂 24 位",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .toggleable(options.advanced, role = Role.Switch) { change(options.copy(advanced = it)) }
                        .testTag("generator_advanced"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("高级自定义", style = MaterialTheme.typography.titleSmall)
                        Text("各项选填，未设置时使用默认规则。", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = options.advanced, onCheckedChange = null)
                }
                if (options.advanced) {
                    Text("复杂程度（选填）", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = options.customComplexity == null,
                            onClick = { change(options.copy(customComplexity = null)) },
                            label = { Text("默认（普通）") }, modifier = Modifier.testTag("custom_DEFAULT"))
                        PasswordComplexity.entries.forEach { complexity ->
                            FilterChip(selected = options.customComplexity == complexity,
                                onClick = { change(options.copy(customComplexity = complexity)) },
                                label = { Text(complexity.label()) }, modifier = Modifier.testTag("custom_$complexity"))
                        }
                    }
                    OutlinedTextField(
                        value = options.customLength, onValueChange = { change(options.copy(customLength = it)) },
                        label = { Text("密码长度（选填）") },
                        supportingText = { Text(rulesResult.exceptionOrNull()?.message ?: "8–128 位；留空使用复杂程度的默认长度。") },
                        isError = rulesResult.isFailure, singleLine = true,
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("generator_length"), shape = MaterialTheme.shapes.medium,
                    )
                    Text("字符类型（多选，选填）", style = MaterialTheme.typography.titleSmall)
                    Column {
                        PasswordCharacterType.entries.forEach { type ->
                            val checked = type in options.customTypes
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .toggleable(checked, role = Role.Checkbox) { selected ->
                                    change(options.copy(customTypes = if (selected) options.customTypes + type else options.customTypes - type))
                                }.testTag("type_$type"), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                Text(type.label(), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Text("均未选择时，沿用复杂程度的字符类型；选择后，每种类型至少出现一次。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                rules?.let {
                    Text("本次规则：${it.length} 位 · ${it.types.joinToString("、") { type -> type.label().substringBefore("（") }}",
                        modifier = Modifier.testTag("generator_rules"), style = MaterialTheme.typography.bodyMedium)
                }
                if (generationError) Text("暂时无法生成密码，请重试。", color = MaterialTheme.colorScheme.error)
                Text("使用后填入表单，仍需保存账号。只更新本地记录，不会修改网站或 App 的密码。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { regenerate() }, enabled = rules != null,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("generator_regenerate")) { Text("重新生成") }
                Button(onClick = { candidate?.let { onInteraction(); focus.clearFocus(); onUse(it) } },
                    enabled = candidate != null && rules != null,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("generator_use")) { Text("使用此密码") }
            }
        }
    }
}

private fun PasswordComplexity.label() = when (this) {
    PasswordComplexity.SIMPLE -> "简单"
    PasswordComplexity.NORMAL -> "普通"
    PasswordComplexity.COMPLEX -> "复杂"
}

private fun PasswordCharacterType.label() = when (this) {
    PasswordCharacterType.LOWERCASE -> "小写字母（a–z）"
    PasswordCharacterType.UPPERCASE -> "大写字母（A–Z）"
    PasswordCharacterType.DIGITS -> "数字（0–9）"
    PasswordCharacterType.SYMBOLS -> "符号（如 ! @ #）"
}
