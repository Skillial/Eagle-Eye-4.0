package com.wangGang.eagleEye.processing.imagetools

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.wangGang.eagleEye.io.FileImageWriter
import com.wangGang.eagleEye.io.ImageFileAttribute
import com.wangGang.eagleEye.model.single_gaussian.LoadedImagePatch
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File

/**
 * Miscellaneous image operators
 * Created by NeilDG on 5/23/2016.
 */
object ImageOperator {
    init {
        System.loadLibrary("eagleEye")
    }
    private const val TAG = "ImageOperator"

    external fun mergeQuadrants(
        filenames: Array<String>,  // or List<String> if you prefer using a list
        divisionFactor: Int,
        interpolationValue: Int,
        quadrantWidth: Int,
        quadrantHeight: Int
    ): Mat

    external fun mergeQuadrantsWithFileSave(
        filenames: Array<String>,  // or List<String> if you prefer using a list
        divisionFactor: Int,
        interpolationValue: Int,
        quadrantWidth: Int,
        quadrantHeight: Int,
        outputFile: String
    )
    /*
     * Adds random noise. Returns the same mat with the noise operator applied.
     */
    fun induceNoise(inputMat: Mat): Mat {
        val noiseMat = Mat(inputMat.size(), inputMat.type())
        Core.randn(noiseMat, 5.0, 20.0)

        Core.add(noiseMat, inputMat, inputMat)
        return inputMat
    }

    fun produceMask(inputMat: Mat): Mat {
        val baseMaskMat = Mat()
        produceMask(inputMat, baseMaskMat)

        return baseMaskMat
    }

    fun produceMask(inputMat: Mat, dstMask: Mat) {
        inputMat.copyTo(dstMask)

        if (inputMat.channels() == 3 || inputMat.channels() == 4) {
            Imgproc.cvtColor(dstMask, dstMask, Imgproc.COLOR_BGR2GRAY)
        }

        dstMask.convertTo(dstMask, CvType.CV_8UC1)
        Imgproc.threshold(dstMask, dstMask, 1.0, 1.0, Imgproc.THRESH_BINARY)
    }

    fun produceMask(inputMat: Mat, threshold: Int): Mat {
        val baseMaskMat = Mat()
        inputMat.copyTo(baseMaskMat)

        if (inputMat.channels() == 3 || inputMat.channels() == 4) {
            Imgproc.cvtColor(baseMaskMat, baseMaskMat, Imgproc.COLOR_BGR2GRAY)
        }

        baseMaskMat.convertTo(baseMaskMat, CvType.CV_8UC1)
        Imgproc.threshold(
            baseMaskMat,
            baseMaskMat,
            threshold.toDouble(),
            1.0,
            Imgproc.THRESH_BINARY
        )

        return baseMaskMat
    }

    fun produceMask(inputMat: Mat, threshold: Int, newValue: Int): Mat {
        val baseMaskMat = Mat()
        inputMat.copyTo(baseMaskMat)

        if (inputMat.channels() == 3 || inputMat.channels() == 4) {
            Imgproc.cvtColor(baseMaskMat, baseMaskMat, Imgproc.COLOR_BGR2GRAY)
        }

        baseMaskMat.convertTo(baseMaskMat, CvType.CV_8UC1)
        Imgproc.threshold(
            baseMaskMat,
            baseMaskMat,
            threshold.toDouble(),
            newValue.toDouble(),
            Imgproc.THRESH_BINARY
        )

        return baseMaskMat
    }

    /*
     * Zero values are labelled as 1, 0 for nonzero values
     */
    fun produceOppositeMask(inputMat: Mat, threshold: Int, newValue: Int): Mat {
        val baseMaskMat = Mat()
        inputMat.copyTo(baseMaskMat)

        if (inputMat.channels() == 3 || inputMat.channels() == 4) {
            Imgproc.cvtColor(baseMaskMat, baseMaskMat, Imgproc.COLOR_BGR2GRAY)
        }

        baseMaskMat.convertTo(baseMaskMat, CvType.CV_8UC1)
        Imgproc.threshold(
            baseMaskMat,
            baseMaskMat,
            threshold.toDouble(),
            newValue.toDouble(),
            Imgproc.THRESH_BINARY_INV
        )

        return baseMaskMat
    }

