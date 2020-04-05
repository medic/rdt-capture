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
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
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

/**
 * A class used to hold generic image processing functions that are not
 * necessarily specific to RDTs
 */
public final class ImageUtil {
    private static String TAG = "ImageUtil";

    /**
     * Convert Android's Image class to an OpenCV Mat
     * @param image: the input Image
     * @return the same image as an OpenCV Mat
     */
    public static Mat imageToRGBMat(Image image) {
        ByteBuffer buffer;
        int rowStride;
        int pixelStride;
        int width = image.getWidth();
        int height = image.getHeight();
        int offset = 0;

        // Initialize data structure for output
        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[image.getWidth() * image.getHeight() * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];

        // Extract data as YUV
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

                    if (h-row != 1)
                        buffer.position(buffer.position() + rowStride - length);
                    offset += length;
                } else {
                    if (h - row == 1)
                        buffer.get(rowData, 0, width - pixelStride + 1);
                    else
                        buffer.get(rowData, 0, rowStride);

                    for (int col=0; col<w; col++)
                        data[offset++] = rowData[col * pixelStride];
                }
            }
        }

        // Put data in a Mat object
        Mat yuvMat = new Mat(height + height/2, width, CvType.CV_8UC1);
        yuvMat.put(0, 0, data);

        // Convert from YUV to RGBA
        Mat bgrMat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC4);
        image.close();
        cvtColor(yuvMat, bgrMat, Imgproc.COLOR_YUV2BGR_I420);
        Mat rgbaMat = new Mat();
        cvtColor(bgrMat, rgbaMat, Imgproc.COLOR_BGR2RGBA, 0);

        // Garbage collection
        yuvMat.release();
        bgrMat.release();

        return rgbaMat;
    }

    /**
     * Converts a Mat to a byte array for saving
     * @param inputMat: the input iamge as a Mat
     * @return a corresponding byte array
     */
    public static byte[] matToByteArray(Mat inputMat) {
        // Convert from Mat to Bitmap
        Bitmap resultBitmap = Bitmap.createBitmap(inputMat.cols(), inputMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(inputMat, resultBitmap);

        // Rotate by 90°
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        resultBitmap = Bitmap.createBitmap(resultBitmap, 0, 0, resultBitmap.getWidth(), resultBitmap.getHeight(), matrix, true);

        // Compress into byte array using JPEG
        ByteArrayOutputStream bs = new ByteArrayOutputStream();
        resultBitmap.compress(Bitmap.CompressFormat.JPEG, 100, bs);
        return bs.toByteArray();
    }

    /**
     * Extract the brightness from an RGB color
     * @param rgb: the RGB color
     * @return the brightness (Y in YUV)
     */
    public static double rgbToY(double[] rgb) {
        return 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2] + 20.0;
    }

    /**
     * Crops the input image around the edges to reduce data size (for computation and upload)
     * @param inputMat: the candidate video frame
     * @param cropRatio: the amount by which the image should be cropped
     *                 (as a fraction of each dimension)
     * @return the cropped image
     */
    public static Mat cropInputMat(Mat inputMat, double cropRatio) {
        int x = (int) (inputMat.cols() * (1.0-cropRatio)/2);
        int y = (int) (inputMat.rows() * (1.0-cropRatio)/2);
        int width = (int) (inputMat.cols() * cropRatio);
        int height = (int) (inputMat.rows() * cropRatio);
        org.opencv.core.Rect roi = new org.opencv.core.Rect(x, y, width, height);

        return new Mat(inputMat, roi);
    }

    /**
     * Adjusts the boundary so the coordinates align with image cropping
     * @param inputMat: the candidate video frame
     * @param boundary: the corners of the bounding box around the detected RDT
     * @param cropRatio: the amount by which the image should be cropped
     *                 (as a fraction of each dimension)
     * @return the adjusted boundary coordinates
     */
    public static MatOfPoint2f adjustBoundary(Mat inputMat, MatOfPoint2f boundary, double cropRatio) {
        // Compute the offset
        int x = (int) (inputMat.cols() * (1.0-cropRatio)/2);
        int y = (int) (inputMat.rows() * (1.0-cropRatio)/2);

        // Apply the offset
        Point[] boundaryPts = boundary.toArray();
        for (Point p: boundaryPts) {
            p.x -= x;
            p.y -= y;
        }

        // Return the new boundary
        return new MatOfPoint2f(boundaryPts);
    }

    /**
     * Identifies the peaks/troughs within a vector of values
     * Adapted from: https://gist.github.com/endolith/250860
     * Note: This assumes alternating peaks and troughs, which is fine for this application,
     * but may not be for other applications that require peak detection
     * @param arr: the array of values
     * @param delta: the minimum peak/trough height
     * @param max: whether a peak (max) or trough (min) is being tracked
     * @return a List of [peak_idx, peak_value, peak_width] for all detected peaks/troughs
     */
    public static ArrayList<double[]> detectPeaks(double[] arr, double delta, boolean max) {
        ArrayList<double[]> peaks = new ArrayList<>();
        ArrayList<double[]> troughs = new ArrayList<>();

        // Initialize peak tracking variables
        double min_val = arr[0];
        double max_val = arr[0];
        int min_idx = Integer.MIN_VALUE;
        int max_idx = Integer.MIN_VALUE;
        boolean lookingForMax = true;

        // Start looking for peaks/troughs
        for (int i=0; i<arr.length; i++) {
            double curr = arr[i];
            // Update the min/max values and locations
            if (curr > max_val) {
                max_val = curr;
                max_idx = i;
            }
            if (curr < min_val) {
                min_val = curr;
                min_idx = i;
            }

            // Determine if local optima has been found
            if (lookingForMax) {
                // Peak finding
                if (curr < max_val-delta) {
                    if (max_idx != Integer.MIN_VALUE)
                        peaks.add(new double[]{max_idx, max_val, measurePeakWidth(arr, max_idx, true)});
                    min_val = curr;
                    min_idx = i;
                    lookingForMax = false;
                }
            } else {
                // Trough finding
                if (curr > min_val+delta) {
                    if (min_idx != Integer.MIN_VALUE)
                        troughs.add(new double[]{min_idx, min_val, measurePeakWidth(arr, min_idx, false)});
                    max_val = curr;
                    max_idx = i;
                    lookingForMax = true;
                }
            }
        }

        // Return peaks or valleys
        return (max ? peaks : troughs);
    }

    /**
     * Measures the width of a detected peak/trough at the given location
     * @param arr: the array of values
     * @param idx: the index of the detected peak/trough
     * @param max: whether a peak (max) or trough (min) is being tracked
     * @return the width of the peak in pixels
     */
    private static double measurePeakWidth(double[] arr, int idx, boolean max) {
        double width = 0;
        int i;
        if (max) {
            // Measure the peak to the left side of the array
            i = idx - 1;
            while (i > 0 && arr[i] > arr[i - 1]) {
                width += 1;
                i -= 1;
            }

            // Measure the peak to the right side of the array
            i = idx;
            while (i < arr.length - 1 && arr[i] > arr[i + 1]) {
                width += 1;
                i += 1;
            }
        } else {
            // Measure the valley to the left side of the array
            i = idx - 1;
            while (i > 0 && arr[i] < arr[i - 1]) {
                width += 1;
                i -= 1;
            }

            // Measure the valley to the right side of the array
            i = idx;
            while (i < arr.length - 1 && arr[i] < arr[i + 1]) {
                width += 1;
                i += 1;
            }
        }
        return width;
    }

    /**
     * (For debug purposes) Saves image to local directory.
     * @param inputMat: the candidate video frame
     */
    public static void saveImage (Mat inputMat) {
        File sdIconStorageDir = new File(Constants.RDT_IMAGE_DIR);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss-SSS");

        try {
            String filePath = sdIconStorageDir.toString() + String.format("/%s-%08dms.jpg", sdf.format(new Date()), 0);
            FileOutputStream fileOutputStream = new FileOutputStream(filePath);

            fileOutputStream.write(matToRotatedByteArray(inputMat));

            fileOutputStream.flush();
            fileOutputStream.close();

            //sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + filePath)));

            //Toast.makeText(this,"Image is successfully saved!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.w("TAG", "Error saving image file: " + e.getMessage());
        }
    }

    /**
     * (For debug purposes) Converts Mat object and rotate 90 degrees clockwise.
     * @param inputMat: the candidate video frame
     */
    public static byte[] matToRotatedByteArray(Mat inputMat) {
        Bitmap resultBitmap = Bitmap.createBitmap(inputMat.cols(), inputMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(inputMat, resultBitmap);

        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        resultBitmap = Bitmap.createBitmap(resultBitmap, 0, 0, resultBitmap.getWidth(), resultBitmap.getHeight(), matrix, true);

        ByteArrayOutputStream bs = new ByteArrayOutputStream();
        resultBitmap.compress(Bitmap.CompressFormat.JPEG, 100, bs);

        return bs.toByteArray();
    }

    /**
     * (For debug purposes) Draws an image for debugging the feature-based template matching approach for RDT detection
     * @param inputMat: the candidate video frame (in grayscale)
     * @param boundary: the corners of the bounding box around the detected RDT
     * @param keypoints: the SIFT keypoints within the video frame
     * @param matchesMat: the correspondence between the different keypoints
     * @return an image with keypoint matches drawn between the reference image
     * and the candidate video frame
     */
    private Mat drawKeypointsAndMatches(Mat inputMat, MatOfPoint boundary,
                                        MatOfKeyPoint keypoints, MatOfDMatch matchesMat, Mat refImg, MatOfKeyPoint refKeypoints) {
        Mat debugMat = new Mat();
        MatOfPoint boundaryMat = new MatOfPoint();
        boundaryMat.fromList(boundary.toList());
        ArrayList<MatOfPoint> list = new ArrayList<>();
        list.add(boundaryMat);

        Mat tempInputMat = inputMat.clone();
        Imgproc.polylines(tempInputMat, list, true, Scalar.all(0),10);
        Features2d.drawKeypoints(tempInputMat, keypoints, tempInputMat);
        Features2d.drawMatches(refImg, refKeypoints, tempInputMat, keypoints,
                matchesMat, debugMat);
        Bitmap bitmap = Bitmap.createBitmap(inputMat.cols(), inputMat.rows(),
                Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(debugMat, bitmap);
        return debugMat;
    }

    /**
     * (For debug purposes) Calculates average values
     * @param inputMat the candidate video frame (in HLS)
     */
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

    /**
     * (For debug purposes) Corrects gamma for input frame
     * @param inputMat the candidate video frame
     * @param gamma gamma value
     * @return corrected frame in Mat object
     */
    private Mat correctGamma(Mat inputMat, double gamma) {
        Mat lutMat = new Mat(1, 256, CvType.CV_8UC1);
        for (int i = 0; i < 256; i ++) {
            double g = Math.pow((double)i/255.0, gamma)*255.0;
            g = g > 255.0 ? 255.0 : g < 0 ? 0 : g;
            lutMat.put(0, i, g);
        }
        Mat result = new Mat();
        LUT(inputMat, lutMat, result);
        return result;
    }
}
