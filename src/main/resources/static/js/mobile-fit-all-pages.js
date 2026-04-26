(function () {
  const MOBILE_QUERY = "(max-width: 991.98px)";
  const MOBILE_SCALE = 0.9;
  const BODY_CLASS = "zynee-mobile-compact";

  let rafToken = null;
  let sidebarObserver = null;
  let observedSidebar = null;

  function isMobileLayout() {
    return window.matchMedia(MOBILE_QUERY).matches;
  }

  function injectMobileStyle() {
    if (document.getElementById("zynee-mobile-fit-style")) return;

    const style = document.createElement("style");
    style.id = "zynee-mobile-fit-style";
    style.textContent = `
@media (max-width: 991.98px) {
  html,
  body {
    overflow-x: hidden !important;
    max-width: 100% !important;
    -webkit-text-size-adjust: 100% !important;
  }

  body.${BODY_CLASS} {
    --zynee-mobile-scale: ${MOBILE_SCALE};
    font-size: calc(16px * var(--zynee-mobile-scale)) !important;
  }

  body.${BODY_CLASS} #mainContent,
  body.${BODY_CLASS} #mainContent.shifted,
  body.${BODY_CLASS} .main,
  body.${BODY_CLASS} .main.shifted,
  body.${BODY_CLASS} .main-wrapper,
  body.${BODY_CLASS} .main-wrapper.shifted,
  body.${BODY_CLASS} main {
    margin-left: 0 !important;
    left: 0 !important;
    width: 100% !important;
    max-width: 100% !important;
    transform: none !important;
    padding-left: 0.75rem !important;
    padding-right: 0.75rem !important;
  }

  body.${BODY_CLASS} .top-bar {
    min-height: 56px !important;
    height: 56px !important;
    display: grid !important;
    grid-template-columns: auto minmax(0, 1fr) auto !important;
    align-items: center !important;
    gap: 0.45rem !important;
    padding-left: 0.6rem !important;
    padding-right: 0.6rem !important;
    z-index: 2105 !important;
  }

  body.${BODY_CLASS} .top-bar > * {
    min-width: 0 !important;
  }

  body.${BODY_CLASS} .profile-icon,
  body.${BODY_CLASS} #profileCircle,
  body.${BODY_CLASS} .top-bar img[style*="width: 40px"] {
    width: 34px !important;
    height: 34px !important;
  }

  body.${BODY_CLASS} .search-bar {
    position: static !important;
    left: auto !important;
    transform: none !important;
    flex: 1 1 auto !important;
    width: 100% !important;
    min-width: 0 !important;
    max-width: 100% !important;
    font-size: 0.95rem !important;
    padding: 0.35rem 0.75rem !important;
  }

  body.${BODY_CLASS} .search-bar:focus {
    width: 100% !important;
  }

  body.${BODY_CLASS} .top-bar .sort-select {
    margin-right: 0 !important;
    padding: 0.3rem 0.55rem !important;
    min-width: 84px !important;
    font-size: 0.88rem !important;
  }

  body.${BODY_CLASS} .top-bar .top-close-btn {
    position: static !important;
    top: auto !important;
    right: auto !important;
    font-size: 1.1rem !important;
  }

  body.${BODY_CLASS} .suggestions {
    width: min(92vw, 320px) !important;
    max-width: 92vw !important;
    top: calc(56px + 0.35rem) !important;
    z-index: 2104 !important;
  }

  body.${BODY_CLASS} .sidebar {
    position: fixed !important;
    top: 0 !important;
    left: 0 !important;
    width: 100vw !important;
    max-width: 100vw !important;
    height: 100dvh !important;
    border-radius: 0 !important;
    transform: translateX(-100%) !important;
    z-index: 2100 !important;
    padding: calc(56px + env(safe-area-inset-top, 0px) + 0.65rem) 1rem 1.2rem !important;
  }

  body.${BODY_CLASS} .sidebar.open {
    transform: translateX(0) !important;
  }

  body.${BODY_CLASS} .sidebar h4 {
    margin-bottom: 1rem !important;
    font-size: 1.2rem !important;
  }

  body.${BODY_CLASS} .sidebar ul li {
    padding: 0.45rem 0 !important;
    font-size: 0.98rem !important;
  }

  body.${BODY_CLASS} .container,
  body.${BODY_CLASS} .container-fluid {
    max-width: 100% !important;
    padding-left: 0.35rem !important;
    padding-right: 0.35rem !important;
  }

  body.${BODY_CLASS} .row {
    --bs-gutter-x: 0.8rem !important;
    margin-left: 0 !important;
    margin-right: 0 !important;
  }

  body.${BODY_CLASS} .card,
  body.${BODY_CLASS} .card-box,
  body.${BODY_CLASS} .note-card,
  body.${BODY_CLASS} .summary-card,
  body.${BODY_CLASS} .checkin-card,
  body.${BODY_CLASS} .insight-card,
  body.${BODY_CLASS} .about-block,
  body.${BODY_CLASS} .music-card-shell,
  body.${BODY_CLASS} .notebook-box,
  body.${BODY_CLASS} .profile-card,
  body.${BODY_CLASS} .otp-card,
  body.${BODY_CLASS} .login-card,
  body.${BODY_CLASS} .signup-card,
  body.${BODY_CLASS} .terms-root,
  body.${BODY_CLASS} .entry-container,
  body.${BODY_CLASS} .guest-upgrade-card {
    width: 100% !important;
    max-width: 100% !important;
    margin-left: auto !important;
    margin-right: auto !important;
    border-radius: 16px !important;
  }

  body.${BODY_CLASS} .about-block,
  body.${BODY_CLASS} .notebook-box {
    height: auto !important;
    max-height: none !important;
    overflow: visible !important;
  }

  body.${BODY_CLASS} .card,
  body.${BODY_CLASS} .card-box,
  body.${BODY_CLASS} .about-block,
  body.${BODY_CLASS} .notebook-box,
  body.${BODY_CLASS} .profile-card,
  body.${BODY_CLASS} .entry-container,
  body.${BODY_CLASS} .login-card,
  body.${BODY_CLASS} .signup-card,
  body.${BODY_CLASS} .otp-card {
    padding: 1rem !important;
  }

  body.${BODY_CLASS} .card *,
  body.${BODY_CLASS} .card-box *,
  body.${BODY_CLASS} .about-block *,
  body.${BODY_CLASS} .notebook-box *,
  body.${BODY_CLASS} .profile-card *,
  body.${BODY_CLASS} .entry-container * {
    max-width: 100% !important;
    overflow-wrap: break-word !important;
    word-break: normal !important;
  }

  body.${BODY_CLASS} h1,
  body.${BODY_CLASS} h2,
  body.${BODY_CLASS} h3,
  body.${BODY_CLASS} h4,
  body.${BODY_CLASS} h5 {
    line-height: 1.2 !important;
    word-break: normal !important;
    overflow-wrap: break-word !important;
  }

  body.${BODY_CLASS} .notebook-header {
    flex-wrap: wrap !important;
    align-items: flex-start !important;
    gap: 0.45rem !important;
  }

  body.${BODY_CLASS} .notebook-header h3 {
    flex: 1 1 100% !important;
    margin: 0 !important;
    font-size: 1.45rem !important;
  }

  body.${BODY_CLASS} .timestamp {
    font-size: 0.95rem !important;
    margin-bottom: 0.65rem !important;
  }

  body.${BODY_CLASS} .journal-textarea {
    min-height: 300px !important;
    font-size: 1rem !important;
    line-height: 1.7 !important;
    background-size: 100% 1.7rem !important;
    padding: 1rem 0.8rem !important;
  }

  body.${BODY_CLASS} .welcome-box {
    width: min(92vw, 340px) !important;
    max-width: 340px !important;
    padding: 0.95rem 0.9rem 0.9rem !important;
    border-radius: 16px !important;
    top: 50% !important;
    left: 50% !important;
    transform: translate(-50%, -50%) !important;
    z-index: 2300 !important;
  }

  body.${BODY_CLASS} .welcome-title {
    font-size: 1.15rem !important;
    line-height: 1.25 !important;
    margin-bottom: 0.45rem !important;
  }

  body.${BODY_CLASS} .welcome-msg {
    font-size: 0.86rem !important;
    line-height: 1.4 !important;
    margin-bottom: 0.55rem !important;
  }

  body.${BODY_CLASS} .welcome-actions {
    gap: 0.45rem !important;
    flex-direction: column !important;
  }

  body.${BODY_CLASS} .welcome-action-btn {
    width: 100% !important;
    font-size: 0.84rem !important;
    padding: 0.45rem 0.8rem !important;
  }

  body.${BODY_CLASS} .welcome-box .close-btn,
  body.${BODY_CLASS} .welcome-box .close-btn:hover {
    width: 30px !important;
    height: 30px !important;
    top: 6px !important;
    right: 6px !important;
    font-size: 1.2rem !important;
    z-index: 2305 !important;
    pointer-events: auto !important;
    touch-action: manipulation !important;
  }

  body.${BODY_CLASS} .blocker {
    z-index: 2290 !important;
  }

  body.${BODY_CLASS} .guest-upgrade-card {
    width: min(92vw, 330px) !important;
    border-radius: 16px !important;
    padding: 1rem 0.9rem !important;
  }

  body.${BODY_CLASS} .guest-upgrade-title {
    font-size: 1rem !important;
  }

  body.${BODY_CLASS} .guest-upgrade-message {
    font-size: 0.86rem !important;
  }

  body.${BODY_CLASS} .zy-comp-bubble {
    width: 48px !important;
    height: 48px !important;
    font-size: 18px !important;
  }

  body.${BODY_CLASS} .zy-comp-panel {
    width: min(90vw, 320px) !important;
    max-width: 90vw !important;
    height: min(64dvh, 390px) !important;
    max-height: 390px !important;
  }

  body.${BODY_CLASS} .zy-comp-header {
    height: 42px !important;
  }

  body.${BODY_CLASS} .zy-comp-name,
  body.${BODY_CLASS} .zy-comp-msg {
    font-size: 13px !important;
  }

  body.${BODY_CLASS} .zy-comp-input,
  body.${BODY_CLASS} .zy-comp-send {
    height: 40px !important;
    font-size: 16px !important;
  }

  body.${BODY_CLASS} .zy-comp-send {
    min-width: 72px !important;
    white-space: nowrap !important;
    overflow-wrap: normal !important;
    word-break: keep-all !important;
    border-radius: 20px !important;
  }

  body.${BODY_CLASS} .assistant-composer {
    display: flex !important;
    align-items: center !important;
    gap: 0.4rem !important;
    flex-wrap: nowrap !important;
  }

  body.${BODY_CLASS} #userInput {
    flex: 1 1 auto !important;
    min-width: 0 !important;
    min-height: 42px !important;
    font-size: 16px !important;
  }

  body.${BODY_CLASS} #sendButton {
    min-width: 68px !important;
    min-height: 40px !important;
    font-size: 15px !important;
    white-space: nowrap !important;
    overflow-wrap: normal !important;
    word-break: keep-all !important;
    padding: 0.5rem 0.8rem !important;
  }

  body.${BODY_CLASS} .unlock-input-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr)) !important;
    gap: 0.45rem !important;
  }

  body.${BODY_CLASS} .unlock-input-grid > * {
    min-width: 0 !important;
  }

  body.${BODY_CLASS} .future-input,
  body.${BODY_CLASS} #futureUnlockTime {
    width: 100% !important;
    min-width: 0 !important;
    font-size: 0.92rem !important;
    padding: 0.45rem 0.5rem !important;
  }

  body.${BODY_CLASS} .entry-actions {
    flex-wrap: wrap !important;
    gap: 0.45rem !important;
  }

  body.${BODY_CLASS} #miniCalendar {
    gap: 8px !important;
    padding: 0 6px !important;
  }

  body.${BODY_CLASS} #miniCalendar .day-name {
    white-space: nowrap !important;
    overflow-wrap: normal !important;
    word-break: keep-all !important;
    font-size: 0.78rem !important;
  }

  body.${BODY_CLASS} .btn,
  body.${BODY_CLASS} .cute-button,
  body.${BODY_CLASS} .welcome-action-btn,
  body.${BODY_CLASS} .unlock-action {
    white-space: nowrap !important;
    overflow-wrap: normal !important;
    word-break: keep-all !important;
  }

  body.${BODY_CLASS} #miniCalendar .day {
    padding: 8px 0 !important;
    min-height: 36px !important;
  }

  body.${BODY_CLASS} #board {
    max-width: 100% !important;
    margin-inline: auto !important;
  }

  body.${BODY_CLASS} .card-box > .cute-button {
    display: block !important;
    width: 100% !important;
    max-width: 280px !important;
    margin: 0.35rem auto !important;
  }

  body.${BODY_CLASS} .card-box #savedAffirmationsList div {
    flex-wrap: wrap !important;
    gap: 0.45rem !important;
    align-items: flex-start !important;
  }

  body.${BODY_CLASS} .chat-container,
  body.${BODY_CLASS} #chatContainer {
    padding: 0.75rem !important;
  }

  body.${BODY_CLASS} .emoji-grid,
  body.${BODY_CLASS} .feeling-grid,
  body.${BODY_CLASS} .trigger-grid,
  body.${BODY_CLASS} .charts-grid,
  body.${BODY_CLASS} .stats-grid,
  body.${BODY_CLASS} .summary-grid,
  body.${BODY_CLASS} .dashboard-grid,
  body.${BODY_CLASS} .main-grid,
  body.${BODY_CLASS} .future-grid,
  body.${BODY_CLASS} .future-notes-grid {
    grid-template-columns: 1fr !important;
  }
}
`;
    document.head.appendChild(style);
  }

  function syncMainLayout() {
    const mainCandidates = document.querySelectorAll("#mainContent, .main, .main-wrapper, main");
    mainCandidates.forEach((main) => {
      if (!main) return;
      main.classList.remove("shifted");
      main.style.marginLeft = "0";
      main.style.width = "100%";
      main.style.maxWidth = "100%";
    });
  }

  function syncSidebarLayout() {
    const sidebar = document.getElementById("sidebar");
    if (!sidebar) {
      document.body.style.overflow = "";
      return;
    }

    sidebar.style.left = "0";
    sidebar.style.width = "100vw";
    sidebar.style.maxWidth = "100vw";
    sidebar.style.top = "0";
    sidebar.style.height = "100dvh";

    const sidebarOpen = sidebar.classList.contains("open");
    document.body.style.overflow = sidebarOpen ? "hidden" : "";
  }

  function watchSidebar() {
    const sidebar = document.getElementById("sidebar");
    if (!window.MutationObserver) return;

    if (!sidebar) {
      if (sidebarObserver) {
        sidebarObserver.disconnect();
        sidebarObserver = null;
        observedSidebar = null;
      }
      return;
    }

    if (observedSidebar === sidebar && sidebarObserver) return;

    if (sidebarObserver) sidebarObserver.disconnect();
    observedSidebar = sidebar;
    sidebarObserver = new MutationObserver(scheduleApplyMobileFit);
    sidebarObserver.observe(sidebar, {
      attributes: true,
      attributeFilter: ["class", "style"]
    });
  }

  function applyProfileImageFallbacks() {
    const candidates = document.querySelectorAll(
      "img.profile-pic, img[alt='Profile'], img[alt='Profile Picture'], #cropImagePreview"
    );
    candidates.forEach((img) => {
      if (!img || img.dataset.zyneeFallbackBound === "1") return;
      img.dataset.zyneeFallbackBound = "1";
      img.addEventListener("error", () => {
        img.style.display = "none";
        const circle = document.getElementById("profileCircle");
        if (circle) circle.style.display = "inline-flex";
        const initials = document.querySelector(".profile-initials");
        if (initials) initials.style.display = "inline-flex";
        const initialsPreview = document.getElementById("initialsPreview");
        if (initialsPreview) initialsPreview.classList.remove("d-none");
      });
    });
  }

  function applyMobileFit() {
    if (!isMobileLayout()) {
      document.body.classList.remove(BODY_CLASS);
      document.body.style.overflow = "";
      return;
    }

    document.body.classList.add(BODY_CLASS);
    syncMainLayout();
    syncSidebarLayout();
    watchSidebar();

    const topBar = document.querySelector(".top-bar");
    if (topBar) {
      topBar.style.width = "100%";
      topBar.style.maxWidth = "100%";
      topBar.style.left = "0";
    }
    applyProfileImageFallbacks();
  }

  function scheduleApplyMobileFit() {
    if (rafToken !== null) return;
    rafToken = window.requestAnimationFrame(() => {
      rafToken = null;
      applyMobileFit();
    });
  }

  function init() {
    injectMobileStyle();
    scheduleApplyMobileFit();
    setTimeout(scheduleApplyMobileFit, 100);
    setTimeout(scheduleApplyMobileFit, 260);
  }

  document.addEventListener("DOMContentLoaded", init);
  window.addEventListener("load", scheduleApplyMobileFit);
  window.addEventListener("pageshow", scheduleApplyMobileFit);
  window.addEventListener("resize", scheduleApplyMobileFit, { passive: true });
  window.addEventListener("orientationchange", function () {
    setTimeout(scheduleApplyMobileFit, 120);
  });
})();
