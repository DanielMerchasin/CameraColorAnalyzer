package com.wildcherryapps.cameracoloranalyzer.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

import com.wildcherryapps.cameracoloranalyzer.R;

import java.util.Random;

public class ColorPanelView extends View {

    /** Log tag */
    private static final String TAG = "ColorPanelView";

    /** Default color count if not specified */
    private static final int DEFAULT_COLOR_COUNT = 3;

    /** Colors data */
    private ColorBlock[] colorBlocks;

    /** View Orientation - true = vertical, false = horizontal */
    private boolean orientationVertical;

    public ColorPanelView(Context context) {
        super(context);
        init(null);
    }

    public ColorPanelView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public ColorPanelView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    public ColorPanelView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs);
    }

    public void init(AttributeSet attrs) {

        int colorCount;

        if (attrs != null) {
            // Load attributes
            TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.ColorPanelView);
            colorCount = ta.getInt(R.styleable.ColorPanelView_max_colors, DEFAULT_COLOR_COUNT);
            orientationVertical = ta.getInt(R.styleable.ColorPanelView_orientation, 0) == 1;
            ta.recycle();
        } else {
            // Default values
            colorCount = DEFAULT_COLOR_COUNT;
        }

        // Initialize color blocks
        colorBlocks = new ColorBlock[colorCount];
        for (int i = 0; i < colorBlocks.length; i++)
            colorBlocks[i] = new ColorBlock(0xFF000000, 1f / colorBlocks.length);

    }

    public ColorBlock[] getColorBlocks() {
        return colorBlocks;
    }

    private float getFactorSum() {

        // Calculate factor sum
        float factorSum = 0f;
        for (ColorBlock colorBlock: colorBlocks)
            factorSum += colorBlock.getPercentage();
        return factorSum;

    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final float factorSum = getFactorSum();

        float rectStart = 0f;

        for (ColorBlock colorBlock: colorBlocks) {

            final float blockSize;
            final float rectEnd;
            if (orientationVertical) {
                blockSize = colorBlock.getPercentage() / factorSum * getHeight();
                rectEnd = rectStart + blockSize;
                canvas.drawRect(0, rectStart, getWidth(), rectEnd, colorBlock.getPaint());
            } else {
                blockSize = colorBlock.getPercentage() / factorSum * getWidth();
                rectEnd = rectStart + blockSize;
                canvas.drawRect(rectStart, 0, rectEnd, getHeight(), colorBlock.getPaint());
            }

            rectStart += blockSize;

        }

    }

}
