package com.ws.livegif.livegif;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by WangShuo on 2016/8/18.
 */
public class FrameAnimation extends SurfaceView implements SurfaceHolder.Callback {

    private SurfaceHolder mSurfaceHolder;

    private boolean mIsThreadRunning = false; // 线程运行开关
    public static boolean mIsDestroy = false;// 是否已经销毁
    public static boolean isRepetitionStart = false;//是否重复开始动画
    public static boolean isQueuePlayMode = true;//是否队列播放模式true
    public static boolean isEndFrameUnlockCanvas = false;//是否最后一帧的UnlockCanvas

    private int[] mBitmapResourceIds;// 用于播放动画的图片资源id数组
    private ArrayList<String> mBitmapResourcePaths;// 用于播放动画的图片资源path数组
    private List<String> mBitmapAssetFiles;// 外界传入播放动画的图片资源Asset数组
    private List<String> mIsAssetFiles;// 真正用于播放动画的图片资源Asset数组
    private String assetFile;// 外界传入
    private String mIsAssetFile;// 真正用于
    private int totalCount;//资源总数
    private int mIsotalCount;//资源总数
    private Canvas mCanvas;
    private Bitmap mBitmap;// 显示的图片

    private int mCurrentIndext;// 当前动画播放的位置
    private int mGapTime = 60;// 每帧动画的间隔
    private int mIsGapTime = 60;// 真正每帧动画的间隔
    private int queueTime = 1000;// 队列模式时每个动画的间隔
    private OnFrameFinishedListener mOnFrameFinishedListener;// 动画监听事件
    private Thread drawThread;
    private AssetManager assetManager;
    private BlockingQueue assetFileQueue;//阻塞队列 只支持mBitmapAssetFiles模式
    private BlockingQueue blockingQueue;//阻塞队列 只支持mBitmapAssetFiles模式
    private BlockingQueue totalCountQueue;//阻塞队列 只支持mBitmapAssetFiles模式
    private BlockingQueue gapTimeQueue;//阻塞队列 只支持mBitmapAssetFiles模式
    private final Object frameAnimation = new Object();

    public FrameAnimation(Context context) {
        this(context, null);
        initView(context);
    }

    public FrameAnimation(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView(context);
    }

    public FrameAnimation(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        initView(context);

    }

    private void initView(Context context) {
        mSurfaceHolder = this.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setFormat(PixelFormat.TRANSLUCENT);
        this.setZOrderOnTop(true);

        assetManager = context.getAssets();
        blockingQueue = new LinkedBlockingQueue<List<String>>();
        assetFileQueue = new LinkedBlockingQueue<String>();
        totalCountQueue = new LinkedBlockingQueue<Integer>();
        gapTimeQueue = new LinkedBlockingQueue<Integer>();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mIsDestroy = false;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // 当surfaceView销毁时, 停止线程的运行. 避免surfaceView销毁了线程还在运行而报错.
        destory();
        mIsDestroy = true;
    }


    /**
     * 开始动画
     */
    public void start() {
       synchronized (frameAnimation) {
               if (mIsThreadRunning && mBitmapAssetFiles != null) {//start后 如果正在渲染就先加入队列
                   Log.e("wangshuo", "startFramestrategy  mIsThreadRunning");
                   addBlockingQueue(mBitmapAssetFiles);
                   return;
               } else {
                   if (mBitmapAssetFiles != null) {
                       Log.e("wangshuo", "startFramestrategy  mBitmapAssetFiles != null");
                       mIsAssetFile = assetFile;
                       mIsotalCount = totalCount;
                       mIsAssetFiles = mBitmapAssetFiles;
                       mIsGapTime = mGapTime;
                   }
               }
           run();
       }
    }

