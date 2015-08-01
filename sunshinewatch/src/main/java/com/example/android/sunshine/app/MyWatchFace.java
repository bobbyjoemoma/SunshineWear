/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements
            DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener {

        private GoogleApiClient googleApiClient;
        private String mTempHigh = "99";
        private String mTempLow = "00";
        private Bitmap mWeatherIcon;
        static final int MSG_UPDATE_TIME = 0;

        /**
         * Handler to update the time periodically in interactive mode.
         */
        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                    - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        boolean mRegisteredTimeZoneReceiver = false;

        Paint mBackgroundPaint;
        Paint mTextPaintTimeNormal;
        Paint mTextPaintTimeBold;
        Paint mTextPaintDateNormal;
        Paint mTextPaintDateBold;
        Paint mTextPaintTempHighNormal;
        Paint mTextPaintTempHighBold;
        Paint mTextPaintTempLowNormal;
        Paint mTextPaintTempLowBold;

        boolean mAmbient;

        Time mTime;

        float mXOffset;
        float mYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            googleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            googleApiClient.connect();

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.primary));

            mTextPaintTimeNormal = new Paint();
            mTextPaintTimeBold = new Paint();
            mTextPaintDateNormal = new Paint();
            mTextPaintDateBold = new Paint();
            mTextPaintTempHighNormal = new Paint();
            mTextPaintTempHighBold = new Paint();
            mTextPaintTempLowNormal = new Paint();
            mTextPaintTempLowBold = new Paint();

            mTextPaintTimeNormal = createTextPaint(resources.getColor(R.color.white_text), NORMAL_TYPEFACE);
            mTextPaintTimeBold = createTextPaint(resources.getColor(R.color.white_text), BOLD_TYPEFACE);
            mTextPaintDateNormal = createTextPaint(resources.getColor(R.color.grey_text), NORMAL_TYPEFACE);
            mTextPaintDateBold = createTextPaint(resources.getColor(R.color.grey_text), BOLD_TYPEFACE);
            mTextPaintTempHighNormal = createTextPaint(resources.getColor(R.color.white_text), NORMAL_TYPEFACE);
            mTextPaintTempHighBold = createTextPaint(resources.getColor(R.color.white_text), BOLD_TYPEFACE);
            mTextPaintTempLowNormal = createTextPaint(resources.getColor(R.color.grey_text), NORMAL_TYPEFACE);
            mTextPaintTempLowBold = createTextPaint(resources.getColor(R.color.grey_text), BOLD_TYPEFACE);

            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                googleApiClient.connect();
                //Wearable.DataApi.addListener(googleApiClient, this);
                //Wearable.DataApi.getDataItems(googleApiClient).setResultCallback(onConnectedResultCallback);

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
                if (googleApiClient != null && googleApiClient.isConnected()) {
                //   Wearable.DataApi.removeListener(googleApiClient, onDataChangedListener);
                    googleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);

            //set text size based on watch shape
            mTextPaintTimeNormal.setTextSize(resources.getDimension(isRound
                    ? R.dimen.digital_text_time_size_round
                    : R.dimen.digital_text_time_size));
            mTextPaintTimeBold.setTextSize(resources.getDimension(isRound
                    ? R.dimen.digital_text_time_size_round
                    : R.dimen.digital_text_time_size));
            mTextPaintDateNormal.setTextSize(resources.getDimension(isRound
                    ? R.dimen.digital_text_date_size_round
                    : R.dimen.digital_text_date_size));
            mTextPaintDateBold.setTextSize(resources.getDimension(isRound
                    ? R.dimen.digital_text_date_size_round
                    : R.dimen.digital_text_date_size));
            mTextPaintTempHighNormal.setTextSize(resources.getDimension(isRound
                    ? R.dimen.digital_text_temp_size_round
                    : R.dimen.digital_text_temp_size));
            mTextPaintTempHighBold.setTextSize(resources.getDimension(isRound
                    ? R.dimen.digital_text_temp_size_round
                    : R.dimen.digital_text_temp_size));
            mTextPaintTempLowNormal.setTextSize(resources.getDimension(isRound
                    ? R.dimen.digital_text_temp_size_round
                    : R.dimen.digital_text_temp_size));
            mTextPaintTempLowBold.setTextSize(resources.getDimension(isRound
                    ? R.dimen.digital_text_temp_size_round
                    : R.dimen.digital_text_temp_size));
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaintTimeNormal.setAntiAlias(!inAmbientMode);
                    mTextPaintTimeBold.setAntiAlias(!inAmbientMode);
                    mTextPaintDateNormal.setAntiAlias(!inAmbientMode);
                    mTextPaintDateBold.setAntiAlias(!inAmbientMode);
                    mTextPaintTempHighNormal.setAntiAlias(!inAmbientMode);
                    mTextPaintTempHighBold.setAntiAlias(!inAmbientMode);
                    mTextPaintTempLowNormal.setAntiAlias(!inAmbientMode);
                    mTextPaintTempLowBold.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

            int centerX = bounds.centerX();
            int centerY = bounds.centerY();
            int spacing_5 = 5;
            int spacing_10 = 10;
            int spacing_20 = 20;
            int spacing_30 = 30;
            int y_origin = centerY;
            //time object
            mTime.setToNow();

            //generic rectangle for bound measurement
            Rect mTextBounds = new Rect();

            //weather icon
            if(mWeatherIcon != null) {
                mWeatherIcon = Bitmap.createScaledBitmap(mWeatherIcon,55,55,true); //scale to size
                y_origin -= mWeatherIcon.getHeight() / 2;
                canvas.drawBitmap(mWeatherIcon, centerX - mWeatherIcon.getWidth() / 2, y_origin, null);
            }

            //date string
            String date_text = String.format("%02d.%02d.%d", (mTime.month + 1), mTime.monthDay, mTime.year);
            mTextPaintDateNormal.getTextBounds(date_text,0,date_text.length(),mTextBounds);
            y_origin -= (mTextBounds.height() + spacing_5);
            canvas.drawText(date_text,
                    centerX - mTextBounds.width()/2,
                    y_origin,
                    mTextPaintDateNormal);

            //time
            String time_text_hour = String.format("%d:", mTime.hour);
            mTextPaintTimeBold.getTextBounds(time_text_hour,0,time_text_hour.length(), mTextBounds);
            y_origin -= (mTextBounds.height() + spacing_10);
            canvas.drawText(time_text_hour, centerX - mTextBounds.width(), y_origin, mTextPaintTimeBold);
            String time_text_minute = String.format("%02d",mTime.minute);
            mTextPaintTimeNormal.getTextBounds(time_text_minute,0,time_text_minute.length(),mTextBounds);
            canvas.drawText(time_text_minute, centerX, y_origin, mTextPaintTimeNormal);

            //temperature
            String temp_high_text = mTempHigh;
            mTextPaintTempHighBold.getTextBounds(temp_high_text,0,temp_high_text.length(), mTextBounds);
            if(mWeatherIcon != null){
                y_origin = centerY + mWeatherIcon.getHeight()/2 + spacing_20;
            }
            else {
                y_origin = centerY + spacing_20;
            }
            canvas.drawText(temp_high_text, centerX - mTextBounds.width(), y_origin, mTextPaintTempHighBold);

            String temp_low_text = mTempLow;
            mTextPaintTempLowNormal.getTextBounds(temp_low_text, 0, temp_low_text.length(), mTextBounds);
            canvas.drawText(temp_low_text, centerX, y_origin, mTextPaintTempLowNormal);

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        private void processDataIem(DataMap dataMap){
            if (dataMap.containsKey("ICON_KEY")) {
                mWeatherIcon = loadBitmapFromAsset(dataMap.getAsset("ICON_KEY"));
            }

            if (dataMap.containsKey("TEMPERATURE_HIGH_KEY")) {
                mTempHigh = dataMap.getString("TEMPERATURE_HIGH_KEY");
            }

            if (dataMap.containsKey("TEMPERATURE_LOW_KEY")) {
                mTempLow = dataMap.getString("TEMPERATURE_LOW_KEY");
            }
        }

        public Bitmap loadBitmapFromAsset(Asset asset) {
            if(googleApiClient != null && googleApiClient.isConnected()){
                InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                        googleApiClient, asset).await().getInputStream();
                if (assetInputStream != null) {
                    return BitmapFactory.decodeStream(assetInputStream);
                }
            }
            return null;
        }

        @Override
        public void onConnected(Bundle bundle) {
            Log.d("wear", "onConnected");
            Wearable.DataApi.addListener(googleApiClient, this);
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d("wear", "onConnectionSuspended");
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            Log.d("wear", "onConnectionFailed");
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            Log.v("wear","data change received");
            final List<DataEvent> events = FreezableUtils
                    .freezeIterable(dataEvents);

            ConnectionResult connectionResult =
                    googleApiClient.blockingConnect(15, TimeUnit.SECONDS);

            if (!connectionResult.isSuccess()) {
                Log.e("connections", "Failed to connect to GoogleApiClient.");
                return;
            }

            for (DataEvent event : events) {
                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    DataItem item = event.getDataItem();
                    if (item.getUri().getPath().equals("/weather")) {
                        processDataIem(DataMapItem.fromDataItem(item).getDataMap());
                    }
                }
            }
            dataEvents.release();
            invalidate();
        }
    }
}