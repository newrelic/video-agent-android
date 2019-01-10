//
//  ContentsTrackerCore.cpp
//  NewRelicVideo
//
//  Created by Andreu Santaren on 29/10/2018.
//  Copyright © 2018 New Relic Inc. All rights reserved.
//

#include "ContentsTrackerCore.hpp"
#include "TimestampHolder.hpp"
#include "ValueHolder.hpp"
#include "CAL.hpp"
#include "EventDefsCore.hpp"
#include <string.h>

ContentsTrackerCore::ContentsTrackerCore() {
    requestTimestamp = new TimestampHolder(0);
    heartbeatTimestamp = new TimestampHolder(0);
    startedTimestamp = new TimestampHolder(0);
    pausedTimestamp = new TimestampHolder(0);
    bufferBeginTimestamp = new TimestampHolder(0);
    seekBeginTimestamp = new TimestampHolder(0);
    lastAdTimestamp = new TimestampHolder(0);
}

ContentsTrackerCore::~ContentsTrackerCore() {
    delete requestTimestamp;
    delete heartbeatTimestamp;
    delete startedTimestamp;
    delete pausedTimestamp;
    delete bufferBeginTimestamp;
    delete seekBeginTimestamp;
    delete lastAdTimestamp;
}

void ContentsTrackerCore::reset() {
    TrackerCore::reset();
    
    totalPlaytime = 0;
    playtimeSinceLastEventTimestamp = 0;
    totalPlaytimeTimestamp = 0;
    
    requestTimestamp->setMain(0);
    heartbeatTimestamp->setMain(0);
    startedTimestamp->setMain(0);
    pausedTimestamp->setMain(0);
    bufferBeginTimestamp->setMain(0);
    seekBeginTimestamp->setMain(0);
    lastAdTimestamp->setMain(0);
}

void ContentsTrackerCore::setup() {
    TrackerCore::setup();
}

void ContentsTrackerCore::preSend() {
    // Special time calculations, accumulative timestamps
    
    if (state() == CoreTrackerStatePlaying) {
        totalPlaytime += timeSince(totalPlaytimeTimestamp);
        totalPlaytimeTimestamp = systemTimestamp();
    }
    updateAttribute("totalPlaytime", ValueHolder(1000.0f * totalPlaytime), "CONTENT_");
    
    if (playtimeSinceLastEventTimestamp == 0) {
        playtimeSinceLastEventTimestamp = systemTimestamp();
    }
    updateAttribute("playtimeSinceLastEvent", ValueHolder(1000.0f * timeSince(playtimeSinceLastEventTimestamp)), "CONTENT_");
    playtimeSinceLastEventTimestamp = systemTimestamp();
    
    // Regular offset timestamps, time since
    
    if (heartbeatTimestamp->timestamp() > 0.0) {
        updateAttribute("timeSinceLastHeartbeat", ValueHolder(heartbeatTimestamp->sinceMillis()), "CONTENT_");
    }
    else {
        updateAttribute("timeSinceLastHeartbeat", ValueHolder(requestTimestamp->sinceMillis()), "CONTENT_");
    }
    
    updateAttribute("timeSinceRequested", ValueHolder(requestTimestamp->sinceMillis()));
    updateAttribute("timeSinceStarted", ValueHolder(startedTimestamp->sinceMillis()));
    updateAttribute("timeSincePaused", ValueHolder(pausedTimestamp->sinceMillis()), CONTENT_RESUME);
    updateAttribute("timeSinceBufferBegin", ValueHolder(bufferBeginTimestamp->sinceMillis()), CONTENT_BUFFER_END);
    updateAttribute("timeSinceSeekBegin", ValueHolder(seekBeginTimestamp->sinceMillis()), CONTENT_SEEK_END);
    updateAttribute("timeSinceLastAd", ValueHolder(lastAdTimestamp->sinceMillis()));
    
    // Content Getters
    
    ValueHolder contentIdVal = callGetter("contentId", this);
    // Check if contentId has heen registered by a tracker and use this overwritten implementation if available.
    if (contentIdVal.getValueType() == ValueHolder::ValueHolderTypeString) {
        updateAttribute("contentId", contentIdVal);
    }
    else {
        updateAttribute("contentId", ValueHolder(getVideoId()));
    }
    
    updateAttribute("contentTitle", callGetter("contentTitle", this));
    updateAttribute("contentBitrate", callGetter("contentBitrate", this));
    updateAttribute("contentRenditionName", callGetter("contentRenditionName", this));
    updateAttribute("contentRenditionBitrate", callGetter("contentRenditionBitrate", this));
    updateAttribute("contentRenditionWidth", callGetter("contentRenditionWidth", this));
    updateAttribute("contentRenditionHeight", callGetter("contentRenditionHeight", this));
    updateAttribute("contentDuration", callGetter("contentDuration", this));
    updateAttribute("contentPlayhead", callGetter("contentPlayhead", this));
    updateAttribute("contentLanguage", callGetter("contentLanguage", this));
    updateAttribute("contentSrc", callGetter("contentSrc", this));
    updateAttribute("contentIsMuted", callGetter("contentIsMuted", this));
    updateAttribute("contentCdn", callGetter("contentCdn", this));
    updateAttribute("contentFps", callGetter("contentFps", this));
    updateAttribute("contentPlayrate", callGetter("contentPlayrate", this));
    updateAttribute("contentIsLive", callGetter("contentIsLive", this));
    updateAttribute("contentIsAutoplayed", callGetter("contentIsAutoplayed", this));
    updateAttribute("contentPreload", callGetter("contentPreload", this));
    updateAttribute("contentIsFullscreen", callGetter("contentIsFullscreen", this));
}

