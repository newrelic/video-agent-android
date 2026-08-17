package com.newrelic.videoagent.mediatailor.net;

import com.newrelic.videoagent.core.utils.NRLog;
import com.newrelic.videoagent.mediatailor.MTAdErrorCode;
import com.newrelic.videoagent.mediatailor.MTConstants;
import com.newrelic.videoagent.mediatailor.model.MTTrackingEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fetches AWS MediaTailor's client-side tracking metadata via {@code GET
 * /v1/tracking}. The response carries a short {@code Cache-Control: max-age}
 * — this is a cacheable-GET polling contract, not a POST one; a CDN fronting
 * MediaTailor may (and by default does) reject POST to this path entirely.
 *
 * <p>Instance lifecycle: <b>one client per playback session</b>, reused across
 * every poll. The pagination cursor is scoped to a single poll cycle: each
 * cycle reads the full current window from page 1 and follows {@code
 * NextToken} within that cycle until the window is exhausted. The cursor is
 * not carried into the next cycle — doing so would page past the window and
 * skip avails that are still relevant.</p>
 *
 * <p>Request shape:</p>
 * <ul>
 *   <li>First page of a cycle: {@code GET /v1/tracking/…} with no query
 *       params — returns the current manifest window and, if there is
 *       more, a {@code nextToken}.</li>
 *   <li>Subsequent pages <b>within the same cycle</b>: {@code GET
 *       /v1/tracking/…?NextToken=…} — returns the next page. The loop follows
 *       the cursor until the server stops issuing a new token or the page cap
 *       is hit, then merges every page's avails into one response.</li>
 *   <li>HTTP 400 on the first page: the token has expired. The client drops it
 *       and retries once with no query param; if that also 400s the response
 *       is surfaced as {@code null} so the caller degrades to manifest-only.</li>
 * </ul>
 *
 * <p>The cursor is deliberately <b>not</b> persisted across poll cycles.
 * Carrying it forward would page past the current window and skip avails that
 * are still relevant, so every cycle restarts token-less and re-reads the full
 * window.</p>
 */
public class MTTrackingClient {

    /**
     * Upper bound on pages followed within a single poll cycle. A well-behaved
     * MediaTailor window paginates in one or two pages; the cap stops a
     * runaway cursor loop from blocking the fetch thread indefinitely.
     */
    private static final int MAX_PAGES_PER_CYCLE = 5;

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile HttpURLConnection activeConnection;

    /**
     * Reason the most recent {@link #fetch(String)} returned {@code null}.
     * Reset to {@code null} at the top of every fetch; callers can read it
     * after a null response to distinguish a socket timeout from a persistent
     * token expiry.
     */
    private volatile MTAdErrorCode lastError;

    public MTAdErrorCode getLastError() {
        return lastError;
    }

    public void cancel() {
        cancelled.set(true);
        HttpURLConnection c = activeConnection;
        if (c != null) {
            try { c.disconnect(); } catch (Exception ignored) {}
        }
    }

    public MTTrackingResponse fetch(String trackingUrl) {
        // A cancel from a prior fetch would otherwise permanently poison this
        // client — but the tracker reuses one instance across polls, so treat
        // each cycle as a fresh attempt.
        cancelled.set(false);
        lastError = null;

        // Page 1 uses the retry budget (transient failures + one 400-recovery).
        MTTrackingResponse first = fetchFirstPage(trackingUrl);
        if (first == null) return null;

        // Follow the cursor within this same cycle, merging every page's avails
        // into the first response. A within-cycle token is a continuation of
        // the window we're already reading, so a mid-page transient failure
        // just truncates pagination — we still return the pages we have rather
        // than discarding a good first page.
        String token = first.nextToken;
        int page = 1;
        while (token != null && !token.isEmpty()) {
            if (cancelled.get()) return first;
            if (page >= MAX_PAGES_PER_CYCLE) {
                NRLog.w("MT tracking pagination cap (" + MAX_PAGES_PER_CYCLE
                        + " pages) hit — later avails in this window not fetched this cycle");
                break;
            }
            MTTrackingResponse next;
            try {
                next = fetchOnce(trackingUrl, token);
            } catch (Exception e) {
                if (cancelled.get()) return first;
                NRLog.d("MT tracking pagination page " + (page + 1) + " failed: " + e
                        + " — returning pages fetched so far");
                break;
            }
            if (next == null) break;
            first.avails.addAll(next.avails);
            first.nonLinearAvails.addAll(next.nonLinearAvails);
            // Guard against a server that echoes the same token forever.
            if (token.equals(next.nextToken)) break;
            token = next.nextToken;
            page++;
        }
        first.nextToken = null;
        return first;
    }