    /**
     * 开始动画线程
     */
    private void run() {
        if (!mIsDestroy) {
            mCurrentIndext = 0;
            mIsThreadRunning = true;
            drawThread = new Thread() {
                @Override
                public void run() {
                    super.run();
                    if (mOnFrameFinishedListener != null) {
                        mOnFrameFinishedListener.onStart();
                    }
                    Log.e("wangshuo", "run  mIsThreadRunning="+ mIsThreadRunning);
                    while (mIsThreadRunning) {
                        long now = System.currentTimeMillis();
                        drawView();
                        try {
                            //Log.e("wangshuo","解码图片时间："+(System.currentTimeMillis() - now));
                            Thread.sleep(mIsGapTime - (System.currentTimeMillis() - now) > 0 ? mIsGapTime - (System.currentTimeMillis() - now) : 0);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    if (mOnFrameFinishedListener != null) {
                        mOnFrameFinishedListener.onStop();
                    }
                }
            };
            drawThread.start();
        } else {
            // 如果SurfaceHolder已经销毁抛出该异常
            try {
                throw new Exception("IllegalArgumentException:Are you sure the SurfaceHolder is not destroyed");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 暂停动画
     */
    public void pause() {
        mIsThreadRunning = false;
    }

    /**
     * 结束动画,清掉线程
     */
    public void stop() {
        mIsThreadRunning = false;
        mBitmapResourceIds = null;
        mBitmapResourcePaths = null;
        mBitmapAssetFiles = null;
        mIsAssetFiles = null;
        if(mCanvas != null) {
            mCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        }
        if(drawThread != null && !drawThread.isInterrupted()) {
            drawThread.interrupt();
        }
        if (mOnFrameFinishedListener != null) {
            mOnFrameFinishedListener.onStop();
        }
    }



    /**
     * 销毁一切
     */
    public void destory() {
      stop();
      blockingQueue.clear();
      assetFileQueue.clear();
      totalCountQueue.clear();
      gapTimeQueue.clear();
    }

    /**
     * 设置每帧时间
     */
    public void setGapTime(int gapTime) {
        synchronized (frameAnimation) {
            this.mGapTime = gapTime;
        }
    }

    /**
     * 队列模式时设置每个动画执行完之间的间隔
     */
    public void setQueueTime(int Queuetime) {
        synchronized (frameAnimation) {
            this.queueTime = Queuetime;
        }
    }

    /**
     * 返回是否在动画中
     */
    public boolean getDrawingState()
    {
        return mIsThreadRunning;
    }

    /**
     * 继续动画
     */
    public void reStart() {
        mIsThreadRunning = true;
    }

    /**
     * 是否重复循环动画  默认false
     */
    public void setRepetition(boolean bool) {
        isRepetitionStart = bool;
    }

    /**
     * 是否队列播放模式  默认true
     */
    public void setQueuePlayMode(boolean bool) {
        isQueuePlayMode = bool;
    }

    /**
     * 设置动画监听器
     */
    public void setOnFrameFinisedListener(OnFrameFinishedListener onFrameFinishedListener) {
        this.mOnFrameFinishedListener = onFrameFinishedListener;
    }

    /**
     * 设置动画播放素材的id
     *
     * @param bitmapResourceIds 图片资源id
     */
    public void setBitmapResoursID(int[] bitmapResourceIds) {
        synchronized (frameAnimation) {
            this.mBitmapResourceIds = null;
            this.mBitmapResourceIds = bitmapResourceIds;
            totalCount = bitmapResourceIds.length;
        }
    }

    /**
     * 设置动画播放素材的Asset
     *
     * @param assetFile
     */
    public void setBitmapAssetFile(String assetFile) {
        synchronized (frameAnimation) {
            this.mBitmapAssetFiles = null;
            this.assetFile = null;
            try {
                this.assetFile = assetFile;
                this.mBitmapAssetFiles =  Arrays.asList(assetManager.list(assetFile));
                totalCount = mBitmapAssetFiles.size();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 设置动画播放素材的路径 eg: File file =new File(Environment.getExternalStorageDirectory()+"/aa");
     *
     * @param file
     */
    public void setmBitmapResourcePath(File file) {
        final List<String> mPathList= new ArrayList<>();
        if(file.isDirectory())
        {
            File[] files=file.listFiles();
            for(File mFrameFile:files)
            {
                mPathList.add(mFrameFile.getAbsolutePath());
            }
        }
        if(mPathList==null||mPathList.size()<=1)
        {
            Log.d("wangshuo","mPathList==null");
            return;
        }
        File filess=new File(mPathList.get(0));
        if(!filess.exists())
        {
            Log.d("wangshuo","!filess.exists()");
            return;
        }
        setmBitmapResourcePath((ArrayList) mPathList);
    }

    /**
     * 设置动画播放素材的路径
     *
     * @param bitmapResourcePaths
     */
    public void setmBitmapResourcePath(ArrayList bitmapResourcePaths) {
        synchronized (frameAnimation) {
            this.mBitmapResourcePaths = null;
            this.mBitmapResourcePaths = bitmapResourcePaths;
            totalCount = bitmapResourcePaths.size();
        }
    }


    /**
     * 制图方法
     */
    private void drawView() {
        // 无资源文件退出
        if (mBitmapResourceIds == null && mBitmapResourcePaths == null && mIsAssetFiles == null) {
            Log.e("wangshuo", "the bitmap is null");
            mIsThreadRunning = false;
            return;
        }

        // 锁定画布
        if(mSurfaceHolder != null){
            mCanvas = mSurfaceHolder.lockCanvas();
            isEndFrameUnlockCanvas = false;
        }
        try {
            if (mSurfaceHolder != null && mCanvas != null) {

                mCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

                 if(mIsAssetFile !=null && mIsAssetFiles != null && mIsAssetFiles.size()>0){
                    InputStream is = assetManager.open(mIsAssetFile+"/"+mIsAssetFiles.get(mCurrentIndext));
                    mBitmap = BitmapFactory.decodeStream(is);
                }
                else if (mBitmapResourceIds != null && mBitmapResourceIds.length > 0)
                    mBitmap = BitmapFactory.decodeResource(getResources(), mBitmapResourceIds[mCurrentIndext]);
                else if (mBitmapResourcePaths != null && mBitmapResourcePaths.size() > 0) {
                    mBitmap = BitmapFactory.decodeFile(mBitmapResourcePaths.get(mCurrentIndext));
                }

                Paint paint = new Paint();
                paint.setAntiAlias(true);
                paint.setStyle(Paint.Style.STROKE);
                Rect mSrcRect, mDestRect;
                mSrcRect = new Rect(0, 0, mBitmap.getWidth(), mBitmap.getHeight());
                mDestRect = new Rect(0, 0, getWidth(), getHeight());
                mCanvas.drawBitmap(mBitmap, mSrcRect, mDestRect, paint);

                // 播放到最后一张图片
                if (mCurrentIndext == mIsotalCount - 1 ) {
                    endFramestrategy();
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {

            mCurrentIndext++;
            // 播放到最后一张图片
            if(mCurrentIndext >= mIsotalCount){
                endFramestrategy();
            }
            if (mCanvas != null) {
                // 将画布解锁并显示在屏幕上
                if(mSurfaceHolder!=null && !isEndFrameUnlockCanvas){
                    mSurfaceHolder.unlockCanvasAndPost(mCanvas);
                }
            }

            if (mBitmap != null) {
                // 收回图片
                mBitmap.recycle();
                mBitmap = null;
            }
        }
    }

    //播放到最后一帧的策略
    private void endFramestrategy(){
        synchronized (frameAnimation) {
            Log.e("wangshuo", "endFramestrategy");
            if (isRepetitionStart) {//循环播放
                Log.e("wangshuo", "endFramestrategy isRepetitionStart");
                mCurrentIndext = 0;
            } else if (isQueuePlayMode) {//队列播放
                if (mCanvas != null && mSurfaceHolder != null && !isEndFrameUnlockCanvas) {
                    mCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                    mSurfaceHolder.unlockCanvasAndPost(mCanvas);
                    isEndFrameUnlockCanvas = true;
                }
                this.mIsAssetFiles = null;
                mCurrentIndext = 0;
                this.mIsAssetFiles = takeBlockingQueue();
                Log.e("wangshuo", "takeBlockingQueue mIsAssetFiles= " + mIsAssetFiles.toString());
                try {
                    Thread.sleep(queueTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                stop();
            }
        }
    }

   //加入队列
    private void addBlockingQueue(List<String> bitmapAssetFiles){
        synchronized (frameAnimation) {
            if(bitmapAssetFiles != null && assetFile != null){
                try {
                    Log.e("wangshuo", "addBlockingQueue");
                    assetFileQueue.put(assetFile);
                    totalCountQueue.put(totalCount);
                    gapTimeQueue.put(mGapTime);
                    blockingQueue.put(bitmapAssetFiles);//start一次 加入队列一次
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //从队列中取出 获取最新
    private List<String> takeBlockingQueue(){
        synchronized (frameAnimation) {
            try {
                if(!blockingQueue.isEmpty() && !assetFileQueue.isEmpty()){
                    Log.e("wangshuo", "takeBlockingQueue take");
                    mIsAssetFile = (String) assetFileQueue.take();
                    mIsotalCount = (int) totalCountQueue.take();
                    mIsGapTime = (int) gapTimeQueue.take();
                    return (List<String>) blockingQueue.take();
                }else {
                    Log.e("wangshuo", "takeBlockingQueue stop");
                    stop();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }
    }


    /**
     * 动画监听器
     *
     * @author qike
     */
    public interface OnFrameFinishedListener {

        /**
         * 动画开始
         */
        void onStart();

        /**
         * 动画结束
         */
        void onStop();
    }

    /**
     * 当用户点击返回按钮时，停止线程，反转内存溢出
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 当按返回键时，将线程停止，避免surfaceView销毁了,而线程还在运行而报错
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            mIsThreadRunning = false;
        }

        return super.onKeyDown(keyCode, event);
    }


}