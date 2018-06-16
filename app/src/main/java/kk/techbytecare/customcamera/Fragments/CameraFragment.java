package kk.techbytecare.customcamera.Fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import kk.techbytecare.customcamera.Interface.ICallback;
import kk.techbytecare.customcamera.R;
import kk.techbytecare.customcamera.Remote.IMainActivity;
import kk.techbytecare.customcamera.Utils.Utility;
import kk.techbytecare.customcamera.ui.DrawableImageView;
import kk.techbytecare.customcamera.ui.ScalingTextureView;
import kk.techbytecare.customcamera.ui.VerticalSlideColorPicker;

public class CameraFragment extends Fragment implements View.OnClickListener, View.OnTouchListener,
        VerticalSlideColorPicker.OnColorChangeListener {

    private static final String TAG = "Camera2Fragment";
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    private static final int ICON_FADE_DURATION  = 400;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private int mState = STATE_PREVIEW;

    private static final int STATE_PREVIEW = 0;

    private static final int STATE_WAITING_LOCK = 1;

    private static final int STATE_WAITING_PRECAPTURE = 2;

    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    private static final int STATE_PICTURE_TAKEN = 4;


    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private CameraCaptureSession mCaptureSession;

    private CameraDevice mCameraDevice;

    private String mCameraId;

    private Size mPreviewSize;

    private int mSensorOrientation;

    private ScalingTextureView mTextureView;

    private CaptureRequest.Builder mPreviewRequestBuilder;

    private CaptureRequest mPreviewRequest;

    private HandlerThread mBackgroundThread;

    private Handler mBackgroundHandler;

    private ImageReader mImageReader;

    private int MAX_PREVIEW_WIDTH = 1920;

    private int MAX_PREVIEW_HEIGHT = 1080;

    private int SCREEN_WIDTH = 0;

    private int SCREEN_HEIGHT = 0;

    private float ASPECT_RATIO_ERROR_RANGE = 0.1f;

    private Image mCapturedImage;

    private boolean mIsImageAvailable = false;

    private IMainActivity mIMainActivity;

    private Bitmap mCapturedBitmap;

    private BackgroundImageRotater mBackgroundImageRotater;

    private boolean mIsDrawingEnabled = false;

    boolean mIsCurrentlyDrawing = false;

    //widgets
    private RelativeLayout mStillshotContainer, mFlashContainer, mSwitchOrientationContainer,
            mCaptureBtnContainer, mCloseStillshotContainer, mPenContainer, mUndoContainer, mColorPickerContainer,
            mSaveContainer, mStickerContainer, mTrashContainer;

    private DrawableImageView mStillshotImageView;
    private ImageButton mTrashIcon, mFlashIcon;
    private VerticalSlideColorPicker mVerticalSlideColorPicker;



    public static CameraFragment newInstance(){
        return new CameraFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_camera, container, false);


        return view;
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onViewCreated: created view.");
        view.findViewById(R.id.stillshot).setOnClickListener(this);
        view.findViewById(R.id.switch_orientation).setOnClickListener(this);
        view.findViewById(R.id.init_draw_icon).setOnClickListener(this);
        view.findViewById(R.id.save_stillshot).setOnClickListener(this);
        view.findViewById(R.id.init_sticker_icon).setOnClickListener(this);

        mTrashContainer = view.findViewById(R.id.trash_container);
        mTrashIcon = view.findViewById(R.id.trash);
        mFlashIcon = view.findViewById(R.id.flash_toggle);
        mFlashContainer = view.findViewById(R.id.flash_container);
        mStickerContainer = view.findViewById(R.id.sticker_container);
        mSaveContainer = view.findViewById(R.id.save_container);
        mVerticalSlideColorPicker = view.findViewById(R.id.color_picker);
        mUndoContainer = view.findViewById(R.id.undo_container);
        mColorPickerContainer = view.findViewById(R.id.color_picker_container);
        mPenContainer = view.findViewById(R.id.pen_container);
        mCloseStillshotContainer = view.findViewById(R.id.close_stillshot_view);
        mStillshotImageView = view.findViewById(R.id.stillshot_imageview);
        mStillshotContainer = view.findViewById(R.id.stillshot_container);
        mFlashContainer = view.findViewById(R.id.flash_container);
        mSwitchOrientationContainer = view.findViewById(R.id.switch_orientation_container);
        mCaptureBtnContainer = view.findViewById(R.id.capture_button_container);
        mTextureView = view.findViewById(R.id.texture);

        mFlashIcon.setOnClickListener(this);
        mTrashIcon.setOnClickListener(this);
        mCloseStillshotContainer.setOnClickListener(this);
        mUndoContainer.setOnClickListener(this);

        mStillshotImageView.setOnTouchListener(this);
        mTextureView.setOnTouchListener(this);

        mVerticalSlideColorPicker.setOnColorChangeListener(this);

        setMaxSizes();
        resetIconVisibilities();
    }



    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.stillshot: {
                if(!mIsImageAvailable){
                    Log.d(TAG, "onClick: taking picture.");
                    takePicture();
                }
                break;
            }

            case R.id.switch_orientation: {
                Log.d(TAG, "onClick: switching camera orientation.");
                toggleCameraDisplayOrientation();
                break;
            }

            case R.id.close_stillshot_view:{
                hideStillshotContainer();
                break;
            }

            case R.id.init_draw_icon:{
                toggleColorPickerVisibility();
                break;
            }

            case R.id.undo_container:{
                undoAction();
                break;
            }

            case R.id.save_stillshot:{
                saveCapturedStillshotToDisk();
                break;
            }

            case R.id.init_sticker_icon:{
                toggleStickers();
                break;
            }
        }
    }

    public void addSticker(Drawable sticker){
        mStillshotImageView.addNewSticker(sticker);
    }

    public void setTrashIconSize(int width, int height){
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mTrashIcon.getLayoutParams();

        final float scale = getContext().getResources().getDisplayMetrics().density;

        params.height = (int) (width * scale + 0.5f);
        params.width = (int) (height * scale + 0.5f);;
        mTrashIcon.setLayoutParams(params);
    }

    public void dragStickerStarted(){
        if(mStillshotImageView.mSelectedStickerIndex != -1){
            mColorPickerContainer.animate().alpha(0.0f).setDuration(ICON_FADE_DURATION);
            mUndoContainer.animate().alpha(0.0f).setDuration(ICON_FADE_DURATION);
            mPenContainer.animate().alpha(0.0f).setDuration(ICON_FADE_DURATION);
            mSaveContainer.animate().alpha(0.0f).setDuration(ICON_FADE_DURATION);
            mCloseStillshotContainer.animate().alpha(0.0f).setDuration(ICON_FADE_DURATION);
            mStickerContainer.animate().alpha(0.0f).setDuration(ICON_FADE_DURATION);

            mTrashContainer.setVisibility(View.VISIBLE);
        }
    }

    public void dragStickerStopped(){
        if(mStillshotImageView.mSelectedStickerIndex == -1){
            mColorPickerContainer.animate().alpha(1.0f).setDuration(0);
            mUndoContainer.animate().alpha(1.0f).setDuration(0);
            mPenContainer.animate().alpha(1.0f).setDuration(0);
            mSaveContainer.animate().alpha(1.0f).setDuration(0);
            mCloseStillshotContainer.animate().alpha(1.0f).setDuration(0);
            mStickerContainer.animate().alpha(1.0f).setDuration(0);

            mTrashContainer.setVisibility(View.INVISIBLE);
        }
    }

    private void toggleStickers(){
        Log.d(TAG, "displayStickers: called.");
        mIMainActivity.toggleViewStickersFragment();
    }

    private void saveCapturedStillshotToDisk(){
        if(mIsImageAvailable){
            Log.d(TAG, "saveCapturedStillshotToDisk: saving image to disk.");

            final ICallback callback = new ICallback() {
                @Override
                public void done(Exception e) {
                    if(e == null){
                        Log.d(TAG, "onImageSavedCallback: image saved!");
                        showSnackBar("Image saved", Snackbar.LENGTH_SHORT);
                    }
                    else{
                        Log.d(TAG, "onImageSavedCallback: error saving image: " + e.getMessage());
                        showSnackBar("Error saving image", Snackbar.LENGTH_SHORT);
                    }
                }
            };

            if(mCapturedImage != null){

                Log.d(TAG, "saveCapturedStillshotToDisk: saving to disk.");

                mStillshotImageView.invalidate();
                Bitmap bitmap = Bitmap.createBitmap(mStillshotImageView.getDrawingCache());

                ImageSaver imageSaver = new ImageSaver(
                        bitmap,
                        getActivity().getExternalFilesDir(null),
                        callback
                );
                mBackgroundHandler.post(imageSaver);
            }
        }
    }

    @Override
    public void onColorChange(int selectedColor) {
        mStillshotImageView.setBrushColor(selectedColor);
    }

    private void toggleColorPickerVisibility(){
        if(mColorPickerContainer.getVisibility() == View.VISIBLE){
            mColorPickerContainer.setVisibility(View.INVISIBLE);
            mUndoContainer.setVisibility(View.INVISIBLE);
            mStickerContainer.setVisibility(View.VISIBLE);
            mTrashContainer.setVisibility(View.INVISIBLE);

            mIsDrawingEnabled = false;
        }
        else if(mColorPickerContainer.getVisibility() == View.INVISIBLE){
            mColorPickerContainer.setVisibility(View.VISIBLE);
            mUndoContainer.setVisibility(View.VISIBLE);
            mStickerContainer.setVisibility(View.INVISIBLE);
            mTrashContainer.setVisibility(View.INVISIBLE);

            if(mStillshotImageView.getBrushColor() == 0){
                mStillshotImageView.setBrushColor(Color.WHITE);
            }

            mIsDrawingEnabled = true;
        }
        mStillshotImageView.setDrawingIsEnabled(mIsDrawingEnabled);
    }

    private void undoAction(){
        if(mIsDrawingEnabled){
            mStillshotImageView.removeLastPath();
        }
    }

    public void drawingStarted(){
        if(!mIsCurrentlyDrawing){
            mColorPickerContainer.animate().alpha(0.0f).setDuration(ICON_FADE_DURATION);
            mUndoContainer.animate().alpha(0.0f).setDuration(ICON_FADE_DURATION);
            mSaveContainer.animate().alpha(0.0f).setDuration(ICON_FADE_DURATION);
            mPenContainer.animate().alpha(0.0f).setDuration(ICON_FADE_DURATION);
            mCloseStillshotContainer.animate().alpha(0.0f).setDuration(ICON_FADE_DURATION);
            mStickerContainer.animate().alpha(0.0f).setDuration(ICON_FADE_DURATION);

            mIsCurrentlyDrawing = true;
        }
    }

    public void drawingStopped(){

        if(mIsCurrentlyDrawing){
            mColorPickerContainer.animate().alpha(1.0f).setDuration(0);
            mUndoContainer.animate().alpha(1.0f).setDuration(0);
            mPenContainer.animate().alpha(1.0f).setDuration(0);
            mSaveContainer.animate().alpha(1.0f).setDuration(0);
            mCloseStillshotContainer.animate().alpha(1.0f).setDuration(0);
            mStickerContainer.animate().alpha(1.0f).setDuration(0);

            mIsCurrentlyDrawing = false;
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {

        switch (motionEvent.getAction()) {

            case MotionEvent.ACTION_DOWN:{
                startX = motionEvent.getX();
                startY = motionEvent.getY();
                break;
            }

            case MotionEvent.ACTION_UP:{
                float endX = motionEvent.getX();
                float endY = motionEvent.getY();
                if (isAClick(startX, endX, startY, endY)) {
                    if(view.getId() == R.id.texture && view.getId() != R.id.stillshot){
                        Log.d(TAG, "onTouch: MANUAL FOCUS.");
                        startManualFocus(view, motionEvent);
                    }
                }
                break;
            }

            case MotionEvent.ACTION_MOVE:{

                break;
            }
        }

        if(mIsImageAvailable){
            Log.d(TAG, "onTouch: sending touch event to DrawableImageView");
            return mStillshotImageView.touchEvent(motionEvent);
        }
        else{
            Log.d(TAG, "onTouch: ZOOM.");
            return mTextureView.onTouch(motionEvent);
        }

    }

    private boolean mManualFocusEngaged = false;
    private int CLICK_ACTION_THRESHOLD = 200;
    private float startX;
    private float startY;

    private boolean isAClick(float startX, float endX, float startY, float endY) {
        float differenceX = Math.abs(startX - endX);
        float differenceY = Math.abs(startY - endY);
        return !(differenceX > CLICK_ACTION_THRESHOLD/* =5 */ || differenceY > CLICK_ACTION_THRESHOLD);
    }

    private boolean isMeteringAreaAFSupported() {

        CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics characteristics = null;
        try {
            characteristics = manager.getCameraCharacteristics(mCameraId);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return false;
        }
        return characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) >= 1;
    }

    private boolean startManualFocus(View view, MotionEvent motionEvent){
        Log.d(TAG, "startManualFocus: called");

        if (mManualFocusEngaged) {
            Log.d(TAG, "startManualFocus: Manual focus already engaged");
            return true;
        }

        CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics characteristics = null;
        try {
            characteristics = manager.getCameraCharacteristics(mCameraId);

            final Rect sensorArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

            final int y = (int)((motionEvent.getX() / (float)view.getWidth())  * (float)sensorArraySize.height());
            final int x = (int)((motionEvent.getY() / (float)view.getHeight()) * (float)sensorArraySize.width());
            final int halfTouchWidth  = 150;
            final int halfTouchHeight = 150;
            MeteringRectangle focusAreaTouch = new MeteringRectangle(
                    Math.max(x - halfTouchWidth,  0),
                    Math.max(y - halfTouchHeight, 0),
                    halfTouchWidth  * 2,
                    halfTouchHeight * 2,
                    MeteringRectangle.METERING_WEIGHT_MAX - 1);

            CameraCaptureSession.CaptureCallback captureCallbackHandler = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    mManualFocusEngaged = false;

                    if (request.getTag() == "FOCUS_TAG") {

                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, null);

                        try {
                            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }
                }

                @Override
                public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
                    super.onCaptureFailed(session, request, failure);
                    Log.e(TAG, "startManualFocus: Manual AF failure: " + failure);
                    mManualFocusEngaged = false;
                }
            };

            mCaptureSession.stopRepeating();

            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), captureCallbackHandler, mBackgroundHandler);

            if (isMeteringAreaAFSupported()) {
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{focusAreaTouch});
            }
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);

            mPreviewRequestBuilder.setTag("FOCUS_TAG");

            mCaptureSession.capture(mPreviewRequestBuilder.build(), captureCallbackHandler, mBackgroundHandler);
            mManualFocusEngaged = true;


        } catch (CameraAccessException e) {
            e.printStackTrace();
            return true;
        }

        return true;
    }

    private void hideStillshotContainer(){
        mIMainActivity.showStatusBar();
        if(mIsImageAvailable){
            mIsImageAvailable = false;
            mCapturedBitmap = null;
            mStillshotImageView.setImageBitmap(null);

            mIsDrawingEnabled = false;
            mStillshotImageView.reset();
            mStillshotImageView.setDrawingIsEnabled(mIsDrawingEnabled);
            mStillshotImageView.setImageBitmap(null);

            resetIconVisibilities();

            mTextureView.resetScale();

            reopenCamera();
        }
    }

    private void resetIconVisibilities(){
        mColorPickerContainer.setVisibility(View.INVISIBLE);
        mUndoContainer.setVisibility(View.INVISIBLE);
        mStillshotContainer.setVisibility(View.INVISIBLE);
        mPenContainer.setVisibility(View.VISIBLE);
        mStickerContainer.setVisibility(View.VISIBLE);

        mFlashContainer.setVisibility(View.VISIBLE);
        mSwitchOrientationContainer.setVisibility(View.VISIBLE);
        mCaptureBtnContainer.setVisibility(View.VISIBLE);
        mTrashContainer.setVisibility(View.INVISIBLE);

    }

    private void takePicture()  {
        lockFocus();
    }

    private void lockFocus() {
        try {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);

            mState = STATE_WAITING_LOCK;

            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            Log.d(TAG, "onSurfaceTextureAvailable: w: " + width + ", h: " + height);
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            Log.d(TAG, "onSurfaceTextureSizeChanged: w: " + width + ", h: " + height);
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };

    public void reopenCamera() {
        Log.d(TAG, "reopenCamera: called.");
        if (mTextureView.isAvailable()) {
            Log.d(TAG, "reopenCamera: a surface is available.");
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            Log.d(TAG, "reopenCamera: no surface is available.");
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }

    }

    private void openCamera(int width, int height) {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }

        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {

                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    else if(afState == CaptureResult.CONTROL_AF_STATE_INACTIVE){
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };

    private void runPrecaptureSequence() {
        try {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void captureStillPicture() {
        Log.d(TAG, "captureStillPicture: capturing picture.");
        try {

            final Activity activity = getActivity();
            if (null == activity || null == mCameraDevice) {
                return;
            }
            CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    unlockFocus();
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "onImageAvailable: called.");

            if(!mIsImageAvailable){
                mCapturedImage = reader.acquireLatestImage();

                Log.d(TAG, "onImageAvailable: captured image width: " + mCapturedImage.getWidth());
                Log.d(TAG, "onImageAvailable: captured image height: " + mCapturedImage.getHeight());

                saveTempImageToStorage();

                final Activity activity = getActivity();
                if(activity != null){
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            Glide.with(activity)
                                    .load(mCapturedImage)
                                    .into(mStillshotImageView);

                            showStillshotContainer();
                        }
                    });
                }
            }

        }
    };

    private int getOrientation(int rotation) {

        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    private void unlockFocus() {
        try {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
            mState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }



    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            Log.d(TAG, "onError: " + error);
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }
    };

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            Surface surface = new Surface(texture);

            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);


            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            if (null == mCameraDevice) {
                                return;
                            }
                            mCaptureSession = cameraCaptureSession;

                            try {

                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            showSnackBar("Failed", Snackbar.LENGTH_LONG);
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {

        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    private void startBackgroundThread() {
        if(mBackgroundThread == null){
            Log.d(TAG, "startBackgroundThread: called.");
            mBackgroundThread = new HandlerThread("CameraBackground");
            mBackgroundThread.start();
            mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        }
    }

    private void stopBackgroundThread() {
        if(mBackgroundThread != null){
            mBackgroundThread.quitSafely();
            try {
                mBackgroundThread.join();
                mBackgroundThread = null;
                mBackgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume: called.");
        super.onResume();

        startBackgroundThread();

        if(mIsImageAvailable){
            mIMainActivity.hideStatusBar();
        }
        else{
            mIMainActivity.showStatusBar();

            reopenCamera();
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        if(mBackgroundImageRotater != null){
            mBackgroundImageRotater.cancel(true);
        }
        super.onPause();
    }

    @SuppressWarnings("SuspiciousNameCombination")
    private void setUpCameraOutputs(int width, int height) {
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

        try {
            Log.d(TAG, "setUpCameraOutputs: called.");
            if (!mIMainActivity.isCameraBackFacing() && !mIMainActivity.isCameraFrontFacing()) {
                Log.d(TAG, "setUpCameraOutputs: finding camera id's.");
                findCameraIds();
            }

            CameraCharacteristics characteristics
                    = manager.getCameraCharacteristics(mCameraId);

            Log.d(TAG, "setUpCameraOutputs: camera id: " + mCameraId);

            StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;

            Size largest = null;
            float screenAspectRatio = (float)SCREEN_WIDTH / (float)SCREEN_HEIGHT;
            List<Size> sizes = new ArrayList<>();
            for( Size size : Arrays.asList(map.getOutputSizes(ImageFormat.JPEG))){

                float temp = (float)size.getWidth() / (float)size.getHeight();

                Log.d(TAG, "setUpCameraOutputs: temp: " + temp);
                Log.d(TAG, "setUpCameraOutputs: w: " + size.getWidth() + ", h: " + size.getHeight());

                if(temp > (screenAspectRatio - screenAspectRatio * ASPECT_RATIO_ERROR_RANGE )
                        && temp < (screenAspectRatio + screenAspectRatio * ASPECT_RATIO_ERROR_RANGE)){
                    sizes.add(size);
                    Log.d(TAG, "setUpCameraOutputs: found a valid size: w: " + size.getWidth() + ", h: " + size.getHeight());
                }

            }
            if(sizes.size() > 0){
                largest = Collections.max(
                        sizes,
                        new Utility.CompareSizesByArea());
                Log.d(TAG, "setUpCameraOutputs: largest width: " + largest.getWidth());
                Log.d(TAG, "setUpCameraOutputs: largest height: " + largest.getHeight());
            }

            int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();

            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            boolean swappedDimensions = false;
            switch (displayRotation) {
                case Surface.ROTATION_0:
                case Surface.ROTATION_180:
                    if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                        swappedDimensions = true;
                    }
                    break;
                case Surface.ROTATION_90:
                case Surface.ROTATION_270:
                    if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                        swappedDimensions = true;
                    }
                    break;
                default:
                    Log.e(TAG, "Display rotation is invalid: " + displayRotation);
            }

            Point displaySize = new Point();
            activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
            int rotatedPreviewWidth = width;
            int rotatedPreviewHeight = height;
            int maxPreviewWidth = displaySize.x;
            int maxPreviewHeight = displaySize.y;

            if (swappedDimensions) {
                rotatedPreviewWidth = height;
                rotatedPreviewHeight = width;
                maxPreviewWidth = displaySize.y;
                maxPreviewHeight = displaySize.x;
            }

            if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                maxPreviewWidth = MAX_PREVIEW_WIDTH;
            }

            if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                maxPreviewHeight = MAX_PREVIEW_HEIGHT;
            }

            Log.d(TAG, "setUpCameraOutputs: max preview width: " + maxPreviewWidth);
            Log.d(TAG, "setUpCameraOutputs: max preview height: " + maxPreviewHeight);


            mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                    ImageFormat.JPEG, /*maxImages*/2);
            mImageReader.setOnImageAvailableListener(
                    mOnImageAvailableListener, mBackgroundHandler);


            mPreviewSize = Utility.chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                    maxPreviewHeight, largest);


            Log.d(TAG, "setUpCameraOutputs: preview width: " + mPreviewSize.getWidth());
            Log.d(TAG, "setUpCameraOutputs: preview height: " + mPreviewSize.getHeight());

            // We fit the aspect ratio of TextureView to the size of preview we picked.
            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView.setAspectRatio(
                        mPreviewSize.getWidth(), mPreviewSize.getHeight(), SCREEN_WIDTH, SCREEN_HEIGHT);
            } else {
                mTextureView.setAspectRatio(
                        mPreviewSize.getHeight(), mPreviewSize.getWidth(), SCREEN_HEIGHT, SCREEN_WIDTH);
            }


            Log.d(TAG, "setUpCameraOutputs: cameraId: " + mCameraId);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {

            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }

    }

    private void setMaxSizes(){
        Point displaySize = new Point();
        getActivity().getWindowManager().getDefaultDisplay().getSize(displaySize);
        SCREEN_HEIGHT = displaySize.x;
        SCREEN_WIDTH = displaySize.y;

        Log.d(TAG, "setMaxSizes: screen width:" + SCREEN_WIDTH);
        Log.d(TAG, "setMaxSizes: screen height: " + SCREEN_HEIGHT);
    }


    private void findCameraIds(){

        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

        try {
            for (String cameraId : manager.getCameraIdList()) {
                Log.d(TAG, "findCameraIds: CAMERA ID: " + cameraId);
                if (cameraId == null) continue;
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                int facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing == CameraCharacteristics.LENS_FACING_FRONT){
                    mIMainActivity.setFrontCameraId(cameraId);
                }
                else if (facing == CameraCharacteristics.LENS_FACING_BACK){
                    mIMainActivity.setBackCameraId(cameraId);
                }
            }
            mIMainActivity.setCameraFrontFacing();
            mCameraId = mIMainActivity.getFrontCameraId();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void toggleCameraDisplayOrientation(){
        if(mCameraId.equals(mIMainActivity.getBackCameraId())){
            mCameraId = mIMainActivity.getFrontCameraId();
            mIMainActivity.setCameraFrontFacing();
            closeCamera();
            reopenCamera();
            Log.d(TAG, "toggleCameraDisplayOrientation: switching to front-facing camera.");
        }
        else if(mCameraId.equals(mIMainActivity.getFrontCameraId())){
            mCameraId = mIMainActivity.getBackCameraId();
            mIMainActivity.setCameraBackFacing();
            closeCamera();
            reopenCamera();
            Log.d(TAG, "toggleCameraDisplayOrientation: switching to back-facing camera.");
        }
        else{
            Log.d(TAG, "toggleCameraDisplayOrientation: error.");
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        Log.d(TAG, "configureTransform: viewWidth: " + viewWidth + ", viewHeight: " + viewHeight);
        Log.d(TAG, "configureTransform: previewWidth: " + mPreviewSize.getWidth() + ", previewHeight: " + mPreviewSize.getHeight());
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            Log.d(TAG, "configureTransform: rotating from 90 or 270");
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        else if (Surface.ROTATION_180 == rotation) {
            Log.d(TAG, "configureTransform: rotating 180.");
            matrix.postRotate(180, centerX, centerY);
        }


        float screenAspectRatio = (float)SCREEN_WIDTH / (float)SCREEN_HEIGHT;
        float previewAspectRatio = (float)mPreviewSize.getWidth() / (float)mPreviewSize.getHeight();
        String roundedScreenAspectRatio = String.format("%.2f", screenAspectRatio);
        String roundedPreviewAspectRatio = String.format("%.2f", previewAspectRatio);
        if(!roundedPreviewAspectRatio.equals(roundedScreenAspectRatio) ){

            float scaleFactor = (screenAspectRatio / previewAspectRatio);
            Log.d(TAG, "configureTransform: scale factor: " + scaleFactor);

            float heightCorrection = (((float)SCREEN_HEIGHT * scaleFactor) - (float)SCREEN_HEIGHT) / 2;

            matrix.postScale(scaleFactor, 1);
            matrix.postTranslate(-heightCorrection, 0);
        }

        mTextureView.setTransform(matrix);
    }

    private void requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    private void saveTempImageToStorage(){

        Log.d(TAG, "saveTempImageToStorage: saving temp image to disk.");
        final ICallback callback = new ICallback() {
            @Override
            public void done(Exception e) {
                if(e == null){
                    Log.d(TAG, "onImageSavedCallback: image saved!");

                    mBackgroundImageRotater = new BackgroundImageRotater(getActivity());
                    mBackgroundImageRotater.execute();
                    mIsImageAvailable = true;
                    mCapturedImage.close();

                }
                else{
                    Log.d(TAG, "onImageSavedCallback: error saving image: " + e.getMessage());
                    showSnackBar("Error displaying image", Snackbar.LENGTH_SHORT);
                }
            }
        };

        ImageSaver imageSaver = new ImageSaver(
                mCapturedImage,
                getActivity().getExternalFilesDir(null),
                callback
        );
        mBackgroundHandler.post(imageSaver);
    }

    private void displayCapturedImage(){
        Log.d(TAG, "displayCapturedImage: displaying stillshot image.");
        final Activity activity = getActivity();
        if(activity != null){
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    RequestOptions options = new RequestOptions()
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .skipMemoryCache(true)
                            .centerCrop();

                    int bitmapWidth = mCapturedBitmap.getWidth();
                    int bitmapHeight = mCapturedBitmap.getHeight();

                    Log.d(TAG, "run: captured image width: " + bitmapWidth);
                    Log.d(TAG, "run: captured image height: " + bitmapHeight);


                    int focusX = (int)(mTextureView.mFocusX);
                    int focusY = (int)(mTextureView.mFocusY);
                    Log.d(TAG, "run: focusX: " + focusX);
                    Log.d(TAG, "run: focusY: " + focusY);


                    int maxWidth = mTextureView.getWidth();
                    int maxHeight = mTextureView.getHeight();
                    Log.d(TAG, "run: initial maxWidth: " + maxWidth);
                    Log.d(TAG, "run: initial maxHeight: " + maxHeight);

                    float bitmapHeightScaleFactor = (float)bitmapHeight / (float)maxHeight;
                    float bitmapWidthScaleFactor = (float)bitmapWidth / (float)maxWidth;
                    Log.d(TAG, "run: bitmap width scale factor: " + bitmapWidthScaleFactor);
                    Log.d(TAG, "run: bitmap height scale factor: " + bitmapHeightScaleFactor);

                    int actualWidth = (int)(maxWidth * (1 / mTextureView.mScaleFactorX));
                    int actualHeight = (int)(maxHeight * (1 / mTextureView.mScaleFactorY));
                    Log.d(TAG, "run: actual width: " + actualWidth);
                    Log.d(TAG, "run: actual height: " + actualHeight);


                    int scaledWidth = (int)(actualWidth * bitmapWidthScaleFactor);
                    int scaledHeight = (int)(actualHeight * bitmapHeightScaleFactor);
                    Log.d(TAG, "run: scaled width: " + scaledWidth);
                    Log.d(TAG, "run: scaled height: " + scaledHeight);

                    focusX *= bitmapWidthScaleFactor;
                    focusY *= bitmapHeightScaleFactor;

                    Bitmap background = null;
                    background = Bitmap.createBitmap(
                            mCapturedBitmap,
                            focusX,
                            focusY,
                            scaledWidth,
                            scaledHeight
                    );

                    Glide.with(activity)
                            .setDefaultRequestOptions(options)
                            .load(background)
                            .into(mStillshotImageView);

                    showStillshotContainer();
                }
            });
        }
    }

    private void showStillshotContainer(){
        mStillshotContainer.setVisibility(View.VISIBLE);
        mFlashContainer.setVisibility(View.INVISIBLE);
        mSwitchOrientationContainer.setVisibility(View.INVISIBLE);
        mCaptureBtnContainer.setVisibility(View.INVISIBLE);

        mIMainActivity.hideStatusBar();
        closeCamera();
    }

    private class BackgroundImageRotater extends AsyncTask<Void, Integer, Integer>{

        Activity mActivity;

        public BackgroundImageRotater(Activity activity) {
            mActivity = activity;
        }

        @Override
        protected Integer doInBackground(Void... voids) {
            Log.d(TAG, "doInBackground: adjusting image for display...");
            File file = new File(mActivity.getExternalFilesDir(null), "temp_image.jpg");
            final Uri tempImageUri = Uri.fromFile(file);

            Bitmap bitmap = null;
            try {
                ExifInterface exif = new ExifInterface(tempImageUri.getPath());
                bitmap = MediaStore.Images.Media.getBitmap(mActivity.getContentResolver(), tempImageUri);
                int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
                mCapturedBitmap = rotateBitmap(bitmap, orientation);
            } catch (IOException e) {
                e.printStackTrace();
                return 0;
            }
            return 1;
        }

        @Override
        protected void onPostExecute(Integer integer) {
            super.onPostExecute(integer);
            if(integer == 1){
                displayCapturedImage();
            }
            else{
                showSnackBar("Error preparing image", Snackbar.LENGTH_SHORT);
            }
        }
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int orientation) {

        Matrix matrix = new Matrix();
        switch (orientation) {

            case ExifInterface.ORIENTATION_TRANSPOSE:
                Log.d(TAG, "rotateBitmap: transpose");
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;

            case ExifInterface.ORIENTATION_NORMAL:
                Log.d(TAG, "rotateBitmap: normal.");
                return bitmap;

            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                Log.d(TAG, "rotateBitmap: flip horizontal");
                matrix.setScale(-1, 1);
                break;

            case ExifInterface.ORIENTATION_ROTATE_180:
                Log.d(TAG, "rotateBitmap: rotate 180");
                matrix.setRotate(180);
                break;

            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                Log.d(TAG, "rotateBitmap: rotate vertical");
                matrix.setRotate(180);
                matrix.postScale(-1, 1);
                break;

            case ExifInterface.ORIENTATION_ROTATE_90:
                Log.d(TAG, "rotateBitmap: rotate 90");
                matrix.setRotate(90);
                break;

            case ExifInterface.ORIENTATION_TRANSVERSE:
                Log.d(TAG, "rotateBitmap: transverse");
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;

            case ExifInterface.ORIENTATION_ROTATE_270:
                Log.d(TAG, "rotateBitmap: rotate 270");
                matrix.setRotate(-90);
                break;
        }
        try {
            if (mIMainActivity.isCameraFrontFacing()) {
                Log.d(TAG, "rotateBitmap: MIRRORING IMAGE.");
                matrix.postScale(-1.0f, 1.0f);
            }

            Bitmap bmRotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap.recycle();

            return bmRotated;
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            return null;
        }
    }

    private static class ImageSaver implements Runnable {

        private final File mFile;

        private Image mImage;

        private ICallback mCallback;

        private Bitmap mBitmap;

        ImageSaver(Bitmap bitmap, File file, ICallback callback) {
            mBitmap = bitmap;
            mFile = file;
            mCallback = callback;
        }

        ImageSaver(Image image, File file, ICallback callback) {
            mImage = image;
            mFile = file;
            mCallback = callback;
        }

        @Override
        public void run() {

            if(mImage != null){
                ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                FileOutputStream output = null;
                try {
                    File file = new File(mFile, "temp_image.jpg");
                    output = new FileOutputStream(file);
                    output.write(bytes);
                } catch (IOException e) {
                    e.printStackTrace();
                    mCallback.done(e);
                } finally {
                    mImage.close();
                    if (null != output) {
                        try {
                            output.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    mCallback.done(null);
                }
            }
            else if(mBitmap != null){
                ByteArrayOutputStream stream = null;
                byte[] imageByteArray = null;
                stream = new ByteArrayOutputStream();
                mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
                imageByteArray = stream.toByteArray();

                SimpleDateFormat s = new SimpleDateFormat("ddMMyyyyhhmmss");
                String format = s.format(new Date());
                File file = new File(mFile, "image_" + format + ".jpg");

                // save the mirrored byte array
                FileOutputStream output = null;
                try {
                    output = new FileOutputStream(file);
                    output.write(imageByteArray);
                } catch (IOException e) {
                    mCallback.done(e);
                    e.printStackTrace();
                } finally {
                    if (null != output) {
                        try {
                            output.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        mCallback.done(null);
                    }
                }
            }
        }
    }

    private void showSnackBar(final String text, final int length) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    View view = activity.findViewById(android.R.id.content).getRootView();
                    Snackbar.make(view, text, length).show();
                }
            });
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try{
            mIMainActivity = (IMainActivity) getActivity();
        }catch (ClassCastException e){
            Log.e(TAG, "onAttach: ClassCastException: " + e.getMessage() );
        }
    }

    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }

    }

    public static class ConfirmationDialog extends DialogFragment {

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.request_permission)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            parent.requestPermissions(new String[]{Manifest.permission.CAMERA},
                                    REQUEST_CAMERA_PERMISSION);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Activity activity = parent.getActivity();
                                    if (activity != null) {
                                        activity.finish();
                                    }
                                }
                            })
                    .create();
        }
    }
}
