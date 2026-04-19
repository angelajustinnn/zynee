(function () {
  "use strict";

  const path = (window.location.pathname || "").toLowerCase();
  if (
    path.endsWith("/login") || path.endsWith("/login.html") ||
    path.endsWith("/signup") || path.endsWith("/signup.html") ||
    path.endsWith("/otp-verify") || path.endsWith("/verify-otp") ||
    path.endsWith("/terms")
  ) {
    return;
  }

  injectStyles();

  const root = document.createElement("div");
  root.className = "zy-fn-notify";
  root.innerHTML = `
    <div class="zy-fn-notify-text" id="zyFnNotifyText"></div>
    <div class="zy-fn-notify-actions">
      <button type="button" class="zy-fn-btn open" id="zyFnNotifyOpen">Open</button>
      <button type="button" class="zy-fn-btn close" id="zyFnNotifyClose">Dismiss</button>
    </div>
  `;
  document.body.appendChild(root);

  const textEl = document.getElementById("zyFnNotifyText");
  const openBtn = document.getElementById("zyFnNotifyOpen");
  const closeBtn = document.getElementById("zyFnNotifyClose");
  const DISMISS_UNREAD_KEY = "zy_fn_notify_dismiss_unread_count";

  let timer = null;
  let lastUnread = -1;
  let dismissed = false;
  let themeObserver = null;

  openBtn.addEventListener("click", function () {
    dismissForUnread(lastUnread);
    window.location.href = "/future-notes.html?view=unlocked";
  });

  closeBtn.addEventListener("click", function () {
    dismissForUnread(lastUnread);
  });

  window.addEventListener("storage", function (event) {
    if (event.key !== DISMISS_UNREAD_KEY) return;
    if (lastUnread > 0 && isDismissedForUnread(lastUnread)) {
      dismissed = true;
      hideBanner();
    } else {
      dismissed = false;
    }
  });

  document.addEventListener("visibilitychange", function () {
    if (document.visibilityState === "visible") {
      pollNotification();
    }
  });

  syncThemeFromPage();
  watchThemeChanges();
  pollNotification();
  timer = window.setInterval(pollNotification, 10000);

  async function pollNotification() {
    try {
      const response = await fetch("/api/future-notes/notification", {
        credentials: "same-origin",
        cache: "no-store"
      });
      if (response.status === 401 || response.status === 403) {
        hideBanner();
        return;
      }

      const data = await response.json().catch(function () {
        return {};
      });
      if (!response.ok) {
        hideBanner();
        return;
      }

      const unread = Number(data.unreadUnlockedCount || 0);
      if (!Number.isFinite(unread) || unread <= 0) {
        lastUnread = unread;
        dismissed = false;
        clearDismissal();
        hideBanner();
        return;
      }

      if (!isDismissedForUnread(unread)) {
        dismissed = false;
      } else {
        dismissed = true;
      }
      lastUnread = unread;
      if (dismissed) {
        hideBanner();
        return;
      }

      textEl.textContent = unread === 1
        ? "🔓 1 future note has unlocked."
        : `🔓 ${unread} future notes have unlocked.`;
      showBanner();
    } catch (error) {
      // Keep silent on transient network errors.
    }
  }

  function showBanner() {
    root.classList.add("show");
  }

  function hideBanner() {
    root.classList.remove("show");
  }

  function dismissForUnread(unread) {
    const count = Number(unread);
    if (!Number.isFinite(count) || count <= 0) return;
    dismissed = true;
    localStorage.setItem(DISMISS_UNREAD_KEY, String(count));
    window.dispatchEvent(new CustomEvent("zynee-future-note-dismiss", {
      detail: { dismissed: true, unread: count }
    }));
    hideBanner();
  }

  function clearDismissal() {
    localStorage.removeItem(DISMISS_UNREAD_KEY);
    window.dispatchEvent(new CustomEvent("zynee-future-note-dismiss", {
      detail: { dismissed: false, unread: 0 }
    }));
  }

  function isDismissedForUnread(unread) {
    const count = Number(unread);
    if (!Number.isFinite(count) || count <= 0) return false;
    const raw = localStorage.getItem(DISMISS_UNREAD_KEY);
    const saved = Number(raw);
    return Number.isFinite(saved) && saved === count;
  }

  function watchThemeChanges() {
    if (!window.MutationObserver) return;
    themeObserver = new MutationObserver(syncThemeFromPage);
    themeObserver.observe(document.documentElement, {
      subtree: true,
      attributes: true,
      attributeFilter: ["class", "style"]
    });
  }

  function syncThemeFromPage() {
    const source = document.querySelector(".sidebar, .top-bar, .card, .notebook-box") || document.body;
    const style = window.getComputedStyle(source);
    const panelBg = normalizeColor(style.backgroundColor, "rgba(255, 255, 255, 0.95)");
    const textColor =
      window.getComputedStyle(document.documentElement).getPropertyValue("--card-text-color").trim() ||
      normalizeColor(style.color, "#1a1a1a");
    const accent = pickAccentColor();

    root.style.setProperty("--zy-fn-bg", panelBg);
    root.style.setProperty("--zy-fn-text", textColor);
    root.style.setProperty("--zy-fn-accent", accent);
  }

  function pickAccentColor() {
    const picks = [".hamburger", ".sidebar h4", ".welcome-title", ".card h3"];
    for (const selector of picks) {
      const el = document.querySelector(selector);
      if (!el) continue;
      const color = normalizeColor(window.getComputedStyle(el).color, "");
      if (color) return color;
    }
    return "#8b5da9";
  }

  function normalizeColor(value, fallback) {
    const raw = String(value || "").trim();
    if (!raw || raw === "transparent" || raw === "rgba(0, 0, 0, 0)") {
      return fallback;
    }
    return raw;
  }

  function injectStyles() {
    if (document.getElementById("zy-fn-notify-style")) return;
    const style = document.createElement("style");
    style.id = "zy-fn-notify-style";
    style.textContent = `
      .zy-fn-notify {
        position: fixed;
        top: 74px;
        right: 16px;
        z-index: 1300;
        display: none;
        align-items: center;
        gap: 10px;
        min-width: 270px;
        max-width: 360px;
        padding: 10px 12px;
        border-radius: 14px;
        border: 1px solid var(--zy-fn-accent, #8b5da9);
        background: var(--zy-fn-bg, rgba(255, 255, 255, 0.95));
        color: var(--zy-fn-text, #1a1a1a);
        box-shadow: 0 10px 24px rgba(28, 18, 44, 0.2);
        backdrop-filter: blur(6px);
      }
      .zy-fn-notify.show {
        display: flex;
      }
      .zy-fn-notify-text {
        flex: 1;
        font-size: 0.9rem;
        font-weight: 600;
        line-height: 1.25rem;
      }
      .zy-fn-notify-actions {
        display: flex;
        gap: 6px;
      }
      .zy-fn-btn {
        border: none;
        border-radius: 999px;
        padding: 0.28rem 0.75rem;
        font-size: 0.78rem;
        font-weight: 600;
        cursor: pointer;
      }
      .zy-fn-btn.open {
        background: rgba(139, 93, 169, 0.2);
        color: #5f3f82;
      }
      .zy-fn-btn.close {
        background: #efe7f9;
        color: #6a5a80;
      }

      /* Global dark-mode heading override: keep headings white, never purple */
      body.dark-mode h1,
      body.dark-mode h2,
      body.dark-mode h3,
      body.dark-mode h4,
      body.dark-mode h5,
      body.dark-mode h6,
      body.dark-mode .card-title,
      body.dark-mode .assistant-title,
      body.dark-mode .checkin-title,
      body.dark-mode .page-heading {
        color: #ffffff !important;
      }

      @media (max-width: 700px) {
        .zy-fn-notify {
          left: 12px;
          right: 12px;
          max-width: none;
          min-width: 0;
        }
      }
    `;
    document.head.appendChild(style);
  }
})();
