/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.scan.view;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.scan.R;
import com.android.scan.camera.CameraManager;
import com.android.scan.util.AmbientLightManager;
import com.android.scan.util.BeepManager;
import com.android.scan.util.DecodeFormatManager;
import com.android.scan.util.DecodeHintManager;
import com.android.scan.util.InactivityTimer;
import com.android.scan.util.IntentSource;
import com.android.scan.util.Intents;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * This activity opens the camera and does the actual scanning on a background
 * thread. It draws a viewfinder to help the user place the barcode correctly,
 * shows feedback as the image processing is happening, and then overlays the
 * results when a scan is successful.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 */
public final class CaptureActivity extends Activity implements SurfaceHolder.Callback {

	private static final String TAG = CaptureActivity.class.getSimpleName();

	private CameraManager cameraManager;
	private CaptureActivityHandler handler;
	private ViewfinderView viewfinderView;
	private boolean hasSurface;
	private IntentSource source;
	private Collection<BarcodeFormat> decodeFormats;
	private Map<DecodeHintType, ?> decodeHints;
	private String characterSet;
	private InactivityTimer inactivityTimer;
	private BeepManager beepManager;
	private AmbientLightManager ambientLightManager;
	private boolean openFlash = true;

	ViewfinderView getViewfinderView() {
		return viewfinderView;
	}

	public Handler getHandler() {
		return handler;
	}

