const SAT_MSG = [
  { e:"🎉", t:"Выходной — личные занятия!", a:"" },
  { e:"🤸", t:"Суббота: гимнастика + шахматы", a:"float" },
  { e:"♟️", t:"Шахматный день!", a:"" },
  { e:"☀️", t:"Суббота и спорт — идеально", a:"pulse" },
  { e:"💪", t:"Субботняя форма!", a:"" },
  { e:"🎵", t:"Суббота — день музыки и спорта", a:"float" },
  { e:"🤸", t:"Гимнастика утром — весь день свободен", a:"" },
  { e:"♟️", t:"Шахматы — тренируем мышление", a:"pulse" },
  { e:"🌈", t:"Прекрасная суббота!", a:"float" },
  { e:"☕", t:"Субботнее утро и гимнастика", a:"" },
  { e:"🎯", t:"День для своих дел!", a:"" },
  { e:"🏃", t:"Суббота — день активности", a:"float" },
  { e:"🤸", t:"Растяжка и шахматы — отличный план", a:"" },
  { e:"♟️", t:"Шахматы — лучший способ провести субботу", a:"" },
  { e:"☀️", t:"Выходной день — наслаждаемся!", a:"pulse" },
  { e:"💪", t:"Спортивная суббота!", a:"" },
  { e:"🎵", t:"Музыка и спорт — наш стиль", a:"float" },
  { e:"🤸", t:"Гимнастика — заряжаемся энергией", a:"" },
  { e:"♟️", t:"Шахматы — развиваем стратегическое мышление", a:"" },
  { e:"🌈", t:"Суббота — день возможностей!", a:"pulse" },
];
const SUN_MSG = [
  { e:"😱", t:"Завтра понедельник...", a:"" },
  { e:"😩", t:"Воскресенье заканчивается...", a:"" },
  { e:"😅", t:"Ещё один день отдыха!", a:"" },
  { e:"🤢", t:"Завтра снова в школу!", a:"" },
  { e:"😤", t:"Воскресенье — восстанавливаем силы!", a:"" },
  { e:"♟️", t:"Воскресенье: шахматы вечером", a:"float" },
  { e:"🙈", t:"Завтра понедельник... кошмар", a:"" },
  { e:"😬", t:"Последний день свободы!", a:"" },
  { e:"🤞", t:"Надеюсь, завтра будет легко", a:"" },
  { e:"💫", t:"Воскресенье — день восстановления", a:"float" },
  { e:"☕", t:"Наслаждаемся выходным", a:"" },
  { e:"📋", t:"Готовим рюкзак на завтра!", a:"" },
  { e:"😅", t:"Дышим... завтра снова в бой", a:"" },
  { e:"🌙", t:"Вечер воскресенья — самое грустное время", a:"" },
  { e:"💪", t:"Ничего, справимся и завтра!", a:"pulse" },
  { e:"🫣", t:"Будильник на 6:30... кошмар", a:"" },
  { e:"🎈", t:"Но сегодня ещё выходной!", a:"" },
  { e:"🫡", t:"Готовимся к новой неделе!", a:"" },
  { e:"♟️", t:"Вечером шахматы — закрепляем стратегию", a:"float" },
  { e:"🤷", t:"Завтра понедельник. Бывает.", a:"" },
];

let SCHOOL = [];
let PERSONAL = {};
let EXTENDED = [];

const DAYS = ["ПН","ВТ","СР","ЧТ","ПТ","СБ","ВС"];
const DAY_NAMES = ["Понедельник","Вторник","Среда","Четверг","Пятница","Суббота","Воскресенье"];

let showPersonal = localStorage.getItem("nikol_personal") === "true";
let showExtended = localStorage.getItem("nikol_extended") === "true";
let currentDayIdx = -1;

function getDaySeed() {
  const now = new Date();
  return now.getFullYear() * 1000 + now.getMonth() * 50 + now.getDate();
}
function getWeekendMessage(dayIdx) {
  const msgs = dayIdx === 5 ? SAT_MSG : SUN_MSG;
  return msgs[getDaySeed() % msgs.length];
}
function timeRangeOverlap(a, b) {
  const [aS, aE] = a.time.split("–").map(parseTime);
  const [bS, bE] = b.time.split("–").map(parseTime);
  return aS < bE && bS < aE;
}

