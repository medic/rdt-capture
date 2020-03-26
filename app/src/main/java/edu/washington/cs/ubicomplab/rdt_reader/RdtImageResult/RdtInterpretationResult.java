package edu.washington.cs.ubicomplab.rdt_reader.RdtImageResult;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

/**
 * Interpretation result represents the image result of RDT image after processing which describes if
 * a particular line of a RDT is appeared (3 lines in this example)
 */

public class RdtInterpretationResult {
    public boolean topLine;
    public boolean middleLine;
    public boolean bottomLine;
    public String topLineName;
    public String middleLineName;
    public String bottomLineName;
    public Mat resultMat;
    public Bitmap resultBitmap;

    public RdtInterpretationResult() {
        topLine = false;
        middleLine = false;
        bottomLine = false;
        topLineName = "Top Line";
        middleLineName = "Middle Line";
        bottomLineName = "Bottom Line";
        resultMat = new Mat();
        resultBitmap = null;
    }

    public RdtInterpretationResult(Mat resultMat, boolean topLine, boolean middleLine, boolean bottomLine,
                                   String topLineName, String middleLineName, String bottomLineName){
        this.resultMat = resultMat;
        this.topLine = topLine;
        this.middleLine = middleLine;
        this.bottomLine = bottomLine;
        this.topLineName = topLineName;
        this.middleLineName = middleLineName;
        this.bottomLineName = bottomLineName;
        if (resultMat.cols() > 0 && resultMat.rows() > 0) {
            this.resultBitmap = Bitmap.createBitmap(resultMat.cols(), resultMat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(resultMat, resultBitmap);
        }
    }
}

