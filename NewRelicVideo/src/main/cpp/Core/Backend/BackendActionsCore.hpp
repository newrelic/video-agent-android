//
//  BackendActionsCore.hpp
//  NewRelicVideo
//
//  Created by Andreu Santaren on 22/10/2018.
//  Copyright © 2018 New Relic Inc. All rights reserved.
//

#ifndef BackendActionsCore_hpp
#define BackendActionsCore_hpp

#include <stdio.h>
#include <string>
#include <map>

class ValueHolder;

class BackendActionsCore {
public:
    std::map<std::string, ValueHolder> generalOptions;
    std::map<std::string, std::map<std::string, ValueHolder>> actionOptions;

    BackendActionsCore();
    ~BackendActionsCore();
    
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
    
    void sendAdRequest();
    void sendAdStart();
    void sendAdEnd();
    void sendAdPause();
    void sendAdResume();
    void sendAdSeekStart();
    void sendAdSeekEnd();
    void sendAdBufferStart();
    void sendAdBufferEnd();
    void sendAdHeartbeat();
    void sendAdRenditionChange();
    void sendAdError(std::string message);
    void sendAdBreakStart();
    void sendAdBreakEnd();
    void sendAdQuartile();
    void sendAdClick();
    
    void sendPlayerReady();
    void sendDownload();
    
    void sendAction(std::string name);
    void sendAction(std::string name, std::map<std::string, ValueHolder> attr);
    
private:
    std::map<std::string, ValueHolder> actionOptionsForName(std::string name);
};

#endif /* BackendActionsCore_hpp */