function getItemsForDay(dayIdx) {
  const isWeekend = dayIdx >= 5;
  const daySchedule = SCHOOL[dayIdx] || { lessons: [] };
  const school = isWeekend ? [] : (daySchedule.lessons || []).map(s => {
    const iconMap = {
      "Белорусская литература":"📖","Белорусский язык":"💬","ФКиЗ":"⚽",
      "Русская литература":"📖","Русский язык":"✏️","Математика":"🔢",
      "Трудовое обучение":"🔧","ОБЖ":"🛡️","Музыка":"🎵",
      "Человек и мир":"🌍","Изобразительное искусство":"🎨","Факультатив":"⭐",
      "Факультатив \"Пиши грамотно\"":"✍️","Факультатив \"Считаем и решаем\"":"🧮","Факультатив \"Вытокi роднай мовы\"":"🗣️",
      "Факультатив \"Решение текстовых задач\"":"📝"
    };
    return { n: s.n, time: s.time, subj: s.subj, icon: iconMap[s.subj] || "📋", type: "school", paid: s.paid };
  });
  const personal = showPersonal ? (PERSONAL[dayIdx] || []).map(p => ({...p, type:"personal"})) : [];

  let extended = [];
  if (showExtended && !isWeekend && school.length > 0) {
    const dayLessons = (SCHOOL[dayIdx] || {}).lessons || [];
    if (dayLessons.length > 0) {
      const lastLessonEnd = parseTime(dayLessons[dayLessons.length - 1].time) + 45;
      extended = EXTENDED.filter(ext => parseTime(ext.time) >= lastLessonEnd).map(e => ({...e, type:"extended"}));
    }
  }

  if (personal.length === 0 && extended.length === 0) return school;

  const all = [...school, ...personal, ...extended];
  if (all.length <= 1) return all;

  const parent = all.map((_, i) => i);
  function find(x) { while (parent[x] !== x) { parent[x] = parent[parent[x]]; x = parent[x]; } return x; }
  function union(a, b) { parent[find(a)] = find(b); }

  for (let i = 0; i < all.length; i++) {
    for (let j = i + 1; j < all.length; j++) {
      if (timeRangeOverlap(all[i], all[j])) union(i, j);
    }
  }

  const groups = {};
  for (let i = 0; i < all.length; i++) {
    const root = find(i);
    if (!groups[root]) groups[root] = [];
    groups[root].push(all[i]);
  }

  const merged = Object.values(groups).map(group => {
    if (group.length > 1) {
      group.sort((a, b) => {
        const order = { school: 0, personal: 1, extended: 2 };
        return (order[a.type] || 0) - (order[b.type] || 0);
      });
      return { type: "merge", items: group };
    }
    return group[0];
  });
  merged.sort((a, b) => {
    const aTime = a.type === "merge" ? parseTime(a.items[0].time) : parseTime(a.time);
    const bTime = b.type === "merge" ? parseTime(b.items[0].time) : parseTime(b.time);
    return aTime - bTime;
  });

  return merged;
}

function renderSingle(item, dayIdx) {
  const state = getCardState(dayIdx, item.time);
  const cls = `lesson ${state}`;
  const num = item.type === "school" ? (item.subj && item.subj.startsWith("Факультатив") ? "⭐" : item.n) : "⭐";
  const startTime = parseTime(item.time);
  const endTime = parseTime(item.time.split("–")[1]);
  const paidBadge = item.paid ? ' <span style="font-size:11px;color:#e8a84c;" title="Платный">💰</span>' : "";
  const progressAttr = state === "current" ? `data-progress="${startTime}" data-end="${endTime}"` : "";
  const progressStyle = state === "current" ? (() => {
    const now = new Date();
    const cur = now.getHours() * 60 + now.getMinutes();
    const pct = Math.max(0, Math.min(100, ((cur - startTime) / (endTime - startTime)) * 100));
    return `style="--progress:${pct}%"`;
  })() : "";
  const cdAttr = state === "next" ? `data-cd="${startTime}"` : state === "current" ? `data-cd-end="${endTime}"` : "";
  const cdText = state === "next" ? countdownSec(startTime) : state === "current" ? remainingSec(endTime) : "";
  return `
    <div class="${cls}" data-start="${startTime}" data-end="${endTime}" data-day="${dayIdx}" data-state="${state}" ${progressAttr} ${progressStyle}>
      <div class="lesson-body">
        <div class="lesson-icon">${item.icon}</div>
        <div class="lesson-num">${num}</div>
        <div class="lesson-info">
          <div class="lesson-time">${item.time}</div>
          <div class="lesson-subject">${item.subj}${paidBadge}</div>
          ${cdAttr ? `<div class="lesson-countdown" ${cdAttr}>${cdText}</div>` : ""}
        </div>
      </div>
    </div>`;
}

