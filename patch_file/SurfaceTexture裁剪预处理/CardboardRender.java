package com.rockchip.vr.videoplayer.renderer;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.Message;
import android.renderscript.Matrix4f;
import android.util.Log;
import android.view.Surface;
import android.media.MediaMetadataRetriever;

import com.google.vrtoolkit.cardboard.CardboardView;
import com.google.vrtoolkit.cardboard.Eye;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.Viewport;
import com.rockchip.vr.videoplayer.utils.SystemProperties;
import com.rockchip.vr.videoplayer.R;
import com.rockchip.vr.videoplayer.app.BaseApplication;
import com.rockchip.vr.videoplayer.model.CurveCanvas;
import com.rockchip.vr.videoplayer.model.Mode;
import com.rockchip.vr.videoplayer.model.Plane;
import com.rockchip.vr.videoplayer.model.Sphere;
import com.rockchip.vr.videoplayer.model.VideoInfo;
import com.rockchip.vr.videoplayer.ui.VRProgressBar;
import com.rockchip.vr.videoplayer.ui.VRect;
import com.rockchip.vr.videoplayer.utils.Constant;
import com.rockchip.vr.videoplayer.utils.DBUtils;
import com.rockchip.vr.videoplayer.utils.SharedPreference;
import com.rockchip.vr.videoplayer.utils.TextureHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;

import javax.microedition.khronos.egl.EGLConfig;

public class CardboardRender implements CardboardView.StereoRenderer, SurfaceTexture.OnFrameAvailableListener {
    // ============ mode setting ===============
    private final boolean getVideoDir = true;
    private final boolean getImageDir = true;
    private final boolean rotateDebug = false;
    private final boolean logoDisplay = false;
    private final boolean chkEnable = false;
    private final float logoFadeSpeed = 0.0013f;
    // =========================================

    // ============== PrograssBar ===============
    VRProgressBar mProgressBar;
    Bitmap mProgressBitmap;
    //===========================================

    private final String TAG = "CardboardRender";

    public float zhanghao = 0f;

    public int onFrameAvailable = 0;

    private Activity mContext;
    private Sphere mSphere;
    private CurveCanvas mCurveCanvas;
    private Plane mCanvas;
    private boolean firstFlag = true;
    private int playMode = 3;
    private String mVideoPath;
    private Matrix4f projMat = null;
    private Matrix4f viewMat = null;
    private Matrix4f modeMat = null;
    private Matrix4f mvpMat = null;
    private Matrix4f resetViewMat = null;
    private Matrix4f resetMat1 = new Matrix4f();
    private Matrix4f resetMat2 = new Matrix4f();
    private Matrix4f resetHeadMat = new Matrix4f();
    private float[] mvpMatArray = new float[16];

    private TextureManager texMgr;
    public RenderManager renderMgr;
    public SourceManager srcMgr;
    public CheckManager chkMgr;

    private MediaPlayer mMediaPlayer = null;
    private VideoInfo mCurrentVideoInfo = null;
    private SurfaceTexture mSurfaceTexture;

    private int drawBlackForPic = -1;
    private int drawBlackForVideo = -1;

    private int shader_type_for_pic = 0;
    private int shader_mode_for_pic = 0;

    private float logoFade = 1.0f;
    private int logoBinding = -1;

    private float angle = 0.0f;

    private int bufferWidth;
    private int videoWidth;
    private int videoHeight;
    private float widthRatioToDraw = 1.0f;

    private float[] projectionMatrix = new float[16];

    private float[] viewMatrix = new float[16];

    private final float[] model_Matrix = new float[]{
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
    };

    private float[] transformMatrix = new float[]{
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
    };

    private float[] identity_matrix = new float[]{
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
    };

    private float[] rotate_matrix = new float[]{
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
    };

    public final int maxPlane = 10;
    public Plane[] mPlane = new Plane[maxPlane];
    public boolean[] addSceneList = new boolean[maxPlane];
    public boolean resetPositionList = false;

    private int resetAndSkip = 2;
    private int mLooking = -1;
    private Boolean mShowControl = false;
    private boolean mResetedPosition = false;
    public int djw = 0;
    private float[] headView = new float[16];
    private float[] headViewToDraw = new float[16];
    private float[] inv_headView = new float[16];
    private Matrix4f resetHeadView = new Matrix4f();
    private Matrix4f inverseHeadView = new Matrix4f();
    private float[] rotateAngle = new float[3];
    private float mProgressLooking = -1.0f;
    private Object obj = new Object();

    private boolean mLastControl;

    public void initScene(HeadTransform headTransform) {
        // djw - get current headView
        headTransform.getHeadView(headView, 0);

        // djw - copy current headView use in onDrawScene
        headViewToDraw = headView;

        // djw - reset List
        for (int i = 0; i < maxPlane; i++) {
            addSceneList[i] = false;
            resetPositionList = false;
        }

        if (mShowControl) {
            if (!mLastControl) {
                mLastControl = mShowControl;
                SetCpuGpuLevel(1, 1);
            }
            if (!mResetedPosition) {
                Log.e(TAG, "resetPositionList now");
                resetPositionList = true;
                mResetedPosition = true;
                resetAndSkip = 2;

                // djw - calculate inverse headView
                Matrix4f tmp = new Matrix4f(headView);
                rotateAngle = getRotation2(tmp);
                tmp.loadIdentity();
                tmp.rotate((float) Math.toDegrees(rotateAngle[0]), 0f, 1f, 0f);
                resetHeadView = tmp;
            }

            // djw - headView multiply resetHeadView
            Matrix4f tmp = new Matrix4f(headView);
            tmp.multiply(resetHeadView);
            headView = tmp.getArray();

            //======== 背景图 ========
            mPlane[0].setTranslate(0f, -110f, -150f);
            mPlane[0].setScale(70f, 35f, 50f);
            mPlane[0].bindTexture(texMgr.menu_bg);
//			if (mPlane[0].isLookingAtObject(headView)) {
//				mPlane[0].setFade(0.5f);
//			} else
            mPlane[0].setFade(1f);
            addSceneList[0] = true;

            //======== 进度条 ========
            mPlane[1].setTranslate(0f, -85f, -150f);
            mPlane[1].setScale(60f, 40f, 50f);
            mPlane[1].bindTexture(texMgr.prograss_bar[0]);

            if (mPlane[1].isLookingAtObject(headView, -0.23f, 0.23f, -0.22f, -0.16f)) {
//				mPlane[1].setFade(0.5f);
                float duration = 0.46f;
                float position = 0.0f;
                float yaw = mPlane[1].getYaw(headView);
                if (yaw < 0) {
                    position = Math.abs(yaw) + 0.23f;
                    if (position > duration) {
                        position = duration;
                    }
                } else {
                    position = 0.23f - yaw;
                    if (position < 0) {
                        position = 0;
                    }
                }
                position = position > duration ? position = duration : position < 0 ? 0 : position;
                position = position / duration;
                mProgressLooking = position;
            } else {
                mProgressLooking = -1.0f;
                mPlane[1].setFade(1.0f);
            }
            addSceneList[1] = true;

            //======== 上一曲 ========
            mPlane[2].setTranslate(-75f, -210f, -100f);
            mPlane[2].setScale(20f, 15f, 50f);
            mPlane[2].bindTexture(texMgr.img_prev);
            if (mPlane[2].isLookingAtObject(headView, -0.28f, -0.16f, -0.73f, -0.66f)) {
                mPlane[2].setFade(0.5f);
            } else
                mPlane[2].setFade(1f);
            addSceneList[2] = true;

            //======== 播放暂停 ========
            mPlane[3].setTranslate(0f, -210f, -100f);
            mPlane[3].setScale(20f, 15f, 50f);
            mPlane[3].bindTexture(texMgr.img_play_pause[0]);
//			if (mPlane[3].isLookingAtObject(headView, -0.10f, 0.10f,-0.74f, -0.68f)) {
            if (mPlane[3].isLookingAtObject(headView, -0.06f, 0.06f, -0.74f, -0.68f)) {
                mPlane[3].setFade(0.5f);
            } else
                mPlane[3].setFade(1f);
            addSceneList[3] = true;

            //======== 下一曲 ========
            mPlane[4].setTranslate(75f, -210f, -100f);
            mPlane[4].setScale(20f, 15f, 50f);
            mPlane[4].bindTexture(texMgr.img_next);
            if (mPlane[4].isLookingAtObject(headView, 0.18f, 0.31f, -0.71f, -0.64f)) {
                mPlane[4].setFade(0.5f);
            } else {
                mPlane[4].setFade(1f);
            }
            addSceneList[4] = true;

            // cursor
            mPlane[9].setTranslate(0f, 0f, -100f);
            mPlane[9].setScale(12f, 12f, 50f);
            mPlane[9].bindTexture(texMgr.texture_cursor);
            mPlane[9].setFade(1f);
            addSceneList[9] = true;

//			mPlane[9].setTranslate(0f, 0f, -200f);
//			mPlane[9].setScale(5f, 5f, 50f);
//			mPlane[9].bindTexture(texMgr.texture_cursor);
//			if (mPlane[9].isLookingAtObject(headView))
//				mPlane[9].setFade(0.5f);
//			else
//				mPlane[9].setFade(1f);
//			addSceneList[9] = true;

//			if (mPlane[0].isLookingAtObject(headView)) {
//				mLooking = VR.id.VIDEO_CONTROL_BG;
//			} else
            if (mPlane[1].isLookingAtObject(headView, -0.23f, 0.23f, -0.22f, -0.16f)) {
                mLooking = VR.id.VIDEO_CONTROL_PRO;
            } else if (mPlane[2].isLookingAtObject(headView, -0.28f, -0.16f, -0.73f, -0.66f)) {
                mLooking = VR.id.VIDEO_CONTROL_PREV;
            } else if (mPlane[3].isLookingAtObject(headView, -0.10f, 0.10f, -0.74f, -0.68f)) {
                mLooking = VR.id.VIDEO_CONTROL_PLAY;
            } else if (mPlane[4].isLookingAtObject(headView, 0.18f, 0.31f, -0.71f, -0.64f)) {
                mLooking = VR.id.VIDEO_CONTROL_NEXT;
            } else {
                mLooking = VR.id.VIDEO_VIEW_NONE;
            }
        } else {
            if (mLastControl) {
                mLastControl = mShowControl;
                SetCpuGpuLevel(0, 0);
            }
            mLooking = VR.id.VIDEO_VIEW_NONE;
            mResetedPosition = false;
        }
    }

