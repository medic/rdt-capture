package edu.washington.cs.ubicomplab.rdt_reader;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.DMatch;
import org.opencv.core.Mat;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Importer;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.Features2d;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.ContactsContract;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.opencv.core.CvType.CV_32F;
import static org.opencv.core.CvType.CV_8UC1;

public class MainActivity extends AppCompatActivity implements CvCameraViewListener2{
    private static final String TAG = "rdt-reader:MainActivity";
    private static final int MY_CAMERA_REQUEST_CODE = 100;
    private static final Scalar RED = new Scalar(255, 0, 0);
    private static final Scalar GREEN = new Scalar(0, 255, 0);

    private CameraBridgeViewBase mOpenCvCameraView;
    private ConstraintLayout mContainer;
    private FeatureDetector mFeatureDetector;
    private Mat mRefImg;
    private DescriptorExtractor mDescriptor;
    private DescriptorMatcher mMatcher;
    private Mat mRefDescriptor1;
    private MatOfKeyPoint mRefKeypoints1;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
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
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    MY_CAMERA_REQUEST_CODE);
        }


        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        Log.i(TAG, "called onCreate");

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        mContainer = findViewById(R.id.container);

        mOpenCvCameraView = findViewById(R.id.opencv_camera_view);

        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);

        mOpenCvCameraView.setCvCameraViewListener(this);

        //mOpenCvCameraView.setMaxFrameSize(container.getMaxWidth(), container.getMinHeight());


        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_3_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_3_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }

        loadReference(R.drawable.remel_flu_ref);
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        System.gc();

        /*Mat newFrame = new Mat();

        double hRatio;
        double wRatio;

        hRatio = mContainer.getHeight()/inputFrame.rgba().height();
        wRatio = mContainer.getWidth()/inputFrame.rgba().width();

        Log.d(TAG, String.format("%d,%d %d.%d", mContainer.getHeight(), mContainer.getWidth(), inputFrame.rgba().height(), inputFrame.rgba().width()));

        if (hRatio < wRatio)
            Imgproc.resize(inputFrame.rgba(), newFrame, new Size(inputFrame.rgba().width()*hRatio, inputFrame.rgba().height()*hRatio));
        else
            Imgproc.resize(inputFrame.rgba(), newFrame, new Size(inputFrame.rgba().width()*wRatio, inputFrame.rgba().height()*wRatio));

        return newFrame;*/



        /*Mat mRgba = inputFrame.rgba();
        Mat mRgbaT = mRgba.t();
        Core.flip(mRgba.t(), mRgbaT, 1);
        Imgproc.resize(mRgbaT, mRgbaT, new Size(mContainer.getWidth(), mContainer.getHeight()));

        Log.d(TAG, String.format("%d,%d %d,%d %d,%d", mContainer.getHeight(), mContainer.getWidth(), mOpenCvCameraView.getHeight(), mOpenCvCameraView.getWidth(), inputFrame.rgba().height(), inputFrame.rgba().width()));


        return mRgbaT;*/

        Log.d(TAG, String.format("%d,%d %d.%d", mContainer.getHeight(), mContainer.getWidth(), inputFrame.rgba().height(), inputFrame.rgba().width()));

        //return inputFrame.rgba();
        //return drawContour(inputFrame.rgba());
        return extractFeatures(inputFrame.rgba());
    }

    private void loadReference(int id){
        mFeatureDetector = FeatureDetector.create(FeatureDetector.ORB);
        mDescriptor = DescriptorExtractor.create(DescriptorExtractor.ORB);
        mMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
        mRefImg = new Mat();

        Bitmap bitmap = BitmapFactory.decodeResource(getApplicationContext().getResources(), id);
        Utils.bitmapToMat(bitmap, mRefImg);
        Imgproc.cvtColor(mRefImg, mRefImg, Imgproc.COLOR_RGB2GRAY);
        mRefImg.convertTo(mRefImg, 0); //converting the image to match with the type of the cameras image
        mRefDescriptor1 = new Mat();
        mRefKeypoints1 = new MatOfKeyPoint();
        mFeatureDetector.detect(mRefImg, mRefKeypoints1);
        mDescriptor.compute(mRefImg, mRefKeypoints1, mRefDescriptor1);
    }

    private Mat extractFeatures(Mat input) {
        Imgproc.cvtColor(input, input, Imgproc.COLOR_RGB2GRAY);
        Mat descriptors2 = new Mat();
        MatOfKeyPoint keypoints2 = new MatOfKeyPoint();
        mFeatureDetector.detect(input, keypoints2);
        mDescriptor.compute(input, keypoints2, descriptors2);

        Size size = descriptors2.size();

        if (descriptors2.size().equals(new Size(0,0))) {
            return input;
        }

        // Matching
        MatOfDMatch matches = new MatOfDMatch();
        //ArrayList<MatOfDMatch> matchList = new ArrayList<>();
        if (mRefImg.type() == input.type()) {
           mMatcher.match(mRefDescriptor1, descriptors2, matches);
           //mMatcher.knnMatch(mRefDescriptor1, descriptors2, matchList, 2);
        } else {
            return input;
        }
        List<DMatch> matchesList = matches.toList();

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
            if (matchesList.get(i).distance <= (1.3 * min_dist))
                good_matches.addLast(matchesList.get(i));
        }

        MatOfDMatch goodMatches = new MatOfDMatch();
        goodMatches.fromList(good_matches);
        Mat outputImg = new Mat();
        MatOfByte drawnMatches = new MatOfByte();


        LinkedList<Point> objList = new LinkedList<>();
        LinkedList<Point> sceneList = new LinkedList<>();
        for(int i=0;i<good_matches.size();i++){
            objList.addLast(keypoints2.toList().get(good_matches.get(i).trainIdx).pt);
            sceneList.addLast(mRefKeypoints1.toList().get(good_matches.get(i).queryIdx).pt);
        }

        Mat obj = Converters.vector_Point2f_to_Mat(objList);
        Mat scene = Converters.vector_Point2f_to_Mat(sceneList);

        Log.d(TAG, String.format("checkvector: %d %d", obj.checkVector(2, CV_32F), scene.checkVector(2, CV_32F)));

        Mat perspectiveTransform = Imgproc.getPerspectiveTransform(scene, obj);

        ArrayList<MatOfPoint> temp = new ArrayList<>();
        Converters.Mat_to_vector_vector_Point(perspectiveTransform, temp);
        Imgproc.polylines(outputImg, temp, false, RED);

        if (input.empty() || input.cols() < 1 || input.rows() < 1) {
            return input;
        }

        Features2d.drawMatches(mRefImg, mRefKeypoints1, input, keypoints2, goodMatches, outputImg, GREEN, RED, drawnMatches, Features2d.NOT_DRAW_SINGLE_POINTS);
        Imgproc.resize(outputImg, outputImg, input.size());

        return outputImg;
    }

    private Mat drawContour(Mat input) {
        Mat sobelx = new Mat();
        Mat sobely = new Mat();
        Mat output = new Mat();
        Mat sharp = new Mat();

        //Imgproc.GaussianBlur(input, output, new Size(21, 21), 8);
        Imgproc.GaussianBlur(input, output, new Size(21, 21), 3);
        Imgproc.cvtColor(output, output, Imgproc.COLOR_RGB2GRAY);

        Imgproc.Sobel(output, sobelx, CV_32F, 0, 1); //ksize=5
        Imgproc.Sobel(output, sobely, CV_32F, 1, 0); //ksize=5

        Core.pow(sobelx, 2, sobelx);
        Core.pow(sobely, 2, sobely);

        Core.add(sobelx, sobely, output);

        output.convertTo(output, CV_32F);

        Core.pow(output, 0.5, output);
        Core.multiply(output, new Scalar(Math.pow(2, 0.5)),output);

        output.convertTo(output, CV_8UC1);

        Imgproc.GaussianBlur(output, sharp, new Size(0, 0), 3);
        Core.addWeighted(output, 1.5, sharp, -0.5, 0, output);

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
                Imgproc.rectangle(output, rect.tl(), rect.br(), new Scalar(255, 255, 255));
        }

        //output.convertTo(output, CV_32F);

        return output;

    }
}