    /*
     * Performs an edge sobel measure by counting the non-zero elements produced by getting the sobel derivatives of an image in X and  Y axis.
     * Creates a copy of the input mat for temporary processing.
     */
    fun edgeSobelMeasure(inputMat: Mat, applyBlur: Boolean): Int {
        val referenceMat = Mat()
        inputMat.copyTo(referenceMat)
        val gradX = Mat()
        val gradY = Mat()
        var referenceSobelMat = Mat()

        if (applyBlur) {
            Imgproc.blur(referenceMat, referenceMat, Size(3.0, 3.0))
        }

        Imgproc.Sobel(referenceMat, gradX, CvType.CV_16S, 1, 0, 3, 1.0, 0.0, Core.BORDER_DEFAULT)
        Imgproc.Sobel(referenceMat, gradY, CvType.CV_16S, 0, 1, 3, 1.0, 0.0, Core.BORDER_DEFAULT)

        gradX.convertTo(gradX, CvType.CV_8UC(gradX.channels()))
        gradY.convertTo(gradY, CvType.CV_8UC(gradX.channels()))
        Core.addWeighted(gradX, 0.5, gradY, 0.5, 0.0, referenceSobelMat)
        referenceSobelMat = produceMask(referenceSobelMat)

        val sobelReferenceMeasure = Core.countNonZero(referenceSobelMat)

        referenceSobelMat.release()
        referenceMat.release()
        gradX.release()
        gradY.release()

        return sobelReferenceMeasure
    }

    /*
    * Performs an edge sobel measure by counting the non-zero elements produced by getting the sobel derivatives of an image in X and  Y axis.
    * Creates a copy of the input mat for temporary processing. Can be used for debugging to save the sobel edge result.
    */
    fun edgeSobelMeasure(inputMat: Mat, applyBlur: Boolean, debugFileName: String): Int {
        val referenceMat = Mat()
        inputMat.copyTo(referenceMat)
        val gradX = Mat()
        val gradY = Mat()
        var referenceSobelMat = Mat()

        if (applyBlur) {
            Imgproc.blur(referenceMat, referenceMat, Size(3.0, 3.0))
        }

        Imgproc.Sobel(referenceMat, gradX, CvType.CV_16S, 1, 0, 3, 1.0, 0.0, Core.BORDER_DEFAULT)
        Imgproc.Sobel(referenceMat, gradY, CvType.CV_16S, 0, 1, 3, 1.0, 0.0, Core.BORDER_DEFAULT)

        gradX.convertTo(gradX, CvType.CV_8UC(gradX.channels()))
        gradY.convertTo(gradY, CvType.CV_8UC(gradX.channels()))
        Core.addWeighted(gradX, 0.5, gradY, 0.5, 0.0, referenceSobelMat)

        FileImageWriter.getInstance()
            ?.saveMatrixToImage(referenceSobelMat, debugFileName, ImageFileAttribute.FileType.JPEG)

        referenceSobelMat = produceMask(referenceSobelMat)
        val sobelReferenceMeasure = Core.countNonZero(referenceSobelMat)

        referenceSobelMat.release()
        referenceMat.release()
        gradX.release()
        gradY.release()

        return sobelReferenceMeasure
    }

    fun blendImages(matList: List<Mat>): Mat {
        val matInput = matList[0]
        val mergedMat = Mat(matInput.size(), matInput.type(), Scalar(0.0))
        //Add each image from a vector<Mat> inputImages with weight 1.0/n where n is number of images to merge
        for (i in matList.indices) {
            val mat = matList[i]

            //Core.addWeighted(mergedMat, 1, mat, 1.0/matList.size(), 0, mergedMat);
            Core.add(mergedMat, mat, mergedMat)
            FileImageWriter.getInstance()?.saveMatrixToImage(
                mergedMat, "fusion",
                "fuse_$i", ImageFileAttribute.FileType.JPEG
            )
        }

        return mergedMat
    }

    /*
     * Performs zero-filling upsample of a given mat
     */
    fun performZeroFill(fromMat: Mat, scaling: Int, xOffset: Int, yOffset: Int): Mat {
        val hrMat = Mat.zeros(fromMat.rows() * scaling, fromMat.cols() * scaling, fromMat.type())

        for (row in 0 until fromMat.rows()) {
            for (col in 0 until fromMat.cols()) {
                val lrPixelData = fromMat[row, col]

                val resultRow = (row * scaling) + yOffset
                val resultCol = (col * scaling) + xOffset

                if (resultRow < hrMat.rows() && resultCol < hrMat.cols()) {
                    hrMat.put(resultRow, resultCol, *lrPixelData)
                }
            }
        }

        return hrMat
    }

