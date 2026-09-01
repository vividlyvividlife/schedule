const SAT_MESSAGES = [
  { emoji: "🎉", text: "Наконец-то выходной!", anim: "float" },
  { emoji: "🍕", text: "Суббота! Можно ничего не делать", anim: "" },
  { emoji: "🎮", text: "Свободный день — играем!", anim: "" },
  { emoji: "☀️", text: "Выходной и солнце — идеально", anim: "pulse" },
  { emoji: "🧸", text: "Суббота — день приключений", anim: "" },
  { emoji: "🍦", text: "Выходной = мороженое", anim: "" },
  { emoji: "📚", text: "Можно почитать что хочется!", anim: "" },
  { emoji: "⚽", text: "Суббота — лучший день для игр", anim: "float" },
  { emoji: "🎨", text: "Рисуем, лепим, творим!", anim: "" },
  { emoji: "🌳", text: "Выходной — гуляем на свежем воздухе", anim: "" },
  { emoji: "😴", text: "Спим сколько хочется!", anim: "" },
  { emoji: "🎪", text: "Суббота — как маленький праздник", anim: "pulse" },
  { emoji: "🐕", text: "Выходной — время для любимых дел", anim: "" },
  { emoji: "🧹", text: "Суббота: сначала убираемся, потом веселимся", anim: "" },
  { emoji: "🎯", text: "День для своих планов!", anim: "" },
  { emoji: "🌈", text: "Выходной — мир прекрасен", anim: "float" },
  { emoji: "🍳", text: "Субботнее утро = завтрак от шефа", anim: "" },
  { emoji: "🚴", text: "Катаемся, бегаем, прыгаем!", anim: "" },
  { emoji: "🎵", text: "Суббота — день музыки и танцев", anim: "pulse" },
  { emoji: "🧩", text: "Собираем пазлы и играем в настолки", anim: "" },
];

const SUN_MESSAGES = [
  { emoji: "😱", text: "Ой, завтра понедельник...", anim: "" },
  { emoji: "😩", text: "Почему воскресенье такое короткое?", anim: "" },
  { emoji: "😅", text: "Завтра в школу... но ещё есть время!", anim: "" },
  { emoji: "🤢", text: "О нет, завтра снова вставать рано!", anim: "" },
  { emoji: "😤", text: "Воскресенье — последний шанс отдохнуть!", anim: "" },
  { emoji: "😈", text: "Завтра понедельник, а я ещё не готов", anim: "" },
  { emoji: "🙈", text: "Домашку сделали? ...я тоже нет", anim: "" },
  { emoji: "😬", text: "Завтра 8:00... это рано...", anim: "" },
  { emoji: "🤞", text: "Надеюсь, завтра будет легко", anim: "" },
  { emoji: "💫", text: "Воскресенье — день восстановления", anim: "float" },
  { emoji: "☕", text: "Наслаждаемся последним днём каникул", anim: "" },
  { emoji: "📋", text: "Проверяем рюкзак на завтра!", anim: "" },
  { emoji: "😅", text: "Дышим... завтра снова в бой", anim: "" },
  { emoji: "🌙", text: "Вечер воскресенья — самое грустное время", anim: "" },
  { emoji: "💪", text: "Ничего, справимся и завтра!", anim: "pulse" },
  { emoji: "🫣", text: "Смотрю на будильник... 6:30... кошмар", anim: "" },
  { emoji: "🎈", text: "Но сегодня ещё выходной!", anim: "" },
  { emoji: "🫡", text: "Готовимся к новой неделе!", anim: "" },
  { emoji: "😴", text: "Ложимся рано... или не ложимся?", anim: "" },
  { emoji: "🤷", text: "Завтра понедельник. Бывает.", anim: "" },
];

const ICONS = {
  "Белорусская литература": "📖",
  "Белорусский язык": "💬",
  "ФКиЗ": "⚽",
  "Русская литература": "📚",
  "Русский язык": "✏️",
  "Математика": "🔢",
  "Трудовое обучение": "🔧",
  "ОБЖ": "🛡️",
  "Музыка": "🎵",
  "Человек и мир": "🌍",
  "Изобразительное искусство": "🎨",
  "Факультатив": "⭐",
};

