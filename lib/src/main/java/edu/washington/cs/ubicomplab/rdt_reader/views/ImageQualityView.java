/*
 * Copyright (C) 2019 University of Washington Ubicomp Lab
 * All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of a BSD-style license that can be found in the LICENSE file.
 */

package edu.washington.cs.ubicomplab.rdt_reader.views;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
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
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.text.Spanned;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.core.Mat;

import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import static edu.washington.cs.ubicomplab.rdt_reader.Constants.CAMERA2_IMAGE_SIZE;
import static edu.washington.cs.ubicomplab.rdt_reader.Constants.CAMERA2_PREVIEW_SIZE;
import static edu.washington.cs.ubicomplab.rdt_reader.Constants.CAPTURE_COUNT;
import static edu.washington.cs.ubicomplab.rdt_reader.Constants.MY_PERMISSION_REQUEST_CODE;
import static edu.washington.cs.ubicomplab.rdt_reader.util.Utils.hideProgressDialog;
import static edu.washington.cs.ubicomplab.rdt_reader.util.Utils.showProgressDialog;
import edu.washington.cs.ubicomplab.rdt_reader.R;
import edu.washington.cs.ubicomplab.rdt_reader.activities.ImageQualityActivity;
import edu.washington.cs.ubicomplab.rdt_reader.core.ImageProcessor;
import edu.washington.cs.ubicomplab.rdt_reader.interfaces.ImageQualityViewListener;
import edu.washington.cs.ubicomplab.rdt_reader.core.RDTCaptureResult;
import edu.washington.cs.ubicomplab.rdt_reader.core.RDTInterpretationResult;
import edu.washington.cs.ubicomplab.rdt_reader.utils.ImageUtil;

import static edu.washington.cs.ubicomplab.rdt_reader.core.Constants.*;

/**
 * A {@link View} for showing a real-time camera feed during image capture and
 * providing real-time feedback to the user
 */
public class ImageQualityView extends LinearLayout implements View.OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback {
    private Activity mActivity;
    private static final String TAG = "ImageQualityView";

    // UI elements
    private TextView mImageQualityFeedbackView;
    private TextView mProgressText;
    private ProgressBar mProgress;
    private ProgressBar mCaptureProgressBar;
    private View mProgressBackgroundView;
    private TextView mInstructionText;
    private ViewportUsingBitmap mViewport;
    private boolean showViewport;
    private boolean showFeedback;
    private AutoFitTextureView mTextureView;

    // Image processing variables
    private ImageProcessor processor;
    private long timeTaken = 0;

    // Image capture variables
    private CameraCaptureSession mCaptureSession;
    private String mCameraId;
    private CameraDevice mCameraDevice;
    private Size mPreviewSize;
    public boolean flashEnabled = true;
    private String rdtName;

    private ImageButton btnFlashToggle;

    private long timeTaken = 0;

    private ViewportUsingBitmap mViewport;

    private FocusState mFocusState = FocusState.INACTIVE;

    private ImageQualityViewListener mImageQualityViewListener;
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    private ImageQualityViewListener mImageQualityViewListener;
    private int mMeteringCounter = Integer.MIN_VALUE;

    // Thread handling for the preview and camera hardware
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private CaptureRequest mPreviewRequest;

    // Thread handling for the image processing
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private HandlerThread mOnImageAvailableThread;
    private Handler mOnImageAvailableHandler;
    private ImageReader mImageReader;
    final Object focusStateLock = new Object();
    final BlockingQueue<Image> imageQueue = new ArrayBlockingQueue<>(1);
    private CaptureRequest.Builder mPreviewRequestBuilder;

    public void setFlashEnabled(boolean flashEnabled) {
        if (this.flashEnabled == flashEnabled) {
            return;
        }

        this.flashEnabled = flashEnabled;
        updateFlashIndicators(flashEnabled);
        if (mCameraId != null) {
            this.updateRepeatingRequest();
        }
    }

    private void updateFlashIndicators(boolean isFlashEnabled) {
        int drawableId = isFlashEnabled ? R.drawable.ic_toggle_flash_off : R.drawable.ic_toggle_flash_on;
        Drawable drawable = ContextCompat.getDrawable(mActivity.getApplicationContext(), drawableId);
        btnFlashToggle.setBackground(drawable);

        TextView tvFlashOnStatus = findViewById(R.id.img_quality_flash_on_status);
        int stringId = isFlashEnabled ? R.string.light_off : R.string.light_on;
        tvFlashOnStatus.setText(stringId);
    }

