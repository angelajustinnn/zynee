// shared.js

const suggestionsData = [
  "Journal Entry", "Mood: Happy", "Mood: Anxious", "Profile Settings",
  "About Zyneé", "Theme Settings", "Music Player", "Candles", "Mood Tracker"
];

function toggleSidebar() {
  const sidebar = document.getElementById("sidebar");
  const mainContent = document.getElementById("mainContent");
  const themeMenu = document.getElementById("themeMenu");

  if (!sidebar.classList.contains("open")) {
    closeAllExcept('sidebar');
    sidebar.classList.add("open");
    if (mainContent) {
      mainContent.style.display = "block";
      mainContent.style.marginLeft = "calc(240px + 0.1cm)";
      mainContent.style.width = "calc(100% - 240px - 0.1cm)";
    }
  } else {
    sidebar.classList.remove("open");
    themeMenu.classList.remove("active");
    if (mainContent) {
      mainContent.style.marginLeft = "0";
      mainContent.style.width = "100%";
    }
  }
}

function expandSearch() {
  closeAllExcept('search');
  const suggestionsBox = document.getElementById("suggestionsBox");
  const mainContent = document.getElementById("mainContent");
  if (suggestionsBox) suggestionsBox.classList.add("show");
  if (mainContent) mainContent.style.marginTop = "calc(60px + 0.1cm + 3.2rem)";
}

function shrinkSearch() {
  setTimeout(() => {
    const suggestionsBox = document.getElementById("suggestionsBox");
    const mainContent = document.getElementById("mainContent");
    if (suggestionsBox) suggestionsBox.classList.remove("show");
    if (mainContent) mainContent.style.marginTop = "calc(60px + 0.1cm)";
  }, 150);
}

function toggleProfile() {
  closeAllExcept('profile');
  alert("Profile settings coming soon!");
}

function showSuggestions(value) {
  const box = document.getElementById("suggestionsBox");
  const filtered = suggestionsData.filter(item => item.toLowerCase().includes(value.toLowerCase()));
  if (box) {
    box.innerHTML = filtered.length
      ? filtered.map(item => `<div onclick="alert('Navigate to: ${item}')">${item}</div>`).join("")
      : "<div>No matches found</div>";
  }
}

function closeAllExcept(active) {
  const sidebar = document.getElementById("sidebar");
  const suggestionsBox = document.getElementById("suggestionsBox");
  const mainContent = document.getElementById("mainContent");
  const themeMenu = document.getElementById("themeMenu");

  if (active !== 'sidebar' && sidebar) {
    sidebar.classList.remove("open");
    if (mainContent) {
      mainContent.style.marginLeft = "0";
      mainContent.style.width = "100%";
    }
  }
  if (active !== 'search' && suggestionsBox) {
    suggestionsBox.classList.remove("show");
    if (mainContent) mainContent.style.marginTop = "calc(60px + 0.1cm)";
  }

  if (themeMenu) themeMenu.classList.remove("active");
}

function toggleThemeMenu() {
  const themeMenu = document.getElementById("themeMenu");
  if (themeMenu) themeMenu.classList.toggle("active");
}

let selectedTheme = localStorage.getItem("zyneeTheme") || 'default';
let savedHue = localStorage.getItem("zyneeHue") || '270';

document.addEventListener("DOMContentLoaded", () => {
  document.body.classList.add(`${selectedTheme}-mode`);
  applyTheme(selectedTheme);
  if (selectedTheme === 'custom') {
    const hueSlider = document.getElementById("hueSlider");
    if (hueSlider) {
      hueSlider.value = savedHue;
      updateHue(savedHue);
    }
  }

  const welcomeBox = document.getElementById("welcomeBox");
  const blocker = document.getElementById("blocker");
  const topbar = document.getElementById("topbar");
  const mainContent = document.getElementById("mainContent");

  if (welcomeBox && blocker && topbar && mainContent) {
    welcomeBox.classList.remove("hidden");
    blocker.classList.remove("hidden");
    topbar.classList.remove("active");
    mainContent.style.display = "none";

    const closeBtn = welcomeBox.querySelector(".close-btn");
    if (closeBtn) {
      closeBtn.addEventListener("click", () => {
        welcomeBox.classList.add("hidden");
        blocker.classList.add("hidden");
        topbar.classList.add("active");
        mainContent.style.display = "block";
      });
    }
  }
});

function applyTheme(mode) {
  selectedTheme = mode;
  document.querySelectorAll(".theme-option").forEach(option => option.classList.remove("selected"));
  const selected = Array.from(document.querySelectorAll(".theme-option")).find(opt =>
    opt.textContent.trim().toLowerCase().includes(mode)
  );
  if (selected) selected.classList.add("selected");

  const hueContainer = document.getElementById("hueSliderContainer");

  if (mode === 'custom') {
    if (hueContainer) hueContainer.style.display = "block";
    const hue = document.getElementById("hueSlider").value;
    updateHue(hue);
  } else {
    if (hueContainer) hueContainer.style.display = "none";
    applyStaticTheme(mode);
  }

  localStorage.setItem("zyneeTheme", mode);
}

function applyStaticTheme(mode) {
  document.body.classList.remove('default-mode', 'light-mode', 'dark-mode');
  document.body.classList.add(`${mode}-mode`);

  const elements = document.querySelectorAll(".card, .sidebar, .top-bar, .welcome-box");

  if (mode === 'default') {
    document.body.style.background = 'linear-gradient(135deg, #ffe9ec, #f7ddff, #d8eaff, #fff0f5)';
    elements.forEach(el => el.style.backgroundColor = '');
    document.documentElement.style.setProperty('--card-text-color', '#1a1a1a');
  } else if (mode === 'light') {
    document.body.style.background = '#ffffff';
    elements.forEach(el => el.style.backgroundColor = '#ffffff');
    document.documentElement.style.setProperty('--card-text-color', '#1a1a1a');
  } else if (mode === 'dark') {
    document.body.style.background = '#1c1c1e';
    elements.forEach(el => el.style.backgroundColor = '#2c2c2e');
    document.documentElement.style.setProperty('--card-text-color', '#ffffff');
  }
}

function updateHue(hueValue) {
  if (selectedTheme !== 'custom') return;
  const h = parseInt(hueValue);
  document.body.style.background = `hsl(${h}, 70%, 90%)`;
  const elements = document.querySelectorAll(".card, .sidebar, .top-bar, .welcome-box");
  elements.forEach(el => {
    el.style.backgroundColor = `hsl(${h}, 40%, 80%)`;
    el.style.color = `hsl(${h}, 20%, 90%)`;
  });
  document.documentElement.style.setProperty('--card-text-color', `hsl(${h}, 30%, 10%)`);
  localStorage.setItem("zyneeHue", hueValue);
}
