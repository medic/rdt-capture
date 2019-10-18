/*
 * Copyright (C) 2019 University of Washington Ubicomp Lab
 * All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of a BSD-style license that can be found in the LICENSE file.
 */

package edu.washington.cs.ubicomplab.rdt_reader;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.core.TermCriteria;
import org.opencv.features2d.Features2d;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Scalar;


import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static edu.washington.cs.ubicomplab.rdt_reader.Constants.*;
import static java.lang.Math.pow;
import static java.lang.StrictMath.abs;
import static org.opencv.core.Core.KMEANS_PP_CENTERS;
import static org.opencv.core.Core.LUT;
import static org.opencv.core.Core.kmeans;
import static org.opencv.core.Core.meanStdDev;
import static org.opencv.core.Core.perspectiveTransform;
import static org.opencv.core.CvType.CV_32F;
import static org.opencv.core.CvType.CV_8U;
import static org.opencv.core.CvType.CV_8UC3;
import static org.opencv.imgproc.Imgproc.COLOR_RGB2GRAY;
import static org.opencv.imgproc.Imgproc.COLOR_RGBA2RGB;
import static org.opencv.imgproc.Imgproc.Laplacian;
import static org.opencv.imgproc.Imgproc.THRESH_BINARY_INV;
import static org.opencv.imgproc.Imgproc.contourArea;
import static org.opencv.imgproc.Imgproc.createCLAHE;
import static org.opencv.imgproc.Imgproc.cvtColor;
import static org.opencv.imgproc.Imgproc.getPerspectiveTransform;
import static org.opencv.imgproc.Imgproc.minAreaRect;
import static org.opencv.imgproc.Imgproc.resize;
import static org.opencv.imgproc.Imgproc.warpPerspective;


public class ImageProcessor {
    private static String TAG = "ImageProcessor";
    private static ImageProcessor instance = null;
    static RDT mRDT;

    private int mMoveCloserCount = 0;


    public enum ExposureResult {
        UNDER_EXPOSED, NORMAL, OVER_EXPOSED
    }

    public enum SizeResult{
        RIGHT_SIZE, LARGE, SMALL, INVALID

    }

    public static class CaptureResult {
        public boolean allChecksPassed;
        public Mat resultMat;
        public MatOfPoint2f boundary;
        public ExposureResult exposureResult;
        public SizeResult sizeResult;
        public boolean isCentered;
        public boolean isRightOrientation;
        public boolean isSharp;
        public boolean isShadow;
        public boolean fiducial;
        public double angle;
        public boolean flashEnabled;

        public CaptureResult(boolean allChecksPassed, Mat resultMat, boolean fiducial,
                             ExposureResult exposureResult, SizeResult sizeResult,  boolean isCentered,
                             boolean isRightOrientation, double angle, boolean isSharp, boolean isShadow, MatOfPoint2f boundary, boolean flashEnabled){
            this.allChecksPassed = allChecksPassed;
            this.resultMat = resultMat;
            this.fiducial = fiducial;
            this.exposureResult = exposureResult;
            this.sizeResult = sizeResult;
            this.isCentered = isCentered;
            this.isRightOrientation = isRightOrientation;
            this.isSharp = isSharp;
            this.isShadow = isShadow;
            this.angle = angle;
            this.boundary = boundary;
            this.flashEnabled = flashEnabled;
        }

        public CaptureResult(Mat resultMat) {
            this.resultMat = resultMat;
        }
    }

    public static class InterpretationResult {
        public boolean topLine;
        public boolean middleLine;
        public boolean bottomLine;
        public String topLineName;
        public String middleLineName;
        public String bottomLineName;
        public Mat resultMat;
        public Bitmap resultBitmap;

        public InterpretationResult() {
            topLine = false;
            middleLine = false;
            bottomLine = false;
            topLineName = "Top Line";
            middleLineName = "Middle Line";
            bottomLineName = "Bottom Line";
            resultMat = new Mat();
            resultBitmap = null;
        }

