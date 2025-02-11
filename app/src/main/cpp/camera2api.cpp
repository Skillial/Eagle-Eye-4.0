#include <jni.h>
#include <opencv2/opencv.hpp>
#include <fstream>
#include <android/log.h>
#define LOG_TAG "Camera2API"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

extern "C"
JNIEXPORT void JNICALL
Java_com_bgcoding_camera2api_MainActivity_interpolate(
        JNIEnv *env,
        jobject thiz,
        jobject context,
        jobjectArray filenames,
        jint divisionFactor,
        jint interpolationValue,
        jint quadrantWidth,
        jint quadrantHeight,
        jstring outputFilePath) {

    // Convert the Java string to a C++ string
    const char* outputPath = env->GetStringUTFChars(outputFilePath, nullptr);
    // Start time to measure the time taken for the operation (optional)
    long long startTime = cv::getTickCount();

    // Initialize the merged image (size depends on your divisionFactor)
    int totalHeight = divisionFactor * quadrantHeight * interpolationValue;
    int totalWidth = divisionFactor * quadrantWidth * interpolationValue;
    cv::Mat mergedImage(totalHeight, totalWidth, CV_8UC3, cv::Scalar(0, 0, 0)); // Black image as a base
    // Loop through the filenames and process each image
    for (int i = 0; i < env->GetArrayLength(filenames); i++) {
        jstring filename = (jstring) env->GetObjectArrayElement(filenames, i);
        const char* filenameStr = env->GetStringUTFChars(filename, nullptr);

        // Read the current quadrant image
        cv::Mat quadrant = cv::imread(filenameStr);
        if (quadrant.empty()) {
            continue; // Skip if the image couldn't be loaded
        }

        int row = i / divisionFactor;
        int col = i % divisionFactor;

        // Compute the position of the quadrant in the merged image
        int rowOffset = row * quadrantHeight * interpolationValue;
        int colOffset = col * quadrantWidth * interpolationValue;

        // Copy the quadrant to the correct position in the merged image
        quadrant.copyTo(
                mergedImage(cv::Rect(colOffset, rowOffset, quadrant.cols, quadrant.rows))
        );


        // Release memory for the current quadrant
        cv::Mat().release();

        // Delete the file after processing
        std::remove(filenameStr);

        // Release the filename string
        env->ReleaseStringUTFChars(filename, filenameStr);
    }

    // Optionally, print memory usage here
    // Log.d("Memory test - per merge", ...);

    // Save the final image to the specified file path
    cv::imwrite(outputPath, mergedImage);

    // Calculate and log elapsed time
    long long elapsedTime = (cv::getTickCount() - startTime) / cv::getTickFrequency();
    std::cout << "Time taken to merge and save the image: " << elapsedTime << " seconds" << std::endl;

    // Release the file path string
    env->ReleaseStringUTFChars(outputFilePath, outputPath);
}

