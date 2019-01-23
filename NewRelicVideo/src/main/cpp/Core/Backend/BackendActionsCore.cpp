//
//  BackendActionsCore.cpp
//  NewRelicVideo
//
//  Created by Andreu Santaren on 22/10/2018.
//  Copyright © 2018 New Relic Inc. All rights reserved.
//

#include "BackendActionsCore.hpp"
#include "EventDefsCore.hpp"
#include "ValueHolder.hpp"
#include "CAL.hpp"
#include "DictionaryMerge.hpp"

BackendActionsCore::BackendActionsCore() {
}

BackendActionsCore::~BackendActionsCore() {
}

void BackendActionsCore::sendRequest() {
    sendAction(CONTENT_REQUEST);
}

void BackendActionsCore::sendStart() {
    sendAction(CONTENT_START);
}

void BackendActionsCore::sendEnd() {
    sendAction(CONTENT_END);
}

void BackendActionsCore::sendPause() {
    sendAction(CONTENT_PAUSE);
}

void BackendActionsCore::sendResume() {
    sendAction(CONTENT_RESUME);
}

void BackendActionsCore::sendSeekStart() {
    sendAction(CONTENT_SEEK_START);
}

void BackendActionsCore::sendSeekEnd() {
    sendAction(CONTENT_SEEK_END);
}

void BackendActionsCore::sendBufferStart() {
    sendAction(CONTENT_BUFFER_START);
}

void BackendActionsCore::sendBufferEnd() {
    sendAction(CONTENT_BUFFER_END);
}

void BackendActionsCore::sendHeartbeat() {
    sendAction(CONTENT_HEARTBEAT);
}

void BackendActionsCore::sendRenditionChange() {
    sendAction(CONTENT_RENDITION_CHANGE);
}

void BackendActionsCore::sendError(std::string message) {
    sendAction(CONTENT_ERROR, {{"errorMessage", ValueHolder(message)}});
}

void BackendActionsCore::sendDroppedFrame(int count, long elapsed) {
    sendAction(CONTENT_DROPPED_FRAMES, {{"lostFrames", ValueHolder(count)}, {"lostFramesDuration", ValueHolder(elapsed)}});
}

void BackendActionsCore::sendAdRequest() {
    sendAction(AD_REQUEST);
}

void BackendActionsCore::sendAdStart() {
    sendAction(AD_START);
}

void BackendActionsCore::sendAdEnd() {
    sendAction(AD_END);
}

void BackendActionsCore::sendAdPause() {
    sendAction(AD_PAUSE);
}

void BackendActionsCore::sendAdResume() {
    sendAction(AD_RESUME);
}

void BackendActionsCore::sendAdSeekStart() {
    sendAction(AD_SEEK_START);
}

void BackendActionsCore::sendAdSeekEnd() {
    sendAction(AD_SEEK_END);
}

void BackendActionsCore::sendAdBufferStart() {
    sendAction(AD_BUFFER_START);
}

void BackendActionsCore::sendAdBufferEnd() {
    sendAction(AD_BUFFER_END);
}

void BackendActionsCore::sendAdHeartbeat() {
    sendAction(AD_HEARTBEAT);
}

void BackendActionsCore::sendAdRenditionChange() {
    sendAction(AD_RENDITION_CHANGE);
}

void BackendActionsCore::sendAdError(std::string message) {
    sendAction(AD_ERROR, {{"errorMessage", ValueHolder(message)}});
}

void BackendActionsCore::sendAdDroppedFrame(int count, long elapsed) {
    sendAction(AD_DROPPED_FRAMES, {{"lostFrames", ValueHolder(count)}, {"lostFramesDuration", ValueHolder(elapsed)}});
}

void BackendActionsCore::sendAdBreakStart() {
    sendAction(AD_BREAK_START);
}

void BackendActionsCore::sendAdBreakEnd() {
    sendAction(AD_BREAK_END);
}

void BackendActionsCore::sendAdQuartile() {
    sendAction(AD_QUARTILE);
}

void BackendActionsCore::sendAdClick() {
    sendAction(AD_CLICK);
}

void BackendActionsCore::sendPlayerReady() {
    sendAction(PLAYER_READY);
}

void BackendActionsCore::sendDownload() {
    sendAction(DOWNLOAD);
}

void BackendActionsCore::sendAction(std::string name) {
    sendAction(name, {});
}

void BackendActionsCore::sendAction(std::string name, std::map<std::string, ValueHolder> attr) {
    std::map<std::string, ValueHolder> finalAttr = DictionaryMerge::merge(attr, generalOptions);
    std::map<std::string, ValueHolder> actionAttrs = actionOptionsForName(name);
    finalAttr = DictionaryMerge::merge(actionAttrs, finalAttr);

    AV_LOG("sendAction %s\n", name.c_str());
    
    recordCustomEvent(name, finalAttr);
}

std::map<std::string, ValueHolder> BackendActionsCore::actionOptionsForName(std::string name) {
    
    std::map<std::string, ValueHolder> dict;
    
    for (auto& kv : actionOptions) {
        std::string key = kv.first;
        
        if (key.at(key.size() - 1) == '_') {
            // key is a prefix
            if (name.find(key) == 0) {
                dict = DictionaryMerge::merge(actionOptions[key], dict);
            }
        }
        else if (key.at(0) == '_') {
            // key is a suffix
            if(name.size() >= key.size() &&
               name.compare(name.size() - key.size(), key.size(), key) == 0) {
                dict = DictionaryMerge::merge(actionOptions[key], dict);
            }
        }
        else if (key.compare(name) == 0) {
            // Found the "name" filter as is
            dict = DictionaryMerge::merge(actionOptions[key], dict);
        }
    }
    
    return dict;
}