        public InterpretationResult(Mat resultMat, boolean topLine, boolean middleLine, boolean bottomLine) {
            this.resultMat = resultMat;
            this.topLine = topLine;
            this.middleLine = middleLine;
            this.bottomLine = bottomLine;
            this.topLineName = mRDT.topLineName;
            this.middleLineName = mRDT.middleLineName;
            this.bottomLineName = mRDT.bottomLineName;
            if (resultMat.cols() > 0 && resultMat.rows() > 0) {
                this.resultBitmap = Bitmap.createBitmap(resultMat.cols(), resultMat.rows(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(resultMat, resultBitmap);
            }
        }
    }


    public ImageProcessor (Activity activity, String rdtName) {
        long startTime = System.currentTimeMillis();
        mRDT = new RDT(activity.getApplicationContext(), rdtName);

        mRDT.refImgSharpness = calculateSharpness(mRDT.refImg);
        Log.d(TAG, String.format("mRefImg sharpness: %.2f",  mRDT.refImgSharpness));

        Log.d(TAG, "RefImg Size: " + mRDT.refImg.size().toString());
        Log.d(TAG, "SIFT keypoints: " + mRDT.refKeypoints.toArray().length);
        Log.d(TAG, "REFERENCE LOAD/DETECT/COMPUTE: " + (System.currentTimeMillis() - startTime));
    }

    public static ImageProcessor getInstance(Activity activity, String rdtName) {
        if (instance == null)
            instance = new ImageProcessor(activity, rdtName);

        return instance;
    }

    public static void destroy() {
        instance = null;
    }

    public static void loadOpenCV(Context context, BaseLoaderCallback mLoaderCallback) {
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, context, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }

    }

    public void configureCamera() {

    }


    public CaptureResult captureRDT(Mat inputMat, boolean flashEnabled) {
        Mat greyMat = new Mat();
        cvtColor(inputMat, greyMat, Imgproc.COLOR_RGBA2GRAY);
        double matchDistance = -1.0;
        boolean passed = false;

        //check brightness (refactored)
        ExposureResult exposureResult = (checkBrightness(greyMat));

        //check sharpness (refactored)
        boolean isSharp = checkSharpness(greyMat.submat(getViewfinderRect(greyMat)));

        //preform detectRDT only if those two quality checks are passed
        if (exposureResult == ExposureResult.NORMAL && isSharp) {

            MatOfPoint2f boundary = new MatOfPoint2f();
            boundary = detectRDTWithSIFT(greyMat, 5);
            //boundary = detectRDT(greyMat);
            boolean isCentered = false;
            SizeResult sizeResult = SizeResult.INVALID;
            boolean isRightOrientation = false;
            double angle = 0.0;


            //Size size = new Size();
            if (boundary.size().width > 0 && boundary.size().height > 0) {
                isCentered = checkIfCentered(boundary, greyMat.size());
                sizeResult = checkSize(boundary, greyMat.size());
                isRightOrientation = checkOrientation(boundary);
                angle = measureOrientation(boundary);
            }

            passed = sizeResult == SizeResult.RIGHT_SIZE && isCentered && isRightOrientation;

            boolean fiducial = false;
            if (passed) {
                Mat resultMat = cropResultWindow(inputMat, boundary);
                if (resultMat.width() > 0 && resultMat.height() > 0) {
                    fiducial = true;
                }
                resultMat.release();
                passed = passed & fiducial;
                Log.d(TAG, String.format("fiducial: %b", fiducial));
            }

            greyMat.release();

            // Apply crops if necessary
            Mat croppedMat = cropRDTMat(inputMat);
            MatOfPoint2f croppedBoundary = cropRDTBoundary(inputMat, boundary);

            return new CaptureResult(passed, croppedMat, fiducial, exposureResult, sizeResult, isCentered, isRightOrientation, angle, isSharp, false, croppedBoundary, flashEnabled);
        }
        else {
            greyMat.release();
            return new CaptureResult(passed, null, false, exposureResult, SizeResult.INVALID, false, false, 0.0, isSharp, false, new MatOfPoint2f(), flashEnabled);
        }

    }

    private boolean checkSharpness(Mat inputMat) {
        Mat resized = new Mat();
        resize(inputMat, resized, new Size(inputMat.size().width*mRDT.refImg.size().width/inputMat.size().width, inputMat.size().height*mRDT.refImg.size().width/inputMat.size().width));

        double sharpness = calculateSharpness(resized);
        //Log.d(TAG, String.format("inputMat sharpness: %.2f, %.2f",calculateSharpness(resized), calculateSharpness(inputMat)));
        Log.d(TAG, String.format("inputMat sharpness: %.2f",calculateSharpness(resized)));

        boolean isSharp = sharpness > (mRDT.refImgSharpness * (1-SHARPNESS_THRESHOLD));
        Log.d(TAG, "Sharpness: "+sharpness);

        inputMat.release();
        resized.release();

        return isSharp;
    }

    private double calculateSharpness(Mat input) {
        Mat des = new Mat();
        Laplacian(input, des, CvType.CV_64F);

        MatOfDouble median = new MatOfDouble();
        MatOfDouble std = new MatOfDouble();

        meanStdDev(des, median, std);


        double sharpness = pow(std.get(0,0)[0],2);
        des.release();
        return sharpness;
    }

