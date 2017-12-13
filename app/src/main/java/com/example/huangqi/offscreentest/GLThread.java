package com.example.huangqi.offscreentest;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import static android.opengl.EGL14.EGL_NO_SURFACE;

/**
 * Created by huangqi on 2017/12/11.
 */


public abstract class GLThread extends Thread {
  private static final String TAG = "GLThread";
  private EGLConfig eglConfig = null;
  protected EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
  protected EGLContext eglContext = EGL14.EGL_NO_CONTEXT;

  private ArrayBlockingQueue<Event> eventQueue;
  private final List<GLSurface> outputSurfaces;
  private boolean rendering;
  private boolean isRelease;

  public GLThread() {
    setName("GLRenderer-" + getId());
    outputSurfaces = new ArrayList<>();
    rendering = false;
    isRelease = false;

    eventQueue = new ArrayBlockingQueue<>(100);
  }

  protected boolean makeOutputSurface(GLSurface surface) {
    // 创建Surface缓存
    try {
      switch (surface.type) {
        case GLSurface.TYPE_WINDOW_SURFACE: {
          final int[] attributes = {EGL14.EGL_NONE};
          // 创建失败时返回EGL14.EGL_NO_SURFACE
          surface.eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface.surface, attributes, 0);
          break;
        }
        case GLSurface.TYPE_PBUFFER_SURFACE: {
          final int[] attributes = {
            EGL14.EGL_WIDTH, surface.viewport.width,
            EGL14.EGL_HEIGHT, surface.viewport.height,
            EGL14.EGL_NONE};
          // 创建失败时返回EGL14.EGL_NO_SURFACE
          surface.eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, attributes, 0);
          break;
        }
        case GLSurface.TYPE_PIXMAP_SURFACE: {
          Log.w(TAG, "nonsupport pixmap surface");
          return false;
        }
        default:
          Log.w(TAG, "surface type error " + surface.type);
          return false;
      }
    } catch (Exception e) {
      Log.w(TAG, "can't create eglSurface");
      surface.eglSurface = EGL_NO_SURFACE;
      return false;
    }

    return true;
  }

  public void addSurface(@NonNull final GLSurface surface) {
    Event event = new Event(Event.ADD_SURFACE);
    event.param = surface;
    if (!eventQueue.offer(event))
      Log.e(TAG, "queue full");
  }

  public void removeSurface(@NonNull final GLSurface surface) {
    Event event = new Event(Event.REMOVE_SURFACE);
    event.param = surface;
    if (!eventQueue.offer(event))
      Log.e(TAG, "queue full");
  }

  /**
   * 开始渲染
   * 启动线程并等待初始化完毕
   */
  public void startRender() {
    if (!eventQueue.offer(new Event(Event.START_RENDER)))
      Log.e(TAG, "queue full");
    if (getState() == State.NEW) {
      super.start(); // 启动渲染线程
    }
  }

  public void stopRender() {
    if (!eventQueue.offer(new Event(Event.STOP_RENDER)))
      Log.e(TAG, "queue full");
  }

  public boolean postRunnable(@NonNull Runnable runnable) {
    Event event = new Event(Event.RUNNABLE);
    event.param = runnable;
    if (!eventQueue.offer(event)) {
      Log.e(TAG, "queue full");
      return false;
    }

    return true;
  }

  /**
   *
   */
  @Override
  public void start() {
    Log.w(TAG, "Don't call this function");
  }

  public void requestRender() {
    eventQueue.offer(new Event(Event.REQ_RENDER));
  }

  /**
   * 创建OpenGL环境
   */
  private void createGL() {
    // 获取显示设备(默认的显示设备)
    eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
    // 初始化
    int[] version = new int[2];
    if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
      throw new RuntimeException("EGL error " + EGL14.eglGetError());
    }
    // 获取FrameBuffer格式和能力
    int[] configAttribs = {
      EGL14.EGL_BUFFER_SIZE, 32,
      EGL14.EGL_RED_SIZE, 8,
      EGL14.EGL_GREEN_SIZE, 8,
      EGL14.EGL_BLUE_SIZE, 8,
      EGL14.EGL_ALPHA_SIZE, 8,
      // EGL14.EGL_DEPTH_SIZE, 8,
      // EGL14.EGL_STENCIL_SIZE, 0,
      EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
      EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
      EGL14.EGL_NONE
    };
    int[] numConfigs = new int[1];
    EGLConfig[] configs = new EGLConfig[1];
    if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, configs.length, numConfigs, 0)) {
      throw new RuntimeException("EGL error " + EGL14.eglGetError());
    }
    eglConfig = configs[0];
    // 创建OpenGL上下文(可以先不设置EGLSurface，但EGLContext必须创建，
    // 因为后面调用GLES方法基本都要依赖于EGLContext)
    int[] contextAttribs = {
      EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
      EGL14.EGL_NONE
    };
    eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0);
    if (eglContext == EGL14.EGL_NO_CONTEXT) {
      throw new RuntimeException("EGL error " + EGL14.eglGetError());
    }
    // 设置默认的上下文环境和输出缓冲区(小米4上如果不设置有效的eglSurface后面创建着色器会失败，可以先创建一个默认的eglSurface)
    // EGL14.eglMakeCurrent(eglDisplay, surface.eglSurface, surface.eglSurface, eglContext);
    EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, eglContext);
  }

  /**
   * 销毁OpenGL环境
   */
  private void destroyGL() {
    EGL14.eglDestroyContext(eglDisplay, eglContext);
    eglContext = EGL14.EGL_NO_CONTEXT;
    eglDisplay = EGL14.EGL_NO_DISPLAY;
  }

  /**
   * 渲染到各个eglSurface
   */
  private void render() {
    // 渲染(绘制)
    for (GLSurface output : outputSurfaces) {
      if (output.eglSurface == EGL_NO_SURFACE) {
        if (!makeOutputSurface(output))
          continue;
      }
      // 设置当前的上下文环境和输出缓冲区
      EGL14.eglMakeCurrent(eglDisplay, output.eglSurface, output.eglSurface, eglContext);
      // 设置视窗大小及位置
      GLES20.glViewport(output.viewport.x, output.viewport.y, output.viewport.width, output.viewport.height);
      // 绘制
      onDrawFrame(output);
      // 交换显存(将surface显存和显示器的显存交换)
      EGL14.eglSwapBuffers(eglDisplay, output.eglSurface);
    }
  }

  @Override
  public void run() {
    Event event;

    Log.d(TAG, getName() + ": render create");
    createGL();
    onCreated();
    // 渲染
    while (!isRelease) {
      try {
        event = eventQueue.take();
        switch (event.event) {
          case Event.ADD_SURFACE: {
            // 创建eglSurface
            GLSurface surface = (GLSurface) event.param;
            Log.d(TAG, "add:" + surface);
            makeOutputSurface(surface);
            outputSurfaces.add(surface);
            break;
          }
          case Event.REMOVE_SURFACE: {
            GLSurface surface = (GLSurface) event.param;
            Log.d(TAG, "remove:" + surface);
            EGL14.eglDestroySurface(eglDisplay, surface.eglSurface);
            outputSurfaces.remove(surface);

            break;
          }
          case Event.START_RENDER:
            rendering = true;
            break;
          case Event.REQ_RENDER: // 渲染
            if (rendering) {
              onUpdate();
              render(); // 如果surface缓存没有释放(被消费)那么这里将卡住
            }
            break;
          case Event.STOP_RENDER:
            rendering = false;
            break;
          case Event.RUNNABLE:
            ((Runnable) event.param).run();
            break;
          case Event.RELEASE:
            isRelease = true;
            break;
          default:
            Log.e(TAG, "event error: " + event);
            break;
        }
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    // 回调
    onDestroy();
    // 销毁eglSurface
    for (GLSurface outputSurface : outputSurfaces) {
      EGL14.eglDestroySurface(eglDisplay, outputSurface.eglSurface);
      outputSurface.eglSurface = EGL_NO_SURFACE;
    }
    destroyGL();
    eventQueue.clear();
    Log.d(TAG, getName() + ": render release");
  }

  /**
   * 退出OpenGL渲染并释放资源
   * 这里先将渲染器释放(renderer)再退出looper，因为renderer里面可能持有这个looper的handler，
   * 先退出looper再释放renderer可能会报一些警告信息(sending message to a Handler on a dead thread)
   */
  public void release() {
    if (eventQueue.offer(new Event(Event.RELEASE))) {
      // 等待线程结束，如果不等待，在快速开关的时候可能会导致资源竞争(如竞争摄像头)
      // 但这样操作可能会引起界面卡顿，择优取舍
      while (isAlive()) {
        try {
          this.join(1000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
  }

  /**
   * 当创建完基本的OpenGL环境后调用此方法，可以在这里初始化纹理之类的东西
   */
  public abstract void onCreated();

  /**
   * 在渲染之前调用，用于更新纹理数据。渲染一帧调用一次
   */
  public abstract void onUpdate();

  /**
   * 绘制渲染，每次绘制都会调用，一帧数据可能调用多次(不同是输出缓存)
   *
   * @param outputSurface 输出缓存位置surface
   */
  public abstract void onDrawFrame(GLSurface outputSurface);

  /**
   * 当渲染器销毁前调用，用户回收释放资源
   */
  public abstract void onDestroy();


  private static String getEGLErrorString() {
    return GLUtils.getEGLErrorString(EGL14.eglGetError());
  }

  private static class Event {
    static final int ADD_SURFACE = 1; // 添加输出的surface
    static final int REMOVE_SURFACE = 2; // 移除输出的surface
    static final int START_RENDER = 3; // 开始渲染
    static final int REQ_RENDER = 4; // 请求渲染
    static final int STOP_RENDER = 5; // 结束渲染
    static final int RUNNABLE = 6; //
    static final int RELEASE = 7; // 释放渲染器

    final int event;
    Object param;

    Event(int event) {
      this.event = event;
    }
  }
}
