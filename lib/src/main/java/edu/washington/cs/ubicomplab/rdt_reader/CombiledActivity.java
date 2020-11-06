package edu.washington.cs.ubicomplab.rdt_reader;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.imgproc.Imgproc;
//import org.opencv.xfeatures2d.SURF;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.Text;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;
import com.shuhart.stepview.StepView;


import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

import edu.washington.cs.ubicomplab.rdt_reader.views.RDTCameraView;
import edu.washington.cs.ubicomplab.rdt_reader.views.ViewportUsingBitmap;

import static com.google.android.gms.vision.Frame.ROTATION_90;
import static org.opencv.core.CvType.CV_32F;
import static org.opencv.core.CvType.CV_8UC1;

public class CombiledActivity extends AppCompatActivity implements CvCameraViewListener2, View.OnTouchListener {
    private static final String TAG = CombiledActivity.class.getName();

    private static final int MY_CAMERA_REQUEST_CODE = 100;
    private static final Scalar RED = new Scalar(255, 0, 0);
    private static final Scalar GREEN = new Scalar(0, 255, 0);
    private static final Scalar BLUE = new Scalar(0, 0, 255);
    private static final Scalar WHITE = new Scalar(255, 255, 255);
    private static final Scalar BLACK = new Scalar(0, 0, 0);

    private RDTCameraView mOpenCvCameraView;
    private ConstraintLayout mContainer;
    private FeatureDetector mFeatureDetector;
    //private SURF surfDetector;
    private Mat mRefImg;
    private Mat mRefImgGray;
    private DescriptorExtractor mExtractor;
    private DescriptorMatcher mMatcher;
    private Mat mRefDescriptor1;
    private MatOfKeyPoint mRefKeypoints1;
    private boolean mDetected = false;
    private TextRecognizer mTextRecognizer;
    private boolean isExpChecked = false;
    private boolean isQualChecked = false;
    private StepView mStepView;
    private ViewportUsingBitmap viewport;

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
    private Mat mCropped;

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
        setContentView(R.layout.activity_combine);


        Log.i(TAG, "called onCreate");

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        mContainer = findViewById(R.id.container);

