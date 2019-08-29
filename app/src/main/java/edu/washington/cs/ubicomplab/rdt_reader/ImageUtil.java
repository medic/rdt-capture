/*
 * Copyright (C) 2019 University of Washington Ubicomp Lab
 * All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of a BSD-style license that can be found in the LICENSE file.
 */

package edu.washington.cs.ubicomplab.rdt_reader;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Base64;
import java.util.List;
import java.util.ArrayList;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public final class ImageUtil {

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

    private static double findPeakWidth(double idx, Mat mat, double val, boolean findMax) {
        double width = 0;

        if (findMax) {
//            Find the furthest minimum left
            int i = (int) idx - 1;
            while (i > 0 && mat.get(0, i)[0] > mat.get(0, i - 1)[0]) {
                width++;
                i--;
            }
            i = (int) idx;
            while (i < mat.cols() && mat.get(0, i)[0] > mat.get(0, i + 1)[0]) {
                width++;
                i++;
            }
        } else {
            int i = (int) idx;
            while (i > 0 && mat.get(0, i)[0] < mat.get(0, i - 1)[0]) {
                width++;
                i--;
            }
            i = (int) idx;
            while (i < mat.cols() && mat.get(0, i)[0] < mat.get(0, i + 1)[0]) {
                width++;
                i++;
            }
        }

        return width;
    }

    public static List<List<Double>> peakDetection(Mat mat, double delta) {
        List<List<Double>> maxtab = new ArrayList<>();
        List<List<Double>> mintab = new ArrayList<>();
        if (mat == null) {
            return null;
        }

        double min = mat.get(0, 0)[0], max = mat.get(0, 0)[0];
        double mnpos = -1, mxpos = -1;
        boolean lookForMax = true;
        int maxWidth = 0;
        int minWidth = 0;

        for (int i = 1; i < mat.cols(); i++) {
            double curr = mat.get(0, i)[0];
            if (curr > max) {
                max = curr;
                mxpos = mat.get(0, i)[0];
                maxWidth++;
            }
            if (curr < min) {
                min = curr;
                mnpos = mat.get(0, i)[0];
                minWidth++;
            }

            if (lookForMax) {
                if (curr < max - delta) {
                    if (mxpos != -1) {
                        List<Double> res = new ArrayList<>();
                        res.add(mxpos);
                        res.add(max);
                        res.add(findPeakWidth(mxpos, mat, max, true));
                        maxtab.add(res);
                    }
                    min = curr;
                    maxWidth = 0;
                    mnpos = mat.get(0, i)[0];
                    lookForMax = false;
                }
            } else {
                if (curr > min + delta) {
                    if (mnpos != -1) {
                        List<Double> res = new ArrayList<>();
                        res.add(mnpos);
                        res.add(min);
                        res.add(findPeakWidth(mnpos, mat, min, false));
                        mintab.add(res);
                    }
                    max = curr;
                    minWidth = 0;
                    mxpos = mat.get(0, i)[0];
                    lookForMax = true;
                }
            }
        }
        return maxtab;
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
        Imgproc.cvtColor(yuvMat, bgrMat, Imgproc.COLOR_YUV2BGR_I420);
        Mat rgbaMat = new Mat();
        Imgproc.cvtColor(bgrMat, rgbaMat, Imgproc.COLOR_BGR2RGBA, 0);

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
}
