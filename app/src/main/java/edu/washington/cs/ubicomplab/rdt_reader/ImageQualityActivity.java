package edu.washington.cs.ubicomplab.rdt_reader;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
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
import org.opencv.features2d.BRISK;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.Feature2D;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static edu.washington.cs.ubicomplab.rdt_reader.Constants.*;

public class ImageQualityActivity extends AppCompatActivity implements CvCameraViewListener2, SettingDialogFragment.SettingDialogListener, View.OnClickListener {

    private RDTCamera2View mOpenCvCameraView;
    private TextView mImageQualityFeedbackView;
    private TextView mProgressText;
    private ProgressBar mProgress;
    private View mProgressBackgroundView;

    private final String OVER_EXP_MSG = "TOO BRIGHT ";
    private final String UNDER_EXP_MSG = "TOO DARK ";
    private final String SHADOW_MSG = "SHADOW IS VISIBLE!!<br>";

    private final String QUALITY_MSG_FORMAT = "POSITION/SIZE: %s <br>" +
                                                "SHARPNESS: %s <br> " +
                                                "BRIGHTNESS: %s <br>" +
                                                "NO SHADOW: %s ";

    private int counter = 0;


    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    mDetector = new ColorBlobDetector();
                    mDetector.setHsvColor(Constants.RDT_COLOR_HSV);
                    loadReference();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    private enum State {
        INITIALIZATION, ENV_FOCUS_INFINITY, ENV_FOCUS_MACRO, ENV_FOCUS_AUTO_CENTER, QUALITY_CHECK, FINAL_CHECK
    }

    private enum ExposureResult {
        UNDER_EXPOSED, NORMAL, OVER_EXPOSED
    }

    private State mCurrentState = State.INITIALIZATION;
    private boolean mResetCameraNeeded = true;

    private double minBlur = Double.MAX_VALUE;
    private double maxBlur = Double.MIN_VALUE;

    private ColorBlobDetector mDetector;

    private int frameCounter = 0;

    private boolean isCaptured = false;

    private Feature2D mFeatureDetector;
    private DescriptorMatcher mMatcher;
    private Mat mRefImg;
    private Mat mRefDescriptor;
    private MatOfKeyPoint mRefKeypoints;

    private FeatureMathchingTask initTask;
    private ImageQualityCheckTask qualityCheckTask;

    private Mat mCurrentMat;

