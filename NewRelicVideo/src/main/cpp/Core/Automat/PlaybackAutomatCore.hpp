//
//  PlaybackAutomat.hpp
//  NewRelicVideo
//
//  Created by Andreu Santaren on 25/10/2018.
//  Copyright © 2018 New Relic Inc. All rights reserved.
//

#ifndef PlaybackAutomatCore_hpp
#define PlaybackAutomatCore_hpp

#include <stdio.h>
#include <string>
#include <stack>
#include "CoreDefs.hpp"

class BackendActionsCore;

typedef enum {
    CoreTrackerTransitionAutoplay = 0,
    CoreTrackerTransitionClickPlay,
    CoreTrackerTransitionClickPause,
    CoreTrackerTransitionClickStop,
    CoreTrackerTransitionFrameShown,
    CoreTrackerTransitionInitBuffering,
    CoreTrackerTransitionEndBuffering,
    CoreTrackerTransitionVideoFinished,
    CoreTrackerTransitionInitDraggingSlider,
    CoreTrackerTransitionEndDraggingSlider,
} CoreTrackerTransition;

class PlaybackAutomatCore {
private:
    BackendActionsCore *actions;
    std::stack<CoreTrackerState> stateStack;
    CoreTrackerState state;
    
    bool transition(CoreTrackerTransition tt);
    bool performTransitionInStateStopped(CoreTrackerTransition tt);
    bool performTransitionInStateStarting(CoreTrackerTransition tt);
    bool performTransitionInStatePlaying(CoreTrackerTransition tt);
    bool performTransitionInStatePaused(CoreTrackerTransition tt);
    bool performTransitionInStateSeeking(CoreTrackerTransition tt);
    bool performTransitionInStateBuffering(CoreTrackerTransition tt);
    void moveState(CoreTrackerState newState);
    void moveStateAndPush(CoreTrackerState newState);
    void backToState();
    
public:
    bool isAd;
    
    PlaybackAutomatCore();
    ~PlaybackAutomatCore();
    
    BackendActionsCore *getActions();
    CoreTrackerState getState();
    void sendRequest();
    void sendStart();
    void sendEnd();
    void sendPause();
    void sendResume();
    void sendSeekStart();
    void sendSeekEnd();
    void sendBufferStart();
    void sendBufferEnd();
    void sendHeartbeat();
    void sendRenditionChange();
    void sendError(std::string message);
};

#endif /* PlaybackAutomat_hpp */
