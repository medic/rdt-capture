package edu.washington.cs.ubicomplab.rdt_reader;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.widget.TextView;

import org.opencv.android.Utils;
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
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Scalar;

import java.text.SimpleDateFormat;
import java.util.Vector;

import static edu.washington.cs.ubicomplab.rdt_reader.ImageQualityActivity.ExposureResult.NORMAL;
import static edu.washington.cs.ubicomplab.rdt_reader.ImageQualityActivity.ExposureResult.OVER_EXPOSED;
import static edu.washington.cs.ubicomplab.rdt_reader.ImageQualityActivity.ExposureResult.UNDER_EXPOSED;
import static java.lang.Math.pow;
import static java.lang.StrictMath.abs;
import static org.opencv.core.Core.NORM_INF;
import static org.opencv.core.Core.meanStdDev;
import static org.opencv.core.Core.normalize;
import static org.opencv.core.Core.perspectiveTransform;
import static org.opencv.core.CvType.CV_32FC2;
import static org.opencv.core.CvType.CV_64F;
import static org.opencv.core.CvType.CV_8U;
import static org.opencv.imgproc.Imgproc.Laplacian;
import static org.opencv.imgproc.Imgproc.minAreaRect;
import static org.opencv.imgproc.Imgproc.rectangle;

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


    // The hard coded values for the reader

    private TextView mInstructionText;
    private float SHARPNESS_THRESHOLD = 0.0;
    private float OVER_EXP_THRESHOLD = 255;
    private float UNDER_EXP_THRESHOLD = 120;
    private float OVER_EXP_WHITE_COUNT = 100;
    private double SIZE_THRESHOLD = 0.3;
    private double POSITION_THRESHOLD = 0.2;
    private double VIEWPORT_SCALE = 0.60;
    private int GOOD_MATCH_COUNT = 7;
    private double minSharpness = FLT_MIN;
    private double maxSharpness = FLT_MAX; //this value is set to min because blur check is not needed.
    private int MOVE_CLOSER_COUNT = 5;
    private double CROP_RATIO = 0.6;
    private double VIEW_FINDER_SCALE_W = 0.35;
    private double VIEW_FINDER_SCALE_H = 0.50;


    private ImageProcessor(Activity activity) {
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

    public void loadOpenCV() {

    }

    public void configureCamera() {

    }

    public void generateViewFinder() {

    }

    public void captureRDT() {

    }

    public void interpretRDT() {

    }

    private double detectRDT(Mat inputMat, MatOfPoint boundary, Mat inDescripter) {
        Mat inDescriptor = new Mat();
        MatOfKeyPoint inKeypoints = new MatOfKeyPoint();
        //vector<cv::Point2f> boundary;
        double avgDist = 0.0;

        Mat mask = new Mat(inputMat.size().width, inputMat.size().height, CV_8U, new Scalar(0));

        Point p1 = new Point(inputMat.size().width*0.325, inputMat.size().height*0.2);
        Point p2 = new Point(inputMat.size().width-p1.x, inputMat.size().height-p1.y);
        rectangle(mask, p1, p2, new Scalar(255), -1);

        detector-> detectAndCompute(inputMat, mask, inKeypoints, inDescriptor);

        if (inDescriptor.cols() < 1 || inDescriptor.rows() < 1) { // No features found!
            return 0.0;
        }

        // Matching
        double currentTime = 0.0;
        MatOfDMatch[] matches = new MatOfDMatch[0];
        matcher->match(refDescriptor, inDescriptor, matches);

        double maxDist = FLT_MIN;
        double minDist = FLT_MAX;

        for (int i = 0; i < matches.size(); i++) {
            double dist = matches[i].distance;
            maxDist = Math.max(maxDist, dist);
            minDist = Math.min(minDist, dist);
        }

        double sum = 0;
        int count = 0;
        MatOfDMatch goodMatches = new MatOfDMatch();
        for (int i = 0; i < matches.size(); i++) {
            if (matches[i].distance <= (3.0 * minDist)) {
                goodMatches.push_back(matches[i]);
                sum += matches[i].distance;
                count++;
            }
        }

        MatOfPoint2f srcPoints = new MatOfPoint2f(); // Works without allocating space?
        MatOfPoint2f dstPoints = new MatOfPoint2f();

        for (int i = 0; i < goodMatches.size(); i++) {
            DMatch currentMatch = goodMatches[i];
            srcPoints.push_back(refKeypoints[currentMatch.queryIdx].pt);
            dstPoints.push_back(inKeypoints[currentMatch.trainIdx].pt);
        }

        boolean found = false;
        // HOMOGRAPHY!
        if (goodMatches.size(); > GOOD_MATCH_COUNT) {
            Mat H = findHomography(srcPoints, dstPoints, CV_RANSAC, 5);

            if (H.cols() >= 3 && H.rows() >= 3) {
                Mat objCorners = new Mat(4, 1, CV_32FC2);
                Mat sceneCorners = new Mat(4, 1, CV_32FC2);

                objCorners.at<Vec2f>(0, 0)[0] = 0;
                objCorners.at<Vec2f>(0, 0)[1] = 0;

                objCorners.at<Vec2f>(1, 0)[0] = refImg.cols - 1;
                objCorners.at<Vec2f>(1, 0)[1] = 0;

                objCorners.at<Vec2f>(2, 0)[0] = refImg.cols - 1;
                objCorners.at<Vec2f>(2, 0)[1] = refImg.rows - 1;

                objCorners.at<Vec2f>(3, 0)[0] = 0;
                objCorners.at<Vec2f>(3, 0)[1] = refImg.rows - 1;

                perspectiveTransform(objCorners, sceneCorners, H); // Not sure! if I'm suppose to dereference

                (boundary).push_back(new MatOfPoint2f(sceneCorners.at<Vec2f>(0,0)[0], sceneCorners.at<Vec2f>(0,0)[1]));
                (boundary).push_back(new MatOfPoint2f(sceneCorners.at<Vec2f>(1,0)[0], sceneCorners.at<Vec2f>(1,0)[1]));
                (boundary).push_back(new MatOfPoint2f(sceneCorners.at<Vec2f>(2,0)[0], sceneCorners.at<Vec2f>(2,0)[1]));
                (boundary).push_back(new MatOfPoint2f(sceneCorners.at<Vec2f>(3,0)[0], sceneCorners.at<Vec2f>(3,0)[1]));

                objCorners.release();
                sceneCorners.release();

                avgDist = sum/count;

            }
        }

        return avgDist;
    }



    private boolean checkSharpness(Mat inputMat) {
        double sharpness = calculateSharpness(inputMat);

        boolean isSharp = sharpness > (minSharpness * SHARPNESS_THRESHOLD);

        return isSharp;

    }

    private double calculateSharpness(Mat input) {
        Mat des = new Mat();
        Laplacian(input, des, CV_64F);

        MatOfDouble median = new MatOfDouble();
        MatOfDouble std = new MatOfDouble();

        meanStdDev(des, median, std);


        double sharpness = pow(0,2);
        des.release();
        return sharpness;
    }

    private ImageQualityActivity.ExposureResult checkBrightness(Mat inputMat) {

        // Brightness Calculation
        MatOfDouble histograms = new MatOfDouble(calculateBrightness(inputMat));

        int maxWhite = 0;
        float whiteCount = 0;

        for (int i = 0; i < histograms.size(); i++) {
            if (histograms[i] > 0) {
                maxWhite = i;
            }
            if (i == histograms.size() - 1) {
                whiteCount = histograms[i];
            }
        }

        // Check Brightness starts
        ImageQualityActivity.ExposureResult exposureResult;
        if (maxWhite >= OVER_EXP_THRESHOLD && whiteCount > OVER_EXP_WHITE_COUNT) {
            exposureResult = OVER_EXPOSED;
            return exposureResult;
        } else if (maxWhite < UNDER_EXP_THRESHOLD) {
            exposureResult = UNDER_EXPOSED;
            return exposureResult;
        } else {
            exposureResult = NORMAL;
            return exposureResult;
        }
    }

    private double calculateBrightness(Mat input) {
        MatOfInt mHistSizeNum = new MatOfInt(256);
        MatOfInt mHistSize = new MatOfInt();
        mHistSize.push_back(mHistSizeNum);
        Mat hist = new Mat();
        MatOfFloat mBuff = new MatOfFloat(0);
        MatOfFloat histogramRanges = new MatOfFloat();
        histogramRanges.push_back(0.0);
        histogramRanges.push_back(256.0);
        Size sizeRgba = input.size();
        MatOfInt channel = new MatOfInt(0);
        Mat allMat =  input;
        calcHist(allMat, channel, new Mat(), hist, mHistSize, histogramRanges);
        normalize(hist, hist, sizeRgba.height/2, 0, NORM_INF);
        mBuff.assign((float)hist.datastart, (float)hist.dataend);

        return mBuff;

    }

    private SizeResult checkSize(MatOfPoint2f[] boundary, Size size) {

        double height = measureSize(boundary);
        boolean isRightSize = height < size.height*VIEWPORT_SCALE*(1+SIZE_THRESHOLD) && height > size.height*VIEWPORT_SCALE*(1-SIZE_THRESHOLD);

        SizeResult sizeResult = INVALID;

        if (isRightSize) {
            sizeResult = RIGHT_SIZE;
        } else {
            if (height > size.height*VIEWPORT_SCALE*(1+SIZE_THRESHOLD)) {
                sizeResult = LARGE;
            } else if (height < size.height*VIEWPORT_SCALE*(1-SIZE_THRESHOLD)) {
                sizeResult = SMALL;
            } else {
                sizeResult = INVALID;
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

    private boolean checkOrientation(MatOfPoint2f boundry) {
        double angle = measureOrientation(boundry);

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

    private String getInstructionText(SizeResult sizeResult, boolean isCentered, boolean isRightOrientation) {
        int mMoveCloserCount = 0;
        String instructions = "";

        if (sizeResult == RIGHT_SIZE && isCentered && isRightOrientation){
            instructions = instruction_detected;
        } else if (mMoveCloserCount > MOVE_CLOSER_COUNT) {
            if (sizeResult != INVALID && sizeResult == SMALL) {
                 instructions = instruction_too_small;
            }
        } else {
            instructions = instruction_too_small;
            mMoveCloserCount++;
        }

        return instructions;


    }

    private Array<texts> getQualityCheckText(SizeResult sizeResult, boolean isCentered, boolean isRightOrientation, boolean isSharp, ImageQualityActivity.ExposureResult exposureResult) {

        // Find java version of code
        NSMutableArray texts = [[NSMutableArray alloc] init];

        texts[0] = isSharp ? @"Sharpness: PASSED": @"Sharpness: FAILED";
        if (exposureResult == NORMAL) {
            texts[1] = @"Brightness: PASSED";
        } else if (exposureResult == OVER_EXPOSED) {
            texts[1] = @"Brightness: TOO BRIGHT";
        } else if (exposureResult == UNDER_EXPOSED) {
            texts[1] = @"Brightness: TOO DARK";
        }

        texts[2] = sizeResult==RIGHT_SIZE && isCentered && isRightOrientation ? @"POSITION/SIZE: PASSED": @"POSITION/SIZE: FAILED";
        texts[3] = @"Shadow: PASSED";

        return texts;

    }
}
