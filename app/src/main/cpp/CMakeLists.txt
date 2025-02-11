# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("camera2api")

# Set the directories for OpenCV and libpng
set(OpenCV_DIR "C:/Users/user/Desktop/Eagle-Eye-4.0-4x-by-qudrant/openCV/native/jni")
set(LIBPNG_DIR ${CMAKE_SOURCE_DIR}/libpng)

# Find OpenCV package
find_package(OpenCV REQUIRED)

# Add libpng include directory
include_directories(${LIBPNG_DIR})

# If you have a static or shared libpng library, you need to link it
# Rename the library to avoid conflicts
add_library(libpng_static STATIC
        ${LIBPNG_DIR}/png.c
)

# Creates and names a library, sets it as either STATIC or SHARED
# and provides the relative paths to its source code.
add_library(${CMAKE_PROJECT_NAME} SHARED camera2api.cpp)

# Find log library for Android
find_library(log-lib log)

# Specifies libraries CMake should link to your target library.
# You can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        ${log-lib}  # Use defined log-lib
        ${OpenCV_LIBS}  # Link OpenCV
        libpng_static  # Link the renamed libpng static library
)
