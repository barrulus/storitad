cmake_minimum_required(VERSION 3.22)
project(storitad-whisper LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(WHISPER_ROOT ${CMAKE_SOURCE_DIR}/../../../../native/whisper.cpp)

set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE         OFF CACHE BOOL "" FORCE)

add_subdirectory(${WHISPER_ROOT} whisper-build EXCLUDE_FROM_ALL)

add_library(storitad-whisper SHARED storitad_whisper.cpp)
target_link_libraries(storitad-whisper PRIVATE whisper log)
target_compile_options(storitad-whisper PRIVATE -fexceptions -frtti)
