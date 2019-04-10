package edu.washington.cs.ubicomplab.rdt_reader;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.widget.TextView;

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
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Scalar;

import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static edu.washington.cs.ubicomplab.rdt_reader.ImageQualityActivity.ExposureResult.NORMAL;
import static edu.washington.cs.ubicomplab.rdt_reader.ImageQualityActivity.ExposureResult.OVER_EXPOSED;
import static edu.washington.cs.ubicomplab.rdt_reader.ImageQualityActivity.ExposureResult.UNDER_EXPOSED;
import static edu.washington.cs.ubicomplab.rdt_reader.ImageQualityActivity.SizeResult.INVALID;
import static edu.washington.cs.ubicomplab.rdt_reader.ImageQualityActivity.SizeResult.LARGE;
import static edu.washington.cs.ubicomplab.rdt_reader.ImageQualityActivity.SizeResult.RIGHT_SIZE;
import static edu.washington.cs.ubicomplab.rdt_reader.ImageQualityActivity.SizeResult.SMALL;
import static java.lang.Math.pow;
import static java.lang.StrictMath.abs;
import static org.opencv.core.Core.NORM_INF;
import static org.opencv.core.Core.meanStdDev;
import static org.opencv.core.Core.normalize;
import static org.opencv.core.CvType.CV_64F;
import static org.opencv.imgproc.Imgproc.Laplacian;
import static org.opencv.imgproc.Imgproc.minAreaRect;
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


    private TextView mInstructionText;
    private double SHARPNESS_THRESHOLD = 0.0;
    private float OVER_EXP_THRESHOLD = 255;
    private float UNDER_EXP_THRESHOLD = 120;
    private float OVER_EXP_WHITE_COUNT = 100;
    private double SIZE_THRESHOLD = 0.3;
    private double POSITION_THRESHOLD = 0.2;
    private double VIEWPORT_SCALE = 0.60;
    private int GOOD_MATCH_COUNT = 7;
    private double minSharpness = Double.MIN_VALUE;
    private double maxSharpness = Double.MAX_VALUE; //this value is set to min because blur check is not needed.
    private int MOVE_CLOSER_COUNT = 5;
    private double CROP_RATIO = 0.6;
    private double VIEW_FINDER_SCALE_W = 0.35;
    private double VIEW_FINDER_SCALE_H = 0.50;

    String instruction_detected = "RDT detected at the center!";
    String instruction_pos = "Place RDT at the center.\nFit RDT to the rectangle.";
    String instruction_too_small = "Place RDT at the center.\nFit RDT to the rectangle.\nMove closer.";
    String instruction_too_large = "Place RDT at the center.\nFit RDT to the rectangle.\nMove further away.";
    String instruction_focusing = "Place RDT at the center.\nFit RDT to the rectangle.\nCamera is focusing. \nStay still.";
    String instruction_unfocused = "Place RDT at the center.\n Fit RDT to the rectangle.\nCamera is not focused. \nMove further away.";




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
        Mat inputMat = new Mat();
        Mat greyMat = new Mat();
        Imgproc.cvtColor(inputMat, greyMat, Imgproc.COLOR_BGRA2GRAY);
        MatOfPoint2f matchDistance = new MatOfPoint2f();
        boolean passed = false;

        //check brightness (refactored)
        ImageQualityActivity.ExposureResult exposureResult = (checkBrightness(greyMat));

        //check sharpness (refactored)
        boolean isSharp = checkSharpness(greyMat);

        //preform detectRDT only if those two quality checks are passed
        if (exposureResult == NORMAL && isSharp) {
            //CJ: detectRDT starts

            //CJ: detectRDT ends inside of "performBRISKSearchOnMat". Check "performBRISKSearchOnMat" for the end of detectRDT.
            MatOfPoint2f boundary = new MatOfPoint2f();
            matchDistance = detectRDT(greyMat, boundary);
            boolean isCentered = false;
            ImageQualityActivity.SizeResult sizeResult = INVALID;
            boolean isRightOrientation = false;

            //[self checkPositionAndSize:boundary isCropped:false inside:greyMat.size()];

            Size size = new Size();
            if (boundary.size().width > 0 && boundary.size().height > 0) {
                isCentered = checkIfCentered(boundary, size);
                sizeResult = checkSize(boundary, size);
                isRightOrientation = checkOrientation(boundary);
            }

            passed = sizeResult == RIGHT_SIZE && isCentered && isRightOrientation;


            //MatToUIImage --> Utils.matToBitmap()
            CompletionHandler(passed, Utils.matToBitmap(cropRDT(inputMat)), matchDistance, exposureResult, sizeResult, isCentered, isRightOrientation, isSharp, false);
            //completion(passed, MatToUIImage(inputMat), matchDistance, exposureResult, sizeResult, isCentered, isRightOrientation, isSharp, false);
        } else {
            CompletionHandler((passed, null, matchDistance, exposureResult, INVALID, false, false, isSharp, false);
        }

    }

    public void interpretRDT() {

    }

    private MatOfPoint2f detectRDT(Mat input, Mat output) {
        long veryStart = System.currentTimeMillis();

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
            return null;
        }

        // Matching
        MatOfDMatch matches = new MatOfDMatch();
        if (mRefImg.type() == input.type()) {
            try {
                Log.d(TAG, String.format("type: %d, %d", mRefDescriptor.type(), descriptors.type()));
                mMatcher.match(mRefDescriptor, descriptors, matches);
                Log.d(TAG, String.format("matched"));
            } catch (Exception e) {
                return null;
            }
        } else {
            return null;
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

                MatOfPoint2f boundary = new MatOfPoint2f();
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

                boundary.release();
                obj_corners.release();
                scene_corners.release();

                Log.d(TAG, String.format("Average DISTANCE: %.2f, good matches: %d", sum/count, count));

//                if(mCurrentState == State.QUALITY_CHECK && sum/count < minDistance) {
//                    minDistance = sum/count;
//                    minDistanceUpdated = true;
//                }
            }

            Log.d(TAG, "draw homography TIME: " + (System.currentTimeMillis()-veryStart));

            //Features2d.drawMatches(mRefImg, mRefKeypoints, input, keypoints, goodMatches, output, Scalar.all(-1), new Scalar(255,0,0), new MatOfByte(homographyMask), 2);
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

        return result;
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

    private ImageQualityActivity.SizeResult checkSize(MatOfPoint2f boundary, Size size) {

        double height = 0;
        boolean isRightSize = height < size.height*VIEWPORT_SCALE*(1+SIZE_THRESHOLD) && height > size.height*VIEWPORT_SCALE*(1-SIZE_THRESHOLD);

        ImageQualityActivity.SizeResult sizeResult = INVALID;

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

    private String getInstructionText(ImageQualityActivity.SizeResult sizeResult, boolean isCentered, boolean isRightOrientation) {
        int mMoveCloserCount = 0;
        String instructions = instruction_pos;

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

    private String[] getQualityCheckText(ImageQualityActivity.SizeResult sizeResult, boolean isCentered, boolean isRightOrientation, boolean isSharp, ImageQualityActivity.ExposureResult exposureResult) {

        String[] texts = new String[0];

        texts[0] = isSharp ? "Sharpness: PASSED": "Sharpness: FAILED";
        if (exposureResult == NORMAL) {
            texts[1] = "Brightness: PASSED";
        } else if (exposureResult == OVER_EXPOSED) {
            texts[1] = "Brightness: TOO BRIGHT";
        } else if (exposureResult == UNDER_EXPOSED) {
            texts[1] = "Brightness: TOO DARK";
        }

        texts[2] = sizeResult==RIGHT_SIZE && isCentered && isRightOrientation ? "POSITION/SIZE: PASSED": "POSITION/SIZE: FAILED";
        texts[3] = "Shadow: PASSED";

        return texts;

    }
}
