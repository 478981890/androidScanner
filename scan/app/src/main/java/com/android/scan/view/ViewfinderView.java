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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.android.scan.R;
import com.android.scan.camera.CameraManager;
/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView extends View {

  private static final long ANIMATION_DELAY = 80L;
  private static final int CURRENT_POINT_OPACITY = 0xA0;
  private static final int MAX_RESULT_POINTS = 20;
  private static final int POINT_SIZE = 6;

  private CameraManager cameraManager;
  private final Paint paint;
  private Bitmap resultBitmap;
  private final int maskColor;
  private final int laserColor;
  private int angle;
//  private int middle =-1;

  // This constructor is used when the class is built from an XML resource.
  public ViewfinderView(Context context, AttributeSet attrs) {
    super(context, attrs);

    // Initialize these once for performance rather than calling them every time in onDraw().
    paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Resources resources = getResources();
    maskColor = resources.getColor(R.color.viewfinder_mask);
    laserColor = resources.getColor(R.color.viewfinder_laser);
    angle  = resources.getColor(R.color.viewfinder_angle);
  }

  public void setCameraManager(CameraManager cameraManager) {
    this.cameraManager = cameraManager;
  }

  @SuppressLint("DrawAllocation")
  @Override
  public void onDraw(Canvas canvas) {
    if (cameraManager == null) {
      return; // not ready yet, early draw before done configuring
    }
    Rect frame = cameraManager.getFramingRect();
    Rect previewFrame = cameraManager.getFramingRectInPreview();
    if (frame == null || previewFrame == null) {
      return;
    }
    int width = canvas.getWidth();
    int height = canvas.getHeight();

    // Draw the exterior (i.e. outside the framing rect) darkened
    paint.setColor( maskColor);
    canvas.drawRect(0, 0, width, frame.top, paint);
    canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
    canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, paint);
    canvas.drawRect(0, frame.bottom + 1, width, height, paint);
    paint.setColor(angle);
    int angle_width = 20;
    int angle_height = 4;
    canvas.drawRect(frame.left ,frame.top, frame.left +angle_width, frame.top+angle_height, paint);
    canvas.drawRect(frame.left ,frame.top, frame.left +angle_height, frame.top+angle_width, paint);
    canvas.drawRect(frame.left ,frame.bottom - angle_width, frame.left +angle_height, frame.bottom, paint);
    canvas.drawRect(frame.left ,frame.bottom - angle_height, frame.left +angle_width, frame.bottom, paint);
    canvas.drawRect(frame.right - angle_width ,frame.top, frame.right, frame.top + angle_height, paint);
    canvas.drawRect(frame.right - angle_height ,frame.top, frame.right, frame.top + angle_width, paint);
    canvas.drawRect(frame.right - angle_width ,frame.bottom - angle_height, frame.right, frame.bottom , paint);
    canvas.drawRect(frame.right - angle_height ,frame.bottom- angle_width, frame.right, frame.bottom , paint);
    paint.setColor(laserColor);
//    if(middle==-1){
//      middle = frame.top;
//    }else{
//      middle+=8;
//      if(middle>=frame.height()+frame.top){
//        middle = frame.top;
//      }
//    }
//    canvas.drawRect(frame.left + 2, middle - 1, frame.right - 1, middle + 2, paint);

    // Request another update at the animation interval, but only repaint the laser line,
    // not the entire viewfinder mask.
    postInvalidateDelayed(ANIMATION_DELAY,
            frame.left - POINT_SIZE,
            frame.top - POINT_SIZE,
            frame.right + POINT_SIZE,
            frame.bottom + POINT_SIZE);
  }

  public void drawViewfinder() {
    Bitmap resultBitmap = this.resultBitmap;
    this.resultBitmap = null;
    if (resultBitmap != null) {
      resultBitmap.recycle();
    }
    invalidate();
  }



}
