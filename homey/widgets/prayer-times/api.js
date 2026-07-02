"use strict";
const prayer = require("../../lib/prayerTimes.js");

module.exports = {
  async getToday({ homey, query }) {
    const city = prayer.CITIES.includes(query.city) ? query.city : "London";
    try {
      const data = await prayer.getCity(city);
      homey.app.cache.set(city, data);
      return { ...data, stale: false };
    } catch (e) {
      const cached = homey.app.cache.get(city);
      if (cached) return { ...cached, stale: true };
      throw e;
    }
  },
};
