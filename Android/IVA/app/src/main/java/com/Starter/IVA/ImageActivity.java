package com.Starter.IVA;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.text.FirebaseVisionCloudTextRecognizerOptions;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.tensorflow.lite.support.common.TensorProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.TensorLabel;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;
import org.tensorflow.lite.support.tensorbuffer.TensorBufferFloat;

public class ImageActivity extends AppCompatActivity {
    private static final int OPTICAL_CHARACTER_RECOGNITION = 1;
    private static final int CURRENCY_RECOGNITION = 2;
    private final Interpreter.Options tfliteOptions = new Interpreter.Options();
    boolean firstTime = true;
    boolean replayClicked = false;
    boolean retryClicked = false;
    boolean serviceIndication = false;
    private ImageView imageView;
    private String filePath;
    private int imageRotation;
    private Bitmap scaledBitmap;
    private int serviceCode = 0;
    private HandlerThread backgroundHandlerThread;
    private Handler backgroundHandler;
    private AudioManager audioManager;
    private Bitmap rotatedBitmap;
    private String OCR_result = "";
    private String currency_result = "";
    private TextToSpeech tts = null;
    private AudioAttributes audioAttributes;
    private HandlerThread audioHandlerThread;
    private Handler audioHandler;
    private AccessibilityManager accessibilityManager;
    private ClipboardManager clipboard;
    private ImageButton retry;
    private ImageButton copy;
    private Locale langCode = Locale.getDefault();
    private Locale ocrLangCode = Locale.getDefault();
    private List<String> labels = new ArrayList<String>();
    private AssetFileDescriptor fileDescriptor = null;
    private TensorImage inputImageBuffer;
    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;
    private AudioFocusRequest audioFocusRequest;
    private String serString = "";
    private UtteranceProgressListener utteranceProgressListener = new UtteranceProgressListener() {

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
            firstTime = false;
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
            if (firstTime && !serString.equals(getString(R.string.start_ocr))
                    && !serString.equals(getString(R.string.start_currency))
                    && !serString.equals(getString(R.string.sorry_internet))) {
                if (OCR_result != null) {
                    if (getLifecycle().getCurrentState() == Lifecycle.State.RESUMED)
                        setUpAudioFocus(OCR_result, ocrLangCode);
                } else if (currency_result != null) {
                    if (getLifecycle().getCurrentState() == Lifecycle.State.RESUMED)
                        setUpAudioFocus(currency_result, langCode);
                }
            } else if (serviceIndication) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioManager.abandonAudioFocusRequest(audioFocusRequest);
                } else {
                    audioManager.abandonAudioFocus(audioFocusChangeListener);
                }

            } else if (replayClicked || retryClicked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioManager.abandonAudioFocusRequest(audioFocusRequest);
                } else {
                    audioManager.abandonAudioFocus(audioFocusChangeListener);
                }
                replayClicked = false;

            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.image_activity);
        clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        accessibilityManager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        audioAttributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        imageView = findViewById(R.id.imageView);
        ImageButton replay = findViewById(R.id.replay);
        ImageButton backButton = findViewById(R.id.backButton);
        retry = findViewById(R.id.retry);
        copy = findViewById(R.id.copy);
        filePath = getIntent().getStringExtra("imageBytes");
        serviceCode = getIntent().getIntExtra("serviceCode", 0);
        int rotation = getIntent().getIntExtra("rotationCode", 0);
        imageRotation = getIntent().getIntExtra("imageRotation", 0);
        startHandlerThread();
        startAudioHandlerThread();
        if (serviceCode == OPTICAL_CHARACTER_RECOGNITION) {
            serviceIndication = true;
            setUpAudioFocus(getString(R.string.start_ocr), langCode);
        } else if (serviceCode == CURRENCY_RECOGNITION) {
            serviceIndication = true;
            setUpAudioFocus(getString(R.string.start_currency), langCode);
        }
        backgroundHandler.post(this::setUpService);
        backButton.setOnClickListener(v -> goBack());
        replay.setOnClickListener(v -> {
            replayClicked = true;
            if (serviceCode == OPTICAL_CHARACTER_RECOGNITION) {
                if (!OCR_result.equals("")) {
                    setUpAudioFocus(OCR_result, ocrLangCode);

                } else if (OCR_result.equals(getString(R.string.please_ocr))) {
                    setUpAudioFocus(getString(R.string.please_ocr), langCode);
                } else if (OCR_result.equals(getString(R.string.sorry_internet))) {
                    setUpAudioFocus(getString(R.string.sorry_internet), langCode);
                }

            } else {
                if (!currency_result.equals("")) {
                    setUpAudioFocus(currency_result, langCode);
                }
            }
        });
        copy.setOnClickListener(v -> {
            ClipData clip = ClipData.newPlainText("ocr", OCR_result);
            clipboard.setPrimaryClip(clip);
        });
        replay.setOnLongClickListener(v -> {
            if (getLifecycle().getCurrentState() == Lifecycle.State.RESUMED)
                setUpAudioFocus(getString(R.string.replay), langCode);
            return true;
        });
        backButton.setOnLongClickListener(v -> {
            if (getLifecycle().getCurrentState() == Lifecycle.State.RESUMED)
                setUpAudioFocus(getString(R.string.back), langCode);
            return true;
        });
        copy.setOnLongClickListener(v -> {
            if (getLifecycle().getCurrentState() == Lifecycle.State.RESUMED)
                setUpAudioFocus(getString(R.string.copy), langCode);
            return true;
        });

    }

    private void setRetry() {
        retryClicked = true;
        retry.setVisibility(View.VISIBLE);
        retry.setOnClickListener(v -> {
            setOpticalCharacterRecognition();
            retry.setVisibility(View.INVISIBLE);
        });
        retry.setOnLongClickListener(v -> {
            setUpAudioFocus(getString(R.string.retry), langCode);
            return true;
        });
    }

    private void setUpService() {
        Bitmap imageBitmap = BitmapFactory.decodeFile(filePath);
        rotatedBitmap = rotateBitmap(imageBitmap, imageRotation);
        runOnUiThread(() -> imageView.setImageBitmap(rotatedBitmap));
        scaledBitmap = Bitmap.createScaledBitmap(rotatedBitmap
                , 750, 1000, true);
        String base64Image = imageToString(scaledBitmap);
        if (base64Image != null) {

            chooseService();
        }
    }

    private void goBack() {
        backgroundHandlerThread.quit();
        Intent mainIntent = new Intent(ImageActivity.this, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(mainIntent);
    }

    private Bitmap rotateBitmap(Bitmap source, int imageRotation) {
        Matrix matrix = new Matrix();
        matrix.postRotate(imageRotation);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private void startHandlerThread() {
        backgroundHandlerThread = new HandlerThread("CameraX");
        backgroundHandlerThread.start();
        backgroundHandler = new Handler(backgroundHandlerThread.getLooper());

    }

    private void stopHandlerThread() {
        if (backgroundHandlerThread != null) {
            backgroundHandlerThread.quitSafely();
            try {
                //join makes sure that the thread is terminated before excuting the following code line
                backgroundHandlerThread.join();
                backgroundHandlerThread = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    private void chooseService() {


        switch (serviceCode) {
            case OPTICAL_CHARACTER_RECOGNITION: {
                setOpticalCharacterRecognition();
                break;
            }
            case CURRENCY_RECOGNITION: {
                setCurrencyRecognition();
                break;
            }

        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startAudioHandlerThread();
        startHandlerThread();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

    }

    private void startAudioHandlerThread() {
        audioHandlerThread = new HandlerThread("Audio");
        audioHandlerThread.start();
        audioHandler = new Handler(audioHandlerThread.getLooper());

    }

    private void stopAudioHandlerThread() {
        if (audioHandlerThread != null) {
            audioHandlerThread.quitSafely();
            try {
                //join makes sure that the thread is terminated before excuting the following code line
                audioHandlerThread.join();
                audioHandlerThread = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void setOpticalCharacterRecognition() {

        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(scaledBitmap);
        FirebaseVisionCloudTextRecognizerOptions options = new FirebaseVisionCloudTextRecognizerOptions.Builder()
                .setLanguageHints(Arrays.asList("ar","en","fr"))
                .build();
        FirebaseVisionTextRecognizer detector = FirebaseVision.getInstance().getCloudTextRecognizer(options);
        Task<FirebaseVisionText> result =
                detector.processImage(image)
                        .addOnSuccessListener((FirebaseVisionText firebaseVisionText) -> {
                            // Task completed successfully
                            // ...

                            OCR_result = "";
                            for (FirebaseVisionText.TextBlock block : firebaseVisionText.getTextBlocks()) {
                                Rect boundingBox = block.getBoundingBox();
                                Point[] cornerPoints = block.getCornerPoints();
                                OCR_result = OCR_result + block.getText();
                                OCR_result += '\n';
                            }
                            copy.setVisibility(View.VISIBLE);

                            if (!OCR_result.equals("")) {
                                LanguageIdentifier languageIdentifier =
                                        LanguageIdentification.getClient();
                                languageIdentifier.identifyLanguage(OCR_result)
                                        .addOnSuccessListener(
                                                new OnSuccessListener<String>() {
                                                    @Override
                                                    public void onSuccess(String languageCode) {
                                                        if (languageCode.equals("und")) {

                                                        } else {
                                                            ocrLangCode = Locale.forLanguageTag(languageCode);
                                                            setUpAudioFocus(OCR_result, ocrLangCode);
                                                        }
                                                    }
                                                })
                                        .addOnFailureListener(
                                                new OnFailureListener() {
                                                    @Override
                                                    public void onFailure(Exception e) {
                                                        // Model couldn’t be loaded or other internal error.
                                                        // ...
                                                        setUpAudioFocus(OCR_result, ocrLangCode);

                                                    }
                                                });

                            } else {
                                setUpAudioFocus(getString(R.string.please_ocr), langCode);
                                OCR_result = getString(R.string.please_ocr);
                            }
                        })
                        .addOnFailureListener(
                                (Exception e) -> {
                                    OCR_result = getString(R.string.sorry_internet);
                                    setUpAudioFocus(OCR_result, langCode);
                                    retryClicked = false;
                                    setRetry();
                                });

    }

    private MappedByteBuffer loadModelFile() {
        try {
            //TODO: model.tflite is up to you to add
            fileDescriptor = getAssets().openFd("model.tflite");
        } catch (IOException e) {
            e.printStackTrace();
        }

        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        try {
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    ///////////////////////////////////////////////////////
    private void setCurrencyRecognition() {

        Interpreter tflite = new Interpreter(loadModelFile());
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(scaledBitmap, 400, 400, false);

        int imageTensorIndex = 0;
        int[] imageShape = tflite.getInputTensor(imageTensorIndex).shape(); // {1, height, width, 3}
        int imageSizeY = imageShape[1];
        int imageSizeX = imageShape[2];
        float[] inputImage = new float[480000];

        int cnt = 0;
        for (int i = 0; i < 400; i++) {
            for (int j = 0; j < 400; j++) {
                int rgb = resizedBitmap.getPixel(i, j);
                float red = rgb >> 16 & 0x0ff;
                float green = rgb >> 8 & 0x0ff;
                float blue = rgb & 0x0ff;

                red /= 255.0;
                green /= 255.0;
                blue /= 255.0;

                inputImage[cnt++] = red;
                inputImage[cnt++] = green;
                inputImage[cnt++] = blue;
            }
        }

        int probabilityTensorIndex = 0;
        int[] probabilityShape =
                tflite.getOutputTensor(probabilityTensorIndex).shape(); // {1, NUM_CLASSES}
        DataType probabilityDataType = tflite.getOutputTensor(probabilityTensorIndex).dataType();


        // Creates the output tensor and its processor.
        TensorBuffer outputProbabilityBuffer = TensorBuffer.createFixedSize(probabilityShape, probabilityDataType);

        TensorProcessor probabilityProcessor = new TensorProcessor.Builder().build();

        TensorBufferFloat tensorBufferFloat = (TensorBufferFloat) TensorBufferFloat.createFixedSize(imageShape, DataType.FLOAT32);
        tensorBufferFloat.loadArray(inputImage);

        labels.add("100F");
        labels.add("100B");
        labels.add("10F");
        labels.add("10B");
        labels.add("200F");
        labels.add("200B");
        labels.add("20F");
        labels.add("20B");
        labels.add("50F");
        labels.add("50B");
        labels.add("5F");
        labels.add("5B");

        tflite.run(tensorBufferFloat.getBuffer(), outputProbabilityBuffer.getBuffer().rewind());

        Map<String, Float> labeledProbability =
                new TensorLabel(labels, probabilityProcessor.process(outputProbabilityBuffer))
                        .getMapWithFloatValue();

        Map.Entry<String, Float> maxEntry = null;
        for (Map.Entry<String, Float> entry : labeledProbability.entrySet()) {
            if (maxEntry == null || entry.getValue()
                    .compareTo(maxEntry.getValue()) > 0) {
                maxEntry = entry;
            }
        }


        if (maxEntry.getValue() < 0.4) {
            currency_result = getString(R.string.currency_retake);
            setUpAudioFocus(currency_result, langCode);

        } else {
            if (maxEntry.getKey().equals("5F") || maxEntry.getKey().equals("5B"))
            //speakService(5);
            {
                currency_result = getString(R.string.five);
                setUpAudioFocus(currency_result, langCode);
            } else if (maxEntry.getKey().equals("10F") || maxEntry.getKey().equals("10B")) {
                currency_result = getString(R.string.ten);
                setUpAudioFocus(currency_result, langCode);

            } else if (maxEntry.getKey().equals("20F") || maxEntry.getKey().equals("20B")) {
                currency_result = getString(R.string.twenty);
                setUpAudioFocus(currency_result, langCode);
            } else if (maxEntry.getKey().equals("50F") || maxEntry.getKey().equals("50B")) {
                currency_result = getString(R.string.fifty);
                setUpAudioFocus(currency_result, langCode);
            } else if (maxEntry.getKey().equals("100F") || maxEntry.getKey().equals("100B")) {
                currency_result = getString(R.string.hundred);
                setUpAudioFocus(currency_result, langCode);
            } else if (maxEntry.getKey().equals("200F") || maxEntry.getKey().equals("200B")) {
                currency_result = getString(R.string.two_hundred);
                setUpAudioFocus(currency_result, langCode);
            }
        }

    }

    private String imageToString(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
        byte[] imgBytes = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(imgBytes, Base64.DEFAULT);

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

    @Override
    protected void onDestroy() {
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
        super.onDestroy();

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
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            goBack();
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);

        }
        return true;
    }

    private void setUpAudioFocus(String serviceResult, Locale loc) {

        serString = serviceResult;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusChangeListener = focusChange -> {
                    if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                        if (!serviceResult.equals(getString(R.string.start_ocr))
                                && !serviceResult.equals(getString(R.string.start_currency))
                                && !serviceResult.equals(getString(R.string.sorry_internet))) {
                            tts = new TextToSpeech(getApplicationContext(), status -> {
                                if (status != TextToSpeech.ERROR) {
                                    tts.setLanguage(loc);
                                    tts.setOnUtteranceProgressListener(utteranceProgressListener);
                                    tts.setAudioAttributes(audioAttributes);
                                    tts.speak(serviceResult, TextToSpeech.QUEUE_FLUSH, null, "utteranceId");
                                }
                            });
                        }
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
                            tts.setLanguage(loc);
                            tts.setOnUtteranceProgressListener(utteranceProgressListener);
                            tts.setAudioAttributes(audioAttributes);
                            tts.speak(serviceResult, TextToSpeech.QUEUE_FLUSH, null, "utteranceId");
                        }
                    });

                } else if (result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
                    tts = new TextToSpeech(getApplicationContext(), status -> {
                        if (status != TextToSpeech.ERROR) {
                            tts.setLanguage(loc);
                            tts.setOnUtteranceProgressListener(utteranceProgressListener);
                            tts.setAudioAttributes(audioAttributes);
                            tts.speak(serviceResult, TextToSpeech.QUEUE_FLUSH, null, "utteranceId");
                        }
                    });
                } else if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {

                }
            } else {
                audioFocusChangeListener =
                        focusChange -> {
                            if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                                if (!serviceResult.equals(getString(R.string.start_ocr))
                                        && !serviceResult.equals(getString(R.string.start_currency))
                                        && !serviceResult.equals(getString(R.string.sorry_internet))) {
                                    tts = new TextToSpeech(getApplicationContext(), status -> {
                                        if (status != TextToSpeech.ERROR) {
                                            tts.setLanguage(loc);
                                            tts.setOnUtteranceProgressListener(utteranceProgressListener);
                                            tts.setAudioAttributes(audioAttributes);
                                            tts.speak(serviceResult, TextToSpeech.QUEUE_FLUSH, null, "utteranceId");
                                        }
                                    });
                                }
                            } else {
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
                            tts.setLanguage(loc);
                            tts.setOnUtteranceProgressListener(utteranceProgressListener);
                            tts.setAudioAttributes(audioAttributes);
                            tts.speak(serviceResult, TextToSpeech.QUEUE_FLUSH, null, "utteranceId");
                        }
                    });
                }
            }
        }
    }
}