let SCHEDULE = [];
let EXTENDED = [];
let extendedOn = localStorage.getItem("extended") === "true";
let currentDayIdx = -1;
let touchStartX = 0;
let touchStartY = 0;
let touchStartTime = 0;

function parseTime(t) {
  const [h, m] = t.split("–")[0].split(":").map(Number);
  return h * 60 + m;
}

function getTodayIndex() {
  const d = new Date().getDay();
  return d === 0 ? 6 : d - 1;
}

function getDaySeed() {
  const now = new Date();
  return now.getFullYear() * 1000 + now.getMonth() * 50 + now.getDate();
}

function getWeekendMessage(dayIdx) {
  const seed = getDaySeed();
  const msgs = dayIdx === 5 ? SAT_MESSAGES : SUN_MESSAGES;
  return msgs[seed % msgs.length];
}

function getCurrentLesson(day) {
  const now = new Date();
  const cur = now.getHours() * 60 + now.getMinutes();
  for (let i = 0; i < day.lessons.length; i++) {
    const s = parseTime(day.lessons[i].time);
    const end = s + 45;
    if (cur >= s && cur < end) return { idx: i, type: "current" };
    if (cur < s) return { idx: i, type: "next" };
  }
  if (day.lessons.length && cur >= parseTime(day.lessons[day.lessons.length - 1].time) + 45) {
    return { idx: day.lessons.length - 1, type: "past" };
  }
  return null;
}

function countdown(targetMin) {
  const now = new Date();
  const cur = now.getHours() * 60 + now.getMinutes();
  const diff = targetMin - cur;
  if (diff <= 0) return "";
  const h = Math.floor(diff / 60);
  const m = diff % 60;
  if (h > 0) return `через ${h} ч ${m} мин`;
  return `через ${m} мин`;
}

function renderLesson(l, state) {
  const cls = state === "current" ? " current" : state === "past" ? " past" : state === "next" ? " next" : " future";
  const cd = state === "next" ? countdown(parseTime(l.time)) : "";
  const icon = ICONS[l.subj] || "📋";
  return `
    <div class="lesson${cls}">
      <div class="lesson-body">
        <div class="lesson-icon">${icon}</div>
        <div class="lesson-num">${l.n}</div>
        <div class="lesson-info">
          <div class="lesson-time">${l.time}</div>
          <div class="lesson-subject">${l.subj}</div>
          ${cd ? `<div class="lesson-countdown">${cd}</div>` : ""}
        </div>
      </div>
    </div>`;
}

function renderExtendedItem(item, state) {
  const cls = state === "current" ? " current" : state === "past" ? " past" : state === "next" ? " next" : " future";
  const cd = state === "next" ? countdown(parseTime(item.time)) : "";
  return `
    <div class="lesson${cls}">
      <div class="lesson-body">
        <div class="lesson-icon">${item.icon}</div>
        <div class="lesson-num" style="color:var(--accent);font-size:11px;">⏰</div>
        <div class="lesson-info">
          <div class="lesson-time">${item.time}</div>
          <div class="lesson-subject">${item.subj}</div>
          ${cd ? `<div class="lesson-countdown">${cd}</div>` : ""}
        </div>
      </div>
    </div>`;
}

function renderWeekendMsg(dayIdx) {
  const msg = getWeekendMessage(dayIdx);
  const animCls = msg.anim ? ` animate-${msg.anim}` : "";
  return `
    <div class="weekend-msg${animCls}">
      <span class="emoji">${msg.emoji}</span>
      ${msg.text}
      <div class="sub">${dayIdx === 5 ? "Суббота" : "Воскресенье"}</div>
    </div>`;
}