function renderMerge(group, dayIdx) {
  const times = group.items.map(i => i.time.split("–").map(parseTime));
  const earliestS = Math.min(...times.map(t => t[0]));
  const earliestE = Math.max(...times.map(t => t[1]));
  const state = getCardState(dayIdx, `${Math.floor(earliestS/60)}:${String(earliestS%60).padStart(2,"0")}–${Math.floor(earliestE/60)}:${String(earliestE%60).padStart(2,"0")}`);
  const cls = `merge-card ${state}`;

  const rows = group.items.map(item => {
    const labelCls = item.type;
    const labelText = item.type === "school" ? "Урок" : item.type === "personal" ? "Занятие" : "Продлёнка";
    const itemStart = parseTime(item.time);
    const itemEnd = parseTime(item.time.split("–")[1]);
    const rowState = getCardState(dayIdx, item.time);
    const rowProgressAttr = rowState === "current" ? `data-progress="${itemStart}" data-end="${itemEnd}"` : "";
    const rowProgressStyle = rowState === "current" ? (() => {
      const now = new Date();
      const cur = now.getHours() * 60 + now.getMinutes();
      const pct = Math.max(0, Math.min(100, ((cur - itemStart) / (itemEnd - itemStart)) * 100));
      return `style="--progress:${pct}%"`;
    })() : "";
    const cdAttr = rowState === "next" ? `data-cd="${itemStart}"` : rowState === "current" ? `data-cd-end="${itemEnd}"` : "";
    const cdText = rowState === "next" ? countdownSec(itemStart) : rowState === "current" ? remainingSec(itemEnd) : "";
    return `
      <div class="merge-row" data-start="${itemStart}" data-end="${itemEnd}" data-day="${dayIdx}" data-state="${rowState}" ${rowProgressAttr} ${rowProgressStyle}>
        <div class="merge-icon ${item.type}">${item.icon}</div>
        <div class="merge-info">
          <div class="merge-label ${labelCls}">${labelText}</div>
          <div class="merge-time">${item.time}</div>
          <div class="merge-subj">${item.subj}</div>
          ${cdAttr ? `<div class="merge-countdown" ${cdAttr}>${cdText}</div>` : ""}
        </div>
      </div>`;
  }).join("");

  return `<div class="${cls}" data-start="${earliestS}" data-end="${earliestE}" data-day="${dayIdx}" data-state="${state}">${rows}</div>`;
}

function renderWeekendMsg(dayIdx) {
  const msg = getWeekendMessage(dayIdx);
  const animCls = msg.a ? ` animate-${msg.a}` : "";
  return `
    <div class="weekend-msg${animCls}">
      <span class="emoji">${msg.e}</span>
      ${msg.t}
      <div class="sub">${DAY_NAMES[dayIdx]}</div>
    </div>`;
}

function renderStatus() {
  const el = document.getElementById("status");
  const todayIdx = getTodayIndex();
  const items = getItemsForDay(todayIdx);
  const now = new Date();
  const timeStr = now.toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit" });
  const cur = now.getHours() * 60 + now.getMinutes();

  if (items.length === 0) {
    const msg = getWeekendMessage(todayIdx);
    el.innerHTML = `${msg.e} ${msg.t}`;
    return;
  }

  let current = null, next = null;
  for (const item of items) {
    if (item.type === "merge") {
      const times = item.items.map(i => i.time.split("–").map(parseTime));
      const s = Math.min(...times.map(t => t[0]));
      const e = Math.max(...times.map(t => t[1]));
      if (cur >= s && cur < e) { current = item; break; }
      if (!next && cur < s) next = item;
    } else {
      const [s, e] = item.time.split("–").map(parseTime);
      if (cur >= s && cur < e) { current = item; break; }
      if (!next && cur < s) next = item;
    }
  }

  if (current) {
    const label = current.type === "merge"
      ? current.items.map(i => i.subj).join(" + ")
      : current.subj;
    el.innerHTML = `Сейчас: <span class="highlight">${label}</span> · ${timeStr}`;
  } else if (next) {
    const label = next.type === "merge"
      ? next.items.map(i => i.subj).join(" + ")
      : next.subj;
    const time = next.type === "merge" ? next.items[0].time : next.time;
    el.innerHTML = `Следующее: <span class="highlight">${label}</span> · ${time} · <span id="statusCd" data-cd="${parseTime(time)}">${countdownSec(parseTime(time))}</span>`;
  } else {
    el.innerHTML = `${DAY_NAMES[todayIdx]} · ${timeStr} · Занятий на сегодня нет`;
  }
}

