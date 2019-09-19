/*
 * Copyright (C) 2019 University of Washington Ubicomp Lab
 * All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of a BSD-style license that can be found in the LICENSE file.
 */

package edu.washington.cs.ubicomplab.rdt_reader;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.Toast;

import org.opencv.android.JavaCameraView;

import java.util.ArrayList;
import java.util.List;

public class RDTCameraView extends JavaCameraView implements Camera.AutoFocusCallback {
    public RDTCameraView(Context context, int cameraId) {
        super(context, cameraId);
    }

    public RDTCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public Camera getCamera() {
        return this.mCamera;
    }

    public List<Camera.Size> getResolutionList() {
        return  mCamera.getParameters().getSupportedPreviewSizes();
    }

    public Camera.Size getResolution() {
        Camera.Parameters params = mCamera.getParameters();
        Camera.Size s = params.getPreviewSize();
        return s;
    }

    public void setResolution(Camera.Size resolution) {
        disconnectCamera();
        connectCamera((int)resolution.width, (int)resolution.height);
    }

    public void focusOnTouch(MotionEvent event) {
        Rect focusRect = calculateTapArea(event.getRawX(), event.getRawY(), 1f);
        Rect meteringRect = calculateTapArea(event.getRawX(), event.getRawY(), 1.5f);

        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);

        if (parameters.getMaxNumFocusAreas() > 0) {
            List<Camera.Area> focusAreas = new ArrayList<Camera.Area>();
            focusAreas.add(new Camera.Area(focusRect, 1000));

            parameters.setFocusAreas(focusAreas);
        }

        if (parameters.getMaxNumMeteringAreas() > 0) {
            List<Camera.Area> meteringAreas = new ArrayList<Camera.Area>();
            meteringAreas.add(new Camera.Area(meteringRect, 1000));

            parameters.setMeteringAreas(meteringAreas);
        }

        mCamera.setParameters(parameters);
        mCamera.autoFocus(this);
    }

    /**
     * Convert touch position x:y to {@link Camera.Area} position -1000:-1000 to 1000:1000.
     */
    private Rect calculateTapArea(float x, float y, float coefficient) {
        float focusAreaSize = 300;
        int areaSize = Float.valueOf(focusAreaSize * coefficient).intValue();

        int centerX = (int) (x / getResolution().width - 1000);
        int centerY = (int) (y / getResolution().height - 1000);

        int left = clamp(centerX - areaSize / 2, -1000, 1000);
        int top = clamp(centerY - areaSize / 2, -1000, 1000);

        RectF rectF = new RectF(left, top, left + areaSize, top + areaSize);

        return new Rect(Math.round(rectF.left), Math.round(rectF.top), Math.round(rectF.right), Math.round(rectF.bottom));
    }

    private int clamp(int x, int min, int max) {
        if (x > max) {
            return max;
        }
        if (x <min) {
            return min;
        }
        return x;
    }

    public void setFocusMode (Context item, int type){
        Camera.Parameters params = mCamera.getParameters();
        List<String> FocusModes = params.getSupportedFocusModes();

        switch (type){
            case 0:
                if (FocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO))
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                else
                    Toast.makeText(item, "Auto Mode not supported", Toast.LENGTH_SHORT).show();
                break;
            case 1:
                if (FocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                else
                    Toast.makeText(item, "Continuous Mode not supported", Toast.LENGTH_SHORT).show();
                break;
            case 2:
                if (FocusModes.contains(Camera.Parameters.FOCUS_MODE_EDOF))
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_EDOF);
                else
                    Toast.makeText(item, "EDOF Mode not supported", Toast.LENGTH_SHORT).show();
                break;
            case 3:
                if (FocusModes.contains(Camera.Parameters.FOCUS_MODE_FIXED))
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
                else
                    Toast.makeText(item, "Fixed Mode not supported", Toast.LENGTH_SHORT).show();
                break;
            case 4:
                if (FocusModes.contains(Camera.Parameters.FOCUS_MODE_INFINITY))
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
                else
                    Toast.makeText(item, "Infinity Mode not supported", Toast.LENGTH_SHORT).show();
                break;
            case 5:
                if (FocusModes.contains(Camera.Parameters.FOCUS_MODE_MACRO))
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_MACRO);
                else
                    Toast.makeText(item, "Macro Mode not supported", Toast.LENGTH_SHORT).show();
                break;
        }

        mCamera.setParameters(params);
    }

    public void setFlashMode (Context item, int type){
        Camera.Parameters params = mCamera.getParameters();
        List<String> FlashModes = params.getSupportedFlashModes();

        switch (type){
            case 0:
                if (FlashModes.contains(Camera.Parameters.FLASH_MODE_AUTO))
                    params.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
                else
                    Toast.makeText(item, "Auto Mode not supported", Toast.LENGTH_SHORT).show();
                break;
            case 1:
                if (FlashModes.contains(Camera.Parameters.FLASH_MODE_OFF))
                    params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                else
                    Toast.makeText(item, "Off Mode not supported", Toast.LENGTH_SHORT).show();
                break;
            case 2:
                if (FlashModes.contains(Camera.Parameters.FLASH_MODE_ON))
                    params.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
                else
                    Toast.makeText(item, "On Mode not supported", Toast.LENGTH_SHORT).show();
                break;
            case 3:
                if (FlashModes.contains(Camera.Parameters.FLASH_MODE_RED_EYE))
                    params.setFlashMode(Camera.Parameters.FLASH_MODE_RED_EYE);
                else
                    Toast.makeText(item, "Red Eye Mode not supported", Toast.LENGTH_SHORT).show();
                break;
            case 4:
                if (FlashModes.contains(Camera.Parameters.FLASH_MODE_TORCH))
                    params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                else
                    Toast.makeText(item, "Torch Mode not supported", Toast.LENGTH_SHORT).show();
                break;
        }

        mCamera.setParameters(params);
    }

    @Override
    public void onAutoFocus(boolean arg0, Camera arg1) {

    }

    public void turnOffTheFlash() {
        Camera.Parameters params = mCamera.getParameters();
        params.setFlashMode(params.FLASH_MODE_OFF);
        mCamera.setParameters(params);
    }

    public void turnOnTheFlash() {
        Camera.Parameters params = mCamera.getParameters();
        params.setFlashMode(params.FLASH_MODE_TORCH);
        mCamera.setParameters(params);
    }
}
