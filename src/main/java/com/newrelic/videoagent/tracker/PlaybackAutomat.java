package com.newrelic.videoagent.tracker;

import com.newrelic.videoagent.backend.BackendActions;
import com.newrelic.videoagent.utils.NRLog;
import java.util.EmptyStackException;
import java.util.Stack;

public class PlaybackAutomat {

    public enum State {
        Stopped,
        Starting,
        Playing,
        Paused,
        Buffering,
        Seeking
    }

    private enum Transition {
        Autoplay,
        ClickPlay,
        ClickPause,
        ClickStop,
        FrameShown,
        InitBuffering,
        EndBuffering,
        VideoFinished,
        ErrorPlaying,
        InitDraggingSlider,
        EndDraggingSlider,
        Heartbeat,
        RenditionChanged
    }

    private BackendActions actions;
    private PlaybackAutomat.State state;
    private Stack<PlaybackAutomat.State> stateStack;
    private boolean isAd;

    public PlaybackAutomat() {
        actions = new BackendActions();
        state = State.Stopped;
        stateStack = new Stack<>();
        isAd = false;
    }

    public boolean isAd() {
        return isAd;
    }

    public void setAd(boolean ad) {
        isAd = ad;
    }

    public BackendActions getActions() {
        return actions;
    }

    public PlaybackAutomat.State getState() {
        return state;
    }

    public void sendRequest() {
        if (transition(Transition.ClickPlay)) {
            if (this.isAd) {
                this.actions.sendAdRequest();
            } else {
                this.actions.sendRequest();
            }
        }
    }

    public void sendStart() {
        if (transition(Transition.FrameShown)) {
            if (this.isAd) {
                this.actions.sendAdStart();
            } else {
                this.actions.sendStart();
            }
        }
    }

    public void sendEnd() {
        if (this.isAd) {
            this.actions.sendAdEnd();
        }
        else {
            this.actions.sendEnd();
        }

        stateStack.removeAllElements();
        moveState(State.Stopped);
    }

    public void sendPause() {
        if (transition(Transition.ClickPause)) {
            if (this.isAd) {
                this.actions.sendAdPause();
            } else {
                this.actions.sendPause();
            }
        }
    }

    public void sendResume() {
        if (transition(Transition.ClickPlay)) {
            if (this.isAd) {
                this.actions.sendAdResume();
            } else {
                this.actions.sendResume();
            }
        }
    }

    public void sendSeekStart() {
        if (this.isAd) {
            this.actions.sendAdSeekStart();
        }
        else {
            this.actions.sendSeekStart();
        }

        moveStateAndPush(State.Seeking);
    }

    public void sendSeekEnd() {
        if (transition(Transition.EndDraggingSlider)) {
            if (this.isAd) {
                this.actions.sendAdSeekEnd();
            } else {
                this.actions.sendSeekEnd();
            }
        }
    }

    public void sendBufferStart() {
        if (this.isAd) {
            this.actions.sendAdBufferStart();
        }
        else {
            this.actions.sendBufferStart();
        }

        moveStateAndPush(State.Buffering);
    }

    public void sendBufferEnd() {
        if (transition(Transition.EndBuffering)) {
            if (this.isAd) {
                this.actions.sendAdBufferEnd();
            } else {
                this.actions.sendBufferEnd();
            }
        }
    }

    public void sendHeartbeat() {
        if (this.isAd) {
            this.actions.sendAdHeartbeat();
        }
        else {
            this.actions.sendHeartbeat();
        }
    }

    public void sendRenditionChange() {
        if (this.isAd) {
            this.actions.sendAdRenditionChange();
        }
        else {
            this.actions.sendRenditionChange();
        }
    }

    public void sendError(String message) {
        if (this.isAd) {
            this.actions.sendAdError();
        }
        else {
            this.actions.sendError();
        }
    }

    private boolean transition(Transition tt) {

        NRLog.d(">>>> TRANSITION " + tt);

        switch (this.state) {
            default:
            case Stopped: {
                return performTransitionInStateStopped(tt);
            }

            case Starting: {
                return performTransitionInStateStarting(tt);
            }

            case Paused: {
                return performTransitionInStatePaused(tt);
            }

            case Playing: {
                return performTransitionInStatePlaying(tt);
            }

            case Seeking: {
                return performTransitionInStateSeeking(tt);
            }

            case Buffering: {
                return performTransitionInStateBuffering(tt);
            }
        }
    }

    private boolean performTransitionInStateStopped(Transition tt) {
        if (tt == Transition.Autoplay || tt == Transition.ClickPlay) {
            moveState(State.Starting);
            return true;
        }
        return false;
    }

    private boolean performTransitionInStateStarting(Transition tt) {
        if (tt == Transition.FrameShown) {
            moveState(State.Playing);
            return true;
        }
        return false;
    }

    private boolean performTransitionInStatePlaying(Transition tt) {
        if (tt == Transition.ClickPause) {
            moveState(State.Paused);
            return true;
        }
        return false;
    }

    private boolean performTransitionInStatePaused(Transition tt) {
        if (tt == Transition.ClickPlay) {
            moveState(State.Playing);
            return true;
        }
        return false;
    }

    private boolean performTransitionInStateSeeking(Transition tt) {
        if (tt == Transition.EndDraggingSlider) {
            backToState();
            return true;
        }
        // NOTE: just in case seeking gets lost and SEEK_END never arrives. In AVPlayer happens with big videos in streaming
        else if (tt == Transition.ClickPlay) {
            backToState();
            moveState(State.Playing);
            return true;
        }
        else if (tt == Transition.ClickPause) {
            backToState();
            moveState(State.Paused);
            return true;
        }
        return false;
    }

    private boolean performTransitionInStateBuffering(Transition tt) {
        if (tt == Transition.EndBuffering) {
            backToState();
            return true;
        }
        return false;
    }

    // Utils

    private void moveState(State state) {
        this.state = state;
    }

    private void moveStateAndPush(State newState) {
        stateStack.push(state);
        this.state = newState;
    }

    private void backToState() {
        try {
            State prevState = stateStack.pop();
            state = prevState;
        }
        catch (EmptyStackException e) {
            NRLog.d("State Stack underun");
        }
    }
}
