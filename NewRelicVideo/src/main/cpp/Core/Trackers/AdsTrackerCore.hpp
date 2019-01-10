//
//  AdsTrackerCore.hpp
//  NewRelicVideo
//
//  Created by Andreu Santaren on 06/11/2018.
//  Copyright © 2018 New Relic Inc. All rights reserved.
//

#ifndef AdsTrackerCore_hpp
#define AdsTrackerCore_hpp

#include <stdio.h>
#include "TrackerCore.hpp"

class ContentsTrackerCore;
class TimestampHolder;

class AdsTrackerCore: public TrackerCore {
private:
    ContentsTrackerCore *contentsTracker;
    TimestampHolder *adRequestedTimestamp;
    TimestampHolder *lastAdHeartbeatTimestamp;
    TimestampHolder *adStartedTimestamp;
    TimestampHolder *adPausedTimestamp;
    TimestampHolder *adBufferBeginTimestamp;
    TimestampHolder *adSeekBeginTimestamp;
    TimestampHolder *adBreakBeginTimestamp;
    TimestampHolder *lastAdQuartileTimestamp;
    int numberOfAds;
    
protected:
    void preSend();
    
public:
    AdsTrackerCore(ContentsTrackerCore *contentsTracker);
    AdsTrackerCore();
    ~AdsTrackerCore();
    
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
    
    // AdsTracker methods
    void sendAdBreakStart();
    void sendAdBreakEnd();
    void sendAdQuartile();
    void sendAdClick();
    int getNumberOfAds();
};

#endif /* AdsTrackerCore_hpp */
