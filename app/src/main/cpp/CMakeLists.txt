cmake_minimum_required(VERSION 3.22)
project(backgammonplyground C)

# The engine (PureTD networks with our search) lives in /engine so it can also be built and tested
# on the host.
add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/../../../../engine ${CMAKE_CURRENT_BINARY_DIR}/engine)

add_library(ptdjni SHARED ptd_jni.c)
target_link_libraries(ptdjni PRIVATE ptdengine)
target_compile_options(ptdjni PRIVATE -O2 -Wall -Wextra -Wno-unused-parameter)
