/* engines.js — общие модули для расписаний */

/* ===== ЧАСЫ И ОТСЧЁТ ===== */

function parseTime(t) {
  const [h, m] = t.split("–")[0].split(":").map(Number);
  return h * 60 + m;
}

function getTodayIndex() {
  const d = new Date().getDay();
  return d === 0 ? 6 : d - 1;
}

function formatClock() {
  const now = new Date();
  const dateStr = now.toLocaleDateString("ru-RU", { weekday: "long", day: "numeric", month: "long" });
  const timeStr = now.toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
  return `${dateStr} · ${timeStr}`;
}

function countdownSec(targetMin) {
  const now = new Date();
  const curSec = now.getHours() * 3600 + now.getMinutes() * 60 + now.getSeconds();
  const targetSec = targetMin * 60;
  const diff = targetSec - curSec;
  if (diff <= 0) return "";
  const h = Math.floor(diff / 3600);
  const m = Math.floor((diff % 3600) / 60);
  const s = diff % 60;
  if (h > 0) return `через ${h} ч ${m} мин`;
  if (m > 0) return `через ${m} мин ${s} сек`;
  return `через ${s} сек`;
}

function remainingSec(endMin) {
  const now = new Date();
  const curSec = now.getHours() * 3600 + now.getMinutes() * 60 + now.getSeconds();
  const targetSec = endMin * 60;
  const diff = targetSec - curSec;
  if (diff <= 0) return "";
  const h = Math.floor(diff / 3600);
  const m = Math.floor((diff % 3600) / 60);
  const s = diff % 60;
  if (h > 0) return `ещё ${h} ч ${m} мин`;
  if (m > 0) return `ещё ${m} мин ${s} сек`;
  return `ещё ${s} сек`;
}

function updateClocks() {
  document.querySelectorAll("[data-clock]").forEach(el => {
    el.textContent = formatClock();
  });
}

function updateCountdowns() {
  document.querySelectorAll("[data-cd]").forEach(el => {
    const target = parseInt(el.getAttribute("data-cd"));
    el.textContent = countdownSec(target);
  });
  document.querySelectorAll("[data-cd-end]").forEach(el => {
    const target = parseInt(el.getAttribute("data-cd-end"));
    el.textContent = remainingSec(target);
  });
  updateClocks();
  updateProgressBars();
}

/* ===== ПРОГРЕСС-БАР НА КАРТОЧКАХ ===== */

function updateProgressBars() {
  document.querySelectorAll("[data-progress]").forEach(el => {
    const start = parseInt(el.getAttribute("data-progress"));
    const end = parseInt(el.getAttribute("data-end"));
    const now = new Date();
    const cur = now.getHours() * 3600 + now.getMinutes() * 60 + now.getSeconds();
    const pct = Math.max(0, Math.min(100, ((cur - start * 60) / ((end - start) * 60)) * 100));
    el.style.setProperty("--progress", pct + "%");
  });
}

/* ===== СОСТОЯНИЕ КАРТОЧКИ ===== */

function getCardState(dayIdx, time) {
  const todayIdx = getTodayIndex();
  if (dayIdx < todayIdx) return "past";
  if (dayIdx > todayIdx) return "future";
  const cur = new Date().getHours() * 60 + new Date().getMinutes();
  const s = parseTime(time);
  const e = parseTime(time.split("–")[1]);
  if (cur >= s && cur < e) return "current";
  if (cur >= e) return "past";
  if (cur < s && (s - cur) <= 120) return "next";
  return "future";
}

/* ===== РЕНДЕР КАРТОЧКИ УРОКА ===== */

