/* Tijori website script.
   Runs entirely in the browser: no network requests, no cookies, no storage.
   The passphrase demo uses the same EFF word list and the same secure random source idea as the app. */
(function () {
  "use strict";
  document.documentElement.classList.add("js");

  var y = document.querySelector("[data-year]");
  if (y) y.textContent = String(new Date().getFullYear());

  var menu = document.querySelector(".menu");
  if (menu) {
    menu.addEventListener("click", function (e) { if (e.target.closest("a")) menu.removeAttribute("open"); });
    document.addEventListener("keydown", function (e) { if (e.key === "Escape") menu.removeAttribute("open"); });
  }

  /* ---------- Passphrase demo ---------- */
  var box = document.querySelector("[data-pp]");
  var words = window.TIJORI_WORDS;
  if (!box || !words || !window.crypto || !window.crypto.getRandomValues) return;
  var out = box.querySelector("[data-pp-out]");
  var meta = box.querySelector("[data-pp-meta]");
  var count = 5;

  // Unbiased pick: reject values that would favour the first words.
  function pick(n) {
    var limit = Math.floor(4294967296 / n) * n, buf = new Uint32Array(1);
    do { window.crypto.getRandomValues(buf); } while (buf[0] >= limit);
    return buf[0] % n;
  }

  function years(bits) {
    // An attacker with a GPU rig trying 10,000 guesses a second against Argon2id (64 MiB), on average half the space.
    var y = Math.pow(2, bits - 1) / 10000 / 31557600;
    if (y > 1e9) return "billions of years";
    if (y > 1e6) return Math.round(y / 1e6).toLocaleString("en-IN") + " million years";
    if (y > 1e3) return Math.round(y / 1e3).toLocaleString("en-IN") + " thousand years";
    return Math.max(1, Math.round(y)).toLocaleString("en-IN") + " years";
  }

  function make() {
    out.textContent = "";
    for (var i = 0; i < count; i++) {
      var s = document.createElement("span"), n = document.createElement("small");
      n.textContent = String(i + 1);
      s.appendChild(n);
      s.appendChild(document.createTextNode(words[pick(words.length)]));
      out.appendChild(s);
    }
    var bits = Math.floor(count * Math.log2(words.length));
    meta.innerHTML = "";
    var b = document.createElement("b");
    b.textContent = bits + " bits.";
    meta.appendChild(b);
    meta.appendChild(document.createTextNode(" At 10,000 guesses a second, guessing it would take about " + years(bits) + "."));
  }

  box.querySelectorAll("[data-count]").forEach(function (btn) {
    btn.addEventListener("click", function () {
      count = Number(btn.getAttribute("data-count"));
      box.querySelectorAll("[data-count]").forEach(function (o) { o.setAttribute("aria-pressed", String(o === btn)); });
      make();
    });
  });
  var again = box.querySelector("[data-pp-new]");
  if (again) again.addEventListener("click", make);
  box.hidden = false;
  var nojs = document.querySelector("[data-pp-nojs]");
  if (nojs) nojs.hidden = true;
  make();
})();