    private MTTrackingResponse fetchFirstPage(String trackingUrl) {
        int attempts = 0;
        boolean triedResetOn400 = false;
        boolean lastAttemptWasTimeout = false;
        while (attempts <= MTConstants.TRACKING_MAX_RETRIES) {
            if (cancelled.get()) return null;
            try {
                return fetchOnce(trackingUrl, null);
            } catch (TokenExpiredException e) {
                // The server rejected an empty-body request with 400 only on
                // the reset retry — treat a persistent 400 as tracking being
                // unavailable. (First page carries no token, so a 400 here is
                // unusual, but the recovery path is kept for parity.)
                if (!triedResetOn400) {
                    NRLog.d("MT tracking HTTP 400 on first page — retrying fresh window");
                    triedResetOn400 = true;
                } else {
                    NRLog.w("MT tracking HTTP 400 persists — tracking unavailable");
                    lastError = MTAdErrorCode.TOKEN_EXPIRED;
                    return null;
                }
            } catch (SocketTimeoutException e) {
                if (cancelled.get()) return null;
                NRLog.d("MT tracking fetch attempt " + (attempts + 1) + " timed out");
                lastAttemptWasTimeout = true;
                attempts++;
            } catch (Exception e) {
                if (cancelled.get()) return null;
                NRLog.d("MT tracking fetch attempt " + (attempts + 1) + " failed: " + e);
                lastAttemptWasTimeout = false;
                attempts++;
            }
        }
        // Retry budget exhausted. Distinguish a timeout (points at ADS
        // latency) from a generic transport failure (network, DNS, non-2xx)
        // so downstream can alert on the right thing.
        lastError = lastAttemptWasTimeout
                ? MTAdErrorCode.ADS_TIMEOUT
                : MTAdErrorCode.TRACKING_FETCH_FAILED;
        return null;
    }

    private MTTrackingResponse fetchOnce(String trackingUrl, String token) throws IOException, JSONException {
        URL url = new URL(buildRequestUrl(trackingUrl, token));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        activeConnection = conn;
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(MTConstants.TRACKING_TIMEOUT_MS);
            conn.setReadTimeout(MTConstants.TRACKING_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_BAD_REQUEST) {
                throw new TokenExpiredException();
            }
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

    private static String buildRequestUrl(String trackingUrl, String token) {
        if (token == null || token.isEmpty()) return trackingUrl;
        try {
            String encoded = java.net.URLEncoder.encode(token, "UTF-8");
            return trackingUrl + (trackingUrl.contains("?") ? "&" : "?") + "NextToken=" + encoded;
        } catch (java.io.UnsupportedEncodingException e) {
            // UTF-8 is always supported; unreachable in practice.
            return trackingUrl;
        }
    }

    static MTTrackingResponse parse(String json) throws JSONException {
        MTTrackingResponse out = new MTTrackingResponse();
        JSONObject root = new JSONObject(json);
        String token = root.optString("nextToken", null);
        // optString returns "" (not null) when the key is present but empty,
        // which would poison the equality check in rememberToken.
        out.nextToken = (token != null && !token.isEmpty()) ? token : null;
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
        // Bumpers aren't tagged in the JSON schema; MediaTailor customers
        // conventionally label them via adSystem, adTitle, or adId. Best-effort.
        ad.isBumper = containsIgnoreCase(ad.adSystem, "bumper")
                || containsIgnoreCase(ad.adTitle, "bumper")
                || containsIgnoreCase(ad.adId, "bumper");
        // startTimeInSeconds means something different inside a tracking
        // event than at the avail/ad level: here it's relative to the ad's
        // own start, not to the playback session. Rename during parse so
        // downstream can't accidentally treat it as an absolute timeline
        // position — that would fire beacons at wildly wrong moments.
        JSONArray tev = adJson.optJSONArray("trackingEvents");
        if (tev != null) {
            for (int k = 0; k < tev.length(); k++) {
                JSONObject e = tev.getJSONObject(k);
                MTTrackingEvent event = new MTTrackingEvent();
                event.eventType = e.optString("eventType", null);
                event.relativeToAdStartMs = toMs(e.opt("startTimeInSeconds"));
                JSONArray urls = e.optJSONArray("beaconUrls");
                if (urls != null) {
                    for (int u = 0; u < urls.length(); u++) {
                        String url = urls.optString(u, null);
                        if (url != null && !url.isEmpty()) event.beaconUrls.add(url);
                    }
                }
                ad.trackingEvents.add(event);
            }
        }
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

    /** Signals HTTP 400 so the outer loop can distinguish token expiry from
     *  generic transient failures (which use the retry budget). */
    private static final class TokenExpiredException extends IOException {
        TokenExpiredException() { super("Tracking API HTTP 400 (expired NextToken)"); }
    }
}
