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


import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;

import static java.lang.Math.pow;
import static java.lang.StrictMath.abs;
import static org.opencv.core.Core.meanStdDev;
import static org.opencv.core.CvType.CV_64F;
import static org.opencv.imgproc.Imgproc.Laplacian;
import static org.opencv.imgproc.Imgproc.minAreaRect;



/**
 * Created by cjparkuw on 2/27/2019.
 */

public class ImageProcessor {
    private String TAG = "ImageProcessor";
    private static ImageProcessor instance = null;
    private BRISK mFeatureDetector;
    private BFMatcher mMatcher;
    private Mat mRefImg;
    private Mat mRefDescriptor;
    private MatOfKeyPoint mRefKeypoints;


    private double SHARPNESS_THRESHOLD = 0.0;
    private float OVER_EXP_THRESHOLD = 255;
    private float UNDER_EXP_THRESHOLD = 120;
    private float OVER_EXP_WHITE_COUNT = 100;
    private double SIZE_THRESHOLD = 0.3;
    private double POSITION_THRESHOLD = 0.2;
    private double VIEWPORT_SCALE = 0.50;
    private double minSharpness = Double.MIN_VALUE;
    private int MOVE_CLOSER_COUNT = 5;
    private double CROP_RATIO = 0.6;



    int mMoveCloserCount = 0;


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

