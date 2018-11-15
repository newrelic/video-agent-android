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
    jclass cls = env->FindClass("com/newrelic/videoagent/CAL");
    jmethodID mid = env->GetStaticMethodID(cls, "recordCustomEvent", "(Ljava/lang/String;Ljava/util/Map;)V");
    jstring jname = env->NewStringUTF(name.c_str());

    // Create the map
    jclass mapClass = env->FindClass("java/util/HashMap");
    jmethodID init = env->GetMethodID(mapClass, "<init>", "(I)V");
    jsize map_len = 1;
    jobject hashMap = env->NewObject(mapClass, init, map_len);
    jmethodID put = env->GetMethodID(mapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

    // fill the map
    for (auto& kv : attr) {
        std::string key = kv.first;
        ValueHolder val = kv.second;

        jstring jkey = env->NewStringUTF(key.c_str());
        jobject jval;

        switch (val.getValueType()) {
            case ValueHolder::ValueHolderTypeString: {
                jval = env->NewStringUTF(val.getValueString().c_str());
                break;
            }
            case ValueHolder::ValueHolderTypeInt: {
                jclass intClass = env->FindClass("java/lang/Long");
                jmethodID intInit = env->GetMethodID(intClass, "<init>", "(J)V");
                jval = env->NewObject(intClass, intInit, (jlong)val.getValueInt());
                break;
            }
            case ValueHolder::ValueHolderTypeFloat: {
                jclass doubleClass = env->FindClass("java/lang/Double");
                jmethodID doubleInit = env->GetMethodID(doubleClass, "<init>", "(D)V");
                jval = env->NewObject(doubleClass, doubleInit, (jdouble)val.getValueFloat());
                break;
            }
            default: {
                jval = nullptr;
            }
        }

        if (jval != nullptr) {
            env->CallObjectMethod(hashMap, put, jkey, jval);
        }

        env->DeleteLocalRef(jkey);
        env->DeleteLocalRef(jval);
    }

    // Call recordCustomEvent
    env->CallStaticVoidMethod(cls, mid, jname, hashMap);

    env->DeleteLocalRef(jname);
    env->DeleteLocalRef(hashMap);

    return !currentSessionId().empty();
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
    env->DeleteLocalRef(jstr);
}
