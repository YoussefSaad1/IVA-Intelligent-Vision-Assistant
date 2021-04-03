package com.Starter.IVA;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.ImageButton;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;
    private static final int FLASH_OFF = 2;
    private static final int FLASH_ON = 1;
    private static final int FLASH_AUTO = 0;
    private static final int OPTICAL_CHARACTER_RECOGNITION = 1;
    private static final int CURRENCY_RECOGNITION = 2;
    private static SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    public SharedPreferences sharedPreferences;
    public File tempImage;
    private int mSensorOrientation;
    private Boolean connectCamera = false;
    private ImageButton opticalCharacterRecognition;
    private ImageButton currencyRecognition;
    private ImageButton flash;
    private ImageButton help;
    private int flashStatus;
    private int serviceCode = 0;
    private boolean cameraPermissionGranted = false;
    private CameraSelector cameraSelector;
    private Preview preview;
    private ImageCapture imageCapture;
    private Camera camera;
    private ExecutorService cameraExecutor;
    private PreviewView previewView;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ProcessCameraProvider cameraProvider;
    private HandlerThread backgroundHandlerThread;
    private Handler backgroundHandler;
    private HandlerThread audioHandlerThread;
    private Handler audioHandler;
    private byte[] bytes;
    private CameraInfo cameraInfo;
    private CameraControl cameraControl;
    private ScaleGestureDetector.SimpleOnScaleGestureListener simpleOnScaleGestureListener = new ScaleGestureDetector.SimpleOnScaleGestureListener() {
        @Override
        public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
            float currentZoomRatio = cameraInfo.getZoomState().getValue().getZoomRatio();
            float delta = scaleGestureDetector.getScaleFactor();
            cameraControl.setZoomRatio(currentZoomRatio * delta);
            return true;

        }
    };
    private ScaleGestureDetector scaleGestureDetector;
    private SharedPreferences.Editor editor;
    private AudioManager audioManager;
    private OrientationEventListener orientationEventListener;
    private AudioAttributes audioAttributes;
    private AudioFocusRequest audioFocusRequest;
    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;
    private AccessibilityManager accessibilityManager;
    private final UtteranceProgressListener utteranceProgressListener = new UtteranceProgressListener() {

        @Override
        public void onStart(String utteranceId) {
            accessibilityManager.interrupt();
        }

        @Override
        public void onDone(String utteranceId) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            } else {
                audioManager.abandonAudioFocus(audioFocusChangeListener);
            }

        }

        @Override
        public void onError(String utteranceId) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            } else {
                audioManager.abandonAudioFocus(audioFocusChangeListener);
            }

        }

        @Override
        public void onStop(String utteranceId, boolean interrupted) {
            super.onStop(utteranceId, interrupted);
        }
    };
    private int rotation;
    private int res;
    private int imageRotation;
    private TextToSpeech tts;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        accessibilityManager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        audioAttributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        setContentView(R.layout.activity_main);
        sharedPreferences = getSharedPreferences("Shared_Pref", MODE_PRIVATE);
        flashStatus = sharedPreferences.getInt("flash_status", 0);
        String flashDescription = sharedPreferences.getString("flash_description", getString(R.string.auto_flash));
        editor = sharedPreferences.edit();
        scaleGestureDetector = new ScaleGestureDetector(this, simpleOnScaleGestureListener);
        previewView = findViewById(R.id.previewView);
        opticalCharacterRecognition = findViewById(R.id.OCR);
        currencyRecognition = findViewById(R.id.currency);
        flash = findViewById(R.id.flash);
        help = findViewById(R.id.help);
        setUpFlash();
        serviceLongClick();
        serviceClick();
        if (flashStatus == FLASH_AUTO) {
            flash.setImageResource(R.mipmap.auto_flash);
        } else if (flashStatus == FLASH_ON) {
            flash.setImageResource(R.mipmap.flash_on);
        } else if (flashStatus == FLASH_OFF) {
            flash.setImageResource(R.mipmap.flash_off);
        } else {
            flash.setImageResource(R.mipmap.auto_flash);
        }
        flash.setContentDescription(flashDescription);


        cameraExecutor = Executors.newSingleThreadExecutor();
        if (previewView != null) {
            previewView.setOnTouchListener((v, event) -> {
                scaleGestureDetector.onTouchEvent(event);
                return true;
            });
        }
        help.setOnClickListener(v -> setUpAudioFocus(getString(R.string.help_content)));
    }

    private void setUpFlash() {
        flash.setOnClickListener(v -> {
            if (flashStatus == FLASH_AUTO) {
                flash.setImageResource(R.mipmap.flash_on);
                flash.setContentDescription(getString(R.string.flash_on));
                flashStatus = FLASH_ON;
                editor.putInt("flash_status", flashStatus);
                editor.putString("flash_description", getString(R.string.flash_on));
                editor.commit();
                imageCapture.setFlashMode(flashStatus);
            } else if (flashStatus == FLASH_ON) {
                flash.setImageResource(R.mipmap.flash_off);
                flash.setContentDescription(getString(R.string.flash_off));
                flashStatus = FLASH_OFF;
                editor.putInt("flash_status", flashStatus);
                editor.putString("flash_description", getString(R.string.flash_off));
                editor.commit();
                imageCapture.setFlashMode(ImageCapture.FLASH_MODE_OFF);
            } else if (flashStatus == FLASH_OFF) {
                flash.setImageResource(R.mipmap.auto_flash);
                flash.setContentDescription(getString(R.string.auto_flash));
                flashStatus = FLASH_AUTO;

                editor.putInt("flash_status", flashStatus);
                editor.putString("flash_description", getString(R.string.auto_flash));
                editor.commit();
                imageCapture.setFlashMode(flashStatus);
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onResume() {
        super.onResume();
        connectCamera();
        if (cameraPermissionGranted) {
            startCamera();
            boolean cameraStart = true;
        }
        startHandlerThread();
        startAudioHandlerThread();
        if (previewView != null) {
            previewView.setOnTouchListener((v, event) -> {
                scaleGestureDetector.onTouchEvent(event);
                return true;
            });
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        stopHandlerThread();
        stopAudioHandlerThread();
        if (tts != null) {
            if (tts.isSpeaking()) {
                tts.stop();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            } else {
                audioManager.abandonAudioFocus(audioFocusChangeListener);
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopHandlerThread();
        stopAudioHandlerThread();
        if (tts != null) {
            if (tts.isSpeaking()) {
                tts.stop();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            } else {
                audioManager.abandonAudioFocus(audioFocusChangeListener);
            }
        }
    }

    private void startHandlerThread() {
        backgroundHandlerThread = new HandlerThread("CameraX");
        backgroundHandlerThread.start();
        backgroundHandler = new Handler(backgroundHandlerThread.getLooper());

    }

    private void stopHandlerThread() {
        if (backgroundHandlerThread != null) {
            backgroundHandlerThread.quitSafely();
        }
        try {
            //join makes sure that the thread is terminated before excuting the following code line
            if (backgroundHandlerThread != null) {
                backgroundHandlerThread.join();
            }
            backgroundHandlerThread = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startAudioHandlerThread() {
        audioHandlerThread = new HandlerThread("Audio");
        audioHandlerThread.start();
        audioHandler = new Handler(audioHandlerThread.getLooper());

    }

    private void stopAudioHandlerThread() {
        if (audioHandlerThread != null) {
            audioHandlerThread.quitSafely();
        }
        try {
            //join makes sure that the thread is terminated before excuting the following code line
            if (audioHandlerThread != null) {
                audioHandlerThread.join();
            }
            audioHandlerThread = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    //checks the response variable
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION_RESULT) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                cameraPermissionGranted = true;
                startCamera();
            }

        }
    }

    private void connectCamera() {
        //first thing that we need to connect to the camera is to create a camera manager instance
        //connect to the camera
        //checks if marshmallow or later to add permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED) {
                //start camera
                cameraPermissionGranted = true;

            } else {
                if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    if (connectCamera) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        AlertDialog alertDialog = builder.create();
                        alertDialog.setTitle("طلب إذن");
                        alertDialog.setMessage("هذا الإذن ضروري لالتقاط الصور");
                        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "حسنًا", (dialog, which) -> ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION_RESULT));
                        alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "تجاهل", (dialog, which) -> alertDialog.dismiss());
                        alertDialog.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                        alertDialog.show();

                    } else {
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION_RESULT);
                        connectCamera = true;
                    }
                } else {
                    //google it
                    //Request code , the response needs a variable
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION_RESULT);
                    connectCamera = true;
                    connectCamera = true;
                }
            }
        } else {
            cameraPermissionGranted = true;
        }

    }

    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    @SuppressLint({"RestrictedApi", "UnsafeExperimentalUsageError"})
    private void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                preview = new Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_16_9).build();

                rotation = preview.getTargetRotation();
                if (previewView != null) {
                    imageCapture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .setTargetResolution(new Size(400, 400)).setFlashMode(flashStatus).build();
                }
                orientationEventListener = new OrientationEventListener(this) {
                    @Override
                    public void onOrientationChanged(int orientation) {


                        // Monitors orientation values to determine the target rotation value
                        if (orientation >= 45 && orientation < 135) {
                            rotation = Surface.ROTATION_270;
                        } else if (orientation >= 135 && orientation < 225) {
                            rotation = Surface.ROTATION_180;
                        } else if (orientation >= 225 && orientation < 315) {
                            rotation = Surface.ROTATION_90;
                        } else {
                            rotation = Surface.ROTATION_0;
                        }
                        res = getOrientation(rotation);
                        if (res == 0) {
                            imageCapture.setTargetRotation(Surface.ROTATION_0);
                        } else if (res == 90) {
                            imageCapture.setTargetRotation(Surface.ROTATION_90);
                        } else if (res == 180) {
                            imageCapture.setTargetRotation(Surface.ROTATION_180);
                        } else if (res == 270) {
                            imageCapture.setTargetRotation(Surface.ROTATION_270);
                        }
                    }
                };
                orientationEventListener.enable();
                ////////////////////

                cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();

                preview.setSurfaceProvider(previewView.createSurfaceProvider());
                // unind use cases before rebinding
                cameraProvider.unbindAll();
                //bind use cases to camera
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture, preview);
                preview.setSurfaceProvider(previewView.createSurfaceProvider());
                cameraInfo = camera.getCameraInfo();
                cameraControl = camera.getCameraControl();
                mSensorOrientation = cameraInfo.getSensorRotationDegrees();

            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));


    }

    private void takePhoto() {
        if (imageCapture != null) {
            imageCapture.takePicture(cameraExecutor, new ImageCapture.OnImageCapturedCallback() {
                        @Override
                        public void onCaptureSuccess(@NonNull ImageProxy image) {
                            imageRotation = image.getImageInfo().getRotationDegrees();
                            backgroundHandler.post(new imageConverter(image));
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            super.onError(exception);
                        }

                    }
            );
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);

        }
        return true;
    }

    private void serviceClick() {
        opticalCharacterRecognition.setOnClickListener(v -> {
            takePhoto();
            serviceCode = OPTICAL_CHARACTER_RECOGNITION;
        });

        currencyRecognition.setOnClickListener(v -> {
            takePhoto();
            serviceCode = CURRENCY_RECOGNITION;
        });


    }

    private String saveImage(byte[] bytes) {
        try {
            //google it
            tempImage = File.createTempFile("IVA", "JPEG", getCacheDir());
            tempImage.deleteOnExit();
            FileOutputStream fos = new FileOutputStream(tempImage);
            fos.write(bytes);
            fos.close();

        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return tempImage.getPath();
    }

    private void serviceLongClick() {
        flash.setOnLongClickListener(v -> {
            switch (flashStatus) {
                case FLASH_AUTO: {
                    setUpAudioFocus(getString(R.string.auto_flash));
                    break;
                }
                case FLASH_ON: {
                    setUpAudioFocus(getString(R.string.flash_on));
                    break;
                }
                case FLASH_OFF: {
                    setUpAudioFocus(getString(R.string.flash_off));
                    break;
                }
            }
            return true;
        });

        opticalCharacterRecognition.setOnLongClickListener(v -> {
            setUpAudioFocus(getString(R.string.ocr));
            return true;
        });
        currencyRecognition.setOnLongClickListener(v -> {
            setUpAudioFocus(getString(R.string.currencyDetection));
            return true;
        });
        help.setOnLongClickListener(v -> {
            setUpAudioFocus(getString(R.string.help));
            return true;
        });

    }

    private void setUpAudioFocus(String serviceResult) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusChangeListener = focusChange -> {
                    if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {

                    } else {

                        audioManager.abandonAudioFocusRequest(audioFocusRequest);
                    }

                };
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(audioAttributes).setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(audioFocusChangeListener, audioHandler).build();

                int result = audioManager.requestAudioFocus(audioFocusRequest);
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    tts = new TextToSpeech(getApplicationContext(), status -> {
                        if (status != TextToSpeech.ERROR) {
                            tts.setLanguage(Locale.getDefault());
                            tts.setOnUtteranceProgressListener(utteranceProgressListener);
                            tts.setAudioAttributes(audioAttributes);
                            tts.speak(serviceResult, TextToSpeech.QUEUE_FLUSH, null, "utteranceId");
                        }
                    });

                } else if (result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
                    tts = new TextToSpeech(getApplicationContext(), status -> {
                        if (status != TextToSpeech.ERROR) {
                            tts.setLanguage(Locale.getDefault());
                            tts.setOnUtteranceProgressListener(utteranceProgressListener);
                            tts.setAudioAttributes(audioAttributes);
                            tts.speak(serviceResult, TextToSpeech.QUEUE_FLUSH, null, "utteranceId");
                        }
                    });
                }
            } else {
                audioFocusChangeListener =
                        focusChange -> {

                            if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {

                            } else {
                                // Permanent loss of audio focus
                                // Pause playback immediately
                                audioManager.abandonAudioFocus(audioFocusChangeListener);

                            }
                        };
                int result = audioManager.requestAudioFocus(audioFocusChangeListener,
                        // Use the music stream.
                        AudioManager.STREAM_MUSIC,
                        // Request permanent focus.
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);

                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED || result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
                    // Start playback
                    tts = new TextToSpeech(getApplicationContext(), status -> {
                        if (status != TextToSpeech.ERROR) {
                            tts.setLanguage(Locale.getDefault());
                            tts.setOnUtteranceProgressListener(utteranceProgressListener);
                            tts.setAudioAttributes(audioAttributes);
                            tts.speak(serviceResult, TextToSpeech.QUEUE_FLUSH, null, "utteranceId");
                        }
                    });
                }
            }
        }
    }

    private class imageConverter implements Runnable {
        private final ImageProxy mImage;

        private imageConverter(ImageProxy image) {
            mImage = image;

        }


        @Override
        public void run() {
            if (mImage != null) {

                if (mImage.getFormat() == ImageFormat.JPEG) {
                    ByteBuffer byteBuffer = mImage.getPlanes()[0].getBuffer();
                    bytes = new byte[byteBuffer.remaining()];
                    byteBuffer.get(bytes);
                    mImage.close();
                }
                if (bytes != null) {
                    Intent intent = new Intent(MainActivity.this, ImageActivity.class);
                    intent.putExtra("rotationCode", rotation);
                    intent.putExtra("imageRotation", imageRotation);
                    intent.putExtra("imageBytes", saveImage(bytes));
                    intent.putExtra("serviceCode", serviceCode);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                }
            }

        }

    }


}