        public CaptureResult(boolean allChecksPassed, Mat resultMat, double matchDistance,
                             ExposureResult exposureResult, SizeResult sizeResult,  boolean isCentered,
                             boolean isRightOrientation, boolean isSharp, boolean isShadow){
            this.allChecksPassed = allChecksPassed;
            this.resultMat = resultMat;
            this.matchDistance = matchDistance;
            this.exposureResult = exposureResult;
            this.sizeResult = sizeResult;
            this.isCentered = isCentered;
            this.isRightOrientation = isRightOrientation;
            this.isSharp = isSharp;
            this.isShadow = isShadow;
        }
    }


    public ImageProcessor (Activity activity) {
        mFeatureDetector = BRISK.create(45, 4, 1.0f);
        mMatcher = BFMatcher.create(BFMatcher.BRUTEFORCE_HAMMING, true);
        mRefImg = new Mat();

        Bitmap bitmap = BitmapFactory.decodeResource(activity.getApplicationContext().getResources(), R.drawable.quickvue_ref);
        Utils.bitmapToMat(bitmap, mRefImg);
        Imgproc.cvtColor(mRefImg, mRefImg, Imgproc.COLOR_RGB2GRAY);
        mRefDescriptor = new Mat();

        mRefKeypoints = new MatOfKeyPoint();
        long startTime = System.currentTimeMillis();
        mFeatureDetector.detect(mRefImg, mRefKeypoints);
        mFeatureDetector.compute(mRefImg, mRefKeypoints, mRefDescriptor);
        Log.d(TAG, "REFERENCE LOAD/DETECT/COMPUTE: " + (System.currentTimeMillis() - startTime));
    }

    public static ImageProcessor getInstance(Activity activity) {
        if (instance == null)
            instance = new ImageProcessor(activity);

        return instance;
    }

    public void loadOpenCV(Context context, BaseLoaderCallback mLoaderCallback) {
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
        Imgproc.cvtColor(inputMat, greyMat, Imgproc.COLOR_BGRA2GRAY);
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


            //Size size = new Size();
            if (boundary.size().width > 0 && boundary.size().height > 0) {
                isCentered = checkIfCentered(boundary, greyMat.size());
                sizeResult = checkSize(boundary, greyMat.size());
                isRightOrientation = checkOrientation(boundary);
            }

            passed = sizeResult == SizeResult.RIGHT_SIZE && isCentered && isRightOrientation;


            return new CaptureResult(passed, cropRDT(inputMat), matchDistance, exposureResult, sizeResult, isCentered, isRightOrientation, isSharp, false);
    }
        else {
            return new CaptureResult(passed, null, matchDistance, exposureResult, SizeResult.INVALID, false, false, isSharp, false);
        }

    }

    public void interpretRDT() {

    }

    private MatOfPoint2f detectRDT(Mat input) {
        long veryStart = System.currentTimeMillis();
        MatOfPoint2f boundary = new MatOfPoint2f();

        //Imgproc.GaussianBlur(input, input, new org.opencv.core.Size(3,3), 2, 2);

        Mat descriptors = new Mat();
        MatOfKeyPoint keypoints = new MatOfKeyPoint();

        long startTime = System.currentTimeMillis();
        Mat mask = new Mat(input.size(), CvType.CV_8U, Scalar.all(0));
        Imgproc.rectangle(mask, new Point(input.width()/5, input.height()/5), new Point(input.width()-input.width()/5, input.height()-input.height()/5), Scalar.all(255), -1);

        mFeatureDetector.detect(input, keypoints);
        mFeatureDetector.compute(input, keypoints, descriptors);
        Log.d(TAG, "detect/compute TIME: " + (System.currentTimeMillis()-startTime));

        org.opencv.core.Size size = descriptors.size();

        if (size.equals(new org.opencv.core.Size(0,0))) {
            Log.d(TAG, String.format("no features on input"));
            return boundary;
        }

        // Matching
        MatOfDMatch matches = new MatOfDMatch();
        if (mRefImg.type() == input.type()) {
            try {
                Log.d(TAG, String.format("type: %d, %d", mRefDescriptor.type(), descriptors.type()));
                mMatcher.match(mRefDescriptor, descriptors, matches);
                Log.d(TAG, String.format("matched"));
            } catch (Exception e) {
                return boundary;
            }
        } else {
            return boundary;
        }
        List<DMatch> matchesList = matches.toList();
        Log.d(TAG, "matching TIME: " + (System.currentTimeMillis()-veryStart));

        Double max_dist = Double.MIN_VALUE;
        Double min_dist = Double.MAX_VALUE;

        for (int i = 0; i < matchesList.size(); i++) {
            Double dist = (double) matchesList.get(i).distance;
            if (dist < min_dist)
                min_dist = dist;
            if (dist > max_dist)
                max_dist = dist;
        }

        Log.d(TAG, "DISTANCE Min dist: " + min_dist + "Max dist: " + max_dist);

        double sum = 0;
        double distance = 0;
        int count = 0;

        ArrayList<DMatch> good_matches = new ArrayList<>();
        for (int i = 0; i < matchesList.size(); i++) {
            if (matchesList.get(i).distance <= (1.5 * min_dist)) {
                good_matches.add(matchesList.get(i));
                sum += matchesList.get(i).distance;
                count++;
                Log.d(TAG, String.format("queryIdx: %d, trainIdx: %d, distance: %.2f", matchesList.get(i).queryIdx, matchesList.get(i).trainIdx, matchesList.get(i).distance));
            }
        }

        distance = sum/count;

        Log.d(TAG, "good matching TIME: " + (System.currentTimeMillis()-veryStart));

        MatOfDMatch goodMatches = new MatOfDMatch();
        goodMatches.fromList(good_matches);

        //put keypoints mats into lists
        List<KeyPoint> keypoints1_List = mRefKeypoints.toList();
        List<KeyPoint> keypoints2_List = keypoints.toList();

        //put keypoints into point2f mats so calib3d can use them to find homography
        LinkedList<Point> objList = new LinkedList<Point>();
        LinkedList<Point> sceneList = new LinkedList<Point>();
        for(int i=0;i<good_matches.size();i++)
        {
            objList.addLast(keypoints1_List.get(good_matches.get(i).queryIdx).pt);
            sceneList.addLast(keypoints2_List.get(good_matches.get(i).trainIdx).pt);
        }

        Log.d(TAG, String.format("Good match: %d", good_matches.size()));

        MatOfPoint2f obj = new MatOfPoint2f();
        MatOfPoint2f scene = new MatOfPoint2f();
        obj.fromList(objList);
        scene.fromList(sceneList);

        MatOfPoint2f result = new MatOfPoint2f(new Point(0.0f, 0.0f));
        result.convertTo(result, CvType.CV_32F);
        Log.d(TAG, "prepare homography TIME: " + (System.currentTimeMillis()-veryStart));

        if (good_matches.size() > Constants.GOOD_MATCH_COUNT) {
            //run homography on object and scene points
            Mat homographyMask = new Mat();
            Mat H = Calib3d.findHomography(obj, scene, Calib3d.RANSAC, 100, homographyMask, 1000, 0.995);
            Log.d(TAG, "find homography TIME: " + (System.currentTimeMillis()-veryStart));

            if (H.cols() >= 3 && H.rows() >= 3) {
                Mat obj_corners = new Mat(4, 1, CvType.CV_32FC2);
                Mat scene_corners = new Mat(4, 1, CvType.CV_32FC2);
                //Mat obj_corners = new Mat(4, 1, CvType.CV_32FC2);

                double[] a = new double[]{0, 0};
                double[] b = new double[]{mRefImg.cols() - 1, 0};
                double[] c = new double[]{mRefImg.cols() - 1, mRefImg.rows() - 1};
                double[] d = new double[]{0, mRefImg.rows() - 1};


                //get corners from object
                obj_corners.put(0, 0, a);
                obj_corners.put(1, 0, b);
                obj_corners.put(2, 0, c);
                obj_corners.put(3, 0, d);

                Log.d(TAG, String.format("H size: %d, %d", H.cols(), H.rows()));

                Core.perspectiveTransform(obj_corners, scene_corners, H);

                Log.d(TAG, String.format("transformed: (%.2f, %.2f) (%.2f, %.2f) (%.2f, %.2f) (%.2f, %.2f), width: %d, height: %d",
                        scene_corners.get(0, 0)[0], scene_corners.get(0, 0)[1],
                        scene_corners.get(1, 0)[0], scene_corners.get(1, 0)[1],
                        scene_corners.get(2, 0)[0], scene_corners.get(2, 0)[1],
                        scene_corners.get(3, 0)[0], scene_corners.get(3, 0)[1], scene_corners.width(), scene_corners.height()));

                MatOfPoint boundaryMat = new MatOfPoint();
                ArrayList<Point> listOfBoundary = new ArrayList<>();
                listOfBoundary.add(new Point(scene_corners.get(0, 0)));
                listOfBoundary.add(new Point(scene_corners.get(1, 0)));
                listOfBoundary.add(new Point(scene_corners.get(2, 0)));
                listOfBoundary.add(new Point(scene_corners.get(3, 0)));
                boundary.fromList(listOfBoundary);
                boundary.convertTo(result, CvType.CV_32F);
                boundaryMat.fromList(listOfBoundary);
                ArrayList<MatOfPoint> list = new ArrayList<>();
                list.add(boundaryMat);

                Imgproc.polylines(input, list, true, Scalar.all(0),10);

                //boundary.release();
                obj_corners.release();
                scene_corners.release();

                Log.d(TAG, String.format("Average DISTANCE: %.2f, good matches: %d", sum/count, count));
                Log.d(TAG, String.format("Center: %s", measureCentering(boundary).toString()));
                Log.d(TAG, String.format("Size: %.2f", measureSize(boundary)));

            }

            Log.d(TAG, "draw homography TIME: " + (System.currentTimeMillis()-veryStart));

            homographyMask.release();
            H.release();
        }

        obj.release();
        scene.release();
        goodMatches.release();
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
        Laplacian(input, des, CV_64F);

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
        boolean isRightSize = height < size.height*VIEWPORT_SCALE*(1+SIZE_THRESHOLD) && height > size.height*VIEWPORT_SCALE*(1-SIZE_THRESHOLD);

        SizeResult sizeResult = SizeResult.INVALID;

        if (isRightSize) {
            sizeResult = SizeResult.RIGHT_SIZE;
        } else {
            if (height > size.height*VIEWPORT_SCALE*(1+SIZE_THRESHOLD)) {
                sizeResult = SizeResult.LARGE;
            } else if (height < size.height*VIEWPORT_SCALE*(1-SIZE_THRESHOLD)) {
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
            angle = 90 - abs(rotatedRect.angle);
        } else {
            angle = abs(rotatedRect.angle);
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
        Imgproc.cvtColor(crop, cropgray, Imgproc.COLOR_RGBA2BGR);
        Imgproc.cvtColor(cropgray, cropgray, Imgproc.COLOR_BGR2HSV);

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


//
//        a = new double[]{Constants.RESULT_WINDOW_X, Constants.RESULT_WINDOW_Y};
//        b = new double[]{Constants.RESULT_WINDOW_X+Constants.RESULT_WINDOW_WIDTH, Constants.RESULT_WINDOW_Y};
//        c = new double[]{Constants.RESULT_WINDOW_X+Constants.RESULT_WINDOW_WIDTH, Constants.RESULT_WINDOW_Y+Constants.RESULT_WINDOW_HEIGHT};
//        d = new double[]{Constants.RESULT_WINDOW_X, Constants.RESULT_WINDOW_Y+Constants.RESULT_WINDOW_HEIGHT};
//

        refResultPoints.put(0, 0, a);
        refResultPoints.put(1, 0, b);
        refResultPoints.put(2, 0, c);
        refResultPoints.put(3, 0, d);

        Log.d(TAG, "perspective results" + refResultPoints.dump());
        Log.d(TAG, "perspective bound" + boundary.dump());

        Mat M = Imgproc.getPerspectiveTransform(refPoints, boundary);
        Log.d(TAG, "perspective transform" + M.dump());
        Mat imgResultPointsMat = new Mat();
        Core.perspectiveTransform(refResultPoints, imgResultPointsMat, M);
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
        Core.LUT(enhancedImg, lutMat, result);
        return result;
    }

    private Mat enhanceImage(Mat resultImg, org.opencv.core.Size tile) {
        Mat result = new Mat();
        Imgproc.cvtColor(resultImg, result, Imgproc.COLOR_RGBA2BGR);
        Imgproc.cvtColor(result, result, Imgproc.COLOR_BGR2HSV);

        CLAHE clahe = Imgproc.createCLAHE(5, tile);

        ArrayList<Mat> channels = new ArrayList<>();
        Core.split(result, channels);

        Mat newChannel = new Mat();

        clahe.apply(channels.get(2), newChannel);

        channels.set(2, newChannel);

        Core.merge(channels, result);

        Imgproc.cvtColor(result, result, Imgproc.COLOR_HSV2BGR);
        Imgproc.cvtColor(result, result, Imgproc.COLOR_BGR2RGBA);

        return result;
    }
}
