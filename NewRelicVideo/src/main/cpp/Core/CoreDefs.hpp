//
//  CoreDefs.hpp
//  NewRelicVideo
//
//  Created by Andreu Santaren on 20/11/2018.
//  Copyright © 2018 New Relic Inc. All rights reserved.
//

#ifndef CoreDefs_h
#define CoreDefs_h

typedef enum {
    CoreTrackerStateStopped = 0,
    CoreTrackerStateStarting,
    CoreTrackerStatePlaying,
    CoreTrackerStatePaused,
    CoreTrackerStateBuffering,
    CoreTrackerStateSeeking
} CoreTrackerState;

#endif /* CoreDefs_h */
