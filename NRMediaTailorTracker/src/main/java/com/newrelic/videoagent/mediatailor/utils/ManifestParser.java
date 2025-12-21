package com.newrelic.videoagent.mediatailor.utils;

import android.util.Log;

import com.newrelic.videoagent.mediatailor.tracker.NRTrackerMediaTailor.AdBreak;
import com.newrelic.videoagent.mediatailor.tracker.NRTrackerMediaTailor.AdPod;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Manifest Parser Utilities for AWS MediaTailor
 * Parses HLS and DASH manifests to detect SCTE-35 ad markers
 * Based on VideoJS mt.js implementation
 */
public class ManifestParser {

    private static final String TAG = "MTManifestParser";

    /**
     * Parse HLS manifest text for SCTE-35 markers (CUE-OUT/CUE-IN)
     * Based on videoJS implementation in mt.js:205-331
     *
     * @param manifestText The HLS manifest content (.m3u8)
     * @return List of detected ad breaks with pod information
     */
    public static List<AdBreak> parseHLSManifest(String manifestText) {
        List<AdBreak> adBreaks = new ArrayList<>();

        if (manifestText == null || manifestText.isEmpty()) {
            Log.w(TAG, "Empty manifest text provided");
            return adBreaks;
        }

        String[] lines = manifestText.split("\n");

        double currentTime = 0;
        AdBreak currentAdBreak = null;
        List<AdPod> adPods = new ArrayList<>();
        Double currentPodStartTime = null;
        String lastMapUrl = null;
        boolean isInAdBreak = false;

        for (String line : lines) {
            line = line.trim();

            // Detect CUE-OUT (ad break start)
            if (line.startsWith("#EXT-X-CUE-OUT")) {
                Matcher matcher = MediaTailorConstants.REGEX_CUE_OUT.matcher(line);
                Double duration = null;

                if (matcher.find()) {
                    duration = Double.parseDouble(matcher.group(1));
                }

                isInAdBreak = true;
                adPods = new ArrayList<>();
                currentPodStartTime = currentTime;
                lastMapUrl = null;

                currentAdBreak = new AdBreak();
                currentAdBreak.id = "avail-manifest-" + currentTime;
                currentAdBreak.startTime = currentTime;
                currentAdBreak.duration = duration != null ? duration : 0;
                currentAdBreak.endTime = 0; // Will be set when CUE-IN is found
                currentAdBreak.source = MediaTailorConstants.AD_SOURCE_MANIFEST_CUE;

                Log.d(TAG, String.format("Found CUE-OUT at %.2fs, duration=%.2fs",
                    currentTime, currentAdBreak.duration));
            }

            // Detect CUE-IN (ad break end)
            else if (line.startsWith("#EXT-X-CUE-IN")) {
                if (currentAdBreak != null) {
                    // Close final pod
                    if (currentPodStartTime != null) {
                        double podDuration = currentTime - currentPodStartTime;
                        AdPod pod = new AdPod();
                        pod.startTime = currentPodStartTime;
                        pod.duration = podDuration;
                        pod.endTime = currentTime;
                        adPods.add(pod);

                        Log.d(TAG, String.format("  Final pod %.2fs-%.2fs (%.2fs)",
                            pod.startTime, pod.endTime, pod.duration));
                    }

                    // Calculate actual duration
                    double actualDuration = currentTime - currentAdBreak.startTime;

                    // Filter false positives (< MIN_AD_DURATION)
                    if (actualDuration >= MediaTailorConstants.MIN_AD_DURATION) {
                        currentAdBreak.duration = actualDuration;
                        currentAdBreak.endTime = currentTime;
                        currentAdBreak.pods = adPods;
                        adBreaks.add(currentAdBreak);

                        Log.d(TAG, String.format("CUE-IN at %.2fs, total duration=%.2fs, %d pod(s)",
                            currentTime, actualDuration, adPods.size()));
                    } else {
                        Log.d(TAG, String.format("Ignoring short ad break (%.2fs)", actualDuration));
                    }

                    // Reset state
                    currentAdBreak = null;
                    isInAdBreak = false;
                    currentPodStartTime = null;
                    lastMapUrl = null;
                    adPods = new ArrayList<>();
                }
            }

            // Detect MAP URL changes (pod boundaries)
            else if (isInAdBreak && line.startsWith("#EXT-X-MAP")) {
                Matcher matcher = MediaTailorConstants.REGEX_MAP.matcher(line);
                if (matcher.find()) {
                    String mapUrl = matcher.group(1);

                    if (mapUrl != null && !mapUrl.equals(lastMapUrl)) {
                        // New MAP = new pod boundary
                        if (currentPodStartTime != null && lastMapUrl != null) {
                            double podDuration = currentTime - currentPodStartTime;
                            AdPod pod = new AdPod();
                            pod.startTime = currentPodStartTime;
                            pod.duration = podDuration;
                            pod.endTime = currentTime;
                            adPods.add(pod);

                            Log.d(TAG, String.format("  Pod boundary (MAP change) %.2fs-%.2fs (%.2fs)",
                                pod.startTime, pod.endTime, pod.duration));
                        }
                        currentPodStartTime = currentTime;
                        lastMapUrl = mapUrl;
                    }
                }
            }

            // Track time via EXTINF (segment duration)
            else if (line.startsWith("#EXTINF:")) {
                Matcher matcher = MediaTailorConstants.REGEX_EXTINF.matcher(line);
                if (matcher.find()) {
                    double segmentDuration = Double.parseDouble(matcher.group(1));
                    currentTime += segmentDuration;
                }
            }
        }

        // Handle unclosed ad break (shouldn't happen with proper manifests)
        if (currentAdBreak != null && currentAdBreak.duration > 0) {
            currentAdBreak.endTime = currentAdBreak.startTime + currentAdBreak.duration;
            currentAdBreak.pods = adPods;
            adBreaks.add(currentAdBreak);
            Log.d(TAG, "Added unclosed ad break");
        }

        return adBreaks;
    }

