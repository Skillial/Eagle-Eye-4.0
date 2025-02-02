package com.bgcoding.camera2api.assessment;

import android.util.Log;

import com.bgcoding.camera2api.processing.ColorSpaceOperator;
import com.bgcoding.camera2api.thread.FlaggingThread;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.concurrent.Semaphore;

public class InputImageEnergyReader extends FlaggingThread {
    private final static String TAG = "InputImageEnergyReader";
    private final String inputImagePath;
    private Mat outputMat;

    public InputImageEnergyReader(Semaphore semaphore, String inputImagePath) {
        super(semaphore);
        this.inputImagePath = inputImagePath;
    }

    @Override
    public void run() {
        Log.d(TAG, "Started energy reading for " +this.inputImagePath);

        Mat inputMat = Imgcodecs.imread(this.inputImagePath);
        Imgproc.resize(inputMat, inputMat, new Size(), 0.125f, 0.125f, Imgproc.INTER_AREA); // downsampled

        Mat[] yuvMat = ColorSpaceOperator.convertRGBToYUV(inputMat);

        this.outputMat = yuvMat[0];
        inputMat.release();

        this.finishWork();

        Log.d(TAG, "Ended energy reading! Success!");
    }

    public Mat getOutputMat() {
        return this.outputMat;
    }



}