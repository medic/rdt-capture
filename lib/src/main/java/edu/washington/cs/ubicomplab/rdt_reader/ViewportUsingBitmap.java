/*
 * Copyright (C) 2019 University of Washington Ubicomp Lab
 * All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of a BSD-style license that can be found in the LICENSE file.
 */

package edu.washington.cs.ubicomplab.rdt_reader;

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

public class ViewportUsingBitmap extends ViewGroup {
    private float hScale;
    private float wScale;

    private Canvas temp;
    private Paint paint;
    private Paint p = new Paint();
    private Bitmap bitmap;

    @ColorRes
    int backgroundColorId = R.color.black_overlay;

    public ViewportUsingBitmap(Context context) {
        super(context);
    }

    public ViewportUsingBitmap(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ViewportUsingBitmap(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.Viewport, 0, 0);
        hScale = ta.getFloat(R.styleable.Viewport_heightScale, hScale);
        wScale = ta.getFloat(R.styleable.Viewport_widthScale, wScale);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        temp = new Canvas(bitmap);
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        int viewportMargin = 32;
        int viewportCornerRadius = 8;
        Paint eraser = new Paint();
        eraser.setAntiAlias(true);
        eraser.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        float width = ((float)getWidth())*wScale;
        float height = ((float)getHeight())*hScale;
        float x = (getWidth() - width)/2;
        float y = (getHeight() - height)/2;
        RectF rect = new RectF(x, y, x+width, y+height);
        RectF frame = new RectF(x-1, y-1, x+width+2, y+height+2);
        Path path = new Path();
        Paint stroke = new Paint();
        stroke.setAntiAlias(true);
        stroke.setStrokeWidth(4);
        stroke.setColor(Color.WHITE);
        stroke.setStyle(Paint.Style.STROKE);
        path.addRoundRect(frame, (float) viewportCornerRadius, (float) viewportCornerRadius, Path.Direction.CW);

        paint = new Paint();
        paint.setColor(getResources().getColor(backgroundColorId));

        temp.drawRect(0, 0, getWidth(), getHeight(), paint);
        temp.drawPath(path, stroke);
        temp.drawRoundRect(rect, (float) viewportCornerRadius, (float) viewportCornerRadius, eraser);

        canvas.drawBitmap(bitmap, 0, 0, p);
    }

    public void setBackgroundColoId(@ColorRes int id) {
        backgroundColorId = id;
        this.invalidate();
    }

    public @ColorRes int getBackgroundColorId() {
        return backgroundColorId;
    }
}