    /**
     * An Enumeration object that acts as a finite-state machine for image quality checking
     * QUALITY_CHECK: still looking for a clean video frame
     * FINAL_CHECK: clean video frame has been found
     */
    private QualityCheckingState mCurrentState = QualityCheckingState.QUALITY_CHECK;
    private enum QualityCheckingState {
        QUALITY_CHECK, FINAL_CHECK
    }

    /**
     * An Enumeration object that acts as a finite-state machine for
     * the camera device's auto-focus operation
     * INACTIVE: not doing anything or not available on the target device
     * UNFOCUSED: camera devices is not focused on the target object
     * FOCUSING: camera device is actively trying to focus
     * FOCUSED: camera device is focused on the target object
     */
    private FocusState mFocusState = FocusState.INACTIVE;
    private enum FocusState {
        INACTIVE, UNFOCUSED, FOCUSING, FOCUSED
    }

    /**
     * An Enumeration object for describing whether images should continue to be captured
     * CONTINUE: still need to look at more images
     * STOP: enough images have been processed
     */
    public enum RDTDetectedResult {
        CONTINUE, STOP
    }

    /////////////////////////////////////////
    // Callbacks, threads, and other objects
    /////////////////////////////////////////

    /**
     * Object for handling several lifecycle events on a {@link TextureView}
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
        public void onSurfaceTextureUpdated(SurfaceTexture texture) { }

    };

    /**
     * The callback for whenever {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // Start the camera preview
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
     * Prepares {@link ImageProcessor} once OpenCV has been loaded
     **/
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(mActivity) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    processor = ImageProcessor.getInstance(mActivity, rdtName);
                    ViewportUsingBitmap viewport = findViewById(R.id.img_quality_check_viewport);
                    viewport.hScale = (float) processor.mRDT.viewFinderScaleH;
                    viewport.wScale = (float) processor.mRDT.viewFinderScaleW;
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    /**
     * Callback for camera configuration
     */
    private CameraCaptureSession.StateCallback mCameraCaptureSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            // The camera is already closed
            if (null == mCameraDevice)
                return;

            // Save the camera session and notify the listener that the camera is ready
            mCaptureSession = cameraCaptureSession;
            if (mImageQualityViewListener != null)
                mImageQualityViewListener.onRDTCameraReady();