function renderLessonCard(item, extraClass) {
  const state = getCardState(item._dayIdx, item.time);
  const cls = `${extraClass || "lesson"} ${state}`;
  const startTime = parseTime(item.time);
  const endTime = parseTime(item.time.split("–")[1]);
  const progressAttr = state === "current" ? `data-progress="${startTime}" data-end="${endTime}"` : "";
  const cdAttr = state === "next" ? `data-cd="${startTime}"` : state === "current" ? `data-cd-end="${endTime}"` : "";
  const cdText = state === "next" ? countdownSec(startTime) : state === "current" ? remainingSec(endTime) : "";
  const num = item.n != null ? item.n : "⭐";
  return `
    <div class="${cls}" ${progressAttr}>
      <div class="lesson-body">
        <div class="lesson-icon">${item.icon}</div>
        <div class="lesson-num">${num}</div>
        <div class="lesson-info">
          <div class="lesson-time">${item.time}</div>
          <div class="lesson-subject">${item.subj}</div>
          ${cdAttr ? `<div class="lesson-countdown" ${cdAttr}>${cdText}</div>` : ""}
        </div>
      </div>
    </div>`;
}

/* ===== ОБНОВЛЕНИЕ СОСТОЯНИЯ КАРТОЧЕК LIVE ===== */

function updateCardStates() {
  const now = new Date();
  const cur = now.getHours() * 60 + now.getMinutes();
  const todayIdx = getTodayIndex();

  document.querySelectorAll("[data-start]").forEach(el => {
    const dayIdx = parseInt(el.getAttribute("data-day"));
    const startTime = parseInt(el.getAttribute("data-start"));
    const endTime = parseInt(el.getAttribute("data-end"));

    let newState;
    if (dayIdx < todayIdx) newState = "past";
    else if (dayIdx > todayIdx) newState = "future";
    else if (cur >= startTime && cur < endTime) newState = "current";
    else if (cur >= endTime) newState = "past";
    else if (cur < startTime && (startTime - cur) <= 120) newState = "next";
    else newState = "future";

    const prevState = el.getAttribute("data-state");
    const hasProgress = el.hasAttribute("data-progress");
    if (prevState === newState && (newState !== "current" || hasProgress)) return;
    el.setAttribute("data-state", newState);

    const base = el.className.replace(/\b(past|current|next|future)\b/g, "").trim();
    el.className = base + " " + newState;

    const info = el.querySelector(".lesson-info, .merge-info");
    if (!info) return;
    let cdEl = info.querySelector(".lesson-countdown, .merge-countdown");

    if (newState === "current") {
      el.setAttribute("data-progress", startTime);
      el.setAttribute("data-end", endTime);
      const now = new Date();
      const curMin = now.getHours() * 60 + now.getMinutes();
      const pct = Math.max(0, Math.min(100, ((curMin - startTime) / (endTime - startTime)) * 100));
      el.style.setProperty("--progress", pct + "%");
      if (!cdEl) {
        cdEl = document.createElement("div");
        cdEl.className = el.classList.contains("merge-card") || el.classList.contains("merge-row") ? "merge-countdown" : "lesson-countdown";
        info.appendChild(cdEl);
      }
      cdEl.setAttribute("data-cd-end", endTime);
      cdEl.removeAttribute("data-cd");
      cdEl.textContent = remainingSec(endTime);
    } else if (newState === "next") {
      el.removeAttribute("data-progress");
      el.style.removeProperty("--progress");
      if (!cdEl) {
        cdEl = document.createElement("div");
        cdEl.className = el.classList.contains("merge-card") || el.classList.contains("merge-row") ? "merge-countdown" : "lesson-countdown";
        info.appendChild(cdEl);
      }
      cdEl.setAttribute("data-cd", startTime);
      cdEl.removeAttribute("data-cd-end");
      cdEl.textContent = countdownSec(startTime);
    } else {
      el.removeAttribute("data-progress");
      el.style.removeProperty("--progress");
      if (cdEl) {
        cdEl.removeAttribute("data-cd");
        cdEl.removeAttribute("data-cd-end");
        cdEl.textContent = "";
      }
    }
  });
}

/* ===== ЗАПУСК ДВИЖКА ===== */

function startEngines(onTick) {
  updateClocks();
  updateCountdowns();
  if (onTick) onTick();
  setInterval(() => { updateCountdowns(); updateCardStates(); if (onTick) onTick(); }, 1000);
}
