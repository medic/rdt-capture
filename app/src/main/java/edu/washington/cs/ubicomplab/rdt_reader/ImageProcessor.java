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

    public void generageViewFinder() {

    }

    public void captureRDT() {

    }

    public void interpretRDT() {

    }

    private void detectRDT() {

    }

    // The brightness and sharpness methods have been
    // filled with the iOS code for now, until
    // I am able to make sense of the android code
    // once I do then I will be able to change
    // syntax of the code and fix it up
    
    private bool checkSharpness(Mat inputMat) {
        double sharpness = [self calculateSharpness:inputMat];

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
        vector<float> histograms = [self calculateBrightness:inputMat];

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
        mBuff.assign((float*)hist.datastart, (float*)hist.dataend);
        return mBuff;

    }

    private void checkSize() {

    }

    private void checkIfCentered() {

    }

    private void checkOrientation() {

    }

    private void cropRDT() {

    }

    private void getInstructionText() {

    }

    private void getQualityCheckText() {

    }
}
