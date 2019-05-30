package edu.washington.cs.ubicomplab.rdt_reader;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
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
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.util.DisplayMetrics;
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
import org.opencv.core.Scalar;
import org.opencv.features2d.BRISK;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static edu.washington.cs.ubicomplab.rdt_reader.Constants.CAMERA2_PREVIEW_SIZE;
import static edu.washington.cs.ubicomplab.rdt_reader.Constants.CAPTURE_COUNT;
import static edu.washington.cs.ubicomplab.rdt_reader.Constants.CAMERA2_IMAGE_SIZE;
import static edu.washington.cs.ubicomplab.rdt_reader.Constants.MY_PERMISSION_REQUEST_CODE;

public class ImageQualityActivity extends AppCompatActivity implements View.OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback {

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
    private Mat bestCapturedMat;
    private double minDistance = Double.MAX_VALUE;
    private boolean minDistanceUpdated = false;

    private long timeTaken = 0;

    private double minBlur = Double.MIN_VALUE;
    private double maxBlur = Double.MIN_VALUE; //this value is set to min because blur check is not needed.
    private ViewportUsingBitmap mViewport;

    private FocusState mFocusState = FocusState.INACTIVE;
    private int mMoveCloserCount = 0;
    public boolean isPostProcessed = false;

    private enum State {
        QUALITY_CHECK, FINAL_CHECK
    }

    private enum FocusState {
        INACTIVE, FOCUSING, FOCUSED, UNFOCUSED
    }

    private enum ExposureResult {
        UNDER_EXPOSED, NORMAL, OVER_EXPOSED
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_quality);

        loadPref();


        mTextureView = findViewById(R.id.texture);

        mFile = new File(getExternalFilesDir(null), "pic.jpg");

        timeTaken = System.currentTimeMillis();

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

    private boolean inProcess = false;
    final Object lock = new Object();
    final Object focusStateLock = new Object();

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

            synchronized (focusStateLock) {
                Log.d(TAG, "LOCAL FOCUS STATE: " + mFocusState + ", " + FocusState.FOCUSED);
                if (mFocusState != FocusState.FOCUSED) {
                    image.close();
                    return;
                }
            }

            synchronized (lock) {
                if (inProcess) {
                    image.close();
                    return;
                }
            }