            updateRepeatingRequest();
        }

        @Override
        public void onConfigureFailed(
                @NonNull CameraCaptureSession cameraCaptureSession) {
            showToast("Unable to open the camera.");
        }
    };

    /**
     * The callback object for whenever {@link ImageReader} has an image available
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            // Check that the reader is available
            if (reader == null)
                return;

            // Check that an image is available
            final Image image = reader.acquireLatestImage();
            if (image == null)
                return;
            if (imageQueue.size() > 0) {
                image.close();
                return;
            }

            // Check that the image is focused
            if (mFocusState != FocusState.FOCUSED) {
                image.close();
                return;
            }

            // Add the image to the queue and execute the quality checking
            // process on a different thread
            imageQueue.add(image);
            new ImageProcessAsyncTask().execute(image);
        }

    };

    private void showProgressDialogInFG() {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showProgressDialog(R.string.please_wait, R.string.processing_image, getContext());
            }
        });
    }

    private void hideProgressDialogFromFG() {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                hideProgressDialog();
            }
        });
    }

    /**
     * Callback for handling events related to JPEG capture
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            // Only process the image when the lock is available
            synchronized (focusStateLock) {
                // Check that camera information is available
                FocusState previousFocusState = mFocusState;
                if (result.get(CaptureResult.CONTROL_AF_MODE) == null ||
                        result.get(CaptureResult.CONTROL_AF_STATE) == null)
                    return;

                // Interpret the current focus state to provide user feedback
                if (result.get(CaptureResult.CONTROL_AF_MODE) ==
                        CaptureResult.CONTROL_AF_MODE_CONTINUOUS_PICTURE) {
                    switch (result.get(CaptureResult.CONTROL_AF_STATE)) {
                        case CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED:
                            mFocusState = FocusState.FOCUSED;
                            break;
                        case CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED:
                            mFocusState = FocusState.UNFOCUSED;
                            break;
                        case CaptureResult.CONTROL_AF_STATE_INACTIVE:
                            mFocusState = FocusState.INACTIVE;
                            break;
                        default:
                            mFocusState = FocusState.FOCUSING;
                            break;
                    }

                    // Display information to user if the focus state changed
                    if (showFeedback && previousFocusState != mFocusState) {
                        mActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                displayQualityResultFocusChanged();
                            }
                        });
                    }
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
     * The main {@link AsyncTask} that calls on the RDT quality checking and interpretation methods
     */
    private class ImageProcessAsyncTask extends AsyncTask<Image, Void, Void> {

        @Override
        protected Void doInBackground(Image... images) {
            // Assess the quality of this image
            Image image = images[0];
            final Mat rgbaMat = ImageUtil.imageToRGBMat(image);
            final RDTCaptureResult captureResult = processor.assessImage(rgbaMat, flashEnabled);
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    displayQualityResult(captureResult);
                }
            });
            Log.d(TAG, String.format("Capture time: %d", System.currentTimeMillis() - timeTaken));
            Log.d(TAG, String.format("Captured result: %b", captureResult.allChecksPassed));

            // If all the quality check were passed, interpret the test result
            RDTInterpretationResult interpretationResult = null;
            if (captureResult.allChecksPassed) {
                interpretationResult = processor.interpretRDT(captureResult.resultMat,
                        captureResult.boundary);
                image.close();
            } else {
                imageQueue.remove();
                image.close();
            }
            rgbaMat.release();

            // Determine if the RDT was successfully detected
            RDTDetectedResult result = RDTDetectedResult.CONTINUE;
            if (mImageQualityViewListener != null) {
                result = mImageQualityViewListener.onRDTDetected(
                        captureResult, interpretationResult,
                        System.currentTimeMillis() - timeTaken
                );
            }

            // Garbage collection
            if (captureResult.resultMat != null)
                captureResult.resultMat.release();
            if (interpretationResult != null && interpretationResult.resultMat != null)
                interpretationResult.resultMat.release();

            // Interrupt the thread if a result was found
            if (result == RDTDetectedResult.STOP)
                mOnImageAvailableThread.interrupt();

            return null;
        }
    }

    /////////////////////////////////////////
    // Methods
    /////////////////////////////////////////

    /**
     * {@link View} constructor
     * @param context: the context where the view is being used
     * @param attrs: the XML attributes for the view
     */
    public ImageQualityView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // determine where
        if (context instanceof Activity)
            mActivity = (Activity) context;
        else
            throw new Error("ImageQualityView must be created in an activity");
        inflate(context, R.layout.image_quality_view, this);
        mActivity.setTitle("Image Quality Checker");

        // Initialize UI elements
        TypedArray styleAttrs = context.getTheme().obtainStyledAttributes(
                attrs, R.styleable.ImageQualityView, 0, 0);
        showViewport = styleAttrs.getBoolean(R.styleable.ImageQualityView_showViewport, true);
        showFeedback = styleAttrs.getBoolean(R.styleable.ImageQualityView_showFeedback, true);
        mTextureView = findViewById(R.id.texture);
        initViews();

        // Keep track of how long it takes for image capture for benchmarking purposes
        timeTaken = System.currentTimeMillis();
    }

    /**
     * Initializes UI elements and checks permissions
     */
    private void initViews() {
        // Keep the screen on
        mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mActivity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Assign UI elements
        mViewport = findViewById(R.id.img_quality_check_viewport);
        mImageQualityFeedbackView = findViewById(R.id.img_quality_feedback_view);
        mProgress = findViewById(R.id.progressCircularBar);
        mProgressBackgroundView = findViewById(R.id.progressBackground);
        mProgressText = findViewById(R.id.progressText);
        mCaptureProgressBar = findViewById(R.id.captureProgressBar);
        mInstructionText = findViewById(R.id.textInstruction);

        // Set UI elements to default values
        mCaptureProgressBar.setMax(CAPTURE_COUNT);
        mCaptureProgressBar.setProgress(0);

        // Decide whether the viewport should be shown or not
        if (showViewport)
            mViewport.setOnClickListener(this);
        else
            mViewport.setVisibility(GONE);

        // Decide whether on-screen feedback should be shown or not
        if (showFeedback) {
            setProgressUI(mCurrentState);
        } else {
            mImageQualityFeedbackView.setVisibility(GONE);
            mProgressBackgroundView.setVisibility(GONE);
            mProgress.setVisibility(GONE);
            mProgressText.setVisibility(GONE);
            mInstructionText.setVisibility(GONE);
            mCaptureProgressBar.setVisibility(GONE);
        }
    }

    /**
     * Assigns the listener to this {@link View}
     * @param listener: the {@link ImageQualityViewListener} to be used with this view
     */
    public void setImageQualityViewListener(ImageQualityViewListener listener) {
        mImageQualityViewListener = listener;
    }

    /**
     * Store the name of the target RDT design
     * @param rdtName: the target RDT's name
     */
    public void setRDTName(String rdtName) {
        this.rdtName = rdtName;
    }

    /**
     * Shows a {@link Toast} on the UI thread
     * @param text The message to show
     */
    private void showToast(final String text) {
        if (mActivity != null) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mActivity, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * {@link View} onResume()
     */
    public void onResume() {
        startBackgroundThread();
        // Utilize the SurfaceTexture if it already exists, otherwise wait until it's available
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
            ImageProcessor.loadOpenCV(mActivity.getApplicationContext(), mLoaderCallback);
        }

    }

    /**
     * {@link View} onPause()
     */
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        ImageProcessor.destroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                showToast("This app requires permission to use your camera.");
            }
        }
    }

    /**
     * Opens the camera specified by {@link #mCameraId}.
     */
    private void openCamera(int width, int height) throws SecurityException {
        // Request camera permission if it has not been granted
        if (ContextCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(mActivity,
                    new String[]{Manifest.permission.CAMERA}, MY_PERMISSION_REQUEST_CODE);
            return;
        }

        // Prepare the camera surfaces
        setUpCameraOutputs();
        configureTransform(width, height);

        // Open the camera
        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS))
                throw new RuntimeException("Time out waiting to lock camera opening.");
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * Closes the {@link CameraDevice}
     */
    private void closeCamera() {
        try {
            // Grab the lock
            mCameraOpenCloseLock.acquire();

            // Close the objects associated with the camera
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
     * Sets up variables related to camera
     */
    private void setUpCameraOutputs() {
        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        try {
            // Try all possible cameras
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                // Skip if front-facing camera
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT)
                    continue;

                // Skip if streaming images not supported
                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null)
                    continue;

                // Initialize sizes for the camera preview and the image based on aspect ratio
                // and maximum size
                double[] aspectRatio = new double[]{16.0, 9.0};
                Size closestPreviewSize = new Size(Integer.MAX_VALUE,
                        (int) (Integer.MAX_VALUE * (aspectRatio[1] / aspectRatio[0])));
                Size closestImageSize = new Size(Integer.MAX_VALUE,
                        (int) (Integer.MAX_VALUE * (aspectRatio[1] / aspectRatio[0])));

                // Find the closest sizes to the that is most similar to the desired aspect ratio
                for (Size size : Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888))) {
                    Log.d(TAG, "Available Sizes: " + size.toString());
                    if (size.getWidth()*aspectRatio[1] == size.getHeight()*aspectRatio[0]) {
                        // Check if current preview size is closer to the ideal
                        double currPreviewDiff = (CAMERA2_PREVIEW_SIZE.height*CAMERA2_PREVIEW_SIZE.width) -
                                closestPreviewSize.getHeight()*closestPreviewSize.getWidth();
                        double newPreviewDiff = (CAMERA2_PREVIEW_SIZE.height*CAMERA2_PREVIEW_SIZE.width) -
                                size.getHeight()*size.getWidth();
                        if (Math.abs(currPreviewDiff) > Math.abs(newPreviewDiff))
                            closestPreviewSize = size;

                        // Check if the current image size is closer to the ideal ratio
                        double currImageDiff = (CAMERA2_IMAGE_SIZE.height * CAMERA2_IMAGE_SIZE.width) -
                                closestImageSize.getHeight() * closestImageSize.getWidth();
                        double newImageDiff = (CAMERA2_IMAGE_SIZE.height * CAMERA2_IMAGE_SIZE.width) - size.getHeight() * size.getWidth();
                        if (Math.abs(currImageDiff) > Math.abs(newImageDiff))
                            closestImageSize = size;
                    }
                }

                Log.d(TAG, "Selected sizes: " + closestPreviewSize.toString() + ", " +
                        closestImageSize.toString());

                // Update the sizes based on what is available in the camera
                mPreviewSize = closestPreviewSize;
                CAMERA2_IMAGE_SIZE.width = closestImageSize.getWidth();
                CAMERA2_IMAGE_SIZE.height = closestImageSize.getHeight();
                CAMERA2_PREVIEW_SIZE.width = closestPreviewSize.getWidth();
                CAMERA2_PREVIEW_SIZE.height = closestPreviewSize.getHeight();

                // Start the image listener
                mImageReader = ImageReader.newInstance(closestImageSize.getWidth(),
                        closestImageSize.getHeight(), ImageFormat.YUV_420_888,5);
                mImageReader.setOnImageAvailableListener(
                        mOnImageAvailableListener, mOnImageAvailableHandler);

                // Update the aspect ratio of the TextureView to the size of the preview
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE)
                    mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                else
                    mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());

                // Save the camera decision
                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            showToast("Unable to open the camera.");
        }
    }

    /**
     * Configures transformation between {@link android.graphics.Matrix} and the TextureView
     * Note:should be called after the camera preview size is determined in
     * setUpCameraOutputs and the size of `mTextureView` is fixed
     * @param viewWidth  The width of the TextureView
     * @param viewHeight The height of the TextureView
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        // Skip if necessary objects have not been set
        if (null == mTextureView || null == mPreviewSize || null == mActivity) {
            return;
        }

        // Calculate rectangles for both objects
        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());

        // Compute the transformation matrix
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

        // Apply the transformation matrix
        mTextureView.setTransform(matrix);
    }

    /**
     * Starts the background threads and their {@link Handler}s
     */
    private void startBackgroundThread() {
        // Start the thread for camera management
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());

        // Start the thread for image processing
        mOnImageAvailableThread = new HandlerThread("OnImageAvailableBackground");
        mOnImageAvailableThread.start();
        mOnImageAvailableHandler = new Handler(mOnImageAvailableThread.getLooper());
    }

    /**
     * Stops the background threads and their {@link Handler}s
     */
    private void stopBackgroundThread() {
        // Quit the thread for camera management
        mBackgroundThread.quit();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Quit the thread for image processing
        mOnImageAvailableThread.quit();
        try {
            mOnImageAvailableThread.join();
            mOnImageAvailableThread = null;
            mOnImageAvailableHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Handles auto-exposure and auto-focus
     */
    private void updateRepeatingRequest() {
        try {
            // Get the camera characteristics
            CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);

            // Define the region for auto-exposure and auto-focus
            final android.graphics.Rect sensor = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            MeteringRectangle mr = new MeteringRectangle(sensor.width()/2 - 50,
                    sensor.height()/2 - 50,
                    100 + (mMeteringCounter %2),
                    100 + (mMeteringCounter %2),
                    MeteringRectangle.METERING_WEIGHT_MAX-1);
            Log.d(TAG, String.format("Sensor Size (%d, %d), Metering %s",
                    sensor.width(), sensor.height(), mr.toString()));
            Log.d(TAG, String.format("Regions AE %s",
                    characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE).toString()));

            // Set the various auto-exposure and auto-focus properties
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS,
                    new MeteringRectangle[]{mr});
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS,
                    new MeteringRectangle[]{mr});
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE,
                    CaptureRequest.CONTROL_AWB_MODE_AUTO);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_REGIONS,
                    new MeteringRectangle[]{mr});

            // Set the flash properties
            mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, flashEnabled ?
                    CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
            mPreviewRequest = mPreviewRequestBuilder.build();

            // Acquire the camera lock and begin requesting for images
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

            // Update counter for auto-focus metering
            mMeteringCounter++;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        try {
            // Configure the size of default buffer
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // Prepare the Surface
            Surface surface = new Surface(texture);
            Surface mImageSurface = mImageReader.getSurface();

            // Add objects to builder
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);
            mPreviewRequestBuilder.addTarget(mImageSurface);

            // Create CaptureSession for preview
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    mCameraCaptureSessionStateCallback, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * The listener for all of the Activity's buttons
     * @param view the button that was selected
     */
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.img_quality_check_viewport: {
                updateRepeatingRequest();
                break;
            }
        }
    }

    /**
     * Updates the on-screen feedback for the user based on the camera's focus
     */
    private void displayQualityResultFocusChanged() {
        // Skip if feedback is not needed
        if (!showFeedback)
            return;

        // Update information about image focus
        FocusState currFocusState;
        synchronized (focusStateLock) {
            currFocusState = mFocusState;
        }

        // Update on-screen feedback
        if (currFocusState == FocusState.FOCUSED) {
            mInstructionText.setText(getResources().getString(R.string.instruction_pos));
            String message = String.format(getResources().getString(R.string.quality_msg_format),
                    "failed", "failed", "failed", "failed");
            mImageQualityFeedbackView.setText(Html.fromHtml(message));
        } else if (currFocusState == FocusState.INACTIVE) {
            mInstructionText.setText(getResources().getString(R.string.instruction_pos));
        } else if (currFocusState == FocusState.UNFOCUSED) {
            mInstructionText.setText(getResources().getString(R.string.instruction_unfocused));
        } else if (currFocusState == FocusState.FOCUSING) {
            mInstructionText.setText(getResources().getString(R.string.instruction_focusing));
        }
    }

    /**
     * Updates the on-screen feedback for the user based on image analysis
     * @param captureResult: the {@link RDTCaptureResult} indicating which quality checks were passed
     */
    private void displayQualityResult(RDTCaptureResult captureResult) {
        // Skip if feedback is not needed
        if (!showFeedback)
            return;

        // Update information about image focus
        FocusState currFocusState;
        synchronized (focusStateLock) {
            currFocusState = mFocusState;
        }

        // Extract information from the ImageProcessor
        ImageProcessor.ExposureResult exposureResult = captureResult.exposureResult;
        boolean isSharp = captureResult.isSharp;
        boolean isCentered = captureResult.isCentered;
        ImageProcessor.SizeResult sizeResult = captureResult.sizeResult;
        boolean isOriented = captureResult.isOriented;
        boolean isGlared = captureResult.isGlared;

        // Update on-screen feedback
        if (currFocusState == FocusState.FOCUSED) {
            // Get the best instruction to help the user
            int instruction = processor.getInstructionText(isCentered, sizeResult, isOriented, isGlared);
            mInstructionText.setText(getResources().getText(instruction));
            // Get the summary of the quality checks
            String[] qChecks = processor.getSummaryText(exposureResult, isSharp, isCentered, sizeResult, isOriented, isGlared);
            String message = String.format(getResources().getString(R.string.quality_msg_format_text), qChecks[0], qChecks[1], qChecks[2], qChecks[3]);
            mImageQualityFeedbackView.setText(Html.fromHtml(message));
        } else if (currFocusState == FocusState.INACTIVE) {
            mInstructionText.setText(getResources().getString(R.string.instruction_pos));
        } else if (currFocusState == FocusState.UNFOCUSED) {
            mInstructionText.setText(getResources().getString(R.string.instruction_unfocused));
        } else if (currFocusState == FocusState.FOCUSING) {
            mInstructionText.setText(getResources().getString(R.string.instruction_focusing));
        }

        btnFlashToggle = findViewById(R.id.btn_img_quality_flash_toggle);
        btnFlashToggle.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setFlashEnabled(!flashEnabled);
            }
        });
    }

    /**
     * Updates the on-screen feedback for the user based on whether the RDT has been detected
     * and the {@link ImageProcessor} is doing its final check
     * @param currentState: the current {@link QualityCheckingState}
     */
    private void setProgressUI(QualityCheckingState currentState) {
        // Skip if feedback is not needed
        if (!showFeedback)
            return;

        // Update interface depending on whether the app is ready for it final check or not
        switch (currentState) {
            case QUALITY_CHECK:
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mProgress.setVisibility(View.GONE);
                        mProgressBackgroundView.setVisibility(View.GONE);
                        mProgressText.setVisibility(View.GONE);
                        mCaptureProgressBar.setVisibility(View.GONE);
                    }
                });
                break;
            case FINAL_CHECK:
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mProgress.setVisibility(View.VISIBLE);
                        mProgressBackgroundView.setVisibility(View.VISIBLE);
                        mProgressText.setText(R.string.progress_final);
                        mProgressText.setVisibility(View.VISIBLE);
                        mCaptureProgressBar.setVisibility(View.GONE);
                    }
                });
                break;
        }
    }

    public void captureImage() {
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                final Image image = reader.acquireLatestImage();
                if (continueProcessingImg(image)) {
                    imageQueue.add(image);
                    Mat capturedMat = ImageUtil.imageToMat(image);
                    ImageProcessor.CaptureResult captureResult = new ImageProcessor.CaptureResult(capturedMat);
                    ((ImageQualityActivity) mActivity).useCapturedImage(captureResult, new ImageProcessor.InterpretationResult(), 0);
                }
            }

        }, null);
    }
}
