"use strict";

// All website assets and copy are bundled locally; the app itself has no network permission.
const messages = {
  zh: {
    pageTitle: "栖密 Qimi — 密码留在本地，轻松留给日常。",
    pageDescription: "栖密 Qimi，一款正在开发中的开源 Android 本地密码管理应用。密码留在本地，轻松留给日常。",
    skip: "跳到主要内容",
    homeLabel: "栖密 Qimi 首页",
    navLabel: "主导航",
    languageLabel: "选择语言",
    navProduct: "产品体验",
    navProgress: "开发进展",
    navSource: "GitHub ↗",
    heroEyebrow: "本地密码管理 · 开源",
    heroLine1: "密码留在本地，",
    heroLine2: "轻松留给日常。",
    heroDescription: "给零散的账号一个安心的归处。记录、查找、取用，在熟悉的手机里，完成日常的小事。",
    viewSource: "查看源码 ↗",
    exploreProduct: "走进栖密 ↓",
    heroStage: "S1 开发版本 · 暂无正式安装包",
    bannerAlt: "栖密本地密码管理应用的产品宣传画面",
    bannerCaption: "为日常留一点从容。",
    principlesLabel: "产品原则",
    principle1: "App 不申请网络权限",
    principle2: "不接入云账户或云同步",
    principle3: "源码开放，边界透明",
    productIndex: "01 / 产品体验",
    productTitle: "少一点寻找，\n多一点从容。",
    productDescription: "从一条记录开始，把账号、网址与备注收在一起。需要时，找到它，再决定如何取用。",
    feature1Title: "记下，不必复杂。",
    feature1Text: "名称、账号、密码、网址和备注，一张清楚的表单。支持新增与编辑，按原值保存。",
    feature2Title: "找到，刚好用得上。",
    feature2Text: "按名称搜索，查看完整记录。按需显示或复制密码，用完回到日常。",
    feature3Title: "离开，也记得锁好。",
    feature3Text: "支持主动锁定与后台锁定，密码定时隐藏，敏感剪贴板按规则清理。",
    feature4Title: "由你掌握，由源码说话。",
    feature4Text: "主密码保护本地加密库，不接入云账户。实现、验证记录与待完成事项公开可读。",
    listAlt: "栖密 Android 应用的密码列表，展示合成测试账号与本地搜索",
    detailAlt: "栖密 Android 应用的记录详情，密码保持隐藏，提供显示与复制操作",
    listCaption: "记录与查找",
    detailCaption: "按需取用",
    screensNote: "此前版本真实 App 界面 · 合成资料示例",
    progressIndex: "02 / 公开构建",
    stageLabel: "当前：S1 开发版本",
    progressTitle: "一小步，\n认真做好。",
    progressText: "已接入本地建库、新增与编辑、搜索、手动取用及基础密码生成。源码现在开放，下一步继续完善备份与恢复。",
    readProgress: "查看实现与验证记录 ↗",
    roadmap1State: "已实现",
    roadmap1Title: "本地建库与手动取用",
    roadmap1Text: "创建、编辑、加密保存、搜索、显示与复制、后台锁定；新增或编辑账号可用三档或高级规则生成密码。",
    roadmap2State: "下一步 · 尚未实现",
    roadmap2Title: "离线备份与验证恢复",
    roadmap2Text: "加密备份、可读性验证、失败保全，并实测系统备份与设备迁移排除。",
    roadmap3State: "后续路线 · 尚未实现",
    roadmap3Title: "整理、管理与便利登录",
    roadmap3Text: "可恢复草稿、历史、生物识别与可信目标自动填充，逐步完成首版验收。",
    developmentNoteTitle: "当前请只用合成资料测试。",
    developmentNoteText: "还没有正式签名安装包。完整安全审查、备份恢复、系统云备份与设备迁移实测尚未完成，当前版本不用于保管真实密码。",
    contributeIndex: "03 / 一起打磨",
    contributeTitle: "更好的日常，\n可以一起写出来。",
    contributeText: "如果你也在意本地控制与顺畅体验，欢迎阅读源码、提出可复现的问题，或贡献实现、测试与文档。",
    contributeNote: "提交反馈时请使用合成资料，勿上传真实密码库、密码或密钥。",
    githubAction: "在 GitHub 参与 ↗",
    readReadme: "阅读 README",
    buildDocs: "从源码构建 · 开发文档 ↗",
    footerTagline: "密码留在本地，轻松留给日常。",
    footerLicense: "以 Apache-2.0 开源 ↗",
    languageAnnouncement: "已切换为中文。"
  },
  en: {
    pageTitle: "Qimi — Passwords stay local. Everyday life feels lighter.",
    pageDescription: "Qimi is an open-source Android password manager in development. Passwords stay local. Everyday life feels lighter.",
    skip: "Skip to main content",
    homeLabel: "Qimi home",
    navLabel: "Main navigation",
    languageLabel: "Choose language",
    navProduct: "The experience",
    navProgress: "Progress",
    navSource: "GitHub ↗",
    heroEyebrow: "Local password manager · Open source",
    heroLine1: "Passwords stay local.",
    heroLine2: "Everyday life feels lighter.",
    heroDescription: "A place for the accounts scattered across your day. Save, find, and use them on the phone you know, with a little less effort.",
    viewSource: "View source ↗",
    exploreProduct: "Explore Qimi ↓",
    heroStage: "S1 development build · No official APK yet",
    bannerAlt: "Product artwork for Qimi, a local Android password manager",
    bannerCaption: "A little more ease, every day.",
    principlesLabel: "Product principles",
    principle1: "App has no network permission",
    principle2: "No cloud account or cloud sync",
    principle3: "Open code. Clear boundaries.",
    productIndex: "01 / THE EXPERIENCE",
    productTitle: "Less searching.\nA little more ease.",
    productDescription: "Start with one record. Keep its account, website, and notes together. Find it when you need it, then choose how to use it.",
    feature1Title: "Make a note. Keep it simple.",
    feature1Text: "Name, account, password, website, and notes in one clear form. Add or edit entries while preserving values exactly as entered.",
    feature2Title: "Find it when it matters.",
    feature2Text: "Search by name and open the full record. Reveal or copy a password when you need it, then get on with your day.",
    feature3Title: "Step away. Lock up.",
    feature3Text: "Manual and background locking, timed password hiding, and sensitive clipboard cleanup according to the documented rules.",
    feature4Title: "Your control. Code you can read.",
    feature4Text: "A master password protects the local encrypted vault. No cloud account. Read the implementation, validation evidence, and remaining work.",
    listAlt: "Qimi Android vault list showing synthetic demo accounts and local search",
    detailAlt: "Qimi Android record detail with the password hidden and reveal and copy actions",
    listCaption: "Save & find",
    detailCaption: "Use when needed",
    screensNote: "Actual screens from an earlier build · Synthetic demo data",
    progressIndex: "02 / BUILDING IN THE OPEN",
    stageLabel: "Now: S1 development build",
    progressTitle: "One step at a time.\nWith care.",
    progressText: "Local vault creation, adding and editing entries, search, manual use, and basic password generation are implemented. The source is open. Backup and recovery remain the next priority.",
    readProgress: "Read the status & validation evidence ↗",
    roadmap1State: "IMPLEMENTED",
    roadmap1Title: "Local vault & manual use",
    roadmap1Text: "Create, edit, save encrypted records, search, reveal, and copy, with background locking. Generate passwords when adding or editing entries using three presets or advanced rules.",
    roadmap2State: "NEXT · NOT IMPLEMENTED",
    roadmap2Title: "Offline backup & verified recovery",
    roadmap2Text: "Encrypted backups, readability checks, failure preservation, and tests for system backup and device transfer exclusions.",
    roadmap3State: "LATER · NOT IMPLEMENTED",
    roadmap3Title: "Organization & easier sign-in",
    roadmap3Text: "Recoverable drafts, history, biometrics, and autofill for verified targets, followed by full first-release acceptance.",
    developmentNoteTitle: "Use synthetic test data only for now.",
    developmentNoteText: "There is no officially signed APK. Full security review, backup and recovery, and system cloud backup and device transfer tests are unfinished. This build is not ready to hold real passwords.",
    contributeIndex: "03 / MAKE IT BETTER TOGETHER",
    contributeTitle: "A better everyday experience.\nWritten together.",
    contributeText: "If local control and thoughtful interactions matter to you, read the code, report reproducible issues, or contribute implementation, tests, and documentation.",
    contributeNote: "Use synthetic data in feedback. Never upload real vaults, passwords, or keys.",
    githubAction: "Join us on GitHub ↗",
    readReadme: "Read the README",
    buildDocs: "Build from source · Developer docs ↗",
    footerTagline: "Passwords stay local. Everyday life feels lighter.",
    footerLicense: "Open source under Apache-2.0 ↗",
    languageAnnouncement: "Language changed to English."
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
    // Language switching remains available when persistent browser storage is disabled.
  }
  return "zh";
}

