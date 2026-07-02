package com.newrelic.videoagent.mediatailor.schedule;

import com.newrelic.videoagent.mediatailor.MTAdErrorCode;
import com.newrelic.videoagent.mediatailor.model.MTAdBreak;

import java.util.ArrayList;
import java.util.List;

/**
 * Return value of {@link MTAdScheduleMerger#enrichWithTracking}. Carries the
 * merged schedule alongside any data-integrity signals the merge surfaced —
 * the merger sits behind static entry points and can't emit events on its
 * own, so it hands anything worth reporting back to the caller.
 *
 * <p>Consumers iterate {@link #pendingErrors} after a merge and fire one
 * {@code AD_ERROR} per code; each entry corresponds to a distinct anomaly
 * (a single avail missing its start time, for example, appends one code).</p>
 */
public final class MergedSchedule {

    public final List<MTAdBreak> breaks;
    public final List<MTAdErrorCode> pendingErrors;

    MergedSchedule(List<MTAdBreak> breaks, List<MTAdErrorCode> pendingErrors) {
        this.breaks = breaks;
        this.pendingErrors = pendingErrors != null ? pendingErrors : new ArrayList<MTAdErrorCode>();
    }
}
