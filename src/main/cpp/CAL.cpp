//
// Created by Andreu Santaren on 13/11/2018.
//

#include "CAL.hpp"
#include "ValueHolder.hpp"
#include "TrackerCore.hpp"
#include <jni.h>
#include <stdio.h>
#include <stdarg.h>
#include <string.h>
#include <malloc.h>

JNIEnv *env;

extern "C"
JNIEXPORT void JNICALL
Java_com_newrelic_videoagent_NewRelicVideoAgent_initJNIEnv(JNIEnv *e, jclass type) {
    env = e;
}

bool recordCustomEvent(std::string name, std::map<std::string, ValueHolder> attr) {
    // TODO: implement
    /*
    std::string a = name;
    std::map<std::string, ValueHolder> b = attr;
    jclass cls = env->FindClass("com/newrelic/videoagent/CAL");
    jmethodID mid = env->GetStaticMethodID(cls, "hello", "()V");
    env->CallStaticVoidMethod(cls, mid);
     */
    return true;
}

std::string currentSessionId() {
    jclass cls = env->FindClass("com/newrelic/videoagent/CAL");
    jmethodID mid = env->GetStaticMethodID(cls, "currentSessionId", "()Ljava/lang/String;");
    jstring jstr = (jstring)env->CallStaticObjectMethod(cls, mid);
    const char *strReturn = env->GetStringUTFChars(jstr, 0);
    std::string ret = std::string(strReturn);
    env->ReleaseStringUTFChars(jstr, strReturn);
    return ret;
}

double systemTimestamp() {
    jclass cls = env->FindClass("com/newrelic/videoagent/CAL");
    jmethodID mid = env->GetStaticMethodID(cls, "systemTimestamp", "()D");
    jdouble ret = env->CallStaticDoubleMethod(cls, mid);
    return ret;
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
    char *str = (char *)malloc(1000);
    if (!str)
        return;

    jstring jstr;

    va_list args;
    va_start(args, format);
    vsnprintf(str, 1000, format, args);
    va_end(args);

    jstr = env->NewStringUTF(str);

    jclass cls = env->FindClass("com/newrelic/videoagent/CAL");
    jmethodID mid = env->GetStaticMethodID(cls, "AV_LOG", "(Ljava/lang/String;)V");
    env->CallStaticVoidMethod(cls, mid, jstr);

    free(str);
}
