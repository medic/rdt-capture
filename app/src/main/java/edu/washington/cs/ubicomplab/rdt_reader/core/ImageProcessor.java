/*
 * Copyright (C) 2019 University of Washington Ubicomp Lab
 * All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of a BSD-style license that can be found in the LICENSE file.
 */

package edu.washington.cs.ubicomplab.rdt_reader.core;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
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
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Scalar;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.washington.cs.ubicomplab.rdt_reader.R;
import edu.washington.cs.ubicomplab.rdt_reader.utils.ImageUtil;

import static edu.washington.cs.ubicomplab.rdt_reader.core.Constants.*;
import static java.lang.Math.pow;
import static java.lang.StrictMath.abs;
import static org.opencv.core.Core.KMEANS_PP_CENTERS;
import static org.opencv.core.Core.addWeighted;
import static org.opencv.core.Core.countNonZero;
import static org.opencv.core.Core.inRange;
import static org.opencv.core.Core.kmeans;
import static org.opencv.core.Core.meanStdDev;
import static org.opencv.core.Core.perspectiveTransform;
import static org.opencv.core.CvType.CV_32F;
import static org.opencv.core.CvType.CV_8U;
import static org.opencv.core.CvType.CV_8UC3;
import static org.opencv.imgproc.Imgproc.COLOR_BGR2HLS;
import static org.opencv.imgproc.Imgproc.COLOR_RGB2GRAY;
import static org.opencv.imgproc.Imgproc.COLOR_RGBA2RGB;
import static org.opencv.imgproc.Imgproc.Laplacian;
import static org.opencv.imgproc.Imgproc.THRESH_BINARY_INV;
import static org.opencv.imgproc.Imgproc.createCLAHE;
import static org.opencv.imgproc.Imgproc.cvtColor;
import static org.opencv.imgproc.Imgproc.getPerspectiveTransform;
import static org.opencv.imgproc.Imgproc.minAreaRect;
import static org.opencv.imgproc.Imgproc.resize;
import static org.opencv.imgproc.Imgproc.warpPerspective;


public class ImageProcessor {
    // Debugging tag
    private static String TAG = "ImageProcessor";

    // Variable for singleton design pattern
    private static ImageProcessor instance = null;

    // Variable to hold metadata for target RDT
    public static RDT mRDT;

    // Variable to track
    private int mMoveCloserCount = 0;

    /**
     * An Enumeration object for specifying the exposure quality of the image
     * UNDER_EXPOSED: the image is too dark
     * NORMAL: the image has just the right amount of light
     * OVER_EXPOSED: the image is too bright
     */
    public enum ExposureResult {
        UNDER_EXPOSED, NORMAL, OVER_EXPOSED
    }

    /**
     * An Enumeration object for specifying whether the RDT has a reasonable scale in the image
     * SMALL: the RDT is too small in the image
     * RIGHT_SIZE: the RDT has just the right size in the image
     * LARGE: the RDT is too large in the image
     * INVALID: the RDT could not be found in the image
     */
    public enum SizeResult{
        SMALL, RIGHT_SIZE, LARGE, INVALID
    }

    /**
     * Constructor
     * @param activity: the activity that is using this code
     * @param rdtName: the name of the target RDT
     */
    public ImageProcessor(Activity activity, String rdtName) {
        // Start timer to track how long it takes to load the reference RDT (debug purposes only)
        long startTime = System.currentTimeMillis();

        // Loads the metadata related to the target RDT
        mRDT = new RDT(activity.getApplicationContext(), rdtName);

        // Calculates a baseline expected sharpness level for the target RDT
        // TODO: smarter place to put this?
        mRDT.refImgSharpness = measureSharpness(mRDT.refImg);

        Log.d(TAG, String.format("mRefImg sharpness: %.2f",  mRDT.refImgSharpness));
        Log.d(TAG, "RefImg Size: " + mRDT.refImg.size().toString());
        Log.d(TAG, "SIFT keypoints: " + mRDT.refKeypoints.toArray().length);
        Log.d(TAG, "REFERENCE LOAD/DETECT/COMPUTE: " + (System.currentTimeMillis() - startTime));
    }

    /**
     * Singleton creation method for this class
     * @param activity: the activity that is using this code
     * @param rdtName: the name of the target RDT
     * @return an instance of this class if one already exists, otherwise creates a new one
     */
    public static ImageProcessor getInstance(Activity activity, String rdtName) {
        if (instance == null)
            instance = new ImageProcessor(activity, rdtName);
        return instance;
    }

    /**
     * Singleton destruction method for this class
     */
    public static void destroy() {
        instance = null;
    }

