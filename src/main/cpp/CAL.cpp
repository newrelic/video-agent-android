//
// Created by Andreu Santaren on 13/11/2018.
//

#include "CAL.hpp"
#include "ValueHolder.hpp"
#include "TrackerCore.hpp"
#include <jni.h>

JNIEnv *env;

extern "C"
JNIEXPORT void JNICALL
Java_com_newrelic_videoagent_NewRelicVideoAgent_initJNIEnv(JNIEnv *e, jclass type) {
    env = e;
}

bool recordCustomEvent(std::string name, std::map<std::string, ValueHolder> attr) {
    // TODO: implement
    std::string a = name;
    std::map<std::string, ValueHolder> b = attr;
    jclass cls = env->FindClass("com/newrelic/videoagent/CAL");
    jmethodID mid = env->GetStaticMethodID(cls, "hello", "()V");
    env->CallStaticVoidMethod(cls, mid);
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