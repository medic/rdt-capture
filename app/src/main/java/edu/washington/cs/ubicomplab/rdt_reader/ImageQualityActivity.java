package edu.washington.cs.ubicomplab.rdt_reader;

import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
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
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static edu.washington.cs.ubicomplab.rdt_reader.Constants.*;

public class ImageQualityActivity extends AppCompatActivity implements CvCameraViewListener2, SettingDialogFragment.SettingDialogListener {

    private RDTCamera2View mOpenCvCameraView;
    private TextView mImageQualityFeedbackView;
    private TextView mProgressText;
    private ProgressBar mProgress;
    private View mProgressBackgroundView;

    private final String NO_MSG = "";
    private final String BLUR_MSG = "PLACE RDT IN THE BOX<br>TRY TO STAY STILL<br>";
    private final String GOOD_MSG = "LOOKS GOOD!<br>";
    private final String OVER_EXP_MSG = "TOO BRIGHT ";
    private final String UNDER_EXP_MSG = "TOO DARK ";
    private final String SHADOW_MSG = "SHADOW IS VISIBLE!!<br>";

    private final String QUALITY_MSG_FORMAT = "POSITION/SIZE: %s <br>" +
                                                "SHARPNESS: %s <br> " +
                                                "BRIGHTNESS: %s <br>" +
                                                "NO SHADOW: %s ";


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

    private State mCurrentState = State.INITIALIZATION;
    private boolean mResetCameraNeeded = true;

    private double minBlur = Double.MAX_VALUE;
    private double maxBlur = Double.MIN_VALUE;

    private ColorBlobDetector mDetector;
    private final Scalar CONTOUR_COLOR = new Scalar(255,0,0,255);

    private int frameCounter = 0;

    private boolean isCaptured = false;

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

        mImageQualityFeedbackView = findViewById(R.id.img_quality_feedback_view);
        mProgress = findViewById(R.id.progressCircularBar);
        mProgressBackgroundView = findViewById(R.id.progressBackground);
        mProgressText = findViewById(R.id.progressText);

        //test purposes
        /*Timer uploadCheckerTimer = new Timer(true);
        uploadCheckerTimer.scheduleAtFixedRate(
                new TimerTask() {
                    public void run() { setNextState(mCurrentState); }
                }, 5*1000, 5 * 1000);*/

        setProgressUI(mCurrentState);
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
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

    /*OpenCV JavaCameraView callbacks*/

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        if (mResetCameraNeeded)
            setupCameraParameters(mCurrentState);

