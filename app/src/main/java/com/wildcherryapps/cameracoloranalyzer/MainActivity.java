package com.wildcherryapps.cameracoloranalyzer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import com.wildcherryapps.cameracoloranalyzer.views.Alert;
import com.wildcherryapps.cameracoloranalyzer.views.ColorBlock;
import com.wildcherryapps.cameracoloranalyzer.views.ColorPanelView;

import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    // Load the native library
    static { System.loadLibrary("native-lib"); }

    /** Log tag */
    private static final String TAG = "MainActivity";

    /** Camera background thread name */
    private static final String CAMERA_HANDLER_THREAD_NAME = "camera_handler_thread";

    /** Permission request code for camera permission */
    private static final int CAMERA_PERMISSION_REQUEST = 5;

    // --- UI Components
    private TextureView textureView;
    private ColorPanelView colorPanelView;

    // --- Camera stuff

    /** Back facing camera ID */
    private String cameraId;

    /** Camera manager system service */
    private CameraManager cameraManager;

    /** The camera device */
    private CameraDevice cameraDevice;

    /** Capture session */
    private CameraCaptureSession captureSession;

    /** Image reader */
    private ImageReader imageReader;

    /** TextureView surface size */
    private Size size;

    /** TextureView surface */
    private Surface surface;

    /** Camera multithreading */
    private Handler cameraHandler;
    private HandlerThread cameraHandlerThread;
    private final Semaphore cameraLock = new Semaphore(1);

    /** Camera State Callback */
    private final CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraLock.release();
            cameraDevice = camera;
            try {
                createCaptureSession();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraLock.release();
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraLock.release();
            camera.close();
            cameraDevice = null;
        }
    };

    /** Capture Session State Callback */
    private final CameraCaptureSession.StateCallback captureSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {

            if (cameraDevice == null)
                return;

            captureSession = session;

            try {
                final CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                builder.addTarget(surface);
                builder.addTarget(imageReader.getSurface());
                session.setRepeatingRequest(builder.build(), null, cameraHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

        }
    };

    /** TextureView Surface Texture Listener */
    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            startHandler();
            try {
                initializeCamera();
                openCamera();
            } catch (Exception e) {
                Alert.display(MainActivity.this, "Error", e.getMessage());
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    /** ImageReader Callback */
    private final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {

            // Get the image from ImageReader
            final Image image = reader.acquireLatestImage();

            // Call the native analyzing function
            final int[] result = ImageAnalyzer.analyzeCommonColors(image,
                    colorPanelView.getColorBlocks().length);

            image.close();

            int i = 0;
            final int numIterations = Math.min(result.length, colorPanelView.getColorBlocks().length);

            // Update block colors
            for (; i < numIterations; i++) {
                ColorBlock colorBlock = colorPanelView.getColorBlocks()[i];
                colorBlock.getPaint().setColor(result[i]);
            }

            // Paint remaining blocks black
            for (; i < colorPanelView.getColorBlocks().length; i++) {
                ColorBlock colorBlock = colorPanelView.getColorBlocks()[i];
                colorBlock.getPaint().setColor(0xFF000000);
            }

            // Update the view
            colorPanelView.postInvalidate();

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI
        textureView = findViewById(R.id.textureView);
        colorPanelView = findViewById(R.id.colorPanelView);

    }

    @Override
    protected void onResume() {
        super.onResume();
        textureView.setSurfaceTextureListener(surfaceTextureListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopHandler();
    }

    @Override
    protected void onStop() {
        super.onStop();
        closeCamera();
    }

    private void initializeCamera() throws Exception {

        cameraManager = getSystemService(CameraManager.class);

        if (cameraManager == null) {
            throw new Exception("The camera manager service doesn't exist on this device.");
        }

        try {
            // Find the back facing camera
            for (String id : cameraManager.getCameraIdList()) {
                final CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                final Integer cameraLensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (cameraLensFacing == null || cameraLensFacing != CameraMetadata.LENS_FACING_BACK)
                    continue;

                final StreamConfigurationMap streamConfigurationMap =
                        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (streamConfigurationMap == null)
                    continue;

                // Set the camera ID
                this.cameraId = id;

                // Set the display size
                size = streamConfigurationMap.getOutputSizes(SurfaceTexture.class)[0];

                // Initialize ImageReader
                imageReader = ImageReader.newInstance(size.getWidth() / 16, size.getHeight() / 16,
                        ImageFormat.YUV_420_888, 2);
                imageReader.setOnImageAvailableListener(onImageAvailableListener, cameraHandler);

                // Break once the desired camera is found
                break;
            }
        } catch (CameraAccessException e) {
            throw new Exception("Camera access failed.", e);
        }

    }

    private void openCamera() throws Exception {

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            return;
        }

        try {
            if (!cameraLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Camera lock acquire timeout.");
            }
            cameraManager.openCamera(cameraId, cameraStateCallback, cameraHandler);

        } catch (InterruptedException e) {
            throw new RuntimeException("Camera lock interrupted.", e);
        } catch (CameraAccessException e) {
            throw new Exception("Camera access failed.", e);
        }

    }

    private void closeCamera() {

        try {
            cameraLock.acquire();

            if (captureSession != null) {
                captureSession.close();
                captureSession = null;
            }

            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }

            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }

            if (surface != null)
                surface.release();

        } catch (InterruptedException e) {
            throw new RuntimeException("Camera lock interrupted.");
        } finally {
            cameraLock.release();
        }
    }

    private void createCaptureSession() throws CameraAccessException {

        final SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(size.getWidth(), size.getHeight());
        surface = new Surface(surfaceTexture);

        cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()),
                captureSessionStateCallback, cameraHandler);
    }

    private void startHandler() {
        cameraHandlerThread = new HandlerThread(CAMERA_HANDLER_THREAD_NAME);
        cameraHandlerThread.start();
        cameraHandler = new Handler(cameraHandlerThread.getLooper());
    }

    private void stopHandler() {
        if (cameraHandlerThread != null) {
            cameraHandlerThread.quitSafely();
            cameraHandlerThread = null;
            cameraHandler = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case CAMERA_PERMISSION_REQUEST:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    try {
                        openCamera();
                    } catch (Exception e) {
                        Alert.display(MainActivity.this, "Error", e.getMessage());
                    }
                } else {
                    Alert.display(MainActivity.this, "Alert",
                            "This app needs camera permissions in order to work properly.");
                }
                break;
        }
    }
}
