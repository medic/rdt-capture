package edu.washington.cs.ubicomplab.rdt_reader;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;

import org.opencv.core.Mat;;
import org.opencv.core.MatOfKeyPoint;

import org.opencv.features2d.BFMatcher;
import org.opencv.features2d.BRISK;

import java.io.File;
import java.util.Arrays;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static edu.washington.cs.ubicomplab.rdt_reader.Constants.CAMERA2_PREVIEW_SIZE;
import static edu.washington.cs.ubicomplab.rdt_reader.Constants.CAPTURE_COUNT;
import static edu.washington.cs.ubicomplab.rdt_reader.Constants.CAMERA2_IMAGE_SIZE;
import static edu.washington.cs.ubicomplab.rdt_reader.Constants.MY_PERMISSION_REQUEST_CODE;
import static edu.washington.cs.ubicomplab.rdt_reader.Constants.REQUEST_CAMERA_PERMISSION;

public class ImageQualityActivity extends AppCompatActivity implements View.OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback {
    private ImageProcessor processor;
    private Activity mActivity = this;
    private File mFile;
    private BRISK mFeatureDetector;
    private BFMatcher mMatcher;
    private Mat mRefImg;
    private Mat mRefDescriptor;
    private MatOfKeyPoint mRefKeypoints;
    private TextView mImageQualityFeedbackView;
    private TextView mProgressText;
    private ProgressBar mProgress;
    private ProgressBar mCaptureProgressBar;
    private View mProgressBackgroundView;
    private TextView mInstructionText;
    private State mCurrentState = State.QUALITY_CHECK;

    private Integer cameraHeight = null;
    private Integer cameraWidth = null;

    private boolean isRequestingPermissions = false;

    private long timeTaken = 0;


    private ViewportUsingBitmap mViewport;

    private FocusState mFocusState = FocusState.INACTIVE;

    private enum State {
        QUALITY_CHECK, FINAL_CHECK
    }

    private enum FocusState {
        INACTIVE, FOCUSING, FOCUSED, UNFOCUSED
    }

    private Activity thisActivity = this;

    ImageUtil imageUtil;