    /**
     * Loads the OpenCV library so that those functions can be used
     * @param context: the app's context
     * @param mLoaderCallback: the callback that will be used once the library is loaded and the
     *                       camera's viewport is ready
     */
    public static void loadOpenCV(Context context, BaseLoaderCallback mLoaderCallback) {
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, context, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    /**
     * Returns the rectangle corresponding to the viewfinder that the user sees
     * (i.e., region-of-interest) for image quality
     * @param inputMat: the candidate video frame
     * @return the rectangle corresponding to the viewfinder
     */
    private Rect getViewfinderRect(Mat inputMat) {
        Point p1 = new Point(inputMat.size().width*(1-mRDT.viewFinderScaleH)/2,
                inputMat.size().height*(1-mRDT.viewFinderScaleW)/2);
        Point p2 = new Point(inputMat.size().width-p1.x, inputMat.size().height-p1.y);
        return new Rect(p1, p2);
    }

    /**
     * Processes the candidate video frame to see if it passes all of the quality checks
     * needed to ensure high likelihood of correct automatic analysis
     * @param inputMat: the candidate video frame
     * @param flashEnabled: whether the camera's flash is currently enabled
     * @return an {@link RDTCaptureResult} indicating which quality checks were passed
     */
    public RDTCaptureResult assessImage(Mat inputMat, boolean flashEnabled) {
        // Convert the image to grayscale
        Mat grayMat = new Mat();
        cvtColor(inputMat, grayMat, Imgproc.COLOR_RGBA2GRAY);
        Size inputSize = grayMat.size();

        // Check the brightness and the sharpness of the overall camera frame
        Rect viewFinderRect = getViewfinderRect(grayMat);
        ExposureResult exposureResult = checkExposure(grayMat);
        boolean isSharp = checkSharpness(grayMat.submat(viewFinderRect));
        boolean passed = exposureResult == ExposureResult.NORMAL && isSharp;

        // If the frame passes those two checks, continue to try to detect the RDT
        if (passed) {
            // Locate the RDT design within the camera frame
            MatOfPoint2f boundary = detectRDT(grayMat);
            grayMat.release();

            // Check the placement, size, and orientation of the RDT,
            // if it is there in the first place
            boolean isCentered = false;
            SizeResult sizeResult = SizeResult.INVALID;
            boolean isOriented = false;
            double angle = 0.0;
            if (boundary.size().width > 0 && boundary.size().height > 0) {
                isCentered = checkCentering(boundary, inputSize);
                sizeResult = checkSize(boundary, inputSize);
                isOriented = checkOrientation(boundary);
                angle = measureOrientation(boundary);
            }
            passed = isCentered && sizeResult == SizeResult.RIGHT_SIZE && isOriented;

            // Crop around the edges to reduce data size and speedup computation
            Mat croppedMat = ImageUtil.cropInputMat(inputMat, CROP_RATIO);
            MatOfPoint2f croppedBoundary = ImageUtil.adjustBoundary(inputMat, boundary, CROP_RATIO);
            
            // Check for glare
            boolean isGlared = false;
            if (passed)
                isGlared = checkGlare(croppedMat, croppedBoundary);
            passed = passed && !isGlared;

            // TODO: add blood checking here once verified
            return new RDTCaptureResult(passed, croppedMat, croppedBoundary, flashEnabled,
                    exposureResult, isSharp, isCentered, sizeResult,
                    isOriented, angle, isGlared, true);
        }
        else {
            grayMat.release();
            return new RDTCaptureResult(false, null, new MatOfPoint2f(), flashEnabled,
                    exposureResult, isSharp, false, SizeResult.INVALID,
                    false, 0.0, false, false);
        }
    }

    /**
     * Locates the RDT within the image (if one is presents) produces a bounding box around it
     * @param inputMat: the candidate video frame (in grayscale)
     * @return the corners of the bounding box around the detected RDT if it is present,
     * otherwise a blank MatOfPoint2f
     */
    private MatOfPoint2f detectRDT(Mat inputMat) {
        double currentTime = System.currentTimeMillis();

        // Resize inputMat for quicker computation
        double scale = SIFT_RESIZE_FACTOR;
        Mat scaledMat = new Mat();
        Imgproc.resize(inputMat, scaledMat, new Size(), scale, scale, Imgproc.INTER_LINEAR);

        // Create mask for region of interest
        Mat mask = new Mat(scaledMat.cols(), scaledMat.rows(), CV_8U, new Scalar(0));
        Point p1 = new Point(0, scaledMat.size().height*(1-mRDT.viewFinderScaleW/CROP_RATIO)/2);
        Point p2 = new Point(scaledMat.size().width-p1.x, scaledMat.size().height-p1.y);
        Imgproc.rectangle(mask, p1, p2, new Scalar(255), -1);

        // Identify SIFT features
        Mat inDescriptor = new Mat();
        MatOfKeyPoint inKeypoints = new MatOfKeyPoint();
        MatOfPoint2f boundary = new MatOfPoint2f();
        mRDT.detector.detectAndCompute(scaledMat, mask, inKeypoints, inDescriptor);

        // Skip if no features are found
        if (mRDT.refDescriptor.size().equals(new Size(0,0))) {
            Log.d(TAG, "No features found in reference");
            return boundary;
        }
        if (inDescriptor.size().equals(new Size(0,0))) {
            Log.d(TAG, "No features found in scene");
            return boundary;
        }

        // Match feature descriptors using KNN
        List<MatOfDMatch> matches = new ArrayList<>();
        mRDT.matcher.knnMatch(mRDT.refDescriptor, inDescriptor, matches,
                2, new Mat(), false);

        // Identify good matches based on nearest neighbor distance ratio test
        ArrayList<DMatch> goodMatches = new ArrayList<>();
        for (int i = 0; i < matches.size(); i++) {
            DMatch[] dMatches = matches.get(i).toArray();
            if (dMatches.length >= 2) {
                DMatch m = dMatches[0];
                DMatch n = dMatches[1];
                if (m.distance <= 0.80 * n.distance)
                    goodMatches.add(m);
            }
        }
        MatOfDMatch goodMatchesMat = new MatOfDMatch();
        goodMatchesMat.fromList(goodMatches);

        // If enough matches are found, calculate homography
        if (goodMatches.size() > GOOD_MATCH_COUNT) {
            // Extract features from reference and scene and put them into proper structure
            List<KeyPoint> keypointsList1 = mRDT.refKeypoints.toList();
            List<KeyPoint> keypointsList2 = inKeypoints.toList();
            List<Point> objList = new ArrayList<>();
            List<Point> sceneList = new ArrayList<>();
            for (int i=0; i<goodMatches.size(); i++) {
                objList.add(keypointsList1.get(goodMatches.get(i).queryIdx).pt);
                sceneList.add(keypointsList2.get(goodMatches.get(i).trainIdx).pt);
            }
            MatOfPoint2f objMat = new MatOfPoint2f();
            MatOfPoint2f sceneMat = new MatOfPoint2f();
            objMat.fromList(objList);
            sceneMat.fromList(sceneList);

            // Calculate homography matrix
            Mat H = Calib3d.findHomography(objMat, sceneMat, Calib3d.RANSAC, RANSAC);

            // Use homography matrix to find bounding box in scene if it is valid
            if (H.cols() >= 3 && H.rows() >= 3) {
                // Define corners of the reference image
                Mat objCorners = new Mat(4, 1, CvType.CV_32FC2);
                double[] a = new double[]{0, 0};
                double[] b = new double[]{mRDT.refImg.cols()-1, 0};
                double[] c = new double[]{mRDT.refImg.cols()-1, mRDT.refImg.rows()-1};
                double[] d = new double[]{0, mRDT.refImg.rows()-1};
                objCorners.put(0, 0, a);
                objCorners.put(1, 0, b);
                objCorners.put(2, 0, c);
                objCorners.put(3, 0, d);

                // Get the corresponding corners in the scene
                Mat sceneCorners = new Mat(4, 1, CvType.CV_32FC2);
                perspectiveTransform(objCorners, sceneCorners, H);

                // Extract corners for bounding box and put them in a MatOfPoint2f
                Point tlBoundary = new Point(sceneCorners.get(0, 0)[0]/scale,
                        sceneCorners.get(0, 0)[1]/scale);
                Point trBoundary = new Point(sceneCorners.get(1, 0)[0]/scale,
                        sceneCorners.get(1, 0)[1]/scale);
                Point brBoundary = new Point(sceneCorners.get(2, 0)[0]/scale,
                        sceneCorners.get(2, 0)[1]/scale);
                Point blBoundary = new Point(sceneCorners.get(3, 0)[0]/scale,
                        sceneCorners.get(3, 0)[1]/scale);
                Log.d(TAG, String.format("transformed -- " +
                                "(%.2f, %.2f) (%.2f, %.2f) (%.2f, %.2f) (%.2f, %.2f), " +
                                "width: %d, height: %d",
                        tlBoundary.x, tlBoundary.y, trBoundary.x, trBoundary.y,
                        brBoundary.x, brBoundary.y, blBoundary.x, blBoundary.y,
                        sceneCorners.width(), sceneCorners.height()));
                ArrayList<Point> listOfBoundary = new ArrayList<>();
                listOfBoundary.add(tlBoundary);
                listOfBoundary.add(trBoundary);
                listOfBoundary.add(brBoundary);
                listOfBoundary.add(blBoundary);
                boundary.fromList(listOfBoundary);
            }
            // Garbage collection
            H.release();
            objMat.release();
            sceneMat.release();
        }

        // Garbage collection
        scaledMat.release();
        goodMatchesMat.release();
        mask.release();
        inDescriptor.release();
        inKeypoints.release();
        Log.d(TAG, "Detect RDT time: " + (System.currentTimeMillis()-currentTime));
        return boundary;
    }

    /**
     * Calculates the brightness histogram of the candidate video frame
     * @param inputMat: the candidate video frame (in grayscale)
     * @return a 256-element histogram that quantifies the number of pixels at each
     * brightness level for the inputMat
     */
    private float[] measureExposure(Mat inputMat) {
        // Setup the histogram calculation
        int mHistSizeNum = 256;
        MatOfInt mHistSize = new MatOfInt(mHistSizeNum);
        Mat hist = new Mat();
        final float[] mBuff = new float[mHistSizeNum];
        final float[] mBuff2 = new float[mHistSizeNum];
        MatOfFloat histogramRanges = new MatOfFloat(0f, 256f);
        MatOfInt mChannels[] = new MatOfInt[] { new MatOfInt(0)};
        org.opencv.core.Size sizeRgba = inputMat.size();

        // Calculate the grayscale histogram
        Imgproc.calcHist(Arrays.asList(inputMat), mChannels[0], new Mat(), hist,
                mHistSize, histogramRanges);
        hist.get(0, 0, mBuff2);
        Core.divide(hist, new Scalar(sizeRgba.area()), hist);
        hist.get(0, 0, mBuff);
        mChannels[0].release();

        // Garbage collection
        mHistSize.release();
        histogramRanges.release();
        hist.release();
        return mBuff;
    }

    /**
     * Determines whether the candidate video frame has sufficient lighting without being too bright
     * @param inputMat: the candidate video frame (in grayscale)
     * @return ExposureResult enum for whether the candidate video frame has a reasonable brightness
     */
    private ExposureResult checkExposure(Mat inputMat) {
        // Calculate the brightness histogram
        float[] histograms = measureExposure(inputMat);

        // Identify the highest brightness level in the histogram
        // and the amount at the highest brightness
        int maxWhite = 0;
        float whiteCount = 0;
        for (int i = 0; i < histograms.length; i++) {
            if (histograms[i] > 0)
                maxWhite = i;
            if (i == histograms.length - 1)
                whiteCount = histograms[i];
        }

        // Assess the brightness relative to thresholds
        if (maxWhite >= OVER_EXPOSURE_THRESHOLD && whiteCount > OVER_EXPOSURE_WHITE_COUNT) {
            return ExposureResult.OVER_EXPOSED;
        } else if (maxWhite < UNDER_EXPOSURE_THRESHOLD) {
            return ExposureResult.UNDER_EXPOSED;
        } else {
            return ExposureResult.NORMAL;
        }
    }

    /**
     * Calculates the Laplacian variance of the candidate video frame as a metric for sharpness
     * @param inputMat: the candidate video frame (in grayscale)
     * @return the Laplacian variance of the candidate video frame
     */
    private double measureSharpness(Mat inputMat) {
        // Calculate the Laplacian
        Mat des = new Mat();
        Laplacian(inputMat, des, CvType.CV_64F);

        // Calculate the mean and std
        MatOfDouble mean = new MatOfDouble();
        MatOfDouble std = new MatOfDouble();
        meanStdDev(des, mean, std);

        // Calculate variance
        double sharpness = pow(std.get(0,0)[0], 2);

        // Garbage collection
        des.release();
        return sharpness;
    }

    /**
     * Determines whether the candidate video frame is focused
     * @param inputMat: the candidate video frame (in grayscale)
     * @return whether the candidate video frame has a reasonable sharpness
     */
    private boolean checkSharpness(Mat inputMat) {
        // Resize the image to the scale of the reference
        Mat resized = new Mat();
        double scale = mRDT.refImg.size().width/inputMat.size().width;
        resize(inputMat, resized,
                new Size(inputMat.size().width*scale,inputMat.size().height*scale));

        // Calculate sharpness and assess relative to thresholds
        double sharpness = measureSharpness(resized);
        boolean isSharp = sharpness > (mRDT.refImgSharpness * (1-SHARPNESS_THRESHOLD));

        // Garbage collection
        inputMat.release();
        resized.release();
        return isSharp;
    }

    /**
     * Identifies the center of the detected RDT
     * @param boundary: the corners of the bounding box around the detected RDT
     * @return the (x, y) coordinate corresponding to the center of the RDT
     */
    private Point measureCentering (MatOfPoint2f boundary) {
        RotatedRect rotatedRect = minAreaRect(boundary);
        return rotatedRect.center;
    }

    /**
     * Determines whether the detected RDT is close enough to the
     * center of the candidate video frame
     * @param boundary: the corners of the bounding box around the detected RDT
     * @param size: the size of the candidate video frame
     * @return whether the boundary of the detected RDT is close enough to the center of the
     * screen for consistent interpretation
     */
    private boolean checkCentering(MatOfPoint2f boundary, Size size) {
        // Calculate the center of the detected RDT
        Point center = measureCentering(boundary);

        // Calculate the center of the screen
        Point trueCenter = new Point(size.width/2, size.height/2);

        // Assess centering relative to thresholds
        double lowerXThreshold = trueCenter.x - (size.width*POSITION_THRESHOLD);
        double upperXThreshold = trueCenter.x + (size.width*POSITION_THRESHOLD);
        double lowerYThreshold = trueCenter.y - (size.height*POSITION_THRESHOLD);
        double upperYThreshold = trueCenter.y + (size.height*POSITION_THRESHOLD);
        return center.x > lowerXThreshold && center.x < upperXThreshold &&
                center.y > lowerYThreshold && center.y < upperYThreshold;
    }

    /**
     * Measures the desired dimension of the bounding box around the detected RDT
     * @param boundary: the corners of the bounding box around the detected RDT
     * @param isHeight: whether the output should be the height (true) or width (false)
     * @return the desired dimension in pixels
     */
    private double measureSize(MatOfPoint2f boundary, boolean isHeight) {
        // Calculate a non-skew rectangle around the boundary
        RotatedRect rotatedRect = minAreaRect(boundary);

        // Pick the height and width relative to camera perspective according to angle
        boolean isUpright = rotatedRect.size.height > rotatedRect.size.width;
        double height, width;
        if (isUpright) {
            height = rotatedRect.size.height;
            width = rotatedRect.size.width;
        } else {
            height = rotatedRect.size.width;
            width = rotatedRect.size.height;
        }

        // Return the desired dimension
        return isHeight ? height : width;
    }

    /**
     * Determines whether the detected RDT is a reasonable size within the camera frame
     * @param boundary: the corners of the bounding box around the detected RDT
     * @param size: the size of the candidate video frame
     * @return whether the boundary of the detected RDT is has a reasonable size
     * for consistent interpretation
     */
    private SizeResult checkSize(MatOfPoint2f boundary, Size size) {
        // Get the height of the bounding box
        double height = measureSize(boundary, true);

        // Calculate quality bounds relative to the viewfinder
        double lowerBound = size.width * (mRDT.viewFinderScaleH - SIZE_THRESHOLD);
        double upperBound = size.width * (mRDT.viewFinderScaleH + SIZE_THRESHOLD);

        // Assess size relative to thresholds
        if (height < lowerBound)
            return SizeResult.SMALL;
        else if (height > upperBound)
            return SizeResult.LARGE;
        else if (height > lowerBound && height < upperBound)
            return SizeResult.RIGHT_SIZE;
        else
            return SizeResult.INVALID;
    }

    /**
     * Measures the orientation of the RDT relative to the camera's perspective
     * (assumes vertical RDT where height > width)
     * @param boundary: the corners of the bounding box around the detected RDT
     * @return the orientation of the RDT's vertical axis relative to the vertical axis of
     * the video frame (0° = upright, 90° = right-to-left, 180° = upside-down, 270° = left-to-right)
     */
    private double measureOrientation(MatOfPoint2f boundary) {
        // Calculate a non-skew rectangle around the boundary
        RotatedRect rotatedRect = minAreaRect(boundary);

        // Correct orientation so that it is relative to camera perspective
        boolean isUpright = rotatedRect.size.height > rotatedRect.size.width;
        if (isUpright) {
            if (rotatedRect.angle < 0)
                return 90 + rotatedRect.angle;
            else
                return rotatedRect.angle - 90;
        } else {
            return rotatedRect.angle;
        }
    }

    /**
     * Determines whether the detected RDT is a reasonable orientation within the camera frame
     * @param boundary: the corners of the bounding box around the detected RDT
     * @return whether the `boundary` of the detected RDT has a reasonable orientation for
     * consistent interpretation
     */
    private boolean checkOrientation(MatOfPoint2f boundary) {
        double angle = measureOrientation(boundary);
        return abs(angle) < ANGLE_THRESHOLD;
    }

    /**
     * Determines if there is glare within the detected RDT's result window (often due to
     * protective covering of the immunoassay)
     * @param inputMat: the candidate video frame (in grayscale)
     * @param boundary: the corners of the bounding box around the detected RDT
     * @return whether there is glare within the detected RDT's result window
     */
    private boolean checkGlare(Mat inputMat, MatOfPoint2f boundary) {
        // Crop the image around the RDT's result window
        Mat resultWindowMat = cropResultWindow(inputMat, boundary);

        if (resultWindowMat.height() == 0 || resultWindowMat.width() == 0)
            return true;

        // Convert the image to HLS
        Mat hls = new Mat();
        cvtColor(resultWindowMat, hls, COLOR_BGR2HLS);

        // Calculate brightness histogram across L channel
        ArrayList<Mat> channels = new ArrayList<>();
        Core.split(hls, channels);
        float[] histograms = measureExposure(channels.get(1));

        // Identify the highest brightness level in the histogram
        // and the amount at the highest brightness
        int maxWhite = 0;
        float clippingCount = 0;
        for (int i = 0; i < histograms.length; i++) {
            if (histograms[i] > 0)
                maxWhite = i;
            if (i == histograms.length - 1)
                clippingCount = histograms[i];
        }
        Log.d(TAG, String.format("maxWhite: %d, clippingCount: %.5f", maxWhite, clippingCount));

        // Assess glare relative to thresholds
        return maxWhite == 255 && clippingCount > GLARE_WHITE_COUNT;
    }

    /**
     * Determines if there is blood within the detected RDT's result window
     * @param inputMat: the candidate video frame (in grayscale)
     * @param boundary: the corners of the bounding box around the detected RDT
     * @return whether there is blood within the detected RDT's result window
     */
    public boolean checkBlood(Mat inputMat, MatOfPoint2f boundary) {
        // Crop the image around the RDT's result window
        Mat resultWindowMat = cropResultWindow(inputMat, boundary);

        if (resultWindowMat.height() == 0 || resultWindowMat.width() == 0)
            return true;

        // Convert the image to HSV
        Mat hsv = new Mat();
        cvtColor(resultWindowMat, hsv, Imgproc.COLOR_RGB2HSV);

        // Filter image according to two definitions of red
        // (note: H in HSV is circular, so red can have low and high H values)
        Mat lowerRedThresh = new Mat();
        Mat upperRedThresh = new Mat();
        Mat redThresh = new Mat();
        inRange(hsv, BLOOD_COLOR_LOW_HUE_LOWER, BLOOD_COLOR_LOW_HUE_UPPER, lowerRedThresh);
        inRange(hsv, BLOOD_COLOR_HIGH_HUE_LOWER, BLOOD_COLOR_HIGH_HUE_UPPER, upperRedThresh);
        addWeighted(lowerRedThresh, 1.0, upperRedThresh, 1.0, 0.0,  redThresh);

        // Determine if there is too much blood for analysis
        double bloodPercentage = countNonZero(redThresh) / redThresh.size().area();
        return bloodPercentage > BLOOD_PERCENTAGE_THRESHOLD;
    }

    /**
     * Generate the most logical instruction to help the user fix a single quality check
     * @param isCentered: whether the boundary of the detected RDT is sufficiently in the
     *                  middle of the screen for consistent interpretation
     * @param sizeResult: whether the boundary of the detected RDT has a reasonable size
     *                  for consistent interpretation
     * @param isOriented: whether the boundary of the detected RDT has a reasonable orientation
     *                  for consistent interpretation
     * @param isGlared: whether there is glare within the detected RDT's result window
     * @return the ID of the instruction text to be found in res/values/strings.xml
     */
    public int getInstructionText(boolean isCentered, SizeResult sizeResult,
                                  boolean isOriented, boolean isGlared) {
        int instructions = R.string.instruction_pos;

        if (isGlared) {
            instructions = R.string.instruction_glare;
        } else if (sizeResult == SizeResult.RIGHT_SIZE && isCentered && isOriented) {
            instructions = R.string.instruction_detected;
        } else if (sizeResult == SizeResult.SMALL) {
            if (mMoveCloserCount <= MOVE_CLOSER_COUNT) {
                mMoveCloserCount++;
                instructions = R.string.instruction_too_small;
            }
            else {
                mMoveCloserCount = 0;
                instructions = R.string.instruction_pos;
            }
        } else if (sizeResult == SizeResult.LARGE) {
            instructions = R.string.instruction_too_large;
        }

        return instructions;
    }

    /**
     * Generate text that can be shown on the screen to summarize all quality checks
     * @param exposureResult: whether the candidate video frame has a reasonable brightness
     * @param isSharp: whether the candidate video frame has a reasonable sharpness
     * @param isCentered: whether the boundary of the detected RDT is sufficiently in the
     *                  middle of the screen for consistent interpretation
     * @param sizeResult: whether the boundary of the detected RDT has a reasonable size
     *                  for consistent interpretation
     * @param isOriented: whether the boundary of the detected RDT has a reasonable orientation
     *                  for consistent interpretation
     * @param isGlared: whether there is glare within the detected RDT's result window
     * @return a String[] that summarizes each quality checking component
     */
    public String[] getSummaryText(ExposureResult exposureResult, boolean isSharp, boolean isCentered,
                                   SizeResult sizeResult, boolean isOriented, boolean isGlared) {
        String[] texts = new String[5];
        String checkSymbol = "&#x2713; ";

        // Exposure text
        if (exposureResult == ExposureResult.NORMAL) {
            texts[0] = checkSymbol + "Exposure: passed";
        } else if (exposureResult == ExposureResult.OVER_EXPOSED) {
            texts[0] = "Exposure: too bright";
        } else if (exposureResult == ExposureResult.UNDER_EXPOSED) {
            texts[0] = "Exposure: too dark";
        }

        // Sharpness text
        texts[1] = isSharp ? checkSymbol + "Sharpness: passed": "Sharpness: failed";

        // Size/position/orientation text
        boolean rdtFramingCheck = sizeResult == SizeResult.RIGHT_SIZE &&
                isCentered && isOriented;
        texts[2] = rdtFramingCheck ? checkSymbol + "Position/Size: passed": "Position/Size: failed";

        // Glare text
        texts[3] = isGlared ? checkSymbol + "No glare: passed" : "No glare: failed";

        // Shadow text
        texts[4] = checkSymbol + "Shadow: passed";
        return texts;
    }

    /**
     * Crops out the detected RDT's result window as a rectangle
     * @param inputMat: the candidate video frame (in grayscale)
     * @param boundary: the corners of the bounding box around the detected RDT
     * @return the RDT image tightly cropped and de-skewed around the result window
     */
    private Mat cropResultWindow(Mat inputMat, MatOfPoint2f boundary) {
        // Get the corners of the reference RDT image
        Mat refBoundary = new Mat(4, 1, CvType.CV_32FC2);
        double[] a = new double[]{0, 0};
        double[] b = new double[]{mRDT.refImg.cols() - 1, 0};
        double[] c = new double[]{mRDT.refImg.cols() - 1, mRDT.refImg.rows() - 1};
        double[] d = new double[]{0, mRDT.refImg.rows() - 1};
        refBoundary.put(0, 0, a);
        refBoundary.put(1, 0, b);
        refBoundary.put(2, 0, c);
        refBoundary.put(3, 0, d);

        // Calculate the perspective transformation matrix that maps the corners of the
        // detected RDT to the corners of the reference image
        Mat M = getPerspectiveTransform(boundary, refBoundary);

        // Apply perspective correction to the RDT in the video frame
        Mat correctedMat = new Mat(mRDT.refImg.rows(), mRDT.refImg.cols(), mRDT.refImg.type());
        warpPerspective(inputMat, correctedMat, M, new Size(mRDT.refImg.cols(), mRDT.refImg.rows()));

        // If fiducials are specified, use them to improve the estimate of the
        // result window's location, otherwise use the default rectangle specified by the user
        Rect resultWindowRect = mRDT.hasFiducial ?
                cropResultWindowWithFidicual(correctedMat) : mRDT.resultWindowRect;

        if (resultWindowRect.width == 0 || resultWindowRect.height == 0)
            return new Mat();

        Log.d(TAG, String.format("result rect: %d, %d, %d, %d", resultWindowRect.x, resultWindowRect.y, resultWindowRect.width, resultWindowRect.height));
        // Resize the window so it's the same size as in the template
        correctedMat = new Mat(correctedMat, resultWindowRect);
        if (correctedMat.width() > 0 && correctedMat.height() > 0)
            resize(correctedMat, correctedMat,
                    new Size(mRDT.resultWindowRect.width, mRDT.resultWindowRect.height));
        return correctedMat;
    }

    /**
     * Uses color clustering to identify explicit 'fiducials' (densely colored markers) on the
     * detected RDT that can be used as reference points for locating the result window
     * @param inputMat: the candidate video frame (in RGBA and de-skewed)
     * @return the RDT image tightly cropped and de-skewed around the result window
     */
    private Rect cropResultWindowWithFidicual(Mat inputMat) {
        // Flatten the input data
        Mat data = new Mat();
        inputMat.convertTo(data, CV_32F);
        cvtColor(data, data, COLOR_RGBA2RGB);
        data = data.reshape(1, (int) data.total());

        // Run k-means clustering
        Mat centers = new Mat();
        Mat labels = new Mat();
        TermCriteria criteria = new TermCriteria(TermCriteria.EPS+TermCriteria.MAX_ITER,
                100, 1.0);
        kmeans(data, FIDUCIAL_SEARCH_NUM_CLUSTERS, labels, criteria,
                10, KMEANS_PP_CENTERS, centers);

        // Extract output of k-means clustering
        centers = centers.reshape(3, centers.rows());
        data = data.reshape(3, data.rows());
        for (int i=0; i<data.rows(); i++) {
            int centerId = (int) labels.get(i,0)[0];
            data.put(i, 0, centers.get(centerId,0));
        }
        data = data.reshape(3, inputMat.rows());
        data.convertTo(data, CV_8UC3);

        // Identify the darkest cluster
        double minCenterBrightness = Double.MAX_VALUE;
        for (int i=0; i < centers.rows(); i++) {
            double[] center = centers.get(i, 0);
            double yval = ImageUtil.rgbToY(center);
            if (yval < minCenterBrightness)
                minCenterBrightness = yval;
        }

        // Threshold the image based on the darkest cluster's brightness
        cvtColor(data, data, COLOR_RGB2GRAY);
        Mat threshold = new Mat();
        Imgproc.threshold(data, threshold, minCenterBrightness, 255, THRESH_BINARY_INV);

        // Smooth the binary mask
        Mat element_erode = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
        Mat element_dilate = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(20, 20));
        Imgproc.erode(threshold, threshold, element_erode);
        Imgproc.dilate(threshold, threshold, element_dilate);
        Imgproc.GaussianBlur(threshold, threshold, new Size(5, 5), 2, 2);

        // Identify contours
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(threshold, contours, hierarchy, Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE, new Point(0, 0));

        // Find contours that correspond to fiducials specified by the user
        List<Rect> fiducialRects = new ArrayList<>();
        for (int i = 0; i < contours.size(); i++) {
            Rect rect = Imgproc.boundingRect(contours.get(i));
            double rectPos = rect.x + rect.width;

            for (Rect trueFiducialRect: mRDT.fiducialRects) {
                if (trueFiducialRect.x + trueFiducialRect.width - Constants.FIDUCIAL_THRESHOLD < rectPos && rectPos < trueFiducialRect.x + trueFiducialRect.width + Constants.FIDUCIAL_THRESHOLD &&
                        trueFiducialRect.height - Constants.FIDUCIAL_THRESHOLD < rect.height &&
                        trueFiducialRect.width - Constants.FIDUCIAL_THRESHOLD < rect.width && rect.width < trueFiducialRect.width + Constants.FIDUCIAL_THRESHOLD) {
                    fiducialRects.add(rect);
                }
            }
        }

        // If the correct number of fiducials was found,
        // find the position of the result window relative to them
        Rect resultWindowMat = new Rect(0, 0, 0, 0);
        if (fiducialRects.size() == mRDT.fiducials.length()) {
            // Find the average fiducial position
            double rectBR0 = fiducialRects.get(0).x + fiducialRects.get(0).width;
            double rectBR1 = fiducialRects.get(0).x + fiducialRects.get(0).width;
            if (fiducialRects.size() > 1)
                rectBR1 = fiducialRects.get(1).x + fiducialRects.get(1).width;

            int midpoint = (int) ((rectBR0 + rectBR1) / 2);

            // Locate the result window relative the fiducials
            Point tl = new Point(midpoint + mRDT.distanctFromFiducialToResultWindow,
                    mRDT.resultWindowRect.y);
            Point br = new Point(midpoint + mRDT.distanctFromFiducialToResultWindow + mRDT.resultWindowRect.width,
                    mRDT.resultWindowRect.y+mRDT.resultWindowRect.height);
            resultWindowMat = new Rect(tl, br);
        }

        // Garbage collection
        labels.release();
        centers.release();
        data.release();
        threshold.release();
        element_erode.release();
        element_dilate.release();
        return resultWindowMat;
    }

