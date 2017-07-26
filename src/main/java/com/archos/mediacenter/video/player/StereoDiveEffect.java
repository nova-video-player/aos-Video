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

import android.app.Activity;
import android.content.Context;
import android.hardware.SensorManager;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class StereoDiveEffect extends VideoEffect {
    private final static String TAG="StereoDiveEffect";
    
    private final static boolean CHECK_GL_ERRORS = false;
    private final static boolean DEBUG_VIEWPORT = false;
    
    private final static boolean FISH_EYE_CORRECTION = true;
    private final static boolean HEAD_TRACKING = false;
    
    private DisplayMetrics mDpm = null;
    
    private int mEyeGLProgram;
    private int mUIOverlayProgram;
    private int mSceneGLProgram;
    private int mTexHandle;
    private int mTexCoordHandle;
    private int mTriangleVerticesHandle;
    private int mProjectionHandle;
    private int mTransformHandle;
    private int mColorFilterHandle;
    private int mMVPTransformHandle;
    private float[] mVideoTransform;
    private float[] mHeadTransform;
    private float[] mHeadOriginTransform;
    
    private float[] mAngleOrigin;
 
    private float[] mCamera;
    private float[] mPersp;
    private float[] mMVP;
    private float[] mColorFilter;
    
    private static final float[] CLEAR_COLOR_FILTER = { 1.0f, 1.0f, 1.0f, 1.0f };
    private static final float[] RED_COLOR_FILTER = { 0.0f, 1.0f, 1.0f, 1.0f };
    private static final float[] CYAN_COLOR_FILTER = { 1.0f, 0.0f, 0.0f, 1.0f };

    private float[] mRightEyePos;
    private float[] mLeftEyePos;

    private int mVideoWidth;
    private int mVideoHeight;
    
    private int mViewWidth = 1;
    private int mViewHeight = 1;

    private Context mActivity;
    private int mOrientation;

    private static final int HORIZONTAL_GRANULARITY = 1;
    private static final int VERTICAL_GRANULARITY = 1;

    private static final float SCREEN_FAR = 200.0f; //25m from the camera
    private static final float SCREEN_WIDTH = 180.0f; //18m width screen

    private static final float EYE_FOV = 90.0f;
    
    private static final float DISTORTION_COEF_ARCHOS = 1.2f;
    private static final float DISTORTION_COEF_DIVE = 1.4f;
    
    private static final float DISTORTION_COEF = DISTORTION_COEF_ARCHOS;

    private float IPD = 6.0f;

    private final static String EYE_VERTEX_SHADER =
        "attribute vec4 vPosition;\n" +
        "attribute vec2 a_texCoord;\n" +
        "varying vec2 v_texCoord;\n" +
        "uniform mat4 u_xform;\n" +
        "uniform mat4 u_mvp_xform;\n" +
        "void main() {\n" +
        "  gl_Position = u_mvp_xform * vPosition;\n" +
        "  v_texCoord = vec2(u_xform * vec4(a_texCoord, 1.0, 1.0));\n" +
        "}\n";

    private final static String EYE_FRAGMENT_SHADER =
        "#extension GL_OES_EGL_image_external : require\n" +
        "precision mediump float;\n" +
        "uniform samplerExternalOES s_texture;\n" +
        "uniform vec4 u_filter;\n" +
        "varying vec2 v_texCoord;\n" +
        "void main() {\n" +
        "  gl_FragColor = texture2D(s_texture, v_texCoord) * u_filter;\n" +
        "}\n";

    private final static String SCENE_VERTEX_SHADER =
        "attribute vec4 vPosition;\n" +
        "attribute vec2 a_texCoord;\n" +
        "varying vec2 v_texCoord;\n" +
        "uniform mat4 u_xform;\n" +
        "void main() {\n" +
        "  gl_Position = vPosition;\n" +
        "  v_texCoord = a_texCoord;\n" +
        "}\n";
        
    private final static String SCENE_FRAGMENT_SHADER =
        "precision highp float;\n" +
        "varying vec2 v_texCoord;\n" +
        "uniform sampler2D s_texture;\n" +
        "uniform vec2 u_projectionRatio;\n" +
        "void main() {\n" +
        "  vec2 uCenter = vec2(0.25, 0.5); \n" +
        "  if (v_texCoord.x > 0.5) { \n" +
        "      uCenter.x += 0.5;\n" +
        "  } \n" +
        "  vec2 radiusCoordDisto = v_texCoord - uCenter;\n" +
        "  float dist = length(radiusCoordDisto * u_projectionRatio);\n " +
        "  float teta = atan(dist); \n" +
        "  vec2 newUV = radiusCoordDisto * dist/teta;\n" +
        "  vec2 undiCoord =  newUV + uCenter;\n" +
        "  gl_FragColor = texture2D(s_texture, undiCoord);\n" +
        "}\n";

    private final static int FLOAT_SIZE_BYTES = 4;
    private static final int GL_TEXTURE_EXTERNAL_OES = 0x8D65;

    private FloatBuffer mVideoTextureCoordRight;
    private FloatBuffer mVideoTextureCoordLeft;
    private FloatBuffer mVideoTextureFinalScene;
    private FloatBuffer mUITextureCoord;
    private FloatBuffer mVertices;
    private FloatBuffer mVerticesFinalScene;

    private int mVideoTexture;
    private int mUIOverlayTexture;
    private int mFBOTexture;

    private int[] textures = new int[3];

    public StereoDiveEffect(Context context) {
        mDpm = new DisplayMetrics();
        mActivity = context;
        getWindowManager(context).getDefaultDisplay().getMetrics(mDpm);
        generateVideoVertices();
        generateVideoTextures();
        generateUITextures();
        
        mCamera = new float[16];
        mPersp = new float[16];
        mMVP = new float[16];
        mHeadTransform = new float[16];
        mAngleOrigin = new float[3];
        
        Matrix.setIdentityM(mHeadTransform, 0);
        Matrix.perspectiveM(mPersp, 0, EYE_FOV, 1.0f, 0.0f, -(1.5f*SCREEN_FAR));
        mRightEyePos = new float[] { 0.0f, -IPD/2.0f, 0.0f };
        mLeftEyePos = new float[] { 0.0f, IPD/2.0f, 0.0f };
    }
    
    public int getUIMode() {
        switch (getEffectMode()) {
            case VideoEffect.STEREO_2D_MODE:
            case VideoEffect.SBS_MODE:
            case VideoEffect.TB_MODE:
            case VideoEffect.ANAGLYPH_MODE:
            //on dive, always display as sbs
                return VideoEffect.SBS_MODE;
            case VideoEffect.NORMAL_2D_MODE:
            default:
                return VideoEffect.NORMAL_2D_MODE;
        }
    }
    
    private static int getScreenOrientation(Context context) {
        int rotation = getWindowManager(context).getDefaultDisplay().getRotation();
        return ((8-rotation+1)%4);
    }

    private static WindowManager getWindowManager(Context context) {
        return context instanceof  Activity?((Activity)context).getWindowManager():(WindowManager)context.getSystemService(context.WINDOW_SERVICE);
    }

    private void updateCurrentRotation() {
        mOrientation = getScreenOrientation(mActivity);
    }
    
    private void generateVideoVertices() {
        float[] Vert;
        if (getEffectMode() != VideoEffect.NORMAL_2D_MODE) {
            Vert = generateTriangleFanQuad(SCREEN_WIDTH*mVideoHeight/mVideoWidth * mViewWidth/(2*mViewHeight), -SCREEN_WIDTH, 
                                          -SCREEN_WIDTH*mVideoHeight/mVideoWidth * mViewWidth/(2*mViewHeight), SCREEN_WIDTH,
                                          -SCREEN_FAR,
                                          VERTICAL_GRANULARITY, HORIZONTAL_GRANULARITY);
        } else {
            Vert = generateTriangleFanQuad(1.0f, -1.0f, 
                                          -1.0f, 1.0f,
                                          -0.0f,
                                          VERTICAL_GRANULARITY, HORIZONTAL_GRANULARITY);
        }
        mVertices = ByteBuffer.allocateDirect( Vert.length * FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mVertices.put(Vert).position(0);

        if (FISH_EYE_CORRECTION) {
            float[] VertFinal = generateTriangleFanQuad(1.0f, -1.0f, 
                                                  -1.0f, 1.0f,
                                                  -0.0f,
                                                   1, 1);
            mVerticesFinalScene = ByteBuffer.allocateDirect( VertFinal.length * FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
            mVerticesFinalScene.put(VertFinal).position(0);
        }
    }

    private float[] generateTextureTriangleFanQuad(float top, float left, float bottom, float right, int vDiv, int hDiv) {
        float[] strip = new float[4*(vDiv)*(hDiv+1)];
        float vStep = (top - bottom)/vDiv;
        float hStep = (right - left)/vDiv;
        for (int i=0; i < vDiv; i++) {
            for (int j=0; j<=hDiv; j++) {
                int k = i*4*(hDiv+1)+4*j;
                strip[k] = strip[k+2] = right - (i%2)*(right - left) + (2*(i%2)-1)*j*hStep;
                strip[k+1] = top - i*vStep;
                strip[k+3] = top - (i+1)*vStep;
            }
        }
        return strip;
    }

    private float[] generateTriangleFanQuad(float top, float left, float bottom, float right, float far, int vDiv, int hDiv) {
        float[] strip = new float[6*(vDiv)*(hDiv+1)];
        float vStep = (top - bottom)/vDiv;
        float hStep = (right - left)/vDiv;
        for (int i=0; i < vDiv; i++) {
            for (int j=0; j<=hDiv; j++) {
                int k = i*6*(hDiv+1)+6*j;
                strip[k] = strip[k+3] = right - (i%2)*(right - left) + (2*(i%2)-1)*j*hStep;
                strip[k+1] = top - i*vStep;
                strip[k+4] = top - (i+1)*vStep;
                strip[k+2] = strip[k+5] = far;
            }
        }
        return strip;
    }

    private void generateUITextures() {
        float[] T = generateTextureTriangleFanQuad(1.0f, 0.0f, 0.0f, 1.0f, VERTICAL_GRANULARITY, HORIZONTAL_GRANULARITY);
        mUITextureCoord = ByteBuffer.allocateDirect( T.length * FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mUITextureCoord.put(T).position(0);
    }

    private void generateVideoTextures() {
        float[] T1 = null;
        float[] T2 = null;

        switch (getEffectMode()) {
            case VideoEffect.SBS_MODE :
                T1 = generateTextureTriangleFanQuad(1.0f, 0.0f, 0.0f, 0.5f, VERTICAL_GRANULARITY, HORIZONTAL_GRANULARITY); //Left part
                T2 = generateTextureTriangleFanQuad(1.0f, 0.5f, 0.0f, 1.0f, VERTICAL_GRANULARITY, HORIZONTAL_GRANULARITY); // Right part
            break;
            case VideoEffect.TB_MODE:
                T1 = generateTextureTriangleFanQuad(1.0f, 0.0f, 0.5f, 1.0f, VERTICAL_GRANULARITY, HORIZONTAL_GRANULARITY); //Top part
                T2 = generateTextureTriangleFanQuad(0.5f, 0.0f, 0.0f, 1.0f, VERTICAL_GRANULARITY, HORIZONTAL_GRANULARITY); //Bottom part
            break;
            case VideoEffect.ANAGLYPH_MODE:
                T1=T2=generateTextureTriangleFanQuad(1.0f, 0.0f, 0.0f, 1.0f, VERTICAL_GRANULARITY, HORIZONTAL_GRANULARITY); //Full texture
            default:
                //initialize texture in case of booting in 2D Mode - Otherwise, let's keep old one to display in fullscreen
                if ((mVideoTextureCoordLeft == null) || (mVideoTextureCoordRight == null)) {
                    T1=T2=generateTextureTriangleFanQuad(1.0f, 0.0f, 0.0f, 1.0f, VERTICAL_GRANULARITY, HORIZONTAL_GRANULARITY); //Full texture
                }
        }

        if ((T1 != null) && (T2 != null)) {
            mVideoTextureCoordLeft = ByteBuffer.allocateDirect( T1.length * FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
            mVideoTextureCoordRight = ByteBuffer.allocateDirect( T2.length * FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
            mVideoTextureCoordLeft.put(T1).position(0);
            mVideoTextureCoordRight.put(T2).position(0);
        }

        if (FISH_EYE_CORRECTION) {
            float[] TFull = generateTextureTriangleFanQuad(1.0f, 0.0f, 0.0f, 1.0f, 1, 1); //Full texture
            mVideoTextureFinalScene = ByteBuffer.allocateDirect( TFull.length * FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
            mVideoTextureFinalScene.put(TFull).position(0);
        }
    }

    public void initGLComponents() {
        mEyeGLProgram = OpenGLUtils.createProgram(EYE_VERTEX_SHADER, EYE_FRAGMENT_SHADER);
        mUIOverlayProgram = OpenGLUtils.createProgram(EYE_VERTEX_SHADER, EYE_FRAGMENT_SHADER);
        if (FISH_EYE_CORRECTION) {
            mSceneGLProgram = OpenGLUtils.createProgram(SCENE_VERTEX_SHADER, SCENE_FRAGMENT_SHADER);
            GLES20.glGenTextures(3, textures, 0);
        }
        else
            GLES20.glGenTextures(2, textures, 0);
        mVideoTexture = textures[0];
        mUIOverlayTexture = textures[1];

        if (FISH_EYE_CORRECTION) {
            mFBOTexture = textures[2];
            
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
            if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glActiveTexture");
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFBOTexture);
            
            // set texture parameters
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
        }

        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("initialization");
        
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("setup");
        
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, mVideoTexture);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("setup");

        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("setup");
        
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, mUIOverlayTexture);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("setup");
        
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glFrontFace(GLES20.GL_CW);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
    }
    
    public void deinitGLComponents() {
        if (mVideoTexture != 0) {
            if (FISH_EYE_CORRECTION)
                GLES20.glDeleteTextures(3, textures, 0);
            else {
                GLES20.glDeleteTextures(2, textures, 0);
                mFBOTexture = 0;
            }
            mVideoTexture = 0;
            mUIOverlayTexture = 0;
        }
        
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("deinitialization of textures");
        GLES20.glDeleteProgram(mEyeGLProgram);
        GLES20.glDeleteProgram(mUIOverlayProgram);
        if (FISH_EYE_CORRECTION)
            GLES20.glDeleteProgram(mSceneGLProgram);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("deinitialization of program");
    }
    
    public int getVideoTexture() {
        return mVideoTexture;
    }
    
    public int getUIOverlayTexture() {
        return mUIOverlayTexture;
    }
    
    public void setVideoTransform(float[] mTransform) {
        mVideoTransform = mTransform;
    }
    
    public void setHeadTransform(float[] mTransform) {
        if (mTransform != null && HEAD_TRACKING) {
               mHeadTransform=mTransform.clone();
               if (mHeadOriginTransform == null) {
                   mHeadOriginTransform = mHeadTransform.clone();
                   SensorManager.getOrientation(mHeadOriginTransform, mAngleOrigin);
               }
               //Be sure that user front is head front
               Matrix.rotateM(mHeadTransform, 0, (float)-Math.toDegrees(mAngleOrigin[2]), 0.0f, 1.0f, 0.0f);
        }        
    }
    
    private void resetHeadPosition() {
        mHeadOriginTransform = null;
    }
    
    public void onPlay() {
        resetHeadPosition();
    }

    public void setVideoSize(int videoWidth, int videoHeight, double aspect) {
        mVideoWidth = videoWidth;
        mVideoHeight = videoHeight;
        generateVideoVertices();
        generateVideoTextures();
    }
    
    public void setViewPort(int width, int height) {
        mViewWidth = width;
        mViewHeight = height;
        generateVideoVertices();
        generateVideoTextures();
    }
    
    private void drawScene() {
        GLES20.glUniform4fv(mColorFilterHandle, 1, mColorFilter, 0);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glUniform4fv");
    
        GLES20.glUniformMatrix4fv(mMVPTransformHandle, 1, false, mMVP, 0);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glUniformMatrix4fv");
                
        GLES20.glEnableVertexAttribArray(mTexCoordHandle);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glEnableVertexAttribArray");
        
        GLES20.glEnableVertexAttribArray(mTriangleVerticesHandle);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glEnableVertexAttribArray");
        
        GLES20.glVertexAttribPointer(mTriangleVerticesHandle, 3, GLES20.GL_FLOAT,
                false, 0, mVertices);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glVertexAttribPointer");

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 2 * (VERTICAL_GRANULARITY) * (HORIZONTAL_GRANULARITY + 1));
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glDrawArrays");

        GLES20.glDisableVertexAttribArray(mTexCoordHandle);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glDisableVertexAttribArray");

        GLES20.glDisableVertexAttribArray(mTriangleVerticesHandle);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glDisableVertexAttribArray");

    }
    
    private void setupMonoEye() {
        Matrix.setIdentityM(mMVP, 0);
        GLES20.glDisable(GLES20.GL_BLEND);
        
        GLES20.glScissor(0, 0, mViewWidth, mViewHeight);
        GLES20.glViewport(0, 0, mViewWidth, mViewHeight);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glViewport");

        GLES20.glVertexAttribPointer(mTexCoordHandle, 2, GLES20.GL_FLOAT,
                false, 0, mVideoTextureCoordRight);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glVertexAttribPointer");
    }

    private void drawEye() {
        if (DEBUG_VIEWPORT)
            GLES20.glClearColor(0.0f,0.0f,1.0f,1.0f);
        else
            GLES20.glClearColor(0.0f,0.0f,0.0f,0.0f);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glClearColor");
        
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glClear");

        GLES20.glUniform1i(mTexHandle, 0);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glUniform1i");
    }

    private void drawUIOverlay() {
        mColorFilter = CLEAR_COLOR_FILTER;
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        
        GLES20.glUniform1i(mTexHandle, 1);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glUniform1i");
        
        GLES20.glVertexAttribPointer(mTexCoordHandle, 2, GLES20.GL_FLOAT,
                false, 0, mUITextureCoord);
    }
    
    private void setupRightEye() {
        Matrix.setLookAtM(mCamera, 0, mRightEyePos[0], mRightEyePos[1], mRightEyePos[2], 0.0f, 0.0f, -SCREEN_FAR, 0.0f, 1.0f, 0.0f);
        if (HEAD_TRACKING) Matrix.rotateM(mCamera, 0, (float)mOrientation*90.0f, 0.0f, 0.0f, -1.0f);
        Matrix.multiplyMM(mMVP, 0, mCamera, 0, mHeadTransform, 0);
        Matrix.multiplyMM(mMVP, 0, mPersp, 0, mMVP, 0);
        
        GLES20.glDisable(GLES20.GL_BLEND);
        
        if (getEffectMode() == VideoEffect.ANAGLYPH_MODE) {
            mColorFilter = RED_COLOR_FILTER;
        }

        GLES20.glScissor(mViewWidth/2, 0, mViewWidth/2, mViewHeight);
        GLES20.glViewport(mViewWidth / 2, 0, mViewWidth / 2, mViewHeight);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glViewport");

        GLES20.glVertexAttribPointer(mTexCoordHandle, 2, GLES20.GL_FLOAT,
                false, 0, mVideoTextureCoordRight);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glVertexAttribPointer");
    }

    private void setupLeftEye() {
        Matrix.setLookAtM(mCamera, 0, mLeftEyePos[0], mLeftEyePos[1], mLeftEyePos[2], 0.0f, 0.0f, -SCREEN_FAR, 0.0f, 1.0f, 0.0f);
        if (HEAD_TRACKING) Matrix.rotateM(mCamera, 0, (float)mOrientation*90.0f, 0.0f, 0.0f, -1.0f);
        Matrix.multiplyMM(mMVP, 0, mCamera, 0, mHeadTransform, 0);
        Matrix.multiplyMM(mMVP, 0, mPersp, 0, mMVP, 0);

        GLES20.glDisable(GLES20.GL_BLEND);
        
        if (getEffectMode() == VideoEffect.ANAGLYPH_MODE) {
            mColorFilter = CYAN_COLOR_FILTER;
        }

        GLES20.glScissor(0, 0, mViewWidth / 2, mViewHeight);
        GLES20.glViewport(0, 0, mViewWidth / 2, mViewHeight);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glViewport");

        GLES20.glVertexAttribPointer(mTexCoordHandle, 2, GLES20.GL_FLOAT,
                false, 0, mVideoTextureCoordLeft);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glVertexAttribPointer");
    }

    private void drawFinalScene() {
        GLES20.glUseProgram(mSceneGLProgram);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glUseProgram");

        mTexHandle = GLES20.glGetUniformLocation(mSceneGLProgram, "s_texture");
        mTexCoordHandle = GLES20.glGetAttribLocation(mSceneGLProgram, "a_texCoord");
        mTriangleVerticesHandle = GLES20.glGetAttribLocation(mSceneGLProgram, "vPosition");
        mProjectionHandle = GLES20.glGetUniformLocation(mSceneGLProgram, "u_projectionRatio");
        
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        GLES20.glViewport(0, 0, mViewWidth, mViewHeight);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glViewport");

        GLES20.glUniform1i(mTexHandle, 2);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glUniform1i");
        
        GLES20.glUniform2f(mProjectionHandle, DISTORTION_COEF, (DISTORTION_COEF * mViewHeight /(mViewWidth/2.0f)));

        GLES20.glEnableVertexAttribArray(mTexCoordHandle);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glEnableVertexAttribArray");
        GLES20.glVertexAttribPointer(mTexCoordHandle, 2, GLES20.GL_FLOAT,
                false, 0, mVideoTextureFinalScene);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glVertexAttribPointer");

        GLES20.glEnableVertexAttribArray(mTriangleVerticesHandle);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glEnableVertexAttribArray");
        GLES20.glVertexAttribPointer(mTriangleVerticesHandle, 3, GLES20.GL_FLOAT,
                false, 0, mVerticesFinalScene);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glVertexAttribPointer");

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glDrawArrays");

        GLES20.glDisableVertexAttribArray(mTexCoordHandle);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glDisableVertexAttribArray");

        GLES20.glDisableVertexAttribArray(mTriangleVerticesHandle);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glDisableVertexAttribArray");
    }

    private void setupPreScene(int glProgram) {
        GLES20.glUseProgram(glProgram);
        mTexHandle = GLES20.glGetUniformLocation(glProgram, "s_texture");
        mTexCoordHandle = GLES20.glGetAttribLocation(glProgram, "a_texCoord");
        mTriangleVerticesHandle = GLES20.glGetAttribLocation(glProgram, "vPosition");
        mTransformHandle = GLES20.glGetUniformLocation(glProgram, "u_xform");
        mMVPTransformHandle = GLES20.glGetUniformLocation(glProgram, "u_mvp_xform");
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glUseProgram");

        GLES20.glUniformMatrix4fv(mTransformHandle, 1, false, mVideoTransform, 0);
        if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glUniformMatrix4fv");

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        
        mColorFilterHandle = GLES20.glGetUniformLocation(glProgram, "u_filter");
        mColorFilter = CLEAR_COLOR_FILTER;
    }

    public void draw() {
        if (getEffectMode() == VideoEffect.NORMAL_2D_MODE) {
            setupPreScene(mEyeGLProgram);
            setupMonoEye();
            drawEye();
            drawScene();
            setupPreScene(mUIOverlayProgram);
            drawUIOverlay();
            drawScene();
        } else {
            updateCurrentRotation();
            setupPreScene(mEyeGLProgram);
            setupRightEye();
            drawEye();
            drawScene();
            setupPreScene(mUIOverlayProgram);
            drawUIOverlay();
            drawScene();
            setupPreScene(mEyeGLProgram);
            setupLeftEye();
            drawEye();
            drawScene();
            setupPreScene(mUIOverlayProgram);
            drawUIOverlay();
            drawScene();

            if (FISH_EYE_CORRECTION) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFBOTexture);
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mViewWidth, mViewHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
                if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glTexImage2D");
            
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFBOTexture);
                GLES20.glCopyTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 0, 0, mViewWidth, mViewHeight, 0);
            
                GLES20.glScissor(0, 0, mViewWidth, mViewHeight);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
                if (CHECK_GL_ERRORS) OpenGLUtils.checkGlError("glClear");
                drawFinalScene();
            }
        }
    }
    
    public void setEffectMode(int mode) {
        super.setEffectMode(mode & VideoEffect.EFFECT_CLASS_MODE);
        generateVideoVertices();
        generateVideoTextures();
    }
    
    @Override
    public int getEffectType() {
        return VideoEffect.EFFECT_STEREO_SPLIT;
    }

}