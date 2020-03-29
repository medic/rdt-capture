/*
 * Copyright (C) 2019 University of Washington Ubicomp Lab
 * All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of a BSD-style license that can be found in the LICENSE file.
 */

package edu.washington.cs.ubicomplab.rdt_reader.utils;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Base64;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.features2d.Features2d;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import edu.washington.cs.ubicomplab.rdt_reader.core.Constants;
import edu.washington.cs.ubicomplab.rdt_reader.core.RDT;

import static org.opencv.core.Core.LUT;
import static org.opencv.core.Core.addWeighted;
import static org.opencv.core.Core.countNonZero;
import static org.opencv.core.Core.inRange;
import static org.opencv.imgproc.Imgproc.cvtColor;

public final class ImageUtil {
    /**
     * Image constants
     */
    private static String TAG = "ImageUtil";

    public static final int GAUSSIAN_BLUR_WINDOW = 5;

    /**
     *
     * @param image
     * @return
     */

    public static byte[] imageToByteArray(Image image) {
        byte[] data = null;
        if (image.getFormat() == ImageFormat.JPEG) {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            data = new byte[buffer.capacity()];
            buffer.get(data);
            return data;
        } else if (image.getFormat() == ImageFormat.YUV_420_888) {
            data = NV21toJPEG(
                    YUV_420_888toNV21(image),
                    image.getWidth(), image.getHeight());
        }
        return data;
    }

    private static byte[] YUV_420_888toNV21(Image image) {
        byte[] nv21;
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        nv21 = new byte[ySize + uSize + vSize];

        //U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        return nv21;
    }

    private static byte[] NV21toJPEG(byte[] nv21, int width, int height) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
        yuv.compressToJpeg(new Rect(0, 0, width, height), 100, out);
        return out.toByteArray();
    }

    public static Mat imageToRGBMat(Image image) {
        Mat yuvMat = imageToMat(image);
        Mat bgrMat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC4);
        image.close();
        cvtColor(yuvMat, bgrMat, Imgproc.COLOR_YUV2BGR_I420);
        Mat rgbaMat = new Mat();
        cvtColor(bgrMat, rgbaMat, Imgproc.COLOR_BGR2RGBA, 0);

        yuvMat.release();
        bgrMat.release();

        return rgbaMat;
    }

    public static Mat imageToMat(Image image) {
        ByteBuffer buffer;
        int rowStride;
        int pixelStride;
        int width = image.getWidth();
        int height = image.getHeight();
        int offset = 0;

        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[image.getWidth() * image.getHeight() * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];

        for (int i = 0; i < planes.length; i++) {
            buffer = planes[i].getBuffer();
            rowStride = planes[i].getRowStride();
            pixelStride = planes[i].getPixelStride();
            int w = (i == 0) ? width : width / 2;
            int h = (i == 0) ? height : height / 2;
            for (int row = 0; row < h; row++) {
                int bytesPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8;
                if (pixelStride == bytesPerPixel) {
                    int length = w * bytesPerPixel;
                    buffer.get(data, offset, length);

                    if (h - row != 1) {
                        buffer.position(buffer.position() + rowStride - length);
                    }
                    offset += length;
                } else {


                    if (h - row == 1) {
                        buffer.get(rowData, 0, width - pixelStride + 1);
                    } else {
                        buffer.get(rowData, 0, rowStride);
                    }

                    for (int col = 0; col < w; col++) {
                        data[offset++] = rowData[col * pixelStride];
                    }
                }
            }
        }

        Mat mat = new Mat(height + height / 2, width, CvType.CV_8UC1);
        mat.put(0, 0, data);

        return mat;
    }

    public static String matToBase64(Mat mat) {
        if (mat == null) {
            return "";
        }
        Bitmap bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, bitmap);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream .toByteArray();
        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }

    public static byte[] matToRotatedByteArray(Mat captureMat) {
        Bitmap resultBitmap = Bitmap.createBitmap(captureMat.cols(), captureMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(captureMat, resultBitmap);

        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        resultBitmap = Bitmap.createBitmap(resultBitmap, 0, 0, resultBitmap.getWidth(), resultBitmap.getHeight(), matrix, true);

        ByteArrayOutputStream bs = new ByteArrayOutputStream();
        resultBitmap.compress(Bitmap.CompressFormat.JPEG, 100, bs);

        return bs.toByteArray();
    }

    private void drawKeypointsAndMatches(Mat inputMat, MatOfPoint boundary, MatOfKeyPoint inKeypoints, MatOfDMatch goodMatchesMat, RDT rdt) {
        Mat resultMat = new Mat();
        MatOfPoint boundaryMat = new MatOfPoint();
        boundaryMat.fromList(boundary.toList());
        ArrayList<MatOfPoint> list = new ArrayList<>();
        list.add(boundaryMat);

        Mat tempInputMat = inputMat.clone();
        Imgproc.polylines(tempInputMat, list, true, Scalar.all(0),10);
        Features2d.drawKeypoints(tempInputMat, inKeypoints, tempInputMat);
        Features2d.drawMatches(rdt.refImg, rdt.refKeypoints, tempInputMat, inKeypoints, goodMatchesMat, resultMat);
        Bitmap bitmap = Bitmap.createBitmap(inputMat.cols(), inputMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(resultMat, bitmap);
    }

    private void calcuateAverageMat(Mat inputMat){
        float[] avgIntensities = new float[inputMat.cols()];
        float[] avgHues = new float[inputMat.cols()];
        float[] avgSats = new float[inputMat.cols()];

        for (int i = 0; i < inputMat.cols(); i++) {
            float sumIntensity=0;
            float sumHue=0;
            float sumSat=0;
            for (int j = 0; j < inputMat.rows(); j++) {
                sumHue+=inputMat.get(j, i)[0];
                sumIntensity+=inputMat.get(j, i)[1];
                sumSat+=inputMat.get(j, i)[2];
            }
            avgIntensities[i] = sumIntensity/inputMat.rows();
            avgHues[i] = sumHue/inputMat.rows();
            avgSats[i] = sumSat/inputMat.rows();

            Log.d(TAG, String.format("HLS at %d (%.2f, %.2f, %.2f) type %d", i,  avgHues[i]*2, avgIntensities[i]/255*100, avgSats[i]/255*100, inputMat.type()));
        }
    }

    private Mat correctGamma(Mat enhancedImg, double gamma) {
        Mat lutMat = new Mat(1, 256, CvType.CV_8UC1);
        for (int i = 0; i < 256; i ++) {
            double g = Math.pow((double)i/255.0, gamma)*255.0;
            g = g > 255.0 ? 255.0 : g < 0 ? 0 : g;
            lutMat.put(0, i, g);
        }
        Mat result = new Mat();
        LUT(enhancedImg, lutMat, result);
        return result;
    }

    public static double rgbToY(double[] rgb) {
        return 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2] + 20.0;
    }
}
