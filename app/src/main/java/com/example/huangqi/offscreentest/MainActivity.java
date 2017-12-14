package com.example.huangqi.offscreentest;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
  static final String TAG = "MainActivity";

  // Used to load the 'native-lib' library on application startup.
  static {
    System.loadLibrary("native-lib");
  }

  private GLRenderer render;

  private class HolderCallBack implements SurfaceHolder.Callback {
    private GLSurface mGLSurface;

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
      mGLSurface = new GLSurface(holder.getSurface(), holder.getSurfaceFrame().width(), holder.getSurfaceFrame().height());
      render.addSurface(mGLSurface);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
      Log.d(TAG, String.format("surfaceChanged width: %d height: %d", width, height));
      mGLSurface.setViewport(0, 0, width, height);
      render.requestRender();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
      render.removeSurface(mGLSurface);
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    Log.d(TAG, "onCreate");
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    // Example of a call to a native method
    TextView tv = (TextView) findViewById(R.id.sample_text);
    this.setupGL();

    tv.setText(stringFromJNI());
  }

  private void setupGL() {
    // Example of a call to a native method
    SurfaceView sv = (SurfaceView) findViewById(R.id.camera_preview);
    SurfaceView sm = (SurfaceView) findViewById(R.id.camera_preview_small);
    this.render = new GLRenderer();
    this.render.startRender();

    sv.getHolder().addCallback(new HolderCallBack());
    sm.getHolder().addCallback(new HolderCallBack());

    this.render.startPreview();
    this.render.setFilter("@beautify face 1 480 640 @curve R(0, 0)(71, 74)(164, 165)(255, 255) @pixblend screen 0.94118 0.29 0.29 1 20");
  }

  @Override
  protected void onDestroy() {
    Log.d(TAG, "onDestroy");
    this.render.stopPreview();
    this.render.release();
    this.render = null;
    super.onDestroy();
  }

  /**
   * A native method that is implemented by the 'native-lib' native library,
   * which is packaged with this application.
   */
  public native String stringFromJNI();
}
