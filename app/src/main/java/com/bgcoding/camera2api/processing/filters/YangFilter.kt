package com.bgcoding.camera2api.processing.filters


import com.bgcoding.camera2api.processing.multiple.fusion.YangFilterFusionOperator
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.utils.Converters

/**
 * Implements the yang filter edge features on a set of images
 * Created by NeilDG on 7/17/2016.
 */
class YangFilter(private val inputMatList: Array<Mat>)  {
    private val f1Kernel: Mat
    private val f2Kernel: Mat
    private val f3Kernel: Mat
    private val f4Kernel: Mat
    private lateinit var edgeMatList: Array<Mat>

    init {
        val f1 = arrayOf(-1, 0, 1)
        val f2 = arrayOf(-1, 0, 1) // transpose
        val f3 = arrayOf(-1, 0, 2, 0, -1)
        val f4 = arrayOf(-1, 0, 2, 0, -1) // transpose

        f1Kernel = Converters.vector_int_to_Mat(f1.toList())
        f2Kernel = Converters.vector_int_to_Mat(f2.toList())
        Core.transpose(f2Kernel, f2Kernel)

        f3Kernel = Converters.vector_int_to_Mat(f3.toList())
        f4Kernel = Converters.vector_int_to_Mat(f4.toList())
        Core.transpose(f4Kernel, f4Kernel)
    }

    fun perform() {
        edgeMatList = Array(inputMatList.size) { Mat() }

        for (i in inputMatList.indices) {
            val inputf1 = Mat()
            val inputf2 = Mat()
            val inputf3 = Mat()
            val inputf4 = Mat()

            val combinedFilterList = arrayOf(inputf1, inputf2, inputf3, inputf4)

            Imgproc.filter2D(inputMatList[i], inputf1, inputMatList[i].depth(), f1Kernel)
            Imgproc.filter2D(inputMatList[i], inputf2, inputMatList[i].depth(), f2Kernel)
            Imgproc.filter2D(inputMatList[i], inputf3, inputMatList[i].depth(), f3Kernel)
            Imgproc.filter2D(inputMatList[i], inputf4, inputMatList[i].depth(), f4Kernel)

            combinedFilterList[0] = inputf1
            combinedFilterList[1] = inputf2
            combinedFilterList[2] = inputf3
            combinedFilterList[3] = inputf4

            val fusionOperator = YangFilterFusionOperator(combinedFilterList)
            fusionOperator.perform()

            // FileImageWriter.getInstance().saveMatrixToImage(fusionOperator.result, FilenameConstants.EDGE_DIRECTORY_PREFIX, FilenameConstants.IMAGE_EDGE_PREFIX + i, ImageFileAttribute.FileType.JPEG)

            // Release memory for the filter results
            combinedFilterList.forEach { it.release() }
            edgeMatList[i] = fusionOperator.getResult()

            // Test: Highlight the edges
            /* Core.addWeighted(fusionOperator.result, 1.0, inputMatList[i], 1.0, 0.0, inputMatList[i])
            val edgeMat = fusionOperator.result
            val maskMat = ImageOperator.produceMask(edgeMat, 25)
            Core.addWeighted(edgeMat, -0.35, inputMatList[i], 1.0, 0.0, inputMatList[i])
            ImageWriter.getInstance().saveMatrixToImage(inputMatList[i], "YangEdges", "image_sharpen_$i", ImageFileAttribute.FileType.JPEG) */
        }
    }

    fun getEdgeMatList(): Array<Mat> {
        return edgeMatList
    }
}
