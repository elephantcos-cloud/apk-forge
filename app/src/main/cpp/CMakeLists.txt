cmake_minimum_required(VERSION 3.22.1)

project("apkforge_native" CXX C)

# C++ Standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Compiler flags
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -O2 -fvisibility=hidden")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -O2")

# Find required Android libraries
find_library(log-lib log)
find_library(android-lib android)

# ─── Native Bridge Library ─────────────────────────────────────────────────
add_library(
    apkforge_native
    SHARED
    native_bridge.cpp
    crypto_utils.cpp
    file_ops.cpp
    hash_engine.cpp
)

# Include directories
target_include_directories(apkforge_native PRIVATE
    ${CMAKE_SOURCE_DIR}/include
    ${ANDROID_NDK}/sysroot/usr/include
)

# Link libraries
target_link_libraries(apkforge_native
    ${log-lib}
    ${android-lib}
)

# Optimization for release
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_compile_options(apkforge_native PRIVATE -O3 -DNDEBUG)
endif()
