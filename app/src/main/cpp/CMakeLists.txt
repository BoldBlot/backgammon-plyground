cmake_minimum_required(VERSION 3.22)
project(backgammonplyground C CXX)

# The engines (PureTD, GNU Backgammon, Open Sage) live in /engine so they can also be built and
# tested on the host.
add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/../../../../engine ${CMAKE_CURRENT_BINARY_DIR}/engine)

add_library(bgjni SHARED ptd_jni.c gnubg_jni.c sage_jni.cpp)
set_target_properties(bgjni PROPERTIES CXX_STANDARD 17)
target_link_libraries(bgjni PRIVATE ptdengine bgengine sageengine)
target_compile_options(bgjni PRIVATE -O2 -Wall -Wextra -Wno-unused-parameter)
