package com.example.huangqi.offscreentest;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.EGL14;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;

import org.wysaid.common.Common;
import org.wysaid.nativePort.CGEFrameRenderer;
import org.wysaid.texUtils.TextureRenderer;
import org.wysaid.texUtils.TextureRendererDrawOrigin;

import java.nio.IntBuffer;

/**
 * Created by huangqi on 2017/12/12.
 */

public class GLRenderer extends GLThread implements SurfaceTexture.OnFrameAvailableListener {
  public static final String LOG_TAG = "GLRenderer";

  private int mTextureID;
  private SurfaceTexture mSurfaceTexture;
  private float[] mTransformMatrix = new float[16];

  private CGEFrameRenderer mRenderer;

  public CameraInstance cameraInstance() {
    return CameraInstance.getInstance();
  }

  public void startPreview() {
    postRunnable(new Runnable() {
      @Override
      public void run() {
        if (!cameraInstance().isCameraOpened()) {

          int facing = Camera.CameraInfo.CAMERA_FACING_FRONT;

          cameraInstance().tryOpenCamera(new CameraInstance.CameraOpenCallback() {
            @Override
            public void cameraReady() {
              Log.i(LOG_TAG, "tryOpenCamera OK...");
            }
          }, facing);
        }

        if (!cameraInstance().isPreviewing()) {
          cameraInstance().startPreview(mSurfaceTexture);
          mRenderer.srcResize(cameraInstance().previewHeight(), cameraInstance().previewWidth());
        }

        requestRender();
      }
    });
  }

  public void stopPreview() {
    cameraInstance().stopPreview();
    requestRender();
  }

  public void setFilter(final String config) {
    postRunnable(new Runnable() {
      @Override
      public void run() {
        if (mRenderer != null) {
          mRenderer.setFilterWidthConfig(config);
        }
      }
    });
  }

  @Override
  public void onCreated() {
    GLES20.glDisable(GLES20.GL_DEPTH_TEST);
    GLES20.glDisable(GLES20.GL_STENCIL_TEST);
    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

    int texSize[] = new int[1];

    GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, texSize, 0);

    mTextureID = Common.genSurfaceTextureID();
    mSurfaceTexture = new SurfaceTexture(mTextureID);
    mSurfaceTexture.setOnFrameAvailableListener(this);

    // FIXME: make temp offscreen surface
    GLSurface temp = new GLSurface(500, 500);
    makeOutputSurface(temp);
    EGL14.eglMakeCurrent(eglDisplay, temp.eglSurface, temp.eglSurface, eglContext);

    mRenderer = new CGEFrameRenderer();
    mRenderer.init(500, 500, 500, 500);

    mRenderer.setSrcRotation((float) (Math.PI / 2.0));
    mRenderer.setSrcFlipScale(1.0f, -1.0f);
    mRenderer.setRenderFlipScale(1.0f, -1.0f);

  }

  @Override
  public void onUpdate() {
    // 将相机图像流转为GL外部纹理
    mSurfaceTexture.updateTexImage();
    mSurfaceTexture.getTransformMatrix(mTransformMatrix);

    mRenderer.update(mTextureID, mTransformMatrix);
    mRenderer.runProc();

    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
  }

  @Override
  public void onDestroy() {
    mRenderer.release();
    mRenderer = null;
    GLES20.glDeleteTextures(1, new int[]{mTextureID}, 0);
    mTextureID = 0;
    mSurfaceTexture.release();
    mSurfaceTexture = null;
  }

  @Override
  public void onDrawFrame(GLSurface outputSurface) {
    GLSurface.Viewport viewport = outputSurface.getViewport();
    mRenderer.render(viewport.x, viewport.y, viewport.width, viewport.height);
    // TODO: draw to view
    // TODO: add filter
  }

  @Override
  public void onFrameAvailable(SurfaceTexture surfaceTexture) {
    this.requestRender();
  }
}
