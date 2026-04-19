(function () {
  "use strict";

  function isGuestUser() {
    return window.ZYNEE_IS_GUEST === true;
  }

  if (!isGuestUser()) {
    return;
  }

  var THEME_MODE_KEY = "zyneeTheme";
  var THEME_HUE_KEY = "zyneeHue";
  var GUEST_THEME_MODE_SESSION_KEY = "zynee_guest_session_theme";
  var GUEST_THEME_HUE_SESSION_KEY = "zynee_guest_session_hue";
  var BLOCKED_STORAGE_EXACT = {
    zyneeTourStateV2: true,
    zyneeTourCompletedV2: true,
    zyneeTourStartRequestedV2: true,
    zyneeConsentPromptRequestedV1: true,
    zynee_data_analysis_consent: true
  };
  var BLOCKED_STORAGE_PREFIX = [
    "zynee_home_widgets::",
    "zynee-home-daily-v1:"
  ];
  var UPGRADE_DEFAULT_MESSAGE = "Guest mode is view-only. Log in or create an account to unlock full access.";
  var SAVE_BANNER_MESSAGE = "Please log in or create an account to save your data.";
  var PROFILE_BANNER_MESSAGE = "Please log in or create an account to edit your profile settings.";
  var JOURNAL_PIN_BANNER_MESSAGE = "Please log in or create an account to use Journal PIN.";
  var FUTURE_NOTE_BANNER_MESSAGE = "Please log in or create an account to add or edit future notes.";
  var QUICK_CHECKIN_BANNER_MESSAGE = "Please log in or create an account to submit quick check-ins.";
  var MOOD_BANNER_MESSAGE = "Please log in or create an account to save mood entries.";
  var AFFIRMATION_BANNER_MESSAGE = "Please log in or create an account to save affirmations.";
  var ASSISTANT_BANNER_MESSAGE = "Please log in or create an account to use the Emotional Assistant.";
  var ALLOWED_MUTATION_PATHS = {
    "/logout": true,
    "/guest-access": true,
    "/login": true,
    "/login.html": true,
    "/forgot-password": true,
    "/api/emotional-assistant/chat": true
  };

  var upgradeOverlayEl = null;
  var upgradeMessageEl = null;

  function isBlockedStorageKey(key) {
    var value = String(key || "");
    if (!value) return false;
    if (BLOCKED_STORAGE_EXACT[value]) return true;
    for (var i = 0; i < BLOCKED_STORAGE_PREFIX.length; i += 1) {
      if (value.indexOf(BLOCKED_STORAGE_PREFIX[i]) === 0) return true;
    }
    return false;
  }

  function shouldBlockStorageAccess(key) {
    var value = String(key || "");
    return value.length > 0;
  }

  function readGuestSessionTheme() {
    var mode = "default";
    var hue = "270";
    try {
      var savedMode = sessionStorage.getItem(GUEST_THEME_MODE_SESSION_KEY);
      var savedHue = sessionStorage.getItem(GUEST_THEME_HUE_SESSION_KEY);
      if (savedMode && typeof savedMode === "string") mode = savedMode;
      if (savedHue && typeof savedHue === "string") hue = savedHue;
    } catch (error) {
      // ignore session storage errors
    }
    return { mode: mode, hue: hue };
  }

  function writeGuestSessionTheme(mode, hue) {
    try {
      if (mode != null) {
        sessionStorage.setItem(GUEST_THEME_MODE_SESSION_KEY, String(mode));
      }
      if (hue != null) {
        sessionStorage.setItem(GUEST_THEME_HUE_SESSION_KEY, String(hue));
      }
    } catch (error) {
      // ignore session storage errors
    }
  }

  function isReadMethod(method) {
    var value = String(method || "GET").toUpperCase();
    return value === "GET" || value === "HEAD" || value === "OPTIONS";
  }

  function isAllowedMutationPath(pathname) {
    var value = String(pathname || "").toLowerCase();
    return ALLOWED_MUTATION_PATHS[value] === true;
  }

  function patchStorageForGuest() {
    if (!window.Storage || window.__zyneeGuestStoragePatched === true) {
      return;
    }

    var proto = window.Storage.prototype;
    var originalGetItem = proto.getItem;
    var originalSetItem = proto.setItem;
    var originalRemoveItem = proto.removeItem;

    proto.getItem = function (key) {
      if (isGuestUser() && this === window.localStorage) {
        if (key === THEME_MODE_KEY) return readGuestSessionTheme().mode;
        if (key === THEME_HUE_KEY) return readGuestSessionTheme().hue;
        if (shouldBlockStorageAccess(key)) return null;
      }
      return originalGetItem.call(this, key);
    };

    proto.setItem = function (key, value) {
      if (isGuestUser() && this === window.localStorage) {
        if (key === THEME_MODE_KEY) {
          writeGuestSessionTheme(value, null);
          return;
        }
        if (key === THEME_HUE_KEY) {
          writeGuestSessionTheme(null, value);
          return;
        }
        if (shouldBlockStorageAccess(key)) return;
      }
      return originalSetItem.call(this, key, value);
    };

    proto.removeItem = function (key) {
      if (isGuestUser() && this === window.localStorage) {
        if (key === THEME_MODE_KEY) {
          try {
            sessionStorage.removeItem(GUEST_THEME_MODE_SESSION_KEY);
          } catch (error) {
            // ignore session storage errors
          }
          return;
        }
        if (key === THEME_HUE_KEY) {
          try {
            sessionStorage.removeItem(GUEST_THEME_HUE_SESSION_KEY);
          } catch (error) {
            // ignore session storage errors
          }
          return;
        }
        if (shouldBlockStorageAccess(key)) return;
      }
      return originalRemoveItem.call(this, key);
    };

    window.__zyneeGuestStoragePatched = true;
  }

  function clearGuestPersistence() {
    try {
      var localKeys = Object.keys(localStorage);
      localKeys.forEach(function (key) {
        if (isBlockedStorageKey(key)) {
          localStorage.removeItem(key);
        }
      });
    } catch (error) {
      // ignore storage issues
    }
  }

  function applyGuestSessionTheme() {
    var sessionTheme = readGuestSessionTheme();
    try {
      if (typeof window.applyTheme === "function") {
        window.applyTheme(sessionTheme.mode || "default");
      } else if (document.body) {
        document.body.classList.remove("light-mode", "dark-mode", "custom-mode");
        document.body.classList.add((sessionTheme.mode || "default") + "-mode");
      }
      document.documentElement.style.setProperty("--theme-hue", sessionTheme.hue || "270");
      var hueSlider = document.getElementById("hueSlider");
      if (hueSlider) hueSlider.value = sessionTheme.hue || "270";
      if ((sessionTheme.mode || "default") === "custom" && typeof window.updateHue === "function") {
        window.updateHue(sessionTheme.hue || "270");
      }
    } catch (error) {
      // ignore theme reset issues
    }
  }

  function ensureUpgradeUi() {
    if (upgradeOverlayEl) return;

    if (!document.getElementById("zynee-guest-upgrade-style")) {
      var style = document.createElement("style");
      style.id = "zynee-guest-upgrade-style";
      style.textContent =
        ".zynee-guest-upgrade-overlay { position: fixed; inset: 0; display: none; align-items: center; justify-content: center; padding: 16px; z-index: 5200; background: rgba(255,255,255,0.14); backdrop-filter: blur(10px); -webkit-backdrop-filter: blur(10px); }" +
        ".zynee-guest-upgrade-overlay.show { display: flex; }" +
        ".zynee-guest-upgrade-card { width: min(420px, 94vw); border-radius: 26px; background: rgba(255,255,255,0.96); box-shadow: 0 20px 42px rgba(54, 36, 84, 0.22); border: 1px solid rgba(226, 196, 250, 0.75); padding: 20px 20px 18px; color: #533f71; font-family: 'Segoe UI', sans-serif; position: relative; }" +
        ".zynee-guest-upgrade-close { position: absolute; top: 12px; right: 14px; border: none; background: transparent; color: #8e76ad; font-size: 1.4rem; line-height: 1; cursor: pointer; }" +
        ".zynee-guest-upgrade-title { font-size: 1.06rem; font-weight: 700; color: #5f4284; margin-bottom: 8px; }" +
        ".zynee-guest-upgrade-msg { font-size: 0.92rem; line-height: 1.46; color: #6d568a; margin-bottom: 14px; }" +
        ".zynee-guest-upgrade-actions { display: flex; gap: 8px; justify-content: flex-end; }" +
        ".zynee-guest-upgrade-btn { border: none; border-radius: 999px; padding: 8px 13px; font-size: 0.86rem; text-decoration: none; cursor: pointer; }" +
        ".zynee-guest-upgrade-btn.login { background: #f3ebfb; color: #5f3f86; }" +
        ".zynee-guest-upgrade-btn.signup { background: linear-gradient(135deg, #8f63ba 0%, #6d4f99 100%); color: #fff; }";
      document.head.appendChild(style);
    }

    upgradeOverlayEl = document.createElement("div");
    upgradeOverlayEl.className = "zynee-guest-upgrade-overlay";
    upgradeOverlayEl.innerHTML =
      '<div class="zynee-guest-upgrade-card">' +
      '  <button type="button" class="zynee-guest-upgrade-close" aria-label="Close">×</button>' +
      '  <div class="zynee-guest-upgrade-title">Guest Access</div>' +
      '  <div class="zynee-guest-upgrade-msg"></div>' +
      '  <div class="zynee-guest-upgrade-actions">' +
      '    <a href="/login.html" class="zynee-guest-upgrade-btn login">Log In</a>' +
      '    <a href="/signup.html" class="zynee-guest-upgrade-btn signup">Create Account</a>' +
      "  </div>" +
      "</div>";

    upgradeMessageEl = upgradeOverlayEl.querySelector(".zynee-guest-upgrade-msg");
    var closeBtn = upgradeOverlayEl.querySelector(".zynee-guest-upgrade-close");
    if (closeBtn) {
      closeBtn.addEventListener("click", function () {
        hideUpgradeBanner();
      });
    }
    upgradeOverlayEl.addEventListener("click", function (event) {
      if (event.target === upgradeOverlayEl) {
        hideUpgradeBanner();
      }
    });

    document.body.appendChild(upgradeOverlayEl);
  }

  function showUpgradeBanner(message) {
    ensureUpgradeUi();
    if (!upgradeOverlayEl) return;
    if (upgradeMessageEl) {
      upgradeMessageEl.textContent = message || UPGRADE_DEFAULT_MESSAGE;
    }
    upgradeOverlayEl.classList.add("show");
  }

  function hideUpgradeBanner() {
    if (upgradeOverlayEl) {
      upgradeOverlayEl.classList.remove("show");
    }
  }

  function blockGuestAction(message) {
    showUpgradeBanner(message || UPGRADE_DEFAULT_MESSAGE);
  }

  function stopEvent(event) {
    if (!event) return;
    if (typeof event.preventDefault === "function") event.preventDefault();
    if (typeof event.stopPropagation === "function") event.stopPropagation();
    if (typeof event.stopImmediatePropagation === "function") event.stopImmediatePropagation();
  }

  function addClickInterceptor(selector, message, shouldBlock) {
    if (!selector) return;
    document.addEventListener(
      "click",
      function (event) {
        if (!isGuestUser()) return;
        var target = event.target;
        if (!target || typeof target.closest !== "function") return;
        var matched = target.closest(selector);
        if (!matched) return;
        if (typeof shouldBlock === "function" && shouldBlock(matched, event) !== true) {
          return;
        }
        stopEvent(event);
        blockGuestAction(message);
      },
      true
    );
  }

  function overrideGlobalFunction(name, message, returnPromise) {
    if (!name || typeof window[name] !== "function") return;
    var original = window[name];
    if (original.__zyneeGuestWrapped === true) return;

    var wrapped = function () {
      if (arguments && arguments[0] && typeof arguments[0].preventDefault === "function") {
        try {
          arguments[0].preventDefault();
        } catch (error) {
          // ignore event errors
        }
      }
      blockGuestAction(message);
      if (returnPromise === true) {
        return Promise.resolve({ guestBlocked: true });
      }
      return false;
    };

    wrapped.__zyneeGuestWrapped = true;
    wrapped.__zyneeGuestOriginal = original;
    window[name] = wrapped;
  }

  function lockProfileFieldsForGuest() {
    var profileForm = document.querySelector("form[action='update-profile'], form[action='/update-profile']");
    if (!profileForm) return;

    var readOnlySelectors = [
      "input[name='name']",
      "input[name='phone']"
    ];
    var disabledSelectors = [
      "select[name='countryCode']",
      "input[name='dob']",
      "select[name='gender']",
      "#profileFileInput"
    ];

    readOnlySelectors.forEach(function (selector) {
      var input = profileForm.querySelector(selector);
      if (!input) return;
      input.readOnly = true;
      input.setAttribute("aria-disabled", "true");
    });

    disabledSelectors.forEach(function (selector) {
      var input = profileForm.querySelector(selector) || document.querySelector(selector);
      if (!input) return;
      input.disabled = true;
      input.setAttribute("aria-disabled", "true");
    });
  }

  function isProfilePage(pathname) {
    return pathname.indexOf("/profile") !== -1;
  }

  function isJournalPage(pathname) {
    return pathname.indexOf("/journal") !== -1;
  }

  function isMoodPage(pathname) {
    return pathname.indexOf("/mood") !== -1;
  }

  function isQuickCheckInPage(pathname) {
    return pathname.indexOf("/quick-checkin") !== -1;
  }

  function isFutureNotesPage(pathname) {
    return pathname.indexOf("/future-notes") !== -1;
  }

  function isAffirmationPage(pathname) {
    return pathname.indexOf("/affirmation") !== -1;
  }

  function isAssistantPage(pathname) {
    return pathname.indexOf("/ai-assistant") !== -1;
  }

  function installProfileGuards() {
    lockProfileFieldsForGuest();

    addClickInterceptor(
      "button.btn-save[type='submit']",
      PROFILE_BANNER_MESSAGE
    );
    addClickInterceptor(
      "button[onclick*='openEditPhotoModal'], button[onclick*='uploadCroppedPhoto'], button[onclick*='removePreview']",
      PROFILE_BANNER_MESSAGE
    );
    addClickInterceptor(
      "#analysisConsentToggleBtn",
      PROFILE_BANNER_MESSAGE
    );
    addClickInterceptor(
      "#otherSettings .list-group-item",
      PROFILE_BANNER_MESSAGE,
      function (item) {
        var label = String(item.textContent || "").toLowerCase();
        return label.indexOf("log out") === -1;
      }
    );

    overrideGlobalFunction("openEditPhotoModal", PROFILE_BANNER_MESSAGE);
    overrideGlobalFunction("uploadCroppedPhoto", PROFILE_BANNER_MESSAGE);
    overrideGlobalFunction("removePreview", PROFILE_BANNER_MESSAGE);
    overrideGlobalFunction("exportData", PROFILE_BANNER_MESSAGE);
    overrideGlobalFunction("toggleDataAnalysisPermission", PROFILE_BANNER_MESSAGE);
    overrideGlobalFunction("takeTourAgain", PROFILE_BANNER_MESSAGE);
    overrideGlobalFunction("showChangePasswordBanner", PROFILE_BANNER_MESSAGE);
    overrideGlobalFunction("showDeleteDataBanner", PROFILE_BANNER_MESSAGE);
    overrideGlobalFunction("showDeleteAccountBanner", PROFILE_BANNER_MESSAGE);
    overrideGlobalFunction("confirmDeleteData", PROFILE_BANNER_MESSAGE, true);
    overrideGlobalFunction("confirmDeleteAccount", PROFILE_BANNER_MESSAGE, true);
    overrideGlobalFunction("submitPasswordChange", PROFILE_BANNER_MESSAGE, true);
  }

  function installJournalGuards() {
    addClickInterceptor("button[onclick*='saveJournal']", SAVE_BANNER_MESSAGE);
    addClickInterceptor("#journalLockButton", JOURNAL_PIN_BANNER_MESSAGE);
    addClickInterceptor("#journalPinOverlay .pin-btn", JOURNAL_PIN_BANNER_MESSAGE);

    overrideGlobalFunction("saveJournal", SAVE_BANNER_MESSAGE);
    overrideGlobalFunction("openJournalPinManager", JOURNAL_PIN_BANNER_MESSAGE, true);
    overrideGlobalFunction("submitPinSetup", JOURNAL_PIN_BANNER_MESSAGE, true);
    overrideGlobalFunction("submitPinVerification", JOURNAL_PIN_BANNER_MESSAGE, true);
    overrideGlobalFunction("submitPinChange", JOURNAL_PIN_BANNER_MESSAGE, true);
    overrideGlobalFunction("submitPinRemove", JOURNAL_PIN_BANNER_MESSAGE, true);
  }

  function installMoodGuards() {
    addClickInterceptor("button[onclick*='saveMoodToDB']", MOOD_BANNER_MESSAGE);
    overrideGlobalFunction("saveMoodToDB", MOOD_BANNER_MESSAGE, true);
  }

  function installQuickCheckInGuards() {
    overrideGlobalFunction("saveCheckIn", QUICK_CHECKIN_BANNER_MESSAGE, true);
  }

  function installFutureNotesGuards() {
    addClickInterceptor("#sealPreviewBtn, #confirmSealBtn, button[onclick*='deleteFutureNote']", FUTURE_NOTE_BANNER_MESSAGE);
    overrideGlobalFunction("prepareSealFutureNote", FUTURE_NOTE_BANNER_MESSAGE);
    overrideGlobalFunction("sealFutureNote", FUTURE_NOTE_BANNER_MESSAGE, true);
    overrideGlobalFunction("deleteFutureNote", FUTURE_NOTE_BANNER_MESSAGE, true);
  }

  function installAffirmationGuards() {
    addClickInterceptor("#saveButton, button[onclick*='saveAffirmation']", AFFIRMATION_BANNER_MESSAGE);
    overrideGlobalFunction("saveAffirmation", AFFIRMATION_BANNER_MESSAGE, true);
  }

  function installAssistantGuards() {
    addClickInterceptor("#sendButton, button[onclick*='sendMessage']", ASSISTANT_BANNER_MESSAGE);
    overrideGlobalFunction("sendMessage", ASSISTANT_BANNER_MESSAGE, true);

    var input = document.getElementById("userInput");
    if (input) {
      input.addEventListener(
        "keydown",
        function (event) {
          if (!isGuestUser()) return;
          if (event.key !== "Enter") return;
          stopEvent(event);
          blockGuestAction(ASSISTANT_BANNER_MESSAGE);
        },
        true
      );
    }
  }

  function installPageSpecificGuards() {
    var pathname = String(window.location.pathname || "").toLowerCase();
    if (!pathname) return;

    if (isProfilePage(pathname)) installProfileGuards();
    if (isJournalPage(pathname)) installJournalGuards();
    if (isMoodPage(pathname)) installMoodGuards();
    if (isQuickCheckInPage(pathname)) installQuickCheckInGuards();
    if (isFutureNotesPage(pathname)) installFutureNotesGuards();
    if (isAffirmationPage(pathname)) installAffirmationGuards();
    if (isAssistantPage(pathname)) installAssistantGuards();
  }

  function patchFetchForGuest() {
    if (typeof window.fetch !== "function" || window.__zyneeGuestFetchPatched === true) {
      return;
    }

    var originalFetch = window.fetch.bind(window);

    window.fetch = function (input, init) {
      if (!isGuestUser()) {
        return originalFetch(input, init);
      }

      var method = "GET";
      if (init && init.method) {
        method = init.method;
      } else if (input && typeof input === "object" && input.method) {
        method = input.method;
      }

      var urlValue = "";
      if (typeof input === "string") {
        urlValue = input;
      } else if (input && typeof input === "object" && input.url) {
        urlValue = input.url;
      }

      var requestUrl;
      try {
        requestUrl = new URL(urlValue || window.location.href, window.location.href);
      } catch (error) {
        return originalFetch(input, init);
      }

      if (requestUrl.origin !== window.location.origin || isReadMethod(method) || isAllowedMutationPath(requestUrl.pathname)) {
        return originalFetch(input, init);
      }

      blockGuestAction();
      return Promise.resolve(
        new Response(
          JSON.stringify({ guestBlocked: true, message: "Guest mode is view-only." }),
          { status: 403, headers: { "Content-Type": "application/json" } }
        )
      );
    };

    window.__zyneeGuestFetchPatched = true;
  }

  function guardFormsForGuest() {
    document.addEventListener(
      "submit",
      function (event) {
        if (!isGuestUser()) return;
        var form = event.target;
        if (!(form instanceof HTMLFormElement)) return;

        var method = String(form.method || "GET").toUpperCase();
        if (isReadMethod(method)) return;

        var action = form.getAttribute("action") || window.location.pathname;
        var formUrl;
        try {
          formUrl = new URL(action, window.location.href);
        } catch (error) {
          return;
        }

        if (formUrl.origin !== window.location.origin || isAllowedMutationPath(formUrl.pathname)) {
          return;
        }

        event.preventDefault();
        blockGuestAction();
      },
      true
    );
  }

  patchStorageForGuest();
  clearGuestPersistence();
  patchFetchForGuest();
  guardFormsForGuest();

  function initializeGuestPage() {
    clearGuestPersistence();
    applyGuestSessionTheme();
    installPageSpecificGuards();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", function () {
      initializeGuestPage();
    });
  } else {
    initializeGuestPage();
  }
})();
