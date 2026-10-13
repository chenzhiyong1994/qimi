"use strict";

// Bundled copy and assets; this page never accepts or processes passwords.
const messages = {
  zh: {
    pageTitle: "栖密 Qimi — 密码留在本地，轻松留给日常。",
    pageDescription: "栖密 Qimi 1.0.0，开源 Android 本地密码管理应用。记录与编辑账号，按你的规则生成密码。查看真实界面、安装包与当前限制。",
    bannerAlt: "栖密本地密码管理应用的品牌宣传插画",
    skip: "跳到主要内容", homeLabel: "栖密 Qimi 首页", navLabel: "主导航", languageLabel: "选择语言",
    navProduct: "产品体验", navLocal: "为何本地", navRelease: "版本与下载",
    releasePill: "认识栖密 1.0.0", heroEyebrow: "ANDROID · 本地密码管理 · 开源",
    heroLine1: "密码留在本地，", heroLine2: "轻松留给日常。",
    heroDescription: "给零散的账号一个安静的归处。记录、编辑、生成密码，在熟悉的手机里，从容处理日常的小事。",
    downloadApk: "下载 Android 版", explore: "看看实际体验", readLimits: "下载前了解当前限制",
    heroCaption: "整齐一点，轻松一点。", screensNote: "1.0.0 真实界面 · 合成资料示例",
    listAlt: "栖密 1.0.0 密码库：合成示例记录只显示名称和隐藏的密码",
    principlesLabel: "产品原则", principle1: "密码库只在本机", principle2: "无需云账户与同步", principle3: "源码开放，边界透明",
    productIndex: "01 / 日常，简单一点", productTitle: "从记下来，\n到用得上。",
    productDescription: "把账号、密码、网址和备注收在一起。需要改动时随手编辑，需要新密码时按你的规则生成。",
    featuresLabel: "查看应用功能", editTitle: "记下，也方便修改。",
    editText: "保存过的账号也能继续编辑。五项内容，一张表单；取消修改，原记录依然保留。",
    editMeta: "新增记录 / 编辑记录", generateTitle: "新密码，按你的规则。",
    generateText: "简单、普通、复杂，三档任选。也可开启高级自定义，设置长度与字符类型，再选用生成结果。",
    generateMeta: "默认普通 / 高级自定义 8–128 位", detailTitle: "需要时，找到再取用。",
    detailText: "按名称、账号或网址域名搜索。列表隐藏密码，详情中按需显示、复制，用完再锁好。",
    detailMeta: "本地搜索 / 按需查看与复制", screenKicker: "在栖密里",
    editAlt: "栖密 1.0.0 编辑账号表单，已填写合成名称和账号，密码保持隐藏",
    generatorAlt: "栖密 1.0.0 高级密码生成面板，可设置复杂度、长度与字符类型，候选密码保持隐藏",
    detailAlt: "栖密 1.0.0 账号详情，提供编辑、显示和复制操作，密码保持隐藏",
    editCaption: "改动之前，内容都在。", generateCaption: "长度与组合，自己决定。", detailCaption: "默认隐藏，需要时再查看。",
    localIndex: "02 / 留在你的设备里", localTitle: "少一份连接，\n多一份自主。",
    localDescription: "栖密从本地出发。不需要注册云账户，App 不申请网络权限，也不接入云同步。",
    viewSource: "到 GitHub 查看源码 ↗", local1Title: "一把主密码，本地加密保存。",
    local1Text: "账号、密码、网址与备注保存在本机加密库中，由你的主密码解锁。",
    local2Title: "暂时离开，也记得锁好。", local2Text: "支持主动锁定与后台锁定。显示的密码会定时隐藏，敏感剪贴板按规则清理。",
    local3Title: "实现与边界，都公开。", local3Text: "源码、验证记录和待完成事项可查阅。本地存储不等于全部安全验证已经完成。",
    releaseIndex: "03 / 从这个版本开始", releaseTitle: "栖密，与你见面。", releaseNotes: "发行说明与文件校验 ↗",
    releaseIntro: "已支持本地建库、新增与编辑、密码生成、搜索、显示与复制。下一步，继续完善离线备份与恢复。",
    limitsLabel: "使用之前", limitsTitle: "请先了解当前边界。",
    limitsText: "备份恢复、草稿、生物识别与自动填充尚未提供。完整安全审查、系统云备份与设备迁移排除实测仍未完成。",
    limitsImportant: "当前请仅使用合成资料测试，勿保管真实密码。", readProgress: "查看实现与验证记录 ↗",
    contributeEyebrow: "公开构建，一起参与", contributeTitle: "一起，把日常打磨得更好。", contributeText: "欢迎反馈问题，或贡献代码、测试与文档。反馈中请只使用合成资料。",
    githubAction: "在 GitHub 参与 ↗", footerTagline: "密码留在本地，轻松留给日常。", languageAnnouncement: "已切换为中文。"
  },
  en: {
    pageTitle: "Qimi — Passwords stay local. Everyday life feels lighter.",
    pageDescription: "Qimi 1.0.0 is an open-source local Android password manager. Save and edit accounts. Generate passwords your way. Explore real screens, the APK, and current limits.",
    bannerAlt: "Brand illustration for Qimi, a local Android password manager",
    skip: "Skip to main content", homeLabel: "Qimi home", navLabel: "Main navigation", languageLabel: "Choose language",
    navProduct: "The experience", navLocal: "Why local", navRelease: "Get Qimi",
    releasePill: "Meet Qimi 1.0.0", heroEyebrow: "ANDROID · LOCAL PASSWORD MANAGER · OPEN SOURCE",
    heroLine1: "Passwords stay local.", heroLine2: "Everyday life feels lighter.",
    heroDescription: "A quiet home for the accounts scattered across your day. Save, edit, and generate passwords on the phone you know, with a little less effort.",
    downloadApk: "Download for Android", explore: "See it in action", readLimits: "Read the current limits first",
    heroCaption: "A little order. A little more ease.", screensNote: "Actual 1.0.0 screens · Synthetic demo data",
    listAlt: "Qimi 1.0.0 vault with synthetic entries showing only names and masked passwords",
    principlesLabel: "Product principles", principle1: "Your vault stays on your device", principle2: "No cloud account or sync", principle3: "Open code. Clear boundaries.",
    productIndex: "01 / EVERYDAY, A LITTLE EASIER", productTitle: "Save it once.\nFind it when it matters.",
    productDescription: "Keep accounts, passwords, websites, and notes together. Update an entry when things change. Generate a new password on your terms.",
    featuresLabel: "Explore app features", editTitle: "Save it. Update it.",
    editText: "Saved entries stay editable. Five fields, one clear form. Canceling an edit leaves the original entry unchanged.",
    editMeta: "ADD ENTRIES / EDIT ENTRIES", generateTitle: "New passwords, your way.",
    generateText: "Choose Simple, Normal, or Complex. Or use advanced settings for length and character types, then apply your chosen result.",
    generateMeta: "NORMAL BY DEFAULT / CUSTOMIZE 8–128 CHARACTERS", detailTitle: "Find it. Use it.",
    detailText: "Search by name, username, or website domain. Passwords stay masked in the list. Reveal or copy in details, then lock up.",
    detailMeta: "LOCAL SEARCH / REVEAL & COPY", screenKicker: "INSIDE QIMI",
    editAlt: "Qimi 1.0.0 edit form with a synthetic name and username, and the password hidden",
    generatorAlt: "Qimi 1.0.0 advanced generator with complexity, length, and character settings, and its candidate password hidden",
    detailAlt: "Qimi 1.0.0 entry details with edit, reveal, and copy actions, and the password hidden",
    editCaption: "Everything in place, ready to update.", generateCaption: "Your length. Your combination.", detailCaption: "Hidden by default. Reveal when needed.",
    localIndex: "02 / ON YOUR DEVICE", localTitle: "Fewer connections.\nMore control.",
    localDescription: "Qimi starts locally. No cloud account to create. The app requests no network permission and has no cloud sync.",
    viewSource: "Explore the source on GitHub ↗", local1Title: "One master password. Local encryption.",
    local1Text: "Accounts, passwords, websites, and notes live in an encrypted vault on your device, unlocked with your master password.",
    local2Title: "Step away. Lock up.", local2Text: "Lock manually or let the app lock in the background. Revealed passwords hide automatically; clipboard cleanup follows the documented rules.",
    local3Title: "The code and its limits, in the open.", local3Text: "Read the source, validation evidence, and remaining work. Local storage does not mean every security check is complete.",
    releaseIndex: "03 / START WITH THIS RELEASE", releaseTitle: "Meet your Qimi.", releaseNotes: "Release notes & file checksums ↗",
    releaseIntro: "Local vaults, adding and editing entries, password generation, search, reveal, and copy are here. Offline backup and recovery are next.",
    limitsLabel: "BEFORE YOU START", limitsTitle: "Know the current limits.",
    limitsText: "Backup and recovery, drafts, biometrics, and autofill are not available yet. Full security review and system cloud backup and device-transfer exclusion tests remain unfinished.",
    limitsImportant: "Use synthetic test data only for now. Do not store real passwords.", readProgress: "Read the status & validation evidence ↗",
    contributeEyebrow: "BUILT IN THE OPEN", contributeTitle: "Make everyday life a little better. Together.", contributeText: "Report an issue, or contribute code, tests, and documentation. Use only synthetic data in feedback.",
    githubAction: "Join us on GitHub ↗", footerTagline: "Passwords stay local. Everyday life feels lighter.", languageAnnouncement: "Language changed to English."
  }
};

