#include <jni.h>
#include <opencv2/opencv.hpp>
#include <fstream>
#include <android/log.h>
#include <android/bitmap.h>
#include <cstdio>
#define LOG_TAG "EagleEyeJNI"
// Write C++ code here.
//
// Do not forget to dynamically load the C++ library into your application.
//
// For instance,
//
// In MainActivity.java:
//    static {
//       System.loadLibrary("eagleEye");
//    }
//
// Or, in MainActivity.kt:
//    companion object {
//      init {
//         System.loadLibrary("eagleEye")
//      }
//    }
extern "C"
JNIEXPORT jobject JNICALL
Java_com_wangGang_eagleEye_processing_imagetools_ImageOperator_mergeQuadrants(JNIEnv *env,
                                                                              jobject thiz,
                                                                              jobjectArray filenames,
                                                                              jint divisionFactor,
                                                                              jint interpolationValue,
                                                                              jint quadrantWidth,
                                                                              jint quadrantHeight) {

    // Calculate total dimensions and initialize the merged image (BGR)
    int totalHeight = divisionFactor * quadrantHeight * interpolationValue;
    int totalWidth = divisionFactor * quadrantWidth * interpolationValue;
    cv::Mat* mergedImage = new cv::Mat(totalHeight, totalWidth, CV_8UC3, cv::Scalar(0, 0, 0));

    // Loop through the filenames and merge quadrants
    int numFiles = env->GetArrayLength(filenames);
    for (int i = 0; i < numFiles; i++) {
        jstring filename = (jstring) env->GetObjectArrayElement(filenames, i);
        const char* filenameStr = env->GetStringUTFChars(filename, nullptr);

        cv::Mat quadrant = cv::imread(filenameStr);
        if (quadrant.empty()) {
            env->ReleaseStringUTFChars(filename, filenameStr);
            continue;
        }

        int row = i / divisionFactor;
        int col = i % divisionFactor;
        int rowOffset = row * quadrantHeight * interpolationValue;
        int colOffset = col * quadrantWidth * interpolationValue;

        quadrant.copyTo((*mergedImage)(cv::Rect(colOffset, rowOffset, quadrant.cols, quadrant.rows)));

        // Delete the file after processing and release the filename string
        std::remove(filenameStr);
        env->ReleaseStringUTFChars(filename, filenameStr);
    }

    jclass matClass = env->FindClass("org/opencv/core/Mat");
    if (matClass == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, "EagleEyeJNI", "Cannot find org/opencv/core/Mat class");
        return nullptr;
    }
    // The constructor signature is (J)V meaning it accepts a native pointer.
    jmethodID matConstructor = env->GetMethodID(matClass, "<init>", "(J)V");
    if (matConstructor == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, "EagleEyeJNI", "Cannot find Mat(long addr) constructor");
        return nullptr;
    }
    jobject jMat = env->NewObject(matClass, matConstructor, reinterpret_cast<jlong>(mergedImage));

    return jMat;
}

cv::Mat produceMask(const cv::Mat& inputMat) {
    cv::Mat dstMask;
    // Copy the input image
    inputMat.copyTo(dstMask);

    // If the image has 3 or 4 channels, convert it to grayscale.
    if (dstMask.channels() == 3 || dstMask.channels() == 4) {
        cv::cvtColor(dstMask, dstMask, cv::COLOR_BGR2GRAY);
    }

    // Convert to single channel 8-bit (CV_8UC1)
    dstMask.convertTo(dstMask, CV_8UC1);

    // Apply a binary threshold: pixels with a value greater than 1 become 1, others 0.
    cv::threshold(dstMask, dstMask, 1.0, 1.0, cv::THRESH_BINARY);
    return dstMask;
}

