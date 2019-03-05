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

    private void checkSharpness() {

    }

    private void calculateSharpness() {

    }

    private void checkBrightness() {

    }

    private void calculateBrightness() {

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
