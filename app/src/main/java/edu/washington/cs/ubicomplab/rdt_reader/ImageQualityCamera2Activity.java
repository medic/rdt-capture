package edu.washington.cs.ubicomplab.rdt_reader;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.features2d.BRISK;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static edu.washington.cs.ubicomplab.rdt_reader.Constants.CAPTURE_COUNT;
import static edu.washington.cs.ubicomplab.rdt_reader.Constants.TAG;

public class ImageQualityCamera2Activity extends AppCompatActivity implements View.OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback {

    private Activity mActivity = this;
    private File mFile;
    private BRISK mFeatureDetector;
    private DescriptorMatcher mMatcher;
    private Mat mRefImg;
    private Mat mRefDescriptor;
    private MatOfKeyPoint mRefKeypoints;
    private TextView mImageQualityFeedbackView;
    private TextView mProgressText;
    private TextView mInstructionText;
    private ProgressBar mProgress;
    private ProgressBar mCaptureProgressBar;
    private View mProgressBackgroundView;
    private State mCurrentState = State.QUALITY_CHECK;
    private ImageQualityCheckTask mQualityCheckTask;
    private Mat bestCapturedMat;
    private double minDistance = Double.MAX_VALUE;
    private boolean minDistanceUpdated = false;

    private long timeTaken = 0;

    private double minBlur = Double.MIN_VALUE;
    private double maxBlur = Double.MAX_VALUE;
    private ViewportUsingBitmap mViewport;

    private enum State {
        QUALITY_CHECK, FINAL_CHECK
    }

    private enum ExposureResult {
        UNDER_EXPOSED, NORMAL, OVER_EXPOSED
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_quality_camera2);

        findViewById(R.id.picture).setOnClickListener(this);
        mTextureView = findViewById(R.id.texture);

        mFile = new File(getExternalFilesDir(null), "pic.jpg");

        timeTaken = System.currentTimeMillis();
        mQualityCheckTask = new ImageQualityCheckTask();

