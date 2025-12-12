cmake_minimum_required(VERSION 3.22.1)

project("mews")

add_library(
        mews-lib
        SHARED
        mews.cpp)

find_library(
        log-lib
        log)

target_link_libraries(
        mews-lib
        ${log-lib})

target_link_options(mews-lib PRIVATE "-Wl,-z,max-page-size=16384")