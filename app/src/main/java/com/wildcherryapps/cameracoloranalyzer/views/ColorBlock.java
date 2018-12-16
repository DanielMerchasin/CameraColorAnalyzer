package com.wildcherryapps.cameracoloranalyzer.views;

import android.graphics.Paint;

/**
 * Color data model of {@link ColorPanelView}
 */
public class ColorBlock {

    private Paint paint;
    private float percentage;

    public ColorBlock() {
        this(0, 0f);
    }

    public ColorBlock(int color, float percentage) {
        this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.paint.setColor(color);
        this.percentage = percentage;
    }

    public Paint getPaint() {
        return paint;
    }

    public void setPaint(Paint paint) {
        this.paint = paint;
    }

    public float getPercentage() {
        return percentage;
    }

    public void setPercentage(float percentage) {
        this.percentage = percentage;
    }

    public void reset() {
        paint.setColor(0);
        percentage = 0f;
    }
}
