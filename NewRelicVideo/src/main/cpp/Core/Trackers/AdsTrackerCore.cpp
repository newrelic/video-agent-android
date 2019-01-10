//
//  AdsTrackerCore.cpp
//  NewRelicVideo
//
//  Created by Andreu Santaren on 06/11/2018.
//  Copyright © 2018 New Relic Inc. All rights reserved.
//

#include "AdsTrackerCore.hpp"
#include "TrackerCore.hpp"
#include "ValueHolder.hpp"
#include "TimestampHolder.hpp"
#include "EventDefsCore.hpp"
#include "CAL.hpp"
#include "ContentsTrackerCore.hpp"

/*
 TODO:
 - Implement AD_QUARTILE's "quartile" attribute. Easy, a simple counter is reset on every AD_REQUEST.
 - Implement AD_CLICK's "url" attribute. Argument to sendAdClick method.
 - Implement AD_END's "skipped" attribute. Argument to sendEnd method (?). Problematic, since we are adding an argument to contents tracker, that doesn't need it.
 */

AdsTrackerCore::AdsTrackerCore(ContentsTrackerCore *contentsTracker) {
    AdsTrackerCore();
    this->contentsTracker = contentsTracker;
}

AdsTrackerCore::AdsTrackerCore() {
    this->contentsTracker = NULL;
    adRequestedTimestamp = new TimestampHolder(0);
    lastAdHeartbeatTimestamp = new TimestampHolder(0);
    adStartedTimestamp = new TimestampHolder(0);
    adPausedTimestamp = new TimestampHolder(0);
    adBufferBeginTimestamp = new TimestampHolder(0);
    adSeekBeginTimestamp = new TimestampHolder(0);
    adBreakBeginTimestamp = new TimestampHolder(0);
    lastAdQuartileTimestamp = new TimestampHolder(0);
}

AdsTrackerCore::~AdsTrackerCore() {
    delete adRequestedTimestamp;
    delete lastAdHeartbeatTimestamp;
    delete adStartedTimestamp;
    delete adPausedTimestamp;
    delete adBufferBeginTimestamp;
    delete adSeekBeginTimestamp;
    delete adBreakBeginTimestamp;
    delete lastAdQuartileTimestamp;
}

// Overwritten from TrackerCore
void AdsTrackerCore::reset() {
    TrackerCore::reset();
    
    numberOfAds = 0;
    adRequestedTimestamp->setMain(0);
    lastAdHeartbeatTimestamp->setMain(0);
    adStartedTimestamp->setMain(0);
    adPausedTimestamp->setMain(0);
    adBufferBeginTimestamp->setMain(0);
    adSeekBeginTimestamp->setMain(0);
    adBreakBeginTimestamp->setMain(0);
    lastAdQuartileTimestamp->setMain(0);
}

void AdsTrackerCore::setup() {
    TrackerCore::setup();
}

void AdsTrackerCore::preSend() {
    updateAttribute("timeSinceRequested", ValueHolder(adRequestedTimestamp->sinceMillis()));
    updateAttribute("timeSinceLastAdHeartbeat", ValueHolder(lastAdHeartbeatTimestamp->sinceMillis()));
    updateAttribute("timeSinceAdStarted", ValueHolder(adStartedTimestamp->sinceMillis()));
    updateAttribute("timeSinceAdPaused", ValueHolder(adPausedTimestamp->sinceMillis()), AD_RESUME);
    updateAttribute("timeSinceAdBufferBegin", ValueHolder(adBufferBeginTimestamp->sinceMillis()), AD_BUFFER_END);
    updateAttribute("timeSinceAdSeekBegin", ValueHolder(adSeekBeginTimestamp->sinceMillis()), AD_SEEK_END);
    updateAttribute("timeSinceAdBreakBegin", ValueHolder(adBreakBeginTimestamp->sinceMillis()));
    updateAttribute("timeSinceLastAdQuartile", ValueHolder(lastAdQuartileTimestamp->sinceMillis()), AD_QUARTILE);
    
    // Ad Getters
    updateAttribute("numberOfAds", callGetter("numberOfAds", this));
    updateAttribute("adId", callGetter("adId", this));
    updateAttribute("adTitle", callGetter("adTitle", this));
    updateAttribute("adBitrate", callGetter("adBitrate", this));
    updateAttribute("adRenditionName", callGetter("adRenditionName", this));
    updateAttribute("adRenditionBitrate", callGetter("adRenditionBitrate", this));
    updateAttribute("adRenditionWidth", callGetter("adRenditionWidth", this));
    updateAttribute("adRenditionHeight", callGetter("adRenditionHeight", this));
    updateAttribute("adDuration", callGetter("adDuration", this));
    updateAttribute("adPlayhead", callGetter("adPlayhead", this));
    updateAttribute("adLanguage", callGetter("adLanguage", this));
    updateAttribute("adSrc", callGetter("adSrc", this));
    updateAttribute("adIsMuted", callGetter("adIsMuted", this));
    updateAttribute("adCdn", callGetter("adCdn", this));
    updateAttribute("adFps", callGetter("adFps", this));
    updateAttribute("adCreativeId", callGetter("adCreativeId", this));
    updateAttribute("adPosition", callGetter("adPosition", this));
    updateAttribute("adPartner", callGetter("adPartner", this));
}

