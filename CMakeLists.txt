cmake_minimum_required(VERSION 3.4.1)

add_library(
        # Lib name
        Core
        # Type (SHARED or STATIC)
        SHARED

        # List of source files
        src/main/cpp/Core_Wrapper.cxx
        src/main/cpp/CAL.cpp
        src/main/cpp/Core/Automat/PlaybackAutomatCore.cpp
        src/main/cpp/Core/Backend/BackendActionsCore.cpp
        src/main/cpp/Core/Time/TimestampHolder.cpp
        src/main/cpp/Core/Trackers/AdsTrackerCore.cpp
        src/main/cpp/Core/Trackers/ContentsTrackerCore.cpp
        src/main/cpp/Core/Trackers/TrackerCore.cpp
        src/main/cpp/Core/Utils/DictionaryMerge/DictionaryMerge.cpp
        src/main/cpp/Core/Utils/ValueHolder/ValueHolder.cpp
)

# To generate Swig code from the terminal, go to Core/Trackers dir and run:
# swig -c++ -java -package com.newrelic.videoagent.jni.swig -outdir /Users/asantaren/AndroidStudioProjects/VideoTest/NewRelicVideo/src/main/java/com/newrelic/videoagent/jni/swig -o ../../Core_Wrapper.cxx ../../Core_Wrapper.i