    /**
     * Extract target duration from HLS manifest
     * Used to determine optimal polling interval for LIVE streams
     *
     * @param manifestText The HLS manifest content
     * @return Target duration in seconds, or 0 if not found
     */
    public static int extractTargetDuration(String manifestText) {
        if (manifestText == null || manifestText.isEmpty()) {
            return 0;
        }

        Matcher matcher = MediaTailorConstants.REGEX_TARGET_DURATION.matcher(manifestText);
        if (matcher.find()) {
            int targetDuration = Integer.parseInt(matcher.group(1));
            Log.d(TAG, "Found target duration: " + targetDuration + "s");
            return targetDuration;
        }

        return 0;
    }

    /**
     * Extract first variant stream URL from master playlist
     * Master playlists don't contain SCTE-35 markers - need to fetch variant playlist
     *
     * @param masterPlaylistText The master playlist content
     * @param baseUrl The base URL for resolving relative paths
     * @return First variant stream URL, or null if not found
     */
    public static String extractFirstVariantUrl(String masterPlaylistText, String baseUrl) {
        if (masterPlaylistText == null || masterPlaylistText.isEmpty()) {
            return null;
        }

        String[] lines = masterPlaylistText.split("\n");
        String lastStreamInfo = null;

        for (String line : lines) {
            line = line.trim();

            // Look for #EXT-X-STREAM-INF (variant stream definition)
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                lastStreamInfo = line;
            }
            // The next non-comment line after STREAM-INF is the variant URL
            else if (lastStreamInfo != null && !line.startsWith("#") && !line.isEmpty()) {
                String variantUrl = line;

                // If relative URL, make it absolute
                if (!variantUrl.startsWith("http")) {
                    // Extract base URL (remove master.m3u8 part)
                    int lastSlash = baseUrl.lastIndexOf('/');
                    if (lastSlash > 0) {
                        String base = baseUrl.substring(0, lastSlash + 1);
                        variantUrl = base + variantUrl;
                    }
                }

                Log.d(TAG, "Found variant stream URL: " + variantUrl);
                return variantUrl;
            }
        }

