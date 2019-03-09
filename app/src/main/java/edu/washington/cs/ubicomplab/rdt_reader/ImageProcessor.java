package edu.washington.cs.ubicomplab.rdt_reader;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.features2d.BFMatcher;
import org.opencv.features2d.BRISK;
import org.opencv.imgproc.Imgproc;

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

    private void detectRDT() {

    }

    // I have went ahead and added all the iOS code,
    // fixed some of it up, will fix the rest sometime
    // tommorrow.


    private boolean checkSharpness(Mat inputMat) {
        double sharpness = calculateSharpness(inputMat);

        bool isSharp = sharpness > (minSharpness * SHARPNESS_THRESHOLD);

        return isSharp;

    }

    private double calculateSharpness(Mat input) {
        Mat des = Mat();
        Laplacian(input, des, CV_64F);

        vector<double> median;
        vector<double> std;

        meanStdDev(des, median, std);


        double sharpness = pow(std[0],2);
        des.release();
        return sharpness;
    }

    private ExposureResult checkBrightness(Mat inputMat) {

        // Brightness Calculation
        vector<float> histograms = calculateBrightness(inputMat);

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
        ExposureResult exposureResult;
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

    private double calculateBrightness(Vector<float> input) {
        int mHistSizeNum =256;
        vector<int> mHistSize;
        mHistSize.push_back(mHistSizeNum);
        Mat hist = Mat();
        vector<float> mBuff;
        vector<float> histogramRanges;
        histogramRanges.push_back(0.0);
        histogramRanges.push_back(256.0);
        cv::Size sizeRgba = input.size();
        vector<int> channel = {0};
        vector<Mat> allMat = {input};
        calcHist(allMat, channel, Mat(), hist, mHistSize, histogramRanges);
        normalize(hist, hist, sizeRgba.height/2, 0, NORM_INF);
        mBuff.assign((float)hist.datastart, (float)hist.dataend);

        return mBuff;

    }

    private SizeResult checkSize(vector<Point2f> boundary, (cv::Size) size) {

        double height = [self measureSize:boundary];
        bool isRightSize = height < size.height*VIEWPORT_SCALE*(1+SIZE_THRESHOLD) && height > size.height*VIEWPORT_SCALE*(1-SIZE_THRESHOLD);

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

    private boolean checkIfCentered() {

        cv::Point center = [self measureCentering:boundary];
        cv::Point trueCenter = cv::Point(size.width/2, size.height/2);
        bool isCentered = center.x < trueCenter.x + (size.width*POSITION_THRESHOLD) && center.x > trueCenter.x-(size.width*POSITION_THRESHOLD)
                && center.y < trueCenter.y+(size.height*POSITION_THRESHOLD) && center.y > trueCenter.y-(size.height*POSITION_THRESHOLD);

        return isCentered;

    }

     private double measureOrientation(vector<Point2f> boundary) {
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

    private boolean checkOrientation() {
        double angle = measureOrientation(boudary);

        bool isOriented = angle < 90.0*POSITION_THRESHOLD;

        return isOriented;
    }

    private void cropRDT() {

    }

    private String getInstructionText(SizeResult sizeResult, boolean isCentered, boolean isRightOrientation) {

        if (sizeResult == RIGHT_SIZE && isCentered && isRightOrientation){
            instructions = instruction_detected;
            mMoveCloserCount = 0;
        } else if (mMoveCloserCount > MOVE_CLOSER_COUNT) {
            if (sizeResult != INVALID && sizeResult == SMALL) {
                instructions = instruction_too_small;
                mMoveCloserCount = 0;
            }
        } else {
            instructions = instruction_too_small;
            mMoveCloserCount++;
        }

        return instructions;


    }

    private Array<texts> getQualityCheckText(SizeResult sizeResult, boolean isCentered, boolean isRightOrientation, boolean isSharp, ExposureResult exposureResult) {

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
