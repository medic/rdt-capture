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
import org.opencv.features2d.BFMatcher;
import org.opencv.features2d.BRISK;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Scalar;
import org.opencv.android.OpenCVLoader;
import org.opencv.xfeatures2d.SIFT;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;

import static edu.washington.cs.ubicomplab.rdt_reader.Constants.*;
import static java.lang.Math.pow;
import static java.lang.StrictMath.abs;
import static org.opencv.core.Core.LUT;
import static org.opencv.core.Core.meanStdDev;
import static org.opencv.core.Core.perspectiveTransform;
import static org.opencv.imgproc.Imgproc.Laplacian;
import static org.opencv.imgproc.Imgproc.contourArea;
import static org.opencv.imgproc.Imgproc.createCLAHE;
import static org.opencv.imgproc.Imgproc.cvtColor;
import static org.opencv.imgproc.Imgproc.getPerspectiveTransform;
import static org.opencv.imgproc.Imgproc.minAreaRect;
import static org.opencv.imgproc.Imgproc.warpPerspective;


/**
 * Created by cjparkuw on 2/27/2019.
 */

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


    private double SHARPNESS_THRESHOLD = 0.0;
    private float OVER_EXP_THRESHOLD = 255;
    private float UNDER_EXP_THRESHOLD = 120;
    private float OVER_EXP_WHITE_COUNT = 100;
    //private double SIZE_THRESHOLD = 0.3;
    //private double POSITION_THRESHOLD = 0.2;
    //private double VIEWPORT_SCALE = 0.50;
    private double minSharpness = Double.MIN_VALUE;
    private int MOVE_CLOSER_COUNT = 5;
    //private double CROP_RATIO = 0.6;

    private int mMoveCloserCount = 0;


    public enum ExposureResult {
        UNDER_EXPOSED, NORMAL, OVER_EXPOSED
    }

    public enum SizeResult{
        RIGHT_SIZE, LARGE, SMALL, INVALID

    }

    public class CaptureResult {
        boolean allChecksPassed;
        Mat resultMat;
        double matchDistance;
        ExposureResult exposureResult;
        SizeResult sizeResult;
        boolean isCentered;
        boolean isRightOrientation;
        boolean isSharp;
        boolean isShadow;
        double angle;

        public CaptureResult(boolean allChecksPassed, Mat resultMat, double matchDistance,
                             ExposureResult exposureResult, SizeResult sizeResult,  boolean isCentered,
                             boolean isRightOrientation, double angle, boolean isSharp, boolean isShadow){
            this.allChecksPassed = allChecksPassed;
            this.resultMat = resultMat;
            this.matchDistance = matchDistance;
            this.exposureResult = exposureResult;
            this.sizeResult = sizeResult;
            this.isCentered = isCentered;
            this.isRightOrientation = isRightOrientation;
            this.isSharp = isSharp;
            this.isShadow = isShadow;
            this.angle = angle;
        }
    }

    public class InterpretationResult {
        boolean control;
        boolean testA;
        boolean testB;
        Mat resultMat;
        Bitmap resultBitmap;

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
            this.resultBitmap = Bitmap.createBitmap(resultMat.cols(), resultMat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(resultMat, resultBitmap);
        }
    }


    public ImageProcessor (Activity activity) {
        mFeatureDetector = BRISK.create(45, 4, 1.0f);
        mMatcher = BFMatcher.create(BFMatcher.BRUTEFORCE_HAMMING, true);
        mRefImg = new Mat();

        //Load reference image for Quickvue flu test strip
        Bitmap bitmap = BitmapFactory.decodeResource(activity.getApplicationContext().getResources(), R.drawable.quickvue_ref_evernote);
        //Load reference image for SD Bioline Malaria RDT
        //Bitmap bitmap = BitmapFactory.decodeResource(activity.getApplicationContext().getResources(), R.drawable.sd_bioline_malaria_ag_pf);
        Utils.bitmapToMat(bitmap, mRefImg);
        cvtColor(mRefImg, mRefImg, Imgproc.COLOR_RGB2GRAY);
        mRefDescriptor = new Mat();

        mRefKeypoints = new MatOfKeyPoint();
        long startTime = System.currentTimeMillis();
        mFeatureDetector.detect(mRefImg, mRefKeypoints);
        mFeatureDetector.compute(mRefImg, mRefKeypoints, mRefDescriptor);

        siftDetector = SIFT.create();
        siftMatcher = BFMatcher.create(BFMatcher.BRUTEFORCE, false);
        siftDetector.detect(mRefImg, siftRefKeypoints);
        siftDetector.compute(mRefImg, siftRefKeypoints, siftRefDescriptor);
        //siftDetector.detectAndCompute(mRefImg, new Mat(), siftRefKeypoints, siftRefDescriptor);

        Log.d(TAG, "REFERENCE LOAD/DETECT/COMPUTE: " + (System.currentTimeMillis() - startTime));
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

    public void configureCamera() {

    }


    public CaptureResult captureRDT(Mat inputMat) {
        Mat greyMat = new Mat();
        cvtColor(inputMat, greyMat, Imgproc.COLOR_BGRA2GRAY);
        double matchDistance = -1.0;
        boolean passed = false;

        //check brightness (refactored)
        ExposureResult exposureResult = (checkBrightness(greyMat));

        //check sharpness (refactored)
        boolean isSharp = checkSharpness(greyMat);

        //preform detectRDT only if those two quality checks are passed
        if (exposureResult == ExposureResult.NORMAL && isSharp) {

            MatOfPoint2f boundary = new MatOfPoint2f();
            boundary = detectRDT(greyMat);
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


            return new CaptureResult(passed, cropRDT(inputMat), matchDistance, exposureResult, sizeResult, isCentered, isRightOrientation, angle, isSharp, false);
    }
        else {
            return new CaptureResult(passed, null, matchDistance, exposureResult, SizeResult.INVALID, false, false, 0.0, isSharp, false);
        }

    }

    private MatOfPoint2f detectRDT(Mat inputMat) {
        long veryStart = System.currentTimeMillis();
        MatOfPoint2f boundary = new MatOfPoint2f();

        //Imgproc.GaussianBlur(input, input, new org.opencv.core.Size(3,3), 2, 2);

        Mat descriptors = new Mat();
        MatOfKeyPoint keypoints = new MatOfKeyPoint();

        long startTime = System.currentTimeMillis();
        Mat mask = new Mat(inputMat.size(), CvType.CV_8U, Scalar.all(0));
        Imgproc.rectangle(mask, new Point(inputMat.width()/5, inputMat.height()/5), new Point(inputMat.width()-inputMat.width()/5, inputMat.height()-inputMat.height()/5), Scalar.all(255), -1);

        mFeatureDetector.detectAndCompute(inputMat, mask, keypoints, descriptors);
        Log.d(TAG, "detect/compute TIME: " + (System.currentTimeMillis()-startTime));

        if (descriptors.size().equals(new Size(0,0))) {
            Log.d(TAG, String.format("no features on input"));
            return boundary;
        }

        // Matching
        MatOfDMatch matches = new MatOfDMatch();
        mMatcher.match(mRefDescriptor, descriptors, matches);

//        if (mRefImg.type() == input.type()) {
//            try {
//                Log.d(TAG, String.format("type: %d, %d", mRefDescriptor.type(), descriptors.type()));
//                mMatcher.match(mRefDescriptor, descriptors, matches);
//                Log.d(TAG, String.format("matched"));
//            } catch (Exception e) {
//                return boundary;
//            }
//        } else {
//            return boundary;
//        }

        List<DMatch> matchesList = matches.toList();
        Log.d(TAG, "matching TIME: " + (System.currentTimeMillis()-veryStart));
        Comparator<DMatch> comparator = new Comparator<DMatch>() {
            @Override
            public int compare(DMatch dMatch, DMatch t1) {
                if (dMatch.distance == t1.distance)
                    return 0;
                else if (dMatch.distance > t1.distance) //reverse order
                    return -1;
                //else if (dMatch.distance < t1.distance) //reverse order
                else
                    return 1;
            }
        };

        Collections.sort(matchesList, comparator);

//        Double max_dist = Double.MIN_VALUE;
//        Double min_dist = Double.MAX_VALUE;
//
//        for (int i = 0; i < matchesList.size(); i++) {
//            Double dist = (double) matchesList.get(i).distance;
//            if (dist < min_dist)
//                min_dist = dist;
//            if (dist > max_dist)
//                max_dist = dist;
//        }
//
//        Log.d(TAG, "DISTANCE Min dist: " + min_dist + "Max dist: " + max_dist);

        double sum = 0;
        double distance = 0;
        int count = 0;

        List<DMatch> goodMatches = matchesList;
        MatOfDMatch goodMatchesMat = new MatOfDMatch();
        goodMatchesMat.fromList(goodMatches);

        //put keypoints mats into lists
        List<KeyPoint> keypoints1_List = mRefKeypoints.toList();
        List<KeyPoint> keypoints2_List = keypoints.toList();

        //put keypoints into point2f mats so calib3d can use them to find homography
        List<Point> objList = new LinkedList<>();
        List<Point> sceneList = new LinkedList<>();
        for(int i=0;i<goodMatches.size();i++)
        {
            objList.add(keypoints1_List.get(goodMatches.get(i).queryIdx).pt);
            sceneList.add(keypoints2_List.get(goodMatches.get(i).trainIdx).pt);
        }

        Log.d(TAG, String.format("Good match: %d", goodMatches.size()));

        MatOfPoint2f objMat = new MatOfPoint2f();
        MatOfPoint2f sceneMat = new MatOfPoint2f();
        objMat.fromList(objList);
        sceneMat.fromList(sceneList);

        // HOMOGRAPHY!
        if (goodMatches.size() > GOOD_MATCH_COUNT) {
            //run homography on object and scene points
            //Mat homographyMask = new Mat();
            //Mat H = Calib3d.findHomography(objMat, sceneMat, Calib3d.RANSAC, 100, homographyMask, 1000, 0.995);
            Mat H = Calib3d.findHomography(objMat, sceneMat, Calib3d.RANSAC, 5);
            Log.d(TAG, "find homography TIME: " + (System.currentTimeMillis()-veryStart));

            if (H.cols() >= 3 && H.rows() >= 3) {
                Mat objCorners = new Mat(4, 1, CvType.CV_32FC2);
                Mat sceneCorners = new Mat(4, 1, CvType.CV_32FC2);
                //Mat obj_corners = new Mat(4, 1, CvType.CV_32FC2);

                double[] a = new double[]{0, 0};
                double[] b = new double[]{mRefImg.cols() - 1, 0};
                double[] c = new double[]{mRefImg.cols() - 1, mRefImg.rows() - 1};
                double[] d = new double[]{0, mRefImg.rows() - 1};

                //get corners from object
                objCorners.put(0, 0, a);
                objCorners.put(1, 0, b);
                objCorners.put(2, 0, c);
                objCorners.put(3, 0, d);

                Log.d(TAG, String.format("H size: %d, %d", H.cols(), H.rows()));

                perspectiveTransform(objCorners, sceneCorners, H);

                Log.d(TAG, String.format("transformed: (%.2f, %.2f) (%.2f, %.2f) (%.2f, %.2f) (%.2f, %.2f), width: %d, height: %d",
                        sceneCorners.get(0, 0)[0], sceneCorners.get(0, 0)[1],
                        sceneCorners.get(1, 0)[0], sceneCorners.get(1, 0)[1],
                        sceneCorners.get(2, 0)[0], sceneCorners.get(2, 0)[1],
                        sceneCorners.get(3, 0)[0], sceneCorners.get(3, 0)[1], sceneCorners.width(), sceneCorners.height()));

                //MatOfPoint boundaryMat = new MatOfPoint();
                ArrayList<Point> listOfBoundary = new ArrayList<>();
                listOfBoundary.add(new Point(sceneCorners.get(0, 0)));
                listOfBoundary.add(new Point(sceneCorners.get(1, 0)));
                listOfBoundary.add(new Point(sceneCorners.get(2, 0)));
                listOfBoundary.add(new Point(sceneCorners.get(3, 0)));
                boundary.fromList(listOfBoundary);
                //boundary.convertTo(result, CvType.CV_32F);
                //boundaryMat.fromList(listOfBoundary);
                //ArrayList<MatOfPoint> list = new ArrayList<>();
                //list.add(boundaryMat);

                //Imgproc.polylines(inputMat, list, true, Scalar.all(0),10);

                //boundary.release();
                objCorners.release();
                sceneCorners.release();

                RotatedRect rotatedRect = minAreaRect(boundary);
                Point[] v = new Point[4];
                Point[] bound = new Point[4];
                rotatedRect.points(v);

                for (int i = 0; i < 4; i++) {
                    if(rotatedRect.angle < -45)
                        bound[(i+2)%4] = v[i];
                    else
                        bound[(i+3)%4] = v[i];
                }

                boundary.fromArray(bound);

                Log.d(TAG, String.format("Average DISTANCE: %.2f, good matches: %d", sum/count, count));
                Log.d(TAG, String.format("Center: %s", measureCentering(boundary).toString()));
                Log.d(TAG, String.format("Size: %.2f", measureSize(boundary)));

            }

            Log.d(TAG, "draw homography TIME: " + (System.currentTimeMillis()-veryStart));

            //homographyMask.release();
            H.release();
        }

        objMat.release();
        sceneMat.release();
        //goodMatches.release();
        matches.release();
        descriptors.release();
        keypoints.release();
        mask.release();

        Log.d(TAG, "Detect RDT TIME: " + (System.currentTimeMillis()-veryStart));

        return boundary;
    }

    private boolean checkSharpness(Mat inputMat) {
        double sharpness = calculateSharpness(inputMat);

        boolean isSharp = sharpness > (minSharpness * SHARPNESS_THRESHOLD);
        Log.d(TAG, "Sharpness: "+sharpness);

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
        boolean isRightSize = height < size.height*VIEW_FINDER_SCALE_H*(1+SIZE_THRESHOLD) && height > size.height*VIEW_FINDER_SCALE_H*(1-SIZE_THRESHOLD);

        SizeResult sizeResult = SizeResult.INVALID;

        if (isRightSize) {
            sizeResult = SizeResult.RIGHT_SIZE;
        } else {
            if (height > size.height*VIEW_FINDER_SCALE_H*(1+SIZE_THRESHOLD)) {
                sizeResult = SizeResult.LARGE;
            } else if (height < size.height*VIEW_FINDER_SCALE_H*(1-SIZE_THRESHOLD)) {
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

        boolean isOriented = angle < 90.0*POSITION_THRESHOLD;

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

        texts[0] = isSharp ? "Sharpness: PASSED": "Sharpness: FAILED";
        if (exposureResult == ExposureResult.NORMAL) {
            texts[1] = "Brightness: PASSED";
        } else if (exposureResult == ExposureResult.OVER_EXPOSED) {
            texts[1] = "Brightness: TOO BRIGHT";
        } else if (exposureResult == ExposureResult.UNDER_EXPOSED) {
            texts[1] = "Brightness: TOO DARK";
        }

        texts[2] = sizeResult == SizeResult.RIGHT_SIZE && isCentered && isRightOrientation ? "POSITION/SIZE: PASSED": "POSITION/SIZE: FAILED";
        texts[3] = "Shadow: PASSED";

        return texts;

    }

    private boolean checkWindowPosition(Mat resultWindowMat) {
        Point line = new Point(resultWindowMat.width()*0.35-resultWindowMat.width()*0.15, 0);
        Rect roi = new Rect((int)(resultWindowMat.width()*0.15), (int)(resultWindowMat.height()*0.2), (int)(resultWindowMat.width()*0.7), (int)(resultWindowMat.height()*0.6));
        Mat crop = resultWindowMat.submat(roi);
        Mat cropgray = new Mat();
        cvtColor(crop, cropgray, Imgproc.COLOR_RGBA2BGR);
        cvtColor(cropgray, cropgray, Imgproc.COLOR_BGR2HSV);

        ArrayList<Mat> channels = new ArrayList<>();
        Core.split(cropgray, channels);

        Core.MinMaxLocResult result = Core.minMaxLoc(channels.get(2), null);

        Log.d(TAG, "MIN MAX LOC Rect: "+crop.size().width+", "+ cropgray.size().width);
        Log.d(TAG, "MIN MAX LOC Rect: "+crop.size().height+", "+ cropgray.size().height);
        Log.d(TAG, "MIN MAX LOC: "+result.minLoc + ", " + result.minVal + ", " + result.maxLoc + ", " + result.maxVal);

        double minColSum = Double.MAX_VALUE;
        int minColIndex = -2;
        for (int i = -2; i < 1; i++) {
            double colSum = channels.get(2).get((int)result.minLoc.y, (int)result.minLoc.x+i)[0] +
                    channels.get(2).get((int)result.minLoc.y, (int)result.minLoc.x+i+1)[0] +
                    channels.get(2).get((int)result.minLoc.y, (int)result.minLoc.x+i+2)[0];

            Log.d(TAG, String.format("MIN MAX explore: %d: %d, %d, %d", (int)result.minLoc.x+i, (int)channels.get(2).get((int)result.minLoc.y, (int)result.minLoc.x+i)[0],
                    (int)channels.get(2).get((int)result.minLoc.y, (int)result.minLoc.x+i+1)[0], (int)channels.get(2).get((int)result.minLoc.y, (int)result.minLoc.x+i+2)[0]));

            minColIndex = (colSum < minColSum) ? i : minColIndex;
            minColSum = (colSum < minColSum) ? colSum : minColSum;
        }

        Log.d(TAG, "MIN MAX col: " + minColIndex);

        double sum = 0;
        for (int i = minColIndex; i < minColIndex+3; i++) {
            for (int j = 0; j < channels.get(2).height(); j++) {
                Log.d(TAG, "MIN MAX coor: "+(result.minLoc.x+i)+","+j+", " +channels.get(2).get(j, (int)(result.minLoc.x+i))[0]);
                sum += channels.get(2).get(j, (int) (result.minLoc.x + i))[0];
                //}
            }
        }

        double avg = sum/(double)(channels.get(0).height()*3);

        Log.d(TAG, "MIN MAX Row Avg: " + avg + " And, MIN VAL: "+result.minVal);
        Log.d(TAG, "MIN MAX Row Loc: " + line + " And, MIN VAL: "+result.minLoc);

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

        Log.d(TAG, "perspective ref" + refPoints.dump());



        a = new double[]{RESULT_WINDOW_X, RESULT_WINDOW_Y};
        b = new double[]{RESULT_WINDOW_X+RESULT_WINDOW_WIDTH, RESULT_WINDOW_Y};
        c = new double[]{RESULT_WINDOW_X+RESULT_WINDOW_WIDTH, RESULT_WINDOW_Y+RESULT_WINDOW_HEIGHT};
        d = new double[]{RESULT_WINDOW_X, RESULT_WINDOW_Y+RESULT_WINDOW_HEIGHT};


        refResultPoints.put(0, 0, a);
        refResultPoints.put(1, 0, b);
        refResultPoints.put(2, 0, c);
        refResultPoints.put(3, 0, d);

        Log.d(TAG, "perspective results" + refResultPoints.dump());
        Log.d(TAG, "perspective bound" + boundary.dump());

        Mat M = getPerspectiveTransform(refPoints, boundary);
        Log.d(TAG, "perspective transform" + M.dump());
        Mat imgResultPointsMat = new Mat();
        perspectiveTransform(refResultPoints, imgResultPointsMat, M);
        Log.d(TAG, "perspective window" + imgResultPointsMat.dump());

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

        Log.d(TAG, "MIN MAX position right: " + windowPosition);

        if (windowPosition) {
            enhancedImg.copyTo(resultImg);
            return input;
        } else {
            return null;
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

    private Mat enhanceImage(Mat resultImg, org.opencv.core.Size tile) {
        Mat result = new Mat();
        cvtColor(resultImg, result, Imgproc.COLOR_RGBA2BGR);
        cvtColor(result, result, Imgproc.COLOR_BGR2HLS);

        CLAHE clahe = createCLAHE(10, tile);

        ArrayList<Mat> channels = new ArrayList<>();
        Core.split(result, channels);

        Mat newChannel = new Mat();

        Core.normalize(channels.get(1), channels.get(1), 0, 255, Core.NORM_MINMAX);

        clahe.apply(channels.get(1), newChannel);

        channels.set(1, newChannel);

        Core.merge(channels, result);

        cvtColor(result, result, Imgproc.COLOR_HLS2BGR);
        cvtColor(result, result, Imgproc.COLOR_BGR2RGBA);

        return result;
    }

    public InterpretationResult interpretResult(Bitmap img) {
        Mat resultMat = new Mat();
        Utils.bitmapToMat(img, resultMat);
        return interpretResult(resultMat);
    }

    public InterpretationResult interpretResult(Mat inputMat) {
        MatOfPoint2f boundary = new MatOfPoint2f();
        Mat grayMat = new Mat();
        cvtColor(inputMat, grayMat, Imgproc.COLOR_BGRA2GRAY);

        int cnt = 0;
        SizeResult isSizeable = SizeResult.INVALID;
        boolean isCentered = false;
        boolean isUpright = false;

        do {
            cnt++;
            boundary = detectRDTWithSIFT(grayMat, cnt);
            isSizeable = checkSize(boundary, new Size(inputMat.size().width/CROP_RATIO, inputMat.size().height/CROP_RATIO));
            isCentered = checkIfCentered(boundary, inputMat.size());
            isUpright = checkOrientation(boundary);
            Log.d(TAG, String.format("SIFT-right size %d, center %d, orientation %d, (%d, %d), cnt %d", isSizeable, isCentered, isUpright, inputMat.size().width, inputMat.size().height, cnt));
        } while(!(isSizeable==SizeResult.RIGHT_SIZE && isCentered && isUpright) && cnt < 10);

        if (boundary.size().width <= 0 && boundary.size().height <= 0)
            return new InterpretationResult();

        Mat resultMat = cropResultWindow(inputMat, boundary);
        boolean control, testA, testB;

        if (resultMat.width() == 0 && resultMat.height() == 0) {
            return new InterpretationResult(resultMat, false, false, false);
        }

        resultMat = enhanceResultWindow(resultMat, new Size(5, resultMat.cols()));

        control = readControlLine(resultMat, new Point(CONTROL_LINE_POSITION, 0));
        testA = readTestLine(resultMat, new Point(TEST_A_LINE_POSITION, 0));
        testB = readTestLine(resultMat, new Point(TEST_A_LINE_POSITION, 0));

        return new InterpretationResult(resultMat, control, testA, testB);
    }

    private Rect checkControlLine(Mat inputMat)  {
        Mat hls = new Mat();
        cvtColor(inputMat, hls, Imgproc.COLOR_RGBA2RGB);
        cvtColor(hls, hls, Imgproc.COLOR_RGB2HLS);

        Mat threshold = new Mat();
        Core.inRange(hls, CONTROL_LINE_COLOR_LOWER, CONTROL_LINE_COLOR_UPPER, threshold);
        Mat element_erode = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
        Mat element_dilate = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(20, 20));
        Imgproc.erode(threshold, threshold, element_erode);
        Imgproc.dilate(threshold, threshold, element_dilate);
        Imgproc.GaussianBlur(threshold, threshold, new Size(5, 5), 2, 2);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();

        Imgproc.findContours(threshold, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE, new Point(0, 0));
        Rect controlLineRect = new Rect(0,0,0,0);

        for (int i = 0; i < contours.size(); i++)
        {
            Rect rect = Imgproc.boundingRect(contours.get(i));
            //NSLog(@"contour rect: %d %d %d %d", rect.x, rect.y, rect.width, rect.height);
            if (CONTROL_LINE_POSITION_MIN < rect.x && rect.x < CONTROL_LINE_POSITION_MAX && CONTROL_LINE_MIN_HEIGHT < rect.height && CONTROL_LINE_MIN_WIDTH < rect.width && rect.width < CONTROL_LINE_MAX_WIDTH) {
                controlLineRect = rect;
                //NSLog(@"control line rect: %d %d %d %d", controlLineRect.x, controlLineRect.y, controlLineRect.width, controlLineRect.height);
            }
        }

        return controlLineRect;
    }

    private boolean readLine(Mat inputMat, Point position, boolean isControlLine) {
        Mat hls = new Mat();
        cvtColor(inputMat, hls, Imgproc.COLOR_BGRA2BGR);
        cvtColor(hls, hls, Imgproc.COLOR_BGR2HLS);

        List<Mat> channels = new ArrayList<>();
        Core.split(hls, channels);

        int lower_bound = (int)(position.x-LINE_SEARCH_WIDTH < 0 ? 0 : position.x-LINE_SEARCH_WIDTH);
        int upper_bound = (int)(position.x+LINE_SEARCH_WIDTH);

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
            //NSLog(@"Avg HLS: %.2f, %.2f, %.2f", avgHues[i-lower_bound]*2, avgIntensities[i-lower_bound]/255*100, avgSats[i-lower_bound]/255*100);
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


//        NSLog(@"refBoundary:  (%.2f, %.2f) (%.2f, %.2f) (%.2f, %.2f) (%.2f, %.2f)",
//                refBoundary.at<Vec2f>(0, 0)[0], refBoundary.at<Vec2f>(0, 0)[1],
//        refBoundary.at<Vec2f>(1, 0)[0], refBoundary.at<Vec2f>(1, 0)[1],
//        refBoundary.at<Vec2f>(2, 0)[0], refBoundary.at<Vec2f>(2, 0)[1],
//        refBoundary.at<Vec2f>(3, 0)[0], refBoundary.at<Vec2f>(3, 0)[1]);

//        NSLog(@"boundaryMat:  (%.2f, %.2f) (%.2f, %.2f) (%.2f, %.2f) (%.2f, %.2f)",
//                boundaryMat.at<Vec2f>(0, 0)[0], boundaryMat.at<Vec2f>(0, 0)[1],
//        boundaryMat.at<Vec2f>(1, 0)[0], boundaryMat.at<Vec2f>(1, 0)[1],
//        boundaryMat.at<Vec2f>(2, 0)[0], boundaryMat.at<Vec2f>(2, 0)[1],
//        boundaryMat.at<Vec2f>(3, 0)[0], boundaryMat.at<Vec2f>(3, 0)[1]);

        Mat M = getPerspectiveTransform(boundary, refBoundary);
        Mat correctedMat = new Mat(mRefImg.rows(), mRefImg.cols(), mRefImg.type());
        warpPerspective(inputMat, correctedMat, M, new Size(mRefImg.cols(), mRefImg.rows()));

        Rect controlLineRect = checkControlLine(correctedMat);

        if (controlLineRect.width == 0 && controlLineRect.height == 0) {
            return new Mat();
        }

        Point tl = new Point((controlLineRect.tl().x+controlLineRect.br().x)/2.0-45, 10);
        Point br = new Point((controlLineRect.tl().x+controlLineRect.br().x)/2.0+45, correctedMat.size().height-10);

        correctedMat = new Mat(correctedMat, new Rect(tl, br));

        return correctedMat;
    }

    private MatOfPoint2f detectRDTWithSIFT(Mat inputMat, int ransac){
        double currentTime = System.currentTimeMillis();
        Mat inDescriptor = new Mat();
        MatOfKeyPoint inKeypoints = new MatOfKeyPoint();
        MatOfPoint2f boundary = new MatOfPoint2f();
        double avgDist = 0.0;

        Mat mask = new Mat(inputMat.cols(), inputMat.rows(), CvType.CV_8U, new Scalar(0));

        Point p1 = new Point(0, inputMat.size().height*(1-VIEW_FINDER_SCALE_W/CROP_RATIO)/2);
        Point p2 = new Point(inputMat.size().width-p1.x, inputMat.size().height-p1.y);
        Imgproc.rectangle(mask, p1, p2, new Scalar(255), -1);

        siftDetector.detectAndCompute(inputMat, mask, inKeypoints, inDescriptor);

        if (inDescriptor.size().equals(new Size(0,0))) { // No features found!
            //NSLog(@"Found no features!");
            //NSLog(@"Time taken to detect: %f -- fail -- SIFT", CACurrentMediaTime() - currentTime);
            return boundary;
        }
        //NSLog(@"Found %lu keypoints from input image", inKeypoints.size());

        // Matching
        List<MatOfDMatch> matches = new ArrayList<>();
        siftMatcher.knnMatch(siftRefDescriptor, inDescriptor, matches, 2, null, false);

        double maxDist = Double.MIN_VALUE;
        double minDist = Double.MAX_VALUE;

        double sum = 0;
        int count = 0;

        ArrayList<DMatch> goodMatches = new ArrayList<>();
        for (int i = 0; i < matches.size(); i++) {
            DMatch m = matches.get(i).toArray()[0];
            DMatch n = matches.get(i).toArray()[1];
            if (m.distance <= 0.80 * n.distance) {
                goodMatches.add(n);
                sum += n.distance;
                count++;
            }
        }

        //put keypoints mats into lists
        List<KeyPoint> keypointsList1 = siftRefKeypoints.toList();
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
        //NSLog(@"GoodMatches size %lu", goodMatches.size());
        if (goodMatches.size() > GOOD_MATCH_COUNT) {
            Mat H = Calib3d.findHomography(objMat, sceneMat, Calib3d.RANSAC, ransac);

            if (H.cols() >= 3 && H.rows() >= 3) {
                Mat objCorners = new Mat(4, 1, CvType.CV_32FC2);
                Mat sceneCorners = new Mat(4, 1, CvType.CV_32FC2);

                double[] a = new double[]{0, 0};
                double[] b = new double[]{mRefImg.cols() - 1, 0};
                double[] c = new double[]{mRefImg.cols() - 1, mRefImg.rows() - 1};
                double[] d = new double[]{0, mRefImg.rows() - 1};

                //get corners from object
                objCorners.put(0, 0, a);
                objCorners.put(1, 0, b);
                objCorners.put(2, 0, c);
                objCorners.put(3, 0, d);

                perspectiveTransform(objCorners, sceneCorners, H);
//                NSLog(@"DstPts-SIFT:  (%.2f, %.2f) (%.2f, %.2f) (%.2f, %.2f) (%.2f, %.2f)",
//                        dstPoints[0].x, dstPoints[0].y,
//                        dstPoints[1].x, dstPoints[1].y,
//                        dstPoints[2].x, dstPoints[2].y,
//                        dstPoints[3].x, dstPoints[3].y);
//                NSLog(@"Transformed-SIFT: %.2f (%.2f, %.2f) (%.2f, %.2f) (%.2f, %.2f) (%.2f, %.2f)",
//                        sceneCorners.at<Vec2f>(1, 0)[0]-sceneCorners.at<Vec2f>(0, 0)[0],
//                sceneCorners.at<Vec2f>(0, 0)[0], sceneCorners.at<Vec2f>(0, 0)[1],
//                sceneCorners.at<Vec2f>(1, 0)[0], sceneCorners.at<Vec2f>(1, 0)[1],
//                sceneCorners.at<Vec2f>(2, 0)[0], sceneCorners.at<Vec2f>(2, 0)[1],
//                sceneCorners.at<Vec2f>(3, 0)[0], sceneCorners.at<Vec2f>(3, 0)[1]);

                ArrayList<Point> listOfBoundary = new ArrayList<>();
                listOfBoundary.add(new Point(sceneCorners.get(0, 0)));
                listOfBoundary.add(new Point(sceneCorners.get(1, 0)));
                listOfBoundary.add(new Point(sceneCorners.get(2, 0)));
                listOfBoundary.add(new Point(sceneCorners.get(3, 0)));

//                (boundary).push_back(Point2f(sceneCorners.at<Vec2f>(0,0)[0], sceneCorners.at<Vec2f>(0,0)[1]));
//                (boundary).push_back(Point2f(sceneCorners.at<Vec2f>(1,0)[0], sceneCorners.at<Vec2f>(1,0)[1]));
//                (boundary).push_back(Point2f(sceneCorners.at<Vec2f>(2,0)[0], sceneCorners.at<Vec2f>(2,0)[1]));
//                (boundary).push_back(Point2f(sceneCorners.at<Vec2f>(3,0)[0], sceneCorners.at<Vec2f>(3,0)[1]));

                boundary.fromList(listOfBoundary);
                objCorners.release();
                sceneCorners.release();

                avgDist = sum/count;

                RotatedRect rotatedRect = minAreaRect(boundary);
                Point[] v = new Point[4];
                Point[] bound = new Point[4];
                rotatedRect.points(v);

                for (int i = 0; i < 4; i++) {
                    if(rotatedRect.angle < -45)
                        bound[(i+2)%4] = v[i];
                    else
                        bound[(i+3)%4] = v[i];
                }

                boundary.fromArray(bound);


//                Rect rect = Imgproc.boundingRect(boundary);
//
//                NSLog(@"Transformed-SIFT-updated: %.2f (%.2f, %.2f) (%.2f, %.2f) (%.2f, %.2f) (%.2f, %.2f)",
//                        v[0].x-v[1].x,
//                        v[0].x, v[0].y,
//                        v[1].x, v[1].y,
//                        v[2].x, v[2].y,
//                        v[3].x, v[3].y);
//
//                float rotatedArea = rotatedRect.size.height*rotatedRect.size.width;
//                float boundArea = rect.area();
//                NSLog(@"Rotated: %.2f, contour: %.2f, diff: %.2f -- SIFT -- angle: %.2f", rotatedArea, contourArea(boundary), rotatedArea-contourArea(boundary), rotatedRect.angle);
            }
        }
//        NSLog(@"Time taken to detect: %f -- success -- SIFT", CACurrentMediaTime() - currentTime);
        return boundary;
    }
}
