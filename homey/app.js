"use strict";
const Homey = require("homey");
const prayer = require("./lib/prayerTimes.js");

module.exports = class PrayerApp extends Homey.App {
  async onInit() {
    this.cache = new Map();
    await this.refreshAll();
    this.scheduleRefresh();
    this.log("Prayer Times app initialized");
  }

  async refreshAll() {
    for (const city of prayer.CITIES) {
      try {
        const data = await prayer.getCity(city);
        this.cache.set(city, data);
        await this.homey.api.realtime("prayertimes:update", { ...data, stale: false });
      } catch (e) {
        this.error("refresh failed for", city, e.message);
      }
    }
  }

  scheduleRefresh() {
    // hourly safety tick (also catches the daily rollover within an hour)
    this.homey.setInterval(() => this.refreshAll(), 60 * 60 * 1000);

    // precise daily rollover: one-shot at the next Europe/London 00:05, then re-arm
    const armMidnight = () => {
      const parts = new Intl.DateTimeFormat("en-GB", {
        timeZone: "Europe/London", hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false,
      }).formatToParts(new Date()).reduce((o, p) => ((o[p.type] = p.value), o), {});
      const secsToday = +parts.hour * 3600 + +parts.minute * 60 + +parts.second;
      const target = 5 * 60; // 00:05 London
      let wait = target - secsToday;
      if (wait <= 0) wait += 86400;
      this.homey.setTimeout(async () => {
        await this.refreshAll();
        armMidnight();
      }, wait * 1000);
    };
    armMidnight();
  }
};