    public static float[] getRotation(Matrix4f m) {
        float x, y, z;
        if (Math.abs(m.get(0, 2) - 1) < 0.0000001) {
            x = (float) Math.atan2(-m.get(1, 0), m.get(1, 1));
            y = (float) (-3.1415926535897931 / 2);
            z = 0.0f;
        } else if (Math.abs(m.get(0, 2) + 1) < 0.0000001) {
            x = (float) Math.atan2(m.get(1, 0), m.get(1, 1));
            y = (float) (3.1415926535897931 / 2);
            z = 0.0f;
        } else {
            x = (float) Math.atan2(m.get(1, 2), m.get(2, 2));
            y = (float) Math.atan2(-m.get(0, 2), Math.sqrt(m.get(1, 2) * m.get(1, 2) + m.get(2, 2) * m.get(2, 2)));
            z = (float) Math.atan2(m.get(0, 1), m.get(0, 0));
        }
        return new float[]{x, y, z};
    }

    public float[] getRotation2(Matrix4f matrix) {
        float yaw = 0.0f;
        float pitch = 0.0f;
        float roll = 0.0f;
        if (matrix.get(0, 0) == 1.0f || matrix.get(0, 0) == -1.0f) {
            yaw = (float) Math.atan2(matrix.get(0, 2), matrix.get(2, 3));
            //pitch and roll remain = 0;
        } else {
            yaw = (float) Math.atan2(-matrix.get(2, 0), matrix.get(0, 0));
            pitch = (float) Math.asin(matrix.get(1, 0));
            roll = (float) Math.atan2(-matrix.get(1, 2), matrix.get(1, 1));
        }
        return new float[]{yaw, pitch, roll};
    }

    private final class VR {
        public final class id {
            public static final int VIDEO_VIEW_NONE = -1;
            public static final int VIDEO_CONTROL_BG = 0;
            public static final int VIDEO_CONTROL_PRO = 1;
            public static final int VIDEO_CONTROL_PREV = 2;
            public static final int VIDEO_CONTROL_PLAY = 3;
            public static final int VIDEO_CONTROL_NEXT = 4;
        }
    }

    public void newPlanes() {
        for (int i = 0; i < maxPlane; i++) {
            mPlane[i] = new Plane();
        }
    }

    public void initPlanes() {
        for (int i = 0; i < maxPlane; i++) {
            mPlane[i].init();
        }
    }

    private Object mMediaPlayerLock = new Object();

    private void playOrPause() {
        if (mMediaPlayer != null) {
            synchronized (mMediaPlayerLock) {
                mNeedReloadState = true;
                if (mMediaPlayer.isPlaying()) {
                    mMediaPlayer.pause();
//					mMainHandler.removeMessages(MSG_UPDATE_PROGRASSBAR);
                } else {
                    mMediaPlayer.start();
                    mMainHandler.removeMessages(MSG_UPDATE_PROGRASSBAR);
                    mMainHandler.sendEmptyMessageDelayed(MSG_UPDATE_PROGRASSBAR, 1000);
                }
            }
        }
    }

    private void next() {
        release();

        mMediaPlayer = new MediaPlayer();
//		String tmp = DBUtils.getNextPath(mContext, mVideoPath);
        synchronized (mMediaPlayerLock) {
            mCurrentVideoInfo = DBUtils.getNextVideoInfo(mContext, mVideoPath);
            String tmp = mCurrentVideoInfo.getPath();
            if (tmp != null) {
                mVideoPath = tmp;
                init_Media(mVideoPath);
                mNeedReloadState = true;
            }
        }
    }

    private void prev() {
        release();

        mMediaPlayer = new MediaPlayer();
//		String tmp = DBUtils.getPrevPath(mContext, mVideoPath);
        synchronized (mMediaPlayerLock) {
            mCurrentVideoInfo = DBUtils.getPrevVideoInfo(mContext, mVideoPath);
            String tmp = mCurrentVideoInfo.getPath();
            if (tmp != null) {
                mVideoPath = tmp;
                init_Media(mVideoPath);
                mNeedReloadState = true;
            }
        }
    }

