//
// Created by Andreu Santaren on 13/11/2018.
//

#include "test.h"
#include <string>
#include <jni.h>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_newrelic_videoagent_NewRelicVideoAgent_testHello(JNIEnv *env, jclass type, jstring name) {
    const char *name_cstr = env->GetStringUTFChars(name, 0);

    std::string returnValue = "Hello " + std::string(name_cstr);

    env->ReleaseStringUTFChars(name, name_cstr);

    return env->NewStringUTF(returnValue.c_str());
}
