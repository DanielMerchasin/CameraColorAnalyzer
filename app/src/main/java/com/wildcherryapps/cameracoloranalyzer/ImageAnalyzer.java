package com.wildcherryapps.cameracoloranalyzer;

import android.media.Image;

import java.nio.ByteBuffer;

public final class ImageAnalyzer {

    /** Log tag */
    private static final String TAG = "ImageAnalyzer";

    /**
     * This function counts all colors in an image in YUV_420_888 format
     * and returns the most common ones as an int array of colors in RGB format.
     * Implementation is in src/main/cpp/native-lib.cpp
     *
     * @param yBuffer   Image Y plane ByteBuffer
     * @param uBuffer   Image U plane ByteBuffer
     * @param vBuffer   Image V plane ByteBuffer
     * @param width     Image width
     * @param height    Image height
     * @param limit     Max amount of values to return
     * @return          Int array of most common colors in RGB format.
     */
    private static native int[] nativeAnalyzeCommonColors(ByteBuffer yBuffer,
                                                          ByteBuffer uBuffer,
                                                          ByteBuffer vBuffer,
                                                          int width,
                                                          int height,
                                                          int limit);

    /**
     * Convenience method for running the native function
     * {@link #nativeAnalyzeCommonColors(ByteBuffer, ByteBuffer, ByteBuffer, int, int, int)}
     *
     * @param image The Image to analyze
     * @param limit Max amount of values to return
     * @return      Int array of most common colors
     */
    public static int[] analyzeCommonColors(Image image, int limit) {
        return nativeAnalyzeCommonColors(
                image.getPlanes()[0].getBuffer(),
                image.getPlanes()[1].getBuffer(),
                image.getPlanes()[2].getBuffer(),
                image.getWidth(),
                image.getHeight(),
                limit);
    }

}
