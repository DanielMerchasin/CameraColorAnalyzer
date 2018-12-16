#include <jni.h>
#include <iostream>
#include <unordered_map>
#include <vector>

extern "C" {

//
// This function counts all colors in an image in YUV_420_888 format 
// and returns the most common ones as an int array of colors in RGB format.
//
// YUV to RGB conversion formula taken from here:
// http://werner-dittmann.blogspot.com/2016/03/solving-some-mysteries-about-androids.html
// 
// Parameters:
// yBuffer = Image Y plane ByteBuffer
// uBuffer = Image U plane ByteBuffer
// vBuffer = Image V plane ByteBuffer
// width = Image width
// height = Image height
// limit = Max amount of values to return
//
JNIEXPORT jintArray 
JNICALL Java_com_wildcherryapps_cameracoloranalyzer_ImageAnalyzer_nativeAnalyzeCommonColors
        (JNIEnv* env, jclass type, jobject yBuffer, jobject uBuffer,
         jobject vBuffer, jint width, jint height, jint limit)
{

    // Get the byte arrays from the plane buffers
    jbyte* yBytes = reinterpret_cast<jbyte*>(env->GetDirectBufferAddress(yBuffer));
    jbyte* uBytes = reinterpret_cast<jbyte*>(env->GetDirectBufferAddress(uBuffer));
    jbyte* vBytes = reinterpret_cast<jbyte*>(env->GetDirectBufferAddress(vBuffer));

    // Array lengths
    jlong yLength = env->GetDirectBufferCapacity(yBuffer);
    jlong uvLength = env->GetDirectBufferCapacity(uBuffer);

    // The counting map (key = color, value = count)
    std::unordered_map<jint, jint>* countMap = new std::unordered_map<jint, jint>();

    // Iterate over all pixels in the frame,
    // convert them to RGB and add them to the counting map
    jint yPos = 0;
    for (jint i = 0; i < height; i += 1)
    {
        jint uvPos = (i >> 1) * width;
        
        for (jint j = 0; j < width; j += 1)
        {

            if (uvPos >= uvLength - 1)
                break;
            if (yPos >= yLength)
                break;

            // YUV pixel values
            jint y1 = yBytes[yPos++] & 0xFF;
            jint u = (uBytes[uvPos] & 0xFF) - 128;
            jint v = (vBytes[uvPos + 1] & 0xFF) - 128;

            // Advance UV position
            if ((j & 1) == 1)
                uvPos += 2;

            // Bug fix - in some older Android versions, the U and V buffers are mostly filled
            // with 0 values, so we remove them from the calculation
            if (u == -128 && v == -128)
                continue;

            // Convert the pixel from YUV to RGB
            jint y1192 = 1192 * y1;
            jint r = y1192 + 1634 * v;
            jint g = y1192 - 833 * v - 400 * u;
            jint b = y1192 + 2066 * u;

            // Clamp values
            r = (r < 0) ? 0 : ((r > 262143) ? 262143 : r);
            g = (g < 0) ? 0 : ((g > 262143) ? 262143 : g);
            b = (b < 0) ? 0 : ((b > 262143) ? 262143 : b);

            // Convert to int
            jint color = 0xFF000000             // Alpha
                        | ((r << 6) & 0xFF0000) // Red
                        | ((g >> 2) & 0xFF00)   // Green
                        | ((b >> 10) & 0xFF);   // Blue

            // Add the color to the counting map
            auto it = countMap->find(color);
            countMap->insert({color, it == countMap->end() ? 0 : it->second + 1});
        }

    }

    // Create a vector of pairs using the map entries
    std::vector<std::pair<jint, jint>>* vector = new std::vector<std::pair<jint, jint>>
            (countMap->begin(), countMap->end());

    // Sort the vector by pair value (count), descending
    std::sort(vector->begin(), vector->end(),
              [](const std::pair<jint, jint>& x, const std::pair<jint, jint>& y)
    {
        return x.second < y.second;
    });
    
    // The result array
    size_t len = static_cast<size_t>(limit < vector->size() ? limit : vector->size());
    jintArray result = env->NewIntArray(static_cast<jsize>(len));
    jint* elements = env->GetIntArrayElements(result, NULL);

    // Populate the array with the common colors
    auto it = vector->begin();
    for (size_t i = 0; i < len; i++, it++)
        elements[i] = it->first;

    // Clean up
    delete countMap;
    delete vector;
    env->ReleaseIntArrayElements(result, elements, NULL);

    return result;

}
}
