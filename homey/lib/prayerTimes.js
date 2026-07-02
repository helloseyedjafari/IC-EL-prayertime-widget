"use strict";
// Thin wrapper over the shared parser core, using Node's built-in `https`
// (works on all Homey firmware Node versions — no global `fetch` dependency).
// homey/lib/prayer-core.js is a synced copy of shared/prayer-core.js.
const https = require("https");
const core = require("./prayer-core.js");

function fetchText(url, redirects = 2) {
  return new Promise((resolve, reject) => {
    https
      .get(url, { headers: { "User-Agent": "Homey-PrayerTimes/1.0" } }, (res) => {
        const { statusCode, headers } = res;
        if (statusCode >= 300 && statusCode < 400 && headers.location && redirects > 0) {
          res.resume();
          return resolve(fetchText(new URL(headers.location, url).toString(), redirects - 1));
        }
        if (statusCode !== 200) {
          res.resume();
          return reject(new Error("HTTP " + statusCode));
        }
        let data = "";
        res.setEncoding("utf8");
        res.on("data", (c) => (data += c));
        res.on("end", () => resolve(data));
      })
      .on("error", reject);
  });
}

async function getCity(city) {
  return core.getPrayerTimes(city, fetchText);
}

module.exports = { getCity, CITIES: core.CITIES };
