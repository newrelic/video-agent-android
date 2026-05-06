package com.newrelic.nrvideoproject;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.newrelic.videoagent.core.NRVideo;
import com.newrelic.videoagent.core.NRVideoPlayerConfiguration;
import com.newrelic.videoagent.core.NRVideoPlayerConfiguration.AdTrackerType;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Sample that drives a MediaTailor SSAI stream end-to-end:
 *
 * <ol>
 *     <li>POST the MediaTailor session-init endpoint →
 *         {@code { "manifestUrl": "...?aws.sessionId=...", "trackingUrl": "..." }}.</li>
 *     <li>Prefix both returned paths with the MediaTailor hostname.</li>
 *     <li>Hand the sessionized manifest URL to ExoPlayer.</li>
 *     <li>Pass the tracking URL explicitly to {@link NRTrackerMediaTailor#setTrackingUrl(String)}
 *         so the tracker doesn't have to parse it out of the manifest URL.</li>
 * </ol>
 *
 * <p>This is the recommended flow. Implicit sessions (client just GETs
 * {@code /v1/dash/.../index.mpd}) also work — the tracker rescues the session
 * id from the MPD {@code <Location>} element — but explicit is more reliable.</p>
 */
public class VideoPlayerMediaTailor extends AppCompatActivity {

    private static final String TAG = "VideoPlayerMT";

    // ── MediaTailor config prefixes (one per protocol) ───────────────────────
    // Paste the "Session initialization prefix" from the AWS MediaTailor console
    // for each of your configs (HLS, DASH). Only the one matching PROTOCOL
    // below is used at runtime; both can be set for quick switching.
    //
    // Example from the console:
    //   https://<hash>.mediatailor.<region>.amazonaws.com/v1/session/<hash>/<configName>/

    private static final String MT_DASH_SESSION_INIT_PREFIX =
            "https://<HASH>.mediatailor.<REGION>.amazonaws.com"
                    + "/v1/session/<ACCOUNT>/<DASH_CONFIG>/";
    private static final String MT_DASH_MANIFEST_PATH = "index.mpd";

    private static final String MT_HLS_SESSION_INIT_PREFIX =
            "https://<HASH>.mediatailor.<REGION>.amazonaws.com"
                    + "/v1/session/<ACCOUNT>/<HLS_CONFIG>/";
    private static final String MT_HLS_MANIFEST_PATH = "master.m3u8";

    // Which protocol to exercise. Can be overridden at runtime via the
    // `protocol` intent extra (values: "dash" | "hls").
    private static final String DEFAULT_PROTOCOL = "hls";

    // MediaTailor reporting mode passed to session init.
    private static final String MT_REPORTING_MODE = "server";

    // Custom adsParams forwarded to MediaTailor at session init and then to
    // the ADS (Google Ad Manager, FreeWheel, etc.) via [player_params.*] tokens
    // configured in the MediaTailor ADS request template.
    // Example keys: deviceType, contentCategory, userId, geoCountry, rating.
    private static final Map<String, String> MT_ADS_PARAMS = new HashMap<>();
    static {
        MT_ADS_PARAMS.put("deviceType", "androidmobile");
        MT_ADS_PARAMS.put("platform", "nr-video-agent-android");
    }

    private ExoPlayer player;
    private Integer trackerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player_mediatailor);

        // DEBUG-ONLY: relax SSL trust so content segments served from a
        // Cloudflare quick-tunnel (or any host whose cert chain isn't in the
        // emulator's trust store) can be fetched. This installs a global
        // trust-all socket factory — do not ship this.
        if (BuildConfig.DEBUG) {
            installTrustAllSsl();
        }

        String protocol = getIntent().getStringExtra("protocol");
        if (protocol == null || protocol.isEmpty()) protocol = DEFAULT_PROTOCOL;
        boolean hls = "hls".equalsIgnoreCase(protocol);

        String overrideInit = getIntent().getStringExtra("sessionInitUrl");
        String sessionInitUrl;
        if (overrideInit != null && !overrideInit.isEmpty()) {
            sessionInitUrl = overrideInit;
        } else if (hls) {
            sessionInitUrl = MT_HLS_SESSION_INIT_PREFIX + MT_HLS_MANIFEST_PATH;
        } else {
            sessionInitUrl = MT_DASH_SESSION_INIT_PREFIX + MT_DASH_MANIFEST_PATH;
        }

        Log.v(TAG, "Protocol=" + protocol + " Session init URL: " + sessionInitUrl);

        // POST on a worker thread; resume player setup on main thread.
        final String finalInitUrl = sessionInitUrl;
        new Thread(() -> {
            try {
                SessionInitResult r = postSessionInit(finalInitUrl);
                Log.d(TAG, "manifestUrl=" + r.manifestUrl + " trackingUrl=" + r.trackingUrl);
                new Handler(Looper.getMainLooper()).post(
                        () -> startPlayback(r.manifestUrl, r.trackingUrl));
            } catch (Exception e) {
                Log.e(TAG, "Session init failed", e);
            }
        }, "MT-session-init").start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (trackerId != null) {
            NRVideo.releaseTracker(trackerId);
        }
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
    }

    // ── main-thread wiring once session init returns ─────────────────────────

    private void startPlayback(String manifestUrl, String trackingUrl) {
        player = new ExoPlayer.Builder(this).build();

        Map<String, Object> customAttrs = new HashMap<>();
        customAttrs.put("contentTitle", "MediaTailor SSAI Sample");
        customAttrs.put("contentProvider", "aws-mediatailor-demo");

        NRVideoPlayerConfiguration cfg = new NRVideoPlayerConfiguration(
                "mediatailor-player",
                player,
                AdTrackerType.MEDIA_TAILOR,
                customAttrs);

        trackerId = NRVideo.addPlayer(cfg);
        Log.d(TAG, "MediaTailor tracker registered, id=" + trackerId);

        // The tracker auto-derives the tracking URL from the sessionized
        // manifest URL passed to ExoPlayer below — no setTrackingUrl() call
        // needed in the normal flow. If you ever hand ExoPlayer a manifest
        // URL that doesn't carry aws.sessionId (rare), grab the adTracker
        // via NewRelicVideoAgent.getInstance().getAdTracker(trackerId) and
        // call setTrackingUrl(trackingUrl) explicitly.

        PlayerView playerView = findViewById(R.id.player);
        playerView.setPlayer(player);

        player.setMediaItem(MediaItem.fromUri(Uri.parse(manifestUrl)));
        player.setPlayWhenReady(true);
        player.prepare();
    }

    // ── DEBUG-ONLY SSL relaxation ────────────────────────────────────────────

    private static boolean sslInstalled = false;

    private static void installTrustAllSsl() {
        if (sslInstalled) return;
        try {
            TrustManager[] trustAll = new TrustManager[] {
                new X509TrustManager() {
                    @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                @Override public boolean verify(String hostname, SSLSession session) { return true; }
            });
            sslInstalled = true;
            Log.w(TAG, "DEBUG SSL trust-all installed — do not ship this");
        } catch (Exception e) {
            Log.e(TAG, "Failed to install trust-all SSL", e);
        }
    }

    // ── explicit session init ────────────────────────────────────────────────

    private static class SessionInitResult {
        final String manifestUrl;
        final String trackingUrl;
        SessionInitResult(String m, String t) { this.manifestUrl = m; this.trackingUrl = t; }
    }

    /**
     * POSTs the MediaTailor session-init endpoint and resolves the returned
     * {@code manifestUrl} / {@code trackingUrl} against the endpoint hostname.
     * MediaTailor returns those as root-relative paths (e.g. {@code /v1/dash/...})
     * — the caller has to prepend the host.
     */
    private static SessionInitResult postSessionInit(String initUrl) throws IOException {
        URL url = new URL(initUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            JSONObject requestBody = new JSONObject();
            requestBody.put("reportingMode", MT_REPORTING_MODE);
            if (!MT_ADS_PARAMS.isEmpty()) {
                requestBody.put("adsParams", new JSONObject(MT_ADS_PARAMS));
            }
            byte[] body = requestBody.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }
            int code = conn.getResponseCode();
            Log.d(TAG, "Session init HTTP " + code);
            if (code < 200 || code >= 300) {
                throw new IOException("Session init HTTP " + code);
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            String responseJson = sb.toString();
            Log.d(TAG, "Session init response: "
                    + (responseJson.length() > 800
                            ? responseJson.substring(0, 800) + "…[truncated]"
                            : responseJson));
            JSONObject resp = new JSONObject(responseJson);
            String manifestPath = resp.optString("manifestUrl", null);
            String trackingPath = resp.optString("trackingUrl", null);
            if (manifestPath == null) {
                throw new IOException("Session init response missing manifestUrl");
            }
            String host = url.getProtocol() + "://" + url.getHost()
                    + (url.getPort() == -1 ? "" : (":" + url.getPort()));
            String manifest = manifestPath.startsWith("http") ? manifestPath : host + manifestPath;
            String tracking = trackingPath == null
                    ? null
                    : (trackingPath.startsWith("http") ? trackingPath : host + trackingPath);
            return new SessionInitResult(manifest, tracking);
        } catch (org.json.JSONException je) {
            throw new IOException("Session init JSON parse failed", je);
        } finally {
            conn.disconnect();
        }
    }
}