void ContentsTrackerCore::sendRequest() {
    requestTimestamp->setMain(systemTimestamp());
    preSend();
    TrackerCore::sendRequest();
}

void ContentsTrackerCore::sendStart() {
    if (state() == CoreTrackerStateStarting) {
        startedTimestamp->setMain(systemTimestamp());
    }
    totalPlaytimeTimestamp = systemTimestamp();
    preSend();
    TrackerCore::sendStart();
}

void ContentsTrackerCore::sendEnd() {
    videoID = "";
    preSend();
    TrackerCore::sendEnd();
    totalPlaytime = 0;
    lastAdTimestamp->setMain(0);
}

void ContentsTrackerCore::sendPause() {
    pausedTimestamp->setMain(systemTimestamp());
    preSend();
    TrackerCore::sendPause();
}

void ContentsTrackerCore::sendResume() {
    totalPlaytimeTimestamp = systemTimestamp();
    preSend();
    TrackerCore::sendResume();
}

void ContentsTrackerCore::sendSeekStart() {
    seekBeginTimestamp->setMain(systemTimestamp());
    preSend();
    TrackerCore::sendSeekStart();
}

void ContentsTrackerCore::sendSeekEnd() {
    preSend();
    TrackerCore::sendSeekEnd();
}

void ContentsTrackerCore::sendBufferStart() {
    bufferBeginTimestamp->setMain(systemTimestamp());
    preSend();
    TrackerCore::sendBufferStart();
}

void ContentsTrackerCore::sendBufferEnd() {
    preSend();
    TrackerCore::sendBufferEnd();
}

void ContentsTrackerCore::sendHeartbeat() {
    heartbeatTimestamp->setMain(systemTimestamp());
    preSend();
    TrackerCore::sendHeartbeat();
}

void ContentsTrackerCore::sendRenditionChange() {
    preSend();
    TrackerCore::sendRenditionChange();
}

void ContentsTrackerCore::sendError(std::string message) {
    preSend();
    TrackerCore::sendError(message);
}

void ContentsTrackerCore::sendPlayerReady() {
    preSend();
    TrackerCore::sendPlayerReady();
}

void ContentsTrackerCore::sendDownload() {
    preSend();
    TrackerCore::sendDownload();
}

void ContentsTrackerCore::sendCustomAction(std::string name) {
    preSend();
    TrackerCore::sendCustomAction(name);
}

void ContentsTrackerCore::sendCustomAction(std::string name, std::map<std::string, ValueHolder> attr) {
    preSend();
    TrackerCore::sendCustomAction(name, attr);
}

bool ContentsTrackerCore::setTimestamp(double timestamp, std::string attributeName) {
    if (!TrackerCore::setTimestamp(timestamp, attributeName)) {
        if (attributeName == "timeSinceRequested") {
            requestTimestamp->setExternal(timestamp);
        }
        else if (attributeName == "timeSinceStarted") {
            startedTimestamp->setExternal(timestamp);
        }
        else if (attributeName == "timeSincePaused") {
            pausedTimestamp->setExternal(timestamp);
        }
        else if (attributeName == "timeSinceBufferBegin") {
            bufferBeginTimestamp->setExternal(timestamp);
        }
        else if (attributeName == "timeSinceSeekBegin") {
            seekBeginTimestamp->setExternal(timestamp);
        }
        else if (attributeName == "timeSinceLastAd") {
            lastAdTimestamp->setExternal(timestamp);
        }
        else if (attributeName == "timeSinceLastHeartbeat") {
            heartbeatTimestamp->setExternal(timestamp);
        }
        else {
            return false;
        }
    }
    
    return true;
}

void ContentsTrackerCore::adHappened(double time) {
    lastAdTimestamp->setMain(time);
}

std::string ContentsTrackerCore::getVideoId() {
    if (videoID.empty()) {
        int i, j;
        unsigned int byte, crc, mask;
        
        ValueHolder src = callGetter("contentSrc", this);
        
        if (src.getValueType() != ValueHolder::ValueHolderTypeString) {
            return "";
        }
        
        unsigned char *message = (unsigned char *)new char[src.getValueString().length() + 1];
        strcpy((char *)message, src.getValueString().c_str());
        
        //  CRC32 algorithm
        i = 0;
        crc = 0xFFFFFFFF;
        while (message[i] != 0) {
            byte = message[i];            // Get next byte.
            crc = crc ^ byte;
            for (j = 7; j >= 0; j--) {    // Do eight times.
                mask = -(crc & 1);
                crc = (crc >> 1) ^ (0xEDB88320 & mask);
            }
            i = i + 1;
        }
        
        delete [] message;
        
        videoID = std::to_string(~crc);
    }
    
    return videoID;
}
