(async () => {
  const input = document.querySelector(".search");
  const list = document.querySelector("ul.entries");
  const filters = document.querySelectorAll(".filter");
  if (!input || !list) return;

  let index = [];
  try {
    const res = await fetch("search-index.json");
    index = await res.json();
  } catch (e) { return; }

  const items = Array.from(list.querySelectorAll("li[data-id]"));
  const byId = new Map(items.map(el => [el.dataset.id, el]));
  let activeRecipient = null;

  function apply() {
    const q = input.value.trim().toLowerCase();
    let shown = 0;
    for (const it of items) it.style.display = "none";
    for (const rec of index) {
      const el = byId.get(rec.id);
      if (!el) continue;
      const matchQ = !q || rec.text.includes(q) || rec.subject.toLowerCase().includes(q);
      const matchF = !activeRecipient || (rec.recipients && rec.recipients.includes(activeRecipient));
      if (matchQ && matchF) { el.style.display = ""; shown++; }
    }
    const empty = document.querySelector(".empty");
    if (empty) empty.style.display = shown === 0 ? "" : "none";
  }

  input.addEventListener("input", apply);
  filters.forEach(btn => btn.addEventListener("click", () => {
    const target = btn.dataset.filter || null;
    if (target === activeRecipient) { activeRecipient = null; btn.classList.remove("active"); }
    else {
      activeRecipient = target;
      filters.forEach(b => b.classList.toggle("active", b === btn));
    }
    apply();
  }));
})();
