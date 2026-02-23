cmake_minimum_required(VERSION 3.22.1)
project(kvstore)

add_library(kvstore SHARED
    kvstore.cpp
    kvstore_jni.cpp
)

target_include_directories(kvstore PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(kvstore log)
target_link_options(kvstore PRIVATE -Wl,-z,max-page-size=16384)
