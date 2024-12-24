package com.example.huzaifa;

import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.QualitySelector;

import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import jp.co.cyberagent.android.gpuimage.GPUImage;
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSepiaToneFilter;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private VideoCapture<Recorder> videoCapture;
    private Camera camera;
    private ProcessCameraProvider cameraProvider;
    private boolean isFrontCamera = false;
    private boolean isFlashOn = false;
    private GPUImage gpuImage;
    private MediaPlayer mediaPlayer;
    private TextView timerText;
    private Recording recording;

    private static final int PICK_AUDIO_REQUEST = 1;
    private static final int PICK_MEDIA_REQUEST = 2;

    private ExecutorService cameraExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        timerText = findViewById(R.id.timerText);
        Button captureButton = findViewById(R.id.captureButton);
        Button flipButton = findViewById(R.id.flipButton);
        Button flashButton = findViewById(R.id.flashButton);
        Button speedButton = findViewById(R.id.speedButton);
        Button filterButton = findViewById(R.id.filterButton);
        Button timerButton = findViewById(R.id.timerButton);
        Button soundButton = findViewById(R.id.soundButton);
        Button uploadButton = findViewById(R.id.uploadButton);

        cameraExecutor = Executors.newSingleThreadExecutor();

        startCamera();

        captureButton.setOnClickListener(v -> capturePhoto());
        captureButton.setOnLongClickListener(v -> {
            startRecording();
            return true;
        });
        captureButton.setOnClickListener(v -> stopRecording());
        flipButton.setOnClickListener(v -> flipCamera());
        flashButton.setOnClickListener(v -> toggleFlash());
        speedButton.setOnClickListener(v -> setPlaybackSpeed(2.0f));  // Example: 2x speed
        filterButton.setOnClickListener(v -> applyFilter());
        timerButton.setOnClickListener(v -> startTimer(3));  // Set 3 seconds timer
        soundButton.setOnClickListener(v -> pickSound());
        uploadButton.setOnClickListener(v -> pickMedia());
    }

    // 1. Starting Camera (Preview)
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCamera(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCamera(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = isFrontCamera ?
                CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Set up image capture
        imageCapture = new ImageCapture.Builder().build();

        // Set up video capture with Recorder
        Recorder recorder = new Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))  // Use QualitySelector
                .build();

        videoCapture = VideoCapture.withOutput(recorder);
        camera = cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture, videoCapture);
    }

    // 2. Flip Camera
    private void flipCamera() {
        isFrontCamera = !isFrontCamera;
        bindCamera(cameraProvider);
    }

    // 3. Capture Image
    private void capturePhoto() {
        File photoFile = new File(getExternalFilesDir(null), System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                Toast.makeText(MainActivity.this, "Photo Saved: " + photoFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Toast.makeText(MainActivity.this, "Photo capture failed: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 4. Start/Stop Video Recording
    private void startRecording() {
        if (videoCapture == null) {
            Toast.makeText(this, "VideoCapture is not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        // Set up a content values object to configure the output file
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, System.currentTimeMillis() + ".mp4");
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");

        // Use MediaStoreOutputOptions to specify where the video file will be saved
        MediaStoreOutputOptions mediaStoreOutputOptions = new MediaStoreOutputOptions
                .Builder(getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(contentValues)
                .build();

        // Start the video recording with MediaStoreOutputOptions and handle the recording events
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        recording = videoCapture.getOutput()
                .prepareRecording(this, mediaStoreOutputOptions)
                .withAudioEnabled()  // If you want to enable audio recording
                .start(ContextCompat.getMainExecutor(this), videoRecordEvent -> {
                    if (videoRecordEvent instanceof VideoRecordEvent.Start) {
                        Toast.makeText(MainActivity.this, "Recording started", Toast.LENGTH_SHORT).show();
                    } else if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                        VideoRecordEvent.Finalize finalizeEvent = (VideoRecordEvent.Finalize) videoRecordEvent;
                        if (finalizeEvent.hasError()) {
                            Toast.makeText(MainActivity.this, "Recording failed: " + finalizeEvent.getError(), Toast.LENGTH_SHORT).show();
                        } else {
                            Uri savedUri = finalizeEvent.getOutputResults().getOutputUri();
                            Toast.makeText(MainActivity.this, "Video saved: " + savedUri.toString(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void stopRecording() {
        if (recording != null) {
            recording.stop();
            recording = null;
        }
    }

    // 5. Flash Control
    private void toggleFlash() {
        isFlashOn = !isFlashOn;
        imageCapture.setFlashMode(isFlashOn ? ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_OFF);
    }

    // 6. Speed (Playback speed)
    private void setPlaybackSpeed(float speed) {
        if (mediaPlayer != null) {
            mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(speed));
        }
    }

    // 7. Apply Filters
    private void applyFilter() {
        gpuImage = new GPUImage(this);

        // Apply Sepia tone filter
        gpuImage.setFilter(new GPUImageSepiaToneFilter());

        // Set the GLSurfaceView to render the camera preview with applied filter
        GLSurfaceView glSurfaceView = findViewById(R.id.glSurfaceView);
        gpuImage.setGLSurfaceView(glSurfaceView);  // Set the OpenGL surface view to show the filtered camera preview

        // Now set the camera preview frame as the input for GPUImage
        gpuImage.setImage(previewView);  // Set the camera preview frames to be processed
    }

    // 8. Timer
    private void startTimer(int seconds) {
        new CountDownTimer(seconds * 1000, 1000) {
            public void onTick(long millisUntilFinished) {
                timerText.setText("Seconds remaining: " + millisUntilFinished / 1000);
            }

            public void onFinish() {
                capturePhoto();
                timerText.setText("");  // Clear timer after capture
            }
        }.start();
    }

    // 9. Pick Sound (Music Picker)
    private void pickSound() {
        Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_AUDIO_REQUEST);
    }

    // 10. Upload Media (Gallery Picker)
    private void pickMedia() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/* video/*");
        startActivityForResult(intent, PICK_MEDIA_REQUEST);
    }

    // Handling the result from media/sound picker
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            Uri selectedMediaUri = data.getData();
            if (requestCode == PICK_AUDIO_REQUEST) {
                // Handle selected audio
            } else if (requestCode == PICK_MEDIA_REQUEST) {
                // Handle selected media
            }
        }
    }

    private File getOutputDirectory() {
        return new File(getExternalFilesDir(null), "CameraXOutputs");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