    /*Activity callbacks*/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_quality);

        setTitle("Image Quality Checker");

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        mOpenCvCameraView = findViewById(R.id.img_quality_check_camera_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setOnClickListener(this);
        findViewById(R.id.img_quality_check_viewport).setOnClickListener(this);
        mImageQualityFeedbackView = findViewById(R.id.img_quality_feedback_view);
        mProgress = findViewById(R.id.progressCircularBar);
        mProgressBackgroundView = findViewById(R.id.progressBackground);
        mProgressText = findViewById(R.id.progressText);

        setProgressUI(mCurrentState);

        initTask = new FeatureMathchingTask();
        qualityCheckTask = new ImageQualityCheckTask();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);

        }
    }

    /*Activity callbacks*/
    @Override
    protected void onPause() {
        super.onPause();
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();

        unloadReference();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();

        unloadReference();
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_settings:
                SettingDialogFragment dialog = new SettingDialogFragment();
                dialog.show(getFragmentManager(), "Setting Dialog");
                return true;
            default:
                return false;
        }
    }


    @Override
    public void onClickPositiveButton() {
        mCurrentState = State.INITIALIZATION;
        setProgressUI(mCurrentState);
    }


    @Override
    public void onClick(View view) {
        Log.d(TAG, "Camera request reset!");
        setupCameraParameters(mCurrentState);
    }

    /*OpenCV JavaCameraView callbacks*/

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        Mat rgbaMat = inputFrame.rgba();
        Mat grayMat = inputFrame.gray();

        if (mResetCameraNeeded) {
            setupCameraParameters(mCurrentState);
            //mResetCameraNeeded = false;
        }

        switch (mCurrentState) {
            case INITIALIZATION:
                if (initTask.getStatus() != AsyncTask.Status.RUNNING ) {
                    initTask = new FeatureMathchingTask();
                    initTask.execute(grayMat);
                }
                break;
            case ENV_FOCUS_INFINITY:
            case ENV_FOCUS_MACRO:
            case ENV_FOCUS_AUTO_CENTER:
                final double currVal = calculateBurriness(rgbaMat);

                if (currVal < minBlur)
                    minBlur = currVal;

                if (currVal > maxBlur)
                    maxBlur = currVal* BLUR_THRESHOLD;

                if (frameCounter > CALIBRATION_FRAME_COUNTER) {
                    setNextState(mCurrentState);
                    frameCounter = 0;
                } else {
                    frameCounter++;
                }

                break;
            case QUALITY_CHECK:
                if (isCaptured)
                    return null;

                if (qualityCheckTask.getStatus() != AsyncTask.Status.RUNNING) {
                    qualityCheckTask = new ImageQualityCheckTask();
                    Log.d(TAG, "rgbaMat 0 Size: "+rgbaMat.size().toString() + ", grayMat 1 Size: "+grayMat.size().toString());
                    qualityCheckTask.execute(rgbaMat, grayMat);
                }

                break;
            case FINAL_CHECK:
                if (isCaptured)
                    return null;
                break;
        }

        System.gc();
        return inputFrame.rgba();
    }

    /*Private methods*/
    private void loadReference(){
        mFeatureDetector = BRISK.create(60, 2, 1.0f);
        mMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
        mRefImg = new Mat();


        Bitmap bitmap = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.sd_bioline_malaria_ag_pf);
        Utils.bitmapToMat(bitmap, mRefImg);
        Imgproc.cvtColor(mRefImg, mRefImg, Imgproc.COLOR_RGB2GRAY);
        mRefDescriptor = new Mat();

        mRefKeypoints = new MatOfKeyPoint();
        long startTime = System.currentTimeMillis();
        mFeatureDetector.detect(mRefImg, mRefKeypoints);
        mFeatureDetector.compute(mRefImg, mRefKeypoints, mRefDescriptor);
        Log.d(TAG, "REFERENCE LOAD/DETECT/COMPUTE: " + (System.currentTimeMillis() - startTime));
    }

    private void unloadReference(){
        mRefImg.release();
        mRefDescriptor.release();
        mRefKeypoints.release();
    }

    private String saveTempRDTImage (Mat captureMat) {
        try {
            Bitmap resultBitmap = Bitmap.createBitmap(captureMat.cols(), captureMat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(captureMat, resultBitmap);
            File outputDir = getApplicationContext().getCacheDir(); // context being the Activity pointer
            File outputFile = File.createTempFile("temp_rdt_capture", ".jpg", outputDir);

            ByteArrayOutputStream bs = new ByteArrayOutputStream();
            resultBitmap.compress(Bitmap.CompressFormat.JPEG, 100, bs);

            FileOutputStream fos = new FileOutputStream(outputFile);
            fos.write(bs.toByteArray());
            fos.close();

            return outputFile.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean checkShadow (MatOfPoint2f approx) {
        Log.d(TAG, "SHADOW!!! " + approx.size().height);
        if (approx.size().height > 10) {
            return true;
        } else {
            return false;
        }
    }

    private boolean checkPositionAndSize (MatOfPoint2f approx, boolean cropped) {
        if (approx.total() < 1)
            return false;

        RotatedRect rotatedRect = Imgproc.minAreaRect(approx);
        if (cropped)
            rotatedRect.center = new Point(rotatedRect.center.x + PREVIEW_SIZE.width/4, rotatedRect.center.y + PREVIEW_SIZE.height/4);

        Point center = rotatedRect.center;
        Point trueCenter = new Point(PREVIEW_SIZE.width/2, PREVIEW_SIZE.height/2);

        boolean isUpright = rotatedRect.size.height > rotatedRect.size.width;
        double angle = 0;
        double height = 0;

        if (isUpright) {
            angle = 90 - Math.abs(rotatedRect.angle);
            height = rotatedRect.size.height;
        } else {
            angle = Math.abs(rotatedRect.angle);
            height = rotatedRect.size.width;
        }

        Log.d(TAG, String.format("POS: %.2f, %.2f, Angle: %.2f, Height: %.2f", center.x, center.y, angle, height));

        return angle < 90.0*POSITION_THRESHOLD && height < PREVIEW_SIZE.width*VIEWPORT_SCALE*(1+SIZE_THRESHOLD) && height > PREVIEW_SIZE.height*VIEWPORT_SCALE*(1-SIZE_THRESHOLD)
                && center.x < trueCenter.x *(1+ POSITION_THRESHOLD) && center.x > trueCenter.x*(1- POSITION_THRESHOLD)
                && center.y < trueCenter.y *(1+ POSITION_THRESHOLD) && center.y > trueCenter.y*(1- POSITION_THRESHOLD);
    }

    private void displayQualityResult (boolean isCorrectPosSize, boolean isBlur, boolean isOverExposed, boolean isUnderExposed, boolean isShadow) {
        String message = String.format(QUALITY_MSG_FORMAT, isCorrectPosSize? OK:NOT_OK,
                !isBlur ? OK : NOT_OK,
                !isOverExposed && !isUnderExposed ? OK : (isOverExposed ? OVER_EXP_MSG + NOT_OK : UNDER_EXP_MSG + NOT_OK),
                !isShadow ? OK : NOT_OK);

        mImageQualityFeedbackView.setText(Html.fromHtml(message));
        if (isCorrectPosSize && !isBlur && !isOverExposed && !isUnderExposed && !isShadow)
            mImageQualityFeedbackView.setBackgroundColor(getResources().getColor(R.color.green_overlay));
        else
            mImageQualityFeedbackView.setBackgroundColor(getResources().getColor(R.color.red_overlay));
    }

    private void setNextState (State currentState) {
        switch (currentState) {
            case INITIALIZATION:
                mCurrentState = State.ENV_FOCUS_INFINITY;
                mResetCameraNeeded = true;
                break;
            case ENV_FOCUS_INFINITY:
                mCurrentState = State.ENV_FOCUS_MACRO;
                mResetCameraNeeded = true;

                break;
            case ENV_FOCUS_MACRO:
                mCurrentState = State.ENV_FOCUS_AUTO_CENTER;
                mResetCameraNeeded = true;
                break;
            case ENV_FOCUS_AUTO_CENTER:
                mCurrentState = State.QUALITY_CHECK;
                mResetCameraNeeded = true;
                break;
            case QUALITY_CHECK:
                mCurrentState = State.FINAL_CHECK;
                mResetCameraNeeded = false;
                break;
            case FINAL_CHECK:
                mCurrentState = State.FINAL_CHECK;
                mResetCameraNeeded = false;
                break;
        }

        setProgressUI(mCurrentState);
    }

    private void setProgressUI (State CurrentState) {
        switch  (CurrentState) {
            case INITIALIZATION:
            case QUALITY_CHECK:
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mProgress.setVisibility(View.GONE);
                        mProgressBackgroundView.setVisibility(View.GONE);
                        mProgressText.setVisibility(View.GONE);
                    }
                });
                break;
            case ENV_FOCUS_INFINITY:
            case ENV_FOCUS_MACRO:
            case ENV_FOCUS_AUTO_CENTER:
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mProgress.setVisibility(View.VISIBLE);
                        mProgressBackgroundView.setVisibility(View.VISIBLE);
                        mProgressText.setText(R.string.progress_initialization);
                        mProgressText.setVisibility(View.VISIBLE);
                    }
                });
                break;
            case FINAL_CHECK:
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mProgress.setVisibility(View.VISIBLE);
                        mProgressBackgroundView.setVisibility(View.VISIBLE);
                        mProgressText.setText(R.string.progress_final);
                        mProgressText.setVisibility(View.VISIBLE);

                    }
                });
                break;
        }

    }

    private void setupCameraParameters (State currentState) {
        try {
            CameraCharacteristics characteristics = mOpenCvCameraView.mCameraManager.getCameraCharacteristics(mOpenCvCameraView.mCameraID);

            switch (currentState) {
                case INITIALIZATION:
                case ENV_FOCUS_AUTO_CENTER:
                case QUALITY_CHECK:
                    //resetCaptureRequest();
                    final android.graphics.Rect sensor = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                    MeteringRectangle mr = new MeteringRectangle(sensor.width() / 2 - 50, sensor.height() / 2 - 50, 100, 100,
                            MeteringRectangle.METERING_WEIGHT_MAX - 1);

                    Log.d(TAG, String.format("Sensor Size (%d, %d), Metering %s", sensor.width(), sensor.height(), mr.toString()));
                    Log.d(TAG, String.format("Regions AE %s", characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE).toString()));

                    mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS,
                            new MeteringRectangle[]{new MeteringRectangle(sensor.width() / 2 - 500+(counter%2), sensor.height() / 2 - 50+(counter%2), 1000+(counter%2), 100+(counter%2),
                                    MeteringRectangle.METERING_WEIGHT_MAX - 1)});
                    mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS,
                            new MeteringRectangle[]{new MeteringRectangle(sensor.width() / 2 - 500+(counter%2), sensor.height() / 2 - 50+(counter%2), 1000+(counter%2), 100+(counter%2),
                                    MeteringRectangle.METERING_WEIGHT_MAX - 1)});
                    mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                    mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_REGIONS,
                            new MeteringRectangle[]{new MeteringRectangle(sensor.width() / 2 - 500+(counter%2), sensor.height() / 2 - 50+(counter%2), 1000+(counter%2), 100+(counter%2),
                                    MeteringRectangle.METERING_WEIGHT_MAX - 1)});
                    mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
                    mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                    counter++;
                    break;
                case ENV_FOCUS_INFINITY:
                    mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
                    mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f);
                    mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                    mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
                    mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                    break;
                case ENV_FOCUS_MACRO:
                    mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_MACRO);
                    mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                    mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
                    mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                    break;
            }
            mOpenCvCameraView.mCaptureSession.setRepeatingRequest(mOpenCvCameraView.mPreviewRequestBuilder.build(), null, null);
        } catch (Exception e) {
            Log.e(TAG, e.getStackTrace().toString());
            Log.d(TAG, String.format("Preview Request Exception?"));
        }
    }

    private float[] calculateHistogram (Mat gray) {
        int mHistSizeNum =256;
        MatOfInt mHistSize = new MatOfInt(mHistSizeNum);
        Mat hist = new Mat();
        final float []mBuff = new float[mHistSizeNum];
        MatOfFloat histogramRanges = new MatOfFloat(0f, 256f);
        MatOfInt mChannels[] = new MatOfInt[] { new MatOfInt(0)};
        Size sizeRgba = gray.size();

        // GRAY
        for(int c=0; c<1; c++) {
            Imgproc.calcHist(Arrays.asList(gray), mChannels[c], new Mat(), hist,
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

    private double calculateBurriness (Mat input) {
        Mat des = new Mat();
        Imgproc.Laplacian(input, des, CvType.CV_64F);

        MatOfDouble median = new MatOfDouble();
        MatOfDouble std= new MatOfDouble();

        Core.meanStdDev(des, median , std);

        double maxLap = Double.MIN_VALUE;

        for(int i = 0; i < std.cols(); i++) {
            for (int j = 0; j < std.rows(); j++) {
                if (maxLap < std.get(j, i)[0]) {
                    maxLap = std.get(j, i)[0];
                }
            }
        }

        double blurriness = Math.pow(maxLap,2);

        Log.d(TAG, String.format("Blurriness for state %s: %.5f", mCurrentState.toString(), blurriness));

        median.release();
        std.release();
        des.release();

        return blurriness;
    }

    private MatOfPoint2f detectRDT(Mat input) {
        long veryStart = System.currentTimeMillis();

        Mat descriptors = new Mat();
        MatOfKeyPoint keypoints = new MatOfKeyPoint();

        long startTime = System.currentTimeMillis();
        mFeatureDetector.detect(input, keypoints);
        mFeatureDetector.compute(input, keypoints, descriptors);
        Log.d(TAG, "detect/compute TIME: " + (System.currentTimeMillis()-startTime));

        Size size = descriptors.size();

        if (size.equals(new Size(0,0))) {
            Log.d(TAG, String.format("no features on input"));
            return null;
        }

        // Matching
        startTime = System.currentTimeMillis();
        MatOfDMatch matches = new MatOfDMatch();
        if (mRefImg.type() == input.type()) {
            Log.d(TAG, String.format("type: %d, %d", mRefDescriptor.type(), descriptors.type()));
            mMatcher.match(mRefDescriptor, descriptors, matches);
            Log.d(TAG, String.format("matched"));
        } else {
            return null;
        }
        List<DMatch> matchesList = matches.toList();
        Log.d(TAG, "matching TIME: " + (System.currentTimeMillis()-startTime));

        Double max_dist = 0.0;
        Double min_dist = 100.0;

        for (int i = 0; i < matchesList.size(); i++) {
            Double dist = (double) matchesList.get(i).distance;
            if (dist < min_dist)
                min_dist = dist;
            if (dist > max_dist)
                max_dist = dist;
        }

        LinkedList<DMatch> good_matches = new LinkedList<DMatch>();
        for (int i = 0; i < matchesList.size(); i++) {
            if (matchesList.get(i).distance <= (1.5 * min_dist))
                good_matches.addLast(matchesList.get(i));
        }

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

        if (good_matches.size() > 5) {
            //run homography on object and scene points
            Mat H = Calib3d.findHomography(obj, scene, Calib3d.RANSAC, 5);

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

                Log.d(TAG, String.format("transformed: (%.2f, %.2f) (%.2f, %.2f) (%.2f, %.2f) (%.2f, %.2f)",
                        scene_corners.get(0, 0)[0], scene_corners.get(0, 0)[1],
                        scene_corners.get(1, 0)[0], scene_corners.get(1, 0)[1],
                        scene_corners.get(2, 0)[0], scene_corners.get(2, 0)[1],
                        scene_corners.get(3, 0)[0], scene_corners.get(3, 0)[1]));

                MatOfPoint2f boundary = new MatOfPoint2f();
                ArrayList<Point> listOfBoundary = new ArrayList<>();
                listOfBoundary.add(new Point(scene_corners.get(0, 0)));
                listOfBoundary.add(new Point(scene_corners.get(1, 0)));
                listOfBoundary.add(new Point(scene_corners.get(2, 0)));
                listOfBoundary.add(new Point(scene_corners.get(3, 0)));
                boundary.fromList(listOfBoundary);
                boundary.convertTo(result, CvType.CV_32F);

                boundary.release();
                obj_corners.release();
                scene_corners.release();
            }

            H.release();
        }

        obj.release();
        scene.release();
        goodMatches.release();
        matches.release();
        descriptors.release();
        keypoints.release();

        Log.d(TAG, "Detect RDT TIME: " + (System.currentTimeMillis()-veryStart));

        return result;
    }

    private MatOfPoint2f detectWhite (Mat input) {

        MatOfPoint2f maxRect = new MatOfPoint2f(new Point(0,0));

        if (mDetector != null) {
            mDetector.process(input);
            List<MatOfPoint> contours = mDetector.getContours();
            Log.e(TAG, "Contours count: " + contours.size());
            //Imgproc.drawContours(input, contours, -1, CONTOUR_COLOR);

            for (int i = 0; i < contours.size(); i++) {
                MatOfPoint2f approx2f = new MatOfPoint2f();

                contours.get(0).convertTo(approx2f, CvType.CV_32F);

                Imgproc.approxPolyDP(approx2f, approx2f, 10, true);

                Log.e(TAG, "Contours corners: " + approx2f.size().height);

                //if (approx.size().height < 10) {
                    //org.opencv.core.Rect rect = Imgproc.boundingRect(approx);
                    RotatedRect rotatedRect =  Imgproc.minAreaRect(approx2f);
                    RotatedRect maxRotatedRect =  Imgproc.minAreaRect(maxRect);
                    //Imgproc.rectangle(input,rect.br(), rect.tl(), new Scalar(0,0,255,255), 5);

                    if (rotatedRect.size.height * rotatedRect.size.width > maxRotatedRect.size.height * maxRotatedRect.size.width)
                        approx2f.copyTo(maxRect);
                //}

                approx2f.release();
            }
        }

        return maxRect;
    }

    private class FeatureMathchingTask extends AsyncTask<Mat, Integer, Boolean> {

        @Override
        protected Boolean doInBackground(Mat... mats) {
            long startTime = System.currentTimeMillis();
            Mat grayMat = mats[0].submat(new Rect((int)(PREVIEW_SIZE.width/4), (int)(PREVIEW_SIZE.height/4), (int)(PREVIEW_SIZE.width*VIEWPORT_SCALE), (int)(PREVIEW_SIZE.height*VIEWPORT_SCALE)));

            MatOfPoint2f approx = detectRDT(grayMat);
            mats[0].release();
            grayMat.release();


            final boolean isCorrectPosSizeInit = checkPositionAndSize(approx, true);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    displayQualityResult(isCorrectPosSizeInit, true, true, true, true);
                }
            });

            approx.release();

            Log.d(TAG, String.format("FeatureMatchingTask TIME: %d", System.currentTimeMillis() - startTime));

            return isCorrectPosSizeInit;
        }

        @Override
        protected void onPostExecute(Boolean isCorrectPosSizeInit) {
            super.onPostExecute(isCorrectPosSizeInit);

            if (isCorrectPosSizeInit) {
                if (frameCounter > FEATURE_MATCHING_FRAME_COUNTER) {
                    setNextState(mCurrentState);
                    frameCounter = 0;
                } else {
                    frameCounter++;
                }
            } else {
                frameCounter = 0;
            }
        }
    }

    private class ImageQualityCheckTask extends AsyncTask<Mat, Integer, Mat> {
        private RDTMathchingTask machingTask = new RDTMathchingTask();
        private BlurCheckTask blurTask = new BlurCheckTask();
        private ExposureCheckTask exposureCheckTask = new ExposureCheckTask();

        private class RDTMathchingTask extends AsyncTask<Mat, Integer, Boolean> {

            @Override
            protected Boolean doInBackground(Mat... mats) {
                long startTime = System.currentTimeMillis();
                Mat grayMat = mats[0].submat(new Rect((int)(PREVIEW_SIZE.width/4), (int)(PREVIEW_SIZE.height/4), (int)(PREVIEW_SIZE.width*VIEWPORT_SCALE), (int)(PREVIEW_SIZE.height*VIEWPORT_SCALE)));

                //MatOfPoint2f approx = detectRDT(mats[0].submat(new Rect((int)(PREVIEW_SIZE.width/4), (int)(PREVIEW_SIZE.height/4), (int)(PREVIEW_SIZE.width*VIEWPORT_SCALE), (int)(PREVIEW_SIZE.height*VIEWPORT_SCALE))));
                MatOfPoint2f approx = detectRDT(grayMat);
                mats[0].release();
                grayMat.release();


                boolean result = checkPositionAndSize(approx, true);

                approx.release();

                Log.d(TAG, String.format("FeatureMatchingTask TIME: %d", System.currentTimeMillis() - startTime));


                return result;
            }
        }

        private class BlurCheckTask extends AsyncTask<Mat, Integer, Boolean> {

            @Override
            protected Boolean doInBackground(Mat... mats) {
                double blurVal = calculateBurriness(mats[0]);

                mats[0].release();

                return blurVal < maxBlur;
            }
        }

        private class ExposureCheckTask extends AsyncTask<Mat, Integer, ExposureResult> {

            @Override
            protected ExposureResult doInBackground(Mat... mats) {
                float[] histogram = calculateHistogram(mats[0]);

                int maxWhite = 0;

                for (int i = 0; i < histogram.length; i++) {
                    if (histogram[i] > 0) {
                        maxWhite = i;
                    }
                }
                Log.d(TAG, "rgbaMat 2 Size: "+mats[0].size().toString());

                mats[0].release();

                if (maxWhite >= OVER_EXP_THRESHOLD)
                    return ExposureResult.OVER_EXPOSED;
                else if (maxWhite < UNDER_EXP_THRESHOLD)
                    return ExposureResult.UNDER_EXPOSED;
                else
                    return ExposureResult.NORMAL;
            }
        }


        @Override
        protected Mat doInBackground(Mat... mats) {
            long startTime = System.currentTimeMillis();
            Mat rgbaMat = mats[0].clone();
            Mat matchingMat = mats[1].clone();
            Mat blurMat = mats[0].clone();
            Mat exposureMat = mats[1].clone();

            machingTask.execute(matchingMat);
            blurTask.execute(blurMat);
            exposureCheckTask.execute(exposureMat);

            mats[0].release();
            mats[1].release();

            return rgbaMat;
        }

        @Override
        protected void onPostExecute(Mat rgbaMat) {
            super.onPostExecute(rgbaMat);

            try {
                final boolean isCorrectPosSize = machingTask.get();
                final boolean isBlur = blurTask.get();
                ExposureResult exposureResult = exposureCheckTask.get();
                final boolean isOverExposed = exposureResult == ExposureResult.OVER_EXPOSED;
                final boolean isUnderExposed = exposureResult == ExposureResult.UNDER_EXPOSED;
                final boolean isShadow = false;


                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        displayQualityResult(isCorrectPosSize, isBlur, isOverExposed, isUnderExposed, isShadow);
                    }
                });

                if (isCorrectPosSize && !isBlur && !isOverExposed && !isUnderExposed && !isShadow) {
                    if (frameCounter > FEATURE_MATCHING_FRAME_COUNTER) {
                        isCaptured = true;

                        setNextState(mCurrentState);
                        setProgressUI(mCurrentState);

                        Log.d(TAG, "rgbaMat 5 Size: " + rgbaMat.size().toString() + ", rect size: " + new Rect((int) (PREVIEW_SIZE.width / 5), (int) (PREVIEW_SIZE.height / 5), (int) (PREVIEW_SIZE.width * 0.6), (int) (PREVIEW_SIZE.height * 0.6)).size().toString());
                        String RDTCapturePath = saveTempRDTImage(rgbaMat.submat(new Rect((int) (PREVIEW_SIZE.width / 5), (int) (PREVIEW_SIZE.height / 5), (int) (PREVIEW_SIZE.width * 0.6), (int) (PREVIEW_SIZE.height * 0.6))));
                        Intent intent = new Intent(ImageQualityActivity.this, ImageResultActivity.class);
                        intent.putExtra("RDTCapturePath", RDTCapturePath);

                        //rgbaMat.release();
                        startActivity(intent);
                        frameCounter = 0;
                    } else {
                        frameCounter++;
                    }
                } else {
                    frameCounter = 0;
                }
            } catch (Exception e) {

            }
        }
    }
}
