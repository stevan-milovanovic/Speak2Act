cmake_minimum_required(VERSION 3.10)

project(whisper_stt CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Vendored whisper.cpp (see whisper/WHISPER_CPP_VERSION.txt)
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper)

# Always optimise the matrix-heavy ggml/whisper code, even in the Debug variant.
# Android's Debug build type compiles at -O0, which makes on-device inference ~10x
# slower. Applied before add_subdirectory so it also covers the ggml targets.
# (clang uses the last -O flag, so this overrides the debug -O0 while keeping -g.)
add_compile_options(-O3)

# Build ggml. On Android only the CPU backend is enabled; all other backends
# (CUDA, Vulkan, Metal, ...) are OFF and their source dirs are pruned.
add_subdirectory(${WHISPER_DIR}/ggml ${CMAKE_CURRENT_BINARY_DIR}/ggml_build)

find_library(LOG_LIB log)

add_library(
    whisper_jni
    SHARED
    ${WHISPER_DIR}/src/whisper.cpp
    whisper_jni.cpp
)

target_include_directories(whisper_jni PRIVATE
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/src
    ${WHISPER_DIR}/ggml/include
    ${WHISPER_DIR}/ggml/src
)

target_compile_definitions(whisper_jni PRIVATE GGML_USE_CPU)

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_options(whisper_jni PRIVATE -ffunction-sections -fdata-sections)
    target_link_options(whisper_jni PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)
endif ()

target_link_libraries(whisper_jni ${LOG_LIB} android ggml)