function renderStatus() {
  const el = document.getElementById("status");
  const today = getTodayIndex();
  const day = SCHEDULE[today];
  const info = getCurrentLesson(day);
  const now = new Date();
  const timeStr = now.toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit" });

  if (day.lessons.length === 0) {
    const msg = getWeekendMessage(today);
    const tip = today === 5
      ? "Отдыхай — ты заслужил!"
      : "Завтра школа — подготовь рюкзак!";
    el.innerHTML = `${msg.emoji} ${msg.text}<br><span style="font-size:12px;color:var(--muted)">${tip}</span>`;
    return;
  }
  if (!info) {
    el.innerHTML = `${day.name} · ${timeStr} · Уроков больше нет`;
    return;
  }
  const l = day.lessons[info.idx];
  if (info.type === "current") {
    el.innerHTML = `Сейчас: <span class="highlight">${l.subj}</span> · ${l.time} · ${timeStr}`;
  } else if (info.type === "next") {
    el.innerHTML = `Следующий: <span class="highlight">${l.subj}</span> · ${l.time} · ${countdown(parseTime(l.time))}`;
  } else {
    el.innerHTML = `${day.name} · ${timeStr} · Уроков больше нет`;
  }
}

function renderProgress() {
  const today = getTodayIndex();
  const day = SCHEDULE[today];
  if (!day.lessons.length) {
    document.getElementById("progressFill").style.width = "0%";
    return;
  }
  const now = new Date();
  const cur = now.getHours() * 60 + now.getMinutes();
  const first = parseTime(day.lessons[0].time);
  const last = parseTime(day.lessons[day.lessons.length - 1].time) + 45;
  const pct = Math.max(0, Math.min(100, ((cur - first) / (last - first)) * 100));
  document.getElementById("progressFill").style.width = pct + "%";
}

function renderTabs() {
  const wrap = document.getElementById("dayTabs");
  const today = getTodayIndex();
  wrap.innerHTML = SCHEDULE.map((d, i) => {
    const cls = i === today ? " active today" : "";
    return `<div class="day-tab${cls}" data-day="${i}" onclick="switchDay(${i})">${d.short}</div>`;
  }).join("");
}

function switchDay(idx) {
  if (idx < 0 || idx >= SCHEDULE.length) return;
  currentDayIdx = idx;
  document.querySelectorAll(".day-tab").forEach((t, i) => {
    t.classList.toggle("active", i === idx);
  });
  document.querySelectorAll(".day-panel").forEach((p, i) => {
    p.classList.toggle("active", i === idx);
  });
}

function toggleExtended() {
  extendedOn = document.getElementById("extendedToggle").checked;
  localStorage.setItem("extended", extendedOn);
  document.getElementById("extendedLabel").textContent = extendedOn ? "ВКЛ" : "";
  renderAll();
}

function toggleTheme() {
  document.body.classList.toggle("dark");
  const btn = document.querySelector(".theme-btn");
  btn.textContent = document.body.classList.contains("dark") ? "☾" : "☀";
  localStorage.setItem("theme", document.body.classList.contains("dark") ? "dark" : "light");
}

