# -*- coding: utf-8 -*-
import io

p = "Nikol/nikol.js"
s = io.open(p, encoding="utf-8", newline="").read()
nl = "\r\n" if "\r\n" in s else "\n"
def J(parts):
    return nl.join(parts)

# 1) state
old = J(["let SCHOOL = [];",
         "let PERSONAL = {};",
         "let EXTENDED = [];"])
new = J(["let SCHOOL = [];",
         "let PERSONAL = {};",
         "let CUSTOM = {};",
         "let EXTENDED = [];"])
assert old in s, "1"
s = s.replace(old, new, 1)

# 2) flags + helpers
old = J(['let showPersonal = localStorage.getItem("nikol_personal") === "true";',
         'let showExtended = localStorage.getItem("nikol_extended") === "true";',
         'let currentDayIdx = -1;'])
new = J(['let showPersonal = localStorage.getItem("nikol_personal") === "true";',
         'let showExtended = localStorage.getItem("nikol_extended") === "true";',
         'let schoolOn = localStorage.getItem("nikol_school") !== "false";',
         'let currentDayIdx = -1;',
         '',
         'function customOn(key) { return localStorage.getItem("nikol_custom_" + key) !== "false"; }',
         'function typeClsOf(key) {',
         '  if (key === "school" || key === "personal" || key === "extended") return key;',
         '  const k = (key || "").toLowerCase();',
         '  if (k.indexOf("факультатив") !== -1) return "custom-facult";',
         '  if (k.indexOf("кружок") !== -1) return "custom-circle";',
         '  return "custom";',
         '}'])
assert old in s, "2"
s = s.replace(old, new, 1)

# 3) getItemsForDay: school flag + custom entities
old = J(['  const isWeekend = dayIdx >= 5;',
         '  const daySchedule = SCHOOL[dayIdx] || { lessons: [] };',
         '  const school = isWeekend ? [] : (daySchedule.lessons || []).map(s => {'])
new = J(['  const isWeekend = dayIdx >= 5;',
         '  const daySchedule = SCHOOL[dayIdx] || { lessons: [] };',
         '  const school = (schoolOn && !isWeekend) ? (daySchedule.lessons || []).map(s => {'])
assert old in s, "3"
s = s.replace(old, new, 1)

old = '  const personal = showPersonal ? (PERSONAL[dayIdx] || []).map(p => ({...p, type:"personal"})) : [];'
new = J(['  const personal = showPersonal ? (PERSONAL[dayIdx] || []).map(p => ({...p, type:"personal"})) : [];',
         '  const custom = Object.keys(CUSTOM).filter(customOn).flatMap(k => (((CUSTOM[k] || {})[dayIdx]) || []).map(p => ({',
         '    ...p, icon: p.icon || "⭐", type: k',
         '  })));'])
assert old in s, "3b"
s = s.replace(old, new, 1)

old = J(['  if (personal.length === 0 && extended.length === 0) return school;',
         '',
         '  const all = [...school, ...personal, ...extended];'])
new = J(['  if (personal.length === 0 && extended.length === 0 && custom.length === 0) return school;',
         '',
         '  const all = [...school, ...personal, ...custom, ...extended];'])
assert old in s, "3c"
s = s.replace(old, new, 1)

# 4) renderSingle labels
old = ('  const typeLabel = item.type === "school" ? (item.subj && item.subj.startsWith("Кружок") ? "Кружок" : item.subj && item.subj.startsWith("Факультатив") ? "Факультатив" : "Урок") : item.type === "personal" ? "Занятие" : "Продлёнка";' + nl +
       '  const typeCls = item.type || "school";')
new = J(['  const typeLabel = item.type === "school" ? (item.subj && item.subj.startsWith("Кружок") ? "Кружок" : item.subj && item.subj.startsWith("Факультатив") ? "Факультатив" : "Урок") : item.type === "personal" ? "Занятие" : item.type === "extended" ? "Продлёнка" : item.type;',
         '  const typeCls = typeClsOf(item.type);'])
assert old in s, "4"
s = s.replace(old, new, 1)

# 5) renderMerge labels + paid badge
old = J(['    const labelCls = item.type;',
         '    const labelText = item.type === "school" ? (item.subj && item.subj.startsWith("Кружок") ? "Кружок" : "Урок") : item.type === "personal" ? "Занятие" : "Продлёнка";'])
new = J(['    const labelCls = typeClsOf(item.type);',
         '    const labelText = item.type === "school" ? (item.subj && item.subj.startsWith("Кружок") ? "Кружок" : "Урок") : item.type === "personal" ? "Занятие" : item.type === "extended" ? "Продлёнка" : item.type;',
         '    const paidBadge = item.paid ? \' <span style="font-size:11px;color:#e8a84c;" title="Платный">💰</span>\' : "";'])
assert old in s, "5"
s = s.replace(old, new, 1)

