package com.newrelic.videoagent.mediatailor.net;

import com.newrelic.videoagent.core.utils.NRLog;
import com.newrelic.videoagent.mediatailor.MTConstants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fetches and parses AWS MediaTailor's client-side tracking metadata.
 *
 * <p>Instance lifecycle:</p>
 * <ol>
 *   <li>Construct once per tracking-fetch attempt.</li>
 *   <li>Call {@link #fetch(String)} from a worker thread — the request
 *       synchronously blocks that thread until the response arrives or the
 *       configured timeout elapses.</li>
 *   <li>If disposal races in, call {@link #cancel()} to abort the open
 *       connection; the in-flight {@code fetch} then returns {@code null}.</li>
 * </ol>
 *
 * <p>Implementation notes:</p>
 * <ul>
 *   <li>Uses only {@link HttpURLConnection} + {@code org.json} — the module
 *       deliberately avoids OkHttp and the AWS SDK.</li>
 *   <li>Cache-busts each request with a {@code ?t=<millis>} query param
 *       (MediaTailor otherwise serves cached JSON).</li>
 *   <li>Retries once on exception; returns {@code null} after that.</li>
 * </ul>
 */
public class MTTrackingClient {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile HttpURLConnection activeConnection;

    public void cancel() {
        cancelled.set(true);
        HttpURLConnection c = activeConnection;
        if (c != null) {
            try { c.disconnect(); } catch (Exception ignored) {}
        }
    }

    public MTTrackingResponse fetch(String trackingUrl) {
        int attempts = 0;
        while (attempts <= MTConstants.TRACKING_MAX_RETRIES) {
            if (cancelled.get()) return null;
            try {
                return fetchOnce(trackingUrl);
            } catch (Exception e) {
                if (cancelled.get()) return null;
                NRLog.d("MT tracking fetch attempt " + (attempts + 1) + " failed: " + e);
                attempts++;
            }
        }
        return null;
    }

    private MTTrackingResponse fetchOnce(String trackingUrl) throws IOException, JSONException {
        URL url = new URL(trackingUrl + "?t=" + System.currentTimeMillis());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        activeConnection = conn;
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(MTConstants.TRACKING_TIMEOUT_MS);
            conn.setReadTimeout(MTConstants.TRACKING_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Tracking API HTTP " + code);
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (cancelled.get()) return null;
                    sb.append(line);
                }
            }
            return parse(sb.toString());
        } finally {
            activeConnection = null;
            try { conn.disconnect(); } catch (Exception ignored) {}
        }
    }

    static MTTrackingResponse parse(String json) throws JSONException {
        MTTrackingResponse out = new MTTrackingResponse();
        JSONObject root = new JSONObject(json);
        JSONArray availsJson = root.optJSONArray("avails");
        if (availsJson != null) {
            for (int i = 0; i < availsJson.length(); i++) {
                out.avails.add(parseAvail(availsJson.getJSONObject(i)));
            }
        }
        JSONArray nonLinearJson = root.optJSONArray("nonLinearAvails");
        if (nonLinearJson != null) {
            for (int i = 0; i < nonLinearJson.length(); i++) {
                JSONObject n = nonLinearJson.getJSONObject(i);
                MTTrackingResponse.NonLinearAvail nl = new MTTrackingResponse.NonLinearAvail();
                nl.availId = n.optString("availId", null);
                nl.startTimeMs = toMs(n.opt("startTimeInSeconds"));
                nl.durationMs = toMs(n.opt("durationInSeconds"));
                JSONArray ads = n.optJSONArray("ads");
                nl.adCount = ads != null ? ads.length() : 0;
                out.nonLinearAvails.add(nl);
            }
        }
        return out;
    }

    private static MTTrackingResponse.Avail parseAvail(JSONObject a) throws JSONException {
        MTTrackingResponse.Avail avail = new MTTrackingResponse.Avail();
        avail.availId = a.optString("availId", null);
        avail.startTimeMs = toMs(a.opt("startTimeInSeconds"));
        avail.durationMs = toMs(a.opt("durationInSeconds"));
        avail.availProgramDateTime = a.optString("availProgramDateTime", null);
        JSONArray adsJson = a.optJSONArray("ads");
        if (adsJson != null) {
            for (int j = 0; j < adsJson.length(); j++) {
                avail.ads.add(parseAd(adsJson.getJSONObject(j)));
            }
        }
        return avail;
    }

    private static MTTrackingResponse.Ad parseAd(JSONObject adJson) throws JSONException {
        MTTrackingResponse.Ad ad = new MTTrackingResponse.Ad();
        ad.adId = adJson.optString("adId", null);
        ad.adTitle = adJson.optString("adTitle", null);
        ad.startTimeMs = toMs(adJson.opt("startTimeInSeconds"));
        ad.durationMs = toMs(adJson.opt("durationInSeconds"));
        ad.adSystem = adJson.optString("adSystem", null);
        ad.creativeId = adJson.optString("creativeId", null);
        ad.creativeSequence = adJson.optString("creativeSequence", null);
        ad.vastAdId = adJson.optString("vastAdId", null);
        ad.skipOffset = adJson.optString("skipOffset", null);
        ad.adProgramDateTime = adJson.optString("adProgramDateTime", null);
        // Heuristic: MediaTailor bumpers aren't explicitly flagged in the JSON,
        // but they typically surface with an ad system name like "Bumper" or
        // an ad title/id containing "bumper". Loose match, case-insensitive.
        ad.isBumper = containsIgnoreCase(ad.adSystem, "bumper")
                || containsIgnoreCase(ad.adTitle, "bumper")
                || containsIgnoreCase(ad.adId, "bumper");
        return ad;
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase().contains(needle);
    }

    private static long toMs(Object seconds) {
        if (seconds == null) return 0L;
        if (seconds instanceof Number) {
            return Math.round(((Number) seconds).doubleValue() * 1000.0);
        }
        try {
            return Math.round(Double.parseDouble(seconds.toString()) * 1000.0);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
