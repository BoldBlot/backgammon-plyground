cmake_minimum_required(VERSION 3.22)
project(ptdengine C)

# The PureTD engine: PureTD's move generator (MIT, puretd/bg_engine.c) and our own network
# evaluation, bear-off database and search (puretd/ptd.c). Used by the Android app through JNI and
# by host tests.

add_library(ptdengine STATIC puretd/ptd.c puretd/bg_engine.c)
target_include_directories(ptdengine PUBLIC ${CMAKE_CURRENT_LIST_DIR}/puretd)
set_target_properties(ptdengine PROPERTIES C_STANDARD 11 POSITION_INDEPENDENT_CODE ON)
target_compile_options(ptdengine PRIVATE -O3 -Wall -Wextra -Wno-unused-parameter)

find_package(Threads REQUIRED)
target_link_libraries(ptdengine PUBLIC Threads::Threads)
find_library(MATH_LIB m)
if(MATH_LIB)
    target_link_libraries(ptdengine PUBLIC ${MATH_LIB})
endif()

if(NOT ANDROID)
    # Host build of the JNI bridge so the app's JVM unit tests can use the real engine.
    find_package(JNI COMPONENTS JVM)
    if(JNI_FOUND)
        add_library(ptdjni SHARED ${CMAKE_CURRENT_LIST_DIR}/../app/src/main/cpp/ptd_jni.c)
        target_include_directories(ptdjni PRIVATE ${JNI_INCLUDE_DIRS})
        target_link_libraries(ptdjni PRIVATE ptdengine)
    endif()
endif()
