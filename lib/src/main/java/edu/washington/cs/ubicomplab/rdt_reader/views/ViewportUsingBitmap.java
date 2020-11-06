/*
 * Copyright (C) 2019 University of Washington Ubicomp Lab
 * All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of a BSD-style license that can be found in the LICENSE file.
 */

package edu.washington.cs.ubicomplab.rdt_reader.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.support.annotation.ColorRes;
import android.util.AttributeSet;
import android.view.ViewGroup;

import edu.washington.cs.ubicomplab.rdt_reader.R;

/**
 *
 */
public class ViewportUsingBitmap extends ViewGroup {
    // UI elements
    private Canvas temp;
    private Bitmap bitmap;

    // Scale parameters
    public float hScale;
    public float wScale;

    @ColorRes
    int backgroundColorId = R.color.black_overlay;

    public ViewportUsingBitmap(Context context) {
        super(context);
    }

    public ViewportUsingBitmap(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * {@link android.view.ViewGroup} constructor
     * @param context: the context where the viewgroup is being used
     * @param attrs: the XML attributes for the viewgroup
     * @param defStyle: the default style to be applied to this viewgroup
     */
    public ViewportUsingBitmap(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.ViewportUsingBitmap,
                0, 0);
        hScale = ta.getFloat(R.styleable.ViewportUsingBitmap_heightScale, hScale);
        wScale = ta.getFloat(R.styleable.ViewportUsingBitmap_widthScale, wScale);
    }

    /**
     * {@link android.view.ViewGroup} onMeasure()
     * @param widthMeasureSpec: the measured width of this viewgroup
     * @param heightMeasureSpec: the measured height of this viewgroup
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * {@link android.view.ViewGroup} onLayout()
     * @param changed: whether this is a new size for this view
     * @param left: left position relative to parent
     * @param top: top position relative to parent
     * @param right: right position relative to parent
     * @param bottom: bottom position relative to parent
     */
    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        temp = new Canvas(bitmap);
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    /**
     * Draws a transparent overlay with a rectangular hole in the middle for where the
     * RDT should be framed in the camera
     * @param canvas: the canvas that is being drawn on
     */
    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        // Calculate the dimensiosn of the different components
        float width = ((float) getWidth())*wScale;
        float height = ((float) getHeight())*hScale;
        float x = (getWidth() - width)/2;
        float y = (getHeight() - height)/2;
        RectF rect = new RectF(x, y, x+width, y+height);
        RectF frame = new RectF(x-1, y-1, x+width+2, y+height+2);
        Path path = new Path();

        // Draw the background
        Paint bgPaint = new Paint();
        bgPaint.setColor(getResources().getColor(backgroundColorId));
        temp.drawRect(0, 0, getWidth(), getHeight(), bgPaint);

        // Draw the outline around the hole
        Paint stroke = new Paint();
        stroke.setAntiAlias(true);
        stroke.setStrokeWidth(4);
        stroke.setColor(Color.WHITE);
        stroke.setStyle(Paint.Style.STROKE);
        int viewportCornerRadius = 8;
        path.addRoundRect(frame, (float) viewportCornerRadius, (float) viewportCornerRadius, Path.Direction.CW);
        temp.drawPath(path, stroke);

        // Draw the hole
        Paint eraser = new Paint();
        eraser.setAntiAlias(true);
        eraser.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        temp.drawRoundRect(rect, (float) viewportCornerRadius, (float) viewportCornerRadius, eraser);

        // Update the bitmap
        canvas.drawBitmap(bitmap, 0, 0, new Paint());
    }
}
