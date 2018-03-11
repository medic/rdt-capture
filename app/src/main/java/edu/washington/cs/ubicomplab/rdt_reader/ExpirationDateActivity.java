package edu.washington.cs.ubicomplab.rdt_reader;

import static com.google.android.gms.vision.Frame.ROTATION_90;
import static edu.washington.cs.ubicomplab.rdt_reader.Constants.*;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.media.Image;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.Text;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.nio.ByteBuffer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.StringTokenizer;

public class ExpirationDateActivity extends AppCompatActivity implements CvCameraViewListener2 {

    private RDTCamera2View mOpenCvCameraView;
    private TextRecognizer mTextRecognizer;
    private TextView mExpDateResultView;

    private final String NOT_DETECTED_MSG = "EXP DATE NOT DETECTED.\n";
    private final String EXPIRED_MSG = "EXPIRED!\n DO NOT USE THIS RDT!";
    private final String VALID_MSG = "VALID!\n YOU CAN USE THIS RDT.";

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
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expiration_date);

        setTitle("Expiration Date Checker");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        mOpenCvCameraView = findViewById(R.id.exp_date_check_camera_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        mTextRecognizer = new TextRecognizer.Builder(getApplicationContext()).build();

        mExpDateResultView = findViewById(R.id.exp_date_result_view);
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

    /*OpenCV JavaCameraView callbacks*/
    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        System.gc();

        final Date now = new Date();
        final Date expDate = readExpirationDate(inputFrame.gray());

        /*Image image = inputFrame.image();

        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        Bitmap myBitmap = BitmapFactory.decodeByteArray(bytes,0,bytes.length,null);*/

        //final Date expDate = readExpirationDate(inputFrame.gray());
        //final Date expDate = readExpirationDate(myBitmap);
        //final Date expDate = readExpirationDate(inputFrame.image());

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (expDate.getTime() == new Date(0).getTime()) {
                    mExpDateResultView.setText(NOT_DETECTED_MSG);
                    mExpDateResultView.setBackgroundColor(getResources().getColor(R.color.gray_overlay));
                } else {
                    if (now.before(expDate)) {
                        mExpDateResultView.setText(VALID_MSG);
                        mExpDateResultView.setBackgroundColor(getResources().getColor(R.color.green_overlay));
                    } else {
                        mExpDateResultView.setText(EXPIRED_MSG);
                        mExpDateResultView.setBackgroundColor(getResources().getColor(R.color.red_overlay));
                    }
                }
            }
        });

        return inputFrame.rgba();
    }

    /*Private methods*/
    private Date readExpirationDate(Mat inputFrame) {
        Bitmap tempBitmap = Bitmap.createBitmap(inputFrame.cols(), inputFrame.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(inputFrame, tempBitmap);
        Frame frame = new Frame.Builder().setBitmap(tempBitmap).setRotation(ROTATION_90).build();
        SparseArray<TextBlock> items = mTextRecognizer.detect(frame);

        Date expDate = new Date(0);

        for (int i = 0; i < items.size(); ++i) {
            TextBlock item = items.valueAt(i);
            for (Text currText : item.getComponents()) {
                StringTokenizer st = new StringTokenizer(currText.getValue());
                ArrayList<SimpleDateFormat> dfs = new ArrayList<>();

                for (String format: DATE_FORMATS) {
                    dfs.add(new SimpleDateFormat(format));
                }

                while (st.hasMoreTokens()) {
                    String str = st.nextToken();
                    for (SimpleDateFormat df : dfs) {
                        if (str.length() > 8) {
                            try {
                                expDate = df.parse(str).after(expDate) ? df.parse(str) : expDate;
                            } catch (ParseException pe) {

                            }
                        }
                    }
                }
            }
        }

        return expDate;
    }
}
