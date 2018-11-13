cmake_minimum_required(VERSION 3.4.1)

add_library(
        # Lib name
        test-jni
        # Type (SHARED or STATIC)
        SHARED
        # List of source files
        src/main/cpp/test.cpp)

include_directories(src/main/cpp/)