    /*
     * PErforms zero-filling according to pixel displacement provided
     */
    fun performZeroFill(fromMat: Mat, scaling: Int, xDisplacement: Mat, yDisplacement: Mat): Mat {
        val hrMat = Mat.zeros(fromMat.rows() * scaling, fromMat.cols() * scaling, fromMat.type())

        for (row in 0 until fromMat.rows()) {
            for (col in 0 until fromMat.cols()) {
                val lrPixelData = fromMat[row, col]

                val xOffset = xDisplacement[row, col][0]
                val yOffset = yDisplacement[row, col][0]

                val floorRow = Math.round(yOffset).toInt() * scaling
                val floorCol = Math.round(xOffset).toInt() * scaling

                if (floorRow < hrMat.rows() && floorCol < hrMat.cols()) {
                    //Log.d(TAG, "Debug values. xOffset: " +xOffset+ " yOffset: " +yOffset+ " X: " +floorCol+ " Y: " +floorRow);
                    hrMat.put(floorRow, floorCol, *lrPixelData)
                }
            }
        }

        return hrMat
    }

    /*
     * Copies the rows of a given mat to the hr mat by zero-filling.
     */
    fun copyMat(fromMat: Mat, hrMat: Mat, scaling: Int, xOffset: Int, yOffset: Int) {
        for (row in 0 until fromMat.rows()) {
            for (col in 0 until fromMat.cols()) {
                val lrPixelData = fromMat[row, col]

                val resultRow = (row * scaling) + yOffset
                val resultCol = (col * scaling) + xOffset

                if (resultRow < hrMat.rows() && resultCol < hrMat.cols()) {
                    hrMat.put(resultRow, resultCol, *lrPixelData)
                }
            }
        }
    }

    fun convertRGBToGray(inputMat: Mat, releaseOldMat: Boolean): Mat {
        val outputMat = Mat()
        if (inputMat.channels() == 3 || inputMat.channels() == 4) {
            Imgproc.cvtColor(inputMat, outputMat, Imgproc.COLOR_BGR2GRAY)
        }

        if (releaseOldMat) {
            inputMat.release()
        }

        return outputMat
    }


    /*
     * Converts the given mat into a type. Returns the converted mat
     */
    fun convertType(fromMat: Mat, dtype: Int, releaseOldMat: Boolean): Mat {
        val convertMat = Mat()
        fromMat.convertTo(convertMat, dtype)

        if (releaseOldMat) {
            fromMat.release()
        }

        return convertMat
    }

    /*
     * Performs interpolation using an existing interpolation algo by OPENCV
     */
    fun performInterpolation(fromMat: Mat, scaling: Float, interpolationType: Int): Mat {
        val newRows = Math.round(fromMat.rows() * scaling)
        val newCols = Math.round(fromMat.cols() * scaling)

        Log.d(TAG, "Orig size: " + fromMat.size() + " New size: " + newRows + " X " + newCols)
        val hrMat = Mat.zeros(newRows, newCols, fromMat.type())

        Imgproc.resize(
            fromMat,
            hrMat,
            hrMat.size(),
            scaling.toDouble(),
            scaling.toDouble(),
            interpolationType
        )

        return hrMat
    }

