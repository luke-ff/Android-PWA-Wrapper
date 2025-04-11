package at.webpos.pwawrapper.webview;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Arrays;

import at.webpos.softpay.SoftPayImpl;
import at.webpos.pwawrapper.Constants;
import at.webpos.pwawrapper.R;
import at.webpos.pwawrapper.ui.UIManager;

public class WebViewHelper {
    // Instance variables
    private final Activity activity;
    private final UIManager uiManager;
    private final WebView webView;
    private final WebSettings webSettings;

    public WebViewHelper(Activity activity, UIManager uiManager) {
        this.activity = activity;
        this.uiManager = uiManager;
        this.webView = activity.findViewById(R.id.webView);
        this.webSettings = webView.getSettings();
    }

    /**
     * Simple helper method checking if connected to Network.
     * Doesn't check for actual Internet connection!
     * @return {boolean} True if connected to Network.
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager manager =
                (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();

        // Wifi or Mobile Network is present and connected
        return networkInfo != null && networkInfo.isConnected();
    }

    // manipulate cache settings to make sure our PWA gets updated
    private void useCache(Boolean use) {
        if (use) {
            webSettings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        } else {
            webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        }
    }

    // public method changing cache settings according to network availability.
    // retrieve content from cache primarily if not connected,
    // allow fetching from web too otherwise to get updates.
    public void forceCacheIfOffline() {
        useCache(!isNetworkAvailable());
    }

    // handles initial setup of webview
    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    public void setupWebView() {

        final SoftPayImpl mySoftPay = new SoftPayImpl(
                activity.getApplicationContext(),
                webView );


        // accept cookies
        CookieManager.getInstance().setAcceptCookie(true);
        // enable JS
        webSettings.setJavaScriptEnabled(true);
        // must be set for our js-popup-blocker:
        webSettings.setSupportMultipleWindows(true);

        webSettings.setDomStorageEnabled(true);
        webSettings.setAppCacheEnabled(true);
        webSettings.setSafeBrowsingEnabled(false);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        //removed in 33
        //webSettings.setAppCachePath(activity.getApplicationContext().getCacheDir().getAbsolutePath());
        //webSettings.setAppCacheEnabled(true);
        webSettings.setDatabaseEnabled(true);

        // enable mixed content mode conditionally
        if (Constants.ENABLE_MIXED_CONTENT) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        // retrieve content from cache primarily if not connected
        forceCacheIfOffline();

        // set User Agent
        if (Constants.OVERRIDE_USER_AGENT || Constants.POSTFIX_USER_AGENT) {
            String userAgent = webSettings.getUserAgentString();
            if (Constants.OVERRIDE_USER_AGENT) {
                userAgent = Constants.USER_AGENT;
            }
            if (Constants.POSTFIX_USER_AGENT) {
                userAgent = userAgent + " " + Constants.USER_AGENT_POSTFIX;
            }
            webSettings.setUserAgentString(userAgent);
        }

        // enable HTML5-support
        webView.setWebChromeClient(new WebChromeClient() {
            //simple yet effective redirect/popup blocker
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                Message href = view.getHandler().obtainMessage();
                view.requestFocusNodeHref(href);
                final String popupUrl = href.getData().getString("url");
                if (popupUrl != null) {
                    //it's null for most rouge browser hijack ads
                    webView.loadUrl(popupUrl);
                    return true;
                }
                return false;
            }

            // Permission requests
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                final boolean reqCamPerm = Arrays.asList(request.getResources()).contains("android.permission.CAMERA");
                final boolean reqVideoPerm = Arrays.asList(request.getResources()).contains("android.webkit.resource.VIDEO_CAPTURE");
                if (reqCamPerm || reqVideoPerm ) {
                    if (ContextCompat.checkSelfPermission(webView.getContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        // Permission is not granted, so request it
                        ActivityCompat.requestPermissions(activity, new String[] { Manifest.permission.CAMERA }, 123);
                    } else {
                        request.grant(request.getResources());
                    }
                } else {
                    super.onPermissionRequest(request);
                }
            }

            // update ProgressBar
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                uiManager.setLoadingProgress(newProgress);
                super.onProgressChanged(view, newProgress);
            }
        });

        // Set up Webview client
        webView.setWebViewClient(new WebViewClient() {


            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                handleUrlLoad(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                //webView.loadUrl("javascript:function inputClick(val){native.click();}");     // override js function
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                // new API method calls this on every error for each resource.
                // we only want to interfere if the page itself got problems.
                String url = request.getUrl().toString();
                if (view.getUrl().equals(url)) {
                    handleLoadError(error.getErrorCode());
                }
            }
        });

        Log.i(TAG,"addJavascriptInterface SoftPay");
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void pay(int cents, String callbackfn ) {
                mySoftPay.pay(cents, callbackfn);
            }
            @JavascriptInterface
            public void setPrintWidth(int width) {
                mySoftPay.setPrintWidth(width);
            }
        }, "SoftPay");

    }

    // Lifecycle callbacks
    public void onPause() {
        webView.onPause();
    }

    public void onResume() {
        webView.onResume();
    }

    // show "no app found" dialog
    private void showNoAppDialog(Activity thisActivity) {
        new AlertDialog.Builder(thisActivity)
            .setTitle(R.string.noapp_heading)
            .setMessage(R.string.noapp_description)
            .show();
    }
    // handle load errors
    private void handleLoadError(int errorCode) {
        if (errorCode != WebViewClient.ERROR_UNSUPPORTED_SCHEME) {
            uiManager.setOffline(true);
        } else {
            // Unsupported Scheme, recover
            new Handler().postDelayed(this::goBack, 100);
        }
    }

    // handle external urls
    private void handleUrlLoad(WebView view, String url) {
        // prevent loading content that isn't ours
        if (!url.startsWith(Constants.WEBAPP_URL)) {
            // stop loading
            // stopping only would cause the PWA to freeze, need to reload the app as a workaround
            view.stopLoading();
            view.reload();

            // open external URL in Browser/3rd party apps instead
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                if (intent.resolveActivity(activity.getPackageManager()) != null) {
                    activity.startActivity(intent);
                } else {
                    showNoAppDialog(activity);
                }
            } catch (Exception e) {
                showNoAppDialog(activity);
            }
            // return value for shouldOverrideUrlLoading
        } else {
            // let WebView load the page!
            // activate loading animation screen
            uiManager.setLoading(true);
            // return value for shouldOverrideUrlLoading
        }
    }

    // handle back button press
    public boolean goBack() {
        if (webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return false;
    }

    // load app startpage
    public void loadHome() {
        webView.loadUrl(Constants.WEBAPP_URL);
    }

    // load URL from intent
    public void loadIntentUrl(String url) {
        if (!url.isEmpty() && url.contains(Constants.WEBAPP_HOST)) {
            webView.loadUrl(url);
        } else {
            // Fallback
            loadHome();
        }
    }
}