    /**
     * Applies CLAHE (https://en.wikipedia.org/wiki/Adaptive_histogram_equalization)
     * to enhance faint marks on the RDT's result window
     * @param resultWindowMat: the RDT's result window (in RGBA)
     * @return a contrast-enhanced version of the RDT's result window
     */
    private Mat enhanceResultWindow(Mat resultWindowMat) {
        // Initialize the parameters for CLAHE
        Size tile = new Size(CLAHE_WIDTH, resultWindowMat.cols());
        CLAHE clahe = createCLAHE(CLAHE_CLIP_LIMIT, tile);

        // Convert the image to HLS
        Mat enhancedMat = new Mat();
        cvtColor(resultWindowMat, enhancedMat, Imgproc.COLOR_RGBA2RGB);
        cvtColor(enhancedMat, enhancedMat, Imgproc.COLOR_RGB2HLS);

        // Split the channels
        ArrayList<Mat> channels = new ArrayList<>();
        Core.split(enhancedMat, channels);

        // Apply CLAHE to L channel
        Mat newChannel = new Mat();
        Core.normalize(channels.get(1), channels.get(1), 0, 255, Core.NORM_MINMAX);
        clahe.apply(channels.get(1), newChannel);
        channels.set(1, newChannel);
        Core.merge(channels, enhancedMat);

        // Convert the image back to RGBA
        cvtColor(enhancedMat, enhancedMat, Imgproc.COLOR_HLS2RGB);
        cvtColor(enhancedMat, enhancedMat, Imgproc.COLOR_RGB2RGBA);
        return enhancedMat;
    }

