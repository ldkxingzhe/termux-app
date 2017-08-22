package com.termux.embedded;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.termux.R;
import com.termux.app.TermuxPreferences;
import com.termux.app.TermuxService;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

/**
 * A Service for embedded in other app
 * Created by ldkxingzhe@163.com on 2017/8/22.
 */
public class TerminalEmbeddedService extends Service{
    @SuppressWarnings("unused")
    private static final String TAG = "TerminalEmbeddedService";

    private TermuxService mTermuxService;
    private ArrayMap<String, TerminalSession> mSessionName2Session = new ArrayMap<>();
    private ArrayMap<String, ResultReceiver> mSessionName2Callback = new ArrayMap<>();
    private TerminalView mTerminalView;
    private RootView mRootView;
    private TermuxPreferences mSettings;
    private WindowManager mWindowManager;

    private Handler mHandler;
    private final Object mLock = new Object();

    @Override
    public void onCreate() {
        super.onCreate();
        bindService(new Intent(this, TermuxService.class), mServiceConnection, BIND_AUTO_CREATE);
        mHandler = new Handler();
        mSettings = new TermuxPreferences(this);
        mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // for ipc
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        String sessionName = intent.getStringExtra("sessionName");
        if (!TextUtils.isEmpty(sessionName)){
            destroySession(sessionName);
        }
        return super.onUnbind(intent);
    }

    private void makeSureViewExit(){
        if (mRootView == null){
            mRootView = new RootView(this);
            mRootView.setBackgroundColor(Color.BLUE);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            params.width = 500;
            params.height = 500;
            params.gravity = Gravity.START|Gravity.TOP;
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            mWindowManager.addView(mRootView, params);
        }
        if (mTerminalView == null){
            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mTerminalView  = (TerminalView) inflater.inflate(R.layout.terminal_full_parent, mRootView, false);
            mTerminalView.setTextSize(mSettings.getFontSize());
            mRootView.addView(mTerminalView);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        if (mSessionName2Session.size() > 0){
            Log.e(TAG, "onDestroy, but has un closed session: " + mSessionName2Session);
        }
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mTermuxService = ((TermuxService.LocalBinder)service).service;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mTermuxService = null;
            Log.e(TAG, "onServiceDisconnected, and name " + name);
        }
    };

    private TerminalSession createNewSession(String sessionName){
        TerminalSession terminalSession = mTermuxService.createTermSession(null, null, null, false);
        if (sessionName != null){
            terminalSession.mSessionName = sessionName;
        }
        return terminalSession;
    }

    private TerminalSession findSession(String sessionName){
        TerminalSession session = mSessionName2Session.get(sessionName);
        if (session == null){
            Log.e(TAG, "session is null, and sessionName is " + sessionName);
        }
        return session;
    }

    // called from main thread
    private void attachSessionAndVisible(@NonNull TerminalSession session){
        mTerminalView.attachSession(session);
        mRootView.setVisibility(View.VISIBLE);
    }

    private void onSessionDestroyed(@NonNull TerminalSession session){
        synchronized (mLock){
            mTermuxService.removeTermSession(session);
            if (mSessionName2Session.size() == 0){
                // no session now
                if (mRootView != null){
                    mWindowManager.removeView(mRootView);
                }
                stopSelf();
            }
        }
    }

    private Binder mBinder = new ITerminalEmbedded.Stub() {
        @Override
        public void writeToTerminal(String sessionName, final String command) throws RemoteException {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Log.v(TAG, "writeToTerminal command: " + command);
                    mTerminalView.sendTextToTerminal(command);
                }
            });
        }

        @Override
        public void onCreate(final String sessionName, final ResultReceiver resultReceiver) throws RemoteException {
            synchronized (mLock){
                if (mSessionName2Session.get(sessionName) != null) {
                    throw new RemoteException("sessionName has been created");
                }
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    synchronized (mLock){
                        TerminalSession session = createNewSession(sessionName);
                        mSessionName2Session.put(sessionName, session);
                        mSessionName2Callback.put(sessionName, resultReceiver);
                    }
                }
            });
        }

        @Override
        public void onVisible(String sessionName) throws RemoteException {
            synchronized (mLock){
                final TerminalSession session = findSession(sessionName);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        makeSureViewExit();
                        attachSessionAndVisible(session);
                    }
                });
            }
        }

        @Override
        public void onInVisible(String sessionName) throws RemoteException {
            synchronized (mLock){
                TerminalSession session = findSession(sessionName);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mRootView.setVisibility(View.GONE);
                    }
                });
            }
        }

        @Override
        public void onDestroyed(String sessionName) throws RemoteException {
            destroySession(sessionName);
        }
    };

    private void destroySession(String sessionName) {
        synchronized (mLock){
            final TerminalSession session = findSession(sessionName);
            if (session == null) return;
            mSessionName2Session.remove(sessionName);
            mSessionName2Callback.remove(sessionName);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    onSessionDestroyed(session);
                }
            });
        }
    }

    // just like rootView in activity
    private class RootView extends FrameLayout{

        public RootView(@NonNull Context context) {
            super(context);
            requestFocus();
            setFocusable(true);
            setFocusableInTouchMode(false);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return false;
        }
    }
}