    private ExposureResult checkBrightness(Mat inputMat) {

        // Brightness Calculation
        float[] histograms = calculateBrightness(inputMat);

        int maxWhite = 0;
        float whiteCount = 0;

        for (int i = 0; i < histograms.length; i++) {
            if (histograms[i] > 0) {
                maxWhite = i;
            }
            if (i == histograms.length - 1) {
                whiteCount = histograms[i];
            }
        }

        // Check Brightness starts
        ExposureResult exposureResult;
        if (maxWhite >= OVER_EXP_THRESHOLD && whiteCount > OVER_EXP_WHITE_COUNT) {
            exposureResult = ExposureResult.OVER_EXPOSED;
            return exposureResult;
        } else if (maxWhite < UNDER_EXP_THRESHOLD) {
            exposureResult = ExposureResult.UNDER_EXPOSED;
            return exposureResult;
        } else {
            exposureResult = ExposureResult.NORMAL;
            return exposureResult;
        }
    }

    private float[] calculateBrightness(Mat input) {
        int mHistSizeNum =256;
        MatOfInt mHistSize = new MatOfInt(mHistSizeNum);
        Mat hist = new Mat();
        final float []mBuff = new float[mHistSizeNum];
        MatOfFloat histogramRanges = new MatOfFloat(0f, 256f);
        MatOfInt mChannels[] = new MatOfInt[] { new MatOfInt(0)};
        org.opencv.core.Size sizeRgba = input.size();

        // GRAY
        for(int c=0; c<1; c++) {
            Imgproc.calcHist(Arrays.asList(input), mChannels[c], new Mat(), hist,
                    mHistSize, histogramRanges);
            Core.normalize(hist, hist, sizeRgba.height/2, 0, Core.NORM_INF);
            hist.get(0, 0, mBuff);
            mChannels[c].release();
        }

        mHistSize.release();
        histogramRanges.release();
        hist.release();
        return mBuff;
    }

    private double measureSize(MatOfPoint2f boundary) {
        RotatedRect rotatedRect = minAreaRect(boundary);

        boolean isUpright = rotatedRect.size.height > rotatedRect.size.width;
        double angle = 0;
        double height = 0;

        if (isUpright) {
            angle = 90 - abs(rotatedRect.angle);
            height = rotatedRect.size.height;
        } else {
            angle = abs(rotatedRect.angle);
            height = rotatedRect.size.width;
        }

        return height;
    }

    private SizeResult checkSize(MatOfPoint2f boundary, Size size) {
        double height = measureSize(boundary);
        boolean isRightSize = height < size.width*mRDT.viewFinderScaleH+size.width*SIZE_THRESHOLD && height > size.width*mRDT.viewFinderScaleH-size.width*SIZE_THRESHOLD;

        SizeResult sizeResult = SizeResult.INVALID;

        if (isRightSize) {
            sizeResult = SizeResult.RIGHT_SIZE;
        } else {
            if (height > size.width*mRDT.viewFinderScaleH+size.width*SIZE_THRESHOLD) {
                sizeResult = SizeResult.LARGE;
            } else if (height < size.width*mRDT.viewFinderScaleH-size.width*SIZE_THRESHOLD) {
                sizeResult = SizeResult.SMALL;
            } else {
                sizeResult = SizeResult.INVALID;
            }
        }

        return sizeResult;

    }

    private boolean checkIfCentered(MatOfPoint2f boundary, Size size) {

        Point center = measureCentering(boundary);
        Point trueCenter = new Point(size.width/2, size.height/2);
        boolean isCentered = center.x < trueCenter.x + (size.width*POSITION_THRESHOLD) && center.x > trueCenter.x-(size.width*POSITION_THRESHOLD)
                && center.y < trueCenter.y+(size.height*POSITION_THRESHOLD) && center.y > trueCenter.y-(size.height*POSITION_THRESHOLD);

        return isCentered;

    }

     private Point measureCentering (MatOfPoint2f boundary) {
        RotatedRect rotatedRect = minAreaRect(boundary);
        return rotatedRect.center;
    }

     private double measureOrientation(MatOfPoint2f boundary) {
        RotatedRect rotatedRect = minAreaRect(boundary);

         boolean isUpright = rotatedRect.size.height > rotatedRect.size.width;
         double angle = 0;
         double height = 0;

         if (isUpright) {
             if (rotatedRect.angle < 0) {
                 angle = 90 + rotatedRect.angle;
             } else {
                 angle = rotatedRect.angle - 90;
             }
         } else {
             angle = rotatedRect.angle;
         }

        return angle;
    }

    private boolean checkOrientation(MatOfPoint2f boundary) {
        double angle = measureOrientation(boundary);

        boolean isOriented = abs(angle) < ANGLE_THRESHOLD;

        return isOriented;
    }

