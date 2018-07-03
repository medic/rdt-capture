package edu.washington.cs.ubicomplab.rdt_reader;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.support.annotation.Dimension;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static edu.washington.cs.ubicomplab.rdt_reader.Constants.TAG;

public class Camera2TestActivity extends AppCompatActivity implements CvCameraViewListener2, View.OnClickListener {
    private RDTCamera2View mOpenCvCameraView;
    private Button mAutoCenterButton;
    private Button mFocusChangeButton;
    private Button mExpChangeButton;
    private Button mFlashButton;
    private LineChart mHistogramChart;

    private boolean mCenterAFAE = false;
    private boolean mManualFocus = false;
    private int mCurrentStep = 0;
    private boolean mFlashOn = false;

    private ColorBlobDetector mDetector;
    private final Scalar CONTOUR_COLOR = new Scalar(255,0,0,255);

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera2_test);

        setTitle("Camera2 Test");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        mHistogramChart = findViewById(R.id.histogram);
        mHistogramChart.getLegend().setEnabled(false);
        mHistogramChart.getAxisRight().setEnabled(false);
        mHistogramChart.getDescription().setEnabled(false);
        mHistogramChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        mHistogramChart.getXAxis().setTextColor(Color.WHITE);
        mHistogramChart.getXAxis().setGranularity(50f);
        mHistogramChart.getAxisLeft().setTextColor(Color.WHITE);

        mOpenCvCameraView = findViewById(R.id.camera2_test_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        mAutoCenterButton = findViewById(R.id.autoCenterButton);
        mFocusChangeButton = findViewById(R.id.focusChangeButton);
        mExpChangeButton = findViewById(R.id.exposureChangeButton);
        mFlashButton = findViewById(R.id.flashButton);

        mAutoCenterButton.setOnClickListener(this);
        mFocusChangeButton.setOnClickListener(this);
        mExpChangeButton.setOnClickListener(this);
        mFlashButton.setOnClickListener(this);
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
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.autoCenterButton) {
            changeAutoExposureToCenter();
        } else if (view.getId() == R.id.focusChangeButton) {
            changeFocusDistance();
        } else if (view.getId() == R.id.exposureChangeButton) {
            changeExposure();
        } else if (view.getId() == R.id.flashButton) {
            changeFlashMode();
        }
    }

    /*OpenCV JavaCameraView callbacks*/
    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        showHistogram(inputFrame.rgba(), inputFrame.gray());
        return whiteDetection(inputFrame.rgba());
        //return inputFrame.rgba();
    }

    /*Private methods*/
    private void changeExposure() {
        try {
            CameraCharacteristics characteristics = mOpenCvCameraView.mCameraManager.getCameraCharacteristics(mOpenCvCameraView.mCameraID);
            Range<Integer> range = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
            int minExposure = range.getLower();
            int maxExposure = range.getUpper();

            Rational step = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);

            Log.d(TAG, String.format("Exposure Comp Range: %d ~ %d", minExposure, maxExposure));
            Log.d(TAG, String.format("Exposure Comp: %.5f", step.doubleValue()));
            Log.d(TAG, String.format("Exposure Comp Step: %d", mCurrentStep));

            mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, mCurrentStep);

            mOpenCvCameraView.mCaptureSession.setRepeatingRequest(mOpenCvCameraView.mPreviewRequestBuilder.build(), null, null);

            mCurrentStep = (mCurrentStep < maxExposure) ? mCurrentStep + 1: minExposure;
        } catch (Exception e) {
            Log.e(TAG, "createCaptureSession failed", e);
        }
    }

    private void changeFocusDistance() {
        try {
            if (!mManualFocus) {
                CameraCharacteristics characteristics = mOpenCvCameraView.mCameraManager.getCameraCharacteristics(mOpenCvCameraView.mCameraID);
                float[] focusDistances = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                float infiniteDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
                float hyperfocalDistance = characteristics.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE);

                Log.d(TAG, String.format("Focus distances length: %d", focusDistances.length));

                Log.d(TAG, String.format("Focus range length: %.5f ~ %.5f", hyperfocalDistance, infiniteDistance));
                for (float distance : focusDistances) {
                    Log.d(TAG, String.format("Focus distance: %.5f", distance));
                }

                mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
                mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f);

                mOpenCvCameraView.mCaptureSession.setRepeatingRequest(mOpenCvCameraView.mPreviewRequestBuilder.build(), null, null);

                mManualFocus = true;
            } else {
                mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                mOpenCvCameraView.mCaptureSession.setRepeatingRequest(mOpenCvCameraView.mPreviewRequestBuilder.build(), null, null);

                mManualFocus = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "createCaptureSession failed", e);
        }
    }

    private void changeAutoExposureToCenter() {
        try {
            if (!mCenterAFAE) {
                CameraCharacteristics characteristics = mOpenCvCameraView.mCameraManager.getCameraCharacteristics(mOpenCvCameraView.mCameraID);
                final Rect sensor = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

                mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS,
                        new MeteringRectangle[]{new MeteringRectangle(sensor.width() / 2 - 50, sensor.height() / 2 - 50, 100, 100,
                                MeteringRectangle.METERING_WEIGHT_MAX - 1)});
                mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS,
                        new MeteringRectangle[]{new MeteringRectangle(sensor.width() / 2 - 50, sensor.height() / 2 - 50, 100, 100,
                                MeteringRectangle.METERING_WEIGHT_MAX - 1)});
                mOpenCvCameraView.mPreviewRequestBuilder.setTag("CENTER_AF_AE_TAG");

                mOpenCvCameraView.mCaptureSession.setRepeatingRequest(mOpenCvCameraView.mPreviewRequestBuilder.build(), null, null);
                Log.i(TAG, "CameraPreviewSession has been centralized");

                mCenterAFAE = true;
            } else {
                CameraCharacteristics characteristics = mOpenCvCameraView.mCameraManager.getCameraCharacteristics(mOpenCvCameraView.mCameraID);
                final Rect sensor = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

                mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS,
                        new MeteringRectangle[]{new MeteringRectangle(0, 0, sensor.width(), sensor.height(),
                                0)});
                mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS,
                        new MeteringRectangle[]{new MeteringRectangle(0, 0, sensor.width(), sensor.height(),
                                0)});
                mOpenCvCameraView.mPreviewRequestBuilder.setTag("CENTER_AF_AE_TAG");

                mOpenCvCameraView.mCaptureSession.setRepeatingRequest(mOpenCvCameraView.mPreviewRequestBuilder.build(), null, null);
                Log.i(TAG, "CameraPreviewSession has been reset");

                mCenterAFAE = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "createCaptureSession failed", e);
        }
    }

    private void changeFlashMode () {
        try {
            if (!mFlashOn) {
                mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                mOpenCvCameraView.mCaptureSession.setRepeatingRequest(mOpenCvCameraView.mPreviewRequestBuilder.build(), null, null);

                mFlashOn = true;
            } else {
                mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                mOpenCvCameraView.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                mOpenCvCameraView.mCaptureSession.setRepeatingRequest(mOpenCvCameraView.mPreviewRequestBuilder.build(), null, null);

                mFlashOn = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "createCaptureSession failed", e);
        }
    }

    private Mat showHistogram (Mat image, Mat gray) {
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

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                List<Entry> floatData = new ArrayList<>();
                for(int h = 0; h < mBuff.length; h++)
                    floatData.add(new Entry(h,mBuff[h]));

                LineDataSet data = new LineDataSet(floatData, "Histogram");
                mHistogramChart.setData(new LineData(data));
                mHistogramChart.invalidate();
            }
        });

        return image;
    }

    private Mat whiteDetection (Mat input) {
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

                Log.e(TAG, "Contours corners: " + approx.size());

                //if (approx.size().height == 4) {
                    org.opencv.core.Rect rect = Imgproc.boundingRect(approx);
                    Imgproc.rectangle(input,rect.br(), rect.tl(), new Scalar(0,0,255,255), 5);
                //}
            }
        }

        return input;
    }
}
