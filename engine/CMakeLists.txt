cmake_minimum_required(VERSION 3.22)
project(engines C CXX)

# The three engines, used by the Android app through JNI (one library, bgjni) and by host tests:
# - PureTD: PureTD's move generator (MIT, puretd/bg_engine.c) and our own network evaluation,
#   bear-off database and search (puretd/ptd.c).
# - GNU Backgammon: its evaluation code (GPL v3, gnubg/) behind a small C API (src/bgengine.c),
#   with a glib shim (compat/).
# - Open Sage: its C++ engine library (MPL 2.0, sage/).

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

set(GNUBG_DIR ${CMAKE_CURRENT_LIST_DIR}/gnubg)
add_library(bgengine STATIC
    src/bgengine.c
    compat/glib_compat.c
    ${GNUBG_DIR}/eval.c
    ${GNUBG_DIR}/bearoff.c
    ${GNUBG_DIR}/bearoffgammon.c
    ${GNUBG_DIR}/positionid.c
    ${GNUBG_DIR}/matchequity.c
    ${GNUBG_DIR}/matchid.c
    ${GNUBG_DIR}/mec.c
    ${GNUBG_DIR}/mtsupport.c
    ${GNUBG_DIR}/lib/neuralnet.c
    ${GNUBG_DIR}/lib/neuralnetsse.c
    ${GNUBG_DIR}/lib/inputs.c
    ${GNUBG_DIR}/lib/cache.c
    ${GNUBG_DIR}/lib/isaac.c
    ${GNUBG_DIR}/lib/md5.c
    ${GNUBG_DIR}/lib/list.c
)
# compat must come first so that <glib.h> resolves to the shim.
target_include_directories(bgengine
    PUBLIC  ${CMAKE_CURRENT_LIST_DIR}/src
    PRIVATE ${CMAKE_CURRENT_LIST_DIR}/compat ${GNUBG_DIR} ${GNUBG_DIR}/lib)
target_compile_definitions(bgengine PRIVATE HAVE_CONFIG_H=1)
set_target_properties(bgengine PROPERTIES C_STANDARD 11 POSITION_INDEPENDENT_CODE ON)
# gnubg is old C; keep the build quiet about its style but not about real errors.
target_compile_options(bgengine PRIVATE
    -O2 -fno-strict-aliasing
    -Wno-unused-parameter -Wno-sign-compare -Wno-unused-function -Wno-unused-variable
    -Wno-unused-but-set-variable -Wno-missing-field-initializers -Wno-deprecated-declarations
    -Wno-implicit-fallthrough
    $<$<C_COMPILER_ID:Clang>:-Wno-unknown-warning-option>)
if(CMAKE_SYSTEM_PROCESSOR MATCHES "^(x86_64|AMD64|i[3-6]86)$")
    target_compile_options(bgengine PRIVATE -msse2)
endif()
if(MATH_LIB)
    target_link_libraries(bgengine PUBLIC ${MATH_LIB})
endif()
if(ANDROID)
    find_library(ANDROID_LOG_LIB log)
    target_link_libraries(bgengine PUBLIC ${ANDROID_LOG_LIB})
endif()

set(SAGE_DIR ${CMAKE_CURRENT_LIST_DIR}/sage)
add_library(sageengine STATIC
    ${SAGE_DIR}/src/board.cpp
    ${SAGE_DIR}/src/moves.cpp
    ${SAGE_DIR}/src/strategy.cpp
    ${SAGE_DIR}/src/pubeval.cpp
    ${SAGE_DIR}/src/encoding.cpp
    ${SAGE_DIR}/src/neural_net.cpp
    ${SAGE_DIR}/src/multipy.cpp
    ${SAGE_DIR}/src/cube.cpp
    ${SAGE_DIR}/src/cube_eval.cpp
    ${SAGE_DIR}/src/match_equity.cpp
    ${SAGE_DIR}/src/bearoff.cpp
)
target_include_directories(sageengine PUBLIC ${SAGE_DIR}/include)
set_target_properties(sageengine PROPERTIES CXX_STANDARD 17 CXX_STANDARD_REQUIRED ON POSITION_INDEPENDENT_CODE ON)
target_compile_definitions(sageengine PRIVATE NOMINMAX)
# Open Sage's own optimization flags (cpp/CMakeLists_cpu.txt); its x86 code needs AVX2.
if(CMAKE_SYSTEM_PROCESSOR MATCHES "^(arm64|aarch64)$")
    target_compile_options(sageengine PRIVATE -O3 -funsafe-math-optimizations -fno-math-errno -funroll-loops)
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "^(x86_64|AMD64)$")
    target_compile_options(sageengine PRIVATE -O3 -ffast-math -funroll-loops -mavx2 -mfma)
else()
    target_compile_options(sageengine PRIVATE -O3 -funroll-loops)
endif()
target_compile_options(sageengine PRIVATE -w)
target_link_libraries(sageengine PUBLIC Threads::Threads)

if(NOT ANDROID)
    # Host-only: GNU engine tests, and a host build of the JNI library so the app's JVM unit tests
    # can use the real engines.
    enable_testing()
    add_executable(engine_test test/engine_test.c)
    target_link_libraries(engine_test PRIVATE bgengine)
    add_test(NAME engine_test COMMAND engine_test ${CMAKE_CURRENT_LIST_DIR}/../app/src/main/assets/gnubg)

    find_package(JNI COMPONENTS JVM)
    if(JNI_FOUND)
        set(JNI_SRC ${CMAKE_CURRENT_LIST_DIR}/../app/src/main/cpp)
        add_library(bgjni SHARED ${JNI_SRC}/ptd_jni.c ${JNI_SRC}/gnubg_jni.c ${JNI_SRC}/sage_jni.cpp)
        target_include_directories(bgjni PRIVATE ${JNI_INCLUDE_DIRS})
        set_target_properties(bgjni PROPERTIES CXX_STANDARD 17)
        target_link_libraries(bgjni PRIVATE ptdengine bgengine sageengine)
    endif()
endif()
