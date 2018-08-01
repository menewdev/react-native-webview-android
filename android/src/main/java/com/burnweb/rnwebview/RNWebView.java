package com.burnweb.rnwebview;

import android.annotation.SuppressLint;

import android.content.Intent;
import android.net.Uri;
import android.graphics.Bitmap;
import android.os.Build;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.WindowManager;
import android.webkit.CookieManager;

import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.common.SystemClock;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.UIManagerModule;
import com.facebook.react.uimanager.events.EventDispatcher;
import io.repro.android.Repro;

class RNWebView extends WebView implements LifecycleEventListener {

    private final EventDispatcher mEventDispatcher;
    private final RNWebViewManager mViewManager;
    private final ThemedReactContext mReactContext;

    private String charset = "UTF-8";
    private String baseUrl = "file:///";
    private String injectedJavaScript = null;
    private boolean allowUrlRedirect = false;

    private String currentUrl = "";
    private String shouldOverrideUrlLoadingUrl = "";

    protected class EventWebClient extends WebViewClient {
        public boolean shouldOverrideUrlLoading(WebView view, String url){
            if (url.substring(0, 7).equals("mailto:")) {
                Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse(url));
                mReactContext.getCurrentActivity().startActivity(intent);
                return true;
            }

            int navigationType = 0;

            if (currentUrl.equals(url) || url.equals("about:blank")) { // for regular .reload() and html reload.
                navigationType = 3;
            }

            shouldOverrideUrlLoadingUrl = url;
            mEventDispatcher.dispatchEvent(new ShouldOverrideUrlLoadingEvent(getId(), SystemClock.nanoTime(), url, navigationType));

            return true;
        }

        public void onPageFinished(WebView view, String url) {
            mEventDispatcher.dispatchEvent(new NavigationStateChangeEvent(getId(), SystemClock.nanoTime(), view.getTitle(), false, url, view.canGoBack(), view.canGoForward()));

            currentUrl = url;

            if(RNWebView.this.getInjectedJavaScript() != null) {
                view.loadUrl("javascript:(function() {\n" + RNWebView.this.getInjectedJavaScript() + ";\n})();");
            }

            Activity currentActivity = mReactContext.getCurrentActivity();
            if (currentActivity != null) {
                if  (url.indexOf("menu.php") != -1) {
                    currentActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    currentActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            }
        }

        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            mEventDispatcher.dispatchEvent(new NavigationStateChangeEvent(getId(), SystemClock.nanoTime(), view.getTitle(), true, url, view.canGoBack(), view.canGoForward()));
        }
    }

    protected class CustomWebChromeClient extends WebChromeClient {
        @Override
        public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
          new AlertDialog.Builder(mReactContext)
                    .setTitle(url + "のページ")
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    result.confirm();
                                }
                            })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    result.cancel();
                                }
                            })
                    .setCancelable(false)
                    .create()
                    .show();

            return true;
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            getModule().showAlert(url, message, result);
            return true;
        }

        // For Android 4.1+
        @SuppressWarnings("unused")
        public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
            getModule().startFileChooserIntent(uploadMsg, acceptType);
        }

        // For Android 5.0+
        @SuppressLint("NewApi")
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
            return getModule().startFileChooserIntent(filePathCallback, fileChooserParams.createIntent());
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            mEventDispatcher.dispatchEvent(new ProgressEvent(getId(), newProgress));
        }
    }

    protected class GeoWebChromeClient extends CustomWebChromeClient {
        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            callback.invoke(origin, true, false);
        }
    }

    public RNWebView(RNWebViewManager viewManager, ThemedReactContext reactContext) {
        super(reactContext);

        mReactContext = reactContext;
        mViewManager = viewManager;
        mEventDispatcher = reactContext.getNativeModule(UIManagerModule.class).getEventDispatcher();

        this.getSettings().setJavaScriptEnabled(true);
        this.getSettings().setBuiltInZoomControls(false);
        this.getSettings().setDomStorageEnabled(true);
        this.getSettings().setGeolocationEnabled(false);
        this.getSettings().setPluginState(WebSettings.PluginState.ON);
        this.getSettings().setAllowFileAccess(true);
        this.getSettings().setAllowFileAccessFromFileURLs(true);
        this.getSettings().setAllowUniversalAccessFromFileURLs(true);
        this.getSettings().setLoadsImagesAutomatically(true);
        this.getSettings().setBlockNetworkImage(false);
        this.getSettings().setBlockNetworkLoads(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            this.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        final EventWebClient eventWebClient = new EventWebClient();
        this.setWebViewClient(eventWebClient);
        this.setWebChromeClient(getCustomClient());

        this.addJavascriptInterface(RNWebView.this, "webView");

        // call startWebViewTracking() with webView and client
        Repro.startWebViewTracking(this, eventWebClient);

        // Custom User Agent
        final WebSettings settings = this.getSettings();
        settings.setUserAgentString(settings.getUserAgentString() + "Repro");

        CookieManager.getInstance().setCookie("http://app.menew.jp", "react_native=true; expires=Fri, 31 Dec 9999 23:59:59 GMT");
        CookieManager.getInstance().setCookie("http://app.menew.jp", "app_flg=1; expires=Fri, 31 Dec 9999 23:59:59 GMT");
        CookieManager.getInstance().setCookie("http://app.menew.jp", "android_app_flg=1; expires=Fri, 31 Dec 9999 23:59:59 GMT");
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public String getCharset() {
        return this.charset;
    }

    public void setAllowUrlRedirect(boolean a) {
        this.allowUrlRedirect = a;
    }

    public boolean getAllowUrlRedirect() {
        return this.allowUrlRedirect;
    }

    public void setInjectedJavaScript(String injectedJavaScript) {
        this.injectedJavaScript = injectedJavaScript;
    }

    public String getInjectedJavaScript() {
        return this.injectedJavaScript;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void shouldOverrideWithResult(RNWebView view, ReadableArray args) {
        if (!args.getBoolean(0)) {
            view.loadUrl(shouldOverrideUrlLoadingUrl);
        }
    }

    public String getBaseUrl() {
        return this.baseUrl;
    }

    public CustomWebChromeClient getCustomClient() {
        return new CustomWebChromeClient();
    }

    public GeoWebChromeClient getGeoClient() {
        return new GeoWebChromeClient();
    }

    public RNWebViewModule getModule() {
        return mViewManager.getPackage().getModule();
    }

    @Override
    public void onHostResume() {

    }

    @Override
    public void onHostPause() {

    }

    @Override
    public void onHostDestroy() {
        destroy();
    }

    // @Override
    // public void onDetachedFromWindow() {
    //     this.loadDataWithBaseURL(this.getBaseUrl(), "<html></html>", "text/html", this.getCharset(), null);
    //     super.onDetachedFromWindow();
    // }

    @JavascriptInterface
     public void postMessage(String jsParamaters) {
        mEventDispatcher.dispatchEvent(new MessageEvent(getId(), jsParamaters));
    }
}
