//
//  CAL.hpp
//  NewRelicVideo
//
//  Created by Andreu Santaren on 22/10/2018.
//  Copyright © 2018 New Relic Inc. All rights reserved.
//

// Core Abstraction Layer
// Define the prototypes of the functions used in the Core that are system dependant.
// To port the Core to a different platform is mandatory to provide proper implementations of these functions.

#ifndef CAL_h
#define CAL_h

#include <string>
#include <map>

class ValueHolder;
class TrackerCore;

bool recordCustomEvent(std::string name, std::map<std::string, ValueHolder> attr);
std::string currentSessionId();
double systemTimestamp();
ValueHolder callGetter(std::string name, void *origin);
void startTimer(TrackerCore *trackerCore, double timeInterval);
void abortTimer();
void AV_LOG(const char *format, ...);

static inline double timeSince(double timestamp) {
    return systemTimestamp() - timestamp;
}

#endif /* CAL_H */