    fun bitmapToMat(bitmap: Bitmap): Mat {
        val mat = Mat()
        val bmp32 = bitmap.copy(Bitmap.Config.ARGB_8888, true) // Ensure it's in ARGB_8888 format
        Utils.bitmapToMat(bmp32, mat)
        Core.rotate(mat, mat, Core.ROTATE_90_CLOCKWISE)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2BGR)
        return mat
    }

    fun matToBitmap(mat: Mat): Bitmap {
        val newMat = Mat()
        mat.copyTo(newMat)
        Core.rotate(newMat, newMat, Core.ROTATE_90_COUNTERCLOCKWISE)
        Imgproc.cvtColor(newMat, newMat, Imgproc.COLOR_BGR2RGB)
        val bitmap = Bitmap.createBitmap(newMat.cols(), newMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(newMat, bitmap)
        return bitmap
    }


    fun performJNIInterpolationWithMerge(fromMat: Mat, scaling: Float): Bitmap {
        val divisionFactor = 4
        val width = fromMat.cols()
        val height = fromMat.rows()
        val quadrantWidth = width / divisionFactor
        val remainderWidth = width % divisionFactor
        val quadrantHeight = height / divisionFactor
        val remainderHeight = height % divisionFactor

        // initialize filenames
        val filenames = Array(divisionFactor * divisionFactor) { index ->
            "/quadrant${index + 1}"
        }

        val fileList = mutableListOf<String>()

        var quadrantCount = 0;
        for (i in 0 until divisionFactor) {
            for (j in 0 until divisionFactor) {
                val topLeftX = j * quadrantWidth
                val bottomRightX =
                    (j + 1) * quadrantWidth + if (j == divisionFactor - 1) remainderWidth else 0
                val topLeftY = i * quadrantHeight
                val bottomRightY =
                    (i + 1) * quadrantHeight + if (i == divisionFactor - 1) remainderHeight else 0
                FileImageWriter.getInstance()?.saveMatrixToImageReturnPath(
                    fromMat.submat(topLeftY, bottomRightY, topLeftX, bottomRightX),
                    filenames[quadrantCount],
                    ImageFileAttribute.FileType.JPEG
                )?.let { fileList.add(it) }

                quadrantCount += 1;
            }
        }
        fromMat.release()
        for (i in fileList.indices) {
            val quadrant = Imgcodecs.imread(fileList[i])
            val resizedQuadrant = Mat()

            // perform bicubic interpolation
            Imgproc.resize(
                quadrant, resizedQuadrant,
                Size(
                    quadrant.cols().toDouble() * scaling,
                    quadrant.rows().toDouble() * scaling
                ),
                0.0, 0.0, Imgproc.INTER_CUBIC
            )
            fileList[i] = FileImageWriter.getInstance()?.saveMatrixToImageReturnPath(
                resizedQuadrant,
                filenames[i],
                ImageFileAttribute.FileType.JPEG
            ).toString()
            quadrant.release()
            resizedQuadrant.release()
        }
        val newMat = mergeQuadrants(fileList.toTypedArray(), divisionFactor, scaling.toInt(), quadrantWidth, quadrantHeight)
        return matToBitmap(newMat)
    }

    fun performInterpolationWithImageSave(fromMat: Mat, scaling: Float) {
        val divisionFactor = 4
        val width = fromMat.cols()
        val height = fromMat.rows()
        val quadrantWidth = width / divisionFactor
        val remainderWidth = width % divisionFactor
        val quadrantHeight = height / divisionFactor
        val remainderHeight = height % divisionFactor

        // initialize filenames
        val filenames = Array(divisionFactor * divisionFactor) { index ->
            "/quadrant${index + 1}"
        }

        val fileList = mutableListOf<String>()

        var quadrantCount = 0;
        for (i in 0 until divisionFactor) {
            for (j in 0 until divisionFactor) {
                val topLeftX = j * quadrantWidth
                val bottomRightX =
                    (j + 1) * quadrantWidth + if (j == divisionFactor - 1) remainderWidth else 0
                val topLeftY = i * quadrantHeight
                val bottomRightY =
                    (i + 1) * quadrantHeight + if (i == divisionFactor - 1) remainderHeight else 0
                FileImageWriter.getInstance()?.saveMatrixToImageReturnPath(
                    fromMat.submat(topLeftY, bottomRightY, topLeftX, bottomRightX),
                    filenames[quadrantCount],
                    ImageFileAttribute.FileType.JPEG
                )?.let { fileList.add(it) }

                quadrantCount += 1;
            }
        }
        fromMat.release()
        for (i in fileList.indices) {
            val quadrant = Imgcodecs.imread(fileList[i])
            val resizedQuadrant = Mat()

            // perform bicubic interpolation
            Imgproc.resize(
                quadrant, resizedQuadrant,
                Size(
                    quadrant.cols().toDouble() * scaling,
                    quadrant.rows().toDouble() * scaling
                ),
                0.0, 0.0, Imgproc.INTER_CUBIC
            )
            fileList[i] = FileImageWriter.getInstance()?.saveMatrixToImageReturnPath(
                resizedQuadrant,
                filenames[i],
                ImageFileAttribute.FileType.JPEG
            ).toString()
            quadrant.release()
            resizedQuadrant.release()
        }
        val outputFile = FileImageWriter.getInstance()?.getSharedAfterPath(ImageFileAttribute.FileType.JPEG)
            ?: throw IllegalStateException("Failed to get output file path 1")
        mergeQuadrantsWithFileSave(fileList.toTypedArray(), divisionFactor, scaling.toInt(), quadrantWidth, quadrantHeight, outputFile)
    }
    fun performJNIInterpolation(fromMat: Mat, count: Int): Array<String> {
        val divisionFactor = 4
        val width = fromMat.cols()
        val height = fromMat.rows()
        val quadrantWidth = width / divisionFactor
        val remainderWidth = width % divisionFactor
        val quadrantHeight = height / divisionFactor
        val remainderHeight = height % divisionFactor

        // initialize filenames
        val filenames = Array(divisionFactor * divisionFactor) { index ->
            "/quadrant${count}_${index + 1}"
        }

        val fileList = mutableListOf<String>()

        var quadrantCount = 0;
        for (i in 0 until divisionFactor) {
            for (j in 0 until divisionFactor) {
                val topLeftX = j * quadrantWidth
                val bottomRightX =
                    (j + 1) * quadrantWidth + if (j == divisionFactor - 1) remainderWidth else 0
                val topLeftY = i * quadrantHeight
                val bottomRightY =
                    (i + 1) * quadrantHeight + if (i == divisionFactor - 1) remainderHeight else 0
                FileImageWriter.getInstance()?.saveMatrixToImageReturnPath(
                    fromMat.submat(topLeftY, bottomRightY, topLeftX, bottomRightX),
                    filenames[quadrantCount],
                    ImageFileAttribute.FileType.JPEG
                )?.let { fileList.add(it) }

                quadrantCount += 1;
            }
        }
        fromMat.release()
        return fileList.toTypedArray()
    }

    fun divideImages(fromMat: Mat, count: Int): Array<String> {
        val divisionFactor = 4
        val width = fromMat.cols()
        val height = fromMat.rows()
        val quadrantWidth = width / divisionFactor
        val remainderWidth = width % divisionFactor
        val quadrantHeight = height / divisionFactor
        val remainderHeight = height % divisionFactor

        // initialize filenames
        val filenames = Array(divisionFactor * divisionFactor) { index ->
            "/quadrant${count}_${index + 1}"
        }

        val fileList = mutableListOf<String>()

        var quadrantCount = 0;
        for (i in 0 until divisionFactor) {
            for (j in 0 until divisionFactor) {
                val topLeftX = j * quadrantWidth
                val bottomRightX =
                    (j + 1) * quadrantWidth + if (j == divisionFactor - 1) remainderWidth else 0
                val topLeftY = i * quadrantHeight
                val bottomRightY =
                    (i + 1) * quadrantHeight + if (i == divisionFactor - 1) remainderHeight else 0
                FileImageWriter.getInstance()?.saveMatrixToImageReturnPath(
                    fromMat.submat(topLeftY, bottomRightY, topLeftX, bottomRightX),
                    filenames[quadrantCount],
                    ImageFileAttribute.FileType.JPEG
                )?.let { fileList.add(it) }

                quadrantCount += 1;
            }
        }
        fromMat.release()
        return fileList.toTypedArray()
    }

    fun performInterpolationInPlace(
        fromMat: Mat?,
        hrMat: Mat,
        scaling: Int,
        interpolationType: Int
    ): Mat {
        Imgproc.resize(
            fromMat,
            hrMat,
            Size(0.0, 0.0),
            scaling.toDouble(),
            scaling.toDouble(),
            interpolationType
        )

        return hrMat
    }

    fun downsample(fromMat: Mat?, decimation: Float): Mat {
        val downsampleMat = Mat()
        Imgproc.resize(
            fromMat,
            downsampleMat,
            Size(),
            decimation.toDouble(),
            decimation.toDouble(),
            Imgproc.INTER_AREA
        )

        return downsampleMat
    }

    fun replacePatchOnROI(
        sourceMat: Mat,
        boundary: Int,
        sourcePatch: LoadedImagePatch,
        replacementPatch: LoadedImagePatch
    ) {
        if (sourcePatch.getColStart() >= 0 && sourcePatch.getColEnd() < sourceMat.cols() && sourcePatch.getRowStart() >= 0 && sourcePatch.getRowEnd() < sourceMat.rows()) {
            val subMat = sourceMat.submat(
                sourcePatch.getRowStart(),
                sourcePatch.getRowEnd(),
                sourcePatch.getColStart(),
                sourcePatch.getColEnd()
            )
            replacementPatch.getPatchMat().copyTo(subMat)

            //attempt to perform blurring by extracting a parent mat at the borders of the submat
            val rowStart: Int = sourcePatch.getRowStart() - boundary
            val rowEnd: Int = sourcePatch.getRowEnd() + boundary
            val colStart: Int = sourcePatch.getColStart() - boundary
            val colEnd: Int = sourcePatch.getColEnd() + boundary

            if (colStart >= 0 && colEnd < sourceMat.cols() && rowStart >= 0 && rowEnd < sourceMat.rows()) {
                val parentMat = sourceMat.submat(rowStart, rowEnd, colStart, colEnd)
                val blurMat = Mat()
                Imgproc.blur(parentMat, blurMat, Size(3.0, 3.0))
                Core.addWeighted(parentMat, 1.5, blurMat, -0.5, 0.0, parentMat)
                blurMat.release()
            }
        }
    }
}