package com.localpasswordmanager.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localpasswordmanager.app.ui.VaultIcon
import com.localpasswordmanager.app.ui.VaultIconKind
import kotlinx.coroutines.delay
import local.passwordmanager.vault.EntryInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VaultScreen(vault: VaultViewModel, clipboard: SensitiveClipboard, onBack: () -> Unit) {
    val state = vault.ui
    // 列表位置仅在当前授权代次的内存保留；锁定立即销毁，不做页面离场动画。
    val listScroll = remember(state.epoch) { ScrollState(0) }
    val pageScroll = remember(state.epoch, state.page) { ScrollState(0) }
    val activeScroll = if (state.page == Page.LIST) listScroll else pageScroll
    LaunchedEffect(state.error) { if (state.error != null) activeScroll.animateScrollTo(0) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (state.page == Page.AUTH || state.page == Page.LIST) {
                            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                                Icon(painterResource(R.drawable.brand_mark), null, Modifier.padding(9.dp).size(24.dp), MaterialTheme.colorScheme.primary)
                            }
                        }
                        Text(if (state.page == Page.HELP) "使用帮助" else stringResource(R.string.brand_name), style = MaterialTheme.typography.titleMedium)
                    }
                },
                navigationIcon = {
                    if (state.page !in setOf(Page.AUTH, Page.LIST)) {
                        IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "返回上一页" }) {
                            VaultIcon(VaultIconKind.ArrowLeft, null)
                        }
                    }
                },
                actions = {
                    if (state.page != Page.AUTH && state.page != Page.HELP) {
                        TextButton(onClick = { clipboard.clearIfOwned(); vault.lock() }, modifier = Modifier.testTag("lock_vault")) {
                            VaultIcon(VaultIconKind.Lock, null, Modifier.size(17.dp), MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text("锁定")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            key(state.epoch, state.page) {
                Column(
                    Modifier.align(Alignment.TopCenter).widthIn(max = 600.dp).fillMaxSize().imePadding()
                        .verticalScroll(activeScroll)
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    state.error?.let { Feedback(it, error = true) }
                    state.message?.let { Feedback(it, error = false) }
                    when (state.page) {
                        Page.AUTH -> AuthForm(state, vault)
                        Page.LIST -> VaultList(state, vault)
                        Page.ADD -> EntryForm(state, vault)
                        Page.EDIT -> key(state.selected?.id, state.selected?.version) { EntryForm(state, vault) }
                        Page.DETAIL -> EntryDetail(state, vault, clipboard)
                        Page.HELP -> Help(vault)
                    }
                    if (state.busy) {
                        Panel {
                            LinearProgressIndicator(Modifier.fillMaxWidth().testTag("operation_busy"))
                            val progress = if (state.page == Page.AUTH) {
                                if (state.exists) "正在解锁密码库…" else "正在创建并验证密码库…"
                            } else "正在保存并核对记录…"
                            Text(progress, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun AuthForm(state: VaultUi, vault: VaultViewModel) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val authenticate: (String, String) -> Unit = { value, confirmed -> focus.clearFocus(); vault.authenticate(value, confirmed) }
    LaunchedEffect(visible) { if (visible) { delay(10_000); visible = false } }

    Surface(shape = MaterialTheme.shapes.extraLarge, color = Color(0xFF244D3C)) {
        Column(
            Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF244D3C), Color(0xFF346B51))))
                .padding(26.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("只在本机 · 离线保管", color = Color(0xFFD5E5D4), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Surface(color = Color.White.copy(alpha = 0.12f), shape = MaterialTheme.shapes.medium) {
                    VaultIcon(VaultIconKind.Shield, null, Modifier.padding(12.dp).size(28.dp), Color(0xFFE9EDD7))
                }
            }
            Text(stringResource(R.string.brand_slogan),
                color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Text("无需注册。账号和密码加密保存在此设备。", color = Color(0xFFD5E5D4), style = MaterialTheme.typography.bodyMedium)
        }
    }

    Panel {
        Text(if (state.exists) "解锁密码库" else "创建你的密码库", style = MaterialTheme.typography.titleLarge)
        Text(if (state.exists) "输入主密码，继续使用你的账号。" else "设置 8–128 个字符的主密码，空格和换行会保留。",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Field(password, { password = it }, "主密码", "master_password", secret = true, revealed = visible,
            enabled = !state.busy, leading = VaultIconKind.Key, onReveal = { visible = !visible },
            revealDescription = if (visible) "隐藏主密码" else "临时显示主密码",
            imeAction = if (state.exists) ImeAction.Done else ImeAction.Next,
            onDone = { authenticate(password, confirmation) })
        if (!state.exists) {
            Field(confirmation, { confirmation = it }, "再次输入主密码", "master_confirm", secret = true,
                revealed = visible, enabled = !state.busy, leading = VaultIconKind.Key,
                imeAction = ImeAction.Done, onDone = { authenticate(password, confirmation) })
            Text("请记住主密码。忘记且没有其他可用解锁方式时，密码库无法恢复。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PrimaryAction(
            label = when {
                state.busy -> if (state.exists) "正在解锁…" else "正在创建…"
                state.waitSeconds > 0 -> "请等待 ${state.waitSeconds} 秒"
                state.exists -> "解锁密码库"
                else -> "创建密码库"
            },
            icon = VaultIconKind.Lock, tag = if (state.exists) "unlock_vault" else "create_vault",
            enabled = !state.busy && state.waitSeconds == 0,
            loading = state.busy,
            onClick = { authenticate(password, confirmation) },
        )
        DevelopmentUnlockAction(state, authenticate)
    }
    TextButton(onClick = vault::help, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("open_help")) {
        VaultIcon(VaultIconKind.Info, null, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text("主密码与本地存储帮助")
    }
}

@Composable
private fun VaultList(state: VaultUi, vault: VaultViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("你的密码库", style = MaterialTheme.typography.headlineLarge)
        Text("需要的账号，随手就能找到。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
    Field(state.query, vault::search, "搜索名称、账号或网址域名", "search", leading = VaultIconKind.Search,
        onClear = if (state.query.isNotEmpty()) ({ vault.search("") }) else null)
    PrimaryAction("新增账号", VaultIconKind.Add, "new_entry", onClick = vault::add)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(if (state.query.isEmpty()) "全部账号" else "搜索结果", style = MaterialTheme.typography.titleSmall)
        Text("${state.entries.size} 个", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (state.entries.isEmpty()) {
        Panel {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    VaultIcon(if (state.query.isEmpty()) VaultIconKind.Key else VaultIconKind.Search, null,
                        Modifier.padding(20.dp).size(32.dp), MaterialTheme.colorScheme.primary)
                }
            }
            Text(if (state.query.isEmpty()) "从第一个账号开始" else "没有找到匹配的账号", style = MaterialTheme.typography.titleMedium)
            Text(if (state.query.isEmpty()) "把重要的登录信息收在一起，需要时快速取用。" else "试试其他名称、账号或域名。",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.query.isNotEmpty()) TextButton(onClick = { vault.search("") }) { Text("清除搜索") }
        }
    }
    state.entries.forEach { entry ->
        Surface(onClick = { vault.detail(entry.id) }, shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth().testTag("entry_row_${entry.id}")) {
            Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AccountAvatar(entry.title)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("••••••••", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = "密码已隐藏" })
                }
                VaultIcon(VaultIconKind.ChevronRight, null, Modifier.size(18.dp))
            }
        }
    }
    TextButton(onClick = vault::help, modifier = Modifier.fillMaxWidth().testTag("open_help")) {
        VaultIcon(VaultIconKind.Info, null, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text("使用与存储说明")
    }
}

@Composable
private fun EntryForm(state: VaultUi, vault: VaultViewModel) {
    val editing = state.page == Page.EDIT
    val original = if (editing) state.selected ?: return else null
    val initial = remember {
        EntryInput(original?.title.orEmpty(), original?.username.orEmpty(), original?.password.orEmpty(),
            original?.url.orEmpty(), original?.notes.orEmpty())
    }
    var title by remember { mutableStateOf(initial.title) }
    var username by remember { mutableStateOf(initial.username) }
    var password by remember { mutableStateOf(initial.password) }
    var url by remember { mutableStateOf(initial.url) }
    var notes by remember { mutableStateOf(initial.notes) }
    var more by remember { mutableStateOf(initial.url.isNotEmpty() || initial.notes.isNotEmpty()) }
    var discard by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var generatorOpen by remember { mutableStateOf(false) }
    val changed = EntryInput(title, username, password, url, notes) != initial
    val focus = LocalFocusManager.current
    LaunchedEffect(visible) { if (visible) { delay(10_000); visible = false } }
    fun leave() { if (editing) vault.cancelEdit() else vault.list() }
    fun back() {
        if (state.busy) return
        if (changed) discard = true else leave()
    }
    BackHandler { back() }
    Text(if (editing) "编辑账号" else "新增账号", style = MaterialTheme.typography.headlineMedium)
    Panel {
        Text("登录信息", style = MaterialTheme.typography.titleMedium)
        Field(title, { vault.touch(); title = it }, "名称（必填）", "entry_title", enabled = !state.busy,
            leading = VaultIconKind.Note, placeholder = "例如，个人邮箱")
        Field(username, { vault.touch(); username = it }, "账号（选填）", "entry_username", enabled = !state.busy,
            leading = VaultIconKind.User, placeholder = "邮箱、手机号或用户名")
        Field(password, { vault.touch(); password = it }, "密码（必填）", "entry_password", secret = true,
            revealed = visible, enabled = !state.busy, leading = VaultIconKind.Key, onReveal = { visible = !visible })
        OutlinedButton(onClick = {
            vault.touch()
            focus.clearFocus()
            visible = false
            generatorOpen = true
        }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("open_password_generator")) {
            VaultIcon(VaultIconKind.Key, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("自动生成密码")
        }
    }
    Panel {
        TextButton(onClick = { more = !more }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("more_info")) {
            VaultIcon(VaultIconKind.More, null, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(if (more) "收起更多信息" else "添加网址与备注")
            Spacer(Modifier.weight(1f))
            VaultIcon(VaultIconKind.ChevronRight, null, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (more) {
                Field(url, { vault.touch(); url = it }, "网址", "entry_url", enabled = !state.busy, leading = VaultIconKind.Globe)
                Text("仅作记录，不代表可信目标。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Field(notes, { vault.touch(); notes = it }, "备注", "entry_notes", enabled = !state.busy, leading = VaultIconKind.Note)
            }
        }
    }
    QuietNote("尚未保存。退出或锁定会丢弃输入，当前开发版没有草稿恢复。", VaultIconKind.Info)
    PrimaryAction(if (state.busy) "正在保存…" else if (editing) "保存修改" else "保存账号",
        VaultIconKind.Check, "save_entry", enabled = !state.busy && (!editing || changed), loading = state.busy) {
        focus.clearFocus()
        vault.save(EntryInput(title, username, password, url, notes))
    }
    TextButton(onClick = { back() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("back")) {
        Text(if (editing) "返回详情" else "返回密码库")
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("这些输入尚未保存") },
        text = { Text("丢弃后无法恢复。继续编辑并保存，或明确丢弃。") },
        confirmButton = { TextButton(onClick = { discard = false; leave() }) { Text("丢弃输入") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } })
    if (generatorOpen) PasswordGeneratorSheet(
        onInteraction = vault::touch,
        onDismiss = { generatorOpen = false },
        onUse = { candidate ->
            val active = vault.ui
            if (active.epoch == state.epoch && active.page == state.page && !active.busy &&
                active.selected?.id == original?.id && active.selected?.version == original?.version) {
                password = candidate
                visible = false
            }
            generatorOpen = false
        },
    )
}

@Composable
private fun EntryDetail(state: VaultUi, vault: VaultViewModel, clipboard: SensitiveClipboard) {
    val entry = state.selected ?: return
    var shown by remember { mutableStateOf(false) }
    var revealSeconds by remember { mutableIntStateOf(0) }
    var accountCopied by remember { mutableStateOf(false) }
    var passwordCopied by remember { mutableStateOf(false) }
    BackHandler { vault.list() }
    LaunchedEffect(shown) {
        if (shown) {
            for (seconds in 10 downTo 1) { revealSeconds = seconds; delay(1_000) }
            shown = false
        } else revealSeconds = 0
    }
    LaunchedEffect(accountCopied) { if (accountCopied) { delay(2_500); accountCopied = false } }
    LaunchedEffect(passwordCopied) { if (passwordCopied) { delay(2_500); passwordCopied = false } }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        AccountAvatar(entry.title, large = true)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(entry.title, style = MaterialTheme.typography.headlineSmall)
        }
    }
    OutlinedButton(onClick = vault::edit, modifier = Modifier.fillMaxWidth().testTag("edit_entry")) {
        VaultIcon(VaultIconKind.Note, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("编辑账号")
    }
    Panel {
        SectionLabel("账号", VaultIconKind.User)
        Text(entry.username.ifEmpty { "未填写" }, style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag("detail_username_text"))
        if (entry.username.isNotEmpty()) OutlinedButton(onClick = {
            vault.credential(state.epoch, entry.id, entry.version, includePassword = false)?.let {
                accountCopied = clipboard.copy(it)
                vault.message(if (accountCopied) "已复制；支持时将在约 30 秒后清除。" else "未能确认复制成功，请重试。")
            }
        }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("copy_username")) {
            VaultIcon(if (accountCopied) VaultIconKind.Check else VaultIconKind.Copy, null, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(if (accountCopied) "已复制" else "复制账号")
        }
    }
    Panel {
        SectionLabel("密码", VaultIconKind.Key)
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
            if (shown) Text(entry.password, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().padding(18.dp).testTag("detail_password_text"))
            else Text("••••••••", style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth().padding(18.dp).testTag("detail_password_text").clearAndSetSemantics { contentDescription = "密码已隐藏" })
        }
        Text(if (shown) "${revealSeconds} 秒后自动隐藏" else "默认隐藏，需要时再查看或复制。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ResponsiveActions(
            first = {
                OutlinedButton(onClick = {
                    if (vault.authorized(state.epoch, entry.id, entry.version)) {
                        vault.touch(); revealSeconds = if (shown) 0 else 10; shown = !shown
                    }
                }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("show_password")) {
                    VaultIcon(if (shown) VaultIconKind.EyeOff else VaultIconKind.Eye, null, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(if (shown) "隐藏密码" else "显示密码")
                }
            },
            second = {
                Button(onClick = {
                    vault.credential(state.epoch, entry.id, entry.version, includePassword = true)?.let {
                        passwordCopied = clipboard.copy(it)
                        vault.message(if (passwordCopied) "已复制；支持时将在约 30 秒后清除。" else "未能确认复制成功，请重试。")
                    }
                }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("copy_password")) {
                    VaultIcon(if (passwordCopied) VaultIconKind.Check else VaultIconKind.Copy, null, Modifier.size(18.dp), MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(if (passwordCopied) "已复制" else "复制密码")
                }
            },
        )
    }
    if (entry.url.isNotEmpty() || entry.notes.isNotEmpty()) Panel {
        if (entry.url.isNotEmpty()) {
            SectionLabel("网址", VaultIconKind.Globe)
            Text(entry.url, style = MaterialTheme.typography.bodyMedium)
        }
        if (entry.notes.isNotEmpty()) {
            SectionLabel("备注", VaultIconKind.Note)
            Text(entry.notes, style = MaterialTheme.typography.bodyMedium)
        }
    }
    TextButton(onClick = vault::list, modifier = Modifier.fillMaxWidth().testTag("back")) { Text("返回密码库") }
}

@Composable
private fun Help(vault: VaultViewModel) {
    BackHandler { vault.leaveHelp() }
    Text("安心使用，\n从了解边界开始。", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("help_title"))
    HelpCard("主密码与手机锁屏密码独立", VaultIconKind.Key,
        "没有客服重置、万能恢复码或解密后门。错误密码不会自动销毁文件；请妥善记住主密码。")
    HelpCard("你的资料，加密保存在本机", VaultIconKind.Shield,
        "密码库使用 KDBX 格式。当前支持建库、新增与编辑、密码生成、搜索与手动取用；备份恢复、生物识别和自动填充仍待后续增量。")
    HelpCard("离开时，密码库会锁定", VaultIconKind.Lock,
        "后台、锁屏、主动锁定或前台闲置约 5 分钟后需重新解锁。已开始的保存会完成提交或回退；未保存输入没有恢复保证。")
    HelpCard("复制后，请留意剪贴板", VaultIconKind.Copy,
        "前台约 30 秒或锁定时，仅在仍能确认属于本次复制时尝试清除。系统限制、进程结束、输入法及其他应用副本无法由本应用保证清除。")
    DevelopmentNotice()
    TextButton(onClick = vault::leaveHelp, modifier = Modifier.fillMaxWidth().testTag("back")) { Text("返回") }
}

@Composable
private fun HelpCard(title: String, icon: VaultIconKind, text: String) = Panel {
    SectionLabel(title, icon)
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
private fun PrimaryAction(label: String, icon: VaultIconKind, tag: String, enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 15.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag(tag)) {
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
        else VaultIcon(icon, null, Modifier.size(20.dp), if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
        Spacer(Modifier.width(10.dp))
        Text(label)
    }
}

@Composable
private fun Field(value: String, change: (String) -> Unit, label: String, tag: String,
    secret: Boolean = false, revealed: Boolean = false, enabled: Boolean = true, leading: VaultIconKind? = null,
    placeholder: String? = null, onReveal: (() -> Unit)? = null, revealDescription: String? = null,
    onClear: (() -> Unit)? = null, imeAction: ImeAction = ImeAction.Default, onDone: (() -> Unit)? = null) {
    OutlinedTextField(value = value, onValueChange = change, label = { Text(label) }, enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag(tag), shape = MaterialTheme.shapes.medium,
        maxLines = if (tag == "entry_notes") 6 else 3,
        placeholder = placeholder?.let { hint -> { Text(hint) } },
        leadingIcon = leading?.let { icon -> { VaultIcon(icon, null, Modifier.size(20.dp)) } },
        trailingIcon = if (onReveal != null) ({
            IconButton(onClick = onReveal, enabled = enabled,
                modifier = Modifier.semantics { contentDescription = revealDescription ?: if (revealed) "隐藏密码" else "显示密码" }) {
                VaultIcon(if (revealed) VaultIconKind.EyeOff else VaultIconKind.Eye, null, Modifier.size(20.dp))
            }
        }) else if (onClear != null) ({
            IconButton(onClick = onClear, modifier = Modifier.testTag("clear_search").semantics { contentDescription = "清空搜索" }) {
                VaultIcon(VaultIconKind.Close, null, Modifier.size(18.dp))
            }
        }) else null,
        visualTransformation = if (secret && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun SectionLabel(label: String, icon: VaultIconKind) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VaultIcon(icon, null, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AccountAvatar(title: String, large: Boolean = false) {
    val palette = listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.tertiaryContainer)
    val ink = listOf(MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
    val index = (title.hashCode() and Int.MAX_VALUE) % palette.size
    val initial = if (title.isEmpty()) "账" else String(Character.toChars(title.codePoints().findFirst().asInt))
    val textStyle = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge
    val diameter = with(LocalDensity.current) { maxOf(if (large) 66.dp else 48.dp, textStyle.lineHeight.toDp() + 12.dp) }
    Surface(shape = if (large) MaterialTheme.shapes.large else MaterialTheme.shapes.medium, color = palette[index]) {
        Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
            Text(initial, color = ink[index], style = textStyle)
        }
    }
}

@Composable
private fun ResponsiveActions(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    val largeType = LocalDensity.current.fontScale > 1.2f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (largeType || maxWidth < 280.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { first(); second() }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { first() }
                Box(Modifier.weight(1f)) { second() }
            }
        }
    }
}

@Composable
private fun QuietNote(text: String, icon: VaultIconKind) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        VaultIcon(icon, null, Modifier.padding(top = 3.dp).size(17.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Feedback(message: String, error: Boolean) {
    Surface(color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(Modifier.padding(15.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
            val ink = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
            VaultIcon(if (error) VaultIconKind.Info else VaultIconKind.Check, null, Modifier.padding(top = 2.dp).size(18.dp), ink)
            Text(message, color = ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DevelopmentNotice() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    QuietNote("内部开发版 · 仅使用合成资料。备份恢复尚未完成，请勿存入真实密码。", VaultIconKind.Info)
}
