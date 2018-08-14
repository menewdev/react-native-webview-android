package com.burnweb.rnwebview;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.events.Event;
import com.facebook.react.uimanager.events.RCTEventEmitter;

public class ErrorEvent extends Event<ErrorEvent> {

    public static final String EVENT_NAME = "errorEvent";

    private final int errorCode;
    private final String description;
    private final String failingUrl;

    public ErrorEvent(int viewId, int errorCode, String description, String failingUrl) {
        super(viewId);

        this.errorCode = errorCode;
        this.description = description;
        this.failingUrl = failingUrl;
    }

    @Override
    public String getEventName() {
        return EVENT_NAME;
    }

    @Override
    public void dispatch(RCTEventEmitter rctEventEmitter) {
        rctEventEmitter.receiveEvent(getViewTag(), getEventName(), serializeEventData());
    }

    private WritableMap serializeEventData() {
        WritableMap eventData = Arguments.createMap();
        eventData.putInt("errorCode", this.errorCode);
        eventData.putString("description", this.description);
        eventData.putString("failingUrl", this.failingUrl);

        return eventData;
    }

}