    /**
     * Crops the input image
     * @param inputMat the input image
     * @return the adjusted image
     */
    private Mat cropRDTMat(Mat inputMat) {
        // Compute the crop ROI
        int width = (int)(inputMat.cols() * CROP_RATIO);
        int height = (int)(inputMat.rows() * CROP_RATIO);
        int x = (int)(inputMat.cols() * (1.0-CROP_RATIO)/2);
        int y = (int)(inputMat.rows() * (1.0-CROP_RATIO)/2);
        Rect roi = new Rect(x, y, width, height);

        // Crop the image
        return new Mat(inputMat, roi);
    }

    /**
     * Adjusts the bounding box coordinates according to how the input matrix should be cropped
     * @param inputMat the input image
     * @param boundary the bounding box
     * @return the adjusted bounding box
     */
    private MatOfPoint2f cropRDTBoundary(Mat inputMat, MatOfPoint2f boundary) {
        // Compute the offset
        int x = (int)(inputMat.cols() * (1.0-CROP_RATIO)/2);
        int y = (int)(inputMat.rows() * (1.0-CROP_RATIO)/2);

        // Apply the offset
        Point[] boundaryPts = boundary.toArray();
        for (Point p: boundaryPts) {
            p.x -= x;
            p.y -= y;
        }

        // Return the new boundary
        return new MatOfPoint2f(boundaryPts);
    }

    public int getInstructionText(SizeResult sizeResult, boolean isCentered, boolean isRightOrientation) {
        int instructions = R.string.instruction_pos;

        if (sizeResult == SizeResult.RIGHT_SIZE && isCentered && isRightOrientation){
            instructions = R.string.instruction_detected;
        } else if (mMoveCloserCount > MOVE_CLOSER_COUNT) {
            if (sizeResult != SizeResult.INVALID && sizeResult == SizeResult.SMALL) {
                 instructions = R.string.instruction_too_small;
                 mMoveCloserCount = 0;
            }
        } else {
            instructions = R.string.instruction_too_small;
            mMoveCloserCount++;
        }

        return instructions;


    }

