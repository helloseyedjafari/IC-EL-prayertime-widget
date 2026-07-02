// Prayer Times — one-time INSTALLER
// -------------------------------------------------------------
// Easiest way to get the widget onto your iPhone:
//   1. Open Scriptable → tap + (new script).
//   2. Paste THIS whole file and tap ▶︎ (Run) once.
//   3. It downloads the latest "Prayer Times" script and adds it to Scriptable.
//   4. Add a Scriptable widget → choose "Prayer Times" → set Parameter to a city.
//   5. You can delete this installer afterwards.
//
// Re-run any time to update to the newest version.
// -------------------------------------------------------------

const RAW = "https://raw.githubusercontent.com/helloseyedjafari/IC-EL-prayertime-widget/main/ios-scriptable/prayer-times.js";
const NAME = "Prayer Times";

async function main() {
  const code = await new Request(RAW).loadString();
  if (!code || code.length < 500 || !code.includes("Scriptable widget")) {
    throw new Error("Download failed or looks wrong. Check your connection and try again.");
  }

  // Write into whichever Scriptable folder this installer itself lives in
  // (iCloud if enabled, otherwise on-device local), so it appears as a script.
  let fm;
  try {
    const icloud = FileManager.iCloud();
    fm = icloud.isFileStoredIniCloud(module.filename) ? icloud : FileManager.local();
  } catch (e) {
    fm = FileManager.local();
  }
  const path = fm.joinPath(fm.documentsDirectory(), NAME + ".js");
  fm.writeString(path, code);

  const a = new Alert();
  a.title = "Prayer Times installed ✓";
  a.message =
    'Added the "' + NAME + '" script.\n\n' +
    "Now: add a Scriptable widget to your home screen → Edit Widget → " +
    'Script = "' + NAME + '", Parameter = a city (London, Cardiff, Glasgow, ' +
    "Manchester, Newcastle; blank = London).\n\nYou can delete this installer.";
  a.addAction("OK");
  await a.present();
}

try {
  await main();
} catch (e) {
  const a = new Alert();
  a.title = "Install failed";
  a.message = String(e && e.message ? e.message : e);
  a.addAction("OK");
  await a.present();
}
Script.complete();