    /**
     * Interprets any lines that appear within the detected RDT's result window
     * @param inputMat: the candidate video frame
     * @param boundary: the corners of the bounding box around the detected RDT
     * @return an {@link RDTInterpretationResult} indicating the test results
     */
    public RDTInterpretationResult interpretRDT(Mat inputMat, MatOfPoint2f boundary) {
        // Crop the result window
        Mat resultWindowMat = cropResultWindow(inputMat, boundary);

        // Skip if there is no window to interpret
        if (resultWindowMat.width() == 0 && resultWindowMat.height() == 0)
            return new RDTInterpretationResult(resultWindowMat,
                    false, false, false,
                    mRDT.topLineName, mRDT.middleLineName, mRDT.bottomLineName);

        // Convert the result window to grayscale
        Mat grayMat = new Mat();
        cvtColor(resultWindowMat, grayMat, COLOR_RGB2GRAY);

        // Compute variance within the window
        MatOfDouble mu = new MatOfDouble();
        MatOfDouble sigma = new MatOfDouble();
        Core.meanStdDev(grayMat, mu, sigma);
        Core.MinMaxLocResult minMaxLocResult = Core.minMaxLoc(grayMat);
        Log.d(TAG, String.format("stdev %.2f, minval %.2f at %s, maxval %.2f at %s",
                sigma.get(0,0)[0],
                minMaxLocResult.minVal, minMaxLocResult.minLoc,
                minMaxLocResult.maxVal, minMaxLocResult.maxLoc));

        // Enhance the result window if there is something worth enhancing in the first place
        if (sigma.get(0,0)[0] > RESULT_WINDOW_ENHANCE_THRESHOLD)
            resultWindowMat = enhanceResultWindow(resultWindowMat);

        // Detect the lines in the result window
        // Convert the image to HLS
        Mat hls = new Mat();
        cvtColor(resultWindowMat, hls, COLOR_BGR2HLS);

        // Split the channels
        List<Mat> channels = new ArrayList<>();
        Core.split(hls, channels);

        // Compute the average intensity for each column of the result window
        Mat lightness = channels.get(1);
        double[] avgIntensities = new double[lightness.cols()];
        for (int i = 0; i < lightness.cols(); i++) {
            avgIntensities[i] = 0;
            for (int j = 0; j < lightness.rows(); j++)
                avgIntensities[i] += lightness.get(j, i)[0];
            avgIntensities[i] /= lightness.rows();
        }

        // Detect the peaks
        ArrayList<double[]> peaks = ImageUtil.detectPeaks(avgIntensities, mRDT.lineIntensity, false);
        for (double[] p : peaks)
            Log.d(TAG, String.format("%.2f, %.2f, %.2f", p[0], p[1], p[2]));

        // Determine which peaks correspond to which lines
        boolean topLine = false;
        boolean middleLine = false;
        boolean bottomLine = false;
        for (double[] p : peaks) {
            if (Math.abs(p[0] - mRDT.topLinePosition) < mRDT.lineSearchWidth) {
                topLine = true;
            } else if (Math.abs(p[0] - mRDT.middleLinePosition) < mRDT.lineSearchWidth) {
                middleLine = true;
            } else if (Math.abs(p[0] - mRDT.bottomLinePosition) < mRDT.lineSearchWidth) {
                bottomLine = true;
            }
        }

        return new RDTInterpretationResult(resultWindowMat,
                topLine, middleLine, bottomLine,
                mRDT.topLineName, mRDT.middleLineName, mRDT.topLineName);
    }
}
