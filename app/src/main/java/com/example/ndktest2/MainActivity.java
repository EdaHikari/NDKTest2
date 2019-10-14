package com.example.ndktest2;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.opencv.core.Mat;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private Context mContext;
    private TextureView mTextureView;
    private CameraCaptureSession mCameraSession;
    private Image mImage;
    private CameraDevice mCameraDevice;
    private String mCameraId;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private ImageView mPreviewView;
    private ImageReader mImageReader;
    private CameraCaptureSession mCaptureSession;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private int mState = 0;
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_PICTURE_TAKEN = 4;
    private Bitmap mBitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888);
    private boolean textureFlag = false;

    static {
        System.loadLibrary("native-lib");
        System.loadLibrary("opencv_java3");
    }
    public native Mat StereomMatching(Mat mat);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextureView = (TextureView) findViewById(R.id.textureView);
        mPreviewView = (ImageView) findViewById(R.id.imageView);
        Button mButton = (Button)findViewById(R.id.picture_button);
        mButton.setOnClickListener(mOnClicklistener);

        CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);

        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics chars
                        = manager.getCameraCharacteristics(cameraId);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);

                if (facing != null && facing ==
                        CameraCharacteristics.LENS_FACING_BACK) {
                    mCameraId = cameraId;
                }
            }
            if (this.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {            }
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
            mImageReader = ImageReader.newInstance(1080, 1920, ImageFormat.JPEG,5);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
        } catch (CameraAccessException e) {
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mCameraSession != null) {
            try {
                mCameraSession.stopRepeating();
            } catch (CameraAccessException e) {
            }
            mCameraSession.close();
        }

        // カメラデバイスとの切断
        if (mCameraDevice != null) {
            mCameraDevice.close();
        }
    }

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            startPreview();
        }
        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    };

    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            if(mState == STATE_PICTURE_TAKEN)  takepicture();
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            process(result);
        }
    };

    private CameraCaptureSession.StateCallback mCaptureSessionCallback
            = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured( CameraCaptureSession cameraCaptureSession) {
            if (null == mCameraDevice)  return;

            mCaptureSession = cameraCaptureSession;
            statejudge();
        }
        @Override
        public void onConfigureFailed(
                CameraCaptureSession cameraCaptureSession) {}
    };


    View.OnClickListener mOnClicklistener =new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            mState = STATE_PICTURE_TAKEN;
            statejudge();
        }
    };

    private void startPreview() {

        if (null == mCameraDevice)  return;

        try {
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            mPreviewRequestBuilder.addTarget(mImageReader.getSurface());
            mCameraDevice.createCaptureSession(Arrays.asList(mImageReader.getSurface()),mCaptureSessionCallback,null);

        } catch (CameraAccessException e) {
        }
    }

    ////////////////////////撮影まじか//////////////////////////////////////////////////////////////////////////////
    //lookfocusに相当
    public void statejudge(){
        try {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            mState = STATE_PICTURE_TAKEN;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //captureStillPictureに相当
    private void takepicture(){
        try {
            if (null == mCameraDevice) {
                return;
            }

            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session,CaptureRequest request,TotalCaptureResult result) {
                    try {
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
                        mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,mBackgroundHandler);
                        mState = STATE_PREVIEW;
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
            };
            mCaptureSession.abortCaptures();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /////////////////////////////////////////////////////////////////////////////////////////////
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage()));
            if (ImageSaver.bmp == null) {
                System.out.println("bitmapありません");
            }
            else {
                System.out.println("bitmapありました");
                mBitmap = ImageSaver.bmp;

                //UseOpenCV stereo = new UseOpenCV();
                //Bitmap bmp = stereo.startOpenCV(mBitmap);
                mPreviewView.setImageBitmap(mBitmap);
            }
        }

    };

    private static class ImageSaver implements Runnable {

        private final Image mImage;
        public static Bitmap bmp;

        ImageSaver(Image image) {
            mImage = image;
        }
        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            mImage.close();
            bmp= BitmapFactory.decodeByteArray(bytes,0,bytes.length);
        }
    }
}
