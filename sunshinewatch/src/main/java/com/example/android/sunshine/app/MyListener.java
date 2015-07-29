package com.example.android.sunshine.app;

import android.net.Uri;
import android.util.Log;

import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.List;

/**
 * Created by TIM on 7/29/2015.
 */
public class MyListener extends WearableListenerService {

    String myTag = "listenerService";

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        super.onDataChanged(dataEvents);
        Log.d(myTag, "onDataChanged()"); //This is NEVER shown

        final List<DataEvent> events = FreezableUtils.freezeIterable(dataEvents);
        for (DataEvent event : events) {
            final Uri uri = event.getDataItem().getUri();
            final String path = uri != null ? uri.getPath() : null;
            if ("/SAMPLE".equals(path)) {
                final DataMap map = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                // read your values from map:
                int color = map.getInt("color");
                String stringExample = map.getString("string_example");
                Log.d(myTag, color + stringExample); //This is NEVER shown
            }
        }
    }
}