old = '          <div class="merge-subj">${item.subj}</div>'
assert old in s, "5b"
s = s.replace(old, '          <div class="merge-subj">${item.subj}${paidBadge}</div>', 1)

# 6) buildToggles + onToggle rewrite
old = J(['function onToggle() {',
         '  showPersonal = document.getElementById("personalToggle").checked;',
         '  showExtended = document.getElementById("extendedToggle").checked;',
         '  localStorage.setItem("nikol_personal", showPersonal);',
         '  localStorage.setItem("nikol_extended", showExtended);',
         '  renderAll();',
         '  renderStatus();',
         '}'])
new = J(['function toggleHtml(id, label) {',
         '  return `<div class="toggle-item"><label class="toggle"><input type="checkbox" id="${id}" onchange="onToggle()"><span class="toggle-slider"></span></label><label for="${id}">${label}</label></div>`;',
         '}',
         '',
         'function buildToggles() {',
         '  const c = document.getElementById("togglesContainer");',
         '  if (!c) return;',
         '  const customKeys = Object.keys(CUSTOM).filter(k => Object.values(CUSTOM[k] || {}).some(arr => Array.isArray(arr) && arr.length > 0));',
         '  let html = toggleHtml("schoolToggle", "Уроки");',
         '  if (Object.keys(PERSONAL || {}).some(k => Array.isArray(PERSONAL[k]) && PERSONAL[k].length > 0)) html += toggleHtml("personalToggle", "Занятия");',
         '  if (EXTENDED.length > 0) html += toggleHtml("extendedToggle", "Продлёнка");',
         '  for (const k of customKeys) {',
         '    html += `<div class="toggle-item"><label class="toggle"><input type="checkbox" data-custom-key="${k}" onchange="onToggle()"><span class="toggle-slider"></span></label><label>${k}</label></div>`;',
         '  }',
         '  c.innerHTML = html;',
         '  if (schoolOn) document.getElementById("schoolToggle").checked = true;',
         '  if (showPersonal) document.getElementById("personalToggle").checked = true;',
         '  if (showExtended) document.getElementById("extendedToggle").checked = true;',
         '  customKeys.forEach(k => {',
         '    if (localStorage.getItem("nikol_custom_" + k) === null) localStorage.setItem("nikol_custom_" + k, "true");',
         '    const t = document.querySelector(\'input[data-custom-key="\' + k + \'"]\');',
         '    if (t && customOn(k)) t.checked = true;',
         '  });',
         '}',
         '',
         'function onToggle() {',
         '  const sch = document.getElementById("schoolToggle");',
         '  const pers = document.getElementById("personalToggle");',
         '  const ext = document.getElementById("extendedToggle");',
         '  if (sch) { schoolOn = sch.checked; localStorage.setItem("nikol_school", schoolOn); }',
         '  if (pers) { showPersonal = pers.checked; localStorage.setItem("nikol_personal", showPersonal); }',
         '  if (ext) { showExtended = ext.checked; localStorage.setItem("nikol_extended", showExtended); }',
         '  document.querySelectorAll("#togglesContainer input[data-custom-key]").forEach(t => {',
         '    localStorage.setItem("nikol_custom_" + t.dataset.customKey, t.checked);',
         '  });',
         '  renderAll();',
         '  renderStatus();',
         '}'])
assert old in s, "6"
s = s.replace(old, new, 1)

# 7) init: CUSTOM load + buildToggles
old = J(['  SCHOOL = data.schedule || [];',
         '  PERSONAL = data.personal || {};',
         '  EXTENDED = data.extended || [];',
         '  const local = loadLocalData();',
         '  if (local) {',
         '    if (local.schedule) SCHOOL = local.schedule;',
         '    if (local.personal) PERSONAL = local.personal;',
         '    if (local.extended) EXTENDED = local.extended;',
         '  }',
         '  renderAll();'])
new = J(['  SCHOOL = data.schedule || [];',
         '  PERSONAL = data.personal || {};',
         '  EXTENDED = data.extended || [];',
         '  CUSTOM = data.custom || {};',
         '  const local = loadLocalData();',
         '  if (local) {',
         '    if (local.schedule) SCHOOL = local.schedule;',
         '    if (local.personal) PERSONAL = local.personal;',
         '    if (local.custom) CUSTOM = local.custom;',
         '    if (local.extended) EXTENDED = local.extended;',
         '  }',
         '  buildToggles();',
         '  renderAll();'])
assert old in s, "7"
s = s.replace(old, new, 1)

# 8) boot block: remove static checked restore (buildToggles handles it)
old = J(['if (showPersonal) document.getElementById("personalToggle").checked = true;',
         'if (showExtended) document.getElementById("extendedToggle").checked = true;',
         ''])
assert old in s, "8"
s = s.replace(old, "", 1)

io.open(p, "w", encoding="utf-8", newline="").write(s)
print("nikol.js patched")