extern "C"
JNIEXPORT jobject  JNICALL
Java_com_wangGang_eagleEye_processing_multiple_fusion_MeanFusionOperator_meanFuse(JNIEnv *env,
                                                                                  jobject thiz,
                                                                                  jobjectArray filenames,
                                                                                  jstring outputFilePath,
                                                                                  jstring outputFilePath2,
                                                                                  jobjectArray quadrantsNames,
                                                                                  jint divisionFactor,
                                                                                  jint interpolationValue,
                                                                                  jint quadrantWidth,
                                                                                  jint quadrantHeight) {
    // TODO: implement meanFuse()
    // Convert the Java string to a C++ string
    const char* outputPath = env->GetStringUTFChars(outputFilePath, nullptr);
    const char* outputPath2 = env->GetStringUTFChars(outputFilePath2, nullptr);

    // Get the number of inner arrays
    jsize outerLength = env->GetArrayLength(filenames);
    if (outerLength == 0) {
        __android_log_print(ANDROID_LOG_ERROR, "EagleEyeJNI", "No filename arrays provided");
        env->ReleaseStringUTFChars(outputFilePath, outputPath);
        env->ReleaseStringUTFChars(outputFilePath2, outputPath);
        return 0;
    }

    for (jsize i=0;i<outerLength;i++){
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Processing inner array %d", i);
        jobject innerObject = env->GetObjectArrayElement(filenames, i);
        jobjectArray innerFilenames = reinterpret_cast<jobjectArray>(innerObject);
        jsize innerLength = env->GetArrayLength(innerFilenames);
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Number of images in inner array %d", innerLength);
        cv::Mat sumMat, img, maskMat;
        for (jsize j = 0; j < innerLength; j++) {
            jstring filename = (jstring) env->GetObjectArrayElement(innerFilenames, j);
            const char* filenameStr = env->GetStringUTFChars(filename, nullptr);
            cv::Mat img = cv::imread(filenameStr, cv::IMREAD_UNCHANGED);
            if (img.empty()) {
                __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Image not loaded properly: %s", filenameStr);
                env->ReleaseStringUTFChars(filename, filenameStr);
                continue; // Skip if image is not loaded properly
            }
            // Convert the image to 16-bit to avoid overflow during summing
            img.convertTo(img, CV_16UC(img.channels()));
            if (sumMat.empty()) {
                sumMat = cv::Mat::zeros(img.size(), CV_16UC(img.channels()));
            }
            // Create a mask based on the image content (Kotlin approach)
            maskMat = produceMask(img);
            // Add the current image to the cumulative sum using the mask
            cv::add(sumMat, img, sumMat, maskMat, CV_16UC(img.channels()));

            // Release resources for the filename string
            env->ReleaseStringUTFChars(filename, filenameStr);

        }
        if (sumMat.empty()) {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "No valid images processed.");
            return 0;
        }
        sumMat /= innerLength;
        // save image
        jstring quadrantName = (jstring) env->GetObjectArrayElement(quadrantsNames, i);
        const char* quadrantNameStr = env->GetStringUTFChars(quadrantName, nullptr);
        cv::imwrite(quadrantNameStr, sumMat);
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Mean fusion completed for %s", quadrantNameStr);
        env->ReleaseStringUTFChars(quadrantName, quadrantNameStr);


    }
    int totalHeight = divisionFactor * quadrantHeight * interpolationValue;
    int totalWidth = divisionFactor * quadrantWidth * interpolationValue;
    cv::Mat* mergedImage = new cv::Mat(totalHeight, totalWidth, CV_8UC3, cv::Scalar(0, 0, 0));

    for (int i = 0; i < env->GetArrayLength(quadrantsNames); i++) {
        jstring filename = (jstring) env->GetObjectArrayElement(quadrantsNames, i);
        const char* filenameStr = env->GetStringUTFChars(filename, nullptr);

        // Read the current quadrant image
        cv::Mat quadrant = cv::imread(filenameStr);
        if (quadrant.empty()) {
            env->ReleaseStringUTFChars(filename, filenameStr);
            env->DeleteLocalRef(filename);
            continue; // Skip if the image couldn't be loaded
        }

        int row = i / divisionFactor;
        int col = i % divisionFactor;

        // Compute the position of the quadrant in the merged image
        int rowOffset = row * quadrantHeight * interpolationValue;
        int colOffset = col * quadrantWidth * interpolationValue;

        // Copy the quadrant to the correct position in the merged image
        quadrant.copyTo((*mergedImage)(cv::Rect(colOffset, rowOffset, quadrant.cols, quadrant.rows)));



        // Release memory for the current quadrant
        cv::Mat().release();

        // Delete the file after processing
        std::remove(filenameStr);

        // Release the filename string
        env->ReleaseStringUTFChars(filename, filenameStr);
        env->DeleteLocalRef(filename);
    }

    // Optionally, print memory usage here
    // Log.d("Memory test - per merge", ...);
//    // Save the final image to the specified file path
//    cv::imwrite(outputPath, mergedImage);
//    cv::imwrite(outputPath2, mergedImage);
//    env->ReleaseStringUTFChars(outputFilePath, outputPath);
//    env->ReleaseStringUTFChars(outputFilePath2, outputPath2);
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Mean fusion completed successfully.");
    // --- Create a Java Mat object from the native cv::Mat pointer ---
    jclass matClass = env->FindClass("org/opencv/core/Mat");
    if (matClass == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, "EagleEyeJNI", "Cannot find org/opencv/core/Mat class");
        return nullptr;
    }
    // The constructor signature is (J)V meaning it accepts a native pointer.
    jmethodID matConstructor = env->GetMethodID(matClass, "<init>", "(J)V");
    if (matConstructor == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, "EagleEyeJNI", "Cannot find Mat(long addr) constructor");
        return nullptr;
    }
    jobject jMat = env->NewObject(matClass, matConstructor, reinterpret_cast<jlong>(mergedImage));

    return jMat;
}



extern "C"
JNIEXPORT void JNICALL
Java_com_wangGang_eagleEye_processing_imagetools_ImageOperator_mergeQuadrantsWithFileSave(
        JNIEnv *env, jobject thiz, jobjectArray filenames, jint divisionFactor,
        jint interpolationValue, jint quadrantWidth, jint quadrantHeight, jstring outputFilePath) {
    // TODO: implement mergeQuadrantsWithFileSave()
    // Calculate total dimensions and initialize the merged image (BGR)
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
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Time taken to merge and save the image: %lld seconds", elapsedTime);

    // Release the file path string
    env->ReleaseStringUTFChars(outputFilePath, outputPath);
}