    private void seekTo(int postion) {
        if (mMediaPlayer != null) {
            synchronized (mMediaPlayerLock) {
                try {
                    if (!mMediaPlayer.isPlaying()) {
                        mMediaPlayer.start();
                        mMainHandler.removeMessages(MSG_UPDATE_PROGRASSBAR);
                        mMainHandler.sendEmptyMessageDelayed(MSG_UPDATE_PROGRASSBAR, 1000);
                    }
                    mNeedReloadState = true;
                    mMediaPlayer.seekTo(postion);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void seekTo(float percentages) {
        if (mMediaPlayer != null) {
            synchronized (mMediaPlayerLock) {
                int duration = mMediaPlayer.getDuration();
                seekTo((int) (duration * percentages));
            }
        }
    }

    public void pause() {
        if (mMediaPlayer != null) {
            synchronized (mMediaPlayerLock) {
                if (mMediaPlayer.isPlaying()) {
                    mMediaPlayer.pause();
                    mMainHandler.removeMessages(MSG_UPDATE_PROGRASSBAR);
                }
            }
        }
    }

    public void release() {
        if (mMediaPlayer != null) {
            synchronized (mMediaPlayerLock) {
                mNeedRender = false;
                if (mMediaPlayer.isPlaying()) {
                    mMediaPlayer.pause();
                    mMediaPlayer.stop();
                }
                mMainHandler.removeMessages(MSG_UPDATE_PROGRASSBAR);
                mMediaPlayer.release();
                mMediaPlayer = null;
            }
        }
    }

    public boolean isPlaying() {
        if (mMediaPlayer != null) {
            return mMediaPlayer.isPlaying();
        }
        return false;
    }

    public void screenOn() {
        if (mMediaPlayer != null) {
            synchronized (mMediaPlayerLock) {
                if (!mMediaPlayer.isPlaying()) {
                    mMediaPlayer.start();
                    mMainHandler.sendEmptyMessageDelayed(MSG_UPDATE_PROGRASSBAR, 1000);
                }
            }
        }
    }

    public void screenOff() {
        if (mMediaPlayer != null) {
            synchronized (mMediaPlayerLock) {
                if (mMediaPlayer.isPlaying()) {
                    Log.d(TAG, "Screen Off, pause Playing");
                    mMediaPlayer.pause();
                    mMainHandler.removeMessages(MSG_UPDATE_PROGRASSBAR);
                }
            }
        }
    }

    public String stringForTime(int timeMs) {
        StringBuilder mFormatBuilder = new StringBuilder();
        Formatter mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());
        int totalSeconds = timeMs / 1000;
        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60);

        mFormatBuilder.setLength(0);
        return mFormatter.format("%02d:%02d", minutes, seconds).toString();
    }

    public class SourceManager {
        public String[] srcPathList = new String[200];
        public int sourceTotal = 0;
        private int sourceIndex = 0;
        public int sourceIndex_last = 0;

        public void setSourceIndex(boolean bool) {
            synchronized (this) {
                if (!bool)
                    this.sourceIndex--;
                else
                    this.sourceIndex++;

                if (this.sourceIndex == this.sourceTotal)
                    this.sourceIndex = 0;
                if (this.sourceIndex == -1)
                    this.sourceIndex = this.sourceTotal - 1;
            }
        }

        public int getSourceIndex() {
            synchronized (this) {
                return this.sourceIndex;
            }
        }

        public void setSourceIndex_last(int index) {
            synchronized (this) {
                this.sourceIndex_last = index;
            }
        }

        public int getSourceIndex_last() {
            synchronized (this) {
                return this.sourceIndex_last;
            }
        }

        public void init_FilePathVideo(boolean bool) {
            traceCall("init_FilePathVideo");
            if (!bool)
                return;

            try {
//				File file = new File("/sdcard/Movies/");
//				File[] files = file.listFiles();
//				for(File index:files){
                String path = mVideoPath;//index.getPath().toString();
                boolean v = true;/*path.endsWith(".mp4")
                        || path.endsWith(".MP4")
						|| path.endsWith(".3GP")
						|| path.endsWith(".3gp")
						|| path.endsWith(".rmvb")
						|| path.endsWith(".RMVB")
						|| path.endsWith(".mkv")
						|| path.endsWith(".MKV");*/
                if (v) {
                    this.srcPathList[this.sourceTotal++] = path;
                }
//				}
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void init_FilePathImage(boolean bool) {
            traceCall("init_FilePathImage");
            if (!bool)
                return;

            try {
                File file = new File("/sdcard/Pictures/");
                File[] files = file.listFiles();
                for (File index : files) {
                    String path = index.getPath().toString();
                    boolean v = path.contains(".jpg")
                            || path.contains(".png")
                            || path.contains(".bmp");
                    if (v) {
                        this.srcPathList[this.sourceTotal++] = path;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class TextureManager {
        public int scene_open;
        public int scene_close;
        public int logo_RK_ChinalMobile;
        public int logo_1;
        public int logo_2;
        public int logo_3;
        public int texture_halo;
        public int texture_cursor;
        public int prograss_bar[];
        public int menu_bg;
        public int img_prev;
        public int img_next;
        public int img_play_pause[];
        public int[] texture_video = new int[1];
        public int[] texture_SDcard = new int[1];

        public void init_TextureApp() {
            prograss_bar = new int[1];
            this.scene_open = TextureHelper.loadTexture(mContext, R.drawable.scene_open);
//			this.scene_close= TextureHelper.loadTexture(mContext, R.drawable.scene_close);
//			this.logo_RK_ChinalMobile = TextureHelper.loadTexture(mContext, R.drawable.logo_rk_china_mobile);
//			this.logo_1 = TextureHelper.loadTexture(mContext, R.drawable.djw);
//			this.logo_2 = TextureHelper.loadTexture(mContext, R.drawable.img_02);
//			this.logo_3 = TextureHelper.loadTexture(mContext, R.drawable.img_03);
//			this.texture_halo = TextureHelper.loadTexture(mContext, R.drawable.halo_gold);
            this.texture_cursor = TextureHelper.loadTexture(mContext, R.drawable.ic_brightness_down);
            this.prograss_bar[0] = TextureHelper.loadTextureFromBitmap(mContext, mProgressBitmap);
            VRect rect = new VRect(mContext);
            Bitmap bitmap = rect.getBitmap();
//			VideoInfo.saveBitmap(bitmap, "/mnt/sdcard/rect.jpg");
            this.menu_bg = TextureHelper.loadTextureFromBitmap(mContext, rect.getBitmap());
            this.img_prev = TextureHelper.loadTexture(mContext, R.drawable.ic_prev);
            this.img_next = TextureHelper.loadTexture(mContext, R.drawable.ic_next);
            img_play_pause = new int[1];
            this.img_play_pause[0] = TextureHelper.loadTexture(mContext, R.drawable.ic_pause);
        }

        public void init_TextureSDcard(String path) {
            traceCall("init_TextureSDcard");
            if (texture_SDcard[0] != 0)
                GLES20.glDeleteTextures(1, texture_SDcard, 0);
            texture_SDcard[0] = TextureHelper.loadTextureFromSdcard(mContext, path);

//			ModelType.Choose_Type = ModelType.Sphere_Type;
            shader_type_for_pic = Plane.SHADER_MAP_PLANE;

            if (path.contains("_lr_sph_180.")) {
//				ModelType.Choose_Type = ModelType.Sphere_Type;
                shader_type_for_pic = Sphere.SHADER_MAP_WORLD_LR_180;
            }
            if (path.contains("_360.")) {
//				ModelType.Choose_Type = ModelType.Sphere_Type;
                shader_type_for_pic = Sphere.SHADER_MAP_WORLD;
            }
            if (path.contains("_ud_sph_360.")) {
//				ModelType.Choose_Type = ModelType.Sphere_Type;
                shader_type_for_pic = Sphere.SHADER_MAP_WORLD;
            }
            if (path.contains("_lr_3d.")) {
//				ModelType.Choose_Type = ModelType.Plane_Type;
                shader_type_for_pic = Sphere.SHADER_MAP_WORLD;
            }
            if (path.contains("_ud_3d.")) {
//				ModelType.Choose_Type = ModelType.Plane_Type;
                shader_type_for_pic = Sphere.SHADER_MAP_WORLD;
            }
        }
    }

    MediaPlayer.OnVideoSizeChangedListener mSizeChangedListener =
            new MediaPlayer.OnVideoSizeChangedListener() {
                public void onVideoSizeChanged(MediaPlayer mp, int width_s, int height_s) {
                    videoWidth = width_s;
                    videoHeight = height_s;
                    Log.d("video-size", "video width=" + videoWidth + "	video height=" + videoHeight);
                }
            };

    public CardboardRender(Activity context) {
        traceCall("CardboardRender");
        this.mContext = context;
        this.mSphere = new Sphere();
        this.mCurveCanvas = new CurveCanvas();
        this.mCanvas = new Plane();
        this.texMgr = new TextureManager();
        this.renderMgr = new RenderManager();
        this.srcMgr = new SourceManager();
        this.chkMgr = new CheckManager();

        this.mProgressBar = new VRProgressBar(context);
        this.mProgressBitmap = mProgressBar.setProgress(0);
        newPlanes();

        mMainHandler.sendEmptyMessageDelayed(MSG_HIDE_UI, 10 * 1000);
    }

    public CardboardRender(Activity context, String path, int mode) {
        this(context);
        this.mVideoPath = path;
        this.playMode = mode;
    }

    public CardboardRender(Activity context, VideoInfo info) {
        this(context, info.getPath(), info.getPlayMode());
        this.mCurrentVideoInfo = info;
    }

    @Override
    public void onSurfaceChanged(int i, int i1) {
        traceCall("onSurfaceChanged");
    }

    @Override
    public void onSurfaceCreated(EGLConfig eglConfig) {
        traceCall("onSurfaceCreated");
        mSphere.init();
        mCurveCanvas.init();
        mCanvas.init();
//		mMainHandler.sendEmptyMessageDelayed(Constant.MSG_LOAD_TEXTURE, 2000);
        texMgr.init_TextureApp();
        srcMgr.init_FilePathVideo(getVideoDir);
        srcMgr.init_FilePathImage(getImageDir);
        initPlanes();
        checkGlError("onSurfaceCreated");
    }

    public void _before_rotate() {
        if (this.rotateDebug) {
            // Y
            angle += 0.005f;
            double s = Math.sin(angle);
            double c = Math.cos(angle);
            float[] tmp_matrix = new float[]{
                    (float) c, 0, (float) -s, 0,
                    0, 1, 0, 0,
                    (float) s, 0, (float) c, 0,
                    0, 0, 0, 1
            };
            rotate_matrix = tmp_matrix;
        }
    }

//	public void _before_chk(){
//		// calculate playmode
//    	if(this.chkEnable){
//			if(((chkMgr.chkCount++)%10)==0){
//				int[] cur_fbo = new int[1];
//				GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, cur_fbo, 0);
//				//this.playMode = chkMgr._chk_run();
//				GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, cur_fbo[0]);
//				checkGlError("onNewFrame(_chk_run)");
//			}
//		}
//		return;
//	}

    public void _before_logo(int index, int index_last) {
        if (this.logoDisplay) {
            this.logoFade -= this.logoFadeSpeed;

            logoBinding = texMgr.logo_RK_ChinalMobile;

            String path = srcMgr.srcPathList[index];
            if (path.contains("_1_"))
                logoBinding = texMgr.logo_1;
            else if (path.contains("_2_"))
                logoBinding = texMgr.logo_2;
            else if (path.contains("_3_"))
                logoBinding = texMgr.logo_3;
        }
    }

    public void _before_pic(int index, int index_last) {
        String path = srcMgr.srcPathList[index];
        boolean v = path.contains(".jpg")
                || path.contains(".png")
                || path.contains(".bmp")
                || path.contains(".jpeg");
        if (!v) {
            return;
        } else {
            if (this.firstFlag) {
                texMgr.init_TextureSDcard(path);
                if (path.contains("_chromatic."))
                    this.playMode = 7;
                else
                    this.playMode = 5;
                return;
            }
        }


        if (this.drawBlackForPic > 0) {
            this.drawBlackForPic--;
            this.playMode = 6;
            this.logoFade = 1.0f;
            if (0 == this.drawBlackForPic) {
                this.logoFade = 1.0f;
                texMgr.init_TextureSDcard(path);
                threadSleep(500);
                if (path.contains("_chromatic."))
                    this.playMode = 7;
                else
                    this.playMode = 5;
            }
            return;
        }

        // display pano picture only
        if (index_last != index) {
            this.drawBlackForPic = 4;
        }

    }

    public void _before_video(int index, int index_last) {
        String path = srcMgr.srcPathList[index];
        boolean v = true;/*path.contains(".mp4")
				|| path.contains(".MP4")
				|| path.contains(".rmvb")
				|| path.contains(".RMVB")
				|| path.contains(".3GP")
				|| path.contains(".3gp")
				|| path.contains(".mkv")
				|| path.contains(".MKV");*/
        if (!v) {
            if (mMediaPlayer != null) {
                mMediaPlayer.stop();
                mMediaPlayer.release();
                mMediaPlayer = null;
                return;
            }
        } else {
            if ((this.firstFlag) || (mMediaPlayer == null)) {
                this.playMode = init_Media(path);
            }
        }


        // draw black frame
        if (this.drawBlackForVideo > 0) {
            this.drawBlackForVideo--;
            this.playMode = 6;
            this.logoFade = 1.0f;
            if (0 == this.drawBlackForVideo) {
                this.logoFade = 1.0f;
                this.playMode = init_Media(path);
                updateTexImage();
                threadSleep(200);
            }
            return;
        }

        // choose data source
        if (mMediaPlayer != null) {
            if (index_last != index) {
                this.drawBlackForVideo = 6;
                return;
            } else {
                updateTexImage();
            }
        }

    }

    private boolean mNeedReloadState = false;
    private boolean mNeedUpdateProgressBar = false;

    private static boolean mPause = false;

    public void setPause(boolean pause) {
        this.mPause = pause;
    }

    private int mLastMode = -1;

    @Override
    public void onNewFrame(HeadTransform headTransform) {
        synchronized (this) {
            if (!mNeedRender) {
                Log.d(TAG, "it's not prepared, needn't do in onNewFrame");
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
                return;
            }
//			if (!mNeedRender) {
//				if (playMode != 6) {
//					mLastMode = playMode;
//				}
//				playMode = 6;
//			} else {
//				if (mLastMode != -1) {
//					playMode = mLastMode;
//					mLastMode = -1;
//				}
//			}
            if (mMediaPlayer != null) {
                synchronized (mMediaPlayerLock) {
                    if (mNeedReloadState) {
                        mNeedReloadState = false;
                        if (texMgr.img_play_pause[0] != 0) {
                            GLES20.glDeleteTextures(1, texMgr.img_play_pause, 0);
                        }
                        if (mMediaPlayer.isPlaying()) {
                            texMgr.img_play_pause[0] = TextureHelper.loadTexture(mContext, R.drawable.ic_pause);

                        } else {
                            texMgr.img_play_pause[0] = TextureHelper.loadTexture(mContext, R.drawable.ic_play);
                        }
                    }
                }
            }
            if (mNeedUpdateProgressBar) {
                mNeedUpdateProgressBar = false;
                if (texMgr.prograss_bar[0] != 0) {
                    GLES20.glDeleteTextures(1, texMgr.prograss_bar, 0);
                }
                texMgr.prograss_bar[0] = TextureHelper.loadTextureFromBitmap(mContext, mProgressBitmap);
            }
            initScene(headTransform);

            int index = srcMgr.getSourceIndex();
            int index_last = srcMgr.getSourceIndex_last();

            _before_rotate();
            _before_logo(index, index_last);
            _before_video(index, index_last);
            _before_pic(index, index_last);

//			chkMgr._chk_test();

            srcMgr.setSourceIndex_last(index);
            this.firstFlag = false;
        }
    }

    @Override
    public void onDrawEye(Eye eye) {
        switch (this.playMode) {
            case 1:// 2d
                renderMgr._onDrawMovieBkg(eye);
                renderMgr._onDrawCanvas(eye);
                renderMgr._onDrawLogo(eye);
                renderMgr._onDrawScene(eye);
                renderMgr._onDrawOnOffToast(eye);
                break;
            case 2:// 3d
                renderMgr._onDrawMovieBkg(eye);
                renderMgr._onDrawCanvas3d(eye);
                renderMgr._onDrawLogo(eye);
                renderMgr._onDrawScene(eye);
                renderMgr._onDrawOnOffToast(eye);
                break;
            case 3:// 360
                renderMgr._onDrawPanoVideo(eye);
                renderMgr._onDrawLogo(eye);
                renderMgr._onDrawScene(eye);
                renderMgr._onDrawOnOffToast(eye);
                break;
            case 4:// 360-3d
                renderMgr._onDrawPano3dVideo(eye);
                renderMgr._onDrawLogo(eye);
                renderMgr._onDrawScene(eye);
                renderMgr._onDrawOnOffToast(eye);
                break;
            case 5:// picture
                renderMgr._onDrawPicture(eye);
                renderMgr._onDrawLogo(eye);
                renderMgr._onDrawScene(eye);
                renderMgr._onDrawOnOffToast(eye);
                break;
            case 6:
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
                break;
            case 7: // test scene
                renderMgr._onDrawChromatic(eye);
                renderMgr._onDrawOnOffToast(eye);
                break;
            default:
                Log.e("onDrawEye", "wrong playMode value " + playMode);
                break;
        }
    }

    @Override
    public void onFinishFrame(Viewport viewport) {
        traceCall("onFinishFrame");
    }

    @Override
    public void onRendererShutdown() {
        traceCall("onRendererShutdown");
        Log.d(TAG, "CardboardRender::onRendererShutdown()");
        if (mMediaPlayer != null) {
            mMediaPlayer.pause();
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        this.mContext = null;
        this.mSphere = null;
        this.mCurveCanvas = null;
        this.mCanvas = null;
        this.texMgr = null;
        this.renderMgr = null;
        this.srcMgr = null;
        this.chkMgr = null;
        checkGlError("onRendererShutdown");
    }

    private boolean mNeedRender = true;

    private int init_Media(String path) {
        traceCall("init_Media");

        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }

        mMediaPlayer = new MediaPlayer();

//		if (mCheckManager == null) {
//		com.rockchip.vr.videoplayer.renderer.CheckManager mCheckManager = com.rockchip.vr.videoplayer.renderer.CheckManager.getInstance(mContext);
//		}
//		float result = mCheckManager.getScore(mCurrentVideoInfo.getThumbs(), mCurrentVideoInfo.getVideoSize().getWidth(), mCurrentVideoInfo.getVideoSize().getHeight());
//		Log.d("chk-test", "result is " + result);

        // as default
        int mode = this.playMode;

//		if(path.contains("2d.")){
//			mode = 1;
//		}
//		if(path.contains("3d.")){
//			mode = 2;
//		}
//		if(path.contains("360.")){
//			mode = 3;
//		}
//		if(path.contains("360_3d.")){
//			mode = 4;
//		}

		MediaMetadataRetriever mmdRetriever = null;

        try {
			mmdRetriever = new MediaMetadataRetriever();
            mmdRetriever.setDataSource(mVideoPath);
			String str = mmdRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO);
			
            mMediaPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
//			mMediaPlayer.setDataSource(path);
            mMediaPlayer.setDataSource(mVideoPath);
            mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mNeedRender = true;
                }
            });
            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    Log.d(TAG, "DirectRender::OnCompletionListener");
                    if (SharedPreference.getString(Constant.KEY_MODE_INFO, Constant.MODE_INFO_SINGLE_TUNE).equals(Constant.MODE_INFO_SINGLE)) {
                        mContext.finish();
                    } else if (SharedPreference.getString(Constant.KEY_MODE_INFO, Constant.MODE_INFO_SINGLE_TUNE).equals(Constant.MODE_INFO_ORDER)) {
                        List<VideoInfo> videoInfos = BaseApplication.getInstance().getVideoInfos();
                        if (mCurrentVideoInfo.getPath().equals(videoInfos.get(videoInfos.size() - 1).getPath())) {
                            mContext.finish();
                            return;
                        }
                        next();
                    } else if (SharedPreference.getString(Constant.KEY_MODE_INFO, Constant.MODE_INFO_SINGLE_TUNE).equals(Constant.MODE_INFO_ORDER_TUNE)) {
                        next();
                    }
                }
            });
            mMediaPlayer.prepare();
            if (SharedPreference.getString(Constant.KEY_MODE_INFO, Constant.MODE_INFO_SINGLE_TUNE).equals(Constant.MODE_INFO_SINGLE_TUNE)) {
                mMediaPlayer.setLooping(true);
            }
            if (texMgr.texture_video[0] != 0) {
                GLES20.glDeleteTextures(1, texMgr.texture_video, 0);
            } else {
                GLES20.glGenTextures(1, texMgr.texture_video, 0);
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texMgr.texture_video[0]);
                GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                        GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                        GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                        GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                        GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
                mSurfaceTexture = new SurfaceTexture(texMgr.texture_video[0]);
            }
            if (mMediaPlayer != null) {
                mMediaPlayer.setSurface(new Surface(mSurfaceTexture));
                mSurfaceTexture.setOnFrameAvailableListener(this);
            }
            mMediaPlayer.start();
            mMainHandler.sendEmptyMessageDelayed(MSG_UPDATE_PROGRASSBAR, 1000);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return mode;
    }

    public void checkGlError(String op) {
        int ret = 0;
        if ((ret = GLES20.glGetError()) != 0)
            Log.d(TAG, "checkGlError op " + op + " error " + ret);
    }

    public void clearGlError() {
        int ret = 0;
        while ((ret = GLES20.glGetError()) != 0) {
        }
    }

    public void traceCall(String name) {
        boolean flag = true;
        if (flag)
            Log.d(TAG, name);
    }

    public void threadSleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void updateTexImage() {
        //do {
            synchronized (obj) {
                if (onFrameAvailable > 0) {
                    onFrameAvailable--;
					mSurfaceTexture.getTransformMatrix(this.transformMatrix);
                    mSurfaceTexture.updateTexImage();
                    if (mCount == 5 && isFirst) {
                        isFirst = false;
						/*new Thread(new Runnable() {
							@Override
							public void run() {
								BaseApplication.getInstance().destroyActivity(VideoListActivity.class.getName());
							}
						}).start(); */
                    } else if (!isFirst) {
                        mCount = 0;
                    } else {
                        mCount++;
                    }
                }
            }
        //} while (0 == getTexWidth(texMgr.texture_video[0]));

        /*bufferWidth = getTexWidth(texMgr.texture_video[0]);
        if (videoWidth == bufferWidth)
            widthRatioToDraw = 1.0f;
        else
            widthRatioToDraw = (float) (videoWidth - 2) / (float) bufferWidth;*/
    }

    private static final int MSG_UPDATE_PROGRASSBAR = 0x00000001;
    private static final int MSG_LOAD_TEXTURE = 0x00000002;
    private static final int MSG_HIDE_UI = 0x00000003;
    private Handler mMainHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG_UPDATE_PROGRASSBAR:
                    if (mMediaPlayer != null) {
                        try {
                            int duration = mMediaPlayer.getDuration();
                            int position = mMediaPlayer.getCurrentPosition();
                            String current = stringForTime(position);
                            if (mProgressLooking < 0) {
                                mProgressBitmap = mProgressBar.setProgress((int) (position * 100.0f / duration),
                                        current, stringForTime(duration));
                            } else {
                                mProgressBitmap = mProgressBar.setProgress((int) (position * 100.0f / duration),
                                        stringForTime(position), stringForTime(duration), mProgressLooking);
                            }
                            mNeedUpdateProgressBar = true;
                            if (mMediaPlayer != null) {
                                mMainHandler.sendEmptyMessageDelayed(MSG_UPDATE_PROGRASSBAR, 1000 - (mMediaPlayer.getCurrentPosition() % 1000));
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case MSG_HIDE_UI:
                    showUI(false);
                    break;
                case MSG_LOAD_TEXTURE:
//					initPlanes();
                    texMgr.init_TextureApp();
                    initPlanes();
                    checkGlError("onSurfaceCreated");
                    break;
            }
        }
    };

    public void showUI(boolean show) {
        synchronized (mShowControl) {
            mShowControl = show;
        }
    }

    public void onClick() throws NullPointerException {
        switch (mLooking) {
            case VR.id.VIDEO_VIEW_NONE:
                Log.d(TAG, "it is click none");
                showUI(!mShowControl);
                mMainHandler.removeMessages(MSG_HIDE_UI);
                mMainHandler.sendEmptyMessageDelayed(MSG_HIDE_UI, 10 * 1000);
                break;
//			case VR.id.VIDEO_CONTROL_BG:
//				Log.d(TAG, "it is click bg");
//				break;
            case VR.id.VIDEO_CONTROL_PRO:
                Log.d(TAG, "it is click pro");
                seekTo(mProgressLooking);
                mMainHandler.removeMessages(MSG_HIDE_UI);
                mMainHandler.sendEmptyMessageDelayed(MSG_HIDE_UI, 10 * 1000);
                break;
            case VR.id.VIDEO_CONTROL_PLAY:
                Log.d(TAG, "it is click play");
                playOrPause();
                mMainHandler.removeMessages(MSG_HIDE_UI);
                mMainHandler.sendEmptyMessageDelayed(MSG_HIDE_UI, 10 * 1000);
                break;
            case VR.id.VIDEO_CONTROL_PREV:
                Log.d(TAG, "it is click prev");
                prev();
                mMainHandler.removeMessages(MSG_HIDE_UI);
                mMainHandler.sendEmptyMessageDelayed(MSG_HIDE_UI, 10 * 1000);
                break;
            case VR.id.VIDEO_CONTROL_NEXT:
                Log.d(TAG, "it is click next");
                next();
                mMainHandler.removeMessages(MSG_HIDE_UI);
                mMainHandler.sendEmptyMessageDelayed(MSG_HIDE_UI, 10 * 1000);
                break;
        }
    }

    private class RenderManager {
        public int positionHandle;
        public int mvpHandle;
        public int textureHandle;
        public int chk_textureHandle;
        public int externalHandle;
        public int widthRatioHandle;
        public int textureCoordinateHandle;
        public int widthHandle;
        public int heightHandle;
        public int fadeHandle;
        public int zHandle;
        public int yoffsetHandle;

        private float radius = 200.0f;

        public void _onDrawCanvas(Eye eye) {
            projectionMatrix = eye.getPerspective(0.5f, 100.0f);
            if (!rotateDebug)
                viewMatrix = eye.getEyeView();
            else
                viewMatrix = rotate_matrix;

            projMat = new Matrix4f(projectionMatrix);
            viewMat = new Matrix4f(viewMatrix);
            modeMat = new Matrix4f(identity_matrix);
            modeMat.translate(0f, 0f, -200f);
            modeMat.scale(355f, 200f, 200f);

            mvpMat = new Matrix4f();
            mvpMat.loadIdentity();
            mvpMat.multiply(projMat);
            mvpMat.multiply(viewMat);
            mvpMat.multiply(modeMat);
            mvpMatArray = mvpMat.getArray();

            mCurveCanvas.setShaderType(CurveCanvas.SHADER_OES_WORLD);
            mCurveCanvas.setDrawMode(0);
            mCurveCanvas.setFlipY(true);
            //mCurveCanvas.setXRatio(widthRatioToDraw);
            mCurveCanvas.setTransformMatrix(transformMatrix);
            mCurveCanvas.bindTexture(texMgr.texture_video[0]);
            mCurveCanvas.loadIdentityMat();
            mCurveCanvas.pushMat(mvpMatArray);
            mCurveCanvas.draw(eye.getType());

            checkGlError("_onDrawCanvas");
        }

        public void _onDrawCanvas3d(Eye eye) {
            projectionMatrix = eye.getPerspective(0.5f, 100.0f);
            if (!rotateDebug)
                viewMatrix = eye.getEyeView();
            else
                viewMatrix = rotate_matrix;

            projMat = new Matrix4f(projectionMatrix);
            viewMat = new Matrix4f(viewMatrix);
            modeMat = new Matrix4f(identity_matrix);
            modeMat.translate(0f, 0f, -200f);
            modeMat.scale(355f, 200f, 200f);

            mvpMat = new Matrix4f();
            mvpMat.loadIdentity();
            mvpMat.multiply(projMat);
            mvpMat.multiply(viewMat);
            mvpMat.multiply(modeMat);
            mvpMatArray = mvpMat.getArray();

            mCurveCanvas.setShaderType(CurveCanvas.SHADER_OES_WORLD);
            mCurveCanvas.setDrawMode(1);
            mCurveCanvas.setFlipY(true);
            //mCurveCanvas.setXRatio(widthRatioToDraw);
            mCurveCanvas.setTransformMatrix(transformMatrix);
            mCurveCanvas.bindTexture(texMgr.texture_video[0]);
            mCurveCanvas.loadIdentityMat();
            mCurveCanvas.pushMat(mvpMatArray);
            mCurveCanvas.draw(eye.getType());

            checkGlError("_onDrawCanvas3d");
        }

        public void _onDrawMovieBkg(Eye eye) {
			String p = SystemProperties.get("sys.vr.cinemabkg_disable","0");
			if(p.equals("1")){
				// if no draw movie bkg, need to call glClear
            	GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
				return;
			}
			
            projectionMatrix = eye.getPerspective(0.5f, 100.0f);
            if (!rotateDebug)
                viewMatrix = eye.getEyeView();
            else
                viewMatrix = rotate_matrix;

            projMat = new Matrix4f(projectionMatrix);
            viewMat = new Matrix4f(viewMatrix);
            modeMat = new Matrix4f(identity_matrix);
            modeMat.scale(radius, radius, radius);

            mvpMat = new Matrix4f();
            mvpMat.loadIdentity();
            mvpMat.multiply(projMat);
            mvpMat.multiply(viewMat);
            mvpMat.multiply(modeMat);
            mvpMatArray = mvpMat.getArray();

            mSphere.setShaderType(Sphere.SHADER_MAP_WORLD);
            mSphere.setDrawMode(0);
            //mSphere.setXRatio(1f);
            mSphere.setFlipY(false);
            mSphere.setTransformMatrix(identity_matrix);
            mSphere.bindTexture(texMgr.scene_open);
            mSphere.loadIdentityMat();
            mSphere.pushMat(mvpMatArray);
            mSphere.draw(eye.getType());

            checkGlError("_onDrawMovieBkg");
        }

        public void _onDrawPicture(Eye eye) {
            projectionMatrix = eye.getPerspective(0.5f, 100.0f);
            if (!rotateDebug)
//				viewMatrix = eye.getEyeView();
                viewMatrix = headView;
            else
                viewMatrix = rotate_matrix;

            projMat = new Matrix4f(projectionMatrix);
            viewMat = new Matrix4f(viewMatrix);
            modeMat = new Matrix4f(identity_matrix);
            modeMat.scale(radius, radius, radius);

            if (shader_type_for_pic == Sphere.SHADER_MAP_WORLD_LR_180) {
                if (1 == eye.getType())
                    viewMat.rotate(-90f - zhanghao, 0f, 1f, 0f);
                if (2 == eye.getType())
                    viewMat.rotate(90f + zhanghao, 0f, 1f, 0f);
            }

            mvpMat = new Matrix4f();
            mvpMat.loadIdentity();
            mvpMat.multiply(projMat);
            mvpMat.multiply(viewMat);
            mvpMat.multiply(modeMat);
            mvpMatArray = mvpMat.getArray();

//			mSphere.setShaderType(Sphere.SHADER_MAP_WORLD);
//			mSphere.setDrawMode(Mode.SPHERE_NORMAL_MODE);

            mSphere.setShaderType(shader_type_for_pic);
            mSphere.setDrawMode(Mode.SPHERE_NORMAL_MODE);
            //mSphere.setXRatio(1f);
            mSphere.setFlipY(false);
            mSphere.setTransformMatrix(identity_matrix);
            mSphere.bindTexture(texMgr.texture_SDcard[0]);
            mSphere.loadIdentityMat();
            mSphere.pushMat(mvpMatArray);
            mSphere.draw(eye.getType());

            checkGlError("_onDrawPicture");
        }

        public void _onDrawPano3dVideo(Eye eye) {
            projectionMatrix = eye.getPerspective(0.5f, 100.0f);
            if (!rotateDebug)
                viewMatrix = eye.getEyeView();
            else
                viewMatrix = rotate_matrix;

            projMat = new Matrix4f(projectionMatrix);
            viewMat = new Matrix4f(viewMatrix);
            modeMat = new Matrix4f(identity_matrix);
            modeMat.scale(radius, radius, radius);

            mvpMat = new Matrix4f();
            mvpMat.loadIdentity();
            mvpMat.multiply(projMat);
            mvpMat.multiply(viewMat);
            mvpMat.multiply(modeMat);
            mvpMatArray = mvpMat.getArray();

            mSphere.setShaderType(Sphere.SHADER_OES_WORLD);
            mSphere.setDrawMode(1);
            mSphere.setFlipY(true);
            //mSphere.setXRatio(widthRatioToDraw);
            mSphere.setTransformMatrix(transformMatrix);
            mSphere.bindTexture(texMgr.texture_video[0]);
            mSphere.loadIdentityMat();
            mSphere.pushMat(mvpMatArray);
            mSphere.draw(eye.getType());

            checkGlError("_onDrawPano3dVideo");
        }

        public void _onDrawPanoVideo(Eye eye) {
            projectionMatrix = eye.getPerspective(0.5f, 100.0f);
            if (!rotateDebug)
                viewMatrix = eye.getEyeView();
            else
                viewMatrix = rotate_matrix;

            projMat = new Matrix4f(projectionMatrix);
            viewMat = new Matrix4f(viewMatrix);
            modeMat = new Matrix4f(identity_matrix);
            modeMat.scale(radius, radius, radius);

            mvpMat = new Matrix4f();
            mvpMat.loadIdentity();
            mvpMat.multiply(projMat);
            mvpMat.multiply(viewMat);
            mvpMat.multiply(modeMat);
            mvpMatArray = mvpMat.getArray();

            mSphere.setShaderType(Sphere.SHADER_OES_WORLD);
            mSphere.setDrawMode(0);
            mSphere.setFlipY(true);
            //mSphere.setXRatio(widthRatioToDraw);
            mSphere.setTransformMatrix(transformMatrix);
            mSphere.bindTexture(texMgr.texture_video[0]);
            mSphere.loadIdentityMat();
            mSphere.pushMat(mvpMatArray);
            mSphere.draw(eye.getType());

            checkGlError("_onDrawPanoVideo");
        }

        public void _onDrawLogo(Eye eye) {
            if (!logoDisplay)
                return;

            if (logoFade < 0.0f)
                return;
            else if (1 == eye.getType())
                logoFade -= logoFadeSpeed;

            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

            projectionMatrix = eye.getPerspective(0.5f, 100.0f);
            if (!rotateDebug)
                viewMatrix = eye.getEyeView();
            else
                viewMatrix = rotate_matrix;

            projMat = new Matrix4f(projectionMatrix);
            viewMat = new Matrix4f(identity_matrix);
            modeMat = new Matrix4f(identity_matrix);
            modeMat.translate(0f, 0f, -200f);
            modeMat.scale(50f, 50f, 50f);

            mvpMat = new Matrix4f();
            mvpMat.loadIdentity();
            mvpMat.multiply(projMat);
            mvpMat.multiply(viewMat);
            mvpMat.multiply(modeMat);
            mvpMatArray = mvpMat.getArray();

            mCanvas.setShaderType(Plane.SHADER_MAP_WORLD);
            mCanvas.setDrawMode(0);
            mCanvas.setFade(logoFade);
            mCanvas.bindTexture(logoBinding);
            mCanvas.loadIdentityMat();
            mCanvas.pushMat(mvpMatArray);
            mCanvas.draw(eye.getType());

            checkGlError("_onDrawLogo");
        }

        public void _onDrawHalo(Eye eye) {
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

            projectionMatrix = eye.getPerspective(0.5f, 100.0f);
            if (!rotateDebug)
                viewMatrix = eye.getEyeView();
            else
                viewMatrix = rotate_matrix;

            projMat = new Matrix4f(projectionMatrix);
            viewMat = new Matrix4f(viewMatrix);
            modeMat = new Matrix4f(identity_matrix);
            float t = 3.0f;
            modeMat.scale(t, t, 1.0f);
            modeMat.translate(0.0f, 0.0f, -500.0f);

            mvpMat = new Matrix4f();
            mvpMat.loadIdentity();
            mvpMat.multiply(projMat);
            mvpMat.multiply(viewMat);
            mvpMat.multiply(modeMat);
            mvpMatArray = mvpMat.getArray();

            GLES20.glDisable(GLES20.GL_BLEND);

            checkGlError("_onDrawLogo");
        }

        public void _onDrawTest(Eye eye) {
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

            projectionMatrix = eye.getPerspective(0.5f, 100.0f);
            if (!rotateDebug)
                viewMatrix = eye.getEyeView();
            else
                viewMatrix = rotate_matrix;

            projMat = new Matrix4f(projectionMatrix);
            viewMat = new Matrix4f(viewMatrix);
            modeMat = new Matrix4f(identity_matrix);
            float t = 3.0f;
            modeMat.scale(t, t, 1.0f);
            modeMat.translate(0.0f, 0.0f, -500.0f);

            mvpMat = new Matrix4f();
            mvpMat.loadIdentity();
            mvpMat.multiply(projMat);
            mvpMat.multiply(viewMat);
            mvpMat.multiply(modeMat);
            mvpMatArray = mvpMat.getArray();

            GLES20.glDisable(GLES20.GL_BLEND);

            checkGlError("_onDrawLogo");
        }

        public void _onDrawChromatic(Eye eye) {
            projectionMatrix = eye.getPerspective(0.5f, 100.0f);
            if (!rotateDebug)
//				viewMatrix = eye.getEyeView();
                viewMatrix = identity_matrix;
            else
                viewMatrix = rotate_matrix;

            projMat = new Matrix4f(projectionMatrix);
            viewMat = new Matrix4f(viewMatrix);
            modeMat = new Matrix4f(identity_matrix);
            modeMat.translate(0f, 0f, -50f);
            modeMat.scale(355f, 200f, 200f);

            mvpMat = new Matrix4f();
            mvpMat.loadIdentity();
            mvpMat.multiply(projMat);
            mvpMat.multiply(viewMat);
            mvpMat.multiply(modeMat);
            mvpMatArray = mvpMat.getArray();

            mCurveCanvas.setShaderType(CurveCanvas.SHADER_MAP_WORLD);
            mCurveCanvas.setDrawMode(0);
            //mCurveCanvas.setXRatio(1f);
            mCurveCanvas.setTransformMatrix(identity_matrix);
            mCurveCanvas.bindTexture(texMgr.texture_SDcard[0]);
            mCurveCanvas.loadIdentityMat();
            mCurveCanvas.pushMat(mvpMatArray);
            mCurveCanvas.draw(eye.getType());

            checkGlError("_onDrawChromatic");
        }

        public void _onDrawOnOffToast(Eye eye) {
            if (tagFade < 0.0f)
                return;

            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

            projectionMatrix = eye.getPerspective(0.5f, 100.0f);
            if (!rotateDebug)
                viewMatrix = eye.getEyeView();
            else
                viewMatrix = rotate_matrix;

            projMat = new Matrix4f(projectionMatrix);
            viewMat = new Matrix4f(identity_matrix);
            modeMat = new Matrix4f(identity_matrix);
            modeMat.translate(0f, 0f, -200f);
            modeMat.scale(50f, 10f, 50f);

            mvpMat = new Matrix4f();
            mvpMat.loadIdentity();
            mvpMat.multiply(projMat);
            mvpMat.multiply(viewMat);
            mvpMat.multiply(modeMat);
            mvpMatArray = mvpMat.getArray();

            tagFade = tagFade - 0.005f;
            mCanvas.setShaderType(Plane.SHADER_MAP_WORLD);
            mCanvas.setDrawMode(0);
            mCanvas.setFade(tagFade);
            mCanvas.bindTexture(tagTexture);
            mCanvas.loadIdentityMat();
            mCanvas.pushMat(mvpMatArray);
            mCanvas.draw(eye.getType());

            checkGlError("_onDrawLogo");
        }

        public void _onDrawScene(Eye eye) {
            if (resetAndSkip > 0) {
                resetAndSkip--;
                return;
            }
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glDepthFunc(GLES20.GL_GEQUAL);

            projectionMatrix = eye.getPerspective(0.5f, 100.0f);
            if (!rotateDebug)
//				viewMatrix = eye.getEyeView();
                viewMatrix = headViewToDraw;
            else
                viewMatrix = rotate_matrix;

            for (int i = 0; i < maxPlane; i++) {
                if (true == addSceneList[i]) {
                    mPlane[i].initModel();
                    mPlane[i].initView();
                    mPlane[i].initProjection();

                    projMat = new Matrix4f(projectionMatrix);
                    if (i != 9)
                        viewMat = new Matrix4f(viewMatrix);
                    else
                        viewMat = new Matrix4f(identity_matrix);

                    modeMat = new Matrix4f(identity_matrix);
                    modeMat = mPlane[i].popModelMat();

                    if (resetPositionList && (i != 9)) {
                        if (viewMat.inverse()) {
                            resetViewMat = viewMat;
                        }
                    }

                    mPlane[i].pushView(viewMat);
                    if (i != 9) {
                        mPlane[i].pushView(resetHeadView);
                    }
                    mPlane[i].pushModel(modeMat);
                    mPlane[i].pushProjection(projMat);
                    mPlane[i].genMVP();

                    mPlane[i].setShaderType(Plane.SHADER_MAP_WORLD);
                    mPlane[i].setDrawMode(Mode.PLANE_NORMAL_MODE);
                    mPlane[i].draw(eye.getType());
                }
            }


            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            GLES20.glDisable(GLES20.GL_BLEND);

            checkGlError("_onDrawScene");
        }
    }

    private class CheckManager {
        // check similarity
        private int initW = 100;
        private int initH = 100;

        private final int ZOOM_OUT_LEVEL = 5;
        private int[] checkLeftFBO = new int[ZOOM_OUT_LEVEL];
        private int[] checkRightFBO = new int[ZOOM_OUT_LEVEL];
        private int[] checkLeftTex = new int[ZOOM_OUT_LEVEL];
        private int[] checkRightTex = new int[ZOOM_OUT_LEVEL];
        private int[] fboWidth = new int[ZOOM_OUT_LEVEL];
        private int[] fboHeight = new int[ZOOM_OUT_LEVEL];
        private boolean fboCreated = false;

        private int[] chk_texture = new int[1];

        private boolean firstFlag = true;

        public float getScore(Bitmap bitmap, boolean panorama) {
            int[] cur_fbo = new int[1];
            GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, cur_fbo, 0);

            _chk_createFBO();
            _chk_initBitmapTex(bitmap);
            if (panorama) {
                _chk_onDraw(2);
            } else {
                _chk_onDraw(1);
            }
            float score = _chk_getScore();
            Log.d("TEST", "init_Media======== panorama:" + panorama + "; score:" + score);

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, cur_fbo[0]);

            checkGlError("onNewFrame(_chk_run)");
            return score;
        }

        public float getScore(Bitmap[] bitmaps, boolean panorama) {
            float total = 0;
            for (Bitmap bitmap : bitmaps) {
                total += getScore(bitmap, panorama);
            }
            return total / bitmaps.length;
        }

        public float getScore(List<Bitmap> bitmaps, boolean panorama) {
            float total = 0;
            for (Bitmap bitmap : bitmaps) {
                total += getScore(bitmap, panorama);
            }
            return total / bitmaps.size();
        }

        private int recognizeMode() {
            return 0;
        }

        public void _chk_test() {
            int[] cur_fbo = new int[1];
            GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, cur_fbo, 0);

            _chk_createFBO();
            _chk_initBitmapTex_tmp();
            _chk_onDraw(1);
//			_chk_onDraw(2);

            Log.d("chk-test", "Socre = " + _chk_getScore());
            float result = _chk_getScore();

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, cur_fbo[0]);