    private static final String TAG = ImageQualityActivity.class.getName();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_quality);


        mTextureView = findViewById(R.id.texture);

        mFile = new File(getExternalFilesDir(null), "pic.jpg");

        timeTaken = System.currentTimeMillis();

        imageUtil = new ImageUtil();

        initViews();

    }

    private boolean isExternalIntent() {
        Intent i = getIntent();
        return i != null && "medic.mrdt.verify".equals(i.getAction());
    }

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;
    /**
     * ID of the current {@link CameraDevice}.
     */
    private String mCameraId;

    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;



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
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mTextureView;


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
            if (null != cameraDevice && null != mCameraDevice) {
                cameraDevice.close();
                mCameraDevice = null;
            }
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

    private boolean inProcess = false;
    final Object lock = new Object();
    final Object focusStateLock = new Object();

    final BlockingQueue<Image> imageQueue = new ArrayBlockingQueue<>(1);

    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {

            if (reader == null) {
                return;
            }

            final Image image = reader.acquireLatestImage();
            Log.d(TAG, "OnImageAvailableListener: image acquired! " + System.currentTimeMillis());

            if (image == null) {
                return;
            }

            if (imageQueue.size() > 0) {
                return;
            }



            Log.d(TAG, "LOCAL FOCUS STATE: " + mFocusState + ", " + FocusState.FOCUSED);
            if (mFocusState != FocusState.FOCUSED) {
                image.close();
                return;
            }

            imageQueue.add(image);

            final Mat rgbaMat = ImageUtil.imageToRGBMat(image);
            final ImageProcessor.CaptureResult result = processor.captureRDT(rgbaMat);
            //ImageProcessor.SizeResult sizeResult, boolean isCentered, boolean isRightOrientation, boolean isSharp, ImageProcessor.ExposureResult exposureResult
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    displayQualityResult(result.sizeResult, result.isCentered, result.isRightOrientation, result.isSharp, result.exposureResult);
                }
            });

            Log.d(TAG, String.format("Capture time: %d", System.currentTimeMillis() - timeTaken));
            Log.d(TAG, String.format("Captured result: %b", result.allChecksPassed));
            if (result.allChecksPassed && result.resultMat != null) {
                Log.d(TAG, String.format("Captured MAT size: %s", result.resultMat.size()));
                useCapturedImage(result.resultMat);
            } else {
                imageQueue.remove();
                image.close();
            }
        }
    };

    protected void useCapturedImage(Mat result) {
        moveToResultActivity(result);
    }

    private void moveToResultActivity(Mat result) {
        byte[] byteArray = ImageUtil.matToRotatedByteArray(result);
        if (isExternalIntent()) {
            Intent i = new Intent();

            i.putExtra("data", byteArray);
            i.putExtra("timeTaken", System.currentTimeMillis() - timeTaken);

            setResult(Activity.RESULT_OK, i);
            mOnImageAvailableThread.interrupt();
            finish();
        } else {
            Intent intent = new Intent(ImageQualityActivity.this, ImageResultActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
            intent.putExtra("RDTCaptureByteArray", byteArray);
            intent.putExtra("timeTaken", System.currentTimeMillis() - timeTaken);
            result.release();
            startActivity(intent);
            mOnImageAvailableThread.interrupt();
        }
    }

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
     */
    private CaptureRequest mPreviewRequest;


    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */

    private int counter = 0;
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            synchronized (focusStateLock) {
                FocusState previousFocusState = mFocusState;
                if (result.get(CaptureResult.CONTROL_AF_MODE) == null) {
                    Log.d(TAG, "FOCUS STATE: is null");
                    return;
                }
                if (result.get(CaptureResult.CONTROL_AF_MODE) == CaptureResult.CONTROL_AF_MODE_CONTINUOUS_PICTURE) {
                    if (result.get(CaptureResult.CONTROL_AF_STATE) == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED) {
                        Log.d(TAG, "FOCUS STATE: focused");
                        mFocusState = FocusState.FOCUSED;
                    } else if (result.get(CaptureResult.CONTROL_AF_STATE) == CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED) {
                        Log.d(TAG, "FOCUS STATE: unfocused");
                        mFocusState = FocusState.UNFOCUSED;
                    } else if (result.get(CaptureResult.CONTROL_AF_STATE) == CaptureResult.CONTROL_AF_STATE_INACTIVE) {
                        Log.d(TAG, "FOCUS STATE: inactive");
                        mFocusState = FocusState.INACTIVE;
                    } else if (result.get(CaptureResult.CONTROL_AF_STATE) == CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN) {
                        Log.d(TAG, "FOCUS STATE: focusing");
                        mFocusState = FocusState.FOCUSING;
                    } else {
                        Log.d(TAG, "FOCUS STATE: unknown state " + result.get(CaptureResult.CONTROL_AF_STATE).toString());
                    }

                    if (previousFocusState != mFocusState) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                displayQualityResultFocusChanged();
                            }
                        });
                    }
                }
            }

            if (!Build.MODEL.equals("TECNO-W3")) {
                if (counter++ % 10 == 0)
                    updateRepeatingRequest();
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
            ImageProcessor.loadOpenCV(getApplicationContext(), mLoaderCallback);
        }

    }

    @Override
    public void onPause() {
        super.onPause();
        if (!isRequestingPermissions) {
            closeCamera();
            stopBackgroundThread();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults){
        isRequestingPermissions = false;
        if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera(cameraWidth, cameraHeight);
            } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                showToast("RDT image capture requires camera permission");
                Intent resultIntent = new Intent();
                setResult(RESULT_CANCELED, resultIntent);
                finish();
            }
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

                // choose optimal size
                Size closestPreviewSize = new Size(Integer.MAX_VALUE, (int) (Integer.MAX_VALUE * (9.0 / 16.0)));
                Size closestImageSize = new Size(Integer.MAX_VALUE, (int) (Integer.MAX_VALUE * (3.0 / 4.0)));
                for (Size size : Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888))) {
                    Log.d(TAG, "Available Sizes: " + size.toString());
                    if (size.getWidth() * 9 == size.getHeight() * 16) { //Preview surface ratio is 16:9
                        double currPreviewDiff = (CAMERA2_PREVIEW_SIZE.height * CAMERA2_PREVIEW_SIZE.width) - closestPreviewSize.getHeight() * closestPreviewSize.getWidth();
                        double newPreviewDiff = (CAMERA2_PREVIEW_SIZE.height * CAMERA2_PREVIEW_SIZE.width) - size.getHeight() * size.getWidth();

                        double currImageDiff = (CAMERA2_IMAGE_SIZE.height * CAMERA2_IMAGE_SIZE.width) - closestImageSize.getHeight() * closestImageSize.getWidth();
                        double newImageDiff = (CAMERA2_IMAGE_SIZE.height * CAMERA2_IMAGE_SIZE.width) - size.getHeight() * size.getWidth();

                        if (Math.abs(currPreviewDiff) > Math.abs(newPreviewDiff)) {
                            closestPreviewSize = size;
                        }

                        if (Math.abs(currImageDiff) > Math.abs(newImageDiff)) {
                            closestImageSize = size;
                        }
                    }
                }

                Log.d(TAG, "Selected sizes: " + closestPreviewSize.toString() + ", " + closestImageSize.toString());

                mPreviewSize = closestPreviewSize;
                mImageReader = ImageReader.newInstance(closestImageSize.getWidth(), closestImageSize.getHeight(),
                        ImageFormat.YUV_420_888, /*maxImages*/5);
                mImageReader.setOnImageAvailableListener(
                        mOnImageAvailableListener, mOnImageAvailableHandler);

                Constants.CAMERA2_IMAGE_SIZE.width = closestImageSize.getWidth();
                Constants.CAMERA2_IMAGE_SIZE.height = closestImageSize.getHeight();

                Constants.CAMERA2_PREVIEW_SIZE.width = closestPreviewSize.getWidth();
                Constants.CAMERA2_PREVIEW_SIZE.height = closestPreviewSize.getHeight();

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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraHeight = height;
            cameraWidth = width;
            isRequestingPermissions = true;
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
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
        Log.d(TAG, "Thread Quit Safely.");
        mBackgroundThread.quit();
        mOnImageAvailableThread.quit();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;

            mOnImageAvailableThread = null;
            mOnImageAvailableHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private CameraCaptureSession.StateCallback mCameraCaptureSessionStateCallback = new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            // The camera is already closed
            if (null == mCameraDevice) {
                return;
            }
            mCaptureSession = cameraCaptureSession;

            updateRepeatingRequest();
            Log.e(TAG, "Successfully configured camera!");
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
            Log.e(TAG, "Camera configuration failed!");
        }
    };

    private int mCounter = Integer.MIN_VALUE;

    private void updateRepeatingRequest() {
        // When the session is ready, we start displaying the preview.
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);


            final android.graphics.Rect sensor = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            Log.d(TAG, "Sensor size: " + sensor.width() + ", " + sensor.height());
            MeteringRectangle mr = new MeteringRectangle(sensor.width() / 2 - 50, sensor.height() / 2 - 50, 100 + (mCounter % 2), 100 + (mCounter % 2),
                    MeteringRectangle.METERING_WEIGHT_MAX - 1);

            Log.d(TAG, String.format("Sensor Size (%d, %d), Metering %s", sensor.width(), sensor.height(), mr.toString()));
            Log.d(TAG, String.format("Regions AE %s", characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE).toString()));

            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS,
                    new MeteringRectangle[]{mr});
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS,
                    new MeteringRectangle[]{mr});
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_REGIONS,
                    new MeteringRectangle[]{mr});

            mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
            mPreviewRequest = mPreviewRequestBuilder.build();

            try {
                mCameraOpenCloseLock.acquire();
                if (mCaptureSession != null) {
                    mCaptureSession.setRepeatingRequest(mPreviewRequest,
                            mCaptureCallback, mBackgroundHandler);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
            } finally {
                mCameraOpenCloseLock.release();
            }

            mCounter++;
        } catch (CameraAccessException e) {
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
                    mCameraCaptureSessionStateCallback, null);
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


    @Override
    public void onClick(View view) {
        int i = view.getId();
        if (i == R.id.viewport) {
            updateRepeatingRequest();
        }
    }


    /**
     * Imported from ImageQualityOpencvActivity
     **/


    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            if (status == LoaderCallbackInterface.SUCCESS) {
                Log.i(TAG, "OpenCV loaded successfully");
                processor = ImageProcessor.getInstance(thisActivity);
            } else {
                super.onManagerConnected(status);

            }
        }
    };

    private void initViews() {
        setTitle("Image Quality Checker");

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Instructions are set here

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

    private void setProgressUI(State CurrentState) {
        if (CurrentState == State.QUALITY_CHECK) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mProgress.setVisibility(View.GONE);
                    mProgressBackgroundView.setVisibility(View.GONE);
                    mProgressText.setVisibility(View.GONE);
                    mCaptureProgressBar.setVisibility(View.GONE);
                }
            });

        } else if (CurrentState == State.FINAL_CHECK) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mProgress.setVisibility(View.VISIBLE);
                    mProgressBackgroundView.setVisibility(View.VISIBLE);
                    mProgressText.setText(R.string.progress_final);
                    mProgressText.setVisibility(View.VISIBLE);
                    mCaptureProgressBar.setVisibility(View.GONE);
                }
            });

        }

    }

    private void displayQualityResult(ImageProcessor.SizeResult sizeResult, boolean isCentered, boolean isRightOrientation, boolean isSharp, ImageProcessor.ExposureResult exposureResult) {
        FocusState currFocusState;

        synchronized (focusStateLock) {
            currFocusState = mFocusState;
        }

        if (currFocusState == FocusState.FOCUSED) {
            String[] qChecks = processor.getQualityCheckText(sizeResult, isCentered, isRightOrientation, isSharp, exposureResult);
            String message = String.format(getResources().getString(R.string.quality_msg_format_text), qChecks[0], qChecks[1], qChecks[2], qChecks[3]);

            mInstructionText.setText(getResources().getText(processor.getInstructionText(sizeResult, isCentered, isRightOrientation)));

            mImageQualityFeedbackView.setText(Html.fromHtml(message));
            if (sizeResult == ImageProcessor.SizeResult.RIGHT_SIZE && isCentered && isRightOrientation && isSharp && exposureResult == ImageProcessor.ExposureResult.NORMAL) {
                if (mViewport.getBackgroundColorId() != R.color.green_overlay) {
                    mViewport.setBackgroundColoId(R.color.green_overlay);
                }
            } else {
                if (mViewport.getBackgroundColorId() != R.color.red_overlay) {
                    mViewport.setBackgroundColoId(R.color.red_overlay);
                }
            }
        } else if (currFocusState == FocusState.INACTIVE) {
            mInstructionText.setText(getResources().getString(R.string.instruction_pos));
            mViewport.setBackgroundColoId(R.color.red_overlay);
        } else if (currFocusState == FocusState.UNFOCUSED) {
            mInstructionText.setText(getResources().getString(R.string.instruction_unfocused));
            mViewport.setBackgroundColoId(R.color.red_overlay);
        } else if (currFocusState == FocusState.FOCUSING) {
            mInstructionText.setText(getResources().getString(R.string.instruction_focusing));
            mViewport.setBackgroundColoId(R.color.red_overlay);
        }
    }

    private void displayQualityResultFocusChanged() {
        FocusState currFocusState;

        synchronized (focusStateLock) {
            currFocusState = mFocusState;
        }

        if (currFocusState == FocusState.FOCUSED) {
            mInstructionText.setText(getResources().getString(R.string.instruction_pos));
            String message = String.format(getResources().getString(R.string.quality_msg_format), "FAILED", "FAILED", "FAILED", "FAILED");
            mImageQualityFeedbackView.setText(Html.fromHtml(message));
        } else if (currFocusState == FocusState.INACTIVE) {
            mInstructionText.setText(getResources().getString(R.string.instruction_pos));
            mViewport.setBackgroundColoId(R.color.red_overlay);
        } else if (currFocusState == FocusState.UNFOCUSED) {
            mInstructionText.setText(getResources().getString(R.string.instruction_unfocused));
            mViewport.setBackgroundColoId(R.color.red_overlay);
        } else if (currFocusState == FocusState.FOCUSING) {
            mInstructionText.setText(getResources().getString(R.string.instruction_focusing));
            mViewport.setBackgroundColoId(R.color.red_overlay);
        }
    }

}