	CameraManager getCameraManager() {
		return cameraManager;
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		Window window = getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.capture);
		hasSurface = false;
		inactivityTimer = new InactivityTimer(this);
		beepManager = new BeepManager(this);
		ambientLightManager = new AmbientLightManager(this);
		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		} else {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		}
    }

	@Override
	protected void onResume() {
		super.onResume();
		// CameraManager must be initialized here, not in onCreate(). This is
		// necessary because we don't
		// want to open the camera driver and measure the screen size if we're
		// going to show the help on
		// first launch. That led to bugs where the scanning rectangle was the
		// wrong size and partially
		// off screen.
		cameraManager = new CameraManager(getApplication());

		viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
		viewfinderView.setCameraManager(cameraManager);

		handler = null;
		beepManager.updatePrefs();
		ambientLightManager.start(cameraManager);

		inactivityTimer.onResume();

		Intent intent = getIntent();

		source = IntentSource.NONE;
		decodeFormats = null;
		characterSet = null;

		if (intent != null) {
			String action = intent.getAction();
			if (Intents.Scan.ACTION.equals(action)) {

				// Scan the formats the intent requested, and return the result
				// to the calling activity.
				source = IntentSource.NATIVE_APP_INTENT;
				decodeFormats = DecodeFormatManager.parseDecodeFormats(intent);
				decodeHints = DecodeHintManager.parseDecodeHints(intent);

				if (intent.hasExtra(Intents.Scan.WIDTH) && intent.hasExtra(Intents.Scan.HEIGHT)) {
					int width = intent.getIntExtra(Intents.Scan.WIDTH, 0);
					int height = intent.getIntExtra(Intents.Scan.HEIGHT, 0);
					if (width > 0 && height > 0) {
						cameraManager.setManualFramingRect(width, height);
					}
				}

				if (intent.hasExtra(Intents.Scan.CAMERA_ID)) {
					int cameraId = intent.getIntExtra(Intents.Scan.CAMERA_ID, -1);
					if (cameraId >= 0) {
						cameraManager.setManualCameraId(cameraId);
					}
				}
			}
			characterSet = intent.getStringExtra(Intents.Scan.CHARACTER_SET);
		}

		SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		if (hasSurface) {
			// The activity was paused but not stopped, so the surface still
			// exists. Therefore
			// surfaceCreated() won't be called, so init the camera here.
			initCamera(surfaceHolder);
		} else {
			// Install the callback and wait for surfaceCreated() to init the
			// camera.
			surfaceHolder.addCallback(this);
		}
	}
	@Override
	protected void onPause() {
		if (handler != null) {
			handler.quitSynchronously();
			handler = null;
		}
		inactivityTimer.onPause();
		ambientLightManager.stop();
		beepManager.close();
		cameraManager.closeDriver();
		// historyManager = null; // Keep for onActivityResult
		if (!hasSurface) {
			SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
			SurfaceHolder surfaceHolder = surfaceView.getHolder();
			surfaceHolder.removeCallback(this);
		}
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		inactivityTimer.shutdown();
		super.onDestroy();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if (resultCode == RESULT_OK) {

		}
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (holder == null) {
			Log.e(TAG, "*** WARNING *** surfaceCreated() gave us a null surface!");
		}
		if (!hasSurface) {
			hasSurface = true;
			initCamera(holder);
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		hasSurface = false;
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		// do nothing
	}

	private void initCamera(SurfaceHolder surfaceHolder) {
		ImageView line = (ImageView) findViewById(R.id.line);
		ImageView flash = (ImageView) findViewById(R.id.flashImage);
		TextView hintText = (TextView) findViewById(R.id.hintText);
		if (surfaceHolder == null) {
			throw new IllegalStateException("No SurfaceHolder provided");
		}
		if (cameraManager.isOpen()) {
			Log.w(TAG, "initCamera() while already open -- late SurfaceView callback?");
			return;
		}
		try {
			cameraManager.openDriver(surfaceHolder);
			// Creating the handler starts the preview, which can also throw a
			// RuntimeException.
			Rect frame = cameraManager.getFramingRect();
			int height = frame.bottom - frame.top;
			ViewGroup.LayoutParams lp = line.getLayoutParams();
			lp.width = height;
			line.setLayoutParams(lp);
			line.setAlpha(1.0f);
			slideview(line,frame.top,frame.bottom-2);
			flash.setPadding(0, height, 0, 0);
			flash.setAlpha(0.8f);
			hintText.setPadding(0, height + hintText.getHeight()*3, 0, 0);
			hintText.setAlpha(1.0f);
			if (handler == null) {
				handler = new CaptureActivityHandler(this, decodeFormats, decodeHints, characterSet, cameraManager);
			}
		} catch (IOException ioe) {
			Log.w(TAG, ioe);
		} catch (RuntimeException e) {
			// Barcode Scanner has seen crashes in the wild of this variety:
			// java.?lang.?RuntimeException: Fail to connect to camera service
			Log.w(TAG, "Unexpected error initializing camera", e);
		}


	}

	private void slideview(ImageView view ,final float p1, final float p2) {
	    TranslateAnimation animation = new TranslateAnimation(0, 0, p1, p2);
	    animation.setInterpolator(new LinearInterpolator());
	    animation.setDuration(2000);
	    animation.setRepeatCount(Animation.INFINITE);
	    animation.setAnimationListener(new Animation.AnimationListener() {
	        @Override
	        public void onAnimationStart(Animation animation) {
	        }
	       
	        @Override
	        public void onAnimationRepeat(Animation animation) {
	        }
	       
	        @Override
	        public void onAnimationEnd(Animation animation) {
	           
	        }
	    });
	    view.startAnimation(animation);
	}
	
	public void restartPreviewAfterDelay(long delayMS) {
		if (handler != null) {
			handler.sendEmptyMessageDelayed(R.id.restart_preview, delayMS);
		}
	}

	public void drawViewfinder() {
		viewfinderView.drawViewfinder();
	}

	//开启/关闭闪光灯
	public void openFlash(View view) {
		cameraManager.setTorch(openFlash);
		openFlash = !openFlash;
	}

    /**
     * A valid barcode has been found, so give an indication of success and show
     * the results.
     *
     * @param rawResult
     *            The contents of the barcode.
     * @param scaleFactor
     *            amount by which thumbnail was scaled
     * @param barcode
     *            A greyscale bitmap of the camera data which was decoded.
     */
    public void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor) {
        inactivityTimer.onActivity();
        restartPreviewAfterDelay(2000L);
        beepManager.playBeepSoundAndVibrate();
    }


}
