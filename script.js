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
  "Факультатив \"Пиши грамотно\"": "✍️",
  "Факультатив \"Считаем и решаем\"": "🧮",
  "Факультатив \"Вытокi роднай мовы\"": "🗣️",
  "Факультатив \"Решение текстовых задач\"": "📝",
  "Кружок \"Ритмика и танец\"": "💃",
};

let SCHEDULE = [];
let PERSONAL = {};
let EXTENDED = [];
let HOLIDAYS = null;
let extendedOn = localStorage.getItem("extended") === "true";
let personalOn = localStorage.getItem("personal") === "true";
let schoolOn = localStorage.getItem("school") !== "false";
let currentDayIdx = -1;
let touchStartX = 0;
let touchStartY = 0;
let touchStartTime = 0;

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
    if (cur < s && (s - cur) <= 120) return { idx: i, type: "next" };
  }
  if (day.lessons.length && cur >= parseTime(day.lessons[day.lessons.length - 1].time) + 45) {
    return { idx: day.lessons.length - 1, type: "past" };
  }
  return null;
}

function daysBetween(date1, date2) {
  const d1 = new Date(date1);
  const d2 = new Date(date2);
  const diff = d2 - d1;
  return Math.ceil(diff / (1000 * 60 * 60 * 24));
}

function isDateInRange(date, start, end) {
  const d = new Date(date);
  const s = new Date(start);
  const e = new Date(end);
  return d >= s && d <= e;
}

function getNextHoliday() {
  if (!HOLIDAYS) return null;
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  for (const h of HOLIDAYS.schoolHolidays) {
    const start = new Date(h.start);
    const end = new Date(h.end);
    if (today >= start && today <= end) {
      return { type: "current", name: h.name, emoji: h.emoji, end: h.end };
    }
    if (today < start) {
      return { type: "upcoming", name: h.name, emoji: h.emoji, start: h.start };
    }
  }
  return null;
}

function getPublicHolidayToday() {
  if (!HOLIDAYS) return null;
  const today = new Date();
  const yyyy = today.getFullYear();
  const mm = String(today.getMonth() + 1).padStart(2, "0");
  const dd = String(today.getDate()).padStart(2, "0");
  const todayStr = `${yyyy}-${mm}-${dd}`;

  const allHolidays = [
    ...(HOLIDAYS.publicHolidays2026 || []),
    ...(HOLIDAYS.publicHolidays2027 || [])
  ];

  return allHolidays.find(h => h.date === todayStr);
}

function getGenitive(name) {
  const map = {
    "Осенние каникулы": "осенних каникул",
    "Зимние каникулы": "зимних каникул",
    "Зимние каникулы (доп. для I–II кл.)": "зимних каникул",
    "Весенние каникулы": "весенних каникул",
    "Летние каникулы": "летних каникул",
  };
  return map[name] || name;
}

function renderCountdowns() {
  if (!HOLIDAYS) return;
  const el = document.getElementById("countdowns");
  if (!el) return;

  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const yyyy = today.getFullYear();
  const mm = String(today.getMonth() + 1).padStart(2, "0");
  const dd = String(today.getDate()).padStart(2, "0");
  const todayStr = `${yyyy}-${mm}-${dd}`;

  let html = "";

  const publicHoliday = getPublicHolidayToday();
  if (publicHoliday) {
    html += `<div class="countdown-item holiday-today">${publicHoliday.emoji} Сегодня ${publicHoliday.name}!</div>`;
  }

  const nextHoliday = getNextHoliday();
  if (nextHoliday) {
    if (nextHoliday.type === "current") {
      const daysLeft = daysBetween(todayStr, nextHoliday.end) + 1;
      html += `<div class="countdown-item active-holiday">${nextHoliday.emoji} ${nextHoliday.name} — осталось ${daysLeft} дн.</div>`;
    } else if (nextHoliday.type === "upcoming") {
      const daysUntil = daysBetween(todayStr, nextHoliday.start);
      const schoolStart = HOLIDAYS.schoolYearStart || "2026-09-01";
      const totalToHoliday = daysBetween(schoolStart, nextHoliday.start);
      const elapsedToHoliday = daysBetween(schoolStart, todayStr);
      const pctHoliday = totalToHoliday > 0 ? Math.min(100, Math.round(elapsedToHoliday / totalToHoliday * 100)) : 0;
      html += `<div class="countdown-item countdown-progress">
        <span>${nextHoliday.emoji} До ${getGenitive(nextHoliday.name)} — ${daysUntil} дн.</span>
        <div class="mini-progress"><div class="mini-progress-fill" style="width:${pctHoliday}%"></div></div>
      </div>`;
    }
  }

  const newYear = new Date("2027-01-01");
  if (today < newYear) {
    const daysToNY = daysBetween(todayStr, "2027-01-01");
    html += `<div class="countdown-item countdown-progress">
      <span>🎄 До Нового года — ${daysToNY} дн.</span>
      <div class="mini-progress"><div class="mini-progress-fill" style="width:${Math.min(100, Math.round((365 - daysToNY) / 365 * 100))}%"></div></div>
    </div>`;
  }

  const startStr = HOLIDAYS.schoolYearStart || "2026-09-01";
  const daysPassed = daysBetween(startStr, todayStr);
  const endStr = HOLIDAYS.schoolYearEnd || "2027-05-31";
  const totalDays = daysBetween(startStr, endStr);
  const pctRaw = Math.max(0, (daysPassed / totalDays) * 100);
  const pct = Math.min(100, Math.round(pctRaw * 10) / 10);
  const pctDisplay = pct % 1 === 0 ? pct : pct.toFixed(1);

  const dayWord = daysPassed === 1 ? "день" : (daysPassed >= 2 && daysPassed <= 4 ? "дня" : "дней");
  const phrases = [
    `📚 ${daysPassed} ${dayWord} учебы — ${pctDisplay}% пути`,
    `📚 ${daysPassed} ${dayWord} за партами — ${pctDisplay}% пути`,
    `📚 Прошли ${pctDisplay}% пути — ${daysPassed} ${dayWord}`,
    `📚 ${daysPassed} ${dayWord} обучения — ${pctDisplay}% пути`,
  ];
  const phrase = phrases[Math.floor(pct / 25) % phrases.length];

  html += `
    <div class="countdown-item countdown-progress">
      <span>${phrase}</span>
      <div class="mini-progress">
        <div class="mini-progress-fill" style="width:${pct}%"></div>
      </div>
    </div>`;

  el.innerHTML = html;
}

