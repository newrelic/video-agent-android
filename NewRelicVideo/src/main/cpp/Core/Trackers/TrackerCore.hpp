//
//  TrackerCore.hpp
//  NewRelicVideo
//
//  Created by Andreu Santaren on 25/10/2018.
//  Copyright © 2018 New Relic Inc. All rights reserved.
//

#ifndef TrackerCore_hpp
#define TrackerCore_hpp

#define OBSERVATION_TIME        2.0f
#define HEARTBEAT_COUNT         (25.0f / OBSERVATION_TIME)

#include <stdio.h>
#include <string>
#include <map>
#include "PlaybackAutomatCore.hpp"

class ValueHolder;
class PlaybackAutomatCore;
class TimestampHolder;

class TrackerCore {
private:
    PlaybackAutomatCore *automat;
    std::string viewId;
    int viewIdIndex;
    int numErrors;
    int heartbeatCounter;
    TimestampHolder *lastRenditionChangeTimestamp;
    TimestampHolder *trackerReadyTimestamp;
    
    void playNewVideo();
    
protected:
    void preSend();
    
public:
    TrackerCore();
    ~TrackerCore();
    
    CoreTrackerState state();
    void updateAttribute(std::string name, ValueHolder value, std::string filter);
    void updateAttribute(std::string name, ValueHolder value);
    void reset();
    void setup();
    std::string getViewId();
    int getNumberOfVideos();
    std::string getCoreVersion();
    std::string getViewSession();
    int getNumberOfErrors();
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
    void setOptions(std::map<std::string, ValueHolder> opts);
    void setOptions(std::map<std::string, ValueHolder> opts, std::string action);
    void startTimerEvent();
    void abortTimerEvent();
    void trackerTimeEvent();
    bool setTimestamp(double timestamp, std::string attributeName);
};

#endif /* TrackerCore_hpp */
