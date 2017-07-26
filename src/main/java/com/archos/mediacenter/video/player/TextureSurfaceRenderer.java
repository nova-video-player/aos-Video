// Copyright 2017 Archos SA
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.archos.mediacenter.video.player;

import android.graphics.SurfaceTexture;
import android.opengl.GLUtils;
import android.util.Log;

import javax.microedition.khronos.egl.*;

/**
 * Renderer which initializes OpenGL 2.0 context on a passed surface and starts a rendering thread
 *
 * This class has to be subclassed to be used properly
 */
public abstract class TextureSurfaceRenderer implements Runnable
{
    private static final int EGL_OPENGL_ES2_BIT = 4;
    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
    private static final String LOG_TAG = "TextureSurfaceRenderer";
    protected SurfaceTexture texture;
    private EGL10 egl;
    private EGLDisplay eglDisplay;
    private EGLContext eglContext;
    private EGLSurface eglSurface;

    protected int width;
    protected int height;
    private boolean running;
    private boolean pause;

    /**
     * @param texture Surface texture on which to render. This has to be called AFTER the texture became available
     * @param width Width of the passed surface
     * @param height Height of the passed surface
     */
    public TextureSurfaceRenderer()
    {
        this.running = true;
        this.pause = false;
    }

    public void setTexture(SurfaceTexture texture, int width, int height)
    {
        this.texture = texture;
        this.width = width;
        this.height = height;
        Thread thrd = new Thread(this);
        thrd.start();
    }

    @Override
    public void run()
    {
        initGL();
        initGLComponents();
        Log.d(LOG_TAG, "OpenGL init OK.");

        while (running)
        {
            long loopStart = System.currentTimeMillis();
            if (!pause) {
                pingFps();

                if (draw())
                {
                    egl.eglSwapBuffers(eglDisplay, eglSurface);
                }
            }

            long waitDelta = 16 - (System.currentTimeMillis() - loopStart);    // Targeting 60 fps, no need for faster
            if (waitDelta > 0)
            {
                try
                {
                    Thread.sleep(waitDelta);
                }
                catch (InterruptedException e)
                {
                    continue;
                }
            }
        }

        deinitGLComponents();
        deinitGL();
    }
    
    void stop() {
        running = false;
    }

    void pause() {
        pause = true;
    }

    void play() {
        pause = false;
    }

    /**
     * Main draw function, subclass this and add custom drawing code here. The rendering thread will attempt to limit
     * FPS to 60 to keep CPU usage low.
     */
    protected abstract boolean draw();

    /**
     * OpenGL component initialization funcion. This is called after OpenGL context has been initialized on the rendering thread.
     * Subclass this and initialize shaders / textures / other GL related components here.
     */
    protected synchronized void initGLComponents() {};
    protected synchronized void deinitGLComponents() {};

    private long lastFpsOutput = 0;
    private int frames;
    private void pingFps()
    {
        if (lastFpsOutput == 0)
            lastFpsOutput = System.currentTimeMillis();

        frames ++;

        if (System.currentTimeMillis() - lastFpsOutput > 1000)
        {
            Log.d(LOG_TAG, "FPS: " + frames);
            lastFpsOutput = System.currentTimeMillis();
            frames = 0;
        }
    }


    /**
     * Call when activity pauses. This stops the rendering thread and deinitializes OpenGL.
     */
    public void onPause()
    {
        running = false;
    }


    private void initGL()
    {
        egl = (EGL10) EGLContext.getEGL();
        eglDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

        int[] version = new int[2];
        egl.eglInitialize(eglDisplay, version);

        EGLConfig eglConfig = chooseEglConfig();
        eglContext = createContext(egl, eglDisplay, eglConfig);

        eglSurface = egl.eglCreateWindowSurface(eglDisplay, eglConfig, texture, null);

        if (eglSurface == null || eglSurface == EGL10.EGL_NO_SURFACE)
        {
            throw new RuntimeException("GL Error: " + GLUtils.getEGLErrorString(egl.eglGetError()));
        }

        if (!egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext))
        {
            throw new RuntimeException("GL Make current error: " + GLUtils.getEGLErrorString(egl.eglGetError()));
        }
    }

    private void deinitGL()
    {
        egl.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
        egl.eglDestroySurface(eglDisplay, eglSurface);
        egl.eglDestroyContext(eglDisplay, eglContext);
        egl.eglTerminate(eglDisplay);
        Log.d(LOG_TAG, "OpenGL deinit OK.");
    }

    private EGLContext createContext(EGL10 egl, EGLDisplay eglDisplay, EGLConfig eglConfig)
    {
        int[] attribList = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE };
        return egl.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, attribList);
    }

    private EGLConfig chooseEglConfig()
    {
        int[] configsCount = new int[1];
        EGLConfig[] configs = new EGLConfig[1];
        int[] configSpec = getConfig();

        if (!egl.eglChooseConfig(eglDisplay, configSpec, configs, 1, configsCount))
        {
            throw new IllegalArgumentException("Failed to choose config: " + GLUtils.getEGLErrorString(egl.eglGetError()));
        }
        else if (configsCount[0] > 0)
        {
            return configs[0];
        }

        return null;
    }

    private int[] getConfig()
    {
        return new int[] {
                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 0,
                EGL10.EGL_STENCIL_SIZE, 0,
                EGL10.EGL_NONE
        };
    }

    @Override
    protected void finalize() throws Throwable
    {
        super.finalize();
        running = false;
    }
}