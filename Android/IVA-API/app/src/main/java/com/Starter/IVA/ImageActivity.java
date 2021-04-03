package com.Starter.IVA;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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
import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.util.Locale;


public class ImageActivity extends AppCompatActivity {
    private static final int OPTICAL_CHARACTER_RECOGNITION = 1;
    private static final int CURRENCY_RECOGNITION = 2;
    private ImageView imageView;
    private String filePath;
    private String base64Image;
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
    boolean firstTime = true;
    boolean replayClicked = false;
    boolean retryClicked = false;
    boolean serviceIndication = false;
    private ImageButton retry;
    private DiskBasedCache cache;
    private BasicNetwork network;
    private RequestQueue requestQueue;
    private JsonObjectRequest objectRequest;
    private String Url = "your api link goes here";
    private int retryCount = 0;
    private Boolean isConnected = false;
    private ConnectivityManager connectivityManager;
    private NetworkInfo networkInfo;
    private MediaPlayer mainMediaPlayer;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.image_activity);
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnectedOrConnecting())
        {
            isConnected = true;
        }
        else {isConnected = false;}
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        accessibilityManager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        audioAttributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        imageView = findViewById(R.id.imageView);
        ImageButton replay = findViewById(R.id.replay);
        ImageButton backButton = findViewById(R.id.backButton);
        retry = findViewById(R.id.retry);
        filePath = getIntent().getStringExtra("imageBytes");
        serviceCode = getIntent().getIntExtra("serviceCode", 0);
        int rotation = getIntent().getIntExtra("rotationCode", 0);
        imageRotation = getIntent().getIntExtra("imageRotation", 0);
        startHandlerThread();
        startAudioHandlerThread();
        if (serviceCode == OPTICAL_CHARACTER_RECOGNITION) {
            serviceIndication = true;
            setUpAudioFocusText(getString(R.string.start_ocr));
        } else if (serviceCode == CURRENCY_RECOGNITION) {
            serviceIndication = true;
            setUpAudioFocusText(getString(R.string.start_currency));
        }
        backgroundHandler.post(this::setUpService);
        backButton.setOnClickListener(v -> goBack());
        replay.setOnClickListener(v -> {
            replayClicked = true;
            if (serviceCode == OPTICAL_CHARACTER_RECOGNITION) {
                if (!OCR_result.equals("")) {
                    setUpAudioFocusText(OCR_result);

                } else if (OCR_result.equals(getString(R.string.please_ocr))){
                    setUpAudioFocusText(getString(R.string.please_ocr));
                }else if (OCR_result.equals(getString(R.string.sorry_internet)))
                {
                    setUpAudioFocusText(getString(R.string.sorry_internet));
                }

            } else {
                if (!currency_result.equals("")) {
                    setUpAudioFocusText(currency_result);
                }
            }
        });
        replay.setOnLongClickListener(v -> {
            if (getLifecycle().getCurrentState() == Lifecycle.State.RESUMED)
                setUpAudioFocusText(getString(R.string.replay));
            return true;
        });
        backButton.setOnLongClickListener(v -> {
            if (getLifecycle().getCurrentState() == Lifecycle.State.RESUMED)
                setUpAudioFocusText(getString(R.string.back));
            return true;
        });

    }
    private void setRetry (){
        retryClicked = true;
        retry.setVisibility(View.VISIBLE);
        retry.setOnClickListener(v -> {
            setOpticalCharacterRecognition();
            retry.setVisibility(View.INVISIBLE);
        });
        retry.setOnLongClickListener(v -> {
            setUpAudioFocusText(getString(R.string.retry));
            return true;
        });
    }
    private void setUpService() {
        Bitmap imageBitmap = BitmapFactory.decodeFile(filePath);
        rotatedBitmap = rotateBitmap(imageBitmap, imageRotation);
        runOnUiThread(() -> imageView.setImageBitmap(rotatedBitmap));
        scaledBitmap = Bitmap.createScaledBitmap(rotatedBitmap
                , 750, 1000, true);
        base64Image = imageToString(scaledBitmap);
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
        networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnectedOrConnecting()) {
            isConnected = true;
        } else {isConnected = false;}
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

    /////////////////////////////////////////////////////////////////
    private void setOpticalCharacterRecognition() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("image", base64Image);
        } catch (JSONException ignored) {
        }
        cache = new DiskBasedCache(getCacheDir(),1024*1024);
        network = new BasicNetwork(new HurlStack());
        requestQueue = new RequestQueue(cache, network);
        requestQueue.start();
        objectRequest = new JsonObjectRequest(
                Request.Method.POST, Url, jsonObject,
                response -> {


                    setOnRespnse(response);
                }, this::setOnError

        );
        objectRequest.setRetryPolicy(new DefaultRetryPolicy(30000
                ,DefaultRetryPolicy.DEFAULT_MAX_RETRIES,DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        objectRequest.setShouldRetryServerErrors(true);
        requestQueue.add(objectRequest);

    }
    ///////////////////////////////////////////////////////
    private void setCurrencyRecognition(){
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("image", base64Image);
        } catch (JSONException ignored) {
        }
        cache = new DiskBasedCache(getCacheDir(),1024*1024);
        network = new BasicNetwork(new HurlStack());
        requestQueue = new RequestQueue(cache, network);
        requestQueue.start();
        objectRequest = new JsonObjectRequest(
                Request.Method.POST, Url, jsonObject,
                response -> {


                    setOnRespnse(response);
                }, this::setOnError

        );
        objectRequest.setRetryPolicy(new DefaultRetryPolicy(30000
                ,DefaultRetryPolicy.DEFAULT_MAX_RETRIES,DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        objectRequest.setShouldRetryServerErrors(true);
        requestQueue.add(objectRequest);
    }


    private void setOnError(VolleyError error) {

        if (error instanceof NetworkError) {

            if (!isConnected)
            {
                setUpAudioFocusText(getString(R.string.sorry_internet));
                //TODO:determine what action you to take
            }
            else
            { if(retryCount <= 10)
            {chooseService();
                retryCount = retryCount + 1;}
            else
            {
                setUpAudioFocusText(getString(R.string.sorry_internet));
                //TODO:determine what action you to take
            }
            }

        }
        /////////////////////////////////////
        else if (error instanceof ServerError) {
            setUpAudioFocusText(getString(R.string.server_error));
            //TODO:determine what action you to take

        }
        ////////////////////////////////////////////////
        else if (error instanceof AuthFailureError) {
            setUpAudioFocusText(getString(R.string.sorry_internet));
            //TODO:determine what action you to take
        }
        /////////////////////////////////////////
        else if (error instanceof ParseError) {
            setUpAudioFocusText(getString(R.string.sorry_internet));
        }

        else if (error instanceof NoConnectionError) {
            setUpAudioFocusText(getString(R.string.sorry_internet));
            //TODO:determine what action you to take
        }
        ////////////////////////////////////
        else if (error instanceof TimeoutError) {
            setUpAudioFocusText(getString(R.string.timeout));
            //TODO:determine what action you to take
        }
    }

    private void setOnRespnse(JSONObject response) {
        try {
            String text = response.getString("text");
            setUpAudioFocusText(text);
            // if you want to play encoded  base64 audio
            /*String sound = response.getString("sound");
            String audio = sound.replaceFirst("b'","");
            playAudio(audio);*/
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

  /*  byte[] audioData;
    private FileInputStream fis;
    private void playAudio(String str) throws IOException {
        audioData = Base64.decode(str,Base64.DEFAULT);
        try{
            //google it
            File tempMp3 = File.createTempFile("IVA", "mp3", getCacheDir());
            tempMp3.deleteOnExit();
            FileOutputStream fos = new FileOutputStream(tempMp3);
            fos.write(audioData);
            fos.close();
            fis = new FileInputStream(tempMp3);
            setUpAudioFocusAudio(fis);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }*/


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

    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;
    private AudioFocusRequest audioFocusRequest;
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
                    && !serString.equals(getString(R.string.sorry_internet))){
            if (OCR_result != null)
            {
                if (getLifecycle().getCurrentState() == Lifecycle.State.RESUMED)
                    setUpAudioFocusText(OCR_result);
            }
            else if(currency_result != null)
            {
                if (getLifecycle().getCurrentState() == Lifecycle.State.RESUMED)
                    setUpAudioFocusText(currency_result);
            }
            }
            else if(serviceIndication)
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioManager.abandonAudioFocusRequest(audioFocusRequest);
                } else {
                    audioManager.abandonAudioFocus(audioFocusChangeListener);
                }

            }
            else if (replayClicked || retryClicked)
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioManager.abandonAudioFocusRequest(audioFocusRequest);
                } else {
                    audioManager.abandonAudioFocus(audioFocusChangeListener);
                }
                replayClicked = false;

            }
        }
    };
    //Using text to speech to play the result as voice
    private String serString = "";
    private void setUpAudioFocusText(String serviceResult) {

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
                                    tts.setLanguage(Locale.forLanguageTag("ar"));
                                    tts.setOnUtteranceProgressListener(utteranceProgressListener);
                                    tts.setAudioAttributes(audioAttributes);
                                    tts.speak(serviceResult, TextToSpeech.QUEUE_FLUSH, null, "utteranceId");
                                }
                            });
                        }
                    }
                    else {
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
                            tts.setLanguage(Locale.forLanguageTag("ar"));
                            tts.setOnUtteranceProgressListener(utteranceProgressListener);
                            tts.setAudioAttributes(audioAttributes);
                            tts.speak(serviceResult, TextToSpeech.QUEUE_FLUSH, null, "utteranceId");
                        }
                    });

                } else if (result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
                    tts = new TextToSpeech(getApplicationContext(), status -> {
                        if (status != TextToSpeech.ERROR) {
                            tts.setLanguage(Locale.forLanguageTag("ar"));
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
                                            tts.setLanguage(Locale.forLanguageTag("ar"));
                                            tts.setOnUtteranceProgressListener(utteranceProgressListener);
                                            tts.setAudioAttributes(audioAttributes);
                                            tts.speak(serviceResult, TextToSpeech.QUEUE_FLUSH, null, "utteranceId");
                                        }
                                    });
                                }
                            }
                        else
                        {
                            audioManager.abandonAudioFocus(audioFocusChangeListener);
                        }
                        };
                int result = audioManager.requestAudioFocus(audioFocusChangeListener,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);

                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED || result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
                    // Start playback
                    tts = new TextToSpeech(getApplicationContext(), status -> {
                        if (status != TextToSpeech.ERROR) {
                            tts.setLanguage(Locale.forLanguageTag("ar"));
                            tts.setOnUtteranceProgressListener(utteranceProgressListener);
                            tts.setAudioAttributes(audioAttributes);
                            tts.speak(serviceResult, TextToSpeech.QUEUE_FLUSH, null, "utteranceId");
                        }
                    });
                }
            }
        }
    }

    // Playing audio from file input stream
    /*
    private void setUpAudioFocusAudio(FileInputStream fileInputStream){

        if (mainMediaPlayer != null)
        {
            mainMediaPlayer.pause();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP )
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                audioAttributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN).build();
                audioFocusChangeListener = focusChange -> {

                    if (focusChange == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT){
                        mainMediaPlayer.pause();
                    }
                    else if (focusChange == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK){
                        mainMediaPlayer.pause();
                    }
                    else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK){
                        mainMediaPlayer.pause();
                    }
                    else if (focusChange == AudioManager.AUDIOFOCUS_LOSS){
                        mainMediaPlayer.pause();
                    }

                };
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(audioAttributes).setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(audioFocusChangeListener,audioHandler).build();
                mainMediaPlayer = new MediaPlayer();

                int result = audioManager.requestAudioFocus(audioFocusRequest);
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                {
                    mainMediaPlayer = new MediaPlayer();
                    try {
                        mainMediaPlayer.setDataSource(fileInputStream.getFD());
                        mainMediaPlayer.prepare();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    mainMediaPlayer.start();
                    mainMediaPlayer.setOnCompletionListener(mp -> {
                        audioManager.abandonAudioFocusRequest(audioFocusRequest);
                    });
                }
                else if (result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED)
                {
                    mainMediaPlayer = new MediaPlayer();
                    try {
                        mainMediaPlayer.setDataSource(fileInputStream.getFD());
                        mainMediaPlayer.prepare();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    mainMediaPlayer.start();
                    mainMediaPlayer.setOnCompletionListener(mp -> {
                        audioManager.abandonAudioFocusRequest(audioFocusRequest);
                    });
                }
                else if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                {
                    if (mainMediaPlayer != null)
                    {
                        mainMediaPlayer.pause();

                    }
                }

            }
            else
            {
                audioFocusChangeListener =
                        focusChange -> {
                            if (focusChange == AudioManager.AUDIOFOCUS_LOSS){
                                // Permanent loss of audio focus
                                // Pause playback immediately
                                mainMediaPlayer.pause();
                            }
                            else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                                // Pause playback
                                mainMediaPlayer.pause();
                            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                                // Lower the volume, keep playing
                                mainMediaPlayer.pause();
                            }
                        };
                int result = audioManager.requestAudioFocus(audioFocusChangeListener,
                        // Use the music stream.
                        AudioManager.STREAM_MUSIC,
                        // Request permanent focus.
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);

                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    // Start playback
                    mainMediaPlayer = new MediaPlayer();
                    try {
                        mainMediaPlayer.setDataSource(fileInputStream.getFD());
                        mainMediaPlayer.prepare();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    mainMediaPlayer.start();
                    mainMediaPlayer.setOnCompletionListener(mp -> {
                        audioManager.abandonAudioFocus(audioFocusChangeListener);
                    });
                }
            }
        }
    }*/
    private void speakService(int serviceCode){
        if (serviceCode == OPTICAL_CHARACTER_RECOGNITION) {
            serviceIndication = true;
            setUpAudioFocusText(getString(R.string.start_ocr));
        } else if (serviceCode == CURRENCY_RECOGNITION) {
            serviceIndication = true;
            setUpAudioFocusText(getString(R.string.start_currency));
        }
    }


}