function renderAll() {
  const todayIdx = getTodayIndex();
  const content = document.getElementById("dayContent");
  const isMobile = window.innerWidth < 768;

  function renderDay(dayIdx) {
    const items = getItemsForDay(dayIdx);
    let html = "";
    if (items.length > 0) {
      html += items.map(item => {
        if (item.type === "merge") return renderMerge(item, dayIdx);
        return renderSingle(item, dayIdx);
      }).join("");
    }
    if (dayIdx >= 5) {
      html += renderWeekendMsg(dayIdx);
    } else if (items.length === 0) {
      html += `<div class="empty-day">Нет занятий</div>`;
    }
    return html;
  }

  if (isMobile) {
    content.innerHTML = DAYS.map((d, i) =>
      `<div class="day-panel${i === currentDayIdx ? ' active' : ''}">${renderDay(i)}</div>`
    ).join("");
  } else {
    const left = DAYS.slice(0, 3);
    const right = DAYS.slice(3);
    const renderSide = (days, startIdx) => days.map((d, i) => `
      <div class="diary-day">
        <div class="diary-day-name">${d}</div>
        ${renderDay(startIdx + i)}
      </div>`).join("");

    content.innerHTML = `
      <div class="diary">
        <div>${renderSide(left, 0)}</div>
        <div>${renderSide(right, 3)}</div>
      </div>`;
  }
}

function renderTabs() {
  document.getElementById("dayTabs").innerHTML = DAYS.map((d, i) => {
    const cls = i === getTodayIndex() ? " active today" : "";
    return `<div class="day-tab${cls}" data-day="${i}" onclick="switchDay(${i})">${d}</div>`;
  }).join("");
}

function switchDay(idx) {
  currentDayIdx = idx;
  document.querySelectorAll(".day-tab").forEach((t, i) => t.classList.toggle("active", i === idx));
  document.querySelectorAll(".day-panel").forEach((p, i) => p.classList.toggle("active", i === idx));
}

function onToggle() {
  showPersonal = document.getElementById("personalToggle").checked;
  showExtended = document.getElementById("extendedToggle").checked;
  localStorage.setItem("nikol_personal", showPersonal);
  localStorage.setItem("nikol_extended", showExtended);
  renderAll();
  renderStatus();
}

function toggleTheme() {
  document.body.classList.toggle("dark");
  document.querySelector(".theme-btn").textContent =
    document.body.classList.contains("dark") ? "☾" : "☀";
  localStorage.setItem("nikol_theme", document.body.classList.contains("dark") ? "dark" : "light");
}

function renderProgress() {
  const todayIdx = getTodayIndex();
  const items = getItemsForDay(todayIdx);
  const fill = document.getElementById("progressFill");
  if (!items.length) { fill.style.width = "0%"; return; }

  const now = new Date();
  const cur = now.getHours() * 60 + now.getMinutes();
  let first = Infinity, last = 0;
  items.forEach(item => {
    if (item.type === "merge") {
      item.items.forEach(i => {
        const s = parseTime(i.time);
        if (s < first) first = s;
        const e = parseTime(i.time.split("–")[1]);
        if (e > last) last = e;
      });
    } else {
      const s = parseTime(item.time);
      const e = parseTime(item.time.split("–")[1]);
      if (s < first) first = s;
      if (e > last) last = e;
    }
  });
  if (first === Infinity) { fill.style.width = "0%"; return; }
  const pct = Math.max(0, Math.min(100, ((cur - first) / (last - first)) * 100));
  fill.style.width = pct + "%";
}

let touchStartX = 0;
let touchStartY = 0;
let touchStartTime = 0;

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
      switchDay(Math.min(currentDayIdx + 1, 6));
    } else {
      switchDay(Math.max(currentDayIdx - 1, 0));
    }
  }
}, { passive: true });

if (localStorage.getItem("nikol_theme") === "dark") {
  document.body.classList.add("dark");
  document.querySelector(".theme-btn").textContent = "☾";
}
if (showPersonal) document.getElementById("personalToggle").checked = true;
if (showExtended) document.getElementById("extendedToggle").checked = true;

currentDayIdx = getTodayIndex();
document.getElementById("dateDisplay").textContent = formatClock();
renderTabs();

fetch("../timeSchedule.json").then(r => r.json()).then(data => {
  SCHOOL = data.schedule || [];
  PERSONAL = data.personal || {};
  EXTENDED = data.extended || [];
  renderAll();
  renderStatus();
  startEngines(() => { renderStatus(); renderProgress(); });
}).catch(() => {
  renderAll();
  renderStatus();
  startEngines(() => { renderStatus(); renderProgress(); });
});
