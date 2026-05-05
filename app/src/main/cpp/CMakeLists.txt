cmake_minimum_required(VERSION 3.22.1)
project("warped_llama")

set(CMAKE_CXX_STANDARD 17)

# --- llama.cpp integration ---
set(LLAMA_STANDALONE OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_STANDALONE OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_HEXAGON OFF CACHE BOOL "" FORCE)
set(GGML_SYCL OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
add_subdirectory(llama.cpp)

# --- JNI wrapper ---
add_library(warped_llama SHARED
    jni_bridge.cpp
    jni_bridge.h
)

target_include_directories(warped_llama PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_compile_definitions(warped_llama PRIVATE
    GGML_USE_CPU=1
    GGML_USE_CPU_AARCH64=1
)

target_link_libraries(warped_llama
    llama
    android
    log
)

target_link_options(warped_llama PRIVATE
    -Wl,-z,max-page-size=16384
)

set_target_properties(warped_llama PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/${ANDROID_ABI}
)