function renderLesson(l, state, dayIdx, itemIdx) {
  const cls = state === "current" ? " current" : state === "past" ? " past" : state === "next" ? " next" : " future";
  const startTime = parseTime(l.time);
  const endTime = parseTime(l.time.split("–")[1]);
  const icon = ICONS[l.subj] || "📋";
  const paidBadge = l.paid ? ' <span style="font-size:11px;color:#e8a84c;" title="Платный">💰</span>' : "";
  const num = (l.subj && (l.subj.startsWith("Факультатив") || l.subj.startsWith("Кружок"))) ? "⭐" : (l.n != null ? l.n : "");
  const progressAttr = state === "current" ? `data-progress="${startTime}" data-end="${endTime}"` : "";
  const progressStyle = state === "current" ? (() => {
    const now = new Date();
    const cur = now.getHours() * 60 + now.getMinutes();
    const pct = Math.max(0, Math.min(100, ((cur - startTime) / (endTime - startTime)) * 100));
    return `style="--progress:${pct}%"`;
  })() : "";
  const cdAttr = state === "next" ? `data-cd="${startTime}"` : state === "current" ? `data-cd-end="${endTime}"` : "";
  const cdText = state === "next" ? countdownSec(startTime) : state === "current" ? remainingSec(endTime) : "";
  const roomText = l.room ? `<div class="lesson-room">${l.room}</div>` : "";
  const bellActive = hasReminder("school", dayIdx, itemIdx, l.time);
  const bellCls = bellActive ? " bell-active" : "";
  const editBtn = editMode ? `<div class="edit-actions"><button class="edit-btn-sm" onclick="event.stopPropagation();showEditModal('school',${dayIdx},${itemIdx})">✏️</button></div>` : "";
  return `
    <div class="lesson${cls}" data-start="${startTime}" data-end="${endTime}" data-day="${dayIdx}" data-state="${state}" ${progressAttr} ${progressStyle} ${editMode ? 'onclick="showEditModal(\'school\',' + dayIdx + ',' + itemIdx + ')"' : ''}>
      <div class="lesson-body">
        <div class="lesson-icon">${icon}</div>
        <div class="lesson-num">${num}</div>
        <div class="lesson-info">
          <div class="lesson-time">${l.time}</div>
          <div class="lesson-subject">${l.subj}${paidBadge}</div>
          ${roomText}
          ${cdAttr ? `<div class="lesson-countdown" ${cdAttr}>${cdText}</div>` : ""}
        </div>
        <button class="bell-btn${bellCls}" onclick="event.stopPropagation();toggleReminder('school',${dayIdx},${itemIdx},'${l.time}','${(l.subj||'').replace(/'/g,"\\'")}')">🔔</button>
        ${editBtn}
      </div>
    </div>`;
}