        switch (mCurrentState) {
            case INITIALIZATION:
                MatOfPoint approxInit = detectWhite(inputFrame.rgba());
                final boolean isCorrectPosSizeInit = checkPositionAndSize(approxInit);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        displayQualityResult(isCorrectPosSizeInit, true, true, true, true);
                    }
                });

                if (isCorrectPosSizeInit) {
                    if (frameCounter > CALIBRATION_FRAME_COUNTER) {
                        setNextState(mCurrentState);
                        frameCounter = 0;
                    } else {
                        frameCounter++;
                    }
                } else {
                    frameCounter = 0;
                }

                approxInit.release();

                break;
            case ENV_FOCUS_INFINITY:
            case ENV_FOCUS_MACRO:
            case ENV_FOCUS_AUTO_CENTER:
                final double currVal = calculateBurriness(inputFrame.rgba());

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

                //result = drawContourUsingSobel(inputFrame.rgba());
                double blurVal = calculateBurriness(inputFrame.rgba());
                final boolean isBlur = blurVal < maxBlur;

                float[] histogram = calculateHistogram(inputFrame.gray());

                int maxWhite = 0;

                for (int i = 0; i < histogram.length; i++) {
                    if (histogram[i] > 0) {
                        maxWhite = i;
                    }
                }

                final boolean isOverExposed = maxWhite >= OVER_EXP_THRESHOLD;
                final boolean isUnderExposed = maxWhite < UNDER_EXP_THRESHOLD;

                MatOfPoint approx = detectWhite(inputFrame.rgba());
                final boolean isCorrectPosSize = checkPositionAndSize(approx);
                final boolean isShadow = checkShadow(approx);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        displayQualityResult(isCorrectPosSize, isBlur, isOverExposed, isUnderExposed, isShadow);
                    }
                });

                synchronized (this) {

                    if (isCorrectPosSize && !isBlur && !isOverExposed && !isUnderExposed && !isShadow && !isCaptured) {
                        isCaptured = true;

                        setNextState(mCurrentState);
                        setProgressUI(mCurrentState);

                        String RDTCapturePath = saveTempRDTImage(inputFrame.rgba());
                        Intent intent = new Intent(ImageQualityActivity.this, ImageResultActivity.class);
                        intent.putExtra("RDTCapturePath", RDTCapturePath);
                        startActivity(intent);
                    }
                }

                approx.release();
                break;
            case FINAL_CHECK:
                if (isCaptured)
                    return null;
                break;
        }

        //setNextState(mCurrentState);

        return inputFrame.rgba();
    }

    /*Private methods*/
    private String saveTempRDTImage (Mat captureMat) {
        try {
            Bitmap resultBitmap = Bitmap.createBitmap(captureMat.cols(), captureMat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(captureMat, resultBitmap);
            File outputDir = getApplicationContext().getCacheDir(); // context being the Activity pointer
            File outputFile = File.createTempFile("temp_rdt_capture", ".png", outputDir);

            ByteArrayOutputStream bs = new ByteArrayOutputStream();
            resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, bs);

            FileOutputStream fos = new FileOutputStream(outputFile);
            fos.write(bs.toByteArray());
            fos.close();

            return outputFile.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean checkShadow (MatOfPoint approx) {
        Log.d(TAG, "SHADOW!!! " + approx.size().height);
        if (approx.size().height > 10) {
            return true;
        } else {
            return false;
        }
    }

    private boolean checkPositionAndSize (MatOfPoint approx) {
        Rect rect = Imgproc.boundingRect(approx);

        Point center = new Point((rect.br().x+rect.tl().x)/2.0f, (rect.br().y+rect.tl().y)/2.0f);
        double height = rect.br().x - rect.tl().x;

        Point trueCenter = new Point(512, 384);

        Log.d(TAG, String.format("POS: %.2f, %.2f, Height: %.2f", center.x, center.y, height));

        if (height < 512*(1+SIZE_THRESHOLD) && height > 512*(1-SIZE_THRESHOLD)
                && center.x < trueCenter.x *(1+ POSITION_THRESHOLD) && center.x > trueCenter.x*(1- POSITION_THRESHOLD)
                && center.y < trueCenter.y *(1+ POSITION_THRESHOLD) && center.y > trueCenter.y*(1- POSITION_THRESHOLD)) {
            return true;
        } else {
            return false;
        }
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
                    final android.graphics.Rect sensor = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

                    mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS,
                            new MeteringRectangle[]{new MeteringRectangle(sensor.width() / 2 - 50, sensor.height() / 2 - 50, 100, 100,
                                    MeteringRectangle.METERING_WEIGHT_MAX - 1)});
                    mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS,
                            new MeteringRectangle[]{new MeteringRectangle(sensor.width() / 2 - 50, sensor.height() / 2 - 50, 100, 100,
                                    MeteringRectangle.METERING_WEIGHT_MAX - 1)});
                    mOpenCvCameraView.mPreviewRequestBuilder.setTag("CENTER_AF_AE_TAG");
                    break;
                case ENV_FOCUS_INFINITY:
                    mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
                    mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f);
                    break;
                case ENV_FOCUS_MACRO:
                    float macroDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
                    mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
                    //mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_MACRO);
                    mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, macroDistance);
                    break;
            }
            mOpenCvCameraView.mCaptureSession.setRepeatingRequest(mOpenCvCameraView.mPreviewRequestBuilder.build(), null, null);
        } catch (Exception e) {

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
        }

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

        des.release();

        return blurriness;
    }

    private Mat drawContourUsingSobel(Mat input) {
        long start = System.currentTimeMillis();

        Mat sobelx = new Mat();
        Mat sobely = new Mat();
        Mat output = new Mat();
        Mat sharp = new Mat();

        //Imgproc.GaussianBlur(input, output, new Size(21, 21), 8);
        Imgproc.GaussianBlur(input, output, new Size(21, 21), 3);
        Imgproc.cvtColor(output, output, Imgproc.COLOR_RGB2GRAY);

        Imgproc.Sobel(output, sobelx, CvType.CV_32F, 0, 1); //ksize=5
        Imgproc.Sobel(output, sobely, CvType.CV_32F, 1, 0); //ksize=5

        Core.pow(sobelx, 2, sobelx);
        Core.pow(sobely, 2, sobely);

        Core.add(sobelx, sobely, output);

        output.convertTo(output, CvType.CV_32F);

        Core.pow(output, 0.5, output);
        Core.multiply(output, new Scalar(Math.pow(2, 0.5)),output);

        output.convertTo(output, CvType.CV_8UC1);

        Imgproc.GaussianBlur(output, sharp, new Size(0, 0), 3);
        Core.addWeighted(output, 1.5, sharp, -0.5, 0, output);
        Core.bitwise_not(output, output);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();

        Imgproc.findContours(output, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        Log.d(TAG, "contours: " + contours.size());

        //output.convertTo(output, CV_32F);

        //for(int idx = 0; idx >= 0; idx = (int) hierarchy.get(0, idx)[0]) {
        for( int idx = 0; idx < contours.size(); idx++ ) {
            MatOfPoint matOfPoint = contours.get(idx);
            Rect rect = Imgproc.boundingRect(matOfPoint);
            if(rect.size().width > 100 && rect.size().height > 100)
                Imgproc.rectangle(input, rect.tl(), rect.br(), new Scalar(255, 255, 255));
        }

        sobelx.release();
        sobelx.release();
        hierarchy.release();
        output.release();
        sharp.release();

        Log.d(TAG, String.format("Sobel took %d ms", (System.currentTimeMillis()-start)));
        return input;
    }

    private MatOfPoint detectWhite (Mat input) {
        MatOfPoint maxRect = new MatOfPoint();;

        if (mDetector != null) {
            mDetector.process(input);
            List<MatOfPoint> contours = mDetector.getContours();
            Log.e(TAG, "Contours count: " + contours.size());
            Imgproc.drawContours(input, contours, -1, CONTOUR_COLOR);

            for (int i = 0; i < contours.size(); i++) {
                MatOfPoint2f this2f = new MatOfPoint2f();
                MatOfPoint approx = new MatOfPoint();
                MatOfPoint2f approx2f = new MatOfPoint2f();

                contours.get(0).convertTo(this2f, CvType.CV_32FC2);

                Imgproc.approxPolyDP(this2f, approx2f, 10, true);

                approx2f.convertTo(approx, CvType.CV_32S);

                Log.e(TAG, "Contours corners: " + approx.size().height);

                //if (approx.size().height < 10) {
                    org.opencv.core.Rect rect = Imgproc.boundingRect(approx);
                    //Imgproc.rectangle(input,rect.br(), rect.tl(), new Scalar(0,0,255,255), 5);

                    if (rect.area() > Imgproc.boundingRect(maxRect).area())
                        approx.copyTo(maxRect);
                //}

                this2f.release();
                approx.release();
                approx2f.release();
            }
        }

        return maxRect;
    }
}
