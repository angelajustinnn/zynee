(function () {
  "use strict";

  const configEl = document.getElementById("floatingCompanionConfig");
  if (!configEl) return;

  const path = (window.location.pathname || "").toLowerCase();
  if (path.endsWith("/profile") || path.endsWith("/profile.html") || path.endsWith("/ai-assistant") || path.endsWith("/ai-assistant.html")) {
    return;
  }

  const sessionKey = (configEl.dataset.sessionKey || "").trim();
  if (!sessionKey) return;

  const STORAGE_PREFIX = "zynee_floating_companion::";
  const NAME_STORAGE_PREFIX = "zynee_floating_companion_name::";
  const storageKey = STORAGE_PREFIX + sessionKey;

  cleanupOldStates(STORAGE_PREFIX, storageKey);
  injectStyles();

  const initialUserName = (configEl.dataset.userName || "").trim();
  const userId = (configEl.dataset.userId || "").trim();
  const userScope = userId ? `id:${userId}` : initialUserName ? `name:${normalizeKey(initialUserName)}` : "";
  const nameStorageKey = userScope ? NAME_STORAGE_PREFIX + userScope : "";

  const defaultName = buildDefaultName(initialUserName);
  const persistentName = loadPersistentName(nameStorageKey);

  const state = loadState(storageKey, {
    name: persistentName || defaultName,
    x: Math.max(16, window.innerWidth - 88),
    y: Math.max(90, window.innerHeight - 120),
    open: false,
    messages: []
  });

  if (persistentName) {
    state.name = persistentName;
  } else if (!state.name || state.name === "Zynee Companion") {
    state.name = defaultName;
  }

  if (nameStorageKey && state.name) {
    savePersistentName(nameStorageKey, state.name);
  }

  let sending = false;
  let typingRow = null;
  let dragState = null;
  let themeObserver = null;
  let welcomeObserver = null;
  let themeSyncQueued = false;

  const root = document.createElement("div");
  root.className = "zy-comp-root";

  const bubble = document.createElement("button");
  bubble.type = "button";
  bubble.className = "zy-comp-bubble";
  bubble.title = "Open companion chat";
  bubble.setAttribute("aria-label", "Open companion chat");
  bubble.innerHTML = '<span class="zy-comp-bubble-icon">🤍</span>';

  const panel = document.createElement("div");
  panel.className = "zy-comp-panel";

  const header = document.createElement("div");
  header.className = "zy-comp-header";

  const nameInput = document.createElement("input");
  nameInput.className = "zy-comp-name";
  nameInput.type = "text";
  nameInput.maxLength = 28;
  nameInput.value = state.name || "Zynee Companion";
  nameInput.title = "Rename companion";

  const closeBtn = document.createElement("button");
  closeBtn.type = "button";
  closeBtn.className = "zy-comp-close";
  closeBtn.setAttribute("aria-label", "Close chat");
  closeBtn.textContent = "×";

  header.appendChild(nameInput);
  header.appendChild(closeBtn);

  const messagesEl = document.createElement("div");
  messagesEl.className = "zy-comp-messages";

  const composer = document.createElement("div");
  composer.className = "zy-comp-composer";

  const input = document.createElement("input");
  input.type = "text";
  input.className = "zy-comp-input";
  input.placeholder = "Message your companion...";
  input.maxLength = 3000;

  const sendBtn = document.createElement("button");
  sendBtn.type = "button";
  sendBtn.className = "zy-comp-send";
  sendBtn.textContent = "Send";

  composer.appendChild(input);
  composer.appendChild(sendBtn);

  panel.appendChild(header);
  panel.appendChild(messagesEl);
  panel.appendChild(composer);

  root.appendChild(bubble);
  root.appendChild(panel);
  document.body.appendChild(root);

  syncThemeFromPage();
  watchThemeChanges();
  syncWelcomeLayer();
  watchWelcomeLayer();

  renderMessages();
  applyPositions();
  togglePanel(Boolean(state.open), false);
  saveState();

  bubble.addEventListener("pointerdown", onBubblePointerDown);
  bubble.addEventListener("pointermove", onBubblePointerMove);
  bubble.addEventListener("pointerup", onBubblePointerUp);
  bubble.addEventListener("pointercancel", onBubblePointerCancel);

  closeBtn.addEventListener("click", function () {
    togglePanel(false);
  });

  nameInput.addEventListener("change", function () {
    updateName(nameInput.value);
  });
  nameInput.addEventListener("blur", function () {
    updateName(nameInput.value);
  });
  nameInput.addEventListener("keydown", function (event) {
    if (event.key === "Enter") {
      event.preventDefault();
      nameInput.blur();
    }
  });

  sendBtn.addEventListener("click", sendMessage);
  input.addEventListener("keydown", function (event) {
    if (event.key === "Enter") {
      event.preventDefault();
      sendMessage();
    }
  });

  window.addEventListener("resize", function () {
    clampBubble();
    applyPositions();
    saveState();
  });

  function onBubblePointerDown(event) {
    dragState = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      bubbleX: state.x,
      bubbleY: state.y,
      moved: false
    };
    bubble.setPointerCapture(event.pointerId);
  }

  function onBubblePointerMove(event) {
    if (!dragState || event.pointerId !== dragState.pointerId) return;
    const dx = event.clientX - dragState.startX;
    const dy = event.clientY - dragState.startY;
    if (Math.abs(dx) > 3 || Math.abs(dy) > 3) {
      dragState.moved = true;
    }
    state.x = dragState.bubbleX + dx;
    state.y = dragState.bubbleY + dy;
    clampBubble();
    applyPositions();
  }

  function onBubblePointerUp(event) {
    if (!dragState || event.pointerId !== dragState.pointerId) return;
    if (bubble.hasPointerCapture(event.pointerId)) {
      bubble.releasePointerCapture(event.pointerId);
    }
    const moved = dragState.moved;
    dragState = null;
    if (!moved) {
      togglePanel(!state.open);
    } else {
      saveState();
    }
  }

  function onBubblePointerCancel(event) {
    if (!dragState || event.pointerId !== dragState.pointerId) return;
    dragState = null;
  }

  function updateName(raw) {
    const next = (raw || "").trim().slice(0, 28);
    state.name = next || "Zynee Companion";
    nameInput.value = state.name;
    if (nameStorageKey) {
      savePersistentName(nameStorageKey, state.name);
    }
    saveState();
  }

  function togglePanel(open, persist = true) {
    state.open = open;
    panel.style.display = open ? "flex" : "none";
    if (open) {
      input.focus();
    }
    applyPositions();
    if (persist) saveState();
  }

  function clampBubble() {
    const maxX = Math.max(12, window.innerWidth - 68);
    const maxY = Math.max(72, window.innerHeight - 68);
    state.x = clamp(state.x, 12, maxX);
    state.y = clamp(state.y, 72, maxY);
  }

  function applyPositions() {
    clampBubble();
    bubble.style.left = `${Math.round(state.x)}px`;
    bubble.style.top = `${Math.round(state.y)}px`;

    const panelWidth = 330;
    const panelHeight = Math.min(470, Math.max(320, window.innerHeight - 120));
    panel.style.width = `${panelWidth}px`;
    panel.style.height = `${panelHeight}px`;
    const panelX = clamp(state.x - panelWidth + 58, 12, window.innerWidth - panelWidth - 12);
    const panelY = clamp(state.y - panelHeight - 14, 72, window.innerHeight - panelHeight - 12);
    panel.style.left = `${Math.round(panelX)}px`;
    panel.style.top = `${Math.round(panelY)}px`;
  }

  function renderMessages() {
    messagesEl.innerHTML = "";
    if (!state.messages.length) {
      appendSystemHint();
      return;
    }
    for (const message of state.messages) {
      appendMessage(message.role, message.content);
    }
    scrollMessagesToBottom();
  }

  function appendSystemHint() {
    const hint = document.createElement("div");
    hint.className = "zy-comp-hint";
    hint.textContent = "Hi! I am your companion. Start chatting any time.";
    messagesEl.appendChild(hint);
  }

  function appendMessage(role, content) {
    const row = document.createElement("div");
    row.className = `zy-comp-row ${role === "user" ? "user" : "assistant"}`;

    const bubbleEl = document.createElement("div");
    bubbleEl.className = `zy-comp-msg ${role === "user" ? "user" : "assistant"}`;
    bubbleEl.innerHTML = linkify(escapeHtml(content)).replace(/\n/g, "<br>");

    row.appendChild(bubbleEl);
    messagesEl.appendChild(row);
  }

  function addMessage(role, content) {
    state.messages.push({ role, content });
    if (state.messages.length > 50) {
      state.messages.splice(0, state.messages.length - 50);
    }
    appendMessage(role, content);
    scrollMessagesToBottom();
    saveState();
  }

  function showTyping() {
    if (typingRow) return;
    typingRow = document.createElement("div");
    typingRow.className = "zy-comp-row assistant";
    typingRow.innerHTML =
      '<div class="zy-comp-msg assistant zy-comp-typing">' +
      '<span class="zy-comp-dot"></span><span class="zy-comp-dot"></span><span class="zy-comp-dot"></span>' +
      "</div>";
    messagesEl.appendChild(typingRow);
    scrollMessagesToBottom();
  }

  function hideTyping() {
    if (!typingRow) return;
    typingRow.remove();
    typingRow = null;
  }

  function scrollMessagesToBottom() {
    messagesEl.scrollTop = messagesEl.scrollHeight;
  }

  function setSending(flag) {
    sending = flag;
    input.disabled = flag;
    sendBtn.disabled = flag;
    sendBtn.textContent = flag ? "..." : "Send";
  }

  function shouldRetryAssistantError(message, status) {
    const text = String(message || "").toLowerCase();
    return status === 502 ||
      status === 503 ||
      text.includes("assistant is unavailable") ||
      text.includes("temporarily unavailable") ||
      text.includes("could not reach local assistant") ||
      text.includes("start ollama");
  }

  function wait(ms) {
    return new Promise(function (resolve) {
      setTimeout(resolve, ms);
    });
  }

  async function sendMessage() {
    const text = input.value.trim();
    if (!text || sending) return;

    const historyForRequest = state.messages.slice(-12).map(function (item) {
      return { role: item.role, content: item.content };
    });

    input.value = "";
    addMessage("user", text);
    showTyping();
    setSending(true);

    try {
      var reply = "";
      var attempt = 0;
      while (attempt < 2) {
        const response = await fetch("/api/emotional-assistant/chat", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          credentials: "same-origin",
          body: JSON.stringify({
            message: text,
            history: historyForRequest
          })
        });

        const data = await response.json().catch(function () {
          return {};
        });
        if (response.ok) {
          reply = String(data.reply || "").trim() || "I could not generate a response right now.";
          break;
        }

        const errorMessage = data.error || "Assistant is unavailable right now.";
        if (attempt === 0 && shouldRetryAssistantError(errorMessage, response.status)) {
          attempt += 1;
          await wait(1400);
          continue;
        }
        throw new Error(errorMessage);
      }

      addMessage("assistant", reply);
    } catch (error) {
      addMessage(
        "assistant",
        error && error.message ? error.message : "Network issue while contacting the assistant."
      );
    } finally {
      hideTyping();
      setSending(false);
      input.focus();
    }
  }

  function saveState() {
    try {
      sessionStorage.setItem(storageKey, JSON.stringify(state));
    } catch (error) {
      // Ignore quota/privacy write failures.
    }
  }

  function loadState(key, fallback) {
    try {
      const raw = sessionStorage.getItem(key);
      if (!raw) return { ...fallback };
      const parsed = JSON.parse(raw);
      return {
        name: typeof parsed.name === "string" ? parsed.name : fallback.name,
        x: Number.isFinite(parsed.x) ? parsed.x : fallback.x,
        y: Number.isFinite(parsed.y) ? parsed.y : fallback.y,
        open: Boolean(parsed.open),
        messages: Array.isArray(parsed.messages)
          ? parsed.messages
              .filter(function (m) {
                return m && (m.role === "user" || m.role === "assistant") && typeof m.content === "string";
              })
              .map(function (m) {
                return { role: m.role, content: m.content.slice(0, 1000) };
              })
          : []
      };
    } catch (error) {
      return { ...fallback };
    }
  }

  function cleanupOldStates(prefix, keepKey) {
    try {
      const keysToDelete = [];
      for (let i = 0; i < sessionStorage.length; i += 1) {
        const key = sessionStorage.key(i);
        if (key && key.startsWith(prefix) && key !== keepKey) {
          keysToDelete.push(key);
        }
      }
      for (const key of keysToDelete) {
        sessionStorage.removeItem(key);
      }
    } catch (error) {
      // Ignore storage access issues.
    }
  }

  function loadPersistentName(key) {
    if (!key) return "";
    try {
      const value = localStorage.getItem(key);
      return value ? value.trim().slice(0, 28) : "";
    } catch (error) {
      return "";
    }
  }

  function savePersistentName(key, value) {
    if (!key) return;
    try {
      localStorage.setItem(key, (value || "").trim().slice(0, 28));
    } catch (error) {
      // Ignore storage write failures.
    }
  }

  function buildDefaultName(userName) {
    const first = String(userName || "").trim().split(/\s+/)[0];
    return first ? `${first}'s Companion` : "Zynee Companion";
  }

  function normalizeKey(value) {
    return String(value || "")
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, "_")
      .replace(/^_+|_+$/g, "")
      .slice(0, 64);
  }

  function watchWelcomeLayer() {
    const welcomeBox = document.getElementById("welcomeBox");
    if (!welcomeBox || !window.MutationObserver) return;

    welcomeObserver = new MutationObserver(syncWelcomeLayer);
    welcomeObserver.observe(welcomeBox, {
      attributes: true,
      attributeFilter: ["class", "style"]
    });

    const blocker = document.querySelector(".blocker");
    if (blocker) {
      welcomeObserver.observe(blocker, {
        attributes: true,
        attributeFilter: ["class", "style"]
      });
    }

    window.addEventListener("resize", syncWelcomeLayer);
  }

  function syncWelcomeLayer() {
    const welcomeBox = document.getElementById("welcomeBox");
    if (!welcomeBox || !isVisible(welcomeBox)) {
      root.style.zIndex = "999";
      return;
    }

    const zIndexText = window.getComputedStyle(welcomeBox).zIndex;
    const welcomeZ = parseInt(zIndexText, 10);
    if (Number.isFinite(welcomeZ)) {
      root.style.zIndex = String(Math.max(1, welcomeZ - 2));
    } else {
      root.style.zIndex = "900";
    }
  }

  function isVisible(element) {
    if (!element) return false;
    if (element.classList && element.classList.contains("hidden")) return false;

    const style = window.getComputedStyle(element);
    if (style.display === "none" || style.visibility === "hidden") return false;
    if (parseFloat(style.opacity || "1") <= 0) return false;

    return true;
  }

  function watchThemeChanges() {
    if (!window.MutationObserver) return;
    themeObserver = new MutationObserver(queueThemeSync);
    themeObserver.observe(document.documentElement, {
      subtree: true,
      attributes: true,
      attributeFilter: ["class", "style"]
    });
  }

  function queueThemeSync() {
    if (themeSyncQueued) return;
    themeSyncQueued = true;
    window.requestAnimationFrame(function () {
      themeSyncQueued = false;
      syncThemeFromPage();
    });
  }

  function syncThemeFromPage() {
    const source = document.querySelector(".welcome-box, .card, .sidebar, .top-bar") || document.body;
    const sourceStyle = window.getComputedStyle(source);
    const rootStyle = window.getComputedStyle(document.documentElement);

    const panelBg = normalizeColor(sourceStyle.backgroundColor, "rgb(255, 255, 255)");
    const panelRgb = parseColor(panelBg) || [255, 255, 255];
    const textColor =
      rootStyle.getPropertyValue("--card-text-color").trim() ||
      normalizeColor(sourceStyle.color, "rgb(26, 26, 26)");
    const accent = pickAccentColor();
    const dark = relativeLuminance(panelRgb[0], panelRgb[1], panelRgb[2]) < 0.45;

    root.style.setProperty("--zy-comp-bubble-bg", buildBubbleGradient(accent));
    root.style.setProperty("--zy-comp-accent", accent);
    root.style.setProperty("--zy-comp-panel-bg", panelBg);
    root.style.setProperty("--zy-comp-text", textColor);

    if (dark) {
      root.style.setProperty("--zy-comp-border", "rgba(255, 255, 255, 0.18)");
      root.style.setProperty("--zy-comp-header-bg", "rgba(255, 255, 255, 0.08)");
      root.style.setProperty("--zy-comp-hint-bg", "rgba(255, 255, 255, 0.08)");
      root.style.setProperty("--zy-comp-hint-text", "rgba(255, 255, 255, 0.85)");
      root.style.setProperty("--zy-comp-user-bg", "rgba(92, 169, 255, 0.3)");
      root.style.setProperty("--zy-comp-assistant-bg", "rgba(255, 255, 255, 0.12)");
      root.style.setProperty("--zy-comp-input-bg", "rgba(255, 255, 255, 0.08)");
      root.style.setProperty("--zy-comp-input-border", "rgba(255, 255, 255, 0.22)");
      root.style.setProperty("--zy-comp-input-text", textColor);
      root.style.setProperty("--zy-comp-close-bg", "rgba(255, 255, 255, 0.14)");
      root.style.setProperty("--zy-comp-close-text", textColor);
      root.style.setProperty("--zy-comp-link", accent);
      root.style.setProperty("--zy-comp-shadow", "0 14px 30px rgba(0, 0, 0, 0.45)");
    } else {
      root.style.setProperty("--zy-comp-border", "#ece1f7");
      root.style.setProperty("--zy-comp-header-bg", "#f4e9ff");
      root.style.setProperty("--zy-comp-hint-bg", "#f8f2ff");
      root.style.setProperty("--zy-comp-hint-text", "#7d6795");
      root.style.setProperty("--zy-comp-user-bg", "#d8eaff");
      root.style.setProperty("--zy-comp-assistant-bg", "#f1e6ff");
      root.style.setProperty("--zy-comp-input-bg", "#ffffff");
      root.style.setProperty("--zy-comp-input-border", "#d9d9d9");
      root.style.setProperty("--zy-comp-input-text", "#1a1a1a");
      root.style.setProperty("--zy-comp-close-bg", "#e9d8fb");
      root.style.setProperty("--zy-comp-close-text", "#5f3f82");
      root.style.setProperty("--zy-comp-link", "#5f3f82");
      root.style.setProperty("--zy-comp-shadow", "0 14px 30px rgba(23, 16, 32, 0.24)");
    }
  }

  function pickAccentColor() {
    const candidates = [".hamburger", ".top-bar .bi", ".sidebar h4", ".profile-icon"];
    for (const selector of candidates) {
      const el = document.querySelector(selector);
      if (!el) continue;
      const color = normalizeColor(window.getComputedStyle(el).color, "");
      if (color) return color;
    }
    return "rgb(139, 93, 169)";
  }

  function buildBubbleGradient(accentColor) {
    const base = parseColor(accentColor);
    if (!base) {
      return "linear-gradient(145deg, #8b5da9, #b787d4)";
    }
    const light = mixColor(base, [255, 255, 255], 0.28);
    const dark = mixColor(base, [0, 0, 0], 0.12);
    return `linear-gradient(145deg, ${toRgb(light)}, ${toRgb(dark)})`;
  }

  function parseColor(value) {
    if (!value) return null;
    const trimmed = value.trim();

    const rgb = trimmed.match(/^rgba?\((\d+),\s*(\d+),\s*(\d+)/i);
    if (rgb) {
      return [clampChannel(rgb[1]), clampChannel(rgb[2]), clampChannel(rgb[3])];
    }

    const hex = trimmed.match(/^#([0-9a-f]{3}|[0-9a-f]{6})$/i);
    if (hex) {
      const token = hex[1];
      if (token.length === 3) {
        return [
          parseInt(token[0] + token[0], 16),
          parseInt(token[1] + token[1], 16),
          parseInt(token[2] + token[2], 16)
        ];
      }
      return [
        parseInt(token.slice(0, 2), 16),
        parseInt(token.slice(2, 4), 16),
        parseInt(token.slice(4, 6), 16)
      ];
    }

    return null;
  }

  function mixColor(from, to, ratio) {
    const clamped = clamp(ratio, 0, 1);
    return [
      Math.round(from[0] * (1 - clamped) + to[0] * clamped),
      Math.round(from[1] * (1 - clamped) + to[1] * clamped),
      Math.round(from[2] * (1 - clamped) + to[2] * clamped)
    ];
  }

  function toRgb(channels) {
    return `rgb(${channels[0]}, ${channels[1]}, ${channels[2]})`;
  }

  function relativeLuminance(r, g, b) {
    const channels = [r, g, b].map(function (channel) {
      const value = channel / 255;
      return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    });
    return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
  }

  function clampChannel(value) {
    return clamp(parseInt(value, 10), 0, 255);
  }

  function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
  }

  function normalizeColor(value, fallback) {
    const raw = (value || "").trim();
    if (!raw || raw === "transparent" || raw === "rgba(0, 0, 0, 0)") {
      return fallback;
    }
    return raw;
  }

  function escapeHtml(value) {
    return value
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function linkify(value) {
    return value.replace(
      /(https?:\/\/[^\s<]+)/g,
      '<a href="$1" target="_blank" rel="noopener noreferrer">$1</a>'
    );
  }

  function injectStyles() {
    if (document.getElementById("zy-companion-style")) return;
    const style = document.createElement("style");
    style.id = "zy-companion-style";
    style.textContent = `
      .zy-comp-root {
        position: fixed;
        inset: 0;
        pointer-events: none;
        z-index: 999;
        --zy-comp-accent: #8b5da9;
        --zy-comp-bubble-bg: linear-gradient(145deg, #8b5da9, #b787d4);
        --zy-comp-panel-bg: #ffffff;
        --zy-comp-header-bg: #f4e9ff;
        --zy-comp-border: #ece1f7;
        --zy-comp-text: #1a1a1a;
        --zy-comp-hint-bg: #f8f2ff;
        --zy-comp-hint-text: #7d6795;
        --zy-comp-user-bg: #d8eaff;
        --zy-comp-assistant-bg: #f1e6ff;
        --zy-comp-input-bg: #ffffff;
        --zy-comp-input-border: #d9d9d9;
        --zy-comp-input-text: #1a1a1a;
        --zy-comp-close-bg: #e9d8fb;
        --zy-comp-close-text: #5f3f82;
        --zy-comp-link: #5f3f82;
        --zy-comp-shadow: 0 14px 30px rgba(23, 16, 32, 0.24);
      }
      .zy-comp-bubble {
        position: fixed;
        width: 56px;
        height: 56px;
        border-radius: 50%;
        border: none;
        cursor: grab;
        pointer-events: auto;
        background: var(--zy-comp-bubble-bg);
        color: #fff;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 22px;
        box-shadow: 0 8px 20px rgba(80, 47, 107, 0.35);
      }
      .zy-comp-bubble:active { cursor: grabbing; }
      .zy-comp-panel {
        position: fixed;
        pointer-events: auto;
        display: none;
        flex-direction: column;
        border-radius: 16px;
        overflow: hidden;
        background: var(--zy-comp-panel-bg);
        box-shadow: var(--zy-comp-shadow);
        border: 1px solid var(--zy-comp-border);
      }
      .zy-comp-header {
        height: 48px;
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 0 10px;
        background: var(--zy-comp-header-bg);
        border-bottom: 1px solid var(--zy-comp-border);
      }
      .zy-comp-name {
        flex: 1;
        border: none;
        background: transparent;
        outline: none;
        color: var(--zy-comp-text);
        font-size: 14px;
        font-weight: 600;
      }
      .zy-comp-close {
        width: 30px;
        height: 30px;
        border: none;
        border-radius: 50%;
        background: var(--zy-comp-close-bg);
        color: var(--zy-comp-close-text);
        cursor: pointer;
        font-size: 22px;
        line-height: 1;
      }
      .zy-comp-messages {
        flex: 1;
        overflow-y: auto;
        background: var(--zy-comp-panel-bg);
        padding: 12px;
        display: flex;
        flex-direction: column;
        gap: 10px;
      }
      .zy-comp-hint {
        font-size: 12px;
        color: var(--zy-comp-hint-text);
        background: var(--zy-comp-hint-bg);
        border: 1px solid var(--zy-comp-border);
        border-radius: 10px;
        padding: 8px 10px;
      }
      .zy-comp-row { display: flex; }
      .zy-comp-row.user { justify-content: flex-end; }
      .zy-comp-row.assistant { justify-content: flex-start; }
      .zy-comp-msg {
        max-width: 82%;
        white-space: pre-wrap;
        word-break: break-word;
        border-radius: 12px;
        padding: 8px 10px;
        font-size: 13px;
        line-height: 1.35;
        color: var(--zy-comp-text);
      }
      .zy-comp-msg.user { background: var(--zy-comp-user-bg); }
      .zy-comp-msg.assistant { background: var(--zy-comp-assistant-bg); }
      .zy-comp-msg a { color: var(--zy-comp-link); text-decoration: underline; }
      .zy-comp-composer {
        display: flex;
        gap: 8px;
        padding: 10px;
        border-top: 1px solid var(--zy-comp-border);
        background: var(--zy-comp-panel-bg);
      }
      .zy-comp-input {
        flex: 1;
        height: 36px;
        border-radius: 18px;
        border: 1px solid var(--zy-comp-input-border);
        background: var(--zy-comp-input-bg);
        color: var(--zy-comp-input-text);
        padding: 0 12px;
        font-size: 13px;
        outline: none;
      }
      .zy-comp-send {
        min-width: 58px;
        height: 36px;
        border-radius: 18px;
        border: none;
        background: var(--zy-comp-accent);
        color: #fff;
        font-size: 13px;
        cursor: pointer;
      }
      .zy-comp-send:disabled { opacity: 0.7; cursor: default; }
      .zy-comp-typing { display: inline-flex; align-items: center; gap: 5px; }
      .zy-comp-dot {
        width: 7px;
        height: 7px;
        border-radius: 50%;
        background: var(--zy-comp-accent);
        animation: zy-comp-dot 1.1s infinite ease-in-out;
        opacity: 0.35;
      }
      .zy-comp-dot:nth-child(2) { animation-delay: 0.2s; }
      .zy-comp-dot:nth-child(3) { animation-delay: 0.4s; }
      @keyframes zy-comp-dot {
        0%, 80%, 100% { opacity: 0.25; transform: translateY(0); }
        40% { opacity: 1; transform: translateY(-2px); }
      }
      @media (max-width: 640px) {
        .zy-comp-panel { width: min(92vw, 340px) !important; }
      }
    `;
    document.head.appendChild(style);
  }
})();