const storageKey = "qimi.site.language";
function readLanguage() {
  const requested = new URLSearchParams(window.location.search).get("lang");
  if (requested === "en" || requested === "zh") return requested;
  try {
    const saved = window.localStorage.getItem(storageKey);
    if (saved === "en" || saved === "zh") return saved;
  } catch {
    // The query string and language buttons also work without browser storage.
  }
  return "zh";
}
function setLanguage(language, announce = false) {
  if (!Object.hasOwn(messages, language)) return;
  const copy = messages[language];
  document.documentElement.lang = language === "zh" ? "zh-CN" : "en";
  document.title = copy.pageTitle;
  document.querySelector('meta[name="description"]').content = copy.pageDescription;
  document.querySelector('meta[property="og:title"]').content = copy.pageTitle;
  document.querySelector('meta[property="og:description"]').content = copy.pageDescription;
  document.querySelector('meta[property="og:image:alt"]').content = copy.bannerAlt;
  document.querySelectorAll("[data-i18n]").forEach((element) => { element.textContent = copy[element.dataset.i18n]; });
  document.querySelectorAll("[data-i18n-aria]").forEach((element) => { element.setAttribute("aria-label", copy[element.dataset.i18nAria]); });
  document.querySelectorAll("[data-i18n-alt]").forEach((element) => { element.alt = copy[element.dataset.i18nAlt]; });
  document.querySelectorAll("[data-language]").forEach((button) => { button.setAttribute("aria-pressed", String(button.dataset.language === language)); });
  document.querySelectorAll("[data-language-readme]").forEach((link) => {
    link.href = `https://github.com/chenzhiyong1994/qimi/blob/main/${language === "en" ? "README.en.md" : "README.md"}`;
  });
  if (announce) {
    document.querySelector("#language-announcement").textContent = copy.languageAnnouncement;
    try { window.localStorage.setItem(storageKey, language); } catch { /* An optional preference. */ }
    const url = new URL(window.location.href);
    url.searchParams.set("lang", language);
    window.history.replaceState(null, "", url);
  }
}
document.querySelectorAll("[data-language]").forEach((button) => {
  button.addEventListener("click", () => setLanguage(button.dataset.language, true));
});
setLanguage(readLanguage());

const tabs = [...document.querySelectorAll('[role="tab"]')];
function selectFeature(selected, moveFocus = false) {
  tabs.forEach((tab) => {
    const active = tab === selected;
    tab.setAttribute("aria-selected", String(active));
    tab.tabIndex = active ? 0 : -1;
    document.getElementById(tab.getAttribute("aria-controls")).hidden = !active;
  });
  if (moveFocus) selected.focus();
}
tabs.forEach((tab, index) => {
  tab.addEventListener("click", () => selectFeature(tab));
  tab.addEventListener("keydown", (event) => {
    let next;
    if (event.key === "ArrowDown") next = (index + 1) % tabs.length;
    if (event.key === "ArrowUp") next = (index + tabs.length - 1) % tabs.length;
    if (event.key === "Home") next = 0;
    if (event.key === "End") next = tabs.length - 1;
    if (next !== undefined) {
      event.preventDefault();
      selectFeature(tabs[next], true);
    }
  });
});
