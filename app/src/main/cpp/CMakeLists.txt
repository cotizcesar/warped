cmake_minimum_required(VERSION 3.22.1)
project("warped_llama")

set(CMAKE_CXX_STANDARD 17)

# JNI wrapper source
set(JNI_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/jni_bridge.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/jni_bridge.h
)

add_library(warped_llama SHARED ${JNI_SOURCES})

target_include_directories(warped_llama PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_compile_definitions(warped_llama PRIVATE
    GGML_USE_CPU=1
    GGML_USE_CPU_AARCH64=1
)

target_link_libraries(warped_llama
    android
    log
)

target_link_options(warped_llama PRIVATE
    -Wl,-z,max-page-size=16384
)

set_target_properties(warped_llama PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/${ANDROID_ABI}
)
