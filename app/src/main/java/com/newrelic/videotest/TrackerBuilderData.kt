package ca.bellmedia.lib.vidi.analytics.qos.trackers

import com.google.android.exoplayer2.Player

/**
 *  Data class holding a player instance and a setting to enable / disable ads tracking
 *  used by the tracker builder.
 */
data class TrackerBuilderData (val player: Player, val adsTrackingEnabled: Boolean)