//
//  ContentsTrackerCore.hpp
//  NewRelicVideo
//
//  Created by Andreu Santaren on 29/10/2018.
//  Copyright © 2018 New Relic Inc. All rights reserved.
//

#ifndef ContentsTrackerCore_hpp
#define ContentsTrackerCore_hpp

#include <stdio.h>
#include "TrackerCore.hpp"

class ValueHolder;
class TimestampHolder;

class ContentsTrackerCore: public TrackerCore {
private:
    std::string videoID;
    
    // Time Counts
    double totalPlaytimeTimestamp;
    double playtimeSinceLastEventTimestamp;
    double totalPlaytime;
    
    // Time Since
    TimestampHolder *requestTimestamp;
    TimestampHolder *heartbeatTimestamp;
    TimestampHolder *startedTimestamp;
    TimestampHolder *pausedTimestamp;
    TimestampHolder *bufferBeginTimestamp;
    TimestampHolder *seekBeginTimestamp;
    TimestampHolder *lastAdTimestamp;
    
protected:
    void preSend();
    std::string getVideoId();
    
public:
    ContentsTrackerCore();
    ~ContentsTrackerCore();
    
    // from TrackerCore
    void reset();
    void setup();
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
    void sendPlayerReady();
    void sendDownload();
    void sendCustomAction(std::string name);
    void sendCustomAction(std::string name, std::map<std::string, ValueHolder> attr);
    bool setTimestamp(double timestamp, std::string attributeName);
    
    // ContentsTracker methods
    void adHappened(double time);
};

#endif /* ContentsTrackerCore_hpp */