        mOpenCvCameraView = findViewById(R.id.opencv_camera_view);

        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);

        mOpenCvCameraView.setCvCameraViewListener(this);

        mOpenCvCameraView.setOnTouchListener(this);

        //mOpenCvCameraView.setMaxFrameSize(container.getMaxWidth(), container.getMinHeight());

        mStepView = findViewById(R.id.step_view);
        mStepView.getState()
                .steps(new ArrayList<String>(){{
                    add("Exp. Date");
                    add("Sample");
                    add("Buffer");
                    add("Image\nCapture");
                    add("Result");
                }})
                .stepsNumber(5)
                .animationDuration(getResources().getInteger(android.R.integer.config_shortAnimTime))
                .commit();


        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_3_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }

        mTextRecognizer = new TextRecognizer.Builder(getApplicationContext()).build();

        viewport = findViewById(R.id.viewport);
        viewport.setVisibility(View.INVISIBLE);
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
        Log.d(TAG, "CameraView stopped.");

    }

    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        System.gc();

        Log.d(TAG, String.format("%d,%d %d.%d", mContainer.getHeight(), mContainer.getWidth(), inputFrame.rgba().height(), inputFrame.rgba().width()));
        Mat returnedMat = inputFrame.rgba();

        Camera.Parameters parameters = mOpenCvCameraView.getCamera().getParameters();
        if (parameters != null && parameters.getFocusAreas() != null && parameters.getFocusAreas().size() > 0) {
            Camera.Area area = parameters.getFocusAreas().get(0);

            Imgproc.rectangle(returnedMat, new Point(area.rect.left, area.rect.top), new Point(area.rect.right, area.rect.bottom), RED, 10);

            Log.d(TAG, String.format("Focus Area: (%d, %d), (%d, %d)", area.rect.left, area.rect.top, area.rect.right, area.rect.bottom));
        }

        if (!isExpChecked) {
            Bitmap tempBitmap = Bitmap.createBitmap(inputFrame.rgba().cols(), inputFrame.rgba().rows(), Bitmap.Config.ARGB_8888);;
            Utils.matToBitmap(inputFrame.rgba(), tempBitmap);
            Frame frame = new Frame.Builder().setBitmap(tempBitmap).setRotation(ROTATION_90).build();
            SparseArray<TextBlock> items = mTextRecognizer.detect(frame);

            Date expDate = null;
            final Date now = new Date();

            Log.d(TAG, "Detected Text: ================================");
            for (int i = 0; i < items.size(); ++i) {
                TextBlock item = items.valueAt(i);
                for(Text currText: item.getComponents()) {
                    Log.d(TAG, "Detected Text: " + currText.getValue());

                    StringTokenizer st = new StringTokenizer(currText.getValue());

                    ArrayList<DateFormat> dfs = new ArrayList<>();
                    dfs.add(DateFormat.getDateInstance(DateFormat.SHORT));
                    dfs.add(DateFormat.getDateInstance(DateFormat.MEDIUM));
                    dfs.add(DateFormat.getDateInstance(DateFormat.LONG));
                    dfs.add(DateFormat.getDateInstance(DateFormat.FULL));

                    while(st.hasMoreTokens()) {
                        String str = st.nextToken();

                        for (DateFormat df: dfs) {
                            if (str.length() > 8) {
                                try {
                                    expDate = df.parse(str);
                                } catch (ParseException pe) {

                                }
                            }
                        }

                        if (expDate != null)
                            break;
                    }
                }
            }

            final Date finalExpDate = expDate;

            if (finalExpDate != null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView instructionView = findViewById(R.id.instructionTextView);

                        if (now.before(finalExpDate)) {
                            isExpChecked = true;
                            viewport.setVisibility(View.VISIBLE);
                            instructionView.setText("Open the RDT and run the test.");
                            mStepView.go(3, true);
                        } else {
                            instructionView.setText("This RDT is expired!\nPlease try with different RDT.");
                        }
                    }
                });
            }

            Log.d(TAG, "Detected Text: ================================");
        } else {

            if (!isQualChecked) {
                System.gc();

                //bluriness
                Mat des = new Mat();
                Imgproc.Laplacian(inputFrame.rgba(), des, inputFrame.rgba().type());
                Log.d(TAG, String.format("DEPTH: %d", inputFrame.rgba().type()));

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

                double bluriness = Math.pow(maxLap,2);

                Log.d(TAG, String.format("Bluriness: %.2f", bluriness));


                median.release();
                std.release();
                des.release();

                Mat temp =  new Mat();

                Imgproc.cvtColor(inputFrame.rgba(), temp, Imgproc.COLOR_RGB2YUV);
                List <Mat> colors = new ArrayList<>();

                Core.split(temp, colors);

                Scalar sum = Core.sumElems(colors.get(0));

                Log.d(TAG, String.format("brightness: %d, elems: %.2f, %.2f, %.2f", sum.val.length, sum.val[0]/(colors.get(0).rows()*colors.get(0).cols()), sum.val[1], sum.val[2]));

                temp.release();
                for(Mat c : colors)
                    c.release();
                isQualChecked = true;
            }
            else {
                //returnedMat = extractFeaturesWithORB(inputFrame.rgba(), mRefImg, mFeatureDetector, mExtractor, mMatcher, mRefDescriptor1, mRefKeypoints1);
                long startTime = System.currentTimeMillis();
                Mat targetMat = new Mat(inputFrame.rgba(), new Rect(inputFrame.rgba().cols()/4, inputFrame.rgba().rows()/4, inputFrame.rgba().cols()/2, inputFrame.rgba().rows()/2));
                //targetMat = extractFeaturesWithSURFSIFT(targetMat, mRefImg, surfDetector, mMatcher, mRefDescriptor1, mRefKeypoints1);
                targetMat = extractFeaturesWithORB(targetMat, mRefImg, mFeatureDetector, mExtractor, mMatcher, mRefDescriptor1, mRefKeypoints1);
                Imgproc.resize(targetMat, returnedMat, new Size(returnedMat.width(), returnedMat.height()));
                Log.d(TAG, String.format("%d taken", System.currentTimeMillis() - startTime));
                //returnedMat = extractFeaturesWithSURFSIFT(inputFrame.rgba());

                /*if (mDetected) {
                    mDetected = false;
                    try {
                        Bitmap resultBitmap = Bitmap.createBitmap(mCropped.cols(), mCropped.rows(), Bitmap.Config.ARGB_8888);
                        Utils.matToBitmap(mCropped, resultBitmap);

                        Intent intent = new Intent(this, ResultActivity.class);
                        File outputDir = getApplicationContext().getCacheDir(); // context being the Activity pointer
                        File outputFile = File.createTempFile("result_segment", ".png", outputDir);

                        ByteArrayOutputStream bs = new ByteArrayOutputStream();
                        resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, bs);

                        FileOutputStream fos = new FileOutputStream(outputFile);
                        fos.write(bs.toByteArray());
                        fos.close();

                        intent.putExtra("imageFilePath", outputFile.getAbsolutePath());
                        startActivity(intent);
                    } catch (Exception e) {

                    }
                }*/
            }
        }

        return returnedMat;
    }

    private void loadReference(int id){
        //surfDetector = SURF.create();
        mFeatureDetector = FeatureDetector.create(FeatureDetector.BRISK);
        mExtractor = DescriptorExtractor.create(DescriptorExtractor.BRISK);
        mMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
        mRefImg = new Mat();

        Bitmap bitmap = BitmapFactory.decodeResource(getApplicationContext().getResources(), id);
        Utils.bitmapToMat(bitmap, mRefImg);
        Imgproc.cvtColor(mRefImg, mRefImg, Imgproc.COLOR_RGB2BGR);
        Imgproc.cvtColor(mRefImg, mRefImg, Imgproc.COLOR_BGR2RGB);
        //Imgproc.cvtColor(mRefImg, mRefImg, Imgproc.COLOR_RGB2GRAY);
        //mRefImg.convertTo(mRefImg, 0); //converting the image to match with the type of the cameras image
        mRefDescriptor1 = new Mat();

        mRefKeypoints1 = new MatOfKeyPoint();
        mFeatureDetector.detect(mRefImg, mRefKeypoints1);
        mExtractor.compute(mRefImg, mRefKeypoints1, mRefDescriptor1);
        //surfDetector.detect(mRefImg, mRefKeypoints1);
        //surfDetector.compute(mRefImg, mRefKeypoints1, mRefDescriptor1);
    }

    private Mat extractFeaturesWithORB(Mat input, Mat refImg, FeatureDetector featureDetector, DescriptorExtractor extractor, DescriptorMatcher matcher, Mat refDescriptor, MatOfKeyPoint refKeypoints) {
        Imgproc.cvtColor(input, input, Imgproc.COLOR_RGB2BGR);
        Imgproc.cvtColor(input, input, Imgproc.COLOR_BGR2RGB);

        Mat descriptors = new Mat();
        MatOfKeyPoint keypoints = new MatOfKeyPoint();

        featureDetector.detect(input, keypoints);
        extractor.compute(input, keypoints, descriptors);

        Size size = descriptors.size();

        if (size.equals(new Size(0,0))) {
            Log.d(TAG, String.format("no features on input"));
            return null;
        }

        // Matching
        MatOfDMatch matches = new MatOfDMatch();
        if (refImg.type() == input.type()) {
            Log.d(TAG, String.format("type: %d, %d", refDescriptor.type(), descriptors.type()));
            matcher.match(refDescriptor, descriptors, matches);
            Log.d(TAG, String.format("matched"));
        } else {
            return null;
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
            if (matchesList.get(i).distance <= (1.5 * min_dist))
                good_matches.addLast(matchesList.get(i));
        }

        MatOfDMatch goodMatches = new MatOfDMatch();
        goodMatches.fromList(good_matches);

        //put keypoints mats into lists
        List<KeyPoint> keypoints1_List = refKeypoints.toList();
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

        if (good_matches.size() > 5) {

            MatOfPoint2f obj = new MatOfPoint2f();
            MatOfPoint2f scene = new MatOfPoint2f();
            obj.fromList(objList);
            scene.fromList(sceneList);

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

                MatOfPoint boundary = new MatOfPoint();
                ArrayList<Point> listOfBoundary = new ArrayList<>();
                listOfBoundary.add(new Point(scene_corners.get(0, 0)));
                listOfBoundary.add(new Point(scene_corners.get(1, 0)));
                listOfBoundary.add(new Point(scene_corners.get(2, 0)));
                listOfBoundary.add(new Point(scene_corners.get(3, 0)));
                boundary.fromList(listOfBoundary);

                ArrayList<MatOfPoint> boundaryList = new ArrayList<>();
                boundaryList.add(boundary);

                Imgproc.polylines(input, boundaryList, true, RED, 15);

                Point topLeft = new Point(Integer.MAX_VALUE, Integer.MAX_VALUE);
                Point bottomRight = new Point(Integer.MIN_VALUE, Integer.MIN_VALUE);

                for (int i = 0; i < scene_corners.rows(); i++) {
                    if (scene_corners.get(i, 0)[0] < topLeft.x)
                        topLeft.x = scene_corners.get(i, 0)[0];
                    if (scene_corners.get(i, 0)[1] < topLeft.y)
                        topLeft.y = scene_corners.get(i, 0)[1];
                    if (scene_corners.get(i, 0)[0] > bottomRight.x)
                        bottomRight.x = scene_corners.get(i, 0)[0];
                    if (scene_corners.get(i, 0)[1] > bottomRight.y)
                        bottomRight.y = scene_corners.get(i, 0)[1];
                }

                double area = (bottomRight.x - topLeft.x)*(bottomRight.y - topLeft.y);

                Log.d(TAG, String.format("(%.2f, %.2f), (%.2f, %.2f) Area: %.2f", topLeft.x, topLeft.y, bottomRight.x, bottomRight.y, area));

                boolean checkRange = true;

                for (int i = 0; i < scene_corners.rows(); i++) {
                    if (topLeft.y < 0 || bottomRight.y > input.rows())
                        checkRange = false;

                    if (topLeft.x < 0 || bottomRight.x > input.cols())
                        checkRange = false;
                }

                /*if (area > 100000 && checkRange) { //TODO: change the threshold value
                    mCropped = new Mat(refImg.size(), refImg.type());
                    Mat ref = new Mat(refImg.size(), refImg.type());
                    Mat cropped = new Mat(refImg.size(), refImg.type());

                    Mat transformMat = Imgproc.getPerspectiveTransform(scene_corners, obj_corners);
                    Imgproc.warpPerspective(input, mCropped, transformMat, refImg.size());

                    Log.d(TAG, String.format("RefImgSize (w,h): (%d, %d)",refImg.rows(), refImg.cols()));

                    Imgproc.resize(mCropped,cropped,input.size());

                    mDetected = true;

                    return cropped;
                }*/
            }
        }

        //Features2d.drawMatches(mRefImg, mRefKeypoints1, input, keypoints2, goodMatches, outputImg, GREEN, RED, drawnMatches, Features2d.NOT_DRAW_SINGLE_POINTS);
        //Imgproc.resize(outputImg, outputImg, input.size());
        //return outputImg;

        if (mDetected) {
            return null;
        } else {
            return input;
        }
    }

       //private Mat extractFeaturesWithSURFSIFT(Mat input) {
    /*private Mat extractFeaturesWithSURFSIFT(Mat input, Mat refImg, SURF detector, DescriptorMatcher matcher, Mat refDescriptor, MatOfKeyPoint refKeypoints) {
        Log.d(TAG, String.format("entering"));
        Imgproc.cvtColor(input, input, Imgproc.COLOR_RGB2GRAY);
        Mat descriptors = new Mat();
        MatOfKeyPoint keypoints = new MatOfKeyPoint();
        int kNeighbors = 5;

        long start = System.currentTimeMillis();
        detector.detect(input, keypoints);
        detector.compute(input, keypoints, descriptors);
        Log.d(TAG, String.format("%d taken for detectAndCompute", System.currentTimeMillis() - start));

        Size size = descriptors.size();

        Log.d(TAG, "descriptor size " + size.height + ", " + size.width);

        if (size.height < kNeighbors) {
            Log.d(TAG, String.format("no features on input"));
            return null;
        }

        // Matching
        ArrayList<MatOfDMatch> matches = new ArrayList<>();
        //ArrayList<MatOfDMatch> matchList = new ArrayList<>();
        if (refImg.type() == input.type()) {
            Log.d(TAG, String.format("type: %d, %d", refDescriptor.type(), descriptors.type()));
            matcher.knnMatch(refDescriptor, descriptors, matches, kNeighbors);
            Log.d(TAG, String.format("matched"));
            //mMatcher.knnMatch(mRefDescriptor1, descriptors2, matchList, 2);
        } else {
            return null;
        }

        float nndrRatio = 0.7f;

        LinkedList<DMatch> good_matches = new LinkedList<DMatch>();

        for (int i = 0; i < matches.size(); i++) {
            MatOfDMatch matofDMatch = matches.get(i);
            DMatch[] dmatcharray = matofDMatch.toArray();
            DMatch m1 = dmatcharray[0];
            DMatch m2 = dmatcharray[1];

            if (m1.distance <= m2.distance * nndrRatio) {
                good_matches.addLast(m1);

            }
        }

        MatOfDMatch goodMatches = new MatOfDMatch();
        goodMatches.fromList(good_matches);
        Mat outputImg = new Mat();
        MatOfByte drawnMatches = new MatOfByte();

        //put keypoints mats into lists
        List<KeyPoint> keypoints1_List = refKeypoints.toList();
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

        if (good_matches.size() > 5) {

            MatOfPoint2f obj = new MatOfPoint2f();
            MatOfPoint2f scene = new MatOfPoint2f();
            obj.fromList(objList);
            scene.fromList(sceneList);

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

                MatOfPoint boundary = new MatOfPoint();
                ArrayList<Point> listOfBoundary = new ArrayList<>();
                listOfBoundary.add(new Point(scene_corners.get(0, 0)));
                listOfBoundary.add(new Point(scene_corners.get(1, 0)));
                listOfBoundary.add(new Point(scene_corners.get(2, 0)));
                listOfBoundary.add(new Point(scene_corners.get(3, 0)));
                boundary.fromList(listOfBoundary);

                ArrayList<MatOfPoint> boundaryList = new ArrayList<>();
                boundaryList.add(boundary);

                Imgproc.polylines(input, boundaryList, true, RED, 15);

                Point topLeft = new Point(Integer.MAX_VALUE, Integer.MAX_VALUE);
                Point bottomRight = new Point(Integer.MIN_VALUE, Integer.MIN_VALUE);

                for (int i = 0; i < scene_corners.rows(); i++) {
                    if (scene_corners.get(i, 0)[0] < topLeft.x)
                        topLeft.x = scene_corners.get(i, 0)[0];
                    if (scene_corners.get(i, 0)[1] < topLeft.y)
                        topLeft.y = scene_corners.get(i, 0)[1];
                    if (scene_corners.get(i, 0)[0] > bottomRight.x)
                        bottomRight.x = scene_corners.get(i, 0)[0];
                    if (scene_corners.get(i, 0)[1] > bottomRight.y)
                        bottomRight.y = scene_corners.get(i, 0)[1];
                }

                double area = (bottomRight.x - topLeft.x)*(bottomRight.y - topLeft.y);

                Log.d(TAG, String.format("(%.2f, %.2f), (%.2f, %.2f) Area: %.2f", topLeft.x, topLeft.y, bottomRight.x, bottomRight.y, area));

                boolean checkRange = true;

                for (int i = 0; i < scene_corners.rows(); i++) {
                    if (topLeft.y < 0 || bottomRight.y > input.rows())
                        checkRange = false;

                    if (topLeft.x < 0 || bottomRight.x > input.cols())
                        checkRange = false;
                }

                /*if (area > 100000 && checkRange) { //TODO: change the threshold value
                    mCropped = new Mat(refImg.size(), refImg.type());
                    Mat ref = new Mat(refImg.size(), refImg.type());
                    Mat cropped = new Mat(refImg.size(), refImg.type());

                    Mat transformMat = Imgproc.getPerspectiveTransform(scene_corners, obj_corners);
                    Imgproc.warpPerspective(input, mCropped, transformMat, refImg.size());

                    Log.d(TAG, String.format("RefImgSize (w,h): (%d, %d)",refImg.rows(), refImg.cols()));

                    Imgproc.resize(mCropped,cropped,input.size());

                    mDetected = true;

                    return cropped;
                }
            }
        }

        if (mDetected) {
            return null;
        } else {
            return input;
        }
    }*/

    private Mat drawContourUsingSobel(Mat input) {
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

    @Override
    public boolean onTouch(View arg0, MotionEvent arg1) {
        // TODO Auto-generated method stub
        //mOpenCvCameraView.focusOnTouch(arg1);
        Log.d(TAG, String.format("Focus touched: (%.2f, %.2f)", arg1.getX(), arg1.getY()));
        return true;
    }
}