    public String[] getQualityCheckText(SizeResult sizeResult, boolean isCentered, boolean isRightOrientation, boolean isSharp, ExposureResult exposureResult) {

        String[] texts = new String[4];

        texts[0] = isSharp ? "&#x2713; Sharpness: passed": "Sharpness: failed";
        if (exposureResult == ExposureResult.NORMAL) {
            texts[1] = "&#x2713; Brightness: passed";
        } else if (exposureResult == ExposureResult.OVER_EXPOSED) {
            texts[1] = "Brightness: too bright";
        } else if (exposureResult == ExposureResult.UNDER_EXPOSED) {
            texts[1] = "Brightness: too dark";
        }

        texts[2] = sizeResult == SizeResult.RIGHT_SIZE && isCentered && isRightOrientation ? "&#x2713; Position/Size: passed": "Position/Size: failed";
        texts[3] = "&#x2713; Shadow: passed";

        return texts;

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

    private Mat enhanceImage(Mat resultImg, org.opencv.core.Size tile) {
        Mat result = new Mat();
        cvtColor(resultImg, result, Imgproc.COLOR_RGBA2RGB);
        cvtColor(result, result, Imgproc.COLOR_RGB2HLS);

        CLAHE clahe = createCLAHE(10, tile);

        ArrayList<Mat> channels = new ArrayList<>();
        Core.split(result, channels);

        Mat newChannel = new Mat();

        Core.normalize(channels.get(1), channels.get(1), 0, 255, Core.NORM_MINMAX);

        clahe.apply(channels.get(1), newChannel);

        channels.set(1, newChannel);

        Core.merge(channels, result);

        cvtColor(result, result, Imgproc.COLOR_HLS2RGB);
        cvtColor(result, result, Imgproc.COLOR_RGB2RGBA);

        return result;
    }

    public InterpretationResult interpretResult(Bitmap img) {
        Mat resultMat = new Mat();
        Utils.bitmapToMat(img, resultMat);
        return interpretResult(resultMat);
    }

    public InterpretationResult interpretResult(Mat inputMat, MatOfPoint2f boundary) {
        Mat resultMat = cropResultWindow(inputMat, boundary);
        boolean topLine, middleLine, bottomLine;

        if (resultMat.width() == 0 && resultMat.height() == 0) {
            return new InterpretationResult(resultMat, false, false, false);
        }

        Mat grayMat = new Mat();
        cvtColor(resultMat, grayMat, COLOR_RGB2GRAY);
        MatOfDouble mu = new MatOfDouble();
        MatOfDouble sigma = new MatOfDouble();
        Core.meanStdDev(grayMat, mu, sigma);
        Core.MinMaxLocResult minMaxLocResult = Core.minMaxLoc(grayMat);

        Log.d(TAG, String.format("stdev %.2f, minval %.2f at %s, maxval %.2f at %s",
                sigma.get(0,0)[0],
                minMaxLocResult.minVal, minMaxLocResult.minLoc,
                minMaxLocResult.maxVal, minMaxLocResult.maxLoc));

        if (sigma.get(0,0)[0] > ENHANCING_THRESHOLD)
            resultMat = enhanceResultWindow(resultMat, new Size(5, resultMat.cols()));

        //resultMat = enhanceResultWindow(resultMat, new Size(10, 10));
        //resultMat = correctGamma(resultMat, 0.75);

        topLine = readControlLine(resultMat, new Point(mRDT.topLinePosition, 0));
        middleLine = readTestLine(resultMat, new Point(mRDT.middleLinePosition, 0));
        bottomLine = readTestLine(resultMat, new Point(mRDT.bottomLinePosition, 0));

        Log.d(TAG, String.format("Interpretation results: %s %s %s", topLine, middleLine, bottomLine));

        return new InterpretationResult(resultMat, topLine, middleLine, bottomLine);
    }

    public InterpretationResult interpretResult(Mat inputMat) {
        MatOfPoint2f boundary = new MatOfPoint2f();
        Mat grayMat = new Mat();
        cvtColor(inputMat, grayMat, Imgproc.COLOR_RGBA2GRAY);

        int cnt = 3;
        SizeResult isSizeable = SizeResult.INVALID;
        boolean isCentered = false;
        boolean isUpright = false;

        do {
            cnt++;
            boundary = detectRDTWithSIFT(grayMat, cnt);
            Log.d(TAG, "SIFT boundary size: " + boundary.size());
            isSizeable = checkSize(boundary, new Size(inputMat.size().width/CROP_RATIO, inputMat.size().height/CROP_RATIO));
            isCentered = checkIfCentered(boundary, inputMat.size());
            isUpright = checkOrientation(boundary);
            Log.d(TAG, String.format("SIFT-right size %s, center %s, orientation %s, (%.2f, %.2f), cnt %d", isSizeable, isCentered, isUpright, inputMat.size().width, inputMat.size().height, cnt));
        } while(!(isSizeable==SizeResult.RIGHT_SIZE && isCentered && isUpright) && cnt < 8);

        if (boundary.size().width <= 0 && boundary.size().height <= 0)
            return new InterpretationResult();

        return interpretResult(inputMat, boundary);
    }

    private Rect checkFiducialKMenas(Mat inputMat) {
        if (mRDT.fiducialCount == 0) {
            Point tl = new Point(mRDT.fiducialToResultWindowOffset - mRDT.resultWindowRectH / 2.0, mRDT.resultWindowRectWPadding);
            Point br = new Point(mRDT.fiducialToResultWindowOffset + mRDT.resultWindowRectH / 2.0, inputMat.size().height - mRDT.resultWindowRectWPadding);

            Rect fiducialRect = new Rect(tl, br);

            return fiducialRect;
        } else {
            int k = 5;
            TermCriteria criteria = new TermCriteria(TermCriteria.EPS+TermCriteria.MAX_ITER, 100, 1.0);
            Mat data = new Mat();
            inputMat.convertTo(data, CV_32F);
            cvtColor(data, data, COLOR_RGBA2RGB);
            data = data.reshape(1, (int)data.total());
            Mat centers = new Mat();
            Mat labels = new Mat();

            kmeans(data, k, labels, criteria, 10, KMEANS_PP_CENTERS, centers);

            centers = centers.reshape(3, centers.rows());
            data = data.reshape(3, data.rows());

            for (int i = 0; i < data.rows(); i++) {
                int centerId = (int)labels.get(i,0)[0];
                data.put(i, 0, centers.get(centerId,0));
            }

            data = data.reshape(3, inputMat.rows());
            data.convertTo(data, CV_8UC3);

            double[] minCenter = new double[3];
            double minCenterVal = Double.MAX_VALUE;

            for (int i = 0; i < centers.rows(); i++) {
                double val = centers.get(i,0)[0] + centers.get(i,0)[1] + centers.get(i,0)[2];
                if (val < minCenterVal) {
                    minCenter = centers.get(i,0);
                    minCenterVal = val;
                }
            }

            double thres = 0.299 * minCenter[0] + 0.587 * minCenter[1] + 0.114 * minCenter[2] + 20.0;

            cvtColor(data, data, COLOR_RGB2GRAY);
            Mat threshold = new Mat();
            Imgproc.threshold(data, threshold, thres, 255, THRESH_BINARY_INV);

            Mat element_erode = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
            Mat element_dilate = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(20, 20));
            Imgproc.erode(threshold, threshold, element_erode);
            Imgproc.dilate(threshold, threshold, element_dilate);
            Imgproc.GaussianBlur(threshold, threshold, new Size(5, 5), 2, 2);

            List<MatOfPoint> contours = new ArrayList<>();
            Mat hierarchy = new Mat();

            Imgproc.findContours(threshold, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE, new Point(0, 0));

            List<Rect> fiducialRects = new ArrayList<>();
            Rect fiducialRect = new Rect(0, 0, 0, 0);

            for (int i = 0; i < contours.size(); i++) {
                Rect rect = Imgproc.boundingRect(contours.get(i));
                double rectPos = rect.x + rect.width;
                if (mRDT.fiducialPositionMin< rectPos && rectPos < mRDT.fiducialPositionMax && mRDT.fiducialMinH < rect.height && mRDT.fiducialMinW < rect.width && rect.width < mRDT.fiducialMaxW) {
                    fiducialRects.add(rect);
                    Log.d(TAG, String.format("Control line rect size: %s %s %s", rect.tl(), rect.br(), rect.size()));
                }
            }

            if (fiducialRects.size() == mRDT.fiducialCount) { //should
                double center0 = fiducialRects.get(0).x + fiducialRects.get(0).width;
                double center1 = fiducialRects.get(0).x + fiducialRects.get(0).width;

                if (fiducialRects.size() > 1) {
                    center1 = fiducialRects.get(1).x + fiducialRects.get(1).width;
                }

                int midpoint = (int) ((center0 + center1) / 2);
                double diff = abs(center0 - center1);

                //double scale = mRDT.fiducialDistance == 0 ? 1 : diff / mRDT.fiducialDistance;
                double scale = 1;
                double offset = scale * mRDT.fiducialToResultWindowOffset;

                Point tl = new Point(midpoint + offset - mRDT.resultWindowRectH * scale / 2.0, mRDT.resultWindowRectWPadding);
                Point br = new Point(midpoint + offset + mRDT.resultWindowRectH * scale / 2.0, inputMat.size().height - mRDT.resultWindowRectWPadding);

                fiducialRect = new Rect(tl, br);
            }


            labels.release();
            centers.release();
            data.release();
            threshold.release();
            element_erode.release();
            element_dilate.release();

            return fiducialRect;
        }
    }

