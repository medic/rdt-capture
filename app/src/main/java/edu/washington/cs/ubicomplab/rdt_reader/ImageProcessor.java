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
import android.graphics.BitmapFactory;
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
import org.opencv.features2d.BFMatcher;
import org.opencv.features2d.BRISK;
import org.opencv.features2d.Features2d;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Scalar;
import org.opencv.xfeatures2d.SIFT;


import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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
    private BRISK mFeatureDetector;
    private BFMatcher mMatcher;
    private Mat mRefImg;
    private Mat mRefDescriptor;
    private MatOfKeyPoint mRefKeypoints;
    private SIFT siftDetector;
    private BFMatcher siftMatcher;
    private MatOfKeyPoint siftRefKeypoints;
    private Mat siftRefDescriptor;
    private double refImgSharpness = Double.MIN_VALUE;
    private int mMoveCloserCount = 0;
    private boolean DEBUG_FLAG = false;


    public enum ExposureResult {
        UNDER_EXPOSED, NORMAL, OVER_EXPOSED
    }

    public enum SizeResult{
        RIGHT_SIZE, LARGE, SMALL, INVALID

    }

    public class CaptureResult {
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

        public CaptureResult(boolean allChecksPassed, Mat resultMat, boolean fiducial,
                             ExposureResult exposureResult, SizeResult sizeResult,  boolean isCentered,
                             boolean isRightOrientation, double angle, boolean isSharp, boolean isShadow, MatOfPoint2f boundary){
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
        }
    }

    public static class InterpretationResult {
        public boolean control;
        public boolean testA;
        public boolean testB;
        public Mat resultMat;
        public Bitmap resultBitmap;

        public InterpretationResult() {
            control = false;
            testA = false;
            testB = false;
            resultMat = new Mat();
            resultBitmap = null;
        }

        public InterpretationResult(Mat resultMat, boolean control, boolean testA, boolean testB) {
            this.resultMat = resultMat;
            this.control = control;
            this.testA = testA;
            this.testB = testB;
            if (resultMat.cols() > 0 && resultMat.rows() > 0) {
                this.resultBitmap = Bitmap.createBitmap(resultMat.cols(), resultMat.rows(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(resultMat, resultBitmap);
            }
        }
    }

    public ImageProcessor (Activity activity) {
        mFeatureDetector = BRISK.create(45, 4, 1.0f);
        mMatcher = BFMatcher.create(BFMatcher.BRUTEFORCE_HAMMING, false);
        mRefImg = new Mat();

        //Load reference image for Quickvue flu test strip
        Bitmap bitmap = BitmapFactory.decodeResource(activity.getApplicationContext().getResources(), R.drawable.quickvue_ref_v5);
        //Load reference image for SD Bioline Malaria RDT
        //Bitmap bitmap = BitmapFactory.decodeResource(activity.getApplicationContext().getResources(), R.drawable.sd_bioline_malaria_ag_pf);

        Utils.bitmapToMat(bitmap, mRefImg);
        //resize(mRefImg, mRefImg, new Size(bitmap.getWidth(), bitmap.getHeight()));
        cvtColor(mRefImg, mRefImg, Imgproc.COLOR_RGB2GRAY);
        mRefDescriptor = new Mat();
        siftRefDescriptor = new Mat();
        mRefKeypoints = new MatOfKeyPoint();
        siftRefKeypoints = new MatOfKeyPoint();
        long startTime = System.currentTimeMillis();
        mFeatureDetector.detectAndCompute(mRefImg, new Mat(), mRefKeypoints, mRefDescriptor);

        Imgproc.GaussianBlur(mRefImg, mRefImg, new Size(5, 5), 0, 0);
        refImgSharpness = calculateSharpness(mRefImg);

        siftDetector = SIFT.create();
        siftMatcher = BFMatcher.create(BFMatcher.BRUTEFORCE, false);
        siftDetector.detectAndCompute(mRefImg, new Mat(), siftRefKeypoints, siftRefDescriptor);

        if (DEBUG_FLAG) {
            Log.d(TAG, "BRISK keypoints: " + mRefKeypoints.toArray().length);
            Log.d(TAG, "SIFT keypoints: " + siftRefKeypoints.toArray().length);
            Log.d(TAG, "REFERENCE LOAD/DETECT/COMPUTE: " + (System.currentTimeMillis() - startTime));
        }
    }

    public static ImageProcessor getInstance(Activity activity) {
        if (instance == null)
            instance = new ImageProcessor(activity);

        return instance;
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

    public CaptureResult captureRDT(Mat inputMat) {
        // Convert the input to grayscale
        Mat greyMat = new Mat();
        cvtColor(inputMat, greyMat, Imgproc.COLOR_RGBA2GRAY);

        // Check brightness
        ExposureResult exposureResult = checkBrightness(greyMat);

        // Check sharpness
        boolean isSharp = checkSharpness(greyMat.submat(getViewfinderRect(greyMat)));

        // Attempt to detect the RDT using homography
        MatOfPoint2f boundary = detectRDTWithSIFT(greyMat, 5);
        //boundary = detectRDT(greyMat);

        // Check the detected RDT's size and position
        boolean isCentered = false;
        SizeResult sizeResult = SizeResult.INVALID;
        boolean isRightOrientation = false;
        double angle = 0.0;
        if (boundary.size().width > 0 && boundary.size().height > 0) {
            isCentered = checkIfCentered(boundary, greyMat.size());
            sizeResult = checkSize(boundary, greyMat.size());
            isRightOrientation = checkOrientation(boundary);
            angle = measureOrientation(boundary);
        }

        boolean passed = exposureResult == ExposureResult.NORMAL &&
                isSharp &&
                sizeResult == SizeResult.RIGHT_SIZE &&
                isCentered &&
                isRightOrientation;

        //check fiducial
        boolean fiducial = false;
        if (passed) {
            Mat resultMat = cropResultWindow(inputMat, boundary);
            if (resultMat.width() > 0 && resultMat.height() > 0) {
                fiducial = true;
            }
            resultMat.release();
            passed = passed & fiducial;
            if (DEBUG_FLAG)
                Log.d(TAG, String.format("fiducial: %b", fiducial));
        }

        greyMat.release();
        return new CaptureResult(passed, cropRDT(inputMat), fiducial, exposureResult, sizeResult, isCentered, isRightOrientation, angle, isSharp, false, boundary);
    }

    /**
     * Determines if the input image is sufficiently sharp
     * @param inputMat: the input image
     * @return a boolean that describes whether the sharpness is good enough or not
     */
    private boolean checkSharpness(Mat inputMat) {
        // Resize the image
        Mat resized = new Mat();
        double scaleFactor = mRefImg.size().width/inputMat.size().width;
        resize(inputMat, resized, new Size(inputMat.size().width*scaleFactor,
                inputMat.size().height*scaleFactor));

        // Calculate sharpness
        double sharpness = calculateSharpness(resized);
        if (DEBUG_FLAG)
            Log.d(TAG, String.format("inputMat sharpness: %.2f", sharpness));

        // Release resources
        inputMat.release();
        resized.release();

        // Compare sharpness to requirement
        return sharpness > (refImgSharpness * (1-SHARPNESS_THRESHOLD));
    }

    /**
     * Computes a sharpness value for the input image
     * @param input: the input image
     * @return mBuff: the squared standard deviation of the Laplacian
     */
    private double calculateSharpness(Mat input) {
        // Compute Laplacian
        Mat laplace = new Mat();
        Laplacian(input, laplace, CvType.CV_64F);

        // Compute Laplacian's mean and stdev
        MatOfDouble median = new MatOfDouble();
        MatOfDouble std = new MatOfDouble();
        meanStdDev(laplace, median, std);

        // Release resources
        laplace.release();

        // Return squared stdev
        return pow(std.get(0,0)[0], 2);
    }

    /**
     * Determines if the input image is sufficiently exposed
     * @param inputMat: the input image
     * @return exposureResult: an ExposureResult enum value that can be {NORMAL,
     * OVER_EXPOSED, UNDER_EXPOSED}
     */
    private ExposureResult checkBrightness(Mat inputMat) {
        // Compute brightness histograms
        float[] histograms = calculateBrightness(inputMat);

        // Calculate brightest value
        int maxWhite = 0;
        for (int i = histograms.length-1; i >= 0; i--) {
            if (histograms[i] > 0) {
                maxWhite = i;
                break;
            }
        }

        // Compute amount of clipping
        float clippingCount = histograms[histograms.length-1];

        // Compare exposure to requirements
        ExposureResult exposureResult;
        if (maxWhite >= OVER_EXP_THRESHOLD && clippingCount > OVER_EXP_WHITE_COUNT) {
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

    /**
     * Computes a brightness histogram for the input image
     * @param input: the input image
     * @return mBuff: a float[] histogram with 256 elements
     */
    private float[] calculateBrightness(Mat input) {
        // Initialize variables
        int mHistSizeNum = 256;
        MatOfInt mHistSize = new MatOfInt(mHistSizeNum);
        Mat hist = new Mat();
        final float[] mBuff = new float[mHistSizeNum];
        MatOfFloat histogramRanges = new MatOfFloat(0f, 256f);
        MatOfInt mChannels[] = new MatOfInt[] { new MatOfInt(0)};
        org.opencv.core.Size sizeRgba = input.size();

        // Go through each channel, normalize the histogram, and add it to mBuff
        // TODO: this for loop is somewhat set up to handle multi-channel, but not done
        for (int ch = 0; ch < input.channels(); ch++) {
            Imgproc.calcHist(Arrays.asList(input), mChannels[ch], new Mat(), hist,
                    mHistSize, histogramRanges);
            Core.normalize(hist, hist, sizeRgba.height/2, 0, Core.NORM_INF);
            hist.get(0, 0, mBuff);
            mChannels[ch].release();
        }

        // Release resources
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
        boolean isRightSize = height < size.width*VIEW_FINDER_SCALE_H+size.width*SIZE_THRESHOLD && height > size.width*VIEW_FINDER_SCALE_H-size.width*SIZE_THRESHOLD;

        SizeResult sizeResult = SizeResult.INVALID;

        if (isRightSize) {
            sizeResult = SizeResult.RIGHT_SIZE;
        } else {
            if (height > size.width*VIEW_FINDER_SCALE_H+size.width*SIZE_THRESHOLD) {
                sizeResult = SizeResult.LARGE;
            } else if (height < size.width*VIEW_FINDER_SCALE_H-size.width*SIZE_THRESHOLD) {
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

    private Point measureCentering(MatOfPoint2f boundary) {
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

    private Mat cropRDT(Mat inputMat) {
        int width = (int)(inputMat.cols() * CROP_RATIO);
        int height = (int)(inputMat.rows() * CROP_RATIO);
        int x = (int)(inputMat.cols() * (1.0-CROP_RATIO)/2);
        int y = (int)(inputMat.rows() * (1.0-CROP_RATIO)/2);

        Rect roi = new Rect(x, y, width, height);
        Mat cropped = new Mat(inputMat, roi);

        return cropped;
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
        boolean control, testA, testB;

        if (resultMat.width() == 0 && resultMat.height() == 0) {
            return new InterpretationResult(resultMat, false, false, false);
        }

        Mat grayMat = new Mat();
        cvtColor(resultMat, grayMat, COLOR_RGB2GRAY);
        MatOfDouble mu = new MatOfDouble();
        MatOfDouble sigma = new MatOfDouble();
        Core.meanStdDev(grayMat, mu, sigma);
        Core.MinMaxLocResult minMaxLocResult = Core.minMaxLoc(grayMat);

        if (DEBUG_FLAG)
            Log.d(TAG, String.format("stdev %.2f, minval %.2f at %s, maxval %.2f at %s",
                    sigma.get(0,0)[0],
                    minMaxLocResult.minVal, minMaxLocResult.minLoc,
                    minMaxLocResult.maxVal, minMaxLocResult.maxLoc));

        if (sigma.get(0,0)[0] > ENHANCING_THRESHOLD)
            resultMat = enhanceResultWindow(resultMat, new Size(5, resultMat.cols()));

        //resultMat = enhanceResultWindow(resultMat, new Size(10, 10));
        //resultMat = correctGamma(resultMat, 0.75);

        control = readControlLine(resultMat, new Point(CONTROL_LINE_POSITION, 0));
        testA = readTestLine(resultMat, new Point(TEST_A_LINE_POSITION, 0));
        testB = readTestLine(resultMat, new Point(TEST_B_LINE_POSITION, 0));

        grayMat.release();
        mu.release();
        sigma.release();

        return new InterpretationResult(resultMat, control, testA, testB);
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
            isSizeable = checkSize(boundary, new Size(inputMat.size().width/CROP_RATIO, inputMat.size().height/CROP_RATIO));
            isCentered = checkIfCentered(boundary, inputMat.size());
            isUpright = checkOrientation(boundary);
            if (DEBUG_FLAG)
                Log.d(TAG, String.format("SIFT-right size %s, center %s, orientation %s, (%.2f, %.2f), cnt %d",
                        isSizeable, isCentered, isUpright, inputMat.size().width, inputMat.size().height, cnt));
        } while(!(isSizeable==SizeResult.RIGHT_SIZE && isCentered && isUpright) && cnt < 8);

        if (boundary.size().width <= 0 && boundary.size().height <= 0)
            return new InterpretationResult();

        return interpretResult(inputMat, boundary);
    }

    private Rect returnResultWindowRect(Mat inputMat) {
        return new Rect(RESULT_WINDOW_X, RESULT_WINDOW_Y, RESULT_WINDOW_WIDTH, RESULT_WINDOW_HEIGHT);
    }

    private Rect checkFiducialKMenas(Mat inputMat) {
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
            if (FIDUCIAL_POSITION_MIN < rectPos && rectPos < FIDUCIAL_POSITION_MAX && FIDUCIAL_MIN_HEIGHT < rect.height && FIDUCIAL_MIN_WIDTH < rect.width && rect.width < FIDUCIAL_MAX_WIDTH) {
                fiducialRects.add(rect);
                if (DEBUG_FLAG)
                    Log.d(TAG, String.format("Control line rect size: %s %s %s", rect.tl(), rect.br(), rect.size()));
            }
        }

        if (fiducialRects.size() == FIDUCIAL_COUNT) { //should
            double center0 = fiducialRects.get(0).x + fiducialRects.get(0).width;
            double center1 = fiducialRects.get(0).x + fiducialRects.get(0).width;

            if (fiducialRects.size() > 1) {
                center1 = fiducialRects.get(1).x + fiducialRects.get(1).width;
            }

            int midpoint = (int) ((center0 + center1) / 2);
            double diff = abs(center0 - center1);

            double scale = FIDUCIAL_DISTANCE == 0 ? 1 : diff / FIDUCIAL_DISTANCE;
            double offset = scale * FIDUCIAL_TO_CONTROL_LINE_OFFSET;

            Point tl = new Point(midpoint + offset - RESULT_WINDOW_RECT_HEIGHT * scale / 2.0, RESULT_WINDOW_RECT_WIDTH_PADDING);
            Point br = new Point(midpoint + offset + RESULT_WINDOW_RECT_HEIGHT * scale / 2.0, inputMat.size().height - RESULT_WINDOW_RECT_WIDTH_PADDING);

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

    private Rect checkFiducialAndReturnResultWindowRect(Mat inputMat)  {
        if (FIDUCIAL_COUNT == 0) {
            Point tl = new Point(FIDUCIAL_TO_CONTROL_LINE_OFFSET - RESULT_WINDOW_RECT_HEIGHT / 2.0, RESULT_WINDOW_RECT_WIDTH_PADDING);
            Point br = new Point(FIDUCIAL_TO_CONTROL_LINE_OFFSET + RESULT_WINDOW_RECT_HEIGHT / 2.0, inputMat.size().height - RESULT_WINDOW_RECT_WIDTH_PADDING);

            Rect fiducialRect = new Rect(tl, br);

            return fiducialRect;
        } else {
            Mat hls = new Mat();
            //inputMat = enhanceImage(inputMat, new Size(5,5));
            cvtColor(inputMat, hls, Imgproc.COLOR_RGBA2RGB);
            cvtColor(hls, hls, Imgproc.COLOR_RGB2HLS);

            Mat[] thresholds = {new Mat(inputMat.rows(), inputMat.cols(), CV_8U, new Scalar(0)), new Mat(inputMat.rows(), inputMat.cols(), CV_8U, new Scalar(0))};

            Mat threshold = new Mat(inputMat.rows(), inputMat.cols(), CV_8U, new Scalar(0));

            for (int i = 0; i < CONTROL_LINE_COLOR_LOWER.length; i++) {
                Core.inRange(hls, CONTROL_LINE_COLOR_LOWER[i], CONTROL_LINE_COLOR_UPPER[i], thresholds[i]);
                Core.add(threshold, thresholds[i], threshold);
            }

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
                double rectCenter = rect.x + rect.width / 2.0;
                if (FIDUCIAL_POSITION_MIN < rectCenter && rectCenter < FIDUCIAL_POSITION_MAX && FIDUCIAL_MIN_HEIGHT < rect.height && FIDUCIAL_MIN_WIDTH < rect.width && rect.width < FIDUCIAL_MAX_WIDTH) {
                    fiducialRects.add(rect);
                    if (DEBUG_FLAG)
                        Log.d(TAG, String.format("Control line rect size: %s %s %s", rect.tl(), rect.br(), rect.size()));
                }
            }

            if (fiducialRects.size() == FIDUCIAL_COUNT) { //should
                double center0 = fiducialRects.get(0).x + fiducialRects.get(0).width / 2.0;
                double center1 = fiducialRects.get(0).x + fiducialRects.get(0).width / 2.0;

                if (fiducialRects.size() > 1) {
                    center1 = fiducialRects.get(1).x + fiducialRects.get(1).width / 2.0;
                }

                int midpoint = (int) ((center0 + center1) / 2);
                double diff = abs(center0 - center1);

                double scale = FIDUCIAL_DISTANCE == 0 ? 1 : diff / FIDUCIAL_DISTANCE;
                double offset = scale * FIDUCIAL_TO_CONTROL_LINE_OFFSET;

                Point tl = new Point(midpoint + offset - RESULT_WINDOW_RECT_HEIGHT * scale / 2.0, RESULT_WINDOW_RECT_WIDTH_PADDING);
                Point br = new Point(midpoint + offset + RESULT_WINDOW_RECT_HEIGHT * scale / 2.0, inputMat.size().height - RESULT_WINDOW_RECT_WIDTH_PADDING);

                fiducialRect = new Rect(tl, br);
            }

            for (int i = 0; i < CONTROL_LINE_COLOR_LOWER.length; i++) {
                thresholds[i].release();
            }
            threshold.release();
            hls.release();
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

        int lower_bound = (int)(position.x-LINE_SEARCH_WIDTH < 0 ? 0 : position.x-LINE_SEARCH_WIDTH);
        int upper_bound = (int)(position.x+LINE_SEARCH_WIDTH);
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
            return min < INTENSITY_THRESHOLD && abs(min-max) > CONTROL_INTENSITY_PEAK_THRESHOLD;
        } else {
            return min < INTENSITY_THRESHOLD && abs(min-max) > TEST_INTENSITY_PEAK_THRESHOLD;
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
        double[] b = new double[]{mRefImg.cols() - 1, 0};
        double[] c = new double[]{mRefImg.cols() - 1, mRefImg.rows() - 1};
        double[] d = new double[]{0, mRefImg.rows() - 1};


        //get corners from object
        refBoundary.put(0, 0, a);
        refBoundary.put(1, 0, b);
        refBoundary.put(2, 0, c);
        refBoundary.put(3, 0, d);

        Mat M = getPerspectiveTransform(boundary, refBoundary);
        Mat correctedMat = new Mat(mRefImg.rows(), mRefImg.cols(), mRefImg.type());
        warpPerspective(inputMat, correctedMat, M, new Size(mRefImg.cols(), mRefImg.rows()));

        //Rect resultWindowRect = checkFiducialAndReturnResultWindowRect(correctedMat);
        Rect resultWindowRect = checkFiducialKMenas(correctedMat);
        //Rect resultWindowRect = returnResultWindowRect(correctedMat);

        correctedMat = new Mat(correctedMat, resultWindowRect);
        if (correctedMat.width() > 0 && correctedMat.height() > 0) {
            resize(correctedMat, correctedMat, new Size(RESULT_WINDOW_RECT_HEIGHT, mRefImg.rows() - 2 * RESULT_WINDOW_RECT_WIDTH_PADDING));
        }

        return correctedMat;
    }

    /**
     * Attempts to identify the bounding box around the RDT within the input image using BRISK, if it is there
     * TODO: we've gotta merge this with the other detectRDT method at some point
     * @param inputMat: the input image
     * @return boundary: the MatOfPoint2f bounding box around the identified RDT
     */
    private MatOfPoint2f detectRDT(Mat inputMat) {
        // Initialize data structures and start timer
        long startTime = System.currentTimeMillis();
        Mat descriptors = new Mat();
        MatOfKeyPoint keypoints = new MatOfKeyPoint();
        MatOfPoint2f boundary = new MatOfPoint2f();

        // Create a mask for where to generate features
        // TODO: can we make this tighter regardless of OpenCV's bug?
        Mat mask = new Mat(inputMat.size(), CV_8U, Scalar.all(0));
        Point p1 = new Point(0, inputMat.size().height*(1-VIEW_FINDER_SCALE_W/CROP_RATIO)/2);
        Point p2 = new Point(inputMat.size().width-p1.x, inputMat.size().height-p1.y);
        Imgproc.rectangle(mask, p1, p2, Scalar.all(255), -1);

        // Compute features and descriptors
        mFeatureDetector.detectAndCompute(inputMat, mask, keypoints, descriptors);
        if (DEBUG_FLAG)
            Log.d(TAG, "detect/compute TIME: " + (System.currentTimeMillis()-startTime));

        // Break early if no features found
        if (descriptors.size().equals(new Size(0,0))) {
            descriptors.release();
            keypoints.release();
            boundary.release();
            return boundary;
        }

        // Compute matches
        MatOfDMatch matches = new MatOfDMatch();
        mMatcher.match(mRefDescriptor, descriptors, matches);
        if (DEBUG_FLAG)
            Log.d(TAG, "matching TIME: " + (System.currentTimeMillis()-startTime));

        // Sort matches from lowest to highest distance
        List<DMatch> matchesList = matches.toList();
        Comparator<DMatch> comparator = new Comparator<DMatch>() {
            @Override
            public int compare(DMatch dMatch, DMatch t1) {
                if (dMatch.distance == t1.distance)
                    return 0;
                else if (dMatch.distance < t1.distance)
                    return -1;
                else
                    return 1;
            }
        };
        Collections.sort(matchesList, comparator);
        if (DEBUG_FLAG)
            Log.d(TAG, "match sorting TIME: " + (System.currentTimeMillis()-startTime));

        // Save only the good matches
        List<DMatch> goodMatches = matchesList;
//        List<DMatch> goodMatches =  matchesList.size() > 50 ? matchesList.subList(0, 50): matchesList;
        MatOfDMatch goodMatchesMat = new MatOfDMatch();
        goodMatchesMat.fromList(goodMatches);
        if (DEBUG_FLAG)
            Log.d(TAG, String.format("Good match: %d", goodMatches.size()));

        // Break early if not enough good matches
        if (goodMatches.size() <= GOOD_MATCH_COUNT) {
            descriptors.release();
            keypoints.release();
            mask.release();
            goodMatchesMat.release();
            return boundary;
        }

        // Put KeyPoints from Mats into Lists
        List<KeyPoint> keypoints1_List = mRefKeypoints.toList();
        List<KeyPoint> keypoints2_List = keypoints.toList();

        // Put Points from good matches into MatOfPoint2f
        List<Point> objList = new ArrayList<>();
        List<Point> sceneList = new ArrayList<>();
        for (int i = 0; i < goodMatches.size(); i++) {
            objList.add(keypoints1_List.get(goodMatches.get(i).queryIdx).pt);
            sceneList.add(keypoints2_List.get(goodMatches.get(i).trainIdx).pt);
        }
        MatOfPoint2f objMat = new MatOfPoint2f();
        MatOfPoint2f sceneMat = new MatOfPoint2f();
        objMat.fromList(objList);
        sceneMat.fromList(sceneList);


        // Compute homography
        Mat H = Calib3d.findHomography(objMat, sceneMat, Calib3d.RANSAC, 5);
        if (DEBUG_FLAG)
            Log.d(TAG, "find homography TIME: " + (System.currentTimeMillis()-startTime));

        // If the homography is valid, map corners of template into input image
        if (H.cols() >= 3 && H.rows() >= 3) {
            // Get template corners
            Mat objCorners = new Mat(4, 1, CvType.CV_32FC2);
            double[] a = new double[]{0, 0};
            double[] b = new double[]{mRefImg.cols() - 1, 0};
            double[] c = new double[]{mRefImg.cols() - 1, mRefImg.rows() - 1};
            double[] d = new double[]{0, mRefImg.rows() - 1};
            objCorners.put(0, 0, a);
            objCorners.put(1, 0, b);
            objCorners.put(2, 0, c);
            objCorners.put(3, 0, d);

            // Apply transform to get corresponding corners in input image
            Mat sceneCorners = new Mat(4, 1, CvType.CV_32FC2);
            perspectiveTransform(objCorners, sceneCorners, H);
            if (DEBUG_FLAG)
                Log.d(TAG, String.format("transformed -- BRISK: (%.2f, %.2f) (%.2f, %.2f) (%.2f, %.2f) (%.2f, %.2f), width: %d, height: %d",
                        sceneCorners.get(0, 0)[0], sceneCorners.get(0, 0)[1],
                        sceneCorners.get(1, 0)[0], sceneCorners.get(1, 0)[1],
                        sceneCorners.get(2, 0)[0], sceneCorners.get(2, 0)[1],
                        sceneCorners.get(3, 0)[0], sceneCorners.get(3, 0)[1], sceneCorners.width(), sceneCorners.height()));

            // Extract those points
            ArrayList<Point> listOfBoundary = new ArrayList<>();
            listOfBoundary.add(new Point(sceneCorners.get(0, 0)));
            listOfBoundary.add(new Point(sceneCorners.get(1, 0)));
            listOfBoundary.add(new Point(sceneCorners.get(2, 0)));
            listOfBoundary.add(new Point(sceneCorners.get(3, 0)));
            boundary.fromList(listOfBoundary);

            // Release resources
            objCorners.release();
            sceneCorners.release();

            RotatedRect rotatedRect = minAreaRect(boundary);
            Point[] v = new Point[4];
            Point[] bound = new Point[4];
            rotatedRect.points(v);

            // Properly orders the points depending on the orientation
            for (int i = 0; i < 4; i++) {
                if(rotatedRect.angle < -45)
                    bound[(i+2)%4] = v[i];
                else
                    bound[(i+3)%4] = v[i];
            }
            boundary.fromArray(bound);
        }

        // Release resources
        descriptors.release();
        keypoints.release();
        mask.release();
        matches.release();
        objMat.release();
        sceneMat.release();
        H.release();
        if (DEBUG_FLAG)
            Log.d(TAG, "Detect RDT TIME: " + (System.currentTimeMillis()-startTime));
        return boundary;
    }

    /**
     * Attempts to identify the bounding box around the RDT within the input image using SIFT, if it is there
     * @param inputMat: the input image
     * @param ransac: the ransac reprojection error threshold // TODO is this needed?
     * @return boundary: the MatOfPoint2f bounding box around the identified RDT
     */
    private MatOfPoint2f detectRDTWithSIFT(Mat inputMat, int ransac) {
        // Initialize data structures and start timer
        double startTime = System.currentTimeMillis();
        Mat inDescriptor = new Mat();
        MatOfKeyPoint inKeypoints = new MatOfKeyPoint();
        MatOfPoint2f boundary = new MatOfPoint2f();

        // Downsample the image to save time
        double scale = 0.5;
        Mat scaledMat = new Mat();
        Imgproc.resize(inputMat, scaledMat, new Size(), scale, scale, Imgproc.INTER_LINEAR);

        // Create a mask for where to generate features
        // TODO: can we make this tighter regardless of OpenCV's bug?
        Mat mask = new Mat(scaledMat.cols(), scaledMat.rows(), CV_8U, new Scalar(0));
        Point p1 = new Point(0, scaledMat.size().height*(1-VIEW_FINDER_SCALE_W/CROP_RATIO)/2);
        Point p2 = new Point(scaledMat.size().width-p1.x, scaledMat.size().height-p1.y);
        Imgproc.rectangle(mask, p1, p2, new Scalar(255), -1);

        // Compute features and descriptors
        siftDetector.detectAndCompute(scaledMat, mask, inKeypoints, inDescriptor);

        // Break early if no features found
        if (inDescriptor.size().equals(new Size(0,0)) ||
                siftRefDescriptor.size().equals(new Size(0,0))) {
            inDescriptor.release();
            inKeypoints.release();
            scaledMat.release();
            mask.release();
            return boundary;
        }

        // Compute matches
        List<MatOfDMatch> matches = new ArrayList<>();
        siftMatcher.knnMatch(siftRefDescriptor, inDescriptor, matches, 2, new Mat(), false);

        // Save only the good matches
        ArrayList<DMatch> goodMatches = new ArrayList<>();
        for (int i = 0; i < matches.size(); i++) {
            DMatch[] dMatches = matches.get(i).toArray();
            if (dMatches.length >= 2) {
                DMatch m = dMatches[0];
                DMatch n = dMatches[1];
                if (m.distance <= 0.80 * n.distance) {
                    goodMatches.add(m);
                }
            }
        }
        MatOfDMatch goodMatchesMat = new MatOfDMatch();
        goodMatchesMat.fromList(goodMatches);

        // Break early if not enough good matches
        if (goodMatches.size() <= GOOD_MATCH_COUNT) {
            inDescriptor.release();
            inKeypoints.release();
            scaledMat.release();
            mask.release();
            goodMatchesMat.release();
            return boundary;
        }

        // Put KeyPoints from Mats into Lists
        List<KeyPoint> keypointsList1 = siftRefKeypoints.toList();
        List<KeyPoint> keypointsList2 = inKeypoints.toList();

        // Put Points from good matches into MatOfPoint2f
        List<Point> objList = new ArrayList<>();
        List<Point> sceneList = new ArrayList<>();
        for(int i = 0; i < goodMatches.size(); i++) {
            DMatch m = goodMatches.get(i);
            objList.add(keypointsList1.get(m.queryIdx).pt);
            sceneList.add(keypointsList2.get(m.trainIdx).pt);
        }
        MatOfPoint2f objMat = new MatOfPoint2f();
        MatOfPoint2f sceneMat = new MatOfPoint2f();
        objMat.fromList(objList);
        sceneMat.fromList(sceneList);

        // Compute homography
        Mat H = Calib3d.findHomography(objMat, sceneMat, Calib3d.RANSAC, ransac);

        // If the homography is valid, map corners of template into input image
        if (H.cols() >= 3 && H.rows() >= 3) {
            // Get template corners
            Mat objCorners = new Mat(4, 1, CvType.CV_32FC2);
            double[] a = new double[]{0, 0};
            double[] b = new double[]{mRefImg.cols() - 1, 0};
            double[] c = new double[]{mRefImg.cols() - 1, mRefImg.rows() - 1};
            double[] d = new double[]{0, mRefImg.rows() - 1};
            objCorners.put(0, 0, a);
            objCorners.put(1, 0, b);
            objCorners.put(2, 0, c);
            objCorners.put(3, 0, d);

            // Apply transform to get corresponding corners in input image
            Mat sceneCorners = new Mat(4, 1, CvType.CV_32FC2);
            perspectiveTransform(objCorners, sceneCorners, H);
            if (DEBUG_FLAG)
                Log.d(TAG, String.format("transformed -- SIFT: (%.2f, %.2f) (%.2f, %.2f) (%.2f, %.2f) (%.2f, %.2f), width: %d, height: %d",
                        sceneCorners.get(0, 0)[0], sceneCorners.get(0, 0)[1],
                        sceneCorners.get(1, 0)[0], sceneCorners.get(1, 0)[1],
                        sceneCorners.get(2, 0)[0], sceneCorners.get(2, 0)[1],
                        sceneCorners.get(3, 0)[0], sceneCorners.get(3, 0)[1], sceneCorners.width(), sceneCorners.height()));

            // Extract those points
            ArrayList<Point> listOfBoundary = new ArrayList<>();
            listOfBoundary.add(new Point(sceneCorners.get(0, 0)));
            listOfBoundary.add(new Point(sceneCorners.get(1, 0)));
            listOfBoundary.add(new Point(sceneCorners.get(2, 0)));
            listOfBoundary.add(new Point(sceneCorners.get(3, 0)));
            boundary.fromList(listOfBoundary);

            // Release resources
            objCorners.release();
            sceneCorners.release();

            // Properly orders the points depending on the orientation and
            // scales the points back to the original image's size
            RotatedRect rotatedRect = minAreaRect(boundary);
            Point[] v = new Point[4];
            Point[] bound = new Point[4];
            rotatedRect.points(v);
            for (int i = 0; i < 4; i++) {
                if (rotatedRect.angle < -45)
                    bound[(i+2) % 4] = new Point(v[i].x/scale, v[i].y/scale);
                else
                    bound[(i+3) % 4] = new Point(v[i].x/scale, v[i].y/scale);
            }
            boundary.fromArray(bound);
        }

        // Release resources
        inKeypoints.release();
        inDescriptor.release();
        scaledMat.release();
        mask.release();
        goodMatchesMat.release();
        objMat.release();
        sceneMat.release();
        H.release();
        if (DEBUG_FLAG)
            Log.d(TAG, "Detect RDT TIME: " + (System.currentTimeMillis()-startTime));
        return boundary;
    }

    /**
     * Generates a bounding box Rect based on the viewfinder window specifications
     * @param inputMat: the input image
     * @return a Rect object describing the viewfinder
     */
    private Rect getViewfinderRect(Mat inputMat) {
        Point p1 = new Point(inputMat.size().width*(1-VIEW_FINDER_SCALE_H)/2,
                inputMat.size().height*(1-VIEW_FINDER_SCALE_W)/2);
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
        Features2d.drawMatches(mRefImg, siftRefKeypoints, tempInputMat, inKeypoints, goodMatchesMat, resultMat);
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

            if (DEBUG_FLAG)
                Log.d(TAG, String.format("HLS at %d (%.2f, %.2f, %.2f) type %d", i,  avgHues[i]*2, avgIntensities[i]/255*100, avgSats[i]/255*100, inputMat.type()));
        }
    }

    // Mali-specific computations
    private boolean checkWindowPosition(Mat resultWindowMat) {
        Point line = new Point(resultWindowMat.width()*0.35-resultWindowMat.width()*0.15, 0);
        Rect roi = new Rect((int)(resultWindowMat.width()*0.15), (int)(resultWindowMat.height()*0.2), (int)(resultWindowMat.width()*0.7), (int)(resultWindowMat.height()*0.6));
        Mat crop = resultWindowMat.submat(roi);
        Mat cropgray = new Mat();
        cvtColor(crop, cropgray, Imgproc.COLOR_RGBA2RGB);
        cvtColor(cropgray, cropgray, Imgproc.COLOR_RGB2HSV);

        ArrayList<Mat> channels = new ArrayList<>();
        Core.split(cropgray, channels);

        Core.MinMaxLocResult result = Core.minMaxLoc(channels.get(2), null);

        if (DEBUG_FLAG) {
            Log.d(TAG, "MIN MAX LOC Rect: " + crop.size().width + ", " + cropgray.size().width);
            Log.d(TAG, "MIN MAX LOC Rect: " + crop.size().height + ", " + cropgray.size().height);
            Log.d(TAG, "MIN MAX LOC: " + result.minLoc + ", " + result.minVal + ", " + result.maxLoc + ", " + result.maxVal);
        }

        double minColSum = Double.MAX_VALUE;
        int minColIndex = -2;
        for (int i = -2; i < 1; i++) {
            double colSum = channels.get(2).get((int)result.minLoc.y, (int)result.minLoc.x+i)[0] +
                    channels.get(2).get((int)result.minLoc.y, (int)result.minLoc.x+i+1)[0] +
                    channels.get(2).get((int)result.minLoc.y, (int)result.minLoc.x+i+2)[0];

            if (DEBUG_FLAG)
                Log.d(TAG, String.format("MIN MAX explore: %d: %d, %d, %d", (int)result.minLoc.x+i, (int)channels.get(2).get((int)result.minLoc.y, (int)result.minLoc.x+i)[0],
                        (int)channels.get(2).get((int)result.minLoc.y, (int)result.minLoc.x+i+1)[0], (int)channels.get(2).get((int)result.minLoc.y, (int)result.minLoc.x+i+2)[0]));

            minColIndex = (colSum < minColSum) ? i : minColIndex;
            minColSum = (colSum < minColSum) ? colSum : minColSum;
        }

        double sum = 0;
        for (int i = minColIndex; i < minColIndex+3; i++) {
            for (int j = 0; j < channels.get(2).height(); j++) {
                if (DEBUG_FLAG)
                    Log.d(TAG, "MIN MAX coor: "+(result.minLoc.x+i)+","+j+", " +channels.get(2).get(j, (int)(result.minLoc.x+i))[0]);
                sum += channels.get(2).get(j, (int) (result.minLoc.x + i))[0];
            }
        }

        double avg = sum/(double)(channels.get(0).height()*3);

        if (DEBUG_FLAG) {
            Log.d(TAG, "MIN MAX Row Avg: " + avg + " And, MIN VAL: " + result.minVal);
            Log.d(TAG, "MIN MAX Row Loc: " + line + " And, MIN VAL: " + result.minLoc);
        }

        return (0 < avg && avg < result.minVal+30 && result.minLoc.x - 0.1*channels.get(2).width() < line.x && line.x < result.minLoc.x + 0.1*channels.get(2).width());
    }

    private Mat enhanceResultWindow(Mat input, MatOfPoint2f boundary) {
        Mat refPoints = new Mat(4, 1, CvType.CV_32FC2);
        Mat refResultPoints = new Mat(4, 1, CvType.CV_32FC2);

        double[] a = new double[]{0, 0};
        double[] b = new double[]{mRefImg.cols() - 1, 0};
        double[] c = new double[]{mRefImg.cols() - 1, mRefImg.rows() - 1};
        double[] d = new double[]{0, mRefImg.rows() - 1};

        //get corners from object
        refPoints.put(0, 0, a);
        refPoints.put(1, 0, b);
        refPoints.put(2, 0, c);
        refPoints.put(3, 0, d);

        a = new double[]{RESULT_WINDOW_X, RESULT_WINDOW_Y};
        b = new double[]{RESULT_WINDOW_X+RESULT_WINDOW_WIDTH, RESULT_WINDOW_Y};
        c = new double[]{RESULT_WINDOW_X+RESULT_WINDOW_WIDTH, RESULT_WINDOW_Y+RESULT_WINDOW_HEIGHT};
        d = new double[]{RESULT_WINDOW_X, RESULT_WINDOW_Y+RESULT_WINDOW_HEIGHT};

        refResultPoints.put(0, 0, a);
        refResultPoints.put(1, 0, b);
        refResultPoints.put(2, 0, c);
        refResultPoints.put(3, 0, d);

        Mat M = getPerspectiveTransform(refPoints, boundary);
        Mat imgResultPointsMat = new Mat();
        perspectiveTransform(refResultPoints, imgResultPointsMat, M);

        MatOfPoint imgResultPoints = new MatOfPoint();

        ArrayList<Point> points = new ArrayList<>();
        points.add(new Point(imgResultPointsMat.get(0, 0)));
        points.add(new Point(imgResultPointsMat.get(1, 0)));
        points.add(new Point(imgResultPointsMat.get(2, 0)));
        points.add(new Point(imgResultPointsMat.get(3, 0)));
        imgResultPoints.fromList(points);

        Point[] p = imgResultPoints.toArray();

        Rect resultRect = Imgproc.boundingRect(imgResultPoints);

        Mat resultImg = input.submat(resultRect);
        Mat enhancedImg = enhanceImage(resultImg, new org.opencv.core.Size(2, resultRect.height));
        enhancedImg = correctGamma(enhancedImg, 1.2);
        boolean windowPosition = checkWindowPosition(resultImg);

        if (windowPosition) {
            enhancedImg.copyTo(resultImg);
            return input;
        } else {
            return null;
        }
    }
}