void AdsTrackerCore::sendRequest() {
    adRequestedTimestamp->setMain(systemTimestamp());
    numberOfAds ++;
    preSend();
    TrackerCore::sendRequest();
}

void AdsTrackerCore::sendStart() {
    adStartedTimestamp->setMain(systemTimestamp());
    preSend();
    TrackerCore::sendStart();
}

void AdsTrackerCore::sendEnd() {
    if (contentsTracker) {
        contentsTracker->adHappened(systemTimestamp());
    }
    
    preSend();
    TrackerCore::sendEnd();
}

void AdsTrackerCore::sendPause() {
    adPausedTimestamp->setMain(systemTimestamp());
    preSend();
    TrackerCore::sendPause();
}

void AdsTrackerCore::sendResume() {
    preSend();
    TrackerCore::sendResume();
}

void AdsTrackerCore::sendSeekStart() {
    adSeekBeginTimestamp->setMain(systemTimestamp());
    preSend();
    TrackerCore::sendSeekStart();
}

void AdsTrackerCore::sendSeekEnd() {
    preSend();
    TrackerCore::sendSeekEnd();
}

void AdsTrackerCore::sendBufferStart() {
    adBufferBeginTimestamp->setMain(systemTimestamp());
    preSend();
    TrackerCore::sendBufferStart();
}

void AdsTrackerCore::sendBufferEnd() {
    preSend();
    TrackerCore::sendBufferEnd();
}

void AdsTrackerCore::sendHeartbeat() {
    lastAdHeartbeatTimestamp->setMain(systemTimestamp());
    preSend();
    TrackerCore::sendHeartbeat();
}

void AdsTrackerCore::sendRenditionChange() {
    preSend();
    TrackerCore::sendRenditionChange();
}

void AdsTrackerCore::sendError(std::string message) {
    preSend();
    TrackerCore::sendError(message);
}

void AdsTrackerCore::sendPlayerReady() {
    preSend();
    TrackerCore::sendPlayerReady();
}

void AdsTrackerCore::sendDownload() {
    preSend();
    TrackerCore::sendDownload();
}

void AdsTrackerCore::sendCustomAction(std::string name) {
    preSend();
    TrackerCore::sendCustomAction(name);
}

void AdsTrackerCore::sendCustomAction(std::string name, std::map<std::string, ValueHolder> attr) {
    preSend();
    TrackerCore::sendCustomAction(name, attr);
}

bool AdsTrackerCore::setTimestamp(double timestamp, std::string attributeName) {
    if (!TrackerCore::setTimestamp(timestamp, attributeName)) {
        if (attributeName == "timeSinceRequested") {
            adRequestedTimestamp->setExternal(timestamp);
        }
        else if (attributeName == "timeSinceLastAdHeartbeat") {
            lastAdHeartbeatTimestamp->setExternal(timestamp);
        }
        else if (attributeName == "timeSinceAdStarted") {
            adStartedTimestamp->setExternal(timestamp);
        }
        else if (attributeName == "timeSinceAdPaused") {
            adPausedTimestamp->setExternal(timestamp);
        }
        else if (attributeName == "timeSinceAdBufferBegin") {
            adBufferBeginTimestamp->setExternal(timestamp);
        }
        else if (attributeName == "timeSinceAdSeekBegin") {
            adSeekBeginTimestamp->setExternal(timestamp);
        }
        else if (attributeName == "timeSinceAdBreakBegin") {
            adBreakBeginTimestamp->setExternal(timestamp);
        }
        else if (attributeName == "timeSinceLastAdQuartile") {
            lastAdQuartileTimestamp->setExternal(timestamp);
        }
        else {
            return false;
        }
    }
    
    return true;
}

// Specific AdsTracker methods
void AdsTrackerCore::sendAdBreakStart() {
    numberOfAds = 0;
    adBreakBeginTimestamp->setMain(systemTimestamp());
    sendCustomAction(AD_BREAK_START);
}

void AdsTrackerCore::sendAdBreakEnd() {
    sendCustomAction(AD_BREAK_END);
}

void AdsTrackerCore::sendAdQuartile() {
    lastAdQuartileTimestamp->setMain(systemTimestamp());
    sendCustomAction(AD_QUARTILE);
}

void AdsTrackerCore::sendAdClick() {
    sendCustomAction(AD_CLICK);
}

int AdsTrackerCore::getNumberOfAds() {
    return numberOfAds;
}
