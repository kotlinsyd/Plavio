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

// Use the same stable mobile manifest as the in-app updater. If it cannot be
// validated or fetched, leave generic release-page links rather than claiming
// an old APK is current.
(function () {
  var STABLE_URL = 'https://gitlab.com/api/v4/projects/87396069/repository/files/stable.json/raw?ref=main';
  var labBtn = document.getElementById('dlGitlab');
  var labVer = document.getElementById('dlVerLab');
  var hubBtn = document.getElementById('dlGithub');
  var hubVer = document.getElementById('dlVerHub');
  var versionPattern = /^\d+\.\d+\.\d+$/;

  function isValidMobileStable(stable) {
    if (!stable || stable.platform !== 'mobile' || stable.channel !== 'stable' ||
        stable.artifactType !== 'apk' || !versionPattern.test(stable.version || '')) return false;
    var expected = 'https://gitlab.com/api/v4/projects/87396069/packages/generic/plavio/' +
      stable.version + '/Plavio-v' + stable.version + '.apk';
    return stable.apkUrl === expected;
  }

  fetch(STABLE_URL)
    .then(function (r) { return r.ok ? r.json() : Promise.reject(); })
    .then(function (stable) {
      if (!isValidMobileStable(stable)) return;
      labBtn.href = stable.apkUrl;
      labVer.textContent = 'v' + stable.version + ' · stable';

      // Update the structured-data version from the validated stable pointer.
      var appSchema = document.getElementById('softwareApplicationSchema');
      if (appSchema) {
        try {
          var schema = JSON.parse(appSchema.textContent);
          schema.softwareVersion = stable.version;
          appSchema.textContent = JSON.stringify(schema);
        } catch (e) {}
      }

      // A GitHub APK is shown only when that exact stable version has the
      // canonical mobile artifact. Never substitute GitHub's latest release.
      return fetch('https://api.github.com/repos/kotlinsyd/Plavio/releases/tags/v' + stable.version)
        .then(function (r) { return r.ok ? r.json() : Promise.reject(); })
        .then(function (rel) {
          var tagVersion = (rel.tag_name || '').replace(/^v/i, '');
          if (tagVersion !== stable.version) return;
          var assets = rel.assets || [];
          for (var i = 0; i < assets.length; i++) {
            if (assets[i].name === 'Plavio-v' + stable.version + '.apk' &&
                /^https:\/\/github\.com\//i.test(assets[i].browser_download_url || '')) {
              hubBtn.href = assets[i].browser_download_url;
              hubVer.textContent = 'v' + stable.version + ' · stable';
              return;
            }
          }
        });
    })
    .catch(function () { /* leave generic release-page links and labels */ });
})();