function setLanguage(language, announce = false) {
  if (!(language in messages)) return;
  const copy = messages[language];
  document.documentElement.lang = language === "zh" ? "zh-CN" : "en";
  document.title = copy.pageTitle;
  document.querySelector('meta[name="description"]').content = copy.pageDescription;
  document.querySelector('meta[property="og:title"]').content = copy.pageTitle;
  document.querySelector('meta[property="og:description"]').content = copy.pageDescription;
  document.querySelector('meta[property="og:image:alt"]').content = copy.bannerAlt;

  document.querySelectorAll("[data-i18n]").forEach((element) => {
    const value = copy[element.dataset.i18n];
    if (value !== undefined) element.textContent = value;
  });
  document.querySelectorAll("[data-i18n-aria]").forEach((element) => {
    const value = copy[element.dataset.i18nAria];
    if (value !== undefined) element.setAttribute("aria-label", value);
  });
  document.querySelectorAll("[data-i18n-alt]").forEach((element) => {
    const value = copy[element.dataset.i18nAlt];
    if (value !== undefined) element.alt = value;
  });
  document.querySelectorAll("[data-language]").forEach((button) => {
    button.setAttribute("aria-pressed", String(button.dataset.language === language));
  });
  const readme = language === "en" ? "README.en.md" : "README.md";
  document.querySelectorAll("[data-language-readme]").forEach((link) => {
    link.href = `https://github.com/chenzhiyong1994/qimi/blob/main/${readme}`;
  });

  if (announce) {
    document.querySelector("#language-announcement").textContent = copy.languageAnnouncement;
    try {
      window.localStorage.setItem(storageKey, language);
    } catch {
      // No persisted preference is required for this interaction.
    }
    const url = new URL(window.location.href);
    url.searchParams.set("lang", language);
    window.history.replaceState(null, "", url);
  }
}

document.querySelectorAll("[data-language]").forEach((button) => {
  button.addEventListener("click", () => setLanguage(button.dataset.language, true));
});
setLanguage(readLanguage());
