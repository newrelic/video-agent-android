//
// Created by Andreu Santaren on 13/11/2018.
//

#include "CAL.hpp"
#include "ValueHolder.hpp"
#include "TrackerCore.hpp"

bool recordCustomEvent(std::string name, std::map<std::string, ValueHolder> attr) {
    // TODO: implement
    return true;
}

std::string currentSessionId() {
    // TODO: implement
    return "";
}

double systemTimestamp() {
    // TODO: implement
    return 0.0;
}

ValueHolder callGetter(std::string name, void *origin) {
    // TODO: implement
    return ValueHolder();
}

void startTimer(TrackerCore *trackerCore, double timeInterval) {
    // TODO: implement
}

void abortTimer() {
    // TODO: implement
}

void AV_LOG(const char *format, ...) {
    // TODO: implement
}