    private boolean readLine(Mat inputMat, Point position, boolean isControlLine) {
        Mat hls = new Mat();
        cvtColor(inputMat, hls, Imgproc.COLOR_RGBA2RGB);
        cvtColor(hls, hls, Imgproc.COLOR_RGB2HLS);

        List<Mat> channels = new ArrayList<>();
        Core.split(hls, channels);

        int lower_bound = (int)(position.x-mRDT.lineSearchWidth < 0 ? 0 : position.x-mRDT.lineSearchWidth);
        int upper_bound = (int)(position.x+mRDT.lineSearchWidth);
        upper_bound = upper_bound > channels.get(1).cols() ? channels.get(1).cols() : upper_bound;

        float[] avgIntensities = new float[upper_bound-lower_bound];
        float[] avgHues = new float[upper_bound-lower_bound];
        float[] avgSats = new float[upper_bound-lower_bound];

        float min = Float.MAX_VALUE, max = Float.MIN_VALUE;
        int minIndex, maxIndex;

        for (int i = lower_bound; i < upper_bound; i++) {
            float sumIntensity=0;
            float sumHue=0;
            float sumSat=0;
            for (int j = 0; j < channels.get(1).rows(); j++) {
                sumIntensity+=channels.get(1).get(j, i)[0];
                sumHue+=channels.get(0).get(j, i)[0];
                sumSat+=channels.get(2).get(j, i)[0];
            }
            avgIntensities[i-lower_bound] = sumIntensity/channels.get(1).rows();
            avgHues[i-lower_bound] = sumHue/channels.get(0).rows();
            avgSats[i-lower_bound] = sumSat/channels.get(2).rows();

            if (avgIntensities[i-lower_bound] < min) {
                min = avgIntensities[i-lower_bound];
                minIndex = i-lower_bound;
            }

            if (avgIntensities[i-lower_bound] > max) {
                max = avgIntensities[i-lower_bound];
                maxIndex = i-lower_bound;
            }
        }

        if (isControlLine) {
            return min < mRDT.intensityThreshold && abs(min-max) > mRDT.controlIntensityPeakThreshold;
        } else {
            return min < mRDT.intensityThreshold && abs(min-max) > mRDT.testIntensityPeakThreshold;
        }
    }

    private boolean readControlLine(Mat inputMat, Point position) {
            return readLine(inputMat, position, true);
    }

    private boolean readTestLine(Mat inputMat, Point position) {
            return readLine(inputMat, position, false);
    }


    private Mat enhanceResultWindow(Mat inputMat, Size tile) {
        return enhanceImage(inputMat, tile);
    }

