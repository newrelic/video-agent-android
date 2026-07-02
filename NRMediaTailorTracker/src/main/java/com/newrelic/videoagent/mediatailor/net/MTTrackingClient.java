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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fetches AWS MediaTailor's client-side tracking metadata via the documented
 * {@code POST /v1/tracking} contract.
 *
 * <p>Instance lifecycle: <b>one client per playback session</b>, reused across
 * every poll. The client holds the current {@code NextToken} — creating a
 * fresh instance on every fetch would discard the pagination cursor and cause
 * the server to re-send the same window each time (wasted parse cycles) or
 * skip beacons that arrived between polls (missed events).</p>
 *
 * <p>Request shape (per the AWS spec):</p>
 * <ul>
 *   <li>First fetch: {@code POST /v1/tracking/…} with body {@code {}} —
 *       returns the full manifest window and a {@code nextToken}.</li>
 *   <li>Subsequent fetches: {@code POST /v1/tracking/…} with body
 *       {@code {"NextToken":"…"}} — returns anything new since that cursor.
 *       If the server responds with the same token, there is nothing new.</li>
 *   <li>HTTP 400: the token has expired (they live for ~24h). The client
 *       drops the token and retries once with an empty body; if the second
 *       call also 400s the response is surfaced as {@code null} so the caller
 *       can degrade to manifest-only detection.</li>
 * </ul>
 *
 * <p>To dodge the forced expiry round-trip, the client resets its own token
 * pre-emptively at 23 hours of age. Nothing else about the wire is stateful
 * — the whole cursor lives here.</p>
 */
public class MTTrackingClient {

    /** Pre-emptive reset threshold — one hour before the server's 24h expiry. */
    private static final long TOKEN_MAX_AGE_MS = 23L * 60L * 60L * 1000L;

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile HttpURLConnection activeConnection;

    /** Retained across fetches so pagination survives poll boundaries. */
    private volatile String nextToken;
    private volatile long tokenIssuedAtMs;

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

    /**
     * Clears any retained cursor so the next fetch starts a fresh manifest
     * window. Called on activate/deactivate boundaries where continuity across
     * sessions would be wrong.
     */
    public void resetToken() {
        nextToken = null;
        tokenIssuedAtMs = 0L;
    }

    public MTTrackingResponse fetch(String trackingUrl) {
        // A cancel from a prior fetch would otherwise permanently poison this
        // client — but the tracker reuses one instance across polls, so treat
        // each fetch as a fresh attempt.
        cancelled.set(false);
        lastError = null;

        // The server would reject an over-age token with HTTP 400 anyway; save
        // the round-trip by dropping it ourselves once we're close to expiry.
        if (nextToken != null
                && tokenIssuedAtMs > 0L
                && System.currentTimeMillis() - tokenIssuedAtMs > TOKEN_MAX_AGE_MS) {
            NRLog.d("MT tracking pre-emptive NextToken reset (age > 23h)");
            resetToken();
        }

        int attempts = 0;
        boolean triedResetOn400 = false;
        boolean lastAttemptWasTimeout = false;
        while (attempts <= MTConstants.TRACKING_MAX_RETRIES) {
            if (cancelled.get()) return null;
            try {
                MTTrackingResponse resp = fetchOnce(trackingUrl);
                if (resp != null) rememberToken(resp.nextToken);
                return resp;
            } catch (TokenExpiredException e) {
                // A 400 here means the server no longer recognises our token
                // (the "same" session was rebalanced onto another node, or the
                // token slipped past its 24h horizon between our pre-emptive
                // check and the server's clock). One reset-and-retry is the
                // documented recovery path.
                if (!triedResetOn400) {
                    NRLog.d("MT tracking HTTP 400 — dropping NextToken and retrying with fresh window");
                    resetToken();
                    triedResetOn400 = true;
                    // retry doesn't count against the transient-failure budget
                } else {
                    NRLog.w("MT tracking HTTP 400 persists after reset — tracking unavailable");
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

    private void rememberToken(String token) {
        if (token == null || token.isEmpty()) return;
        // A repeated token means "no new beacons since your last poll" — the
        // issuance time hasn't actually moved, so leave tokenIssuedAtMs alone
        // and let the pre-emptive-reset clock keep counting from first issue.
        if (!token.equals(this.nextToken)) {
            this.nextToken = token;
            this.tokenIssuedAtMs = System.currentTimeMillis();
        }
    }

    private MTTrackingResponse fetchOnce(String trackingUrl) throws IOException, JSONException {
        URL url = new URL(trackingUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        activeConnection = conn;
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(MTConstants.TRACKING_TIMEOUT_MS);
            conn.setReadTimeout(MTConstants.TRACKING_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json");

            byte[] body = buildRequestBody().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(body.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }

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

    private String buildRequestBody() {
        String token = nextToken;
        if (token == null) return "{}";
        return "{\"NextToken\":\"" + escapeJson(token) + "\"}";
    }

    /**
     * Escapes only the characters that must be escaped inside a JSON string.
     * NextToken is base64-derived so in practice the loop finds nothing, but
     * a defensive escape avoids a wire malformation if AWS ever widens the
     * character set.
     */
    private static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
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