function renderAll() {
  const todayIdx = getTodayIndex();
  const content = document.getElementById("dayContent");
  const isMobile = window.innerWidth < 768;

  function getLessonState(dayIdx, lessonIdx, day) {
    if (dayIdx < todayIdx) return "past";
    if (dayIdx > todayIdx) return "future";
    const info = getCurrentLesson(day);
    if (!info) {
      if (day.lessons.length && Date.now() / 60000 > parseTime(day.lessons[day.lessons.length - 1].time) + 45) return "past";
      return "future";
    }
    if (lessonIdx < info.idx) return "past";
    if (lessonIdx === info.idx) return info.type;
    return "future";
  }

  function getExtState(itemIdx, dayIdx) {
    if (dayIdx < todayIdx) return "past";
    if (dayIdx > todayIdx) return "future";
    const now = new Date();
    const cur = now.getHours() * 60 + now.getMinutes();
    const item = EXTENDED[itemIdx];
    const s = parseTime(item.time);
    if (cur >= s && cur < s + 20) return "current";
    if (cur < s) return "next";
    return "past";
  }

  function renderDayLessons(d, dayIdx) {
    let html = d.lessons.map((l, li) => renderLesson(l, getLessonState(dayIdx, li, d))).join("");
    if (extendedOn && d.lessons.length > 0) {
      const lastLessonEnd = parseTime(d.lessons[d.lessons.length - 1].time) + 45;
      const filtered = EXTENDED.filter(ext => parseTime(ext.time) >= lastLessonEnd);
      html += filtered.map((ext) => renderExtendedItem(ext, getExtState(EXTENDED.indexOf(ext), dayIdx))).join("");
    }
    return html;
  }

  if (isMobile) {
    content.innerHTML = SCHEDULE.map((d, i) => {
      if (d.lessons.length === 0) {
        return `<div class="day-panel${i === currentDayIdx ? ' active' : ''}">${renderWeekendMsg(i)}</div>`;
      }
      return `<div class="day-panel${i === currentDayIdx ? ' active' : ''}">${renderDayLessons(d, i)}</div>`;
    }).join("");
  } else {
    const left = SCHEDULE.slice(0, 3);
    const right = SCHEDULE.slice(3, 5);
    const renderSide = (days) => days.map(d => {
      if (d.lessons.length === 0) {
        return `
          <div class="diary-day">
            <div class="diary-day-name">${d.name}</div>
            ${renderWeekendMsg(SCHEDULE.indexOf(d))}
          </div>`;
      }
      const dayIdx = SCHEDULE.indexOf(d);
      return `
        <div class="diary-day">
          <div class="diary-day-name">${d.name}</div>
          ${renderDayLessons(d, dayIdx)}
        </div>`;
    }).join("");

    const satMsg = getWeekendMessage(5);
    const sunMsg = getWeekendMessage(6);
    const satAnim = satMsg.anim ? ` animate-${satMsg.anim}` : "";
    const sunAnim = sunMsg.anim ? ` animate-${sunMsg.anim}` : "";
    const weekendBlock = `
      <div class="diary-day">
        <div class="diary-day-name" style="background:#e8a84c;">Суббота</div>
        <div class="weekend-msg${satAnim}">
          <span class="emoji">${satMsg.emoji}</span>
          ${satMsg.text}
        </div>
        <div class="diary-day-name" style="background:#d45555;">Воскресенье</div>
        <div class="weekend-msg${sunAnim}">
          <span class="emoji">${sunMsg.emoji}</span>
          ${sunMsg.text}
        </div>
      </div>`;

    content.innerHTML = `
      <div class="diary">
        <div class="diary-side">${renderSide(left)}</div>
        <div class="diary-side">${renderSide(right)}${weekendBlock}</div>
      </div>`;
  }
}

document.addEventListener("touchstart", (e) => {
  touchStartX = e.touches[0].clientX;
  touchStartY = e.touches[0].clientY;
  touchStartTime = Date.now();
}, { passive: true });

document.addEventListener("touchend", (e) => {
  const endX = e.changedTouches[0].clientX;
  const endY = e.changedTouches[0].clientY;
  const dx = endX - touchStartX;
  const dy = Math.abs(endY - touchStartY);
  const dt = Date.now() - touchStartTime;
  const isHorizontal = dy < Math.abs(dx) * 0.5 && dy < 30;
  if (Math.abs(dx) > 40 && isHorizontal && dt < 500) {
    if (dx < 0) {
      switchDay(Math.min(currentDayIdx + 1, SCHEDULE.length - 1));
    } else {
      switchDay(Math.max(currentDayIdx - 1, 0));
    }
  }
}, { passive: true });

async function init() {
  try {
    const res = await fetch("schedule.json?" + Date.now());
    const data = await res.json();
    SCHEDULE = data.schedule;
    EXTENDED = data.extended;
  } catch (e) {
    console.error("Failed to load schedule.json:", e);
    return;
  }

  if (localStorage.getItem("theme") === "dark") {
    document.body.classList.add("dark");
    document.querySelector(".theme-btn").textContent = "☾";
  }

  if (extendedOn) {
    document.getElementById("extendedToggle").checked = true;
    document.getElementById("extendedLabel").textContent = "ВКЛ";
  }

  currentDayIdx = getTodayIndex();
  renderTabs();
  renderAll();
  renderStatus();
  renderProgress();

  setInterval(() => { renderStatus(); renderProgress(); }, 30000);
  window.addEventListener("resize", renderAll);
}

init();