    private Mat cropResultWindow(Mat inputMat, MatOfPoint2f boundary) {
        Mat refBoundary = new Mat(4, 1, CvType.CV_32FC2);

        double[] a = new double[]{0, 0};
        double[] b = new double[]{mRDT.refImg.cols() - 1, 0};
        double[] c = new double[]{mRDT.refImg.cols() - 1, mRDT.refImg.rows() - 1};
        double[] d = new double[]{0, mRDT.refImg.rows() - 1};


        //get corners from object
        refBoundary.put(0, 0, a);
        refBoundary.put(1, 0, b);
        refBoundary.put(2, 0, c);
        refBoundary.put(3, 0, d);

        Mat M = getPerspectiveTransform(boundary, refBoundary);
        Mat correctedMat = new Mat(mRDT.refImg.rows(), mRDT.refImg.cols(), mRDT.refImg.type());
        warpPerspective(inputMat, correctedMat, M, new Size(mRDT.refImg.cols(), mRDT.refImg.rows()));

        //Rect resultWindowRect = checkFiducialAndReturnResultWindowRect(correctedMat);
        Rect resultWindowRect = checkFiducialKMenas(correctedMat);
        //Rect resultWindowRect = returnResultWindowRect(correctedMat);

        correctedMat = new Mat(correctedMat, resultWindowRect);
        if (correctedMat.width() > 0 && correctedMat.height() > 0) {
            resize(correctedMat, correctedMat, new Size(mRDT.resultWindowRectH, mRDT.refImg.rows() - 2 * mRDT.resultWindowRectWPadding));
        }

        return correctedMat;
    }

    private MatOfPoint2f detectRDTWithSIFT(Mat inputMat, int ransac){
        double scale = 0.5;
        Mat scaledMat = new Mat();
        Imgproc.resize(inputMat, scaledMat, new Size(), scale, scale, Imgproc.INTER_LINEAR);
        double currentTime = System.currentTimeMillis();
        Mat inDescriptor = new Mat();
        MatOfKeyPoint inKeypoints = new MatOfKeyPoint();
        MatOfPoint2f boundary = new MatOfPoint2f();

        Mat mask = new Mat(scaledMat.cols(), scaledMat.rows(), CV_8U, new Scalar(0));

        Point p1 = new Point(0, scaledMat.size().height*(1-mRDT.viewFinderScaleW/CROP_RATIO)/2);
        Point p2 = new Point(scaledMat.size().width-p1.x, scaledMat.size().height-p1.y);
        Imgproc.rectangle(mask, p1, p2, new Scalar(255), -1);

        mRDT.detector.detectAndCompute(scaledMat, mask, inKeypoints, inDescriptor);

        if (inDescriptor.size().equals(new Size(0,0))) { // No features found!
            return boundary;
        }

        if (mRDT.refDescriptor.size().equals(new Size(0,0))) { // No features found!
            Log.d(TAG, "Empty sift ref descriptor!!!");
            return boundary;
        }

        // Matching
        List<MatOfDMatch> matches = new ArrayList<>();
        mRDT.matcher.knnMatch(mRDT.refDescriptor, inDescriptor, matches, 2, new Mat(), false);

        double maxDist = Double.MIN_VALUE;
        double minDist = Double.MAX_VALUE;

        double sum = 0;
        int count = 0;

        ArrayList<DMatch> goodMatches = new ArrayList<>();
        for (int i = 0; i < matches.size(); i++) {
            DMatch[] dMatches = matches.get(i).toArray();
            if (dMatches.length >= 2) {
                DMatch m = dMatches[0];
                DMatch n = dMatches[1];
                if (m.distance <= 0.80 * n.distance) {
                    goodMatches.add(m);
                    sum += m.distance;
                    count++;
                }
            }
        }

        MatOfDMatch goodMatchesMat = new MatOfDMatch();
        goodMatchesMat.fromList(goodMatches);

        //put keypoints mats into lists
        List<KeyPoint> keypointsList1 = mRDT.refKeypoints.toList();
        List<KeyPoint> keypointsList2 = inKeypoints.toList();

        List<Point> objList = new ArrayList<>();
        List<Point> sceneList = new ArrayList<>();

        for(int i=0;i<goodMatches.size();i++)
        {
            objList.add(keypointsList1.get(goodMatches.get(i).queryIdx).pt);
            sceneList.add(keypointsList2.get(goodMatches.get(i).trainIdx).pt);
        }

        MatOfPoint2f objMat = new MatOfPoint2f();
        MatOfPoint2f sceneMat = new MatOfPoint2f();
        objMat.fromList(objList);
        sceneMat.fromList(sceneList);

        // HOMOGRAPHY!
        if (goodMatches.size() > GOOD_MATCH_COUNT) {
            Mat H = Calib3d.findHomography(objMat, sceneMat, Calib3d.RANSAC, ransac);

            if (H.cols() >= 3 && H.rows() >= 3) {
                Mat objCorners = new Mat(4, 1, CvType.CV_32FC2);
                Mat sceneCorners = new Mat(4, 1, CvType.CV_32FC2);

                double[] a = new double[]{0, 0};
                double[] b = new double[]{mRDT.refImg.cols() - 1, 0};
                double[] c = new double[]{mRDT.refImg.cols() - 1, mRDT.refImg.rows() - 1};
                double[] d = new double[]{0, mRDT.refImg.rows() - 1};

                //get corners from object
                objCorners.put(0, 0, a);
                objCorners.put(1, 0, b);
                objCorners.put(2, 0, c);
                objCorners.put(3, 0, d);

                perspectiveTransform(objCorners, sceneCorners, H);

                Log.d(TAG, String.format("transformed -- SIFT: (%.2f, %.2f) (%.2f, %.2f) (%.2f, %.2f) (%.2f, %.2f), width: %d, height: %d",
                        sceneCorners.get(0, 0)[0], sceneCorners.get(0, 0)[1],
                        sceneCorners.get(1, 0)[0], sceneCorners.get(1, 0)[1],
                        sceneCorners.get(2, 0)[0], sceneCorners.get(2, 0)[1],
                        sceneCorners.get(3, 0)[0], sceneCorners.get(3, 0)[1], sceneCorners.width(), sceneCorners.height()));

                ArrayList<Point> listOfBoundary = new ArrayList<>();
                listOfBoundary.add(new Point(sceneCorners.get(0, 0)[0]/scale, sceneCorners.get(0, 0)[1]/scale));
                listOfBoundary.add(new Point(sceneCorners.get(1, 0)[0]/scale, sceneCorners.get(1, 0)[1]/scale));
                listOfBoundary.add(new Point(sceneCorners.get(2, 0)[0]/scale, sceneCorners.get(2, 0)[1]/scale));
                listOfBoundary.add(new Point(sceneCorners.get(3, 0)[0]/scale, sceneCorners.get(3, 0)[1]/scale));

                boundary.fromList(listOfBoundary);
                //quidel specific code
//                objCorners.release();
//                sceneCorners.release();
//
//                RotatedRect rotatedRect = minAreaRect(boundary);
//                Point[] v = new Point[4];
//                Point[] bound = new Point[4];
//                rotatedRect.points(v);
//
//                for (int i = 0; i < 4; i++) {
//                    if(rotatedRect.angle < -45)
//                        bound[(i+2)%4] = new Point(v[i].x, v[i].y);
//                    else
//                        bound[(i+3)%4] = new Point(v[i].x, v[i].y);
//                }
//
//                boundary.fromArray(bound);
            }
            H.release();
        }
        scaledMat.release();
        goodMatchesMat.release();
        objMat.release();
        sceneMat.release();
        mask.release();
        inDescriptor.release();
        inKeypoints.release();
        Log.d(TAG, "Detect RDT TIME: " + (System.currentTimeMillis()-currentTime));
        return boundary;
    }