        Log.w(TAG, "No variant stream found in master playlist");
        return null;
    }

    /**
     * Check if manifest is a master playlist (contains #EXT-X-STREAM-INF)
     *
     * @param manifestText The manifest content
     * @return true if master playlist, false if media playlist
     */
    public static boolean isMasterPlaylist(String manifestText) {
        return manifestText != null && manifestText.contains("#EXT-X-STREAM-INF");
    }

    /**
     * Parse DASH manifest for SCTE-35 EventStream markers
     * TODO: Implement DASH manifest parsing
     * Based on videoJS mt.js:573-685
     *
     * @param xmlText The DASH manifest content (.mpd)
     * @return List of detected ad breaks
     */
    public static List<AdBreak> parseDASHManifest(String xmlText) {
        List<AdBreak> adBreaks = new ArrayList<>();

        // TODO: Implement DASH parsing
        // 1. Parse XML using Android XML parser
        // 2. Find <EventStream> elements with SCTE-35 schemeIdUri
        // 3. Extract <Event> elements with presentationTime, duration, id
        // 4. Convert timescale to seconds
        // 5. Create AdBreak objects from events

        Log.w(TAG, "DASH manifest parsing not yet implemented");
        return adBreaks;
    }

    /**
     * Merge manifest-detected ads with existing schedule
     * Deduplicates and enriches ads from both sources
     *
     * @param adSchedule Existing ad schedule (may contain tracking API data)
     * @param manifestAdBreaks Ad breaks detected from manifest parsing
     * @return Merged and deduplicated ad schedule
     */
    public static List<AdBreak> mergeAdSchedules(List<AdBreak> adSchedule, List<AdBreak> manifestAdBreaks) {
        if (manifestAdBreaks == null || manifestAdBreaks.isEmpty()) {
            return adSchedule;
        }

        // If no existing schedule, use manifest ads directly
        if (adSchedule.isEmpty()) {
            Log.d(TAG, "Using manifest ads as primary schedule");
            return new ArrayList<>(manifestAdBreaks);
        }

        List<AdBreak> mergedSchedule = new ArrayList<>(adSchedule);

        // Merge: Check for overlaps with tracking API data
        for (AdBreak manifestBreak : manifestAdBreaks) {
            boolean found = false;

            // Check if this break already exists (from tracking API)
            for (AdBreak existingBreak : mergedSchedule) {
                // Consider it the same break if start times are within AD_TIMING_TOLERANCE
                if (Math.abs(existingBreak.startTime - manifestBreak.startTime)
                        < MediaTailorConstants.AD_TIMING_TOLERANCE) {
                    found = true;

                    // Enrich tracking API break with manifest data
                    if (existingBreak.pods.isEmpty() && !manifestBreak.pods.isEmpty()) {
                        existingBreak.pods = manifestBreak.pods;
                        Log.d(TAG, String.format("Enriched break at %.2fs with %d manifest pod(s)",
                            existingBreak.startTime, manifestBreak.pods.size()));
                    }
                    existingBreak.source = MediaTailorConstants.AD_SOURCE_MANIFEST_AND_TRACKING;
                    break;
                }
            }

            // If not found, add manifest break to schedule
            if (!found) {
                mergedSchedule.add(manifestBreak);
                Log.d(TAG, String.format("Added new manifest-only break at %.2fs", manifestBreak.startTime));
            }
        }

        // Sort by start time (using Collections.sort for API 16+ compatibility)
        java.util.Collections.sort(mergedSchedule, new java.util.Comparator<AdBreak>() {
            @Override
            public int compare(AdBreak a, AdBreak b) {
                return Double.compare(a.startTime, b.startTime);
            }
        });

        Log.d(TAG, "Merged ad schedule now has " + mergedSchedule.size() + " break(s)");
        return mergedSchedule;
    }

    /**
     * Calculate ad position (pre/mid/post) for VOD streams
     *
     * @param adStartTime Ad break start time in seconds
     * @param contentDuration Total content duration in seconds
     * @param tolerance Time tolerance for pre/post detection
     * @return Ad position: "pre", "mid", or "post"
     */
    public static String calculateAdPosition(double adStartTime, double contentDuration, double tolerance) {
        if (adStartTime <= tolerance) {
            return MediaTailorConstants.AD_POSITION_PRE;
        } else if (adStartTime >= contentDuration - tolerance) {
            return MediaTailorConstants.AD_POSITION_POST;
        } else {
            return MediaTailorConstants.AD_POSITION_MID;
        }
    }
}