function renderExtendedItem(item, state, dayIdx, itemIdx) {
  const cls = state === "current" ? " current" : state === "past" ? " past" : state === "next" ? " next" : " future";
  const startTime = parseTime(item.time);
  const endTime = parseTime(item.time.split("–")[1]);
  const cdAttr = state === "next" ? `data-cd="${startTime}"` : state === "current" ? `data-cd-end="${endTime}"` : "";
  const cdText = state === "next" ? countdownSec(startTime) : state === "current" ? remainingSec(endTime) : "";
  const progressAttr = state === "current" ? `data-progress="${startTime}" data-end="${endTime}"` : "";
  const progressStyle = state === "current" ? (() => {
    const now = new Date();
    const cur = now.getHours() * 60 + now.getMinutes();
    const pct = Math.max(0, Math.min(100, ((cur - startTime) / (endTime - startTime)) * 100));
    return `style="--progress:${pct}%"`;
  })() : "";
  const type = item._type || "extended";
  const bellActive = hasReminder(type, dayIdx, itemIdx, item.time);
  const bellCls = bellActive ? " bell-active" : "";
  const editBtn = editMode ? `<div class="edit-actions"><button class="edit-btn-sm" onclick="event.stopPropagation();showEditModal('${type}',${dayIdx},${itemIdx})">✏️</button></div>` : "";
  return `
    <div class="lesson${cls}" data-start="${startTime}" data-end="${endTime}" data-day="${dayIdx}" data-state="${state}" ${progressAttr} ${progressStyle} ${editMode ? `onclick="showEditModal('${type}',${dayIdx},${itemIdx})"` : ''}>
      <div class="lesson-body">
        <div class="lesson-icon">${item.icon}</div>
        <div class="lesson-num" style="color:var(--accent);font-size:11px;">⏰</div>
        <div class="lesson-info">
          <div class="lesson-time">${item.time}</div>
          <div class="lesson-subject">${item.subj}</div>
          ${item.room ? `<div class="lesson-room">${item.room}</div>` : ""}
          ${cdAttr ? `<div class="lesson-countdown" ${cdAttr}>${cdText}</div>` : ""}
        </div>
        <button class="bell-btn${bellCls}" onclick="event.stopPropagation();toggleReminder('${type}',${dayIdx},${itemIdx},'${item.time}','${(item.subj||'').replace(/'/g,"\\'")}')">🔔</button>
        ${editBtn}
      </div>
    </div>`;
}

function timeRangeOverlap(a, b) {
  const aS = parseTime(a.time);
  const aE = parseTime(a.time.split("–")[1]);
  const bS = parseTime(b.time);
  const bE = parseTime(b.time.split("–")[1]);
  return aS < bE && bS < aE;
}

