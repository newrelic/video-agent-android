//
// Created by Andreu Santaren on 13/11/2018.
//

#include "test.h"
#include <string>
#include <jni.h>

extern "C" JNIEXPORT jstring JNICALL Java_com_newrelic_videoagent_NewRelicVideoAgent_testHello(JNIEnv *env, jobject thiz, jstring name) {
    const char *name_cstr = env->GetStringUTFChars(name, NULL);
    return env->NewStringUTF(("Hello " + std::string(name_cstr)).c_str());
}
