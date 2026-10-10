// ---- Recommended addons directory (easy to extend: just add entries) ----
var ADDONS = [];

function copyText(text, btn) {
  function done() {
    btn.textContent = "Copied ✓";
    btn.classList.add("copied");
    setTimeout(function () {
      btn.textContent = "Copy";
      btn.classList.remove("copied");
    }, 1600);
  }
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(text).then(done, function () { fallback(); });
  } else { fallback(); }
  function fallback() {
    var ta = document.createElement("textarea");
    ta.value = text;
    document.body.appendChild(ta);
    ta.select();
    try { document.execCommand("copy"); } catch (e) {}
    document.body.removeChild(ta);
    done();
  }
}

function renderAddons(list) {
  var el = document.getElementById("addonList");
  if (!el) return;
  if (!list.length) {
    var sec = el.closest("section");
    if (sec) sec.style.display = "none";
    return;
  }
  el.innerHTML = "";
  list.forEach(function (a) {
    var card = document.createElement("div");
    card.className = "addon";
    var h = document.createElement("h3");
    h.textContent = a.name;
    var p = document.createElement("p");
    p.textContent = a.desc;
    var row = document.createElement("div");
    row.className = "addon-row";
    var url = document.createElement("div");
    url.className = "addon-url";
    url.textContent = a.url;
    url.title = a.url;
    var btn = document.createElement("button");
    btn.className = "copy-btn";
    btn.textContent = "Copy";
    btn.onclick = function () { copyText(a.url, btn); };
    row.appendChild(url);
    row.appendChild(btn);
    card.appendChild(h);
    card.appendChild(p);
    card.appendChild(row);
    el.appendChild(card);
  });
}

// Addon list lives in addons.json so it can be edited by hand.
// Falls back to the built-in list if the file can't be fetched.
fetch("addons.json")
  .then(function (r) { return r.ok ? r.json() : Promise.reject(); })
  .then(function (d) {
    var list = (d && d.addons && d.addons.length) ? d.addons : ADDONS;
    renderAddons(list);
  })
  .catch(function () { renderAddons(ADDONS); });

// Always point the download buttons at the newest release APKs.
(function () {
  var GITLAB_PROJECT = 87396069;
  var labBtn = document.getElementById('dlGitlab');
  var labVer = document.getElementById('dlVerLab');
  var hubBtn = document.getElementById('dlGithub');
  var hubVer = document.getElementById('dlVerHub');

  // GitLab: newest release with an .apk asset link.
  fetch('https://gitlab.com/api/v4/projects/' + GITLAB_PROJECT + '/releases?per_page=10')
    .then(function (r) { return r.json(); })
    .then(function (releases) {
      for (var i = 0; i < releases.length; i++) {
        var links = (releases[i].assets && releases[i].assets.links) || [];
        for (var j = 0; j < links.length; j++) {
          if (/\.apk$/i.test(links[j].url)) {
            labBtn.href = links[j].url;
            labVer.textContent = releases[i].tag_name;
            return;
          }
        }
      }
    })
    .catch(function () { /* keep the hardcoded fallback link */ });

  // GitHub: newest release with an .apk asset.
  fetch('https://api.github.com/repos/KotlinJS5/PulseStream/releases/latest')
    .then(function (r) { return r.ok ? r.json() : Promise.reject(); })
    .then(function (rel) {
      var assets = rel.assets || [];
      for (var i = 0; i < assets.length; i++) {
        if (/\.apk$/i.test(assets[i].name || '')) {
          hubBtn.href = assets[i].browser_download_url;
          hubVer.textContent = rel.tag_name;
          return;
        }
      }
    })
    .catch(function () { /* keep the releases-page fallback link */ });
})();