            mOnImageAvailableHandler.post(new Runnable() {
                @Override
                public void run() {
                    synchronized (lock) {
                        inProcess = true;
                    }

                    //image pre-processing
                    Mat rgbaMat = ImageUtil.imageToRGBMat(image);
                    Mat grayMat = new Mat();
                    Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGBA2GRAY);

                    Rect cropRect = new Rect((int)(Constants.CAMERA2_IMAGE_SIZE.width/4), (int)(Constants.CAMERA2_IMAGE_SIZE.height/4), (int)(Constants.CAMERA2_IMAGE_SIZE.width*Constants.VIEWPORT_SCALE), (int)(Constants.CAMERA2_IMAGE_SIZE.height*Constants.VIEWPORT_SCALE));

                    Mat matchingMat = grayMat.submat(cropRect);
                    Mat blurMat = rgbaMat.submat(cropRect);
                    Mat exposureMat = grayMat.submat(cropRect);

                    //size and position check
                    MatOfPoint2f approx = detectRDT(grayMat);
                    final boolean[] isCorrectPosSize = checkPositionAndSize(approx, false);


                    //exposure check
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


                    ExposureResult exposureResult;

                    if (maxWhite >= Constants.OVER_EXP_THRESHOLD && whiteCount > Constants.OVER_EXP_WHITE_COUNT)
                        exposureResult = ExposureResult.OVER_EXPOSED;
                    else if (maxWhite < Constants.UNDER_EXP_THRESHOLD)
                        exposureResult = ExposureResult.UNDER_EXPOSED;
                    else
                        exposureResult = ExposureResult.NORMAL;

                    //blur check
                    double blurVal = calculateBlurriness(blurMat);
                    Log.d(TAG, "BLUR CHECK: "+ blurVal + ", " + maxBlur);
                    final boolean isBlur = blurVal < (maxBlur * Constants.BLUR_THRESHOLD);


                    matchingMat.release();
                    exposureMat.release();
                    blurMat.release();
                    grayMat.release();


                    try {
                        if (!isPostProcessed) {
                            final boolean isOverExposed = exposureResult == ExposureResult.OVER_EXPOSED;
                            final boolean isUnderExposed = exposureResult == ExposureResult.UNDER_EXPOSED;
                            final boolean isShadow = false;


                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    displayQualityResult(isCorrectPosSize, isBlur, isOverExposed, isUnderExposed, isShadow);
                                }
                            });

                            Log.d(TAG, "CAPTURE TIME: "+(System.currentTimeMillis()-timeTaken));
                            if (((System.currentTimeMillis()-timeTaken) > Constants.MAX_CAPTURE_TIME) || (isCorrectPosSize[0] && isCorrectPosSize[1] && isCorrectPosSize[2] && !isBlur && !isOverExposed && !isUnderExposed)) {
                                Mat resultWindowMat = enhanceResultWindow(rgbaMat.clone(), approx);

                                if (resultWindowMat != null) {
                                    mCaptureProgressBar.incrementProgressBy(1);
                                    //checkWindowPosition(resultWindowMat);
                                    //if (checkWindowPosition(resultWindowMat) && minDistanceUpdated) {
                                    if (minDistanceUpdated) {
                                        bestCapturedMat = resultWindowMat.clone();
                                        minDistanceUpdated = false;
                                    }
                                    resultWindowMat.release();
                                }

                                if (((System.currentTimeMillis()-timeTaken) > Constants.MAX_CAPTURE_TIME)) {
                                    mCaptureProgressBar.incrementProgressBy(1);
                                    bestCapturedMat = rgbaMat.clone();
                                    minDistanceUpdated = false;
                                }

                                //post-processing
                                if (mCaptureProgressBar.getProgress() >= CAPTURE_COUNT) {
                                    isPostProcessed = true;
                                    Log.d(TAG, String.format("Average DISTANCE (MIN): %.2f", minDistance));

                                    setNextState(mCurrentState);
                                    setProgressUI(mCurrentState);

                                    //Log.d(TAG, "rgbaMat 5 Size: " + bestCapturedMat.size().toString() + ", rect size: " + new Rect((int) (Constants.CAMERA2_IMAGE_SIZE.width / 5), (int) (Constants.CAMERA2_IMAGE_SIZE.height / 5), (int) (Constants.CAMERA2_IMAGE_SIZE.width * 0.6), (int) (Constants.CAMERA2_IMAGE_SIZE.height * 0.6)).size().toString());
                                    Mat withTime = bestCapturedMat.submat(new Rect((int) (Constants.CAMERA2_IMAGE_SIZE.width * 0.20), (int) (Constants.CAMERA2_IMAGE_SIZE.height / 5), (int) (Constants.CAMERA2_IMAGE_SIZE.width * 0.6), (int) (Constants.CAMERA2_IMAGE_SIZE.height * 0.6)));
                                    org.opencv.core.Size textSize = Imgproc.getTextSize(String.format("%.3f", (double)(System.currentTimeMillis() - timeTaken)/1000),
                                            Core.FONT_HERSHEY_DUPLEX, 1, 3, null);
                                    Imgproc.rectangle(withTime, new Point(0, 0), new Point(textSize.width+10, textSize.height+10),
                                            new Scalar(255,255,255,255), Core.FILLED);
                                    Imgproc.putText(withTime, String.format("%.3f", (double)(System.currentTimeMillis() - timeTaken)/1000), new Point(5, textSize.height+5),
                                            Core.FONT_HERSHEY_DUPLEX, 1.0, new Scalar(0, 0, 0, 255), 2);
                                    byte[] byteArray = ImageUtil.matToRotatedByteArray(withTime);
                                    //byte[] byteArray = ImageUtil.matToRotatedByteArray(bestCapturedMat);
                                    // If this activity was triggered by an external
                                    // intent, then respond with the content of the image.
                                    // Otherwise, handle the result inside this app.
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

//                                        File sdIconStorageDir = new File(Constants.RDT_IMAGE_DIR);
//
//                                        //create storage directories, if they don't exist
//                                        sdIconStorageDir.mkdirs();
//
//                                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss-SSS");
//
//                                        try {
//                                            String filePath = sdIconStorageDir.toString() + String.format("/%s-%08dms.jpg", sdf.format(new Date()), timeTaken);
//                                            FileOutputStream fileOutputStream = new FileOutputStream(filePath);
//
//                                            fileOutputStream.write(byteArray);
//
//                                            fileOutputStream.flush();
//                                            fileOutputStream.close();
//
//                                            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + filePath)));
//
//                                            //Toast.makeText(this,"Image is successfully saved!", Toast.LENGTH_SHORT).show();
//                                        } catch (Exception e) {
//                                            Log.w("TAG", "Error saving image file: " + e.getMessage());
//                                        }

                                        rgbaMat.release();
                                        startActivity(intent);
                                        mOnImageAvailableThread.interrupt();
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage());
                    }

                    rgbaMat.release();

                    synchronized (lock) {
                        inProcess = false;
                        Log.d(TAG, "OnImageAvailableListener: computed");
                    }
                }
            });
        }

    };

    private boolean checkWindowPosition(Mat resultWindowMat) {
        Imgproc.rectangle(resultWindowMat, new Point(resultWindowMat.cols()/3*1.6, 0), new Point(resultWindowMat.cols(), resultWindowMat.rows()), new Scalar(255), -1);

        Point line = new Point(resultWindowMat.width()*0.35-resultWindowMat.width()*0.15, 0);
        Rect roi = new Rect((int)(resultWindowMat.width()*0.15), (int)(resultWindowMat.height()*0.2), (int)(resultWindowMat.width()*0.7), (int)(resultWindowMat.height()*0.6));
        Mat crop = resultWindowMat.submat(roi);
        Mat cropgray = new Mat();
        Imgproc.cvtColor(crop, cropgray, Imgproc.COLOR_RGBA2BGR);
        Imgproc.cvtColor(cropgray, cropgray, Imgproc.COLOR_BGR2HSV);

        ArrayList<Mat> channels = new ArrayList<>();
        Core.split(cropgray, channels);

        Core.MinMaxLocResult result = Core.minMaxLoc(channels.get(2), null);

        Log.d(TAG, "MIN MAX LOC Rect: "+crop.size().width+", "+ cropgray.size().width);
        Log.d(TAG, "MIN MAX LOC Rect: "+crop.size().height+", "+ cropgray.size().height);
        Log.d(TAG, "MIN MAX LOC: "+result.minLoc + ", " + result.minVal + ", " + result.maxLoc + ", " + result.maxVal);

        double minColSum = Double.MAX_VALUE;
        int minColIndex = -2;
        for (int i = -2; i < 1; i++) {
            double colSum = channels.get(2).get((int)result.minLoc.y, (int)result.minLoc.x+i)[0] +
            channels.get(2).get((int)result.minLoc.y, (int)result.minLoc.x+i+1)[0] +
            channels.get(2).get((int)result.minLoc.y, (int)result.minLoc.x+i+2)[0];

            Log.d(TAG, String.format("MIN MAX explore: %d: %d, %d, %d", (int)result.minLoc.x+i, (int)channels.get(2).get((int)result.minLoc.y, (int)result.minLoc.x+i)[0],
                    (int)channels.get(2).get((int)result.minLoc.y, (int)result.minLoc.x+i+1)[0], (int)channels.get(2).get((int)result.minLoc.y, (int)result.minLoc.x+i+2)[0]));

            minColIndex = (colSum < minColSum) ? i : minColIndex;
            minColSum = (colSum < minColSum) ? colSum : minColSum;
        }

        Log.d(TAG, "MIN MAX col: " + minColIndex);

        double sum = 0;
        for (int i = minColIndex; i < minColIndex+3; i++) {
            for (int j = 0; j < channels.get(2).height(); j++) {
                Log.d(TAG, "MIN MAX coor: "+(result.minLoc.x+i)+","+j+", " +channels.get(2).get(j, (int)(result.minLoc.x+i))[0]);
                //if (i >= -1 && i < 2) {
                sum += channels.get(2).get(j, (int) (result.minLoc.x + i))[0];
                //}
            }
        }

        double avg = sum/(double)(channels.get(0).height()*3);

        Log.d(TAG, "MIN MAX Row Avg: " + avg + " And, MIN VAL: "+result.minVal);
        Log.d(TAG, "MIN MAX Row Loc: " + line + " And, MIN VAL: "+result.minLoc);

        return (0 < avg && avg < result.minVal+30 && result.minLoc.x - 0.1*channels.get(2).width() < line.x && line.x < result.minLoc.x + 0.1*channels.get(2).width());
    }

    private Mat enhanceResultWindow(Mat input, MatOfPoint2f boundary) {
        Mat refPoints = new Mat(4, 1, CvType.CV_32FC2);
        Mat refResultPoints = new Mat(4, 1, CvType.CV_32FC2);
        //Mat obj_corners = new Mat(4, 1, CvType.CV_32FC2);

        double[] a = new double[]{0, 0};
        double[] b = new double[]{mRefImg.cols() - 1, 0};
        double[] c = new double[]{mRefImg.cols() - 1, mRefImg.rows() - 1};
        double[] d = new double[]{0, mRefImg.rows() - 1};

        //get corners from object
        refPoints.put(0, 0, a);
        refPoints.put(1, 0, b);
        refPoints.put(2, 0, c);
        refPoints.put(3, 0, d);

        Log.d(TAG, "perspective ref" + refPoints.dump());

//        a = new double[]{59, 183};
//        b = new double[]{59+30, 183};
//        c = new double[]{59+30, 183+110};
//        d = new double[]{59, 183+110};

        //TODO: make it as a config
        a = new double[]{Constants.RESULT_WINDOW_X, Constants.RESULT_WINDOW_Y};
        b = new double[]{Constants.RESULT_WINDOW_X+Constants.RESULT_WINDOW_WIDTH, Constants.RESULT_WINDOW_Y};
        c = new double[]{Constants.RESULT_WINDOW_X+Constants.RESULT_WINDOW_WIDTH, Constants.RESULT_WINDOW_Y+Constants.RESULT_WINDOW_HEIGHT};
        d = new double[]{Constants.RESULT_WINDOW_X, Constants.RESULT_WINDOW_Y+Constants.RESULT_WINDOW_HEIGHT};
//        a = new double[]{185, 63};
//        b = new double[]{185+90, 63};
//        c = new double[]{185+90, 63+20};
//        d = new double[]{185, 63+20};

        refResultPoints.put(0, 0, a);
        refResultPoints.put(1, 0, b);
        refResultPoints.put(2, 0, c);
        refResultPoints.put(3, 0, d);

        Log.d(TAG, "perspective results" + refResultPoints.dump());
        Log.d(TAG, "perspective bound" + boundary.dump());

        Mat M = Imgproc.getPerspectiveTransform(refPoints, boundary);
        Log.d(TAG, "perspective transform" + M.dump());
        Mat imgResultPointsMat = new Mat();
        Core.perspectiveTransform(refResultPoints, imgResultPointsMat, M);
        Log.d(TAG, "perspective window" + imgResultPointsMat.dump());

        MatOfPoint imgResultPoints = new MatOfPoint();

        ArrayList<Point> points = new ArrayList<>();
        points.add(new Point(imgResultPointsMat.get(0, 0)));
        points.add(new Point(imgResultPointsMat.get(1, 0)));
        points.add(new Point(imgResultPointsMat.get(2, 0)));
        points.add(new Point(imgResultPointsMat.get(3, 0)));
        imgResultPoints.fromList(points);

        Point[] p = imgResultPoints.toArray();

        Rect resultRect = Imgproc.boundingRect(imgResultPoints);

        Mat resultImg = input.submat(resultRect);
        Mat enhancedImg = enhanceImage(resultImg, new org.opencv.core.Size(2, resultRect.height));
        enhancedImg = correctGamma(enhancedImg, 1.2);
        boolean windowPosition = checkWindowPosition(resultImg);

        Log.d(TAG, "MIN MAX position right: " + windowPosition);

        if (windowPosition) {
            enhancedImg.copyTo(resultImg);
            return input;
        } else {
            return null;
        }
    }

    private Mat correctGamma(Mat enhancedImg, double gamma) {
        Mat lutMat = new Mat(1, 256, CvType.CV_8UC1);
        for (int i = 0; i < 256; i ++) {
            double g = Math.pow((double)i/255.0, gamma)*255.0;
            g = g > 255.0 ? 255.0 : g < 0 ? 0 : g;
            lutMat.put(0, i, g);
        }
        Mat result = new Mat();
        Core.LUT(enhancedImg, lutMat, result);
        return result;
    }

    private Mat enhanceImage(Mat resultImg, org.opencv.core.Size tile) {
        Mat result = new Mat();
        Imgproc.cvtColor(resultImg, result, Imgproc.COLOR_RGBA2BGR);
        Imgproc.cvtColor(result, result, Imgproc.COLOR_BGR2HSV);

        CLAHE clahe = Imgproc.createCLAHE(5, tile);

        ArrayList<Mat> channels = new ArrayList<>();
        Core.split(result, channels);

        Mat newChannel = new Mat();

        clahe.apply(channels.get(2), newChannel);

        channels.set(2, newChannel);

        Core.merge(channels, result);

        Imgproc.cvtColor(result, result, Imgproc.COLOR_HSV2BGR);
        Imgproc.cvtColor(result, result, Imgproc.COLOR_BGR2RGBA);

        return result;
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

                if (result.get(CaptureResult.CONTROL_AF_MODE) != null && result.get(CaptureResult.CONTROL_AF_MODE) == CaptureResult.CONTROL_AF_MODE_CONTINUOUS_PICTURE) {
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
                                displayQualityResult(new boolean[]{false, false, false, false, false, true}, true, true, true, true);
                            }
                        });
                    }
                }
            }

            if (!Build.MODEL.equals("TECNO-W3")) {
                if (counter++%10 == 0)
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
        super.onPause();


        closeCamera();
        stopBackgroundThread();
        unloadReference();
    }

    @Override
    public void onBackPressed() {
        if(isExternalIntent()) {
            super.onBackPressed();
        } else {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                showToast("This sample needs camera permission.");
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
                Size closestPreviewSize = new Size(Integer.MAX_VALUE, (int)(Integer.MAX_VALUE*(9.0/16.0)));
                Size closestImageSize = new Size(Integer.MAX_VALUE, (int)(Integer.MAX_VALUE*(3.0/4.0)));
                for (Size size: Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888))) {
                    Log.d(TAG, "Available Sizes: " + size.toString());
                    if (size.getWidth() < 719)
                        continue;

                    if (size.getWidth()*9 == size.getHeight()*16) { //Preview surface ratio is 16:9
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

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, MY_PERMISSION_REQUEST_CODE);
    }

    /**
     * Opens the camera specified by {@link #mCameraId}.
     */
    private void openCamera(int width, int height) throws SecurityException {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }

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
        Log.d(TAG, "Thread Quit Safely.");
        mBackgroundThread.quitSafely();
        mOnImageAvailableThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;

            mOnImageAvailableThread.join();
            mOnImageAvailableThread = null;
            mOnImageAvailableHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private CameraCaptureSession.StateCallback mCameraCaptureSessionStateCallback =  new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            // The camera is already closed
            if (null == mCameraDevice) {
                return;
            }
            mCaptureSession = cameraCaptureSession;

            updateRepeatingRequest();
        }

        @Override
        public void onConfigureFailed(
                @NonNull CameraCaptureSession cameraCaptureSession) {
            showToast("Failed");
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
            MeteringRectangle mr = new MeteringRectangle(sensor.width() / 2 - 50, sensor.height() / 2 - 50, 100+(mCounter%2), 100+(mCounter%2),
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
                    mCameraCaptureSessionStateCallback, null
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


    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.viewport: {
                updateRepeatingRequest();
                break;
            }
        }
    }



    /**
     * Imported from ImageQualityOpencvActivity
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
        mFeatureDetector = BRISK.create(90, 4, 1.0f);
        mMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
        mRefImg = new Mat();

        BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
        bitmapOptions.inScaled = false;

        Bitmap bitmap = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.sd_bioline_malaria_ag_pf, bitmapOptions);
        Utils.bitmapToMat(bitmap, mRefImg);
        Imgproc.cvtColor(mRefImg, mRefImg, Imgproc.COLOR_RGB2GRAY);
        mRefDescriptor = new Mat();

        mRefKeypoints = new MatOfKeyPoint();
        long startTime = System.currentTimeMillis();
        mFeatureDetector.detect(mRefImg, mRefKeypoints);
        mFeatureDetector.compute(mRefImg, mRefKeypoints, mRefDescriptor);
        Log.d(TAG, "REFERENCE LOAD/DETECT/COMPUTE: " + (System.currentTimeMillis() - startTime));
        Log.d(TAG, "mRef Size: (" + bitmap.getWidth()+", "+bitmap.getHeight()+")");
        Log.d(TAG, "mRef Size: (" + mRefImg.size().width+", "+mRefImg.size().height+")");
    }

    private void unloadReference(){
        mRefImg.release();
        mRefDescriptor.release();
        mRefKeypoints.release();
        if (bestCapturedMat != null)
            bestCapturedMat.release();
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
        FocusState currFocusState;

        synchronized (focusStateLock) {
            currFocusState = mFocusState;
        }

        if (currFocusState == FocusState.FOCUSED) {
            String message = String.format(getResources().getString(R.string.quality_msg_format), isCorrectPosSize[0] && isCorrectPosSize[1] && isCorrectPosSize[2] ? Constants.OK : Constants.NOT_OK,
                    !isBlur ? Constants.OK : Constants.NOT_OK,
                    !isOverExposed && !isUnderExposed ? Constants.OK : (isOverExposed ? getResources().getString(R.string.over_exposed_msg) + Constants.NOT_OK : getResources().getString(R.string.under_exposed_msg) + Constants.NOT_OK),
                    !isShadow ? Constants.OK : Constants.NOT_OK);

            if (isCorrectPosSize[1] && isCorrectPosSize[0] & isCorrectPosSize[2]) {
                mInstructionText.setText(getResources().getText(R.string.instruction_detected));
            } else if (mMoveCloserCount > Constants.MOVE_CLOSER_COUNT)
                if (!isCorrectPosSize[5]) {
                    if (!isCorrectPosSize[0] || (!isCorrectPosSize[1] && isCorrectPosSize[3])) {
                        mInstructionText.setText(getResources().getString(R.string.instruction_pos));
                    } else if (!isCorrectPosSize[1] && isCorrectPosSize[4]) {
                        mInstructionText.setText(getResources().getString(R.string.instruction_too_small));
                        mMoveCloserCount = 0;
                    }
                } else {
                    mInstructionText.setText(getResources().getString(R.string.instruction_pos));
                }
            else {
                mInstructionText.setText(getResources().getString(R.string.instruction_too_small));
                mMoveCloserCount++;
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

        Mat mask = new Mat(input.width(), input.height(), CvType.CV_8U, new Scalar(0));
        Point p1 = new Point(0, input.size().height*(1-Constants.MASK_HEIGHT_SCALE)/2);
        Point p2 = new Point(input.size().width-p1.x, input.size().height-p1.y);
        Imgproc.rectangle(mask, p1, p2, new Scalar(255), -1);

        mFeatureDetector.detect(input, keypoints, mask);
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
            //if (matchesList.get(i).distance <= (1.5 * min_dist)) {
                good_matches.addLast(matchesList.get(i));
                sum += matchesList.get(i).distance;
                count++;
            //}
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

        if (good_matches.size() > Constants.GOOD_MATCH_COUNT) {
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
        boolean results[] = {false, false, false, false, false, true};

        if (approx.total() < 1)
            return results;

        RotatedRect rotatedRect = Imgproc.minAreaRect(approx);
        if (cropped)
            rotatedRect.center = new Point(rotatedRect.center.x + Constants.CAMERA2_IMAGE_SIZE.width/4, rotatedRect.center.y + Constants.CAMERA2_IMAGE_SIZE.height/4);

        Point center = rotatedRect.center;
        Point trueCenter = new Point(Constants.CAMERA2_IMAGE_SIZE.width/2, Constants.CAMERA2_IMAGE_SIZE.height/2);

        boolean isUpright = rotatedRect.size.height > rotatedRect.size.width;
        double angle = 0;
        double height = 0;
        double width = 0;

        if (isUpright) {
            angle = 90 - Math.abs(rotatedRect.angle);
            height = rotatedRect.size.height;
            width = rotatedRect.size.width;
        } else {
            angle = Math.abs(rotatedRect.angle);
            height = rotatedRect.size.width;
            width = rotatedRect.size.height;
        }

        boolean isCentered = center.x < trueCenter.x + Constants.CAMERA2_IMAGE_SIZE.width*Constants.POSITION_THRESHOLD && center.x > trueCenter.x - Constants.CAMERA2_IMAGE_SIZE.width*Constants.POSITION_THRESHOLD
                && center.y < trueCenter.y + Constants.CAMERA2_IMAGE_SIZE.height*Constants.POSITION_THRESHOLD && center.y > trueCenter.y - Constants.CAMERA2_IMAGE_SIZE.height*Constants.POSITION_THRESHOLD;
        boolean isRightSize = height < Constants.CAMERA2_IMAGE_SIZE.width*Constants.VIEWPORT_SCALE*(1+Constants.SIZE_THRESHOLD)
                && height > Constants.CAMERA2_IMAGE_SIZE.width*Constants.VIEWPORT_SCALE*(1-Constants.SIZE_THRESHOLD);
        boolean isOriented = angle < 90.0*Constants.POSITION_THRESHOLD;

        results[0] = isCentered && height > Constants.CAMERA2_IMAGE_SIZE.width*Constants.VIEWPORT_SCALE/5;
        results[1] = isRightSize;
        results[2] = isOriented;
        results[3] = height > Constants.CAMERA2_IMAGE_SIZE.width*Constants.VIEWPORT_SCALE*(1+Constants.SIZE_THRESHOLD); //large
        results[4] = height < Constants.CAMERA2_IMAGE_SIZE.width*Constants.VIEWPORT_SCALE*(1-Constants.SIZE_THRESHOLD); //small
        results[5] = height == 0;

        if (height != 0) {
            if (results[0] && results[1])
                Log.d(TAG, String.format("------POS: %.2f, %.2f, Angle: %.2f, Height: %.2f, %.2f", center.x, center.y, angle, height, width));
            else
                Log.d(TAG, String.format("POS: %.2f, %.2f, Angle: %.2f, Height: %.2f, %.2f", center.x, center.y, angle, height, width));
        }
        return results;
    }

    private double calculateBlurriness(Mat input) {
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

    private void loadPref() {
        Context context = getApplicationContext();
        SharedPreferences sharedPref = context.getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = sharedPref.edit();

        if (sharedPref.contains(getString(R.string.preference_language))) {
            Constants.LANGUAGE = sharedPref.getString(getString(R.string.preference_language),Constants.LANGUAGE);
        } else {
            editor.putString(getString(R.string.preference_language), Constants.LANGUAGE);
        }

        if (sharedPref.contains(getString(R.string.preference_over_exposure))) {
            Constants.OVER_EXP_WHITE_COUNT = sharedPref.getFloat(getString(R.string.preference_over_exposure),(float)Constants.OVER_EXP_WHITE_COUNT);
        } else {
            editor.putFloat(getString(R.string.preference_over_exposure), (float)Constants.OVER_EXP_WHITE_COUNT);
        }

        if (sharedPref.contains(getString(R.string.preference_under_exposure))) {
            Constants.UNDER_EXP_THRESHOLD = sharedPref.getFloat(getString(R.string.preference_under_exposure),(float)Constants.UNDER_EXP_THRESHOLD);
        } else {
            editor.putFloat(getString(R.string.preference_under_exposure), (float)Constants.UNDER_EXP_THRESHOLD);
        }

        if (sharedPref.contains(getString(R.string.preference_sharpness))) {
            Constants.BLUR_THRESHOLD = sharedPref.getFloat(getString(R.string.preference_sharpness),(float)Constants.BLUR_THRESHOLD);
        } else {
            editor.putFloat(getString(R.string.preference_sharpness), (float)Constants.BLUR_THRESHOLD);
        }

        if (sharedPref.contains(getString(R.string.preference_position))) {
            Constants.POSITION_THRESHOLD = sharedPref.getFloat(getString(R.string.preference_position),(float)Constants.POSITION_THRESHOLD);
        } else {
            editor.putFloat(getString(R.string.preference_position), (float)Constants.POSITION_THRESHOLD);
        }

        if (sharedPref.contains(getString(R.string.preference_size))) {
            Constants.SIZE_THRESHOLD = sharedPref.getFloat(getString(R.string.preference_size),(float)Constants.SIZE_THRESHOLD);
        } else {
            editor.putFloat(getString(R.string.preference_size), (float)Constants.SIZE_THRESHOLD);
        }

        editor.apply();

        Resources res = getResources();
        // Change locale settings in the app.
        DisplayMetrics dm = res.getDisplayMetrics();
        android.content.res.Configuration conf = res.getConfiguration();
        conf.setLocale(new Locale(Constants.LANGUAGE)); // API 17+ only.
        // Use conf.locale = new Locale(...) if targeting lower versions
        res.updateConfiguration(conf, dm);
        setContentView(R.layout.activity_image_quality);
    }
}
