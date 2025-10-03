package com.archos.mediacenter.video.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import org.courville.supernova.IAdditionalService;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdditionalServiceSingleton {

    private static final String TAG = "AdditionalServiceSingleton";
    private static final String SERVICE_ACTION = "org.courville.supernova.AdditionalService";
    private static final String SERVICE_PACKAGE = "org.courville.supernova";

    private static final Logger log = LoggerFactory.getLogger(AdditionalServiceSingleton.class);

    private static AdditionalServiceSingleton sInstance;
    private IAdditionalService mService;
    private boolean mIsBound;
    private List<WeakReference<OnConnected>> mOnConnectedListeners = new ArrayList<>();
    private List<WeakReference<OnDisconnected>> mOnDisconnectedListeners = new ArrayList<>();
    public interface OnConnected {
        void onConnected();
    }

    public interface OnDisconnected {
        void onDisconnected();
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            log.debug("SuperNOVA: onServiceConnected");
            mService = IAdditionalService.Stub.asInterface(service);
            mIsBound = true;
            for (WeakReference<OnConnected> ref : mOnConnectedListeners) {
                OnConnected listener = ref.get();
                if (listener != null) {
                    listener.onConnected();
                }
            }
            mOnConnectedListeners.removeIf(ref -> ref.get() == null);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            log.debug("SuperNOVA: onServiceDisconnected");
            mService = null;
            mIsBound = false;
            for (WeakReference<OnDisconnected> ref : mOnDisconnectedListeners) {
                OnDisconnected listener = ref.get();
                if (listener != null) {
                    listener.onDisconnected();
                }
            }
            mOnDisconnectedListeners.removeIf(ref -> ref.get() == null);
        }
    };

    private AdditionalServiceSingleton() {
    }

    public static synchronized AdditionalServiceSingleton getInstance() {
        if (sInstance == null) {
            sInstance = new AdditionalServiceSingleton();
        }
        return sInstance;
    }

    public void bindToService(Context context) {
        if (!mIsBound) {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("org.courville.supernova", "org.courville.supernova.NovaAiService"));
            try {
                boolean res = context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
                log.debug("Trying to bind supernova gave " + res);
            } catch (SecurityException e) {
                log.error("Failed binding to supernova", e);
            }
        }
    }

    public void bindToService(Context context, OnConnected onConnected, OnDisconnected onDisconnected) {
        if (onConnected != null) {
            mOnConnectedListeners.add(new WeakReference<>(onConnected));
        }
        if (onDisconnected != null) {
            mOnDisconnectedListeners.add(new WeakReference<>(onDisconnected));
        }
        if (mIsBound && onConnected != null) {
            onConnected.onConnected();
        }
        bindToService(context);
    }

    public void unbindFromService(Context context) {
        if (mIsBound) {
            context.unbindService(mConnection);
            mIsBound = false;
        }
    }

    public static IAdditionalService getService() {
        return getInstance().mService;
    }

    public IAdditionalService getServiceBlocking(int timeout) {
        while (timeout-- > 0) {
            if (mService != null) {
                return mService;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return mService;
    }

    public boolean isBound() {
        return mIsBound;
    }
}