            checkGlError("onNewFrame(_chk_run)");
        }

        //just for test
        public void _chk_initBitmapTex_tmp(/*Bitmap bm*/) {
            if (this.firstFlag) {
                try {
                    final BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inScaled = false; // No pre-scaling
//		            File f = new File("/sdcard/Pictures/test1.jpg");
                    File f = new File("/sdcard/Pictures/ok2.jpg");
                    final Bitmap bitmap = BitmapFactory.decodeStream(new FileInputStream(f), null, options);
                    this.chk_texture[0] = TextureHelper.loadTextureFromBitmap(mContext, bitmap);
                    this.firstFlag = false;
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }

        public void _chk_initBitmapTex(Bitmap bm) {
            if (this.chk_texture[0] != 0)
                GLES20.glDeleteTextures(1, this.chk_texture, 0);
            else
                this.chk_texture[0] = TextureHelper.loadTextureFromBitmap(mContext, bm);
        }

        public boolean _chk_isPanorama(int srcW, int srcH) {
            if ((srcW != 0) && (srcH != 0) && (srcW == (srcH * 2)))
                return true;
            else
                return false;
        }

        public void _chk_createFBO() {
            if (fboCreated)
                return;

            int w = initW / 4;
            int h = initH / 2;

            for (int i = 0; i < ZOOM_OUT_LEVEL; i++) {
                if ((i + 1) == ZOOM_OUT_LEVEL) {
                    w = 8;
                    h = 8;
                }

                fboWidth[i] = w;
                fboHeight[i] = h;

                GLES20.glGenFramebuffers(1, checkLeftFBO, i);
                GLES20.glGenTextures(1, checkLeftTex, i);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, checkLeftFBO[i]);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, checkLeftTex[i]);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
                GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, checkLeftTex[i], 0);

                GLES20.glGenFramebuffers(1, checkRightFBO, i);
                GLES20.glGenTextures(1, checkRightTex, i);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, checkRightFBO[i]);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, checkRightTex[i]);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
                GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, checkRightTex[i], 0);

                w = w / 2;
                h = h / 2;
            }

            fboCreated = true;

        }

        public void _chk_onDraw(int mode) {
            for (int i = 0; i < ZOOM_OUT_LEVEL; i++) {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, checkLeftFBO[i]);
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
                GLES20.glViewport(0, 0, fboWidth[i], fboHeight[i]);
                GLES20.glScissor(0, 0, fboWidth[i], fboHeight[i]);
                if (0 == i)
                    mCanvas.bindTexture(this.chk_texture[0]);
                else
                    mCanvas.bindTexture(this.checkLeftTex[i - 1]);
                mCanvas.setShaderType(Plane.SHADER_MAP_PLANE);
                mCanvas.setFade(1f);
                mCanvas.setDrawMode(mode);
                mCanvas.loadIdentityMat();
                mCanvas.draw(1);
                GLES20.glFinish();

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, checkRightFBO[i]);
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
                GLES20.glViewport(0, 0, fboWidth[i], fboHeight[i]);
                GLES20.glScissor(0, 0, fboWidth[i], fboHeight[i]);
                if (0 == i)
                    mCanvas.bindTexture(this.chk_texture[0]);
                else
                    mCanvas.bindTexture(this.checkRightTex[i - 1]);
                mCanvas.setShaderType(Plane.SHADER_MAP_PLANE);
                mCanvas.setFade(1f);
                mCanvas.setDrawMode(mode);
                mCanvas.loadIdentityMat();
                mCanvas.draw(2);
                GLES20.glFinish();
            }
        }

        public float _chk_getScore() {
            return getSimilarity(checkLeftFBO[ZOOM_OUT_LEVEL - 1], checkRightFBO[ZOOM_OUT_LEVEL - 1], 8, 8);
            //		return getSimilarity(checkLeftFBO[4],checkRightFBO[4],fboWidth[4],fboHeight[4]);
            //		return getSimilarity(checkLeftFBO[3],checkRightFBO[3],fboWidth[3],fboHeight[3]);
            //		return getSimilarity(checkLeftFBO[2],checkRightFBO[2],fboWidth[2],fboHeight[2]);
            //		return getSimilarity(checkLeftFBO[1],checkRightFBO[1],fboWidth[1],fboHeight[1]);
            //		return getSimilarity(checkLeftFBO[0],checkRightFBO[0],fboWidth[0],fboHeight[0]);
        }

    }

    private int OnOff = 0;
    private int tagTexture = 0;
    private float tagFade = 0f;
    private boolean isFirst = true;
    private long mCount = 0;

    public void enableToast(int mode) {
        OnOff = mode;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        synchronized (obj) {
            onFrameAvailable++;
        }
    }

    public native int getTexWidth(int texture);

    public native int getTexHeight(int texture);

    public native float getSimilarity(int fbo1, int fbo2, int w, int h);

    public native int SetCpuGpuLevel(int cpuLevel, int gpuLevel);

    static {
        System.loadLibrary("vrplayer-jni");
    }

}
