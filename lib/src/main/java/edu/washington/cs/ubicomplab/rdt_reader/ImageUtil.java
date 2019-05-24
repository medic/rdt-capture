package edu.washington.cs.ubicomplab.rdt_reader;

/**
 * Created by cjpark on 6/30/18.
 */

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

import edu.washington.cs.ubicomplab.rdt_reader.callback.OnImageSavedCallBack;

public final class ImageUtil {

    private static final  String TAG = ImageUtil.class.getName();

    public static byte[] imageToByteArray(Image image) {
        byte[] data = null;
        if (image.getFormat() == ImageFormat.JPEG) {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            data = new byte[buffer.capacity()];
            buffer.get(data);
            return data;
        } else if (image.getFormat() == ImageFormat.YUV_420_888) {
            data = NV21toJPEG(
                    YUV_420_888toNV21(image),
                    image.getWidth(), image.getHeight());
        }
        return data;
    }

    private static byte[] YUV_420_888toNV21(Image image) {
        byte[] nv21;
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        nv21 = new byte[ySize + uSize + vSize];

        //U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        return nv21;
    }

    private static byte[] NV21toJPEG(byte[] nv21, int width, int height) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
        yuv.compressToJpeg(new Rect(0, 0, width, height), 100, out);
        return out.toByteArray();
    }

    public static Mat imageToRGBMat(Image image) {
        Mat yuvMat = imageToMat(image);
        Mat bgrMat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC4);
        image.close();
        Imgproc.cvtColor(yuvMat, bgrMat, Imgproc.COLOR_YUV2BGR_I420);
        Mat rgbaMat = new Mat();
        Imgproc.cvtColor(bgrMat, rgbaMat, Imgproc.COLOR_BGR2RGBA, 0);

        yuvMat.release();
        bgrMat.release();

        return rgbaMat;
    }

    public static Mat imageToMat(Image image) {
        ByteBuffer buffer;
        int rowStride;
        int pixelStride;
        int width = image.getWidth();
        int height = image.getHeight();
        int offset = 0;

        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[image.getWidth() * image.getHeight() * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];

        for (int i = 0; i < planes.length; i++) {
            buffer = planes[i].getBuffer();
            rowStride = planes[i].getRowStride();
            pixelStride = planes[i].getPixelStride();
            int w = (i == 0) ? width : width / 2;
            int h = (i == 0) ? height : height / 2;
            for (int row = 0; row < h; row++) {
                int bytesPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8;
                if (pixelStride == bytesPerPixel) {
                    int length = w * bytesPerPixel;
                    buffer.get(data, offset, length);

                    if (h - row != 1) {
                        buffer.position(buffer.position() + rowStride - length);
                    }
                    offset += length;
                } else {


                    if (h - row == 1) {
                        buffer.get(rowData, 0, width - pixelStride + 1);
                    } else {
                        buffer.get(rowData, 0, rowStride);
                    }

                    for (int col = 0; col < w; col++) {
                        data[offset++] = rowData[col * pixelStride];
                    }
                }
            }
        }

        Mat mat = new Mat(height + height / 2, width, CvType.CV_8UC1);
        mat.put(0, 0, data);

        return mat;
    }


    public static byte[] matToRotatedByteArray(Mat captureMat) {
        Bitmap resultBitmap = Bitmap.createBitmap(captureMat.cols(), captureMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(captureMat, resultBitmap);

        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        resultBitmap = Bitmap.createBitmap(resultBitmap, 0, 0, resultBitmap.getWidth(), resultBitmap.getHeight(), matrix, true);

        ByteArrayOutputStream bs = new ByteArrayOutputStream();
        resultBitmap.compress(Bitmap.CompressFormat.JPEG, 100, bs);

        return bs.toByteArray();
    }

    private static class SaveImageParams {
        private Context context;
        private byte[] byteArray;
        private long timeTaken;
        private OnImageSavedCallBack onImageSavedCallBack;

        public SaveImageParams(Context context, byte[] byteArray, long timeTaken, OnImageSavedCallBack onImageSavedCallBack) {
            this.context = context;
            this.byteArray = byteArray;
            this.timeTaken = timeTaken;
            this.onImageSavedCallBack = onImageSavedCallBack;
        }

        public Context getContext() {
            return context;
        }

        public byte[] getByteArray() {
            return byteArray;
        }

        public long getTimeTaken() {
            return timeTaken;
        }

        public OnImageSavedCallBack getOnImageSavedCallBack() {
            return onImageSavedCallBack;
        }
    }

    public static void saveImage(final Context context, final byte[] byteArray, final long timeTaken, final OnImageSavedCallBack onImageSavedCallBack) {

        class SaveImageTask extends AsyncTask<SaveImageParams, Void, String> {
            private OnImageSavedCallBack imageSavedCallBack;
            @Override
            protected String doInBackground(SaveImageParams... params) {
                String filePath = null;
                SaveImageParams imageParams = params[0];
                File sdIconStorageDir = new File(Constants.RDT_IMAGE_DIR);
                sdIconStorageDir.mkdirs();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss-SSS");
                imageSavedCallBack = imageParams.getOnImageSavedCallBack();
                try {
                    filePath = sdIconStorageDir.toString() + String.format("/%s-%08dms.jpg", sdf.format(new Date()), imageParams.getTimeTaken());
                    FileOutputStream fileOutputStream = new FileOutputStream(filePath);

                    fileOutputStream.write(imageParams.getByteArray());

                    fileOutputStream.flush();
                    fileOutputStream.close();

                    imageParams.getContext().sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + filePath)));

                    Log.i(TAG, "Image successfully saved!");
                    imageSavedCallBack.onImageSaved(filePath);
                } catch (Exception e) {
                    Log.e(TAG, "Error saving image file: " + e.getMessage());
                }
                return filePath;
            }

            protected void onPostExecute(String imageFilePath) {
                imageSavedCallBack.onImageSaved(imageFilePath);
            }
        }

        new SaveImageTask().execute(new SaveImageParams(context, byteArray, timeTaken, onImageSavedCallBack));
    }
}
