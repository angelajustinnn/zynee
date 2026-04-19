(function () {
  var HOME_PATH = "/home.html";
  var TOUR_STATE_KEY = "zyneeTourStateV2";
  var TOUR_COMPLETED_KEY = "zyneeTourCompletedV2";
  var TOUR_START_REQUEST_KEY = "zyneeTourStartRequestedV2";
  var CONSENT_PROMPT_REQUEST_KEY = "zyneeConsentPromptRequestedV1";

  var FIRST_LOGIN_ONLY_MODE = true;

  var TOUR_STEPS = [
    {
      selector: '[data-widget-id="journal"]',
      title: "My Journal",
      text: "Start here to write your thoughts and daily reflections.",
      closeSidebar: true,
    },
    {
      selector: '[data-widget-id="stats"]',
      title: "Stats",
      text: "This shows your mood trends and emotional progress charts.",
      closeSidebar: true,
    },
    {
      selector: '[data-widget-id="mood"]',
      title: "Mood Tracker",
      text: "Log your current mood quickly.",
      closeSidebar: true,
    },
    {
      selector: '[data-widget-id="candle"]',
      title: "Virtual Candle",
      text: "Use this mindful space when you want to pause and calm down.",
      closeSidebar: true,
    },
    {
      selector: '[data-widget-id="forecast"]',
      title: "Mood Forecast",
      text: "See AI-based mood predictions from your recent entries.",
      closeSidebar: true,
    },
    {
      selector: '[data-widget-id="cosmic"]',
      title: "Quick Check-In",
      text: "Open a short check-in flow to track how you feel right now.",
      closeSidebar: true,
    },
    {
      selector: '[data-widget-id="quick-checkin"]',
      title: "Cosmic Insights",
      text: "This card shows your daily emotional vibe based on your sun sign.",
      closeSidebar: true,
    },
    {
      selector: '[data-widget-id="affirmations"]',
      title: "Affirmations",
      text: "Generate positive affirmations whenever you need encouragement.",
      closeSidebar: true,
    },
    {
      selector: '[data-widget-id="music"]',
      title: "Music",
      text: "Play calming tracks to relax or shift your mood.",
      closeSidebar: true,
    },
    {
      selector: '[data-widget-id="rps"]',
      title: "RPS Game",
      text: "Try a quick game break when you want a light mood reset.",
      closeSidebar: true,
    },
    {
      selector: '[data-widget-id="ttt"]',
      title: "Tic-Tac-Toe",
      text: "Another mini-game option for short mindful breaks.",
      closeSidebar: true,
    },
    {
      selector: "#sidebarToggleBtn",
      title: "Side Menu",
      text: "Now we move to the menu for more pages.",
      closeSidebar: true,
      forceScrollTop: true,
    },
    {
      selector: '[data-tour="menu-assistant"]',
      title: "Emotional Assistant",
      text: "Open this from the side menu to chat with Zynee's emotional assistant.",
      requiresSidebar: true,
      forceScrollTop: true,
    },
    {
      selector: '[data-tour="menu-future-notes"]',
      title: "Future Notes",
      text: "Use Future Notes to write messages for your future self.",
      requiresSidebar: true,
      forceScrollTop: true,
    },
    {
      selector: '[data-tour="menu-insights"]',
      title: "Insights Page",
      text: "This opens deeper pattern-based emotional insights.",
      requiresSidebar: true,
      forceScrollTop: true,
    },
    {
      selector: '[data-tour="menu-theme"]',
      title: "Theme Settings",
      text: "Use Theme to switch between Default, Light, Dark, or Custom look.",
      requiresSidebar: true,
      openThemeMenu: true,
      forceScrollTop: true,
    },
    {
      selector: '[data-tour="menu-about"]',
      title: "About",
      text: "You can also access more information and support details about us here.",
      requiresSidebar: true,
      forceScrollTop: true,
    },
    {
      selector: "#searchInput",
      title: "Search Bar",
      text: "Search here to quickly find features and pages in Zynee.",
      closeSidebar: true,
      forceScrollTop: true,
    },
    {
      selector: "#profileLink",
      title: "Profile",
      text: "Open your profile here to update picture, details and other settings.",
      closeSidebar: true,
      forceScrollTop: true,
    },
    {
      selector: ".zy-comp-bubble",
      title: "AI Companion",
      text: "This namable floating companion is always available for quick support chat.",
      closeSidebar: true,
    },
  ];

  var overlayEl = null;
  var tooltipEl = null;
  var highlightedEl = null;
  var highlightedContainerEl = null;
  var highlightedInlineStyle = null;
  var retryTimer = null;
  var consentOverlayEl = null;
  var consentModalEl = null;
  var consentPromptOpen = false;
  var consentBusy = false;
  var CONSENT_API_PATH = "/api/data-analysis-consent";
  var guestRuntimeState = null;
  var LIGHT_HIGHLIGHT_START_INDEX = TOUR_STEPS.findIndex(function (step) {
    return step && step.selector === "#sidebarToggleBtn";
  });

  function normalizePath(pathname) {
    var value = pathname || "/";
    value = value.split("?")[0].split("#")[0];
    if (value === "/") return HOME_PATH;
    return value;
  }

  function getCurrentPath() {
    return normalizePath(window.location.pathname || "/");
  }

  function isGuestUser() {
    return window.ZYNEE_IS_GUEST === true;
  }

  function isFirstLoginOnboardingRequired() {
    return window.ZYNEE_FIRST_LOGIN_ONBOARDING_REQUIRED === true;
  }

  function isOnHome() {
    var current = getCurrentPath();
    return current === HOME_PATH || current.endsWith(HOME_PATH);
  }

  function readState() {
    if (isGuestUser()) {
      return guestRuntimeState
        ? { active: guestRuntimeState.active === true, index: Number(guestRuntimeState.index) }
        : null;
    }
    try {
      var raw = localStorage.getItem(TOUR_STATE_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch (error) {
      return null;
    }
  }

  function writeState(state) {
    if (isGuestUser()) {
      guestRuntimeState = state
        ? { active: state.active === true, index: Number(state.index) }
        : null;
      return;
    }
    localStorage.setItem(TOUR_STATE_KEY, JSON.stringify(state));
  }

  function clearState() {
    if (isGuestUser()) {
      guestRuntimeState = null;
      return;
    }
    localStorage.removeItem(TOUR_STATE_KEY);
  }

  function clearGuestTourPersistence() {
    guestRuntimeState = null;
    localStorage.removeItem(TOUR_STATE_KEY);
    localStorage.removeItem(TOUR_COMPLETED_KEY);
    localStorage.removeItem(TOUR_START_REQUEST_KEY);
    localStorage.removeItem(CONSENT_PROMPT_REQUEST_KEY);
  }

  function getActiveState() {
    var state = readState();
    if (!state || !state.active) return null;

    if (
      typeof state.index !== "number" ||
      state.index < 0 ||
      state.index >= TOUR_STEPS.length
    ) {
      clearState();
      return null;
    }

    return state;
  }

  function injectStyles() {
    if (document.getElementById("zynee-tour-style")) return;

    var style = document.createElement("style");
    style.id = "zynee-tour-style";
    style.textContent =
      "#zynee-tour-overlay {" +
      "position: fixed; inset: 0; background: rgba(20, 10, 30, 0.55); z-index: 4000; display: none;" +
      "}" +
      "#zynee-tour-tooltip {" +
      "position: fixed; width: min(360px, calc(100vw - 32px)); background: #fff; color: #3f2e52; border-radius: 14px;" +
      "box-shadow: 0 20px 40px rgba(40, 25, 70, 0.28); z-index: 4002; padding: 16px; display: none;" +
      "font-family: 'Segoe UI', sans-serif;" +
      "}" +
      "#zynee-tour-tooltip h4 { margin: 0 0 6px; font-size: 1.02rem; color: #513a6a; }" +
      "#zynee-tour-tooltip p { margin: 0; font-size: 0.93rem; line-height: 1.45; color: #5f4a77; }" +
      "#zynee-tour-progress { margin-top: 10px; font-size: 0.8rem; color: #8668a6; }" +
      "#zynee-tour-actions { margin-top: 14px; display: flex; gap: 8px; justify-content: flex-end; }" +
      ".zynee-tour-btn { border: 0; border-radius: 9px; padding: 8px 12px; font-size: 0.86rem; cursor: pointer; }" +
      ".zynee-tour-btn:disabled { opacity: 0.45; cursor: not-allowed; }" +
      ".zynee-tour-btn-secondary { background: #f0e8f8; color: #5f3f86; }" +
      ".zynee-tour-btn-primary { background: #7d5aa7; color: #fff; }" +
      ".zynee-tour-btn-skip { background: #f5f2f9; color: #7b6a91; }" +
      ".zynee-tour-highlight {" +
      "z-index: 4001 !important; pointer-events: none !important;" +
      "border-radius: 14px; box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.96), 0 0 0 7px rgba(132, 95, 177, 0.95) !important;" +
      "}" +
      ".zynee-tour-highlight-lit {" +
      "background: rgba(255, 255, 255, 0.96) !important;" +
      "box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.98), 0 0 0 7px rgba(132, 95, 177, 0.95), 0 14px 30px rgba(75, 53, 109, 0.34) !important;" +
      "}" +
      ".sidebar li.zynee-tour-highlight-lit {" +
      "border-radius: 12px !important;" +
      "}" +
      "#sidebarToggleBtn.zynee-tour-highlight-lit, #searchInput.zynee-tour-highlight-lit, #profileLink.zynee-tour-highlight-lit, .zy-comp-bubble.zynee-tour-highlight-lit {" +
      "border-radius: 16px !important;" +
      "}" +
      ".zy-comp-bubble.zynee-tour-highlight, .zy-comp-bubble.zynee-tour-highlight-lit {" +
      "box-shadow: 0 0 0 4px rgba(255, 255, 255, 0.98), 0 0 0 10px rgba(132, 95, 177, 0.98), 0 0 26px 8px rgba(255, 255, 255, 0.55) !important;" +
      "transform: scale(1.04);" +
      "}" +
      "#sidebar.zynee-tour-highlight-container, #topbar.zynee-tour-highlight-container, .zy-comp-root.zynee-tour-highlight-container {" +
      "z-index: 4001 !important;" +
      "}" +
      "body.zynee-tour-fast #sidebar, body.zynee-tour-fast #mainContent, body.zynee-tour-fast #themeMenu {" +
      "transition: none !important;" +
      "}" +
      "#zynee-consent-overlay {" +
      "position: fixed; inset: 0; background-color: rgba(255, 255, 255, 0.1); backdrop-filter: blur(10px); -webkit-backdrop-filter: blur(10px); z-index: 5000; display: none;" +
      "}" +
      "#zynee-consent-modal {" +
      "position: fixed; z-index: 5001; left: 50%; top: 50%; transform: translate(-50%, -50%);" +
      "width: min(560px, calc(100vw - 34px)); background: linear-gradient(180deg, #fff9ff 0%, #ffffff 100%); border-radius: 28px; padding: 22px;" +
      "border: 1px solid rgba(226, 196, 250, 0.7); box-shadow: 0 24px 46px rgba(40, 25, 70, 0.22), inset 0 1px 0 rgba(255, 255, 255, 0.85); color: #4a375f; display: none;" +
      "font-family: 'Segoe UI', sans-serif;" +
      "}" +
      "#zynee-consent-modal h4 { margin: 0 0 9px; color: #4f326d; font-size: 1.14rem; font-weight: 700; }" +
      "#zynee-consent-modal p { margin: 0; color: #614a7a; line-height: 1.58; font-size: 0.95rem; }" +
      "#zynee-consent-actions { margin-top: 18px; display: flex; gap: 10px; justify-content: flex-end; }" +
      ".zynee-consent-btn { border: 0; border-radius: 999px; padding: 10px 18px; cursor: pointer; font-size: 0.9rem; font-weight: 600; transition: transform 130ms ease, box-shadow 180ms ease; }" +
      ".zynee-consent-btn:disabled { opacity: 0.55; cursor: not-allowed; }" +
      ".zynee-consent-btn:not(:disabled):active { transform: scale(0.98); }" +
      ".zynee-consent-decline { background: #f7ecfd; color: #663f89; box-shadow: inset 0 0 0 1px rgba(172, 131, 210, 0.3); }" +
      ".zynee-consent-allow { background: linear-gradient(135deg, #8f63ba 0%, #6d4f99 100%); color: #fff; box-shadow: 0 8px 16px rgba(109, 79, 153, 0.26); }" +
      "#zynee-consent-status { margin-top: 11px; min-height: 1.15em; font-size: 0.84rem; color: #755d90; }";

    document.head.appendChild(style);
  }

  function ensureUi() {
    injectStyles();

    if (!overlayEl) {
      overlayEl = document.createElement("div");
      overlayEl.id = "zynee-tour-overlay";
      document.body.appendChild(overlayEl);
    }

    if (!tooltipEl) {
      tooltipEl = document.createElement("div");
      tooltipEl.id = "zynee-tour-tooltip";
      tooltipEl.innerHTML =
        '<h4 id="zynee-tour-title"></h4>' +
        '<p id="zynee-tour-text"></p>' +
        '<div id="zynee-tour-progress"></div>' +
        '<div id="zynee-tour-actions">' +
        '  <button type="button" class="zynee-tour-btn zynee-tour-btn-skip" data-tour-action="skip">Skip</button>' +
        '  <button type="button" class="zynee-tour-btn zynee-tour-btn-secondary" data-tour-action="back">Back</button>' +
        '  <button type="button" class="zynee-tour-btn zynee-tour-btn-primary" data-tour-action="next">Next</button>' +
        "</div>";

      tooltipEl.addEventListener("click", function (event) {
        var target = event.target;
        if (!(target instanceof HTMLElement)) return;
        var action = target.getAttribute("data-tour-action");

        if (action === "skip") {
          finishTour(false, true);
          return;
        }

        if (action === "back") {
          moveStep(-1);
          return;
        }

        if (action === "next") {
          moveStep(1);
        }
      });

      document.body.appendChild(tooltipEl);
    }
  }

  function ensureConsentUi() {
    injectStyles();

    if (!consentOverlayEl) {
      consentOverlayEl = document.createElement("div");
      consentOverlayEl.id = "zynee-consent-overlay";
      document.body.appendChild(consentOverlayEl);
    }

    if (!consentModalEl) {
      consentModalEl = document.createElement("div");
      consentModalEl.id = "zynee-consent-modal";
      consentModalEl.innerHTML =
        "<h4>Data Analysis Permission</h4>" +
        "<p>Can Zynee use your journal, mood tracking, and quick check-in data to generate insights, analysis, and reports?</p>" +
        '<div id="zynee-consent-status"></div>' +
        '<div id="zynee-consent-actions">' +
        '  <button type="button" class="zynee-consent-btn zynee-consent-decline" data-consent-action="decline">Decline</button>' +
        '  <button type="button" class="zynee-consent-btn zynee-consent-allow" data-consent-action="allow">Allow</button>' +
        "</div>";

      consentModalEl.addEventListener("click", function (event) {
        var target = event.target;
        if (!(target instanceof HTMLElement)) return;
        var action = target.getAttribute("data-consent-action");
        if (!action || consentBusy) return;

        if (action === "allow") {
          submitConsentChoice(true);
          return;
        }
        if (action === "decline") {
          submitConsentChoice(false);
        }
      });

      document.body.appendChild(consentModalEl);
    }
  }

  function setConsentStatusText(message) {
    if (!consentModalEl) return;
    var statusEl = consentModalEl.querySelector("#zynee-consent-status");
    if (!statusEl) return;
    statusEl.textContent = message == null ? "" : String(message);
  }

  function setConsentBusy(nextBusy) {
    consentBusy = nextBusy === true;
    if (!consentModalEl) return;
    var allowBtn = consentModalEl.querySelector('[data-consent-action="allow"]');
    var declineBtn = consentModalEl.querySelector('[data-consent-action="decline"]');
    if (allowBtn) allowBtn.disabled = consentBusy;
    if (declineBtn) declineBtn.disabled = consentBusy;
  }

  function hideConsentPrompt() {
    consentPromptOpen = false;
    setConsentBusy(false);
    setConsentStatusText("");
    if (consentOverlayEl) {
      consentOverlayEl.style.display = "none";
    }
    if (consentModalEl) {
      consentModalEl.style.display = "none";
    }
  }

  function clearHomeAnalysisCachesFromStorage() {
    try {
      Object.keys(localStorage).forEach(function (key) {
        if (!key) return;
        if (key.startsWith("zynee-home-daily-v1:moodForecast:") || key.startsWith("zynee-home-daily-v1:cosmicInsights:")) {
          localStorage.removeItem(key);
        }
      });
    } catch (error) {
      // ignore storage issues
    }
  }

  function readStoredConsentPreference() {
    var stored = localStorage.getItem("zynee_data_analysis_consent");
    if (stored === "allowed") {
      return { consentSet: true, analysisEnabled: true };
    }
    if (stored === "declined") {
      return { consentSet: true, analysisEnabled: false };
    }
    return { consentSet: false, analysisEnabled: false };
  }

  function syncHomeAnalysisUiFromConsent(enabled) {
    if (!isOnHome() || enabled === true) {
      return;
    }

    var todayMood = document.getElementById("todayMoodWord");
    var todayConfidence = document.getElementById("todayMoodConfidence");
    var tomorrowMood = document.getElementById("tomorrowMoodWord");
    var tomorrowConfidence = document.getElementById("tomorrowMoodConfidence");
    var forecastMeta = document.getElementById("moodForecastMeta");

    if (todayMood) todayMood.textContent = "-";
    if (todayConfidence) todayConfidence.textContent = "Confidence: -";
    if (tomorrowMood) tomorrowMood.textContent = "-";
    if (tomorrowConfidence) tomorrowConfidence.textContent = "Confidence: -";
    if (forecastMeta) {
      forecastMeta.style.display = "block";
      forecastMeta.textContent = "Analysis is disabled. Enable it in Profile > Other Settings.";
    }

    var cosmicSunSign = document.getElementById("cosmicSunSign");
    var cosmicHeadline = document.getElementById("cosmicVibeHeadline");
    var cosmicContext = document.getElementById("cosmicVibeContext");

    if (cosmicSunSign) cosmicSunSign.textContent = "Sun sign: -";
    if (cosmicHeadline) cosmicHeadline.textContent = "Analysis Disabled";
    if (cosmicContext) {
      cosmicContext.textContent = "Enable data analysis in Profile > Other Settings to get cosmic insights.";
    }
  }

  function applyConsentStateLocal(enabled, consentSet) {
    if (consentSet !== true) {
      localStorage.removeItem("zynee_data_analysis_consent");
      return;
    }

    var state = enabled === true ? "allowed" : "declined";
    localStorage.setItem("zynee_data_analysis_consent", state);
    if (state === "declined") {
      clearHomeAnalysisCachesFromStorage();
    }
    syncHomeAnalysisUiFromConsent(enabled === true);

    try {
      window.dispatchEvent(new CustomEvent("zynee:data-analysis-consent-changed", {
        detail: { analysisEnabled: enabled === true, consentSet: true }
      }));
    } catch (error) {
      // ignore CustomEvent issues
    }
  }

  async function fetchConsentStatusFromApi() {
    try {
      var response = await fetch(CONSENT_API_PATH, { cache: "no-store" });
      var data = await response.json().catch(function () {
        return {};
      });
      if (!response.ok) {
        var storedFallback = readStoredConsentPreference();
        applyConsentStateLocal(storedFallback.analysisEnabled === true, storedFallback.consentSet === true);
        return {
          consentSet: storedFallback.consentSet === true,
          analysisEnabled: storedFallback.analysisEnabled === true,
          localOnly: true
        };
      }
      applyConsentStateLocal(data.analysisEnabled === true, data.consentSet === true);
      return data;
    } catch (error) {
      var stored = readStoredConsentPreference();
      applyConsentStateLocal(stored.analysisEnabled === true, stored.consentSet === true);
      return {
        consentSet: stored.consentSet === true,
        analysisEnabled: stored.analysisEnabled === true,
        localOnly: true
      };
    }
  }

  async function saveConsentStatusToApi(allow) {
    try {
      var response = await fetch(CONSENT_API_PATH, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ allow: allow === true })
      });
      var data = await response.json().catch(function () {
        return {};
      });
      if (!response.ok) {
        applyConsentStateLocal(allow === true, true);
        return {
          ok: true,
          localOnly: true,
          data: {
            consentSet: true,
            analysisEnabled: allow === true,
            message: ""
          }
        };
      }
      applyConsentStateLocal(data.analysisEnabled === true, data.consentSet === true);
      return { ok: true, data: data };
    } catch (error) {
      applyConsentStateLocal(allow === true, true);
      return {
        ok: true,
        localOnly: true,
        data: {
          consentSet: true,
          analysisEnabled: allow === true,
          message: ""
        }
      };
    }
  }

  async function submitConsentChoice(allow) {
    if (consentBusy) return;
    setConsentBusy(true);
    setConsentStatusText("Saving your preference...");

    var result = await saveConsentStatusToApi(allow === true);
    if (!result || !result.ok) {
      setConsentBusy(false);
      setConsentStatusText((result && result.message) ? result.message : "Could not save your preference.");
      return;
    }

    hideConsentPrompt();
  }

  async function promptForAnalysisConsent(source, forceShow) {
    if (isGuestUser()) {
      return;
    }

    if (!isOnHome()) {
      return;
    }

    if (consentPromptOpen || consentBusy) {
      return;
    }

    var shouldForce = forceShow === true;
    if (!shouldForce) {
      var status = await fetchConsentStatusFromApi();
      if (status && status.consentSet === true) {
        return;
      }

      var stored = localStorage.getItem("zynee_data_analysis_consent");
      if (stored === "allowed" || stored === "declined") {
        return;
      }
    }

    ensureConsentUi();
    consentPromptOpen = true;
    setConsentStatusText("");
    setConsentBusy(false);
    if (consentOverlayEl) {
      consentOverlayEl.style.display = "block";
    }
    if (consentModalEl) {
      consentModalEl.style.display = "block";
    }
  }

  function clearHighlight() {
    if (highlightedEl) {
      restoreHighlightInlineStyles(highlightedEl);
      highlightedEl.classList.remove("zynee-tour-highlight");
      highlightedEl.classList.remove("zynee-tour-highlight-lit");
      highlightedEl = null;
    }
    if (highlightedContainerEl) {
      highlightedContainerEl.classList.remove("zynee-tour-highlight-container");
      highlightedContainerEl = null;
    }
  }

  function applyHighlightInlineStyles(target) {
    if (!target || !(target instanceof HTMLElement)) return;
    var computed = window.getComputedStyle(target);
    highlightedInlineStyle = {
      position: target.style.position || "",
      zIndex: target.style.zIndex || ""
    };
    if (computed.position === "static") {
      target.style.position = "relative";
    }
    target.style.zIndex = "4001";
  }

  function restoreHighlightInlineStyles(target) {
    if (!target || !(target instanceof HTMLElement) || !highlightedInlineStyle) return;

    if (highlightedInlineStyle.position) {
      target.style.position = highlightedInlineStyle.position;
    } else {
      target.style.removeProperty("position");
    }

    if (highlightedInlineStyle.zIndex) {
      target.style.zIndex = highlightedInlineStyle.zIndex;
    } else {
      target.style.removeProperty("z-index");
    }

    highlightedInlineStyle = null;
  }

  function shouldUseLightHighlight(index) {
    return LIGHT_HIGHLIGHT_START_INDEX >= 0 && index >= LIGHT_HIGHLIGHT_START_INDEX;
  }

  function applyHighlightContainer(target) {
    if (!target || !(target instanceof HTMLElement)) return;
    if (highlightedContainerEl) {
      highlightedContainerEl.classList.remove("zynee-tour-highlight-container");
      highlightedContainerEl = null;
    }

    var container = target.closest("#sidebar, #topbar, .zy-comp-root");
    if (container) {
      container.classList.add("zynee-tour-highlight-container");
      highlightedContainerEl = container;
    }
  }

  function teardownUi() {
    if (retryTimer) {
      clearTimeout(retryTimer);
      retryTimer = null;
    }

    clearHighlight();

    if (overlayEl) {
      overlayEl.style.display = "none";
    }

    if (tooltipEl) {
      tooltipEl.style.display = "none";
    }
  }

  function closeSidebar() {
    var sidebar = document.getElementById("sidebar");
    var mainContent = document.getElementById("mainContent");

    if (sidebar) {
      sidebar.classList.remove("open");
    }

    if (mainContent) {
      mainContent.style.marginLeft = "0";
      mainContent.style.width = "100%";
    }
  }

  function openSidebar() {
    var sidebar = document.getElementById("sidebar");
    var mainContent = document.getElementById("mainContent");

    if (sidebar) {
      sidebar.classList.add("open");
    }

    if (mainContent) {
      mainContent.style.marginLeft = "240px";
      mainContent.style.width = "calc(100% - 240px)";
    }
  }

  function closeThemeMenu() {
    var themeMenu = document.getElementById("themeMenu");
    if (themeMenu) {
      themeMenu.classList.remove("active");
    }
  }

  function openThemeMenu() {
    var themeMenu = document.getElementById("themeMenu");
    if (themeMenu) {
      themeMenu.classList.add("active");
    }
  }

  function collapseSearchArea() {
    var suggestions = document.getElementById("suggestionsBox");
    var mainContent = document.getElementById("mainContent");

    if (suggestions) {
      suggestions.classList.remove("show");
    }

    if (mainContent) {
      mainContent.style.marginTop = "60px";
    }
  }

  function prepareHomeForTour() {
    var welcomeBox = document.getElementById("welcomeBox");
    var blocker = document.getElementById("blocker");
    var topbar = document.getElementById("topbar");
    var mainContent = document.getElementById("mainContent");

    if (welcomeBox) {
      welcomeBox.classList.add("hidden");
    }

    if (blocker) {
      blocker.classList.add("hidden");
    }

    if (topbar) {
      topbar.classList.add("active");
    }

    if (mainContent) {
      mainContent.style.display = "block";
    }

    try {
      sessionStorage.setItem("welcomeShown", "true");
    } catch (error) {
      // Ignore storage errors.
    }

    closeSidebar();
    closeThemeMenu();
    collapseSearchArea();
    document.body.classList.remove("widgets-editing");
  }

  function setTourFastUi(enabled) {
    if (!document.body) return;
    document.body.classList.toggle("zynee-tour-fast", enabled === true);
  }

  function restoreHomeUi() {
    if (!isOnHome()) return;
    prepareHomeForTour();
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function finishTour(markComplete, askConsentAfter) {
    clearState();
    if (!isGuestUser()) {
      localStorage.removeItem(TOUR_START_REQUEST_KEY);
    }

    if (markComplete && !isGuestUser()) {
      localStorage.setItem(TOUR_COMPLETED_KEY, "true");
    }

    teardownUi();
    setTourFastUi(false);

    if (isOnHome()) {
      restoreHomeUi();
      if (askConsentAfter) {
        promptForAnalysisConsent("tour-end");
      }
      return;
    }

    if (askConsentAfter && !isGuestUser()) {
      localStorage.setItem(CONSENT_PROMPT_REQUEST_KEY, "true");
    }
    window.location.href = HOME_PATH;
  }

  function positionTooltip(target) {
    if (!tooltipEl) return;

    var rect = target.getBoundingClientRect();
    var tooltipRect = tooltipEl.getBoundingClientRect();

    var top = rect.bottom + 14;
    if (top + tooltipRect.height > window.innerHeight - 10) {
      top = rect.top - tooltipRect.height - 14;
    }

    if (top < 10) {
      top = 10;
    }

    var left = rect.left + rect.width / 2 - tooltipRect.width / 2;
    if (left < 10) left = 10;
    if (left + tooltipRect.width > window.innerWidth - 10) {
      left = window.innerWidth - tooltipRect.width - 10;
    }

    tooltipEl.style.top = top + "px";
    tooltipEl.style.left = left + "px";
  }

  function updateTooltip(step, index) {
    if (!tooltipEl) return;

    var titleNode = tooltipEl.querySelector("#zynee-tour-title");
    var textNode = tooltipEl.querySelector("#zynee-tour-text");
    var progressNode = tooltipEl.querySelector("#zynee-tour-progress");
    var backBtn = tooltipEl.querySelector('[data-tour-action="back"]');
    var nextBtn = tooltipEl.querySelector('[data-tour-action="next"]');

    if (titleNode) titleNode.textContent = step.title;
    if (textNode) textNode.textContent = step.text;
    if (progressNode) {
      progressNode.textContent = "Step " + (index + 1) + " of " + TOUR_STEPS.length;
    }

    if (backBtn) backBtn.disabled = index === 0;
    if (nextBtn) {
      nextBtn.textContent = index >= TOUR_STEPS.length - 1 ? "Finish" : "Next";
    }
  }

  function prepareForStep(step) {
    prepareHomeForTour();

    if (step.forceScrollTop) {
      window.scrollTo({ top: 0, behavior: "auto" });
    }

    if (step.closeSidebar) {
      closeSidebar();
    }

    if (step.requiresSidebar) {
      openSidebar();
    }

    if (step.openThemeMenu) {
      openThemeMenu();
    }
  }

  function moveStep(delta) {
    var state = getActiveState();
    if (!state) return;

    var nextIndex = state.index + delta;

    if (nextIndex < 0) nextIndex = 0;

    if (nextIndex >= TOUR_STEPS.length) {
      finishTour(true, true);
      return;
    }

    writeState({ active: true, index: nextIndex });
    runCurrentStep();
  }

  function handleMissingTarget(index) {
    var state = getActiveState();
    if (!state || state.index !== index) return;

    if (index >= TOUR_STEPS.length - 1) {
      finishTour(true, true);
      return;
    }

    writeState({ active: true, index: index + 1 });
    runCurrentStep();
  }

  function resolveStepTarget(step) {
    var directTarget = document.querySelector(step.selector);
    if (directTarget) return directTarget;

    if (step.selector === ".zy-comp-bubble") {
      return (
        document.querySelector(".zy-comp-root .zy-comp-bubble") ||
        document.querySelector(".zy-comp-bubble") ||
        document.querySelector('button[aria-label="Open companion chat"]')
      );
    }

    if (step.selector === "#sidebarToggleBtn") {
      return (
        document.querySelector("#sidebarToggleBtn") ||
        document.querySelector(".hamburger")
      );
    }

    return null;
  }

  function renderStep(step, index, attempt) {
    if (attempt > 22) {
      handleMissingTarget(index);
      return;
    }

    var target = resolveStepTarget(step);

    if (!target) {
      retryTimer = setTimeout(function () {
        renderStep(step, index, attempt + 1);
      }, 120);
      return;
    }

    clearHighlight();

    var computedPosition = window.getComputedStyle(target).position;
    if (computedPosition !== "fixed") {
      target.scrollIntoView({
        behavior: shouldUseLightHighlight(index) ? "auto" : "smooth",
        block: "center",
        inline: "nearest"
      });
    }

    applyHighlightInlineStyles(target);
    applyHighlightContainer(target);
    target.classList.add("zynee-tour-highlight");
    if (shouldUseLightHighlight(index)) {
      target.classList.add("zynee-tour-highlight-lit");
    }
    highlightedEl = target;

    ensureUi();
    overlayEl.style.display = "block";
    tooltipEl.style.display = "block";

    updateTooltip(step, index);
    positionTooltip(target);
  }

  function runCurrentStep() {
    var state = getActiveState();
    if (!state) {
      teardownUi();
      return;
    }

    if (!isOnHome()) {
      window.location.href = HOME_PATH;
      return;
    }

    var step = TOUR_STEPS[state.index];
    if (!step) {
      finishTour(true, true);
      return;
    }

    if (retryTimer) {
      clearTimeout(retryTimer);
      retryTimer = null;
    }

    setTourFastUi(true);
    prepareForStep(step);

    requestAnimationFrame(function () {
      renderStep(step, state.index, 0);
    });
  }

  function startTour(options) {
    var forceStart = !!(options && options.forceStart === true);
    if (FIRST_LOGIN_ONLY_MODE && !forceStart && !isFirstLoginOnboardingRequired()) {
      return;
    }

    if (!isOnHome()) {
      if (!isGuestUser()) {
        localStorage.setItem(TOUR_START_REQUEST_KEY, "true");
      }
      window.location.href = HOME_PATH;
      return;
    }

    setTourFastUi(true);
    prepareHomeForTour();
    writeState({ active: true, index: 0 });
    runCurrentStep();
  }

  function bootFromSavedState() {
    if (isGuestUser()) {
      clearGuestTourPersistence();
      return;
    }

    var requestStart = localStorage.getItem(TOUR_START_REQUEST_KEY) === "true";
    if (requestStart) {
      if (!isOnHome()) {
        window.location.href = HOME_PATH;
        return;
      }

      localStorage.removeItem(TOUR_START_REQUEST_KEY);
      startTour({ forceStart: true });
      return;
    }

    var requestConsentPrompt = localStorage.getItem(CONSENT_PROMPT_REQUEST_KEY) === "true";
    if (requestConsentPrompt && isOnHome()) {
      localStorage.removeItem(CONSENT_PROMPT_REQUEST_KEY);
      promptForAnalysisConsent("deferred");
    }

    if (getActiveState()) {
      runCurrentStep();
    }
  }

  window.ZyneeOnboardingTour = {
    start: startTour,
    stop: function () {
      finishTour(false, false);
    },
    restart: startTour,
    promptForAnalysisConsent: promptForAnalysisConsent,
    isActive: function () {
      return !!getActiveState();
    },
    setFirstLoginOnly: function (enabled) {
      FIRST_LOGIN_ONLY_MODE = !!enabled;
    },
  };

  window.addEventListener("resize", function () {
    if (highlightedEl && tooltipEl && tooltipEl.style.display === "block") {
      positionTooltip(highlightedEl);
    }
  });

  window.addEventListener(
    "scroll",
    function () {
      if (highlightedEl && tooltipEl && tooltipEl.style.display === "block") {
        positionTooltip(highlightedEl);
      }
    },
    true
  );

  document.addEventListener("DOMContentLoaded", bootFromSavedState);
})();