    private Rect getViewfinderRect(Mat inputMat) {
        Point p1 = new Point(inputMat.size().width*(1-mRDT.viewFinderScaleH)/2, inputMat.size().height*(1-mRDT.viewFinderScaleW)/2);
        Point p2 = new Point(inputMat.size().width-p1.x, inputMat.size().height-p1.y);
        return new Rect(p1, p2);
    }

    //methods for debugging
    private void saveImage (Mat inputMat) {
        File sdIconStorageDir = new File(Constants.RDT_IMAGE_DIR);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss-SSS");

        try {
            String filePath = sdIconStorageDir.toString() + String.format("/%s-%08dms.jpg", sdf.format(new Date()), 0);
            FileOutputStream fileOutputStream = new FileOutputStream(filePath);

            fileOutputStream.write(ImageUtil.matToRotatedByteArray(inputMat));

            fileOutputStream.flush();
            fileOutputStream.close();

            //sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + filePath)));

            //Toast.makeText(this,"Image is successfully saved!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.w("TAG", "Error saving image file: " + e.getMessage());
        }
    }

    private void drawKeypointsAndMatches(Mat inputMat, MatOfPoint boundary, MatOfKeyPoint inKeypoints, MatOfDMatch goodMatchesMat) {
        Mat resultMat = new Mat();
        MatOfPoint boundaryMat = new MatOfPoint();
        boundaryMat.fromList(boundary.toList());
        ArrayList<MatOfPoint> list = new ArrayList<>();
        list.add(boundaryMat);

        Mat tempInputMat = inputMat.clone();
        Imgproc.polylines(tempInputMat, list, true, Scalar.all(0),10);
        Features2d.drawKeypoints(tempInputMat, inKeypoints, tempInputMat);
        Features2d.drawMatches(mRDT.refImg, mRDT.refKeypoints, tempInputMat, inKeypoints, goodMatchesMat, resultMat);
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
}