function renderMergeCard(group, dayIdx) {
  const times = group.map(i => i.time.split("–").map(parseTime));
  const earliestS = Math.min(...times.map(t => t[0]));
  const earliestE = Math.max(...times.map(t => t[1]));
  const timeStr = `${Math.floor(earliestS/60)}:${String(earliestS%60).padStart(2,"0")}–${Math.floor(earliestE/60)}:${String(earliestE%60).padStart(2,"0")}`;
  const state = getCardState(dayIdx, timeStr);
  const cls = `merge-card ${state}`;

  const rows = group.map(item => {
    const labelCls = item._type;
    const labelText = item._type === "school" ? (item.subj && item.subj.startsWith("Кружок") ? "Кружок" : "Урок") : item._type === "personal" ? "Занятие" : "Продлёнка";
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
    const num = item._type === "school" ? (item.subj && (item.subj.startsWith("Факультатив") || item.subj.startsWith("Кружок")) ? "⭐" : (item.n != null ? item.n : "")) : "⏰";
    const paidBadge = item.paid ? ' <span style="font-size:11px;color:#e8a84c;" title="Платный">💰</span>' : "";
    const bellActive = hasReminder(item._type, dayIdx, item._itemIdx, item.time);
    const bellCls = bellActive ? " bell-active" : "";
    const editBtn = editMode ? `<div class="edit-actions"><button class="edit-btn-sm" onclick="event.stopPropagation();showEditModal('${item._type}',${dayIdx},${item._itemIdx})">✏️</button></div>` : "";
    return `
      <div class="merge-row" data-start="${itemStart}" data-end="${itemEnd}" data-day="${dayIdx}" data-state="${rowState}" ${rowProgressAttr} ${rowProgressStyle} ${editMode ? `onclick="showEditModal('${item._type}',${dayIdx},${item._itemIdx})"` : ''}>
        <div class="merge-icon ${labelCls}">${item._icon || "📋"}</div>
        <div class="merge-info">
          <div class="merge-label ${labelCls}">${labelText}</div>
          <div class="merge-time">${item.time}</div>
          <div class="merge-subj">${item.subj}${paidBadge}</div>
          ${item.room ? `<div class="merge-room">${item.room}</div>` : ""}
          ${cdAttr ? `<div class="merge-countdown" ${cdAttr}>${cdText}</div>` : ""}
        </div>
        <button class="bell-btn${bellCls}" onclick="event.stopPropagation();toggleReminder('${item._type}',${dayIdx},${item._itemIdx},'${item.time}','${(item.subj||'').replace(/'/g,"\\'")}')">🔔</button>
        ${editBtn}
      </div>`;
  }).join("");

  return `<div class="${cls}" data-start="${earliestS}" data-end="${earliestE}" data-day="${dayIdx}" data-state="${state}">${rows}</div>`;
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

function renderDate() {
  const el = document.getElementById("dateDisplay");
  if (el) el.textContent = formatClock();
}

function renderStatus() {
  const el = document.getElementById("status");
  const today = getTodayIndex();
  const day = SCHEDULE[today];
  const info = getCurrentLesson(day);
  const now = new Date();
  const timeStr = now.toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit", second: "2-digit" });

  if (day.lessons.length === 0) {
    const msg = getWeekendMessage(today);
    const tip = today === 5
      ? "Отдыхай — ты заслужил!"
      : "Завтра школа — подготовь рюкзак!";
    el.innerHTML = `${msg.emoji} ${msg.text}<br><span style="font-size:12px;color:var(--muted)">${tip}</span>`;
    return;
  }
  if (!info) {
    el.innerHTML = `${day.name} · ${timeStr} · Уроков на сегодня нет`;
    return;
  }
  const l = day.lessons[info.idx];
  if (info.type === "current") {
    const endTime = parseTime(l.time.split("–")[1]);
    el.innerHTML = `Сейчас: <span class="highlight">${l.subj}</span> · ${l.time} · <span data-cd-end="${endTime}">${remainingSec(endTime)}</span>`;
  } else if (info.type === "next") {
    el.innerHTML = `Следующий: <span class="highlight">${l.subj}</span> · ${l.time} · <span data-cd="${parseTime(l.time)}">${countdownSec(parseTime(l.time))}</span>`;
  } else {
    el.innerHTML = `${day.name} · ${timeStr} · Уроков на сегодня нет`;
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
  let first = parseTime(day.lessons[0].time);
  let last = parseTime(day.lessons[day.lessons.length - 1].time) + 45;

  if (extendedOn && EXTENDED.length) {
    const lastLessonEnd = last;
    const filtered = EXTENDED.filter(ext => parseTime(ext.time) >= lastLessonEnd);
    if (filtered.length) {
      const lastExt = filtered[filtered.length - 1];
      last = parseTime(lastExt.time.split("–")[1]);
    }
  }

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

function buildToggles() {
  const c = document.getElementById("togglesContainer");
  if (!c) return;
  let html = "";
  const hasExtended = EXTENDED.length > 0;
  const hasPersonal = Object.keys(PERSONAL).length > 0;
  const hasSchool = SCHEDULE.some(d => d.lessons.length > 0);
  if (hasExtended && localStorage.getItem("extended") === null) { extendedOn = true; localStorage.setItem("extended", true); }
  if (hasPersonal && localStorage.getItem("personal") === null) { personalOn = true; localStorage.setItem("personal", true); }
  if (hasSchool) {
    html += `<div class="toggle-item"><label class="toggle"><input type="checkbox" id="schoolToggle" onchange="onToggle()"><span class="toggle-slider"></span></label><label for="schoolToggle">Уроки</label></div>`;
  }
  if (hasExtended) {
    html += `<div class="toggle-item"><label class="toggle"><input type="checkbox" id="extendedToggle" onchange="onToggle()"><span class="toggle-slider"></span></label><label for="extendedToggle">Продлёнка</label></div>`;
  }
  if (hasPersonal) {
    html += `<div class="toggle-item"><label class="toggle"><input type="checkbox" id="personalToggle" onchange="onToggle()"><span class="toggle-slider"></span></label><label for="personalToggle">Занятия</label></div>`;
  }
  c.innerHTML = html;
  if (schoolOn && hasSchool) document.getElementById("schoolToggle").checked = true;
  if (extendedOn && hasExtended) document.getElementById("extendedToggle").checked = true;
  if (personalOn && hasPersonal) document.getElementById("personalToggle").checked = true;
}

function onToggle() {
  const sch = document.getElementById("schoolToggle");
  const ext = document.getElementById("extendedToggle");
  const pers = document.getElementById("personalToggle");
  if (sch) { schoolOn = sch.checked; localStorage.setItem("school", schoolOn); }
  if (ext) { extendedOn = ext.checked; localStorage.setItem("extended", extendedOn); }
  if (pers) { personalOn = pers.checked; localStorage.setItem("personal", personalOn); }
  renderAll();
  renderProgress();
}

function toggleTheme() {
  document.body.classList.toggle("dark");
  const btn = document.querySelector(".theme-btn");
  btn.textContent = document.body.classList.contains("dark") ? "☾" : "☀";
  localStorage.setItem("theme", document.body.classList.contains("dark") ? "dark" : "light");
}

let editMode = isEditMode();
let modalData = { type: null, dayIdx: -1, itemIdx: -1 };
let reminders = JSON.parse(localStorage.getItem("tg_reminders") || "[]");

function saveReminders() {
  localStorage.setItem("tg_reminders", JSON.stringify(reminders));
  if (window.Android) {
    Android.syncReminders(JSON.stringify(reminders));
  }
}

function getReminderKey(type, dayIdx, itemIdx, time) {
  return type + "_" + dayIdx + "_" + itemIdx + "_" + time;
}

function getReminder(type, dayIdx, itemIdx, time) {
  const key = getReminderKey(type, dayIdx, itemIdx, time);
  return reminders.find(r => r.key === key);
}

function toggleReminder(type, dayIdx, itemIdx, time, subj) {
  const existing = getReminder(type, dayIdx, itemIdx, time);
  if (existing) {
    reminders = reminders.filter(r => r.key !== existing.key);
    saveReminders();
    renderAll();
    if (window.Android) Android.showToast("Напоминание отменено");
    return;
  }
  showReminderDialog(type, dayIdx, itemIdx, time, subj);
}

function showReminderDialog(type, dayIdx, itemIdx, time, subj) {
  const labels = { school: "Урок", personal: "Занятие", extended: "Продлёнка" };
  const dayNames = ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"];
  const existing = getReminder(type, dayIdx, itemIdx, time);
  const d = document.createElement("div");
  d.className = "reminder-dialog-overlay";
  d.style.cssText = "position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,0.6);z-index:1000;display:flex;align-items:center;justify-content:center;";
  d.innerHTML = `
    <div style="background:var(--card);border-radius:16px;padding:20px;width:290px;color:var(--text);">
      <div style="font-size:16px;font-weight:600;margin-bottom:12px;">🔔 Напоминание</div>
      <div style="font-size:13px;color:var(--muted);margin-bottom:12px;">
        ${labels[type]} · ${dayNames[dayIdx]} · ${time}<br>${subj}
      </div>
      <div style="font-size:13px;color:var(--text);margin-bottom:6px;">Повтор:</div>
      <div style="display:flex;gap:6px;margin-bottom:14px;" id="repeatGroup">
        <button class="reminder-chip repeat-chip active" data-repeat="weekly">🔄 Еженедельно</button>
        <button class="reminder-chip repeat-chip" data-repeat="once">1️⃣ Один раз</button>
      </div>
      <div style="font-size:13px;color:var(--text);margin-bottom:6px;">Напомнить за:</div>
      <div style="display:flex;gap:6px;margin-bottom:12px;flex-wrap:wrap;" id="minGroup">
        <button class="reminder-chip min-chip" data-min="5">5 мин</button>
        <button class="reminder-chip min-chip" data-min="10">10 мин</button>
        <button class="reminder-chip min-chip" data-min="15">15 мин</button>
        <button class="reminder-chip min-chip" data-min="30">30 мин</button>
      </div>
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:14px;">
        <span style="font-size:13px;color:var(--text);">Своё:</span>
        <input id="reminderCustomMin" type="number" min="1" max="1440" placeholder="мин"
          style="flex:1;padding:6px 10px;border:1.5px solid var(--line);border-radius:8px;background:var(--bg);color:var(--text);font-size:13px;">
      </div>
      <div style="font-size:13px;color:var(--text);margin-bottom:6px;">Мелодия:</div>
      <div style="display:flex;gap:6px;margin-bottom:14px;flex-wrap:wrap;" id="soundGroup">
        <button class="reminder-chip sound-chip active" data-sound="default">🔔 Системный</button>
        <button class="reminder-chip sound-chip" data-sound="school">🏫 Звонок</button>
        <button class="reminder-chip sound-chip" data-sound="gentle">🎵 Мягкий</button>
        <button class="reminder-chip sound-chip" data-sound="urgent">⚡ Срочный</button>
      </div>
      <div style="display:flex;gap:8px;">
        <button onclick="this.closest('.reminder-dialog-overlay').remove()" style="flex:1;padding:10px;border:1.5px solid var(--line);border-radius:10px;background:transparent;color:var(--muted);cursor:pointer;font-size:13px;">Отмена</button>
        <button id="reminderSaveBtn" style="flex:1;padding:10px;border:none;border-radius:10px;background:var(--accent);color:#fff;cursor:pointer;font-size:13px;font-weight:600;">Сохранить</button>
      </div>
    </div>`;
  document.body.appendChild(d);

  d._selectedRepeat = existing ? existing.repeat : "weekly";
  d._selectedMin = existing ? existing.mins : null;
  d._selectedSound = existing ? (existing.sound || "default") : "default";

  const soundUris = {
    default: "content://settings/system/notification_sound",
    school: "content://settings/system/notification_sound",
    gentle: "content://settings/system/notification_sound",
    urgent: "content://settings/system/notification_sound"
  };

  d.querySelectorAll(".repeat-chip").forEach(chip => {
    chip.style.cssText = "padding:6px 10px;border:1.5px solid var(--line);border-radius:8px;background:transparent;color:var(--text);cursor:pointer;font-size:12px;";
    if (chip.dataset.repeat === d._selectedRepeat) { chip.style.background = "var(--accent)"; chip.style.color = "#fff"; }
    chip.onclick = () => {
      d.querySelectorAll(".repeat-chip").forEach(c => { c.style.background = "transparent"; c.style.color = "var(--text)"; });
      chip.style.background = "var(--accent)";
      chip.style.color = "#fff";
      d._selectedRepeat = chip.dataset.repeat;
    };
  });

  d.querySelectorAll(".min-chip").forEach(chip => {
    chip.style.cssText = "padding:6px 12px;border:1.5px solid var(--line);border-radius:8px;background:transparent;color:var(--text);cursor:pointer;font-size:12px;";
    if (d._selectedMin && parseInt(chip.dataset.min) === d._selectedMin) { chip.style.background = "var(--accent)"; chip.style.color = "#fff"; }
    chip.onclick = () => {
      d.querySelectorAll(".min-chip").forEach(c => { c.style.background = "transparent"; c.style.color = "var(--text)"; });
      chip.style.background = "var(--accent)";
      chip.style.color = "#fff";
      d.querySelector("#reminderCustomMin").value = "";
      d._selectedMin = parseInt(chip.dataset.min);
    };
  });

  d.querySelectorAll(".sound-chip").forEach(chip => {
    chip.style.cssText = "padding:6px 10px;border:1.5px solid var(--line);border-radius:8px;background:transparent;color:var(--text);cursor:pointer;font-size:12px;";
    if (chip.dataset.sound === d._selectedSound) { chip.style.background = "var(--accent)"; chip.style.color = "#fff"; }
    chip.onclick = () => {
      d.querySelectorAll(".sound-chip").forEach(c => { c.style.background = "transparent"; c.style.color = "var(--text)"; });
      chip.style.background = "var(--accent)";
      chip.style.color = "#fff";
      d._selectedSound = chip.dataset.sound;
    };
  });

  d.querySelector("#reminderSaveBtn").onclick = () => {
    const custom = parseInt(d.querySelector("#reminderCustomMin").value);
    const mins = custom || d._selectedMin;
    if (!mins || mins < 1) { if (window.Android) Android.showToast("Укажи минуты"); return; }
    const key = getReminderKey(type, dayIdx, itemIdx, time);
    reminders = reminders.filter(r => r.key !== key);
    reminders.push({ key, type, dayIdx, itemIdx, time, subj, mins, repeat: d._selectedRepeat, sound: d._selectedSound });
    saveReminders();
    d.remove();
    renderAll();
    const rptLabel = d._selectedRepeat === "weekly" ? "еженедельно" : "один раз";
    if (window.Android) Android.showToast("Напоминание за " + mins + " мин ✓ (" + rptLabel + ")");
  };
  d.onclick = (e) => { if (e.target === d) d.remove(); };
}

function hasReminder(type, dayIdx, itemIdx, time) {
  return !!getReminder(type, dayIdx, itemIdx, time);
}

function toggleEditMode() {
  editMode = !editMode;
  localStorage.setItem(EDIT_KEY, editMode);
  document.body.classList.toggle("edit-mode", editMode);
  const editBar = document.getElementById("editBar");
  const addBtn = document.getElementById("addBtn");
  if (editBar) editBar.style.display = editMode ? "flex" : "none";
  if (addBtn) addBtn.style.display = editMode ? "block" : "none";
  renderAll();
}

function showAddModal() {
  modalData = { type: "school", dayIdx: currentDayIdx, itemIdx: -1 };
  document.getElementById("modalTitle").textContent = "Добавить урок";
  document.getElementById("modalDeleteBtn").style.display = "none";
  const daySelect = document.getElementById("modalDay");
  daySelect.innerHTML = SCHEDULE.map((d, i) => `<option value="${i}" ${i === currentDayIdx ? 'selected' : ''}>${d.name}</option>`).join("");
  document.getElementById("modalNum").value = "";
  document.getElementById("modalSubj").value = "";
  document.getElementById("modalTime").value = "";
  document.getElementById("modalRoom").value = "";
  document.getElementById("modalType").value = "school";
  document.getElementById("modalOverlay").style.display = "flex";
}

function showEditModal(type, dayIdx, itemIdx) {
  modalData = { type, dayIdx, itemIdx };
  document.getElementById("modalTitle").textContent = "Редактировать";
  document.getElementById("modalDeleteBtn").style.display = "inline-block";
  const daySelect = document.getElementById("modalDay");
  daySelect.innerHTML = SCHEDULE.map((d, i) => `<option value="${i}" ${i === dayIdx ? 'selected' : ''}>${d.name}</option>`).join("");
  document.getElementById("modalType").value = type;
  let item;
  if (type === "school") {
    item = SCHEDULE[dayIdx].lessons[itemIdx];
    document.getElementById("modalNum").value = item.n || "";
    document.getElementById("modalSubj").value = item.subj || "";
    document.getElementById("modalTime").value = item.time || "";
    document.getElementById("modalRoom").value = item.room || "";
  } else if (type === "personal") {
    item = (PERSONAL[dayIdx] || [])[itemIdx];
    document.getElementById("modalNum").value = "";
    document.getElementById("modalSubj").value = item.subj || "";
    document.getElementById("modalTime").value = item.time || "";
    document.getElementById("modalRoom").value = item.room || "";
  } else {
    item = EXTENDED[itemIdx];
    document.getElementById("modalNum").value = "";
    document.getElementById("modalSubj").value = item.subj || "";
    document.getElementById("modalTime").value = item.time || "";
    document.getElementById("modalRoom").value = item.room || "";
  }
  document.getElementById("modalOverlay").style.display = "flex";
}

function closeModal() {
  document.getElementById("modalOverlay").style.display = "none";
}

function saveModal() {
  const dayIdx = parseInt(document.getElementById("modalDay").value);
  const type = document.getElementById("modalType").value;
  const num = document.getElementById("modalNum").value.trim();
  const subj = document.getElementById("modalSubj").value.trim();
  const time = document.getElementById("modalTime").value.trim();
  const room = document.getElementById("modalRoom").value.trim();
  if (!subj || !time) { alert("Заполните предмет и время"); return; }
  const data = loadLocalData() || { schedule: JSON.parse(JSON.stringify(SCHEDULE)), personal: JSON.parse(JSON.stringify(PERSONAL)), extended: JSON.parse(JSON.stringify(EXTENDED)) };
  if (type === "school") {
    const lesson = { subj, time };
    if (num) lesson.n = parseInt(num);
    if (room) lesson.room = room;
    while (data.schedule.length <= dayIdx) data.schedule.push({ name: SCHEDULE[data.schedule.length]?.name || "", lessons: [] });
    if (modalData.itemIdx >= 0) {
      data.schedule[dayIdx].lessons[modalData.itemIdx] = lesson;
    } else {
      data.schedule[dayIdx].lessons.push(lesson);
      data.schedule[dayIdx].lessons.sort((a, b) => parseTime(a.time) - parseTime(b.time));
    }
  } else if (type === "personal") {
    const item = { subj, time, icon: "🤸" };
    if (room) item.room = room;
    if (!data.personal) data.personal = {};
    if (!data.personal[dayIdx]) data.personal[dayIdx] = [];
    if (modalData.itemIdx >= 0) {
      data.personal[dayIdx][modalData.itemIdx] = item;
    } else {
      data.personal[dayIdx].push(item);
      data.personal[dayIdx].sort((a, b) => parseTime(a.time) - parseTime(b.time));
    }
  } else {
    const item = { subj, time, icon: "🎒" };
    if (room) item.room = room;
    if (!data.extended) data.extended = [];
    if (modalData.itemIdx >= 0) {
      data.extended[modalData.itemIdx] = item;
    } else {
      data.extended.push(item);
      data.extended.sort((a, b) => parseTime(a.time) - parseTime(b.time));
    }
  }
  saveLocalData(data);
  if (type === "school") { while (SCHEDULE.length <= dayIdx) SCHEDULE.push({ name: "", lessons: [] }); SCHEDULE[dayIdx].lessons = data.schedule[dayIdx].lessons; }
  else if (type === "personal") { PERSONAL[dayIdx] = data.personal[dayIdx] || []; }
  else { EXTENDED = data.extended; }
  closeModal();
  buildToggles();
  renderAll();
}

function deleteFromModal() {
  if (!confirm("Удалить?")) return;
  const data = loadLocalData();
  if (!data) return;
  if (modalData.type === "school" && modalData.dayIdx >= 0 && modalData.itemIdx >= 0) {
    data.schedule[modalData.dayIdx].lessons.splice(modalData.itemIdx, 1);
    SCHEDULE[modalData.dayIdx].lessons = data.schedule[modalData.dayIdx].lessons;
  } else if (modalData.type === "personal" && modalData.dayIdx >= 0 && modalData.itemIdx >= 0) {
    data.personal[modalData.dayIdx].splice(modalData.itemIdx, 1);
    PERSONAL[modalData.dayIdx] = data.personal[modalData.dayIdx];
  } else if (modalData.type === "extended" && modalData.itemIdx >= 0) {
    data.extended.splice(modalData.itemIdx, 1);
    EXTENDED = data.extended;
  }
  saveLocalData(data);
  closeModal();
  buildToggles();
  renderAll();
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
    const e = parseTime(item.time.split("–")[1]);
    if (cur >= s && cur < e) return "current";
    if (cur >= e) return "past";
    if (cur < s && (s - cur) <= 120) return "next";
    return "future";
  }

  function renderDayLessons(d, dayIdx) {
    const school = schoolOn ? d.lessons.map((l, li) => ({
      ...l, _type: "school", _icon: ICONS[l.subj] || "📋", _itemIdx: li,
      _state: getLessonState(dayIdx, li, d),
      _noMerge: l.subj === "ФКиЗ"
    })) : [];
    const personal = (personalOn ? (PERSONAL[dayIdx] || []) : []).map((p, pi) => ({
      ...p, _type: "personal", _icon: p.icon || "🤸", _itemIdx: pi,
      _state: (function() {
        if (dayIdx < todayIdx) return "past";
        if (dayIdx > todayIdx) return "future";
        const now = new Date();
        const cur = now.getHours() * 60 + now.getMinutes();
        const s = parseTime(p.time);
        const e = parseTime(p.time.split("–")[1]);
        if (cur >= s && cur < e) return "current";
        if (cur >= e) return "past";
        if (cur < s && (s - cur) <= 120) return "next";
        return "future";
      })()
    }));
    const extended = (extendedOn ? EXTENDED : []).map((ext, ei) => ({
      ...ext, _type: "extended", _icon: ext.icon, _itemIdx: ei,
      _state: getExtState(EXTENDED.indexOf(ext), dayIdx)
    }));
    const all = [...school, ...personal, ...extended];
    if (all.length <= 1) {
      return all.map(item => {
        if (item._type === "school") return renderLesson(item, item._state, dayIdx, item._itemIdx);
        return renderExtendedItem(item, item._state, dayIdx, item._itemIdx);
      }).join("");
    }

    const parent = all.map((_, i) => i);
    function find(x) { while (parent[x] !== x) { parent[x] = parent[parent[x]]; x = parent[x]; } return x; }
    function union(a, b) { parent[find(a)] = find(b); }

    for (let i = 0; i < all.length; i++) {
      if (all[i]._noMerge) continue;
      for (let j = i + 1; j < all.length; j++) {
        if (all[j]._noMerge) continue;
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
        group.sort((a, b) => parseTime(a.time) - parseTime(b.time));
        return { type: "merge", items: group };
      }
      return group[0];
    });
    merged.sort((a, b) => {
      const aTime = a.type === "merge" ? parseTime(a.items[0].time) : parseTime(a.time);
      const bTime = b.type === "merge" ? parseTime(b.items[0].time) : parseTime(b.time);
      return aTime - bTime;
    });

    return merged.map(item => {
      if (item.type === "merge") return renderMergeCard(item.items, dayIdx);
      if (item._type === "school") return renderLesson(item, item._state, dayIdx, item._itemIdx);
      return renderExtendedItem(item, item._state, dayIdx, item._itemIdx);
    }).join("");
  }

  if (isMobile) {
    content.innerHTML = SCHEDULE.map((d, i) => {
      const hasSchool = schoolOn && d.lessons.length > 0;
      const hasPersonal = personalOn && PERSONAL[i] && PERSONAL[i].length > 0;
      if (!hasSchool && !hasPersonal) {
        return `<div class="day-panel${i === currentDayIdx ? ' active' : ''}">${renderWeekendMsg(i)}</div>`;
      }
      return `<div class="day-panel${i === currentDayIdx ? ' active' : ''}">${renderDayLessons(d, i)}</div>`;
    }).join("");
  } else {
    const left = SCHEDULE.slice(0, 3);
    const right = SCHEDULE.slice(3, 5);
    const renderSide = (days) => days.map(d => {
      const dayIdx = SCHEDULE.indexOf(d);
      const hasSchool = schoolOn && d.lessons.length > 0;
      const hasPersonal = personalOn && PERSONAL[dayIdx] && PERSONAL[dayIdx].length > 0;
      if (!hasSchool && !hasPersonal) {
        return `
          <div class="diary-day">
            <div class="diary-day-name">${d.name}</div>
            ${renderWeekendMsg(dayIdx)}
          </div>`;
      }
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
    const [scheduleRes, holidaysRes] = await Promise.all([
      fetch("timeSchedule.json?" + Date.now()),
      fetch("holidays.json?" + Date.now())
    ]);
    const scheduleData = await scheduleRes.json();
    SCHEDULE = scheduleData.schedule;
    PERSONAL = scheduleData.personal || {};
    EXTENDED = scheduleData.extended;
    HOLIDAYS = await holidaysRes.json();
  } catch (e) {
    console.error("Failed to load data:", e);
    return;
  }

  const local = loadLocalData();
  if (local) {
    if (local.schedule && local.schedule.length) SCHEDULE = local.schedule;
    if (local.personal && Object.keys(local.personal).length) PERSONAL = local.personal;
    if (local.extended && local.extended.length) EXTENDED = local.extended;
  }

  buildToggles();

  if (localStorage.getItem("theme") === "dark") {
    document.body.classList.add("dark");
    document.querySelector(".theme-btn").textContent = "☾";
  }

  if (editMode) {
    document.body.classList.add("edit-mode");
    const editBar = document.getElementById("editBar");
    const addBtn = document.getElementById("addBtn");
    if (editBar) editBar.style.display = "flex";
    if (addBtn) addBtn.style.display = "block";
  }

  currentDayIdx = getTodayIndex();
  renderDate();
  renderTabs();
  renderAll();
  renderStatus();
  renderProgress();
  renderCountdowns();

  startEngines(() => { renderStatus(); renderProgress(); renderCountdowns(); });
  window.addEventListener("resize", renderAll);
}

function exportSchool() {
  const local = loadLocalData();
  const data = (local && local.schedule) ? local.schedule : SCHEDULE;
  const json = JSON.stringify(data, null, 2);
  const blob = new Blob([json], { type: "application/json;charset=utf-8" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = "Уроки_2А.json";
  a.click();
  URL.revokeObjectURL(a.href);
}

function exportPersonal() {
  const local = loadLocalData();
  const data = (local && local.personal) ? local.personal : PERSONAL;
  const json = JSON.stringify(data, null, 2);
  const blob = new Blob([json], { type: "application/json;charset=utf-8" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = "Занятия_2А.json";
  a.click();
  URL.revokeObjectURL(a.href);
}

function exportExtended() {
  const local = loadLocalData();
  const data = (local && local.extended) ? local.extended : EXTENDED;
  const json = JSON.stringify(data, null, 2);
  const blob = new Blob([json], { type: "application/json;charset=utf-8" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = "Продлёнка_2А.json";
  a.click();
  URL.revokeObjectURL(a.href);
}

init();
