package edu.washington.cs.ubicomplab.rdt_reader.models;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;

import edu.washington.cs.ubicomplab.rdt_reader.core.ImageProcessor;

/**
 * RDTCaptureResult represents the initial result of the image before inputting to image processing
 * pipeline. Its purpose is to identify the quality of the image and various features such as
 * directions, lighting quality and blurness
 */
public class RDTCaptureResult {
    public boolean allChecksPassed;
    public Mat resultMat;
    public MatOfPoint2f boundary;
    public ImageProcessor.ExposureResult exposureResult;
    public ImageProcessor.SizeResult sizeResult;
    public boolean isCentered;
    public boolean isRightOrientation;
    public boolean isSharp;
    public boolean isShadow;
    public boolean fiducial;
    public double angle;
    public boolean flashEnabled;
    public boolean isGlared;

    public RDTCaptureResult(boolean allChecksPassed, Mat resultMat, boolean fiducial,
                            ImageProcessor.ExposureResult exposureResult, ImageProcessor.SizeResult sizeResult, boolean isCentered,
                            boolean isRightOrientation, double angle, boolean isSharp, boolean isShadow, boolean isGlared, MatOfPoint2f boundary, boolean flashEnabled){
        this.allChecksPassed = allChecksPassed;
        this.resultMat = resultMat;
        this.fiducial = fiducial;
        this.exposureResult = exposureResult;
        this.sizeResult = sizeResult;
        this.isCentered = isCentered;
        this.isRightOrientation = isRightOrientation;
        this.isSharp = isSharp;
        this.isShadow = isShadow;
        this.angle = angle;
        this.boundary = boundary;
        this.flashEnabled = flashEnabled;
        this.isGlared = isGlared;
    }
}