        initViews();
    }

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_CAMERA_PERMISSION = 1;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "Camera2BasicFragment";

    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {

        }

    };

    /**
     * ID of the current {@link CameraDevice}.
     */
    private String mCameraId;

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mTextureView;

    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * The {@link android.util.Size} of camera preview.
     */
    private Size mPreviewSize;

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }


        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
        }

    };

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mOnImageAvailableThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mOnImageAvailableHandler;

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReader;

    private boolean mCapture;
    private boolean inProcess = false;
    Object lock = new Object();

    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {

            final Image image = mImageReader.acquireLatestImage();

            final boolean running;

            synchronized (lock) {
                running = inProcess;
            }

            Log.d(TAG, "OnImageAvailableListener: " + System.currentTimeMillis());

            if (!running) {
                mOnImageAvailableHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        //mQualityCheckTask = new ImageQualityCheckTask();
                        //mQualityCheckTask.execute(image);

                        synchronized (lock) {
                            inProcess = true;
                        }

                        Mat rgbaMat = ImageUtil.imageToRGBMat(image);
                        Mat grayMat = new Mat();
                        Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGBA2GRAY);

                        Log.d(TAG, "rgbaMat 0 Size: "+rgbaMat.size().toString() + ", grayMat 1 Size: "+grayMat.size().toString());

                        Mat matchingMat = grayMat.clone();
                        Mat blurMat = rgbaMat.clone();
                        Mat exposureMat = grayMat.clone();



                        matchingMat = matchingMat.submat(new Rect((int)(Constants.PREVIEW_SIZE.height/4), (int)(Constants.PREVIEW_SIZE.width/4), (int)(Constants.PREVIEW_SIZE.height*Constants.VIEWPORT_SCALE), (int)(Constants.PREVIEW_SIZE.width*Constants.VIEWPORT_SCALE)));


                        //MatOfPoint2f approx = detectRDT(mats[0].submat(new Rect((int)(PREVIEW_SIZE.width/4), (int)(PREVIEW_SIZE.height/4), (int)(PREVIEW_SIZE.width*VIEWPORT_SCALE), (int)(PREVIEW_SIZE.height*VIEWPORT_SCALE))));
                        MatOfPoint2f approx = detectRDT(matchingMat);
                        matchingMat.release();
                        grayMat.release();


                        final boolean[] isCorrectPosSize = checkPositionAndSize(approx, true);


                        float[] histogram = calculateHistogram(exposureMat);

                        int maxWhite = 0;
                        float whiteCount = 0;

                        for (int i = 0; i < histogram.length; i++) {
                            if (histogram[i] > 0) {
                                maxWhite = i;
                            }

                            if (i == histogram.length-1) {
                                whiteCount = histogram[i];
                            }
                        }
                        Log.d(TAG, "rgbaMat 2 Size: "+exposureMat.size().toString());

                        exposureMat.release();

                        ExposureResult exposureResult;

                        if (maxWhite >= Constants.OVER_EXP_THRESHOLD && whiteCount > Constants.OVER_EXP_WHITE_COUNT)
                            exposureResult = ExposureResult.OVER_EXPOSED;
                        else if (maxWhite < Constants.UNDER_EXP_THRESHOLD)
                            exposureResult = ExposureResult.UNDER_EXPOSED;
                        else
                            exposureResult = ExposureResult.NORMAL;


                        double blurVal = calculateBurriness(blurMat);

                        blurMat.release();

                        Log.d(TAG, "BLUR CHECK: "+ blurVal*Constants.BLUR_THRESHOLD + ", " + maxBlur);

                        final boolean isBlur = blurVal < maxBlur * Constants.BLUR_THRESHOLD;


                        grayMat.release();


                        try {
                            final boolean isOverExposed = exposureResult == ExposureResult.OVER_EXPOSED;
                            final boolean isUnderExposed = exposureResult == ExposureResult.UNDER_EXPOSED;
                            final boolean isShadow = false;


                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    displayQualityResult(isCorrectPosSize, isBlur, isOverExposed, isUnderExposed, isShadow);
                                }
                            });

                            if (isCorrectPosSize[0] && isCorrectPosSize[1] && isCorrectPosSize[2] && !isBlur && !isOverExposed && !isUnderExposed) {
                                mCaptureProgressBar.incrementProgressBy(1);

                                if (minDistanceUpdated) {
                                    bestCapturedMat = rgbaMat.clone();
                                    minDistanceUpdated = false;
                                }

                                if (mCaptureProgressBar.getProgress() >= CAPTURE_COUNT) {
                                    Log.d(TAG, String.format("Average DISTANCE (MIN): %.2f", minDistance));

                                    setNextState(mCurrentState);
                                    setProgressUI(mCurrentState);

                                    Log.d(TAG, "rgbaMat 5 Size: " + bestCapturedMat.size().toString() + ", rect size: " + new Rect((int) (Constants.PREVIEW_SIZE.width / 5), (int) (Constants.PREVIEW_SIZE.height / 5), (int) (Constants.PREVIEW_SIZE.width * 0.6), (int) (Constants.PREVIEW_SIZE.height * 0.6)).size().toString());
                                    byte[] byteArray = ImageUtil.matToRotatedByteArray(bestCapturedMat.submat(new Rect((int) (Constants.PREVIEW_SIZE.width / 5), (int) (Constants.PREVIEW_SIZE.height / 5), (int) (Constants.PREVIEW_SIZE.width * 0.6), (int) (Constants.PREVIEW_SIZE.height * 0.6))));

                                    Intent intent = new Intent(ImageQualityCamera2Activity.this, ImageResultActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
                                    intent.putExtra("RDTCaptureByteArray", byteArray);
                                    intent.putExtra("timeTaken", System.currentTimeMillis() - timeTaken);

                                    rgbaMat.release();
                                    startActivity(intent);
                                } else {
                                    rgbaMat.release();
                                }
                            } else {
                                rgbaMat.release();
                            }
                        } catch (Exception e) {
                            rgbaMat.release();
                        }

                        synchronized (lock) {
                            inProcess = false;
                            Log.d(TAG, "OnImageAvailableListener: computed");
                        }
                    }
                });
            } else if (mCapture) {
                mBackgroundHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Mat rgbMat = ImageUtil.imageToRGBMat(image);

                        //byte[] byteArray = ImageUtil.imageToByteArray(image);

                        //byte[] nv21 = ImageUtil.YUV_420_888toI420SemiPlanar(image.getPlanes()[0].getBuffer(), image.getPlanes()[1].getBuffer(), image.getPlanes()[2].getBuffer(), image.getWidth(), image.getHeight(), false);
                        //byte[] byteArray = ImageUtil.NV21toJPEG(nv21, image.getWidth(), image.getHeight(), 100);

                        //save the image to see
                        try {
                            byte[] byteArray = ImageUtil.matToRotatedByteArray(rgbMat);
                            FileOutputStream fileOutputStream = new FileOutputStream(mFile);

                            fileOutputStream.write(byteArray);

                            fileOutputStream.flush();
                            fileOutputStream.close();

                            Toast.makeText(mActivity,"Image is successfully saved!", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Log.w("TAG", "Error saving image file: " + e.getMessage());
                        }

                    }
                });
                mCapture = false;

                Log.d(TAG, "OnImageAvailableListener: captured");
            } else {
                Log.d(TAG, "OnImageAvailableListener: closed");
                image.close();
            }
        }

    };

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
     */
    private CaptureRequest mPreviewRequest;

    /**
     * The current state of camera state for taking pictures.
     *
     */
    private int mState = STATE_PREVIEW;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {


                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show
     */
    private void showToast(final String text) {
        final Activity activity = mActivity;
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }

        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        unloadReference();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                showToast("This sample needs camera permission.");
            }
        } else {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        }
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private void setUpCameraOutputs(int width, int height) {
        Activity activity = mActivity;
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }


                // For still image captures, we use the largest available size.
                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea());

                // For still image captures, we use the largest available size.
                Size smallest = Collections.min(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea());

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                //mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                //        width, height, smallest);

                mPreviewSize = new Size(1280, 720);
                mImageReader = ImageReader.newInstance(960, 720,
                        ImageFormat.YUV_420_888, /*maxImages*/2);
                mImageReader.setOnImageAvailableListener(
                        mOnImageAvailableListener, mOnImageAvailableHandler);

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView.setAspectRatio(
                            mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    mTextureView.setAspectRatio(
                            mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            showToast("This device doesn\\'t support Camera2 API.");
        }
    }

    /**
     * Opens the camera specified by {@link #mCameraId}.
     */
    private void openCamera(int width, int height) throws SecurityException {


        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        Activity activity = mActivity;
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());

        mOnImageAvailableThread = new HandlerThread("OnImageAvailableBackgroud");
        mOnImageAvailableThread.start();
        mOnImageAvailableHandler = new Handler(mOnImageAvailableThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);
            Surface mImageSurface = mImageReader.getSurface();


            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);


            mPreviewRequestBuilder.addTarget(surface);
            mPreviewRequestBuilder.addTarget(mImageSurface);


            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }
                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                                CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);

                                // Auto focus should be continuous for camera preview.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);


                                int counter = 0;
                                final android.graphics.Rect sensor = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                                MeteringRectangle mr = new MeteringRectangle(sensor.width() / 2 - 50, sensor.height() / 2 - 50, 100, 100,
                                        MeteringRectangle.METERING_WEIGHT_MAX - 1);

                                Log.d(TAG, String.format("Sensor Size (%d, %d), Metering %s", sensor.width(), sensor.height(), mr.toString()));
                                Log.d(TAG, String.format("Regions AE %s", characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE).toString()));

                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS,
                                        new MeteringRectangle[]{new MeteringRectangle(sensor.width() / 2 - 50+(counter%2), sensor.height() / 2 - 50+(counter%2), 100+(counter%2), 100+(counter%2),
                                                MeteringRectangle.METERING_WEIGHT_MAX - 1)});
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS,
                                        new MeteringRectangle[]{new MeteringRectangle(sensor.width() / 2 - 50+(counter%2), sensor.height() / 2 - 50+(counter%2), 100+(counter%2), 100+(counter%2),
                                                MeteringRectangle.METERING_WEIGHT_MAX - 1)});
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_REGIONS,
                                        new MeteringRectangle[]{new MeteringRectangle(sensor.width() / 2 - 50+(counter%2), sensor.height() / 2 - 50+(counter%2), 100+(counter%2), 100+(counter%2),
                                                MeteringRectangle.METERING_WEIGHT_MAX - 1)});
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
                                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);

                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);


                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            showToast("Failed");
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = mActivity;
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        try {
            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.picture: {
                //takePicture();
                mCapture = true;
                break;
            }
        }
    }



    /**
     * Imported from ImageQualityActivity
     **/



    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    loadReference();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    private void initViews() {
        setTitle("Image Quality Checker");

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        mViewport = findViewById(R.id.img_quality_check_viewport);
        mViewport.setOnClickListener(this);
        mImageQualityFeedbackView = findViewById(R.id.img_quality_feedback_view);
        mProgress = findViewById(R.id.progressCircularBar);
        mProgressBackgroundView = findViewById(R.id.progressBackground);
        mProgressText = findViewById(R.id.progressText);
        mCaptureProgressBar = findViewById(R.id.captureProgressBar);
        mCaptureProgressBar.setMax(CAPTURE_COUNT);
        mCaptureProgressBar.setProgress(0);
        mInstructionText = findViewById(R.id.textInstruction);

        setProgressUI(mCurrentState);
    }

    private void loadReference() {
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

    private void setProgressUI (State CurrentState) {
        switch  (CurrentState) {
            case QUALITY_CHECK:
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mProgress.setVisibility(View.GONE);
                        mProgressBackgroundView.setVisibility(View.GONE);
                        mProgressText.setVisibility(View.GONE);
                        mCaptureProgressBar.setVisibility(View.VISIBLE);
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
                        mCaptureProgressBar.setVisibility(View.VISIBLE);
                    }
                });
                break;
        }

    }

    private void displayQualityResult (boolean[] isCorrectPosSize, boolean isBlur, boolean isOverExposed, boolean isUnderExposed, boolean isShadow) {
        String message = String.format(getResources().getString(R.string.quality_msg_format), isCorrectPosSize[0] && isCorrectPosSize[1] && isCorrectPosSize[2] ? Constants.OK: Constants.NOT_OK,
                !isBlur ? Constants.OK : Constants.NOT_OK,
                !isOverExposed && !isUnderExposed ? Constants.OK : (isOverExposed ? getResources().getString(R.string.over_exposed_msg) + Constants.NOT_OK : getResources().getString(R.string.under_exposed_msg) + Constants.NOT_OK),
                !isShadow ? Constants.OK : Constants.NOT_OK);

        //mInstructionText.setText("");

        if (isCorrectPosSize[1] && isCorrectPosSize[0] & isCorrectPosSize[2]) {
            mInstructionText.setText("RDT at the center!");
        } else if (!isCorrectPosSize[1] && isCorrectPosSize[3]) {
            mInstructionText.setText(getResources().getString(R.string.instruction_too_large));
        } else if (!isCorrectPosSize[1] && isCorrectPosSize[4]) {
            mInstructionText.setText(getResources().getString(R.string.instruction_too_small));
        } else if (isCorrectPosSize[1] && !isCorrectPosSize[0]) {
            mInstructionText.setText(getResources().getString(R.string.instruction_pos));
        }

        mImageQualityFeedbackView.setText(Html.fromHtml(message));
        if (isCorrectPosSize[0] && isCorrectPosSize[1] && isCorrectPosSize[2] && !isBlur && !isOverExposed && !isUnderExposed && !isShadow) {
            if (mViewport.getBackgroundColorId() != R.color.green_overlay) {
                mViewport.setBackgroundColoId(R.color.green_overlay);
            }
        } else {
            if (mViewport.getBackgroundColorId() != R.color.red_overlay) {
                mViewport.setBackgroundColoId(R.color.red_overlay);
            }
        }
    }


    private void setNextState (State currentState) {
        switch (currentState) {
            case QUALITY_CHECK:
                mCurrentState = State.FINAL_CHECK;
                //mResetCameraNeeded = false;
                break;
            case FINAL_CHECK:
                mCurrentState = State.FINAL_CHECK;
                //mResetCameraNeeded = false;
                break;
        }

        setProgressUI(mCurrentState);
    }


    private MatOfPoint2f detectRDT(Mat input) {
        long veryStart = System.currentTimeMillis();

        Mat descriptors = new Mat();
        MatOfKeyPoint keypoints = new MatOfKeyPoint();

        long startTime = System.currentTimeMillis();
        mFeatureDetector.detect(input, keypoints);
        mFeatureDetector.compute(input, keypoints, descriptors);
        Log.d(TAG, "detect/compute TIME: " + (System.currentTimeMillis()-startTime));

        org.opencv.core.Size size = descriptors.size();

        if (size.equals(new org.opencv.core.Size(0,0))) {
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

        Double max_dist = Double.MIN_VALUE;
        Double min_dist = Double.MAX_VALUE;

        for (int i = 0; i < matchesList.size(); i++) {
            Double dist = (double) matchesList.get(i).distance;
            if (dist < min_dist)
                min_dist = dist;
            if (dist > max_dist)
                max_dist = dist;
        }

        double sum = 0;
        int count = 0;

        LinkedList<DMatch> good_matches = new LinkedList<DMatch>();
        for (int i = 0; i < matchesList.size(); i++) {
            if (matchesList.get(i).distance <= (1.5 * min_dist)) {
                good_matches.addLast(matchesList.get(i));
                sum += matchesList.get(i).distance;
                count++;
            }
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

                Log.d(TAG, String.format("Average DISTANCE: %.2f", sum/count));

                if(mCurrentState == State.QUALITY_CHECK && sum/count < minDistance) {
                    minDistance = sum/count;
                    minDistanceUpdated = true;
                }
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

    private boolean[] checkPositionAndSize (MatOfPoint2f approx, boolean cropped) {
        boolean results[] = {false, false, false, false, false};

        if (approx.total() < 1)
            return results;

        RotatedRect rotatedRect = Imgproc.minAreaRect(approx);
        if (cropped)
            rotatedRect.center = new Point(rotatedRect.center.x + Constants.PREVIEW_SIZE.width/4, rotatedRect.center.y + Constants.PREVIEW_SIZE.height/4);

        Point center = rotatedRect.center;
        Point trueCenter = new Point(Constants.PREVIEW_SIZE.width/2, Constants.PREVIEW_SIZE.height/2);

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

        boolean isCentered = center.x < trueCenter.x *(1+ Constants.POSITION_THRESHOLD) && center.x > trueCenter.x*(1- Constants.POSITION_THRESHOLD)
                && center.y < trueCenter.y *(1+ Constants.POSITION_THRESHOLD) && center.y > trueCenter.y*(1- Constants.POSITION_THRESHOLD);
        boolean isRightSize = height < Constants.PREVIEW_SIZE.width*Constants.VIEWPORT_SCALE*(1+Constants.SIZE_THRESHOLD) && height > Constants.PREVIEW_SIZE.height*Constants.VIEWPORT_SCALE*(1-Constants.SIZE_THRESHOLD);
        boolean isOriented = angle < 90.0*Constants.POSITION_THRESHOLD;

        results[0] = isCentered;
        results[1] = isRightSize;
        results[2] = isOriented;
        results[3] = height > Constants.PREVIEW_SIZE.width*Constants.VIEWPORT_SCALE*(1+Constants.SIZE_THRESHOLD); //large
        results[4] = height < Constants.PREVIEW_SIZE.height*Constants.VIEWPORT_SCALE*(1-Constants.SIZE_THRESHOLD); //small

        if (results[0] && results[1])
            Log.d(TAG, String.format("POS: %.2f, %.2f, Angle: %.2f, Height: %.2f", center.x, center.y, angle, height));

        return results;
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

    private float[] calculateHistogram (Mat gray) {
        int mHistSizeNum =256;
        MatOfInt mHistSize = new MatOfInt(mHistSizeNum);
        Mat hist = new Mat();
        final float []mBuff = new float[mHistSizeNum];
        MatOfFloat histogramRanges = new MatOfFloat(0f, 256f);
        MatOfInt mChannels[] = new MatOfInt[] { new MatOfInt(0)};
        org.opencv.core.Size sizeRgba = gray.size();

        // GRAY
        for(int c=0; c<1; c++) {
            Imgproc.calcHist(Arrays.asList(gray), mChannels[c], new Mat(), hist,
                    mHistSize, histogramRanges);
            Core.normalize(hist, hist, sizeRgba.height/2, 0, Core.NORM_INF);
            hist.get(0, 0, mBuff);
            mChannels[c].release();
        }

        Log.d(TAG, String.format("Histogram for state %s", mCurrentState.toString()));

        mHistSize.release();
        histogramRanges.release();
        hist.release();
        return mBuff;
    }

    private class ImageQualityCheckTask extends AsyncTask<Image, Integer, Mat> {
        private RDTMathchingTask machingTask = new RDTMathchingTask();
        private BlurCheckTask blurTask = new BlurCheckTask();
        private ExposureCheckTask exposureCheckTask = new ExposureCheckTask();

        private class RDTMathchingTask extends AsyncTask<Mat, Integer, boolean[]> {

            @Override
            protected boolean[] doInBackground(Mat... mats) {
                Mat grayMat = mats[0];
                long startTime = System.currentTimeMillis();
                //Mat grayMat = mats[0].submat(new Rect((int)(Constants.PREVIEW_SIZE.width/4), (int)(Constants.PREVIEW_SIZE.height/4), (int)(Constants.PREVIEW_SIZE.width*Constants.VIEWPORT_SCALE), (int)(Constants.PREVIEW_SIZE.height*Constants.VIEWPORT_SCALE)));
                grayMat = grayMat.submat(new Rect((int)(Constants.PREVIEW_SIZE.height/4), (int)(Constants.PREVIEW_SIZE.width/4), (int)(Constants.PREVIEW_SIZE.height*Constants.VIEWPORT_SCALE), (int)(Constants.PREVIEW_SIZE.width*Constants.VIEWPORT_SCALE)));


                //MatOfPoint2f approx = detectRDT(mats[0].submat(new Rect((int)(PREVIEW_SIZE.width/4), (int)(PREVIEW_SIZE.height/4), (int)(PREVIEW_SIZE.width*VIEWPORT_SCALE), (int)(PREVIEW_SIZE.height*VIEWPORT_SCALE))));
                MatOfPoint2f approx = detectRDT(grayMat);
                mats[0].release();
                grayMat.release();


                boolean[] results = checkPositionAndSize(approx, true);

                approx.release();

                Log.d(TAG, String.format("FeatureMatchingTask TIME: %d", System.currentTimeMillis() - startTime));

                return results;
            }
        }

        private class BlurCheckTask extends AsyncTask<Mat, Integer, Boolean> {

            @Override
            protected Boolean doInBackground(Mat... mats) {
                double blurVal = calculateBurriness(mats[0]);

                mats[0].release();

                Log.d(TAG, "BLUR CHECK: "+ blurVal*Constants.BLUR_THRESHOLD + ", " + maxBlur);

                return blurVal < maxBlur * Constants.BLUR_THRESHOLD;
            }
        }

        private class ExposureCheckTask extends AsyncTask<Mat, Integer, ExposureResult> {

            @Override
            protected ExposureResult doInBackground(Mat... mats) {
                float[] histogram = calculateHistogram(mats[0]);

                int maxWhite = 0;
                float whiteCount = 0;

                for (int i = 0; i < histogram.length; i++) {
                    if (histogram[i] > 0) {
                        maxWhite = i;
                    }

                    if (i == histogram.length-1) {
                        whiteCount = histogram[i];
                    }
                }
                Log.d(TAG, "rgbaMat 2 Size: "+mats[0].size().toString());

                mats[0].release();

                if (maxWhite >= Constants.OVER_EXP_THRESHOLD && whiteCount > Constants.OVER_EXP_WHITE_COUNT)
                    return ExposureResult.OVER_EXPOSED;
                else if (maxWhite < Constants.UNDER_EXP_THRESHOLD)
                    return ExposureResult.UNDER_EXPOSED;
                else
                    return ExposureResult.NORMAL;
            }
        }


        @Override
        protected Mat doInBackground(Image... images) {
            Mat rgbaMat = ImageUtil.imageToRGBMat(images[0]);
            Mat grayMat = new Mat();
            Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGBA2GRAY);

            Log.d(TAG, "rgbaMat 0 Size: "+rgbaMat.size().toString() + ", grayMat 1 Size: "+grayMat.size().toString());

            Mat matchingMat = grayMat.clone();
            Mat blurMat = rgbaMat.clone();
            Mat exposureMat = grayMat.clone();

            machingTask.execute(matchingMat);
            blurTask.execute(blurMat);
            exposureCheckTask.execute(exposureMat);

            grayMat.release();

            return rgbaMat;
        }

        @Override
        protected void onPostExecute(Mat rgbaMat) {
            super.onPostExecute(rgbaMat);

            try {
                final boolean[] isCorrectPosSize = machingTask.get();
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

                if (isCorrectPosSize[0] && isCorrectPosSize[1] && isCorrectPosSize[2] && !isBlur && !isOverExposed && !isUnderExposed) {
                    mCaptureProgressBar.incrementProgressBy(1);

                    if (minDistanceUpdated) {
                        bestCapturedMat = rgbaMat.clone();
                        minDistanceUpdated = false;
                    }

                    if (mCaptureProgressBar.getProgress() >= CAPTURE_COUNT) {
                        Log.d(TAG, String.format("Average DISTANCE (MIN): %.2f", minDistance));

                        setNextState(mCurrentState);
                        setProgressUI(mCurrentState);

                        Log.d(TAG, "rgbaMat 5 Size: " + bestCapturedMat.size().toString() + ", rect size: " + new Rect((int) (Constants.PREVIEW_SIZE.width / 5), (int) (Constants.PREVIEW_SIZE.height / 5), (int) (Constants.PREVIEW_SIZE.width * 0.6), (int) (Constants.PREVIEW_SIZE.height * 0.6)).size().toString());
                        byte[] byteArray = ImageUtil.matToRotatedByteArray(bestCapturedMat.submat(new Rect((int) (Constants.PREVIEW_SIZE.width / 5), (int) (Constants.PREVIEW_SIZE.height / 5), (int) (Constants.PREVIEW_SIZE.width * 0.6), (int) (Constants.PREVIEW_SIZE.height * 0.6))));

                        Intent intent = new Intent(ImageQualityCamera2Activity.this, ImageResultActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
                        intent.putExtra("RDTCaptureByteArray", byteArray);
                        intent.putExtra("timeTaken", System.currentTimeMillis() - timeTaken);

                        rgbaMat.release();
                        startActivity(intent);
                    } else {
                        rgbaMat.release();
                    }
                } else {
                    rgbaMat.release();
                }
            } catch (Exception e) {
                rgbaMat.release();
            }
        }
    }
}
