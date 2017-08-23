package com.termux.embedded;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.util.Log;
import android.view.View;

/**
 * A Manager for communicate with Termux App
 * Created by ldkxingzhe@163.com on 2017/8/22.
 */
public final class TerminalHelper {
    @SuppressWarnings("unused")
    private static final String TAG = "TerminalHelper";

    private final String mSessionName;
    private ITerminalEmbedded mITerminalEmbedded;

    public TerminalHelper(Context context, String shortName){
        mSessionName = context.getPackageName().concat(shortName);
        Intent intent = new Intent("com.termux.embedded.action");
        intent.putExtra("sessionName", mSessionName);
        intent.setPackage("com.termux");
        boolean bindResult = context.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
        Log.v(TAG, "bindService and result is " + bindResult);
    }

    public void replaceView(final View replaceView){
        replaceViewInternal(replaceView);
        replaceView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                replaceViewInternal(replaceView);
            }
        });
    }

    private void replaceViewInternal(View replaceView){
        int[] location = new int[2];
        replaceView.getLocationOnScreen(location);
        int x = location[0], y = location[1];
        try {
            mITerminalEmbedded.layoutView(mSessionName, x, y, replaceView.getMeasuredWidth(), replaceView.getMeasuredHeight());
        } catch (RemoteException e) {
            dealWithException(e);
        }
    }

    public void onVisible(){
        if (mITerminalEmbedded != null){
            try {
                mITerminalEmbedded.onVisible(mSessionName);
            } catch (RemoteException e) {
                dealWithException(e);
            }
        }
    }

    public void onInVisible(){
        if (mITerminalEmbedded != null){
            try {
                mITerminalEmbedded.onInVisible(mSessionName);
            } catch (RemoteException e) {
                dealWithException(e);
            }
        }
    }

    private void dealWithException(Throwable throwable){
        Log.e(TAG, throwable.getMessage(), throwable);
    }


    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mITerminalEmbedded = ITerminalEmbedded.Stub.asInterface(service);
            try {
                mITerminalEmbedded.onCreate(mSessionName, mResultReceiver);
            } catch (RemoteException e) {
                dealWithException(e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected " + name);
            mITerminalEmbedded = null;
        }
    };

    private ResultReceiver mResultReceiver = new ResultReceiver(new Handler(Looper.getMainLooper())){
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {

        }
    };

    public void onDestroy(Context context){
        if (mITerminalEmbedded != null){
            try {
                mITerminalEmbedded.onDestroyed(mSessionName);
            } catch (RemoteException e) {
                dealWithException(e);
            }
        }
        context.unbindService(mServiceConnection);
    }
}
