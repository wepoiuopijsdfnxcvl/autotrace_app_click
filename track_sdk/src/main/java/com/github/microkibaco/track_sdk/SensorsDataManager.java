package com.github.microkibaco.track_sdk;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;

import com.github.microkibaco.track_sdk.wrapper.MkOnClickListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author 杨正友(小木箱)于 2020/10/4 14 32 创建
 * @Email: yzy569015640@gmail.com
 * @Tel: 18390833563
 * @function description:
 */
public class SensorsDataManager {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss" + ".SSS", Locale.CHINA);

    public static void mergeJsonObject(final JSONObject source, JSONObject dest) {
        final Iterator<String> superPropertiesIterator = source.keys();
        while (superPropertiesIterator.hasNext()) {
            final String key = superPropertiesIterator.next();
            final Object value;
            try {
                value = source.get(key);
                synchronized (DATE_FORMAT) {
                    dest.put(key, value instanceof Date ? DATE_FORMAT.format((Date) value) : value);
                }
            } catch (JSONException e) {
              Log.getStackTraceString(e);
            }
        }
    }

    public static Map<String, Object> getDeviceInfo(Context context) {
        final Map<String, Object> deviceInfo = new HashMap<>(10);

            deviceInfo.put(ITrackClickEvent.LIB, "Android");
            deviceInfo.put(ITrackClickEvent.LIB_VERSION, SensorsReporter.SDK_VERSION);
            deviceInfo.put(ITrackClickEvent.OS, "Android");
            deviceInfo.put(ITrackClickEvent.OS_VERSION, Build.VERSION.RELEASE == null ? "UNKNOWN" : Build.VERSION.RELEASE);
            deviceInfo.put(ITrackClickEvent.MANUFACTURER, Build.MANUFACTURER == null ? "UNKNOWN" : Build.MANUFACTURER);
            deviceInfo.put(ITrackClickEvent.MODEL, TextUtils.isEmpty(Build.MODEL) ? "UNKNOWN" : Build.MODEL.trim());

            try {
                final PackageManager manager = context.getPackageManager();
                final PackageInfo packageInfo = manager.getPackageInfo(context.getPackageName(), 0);
                deviceInfo.put(ITrackClickEvent.APP_VERSION, packageInfo.versionName);

                final int labelRes = packageInfo.applicationInfo.labelRes;
                deviceInfo.put(ITrackClickEvent.APP_NAME, context.getResources().getString(labelRes));
            } catch (final Exception e) {
                e.printStackTrace();
            }

            final DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
            deviceInfo.put(ITrackClickEvent.SCREEN_HEIGHT, displayMetrics.heightPixels);
            deviceInfo.put(ITrackClickEvent.SCREEN_WIDTH, displayMetrics.widthPixels);

            return Collections.unmodifiableMap(deviceInfo);

    }


    /**
     * 注册 Application.ActivityLifecycleCallbacks
     */
    public static void registerActivityLifecycleCallbacks(Application application) {
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            private ViewTreeObserver.OnGlobalLayoutListener onGlobalLayoutListener;

            @Override
            public void onActivityCreated(@NonNull final Activity activity, @Nullable Bundle savedInstanceState) {
                onGlobalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        delegateViewsOnClickListener(activity,
                                SensorsDataHelper.getRootViewFromActivity(activity, false));
                    }
                };
            }



            @Override
            public void onActivityResumed(@NonNull final Activity activity) {

                SensorsDataHelper
                         .getRootViewFromActivity(activity, true)
                         .getViewTreeObserver()
                         .addOnGlobalLayoutListener(onGlobalLayoutListener);
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {

                /*
                 * 移除顶层Activity的监听
                 */
                SensorsDataHelper.getRootViewFromActivity(activity, false)
                        .getViewTreeObserver()
                        .removeOnGlobalLayoutListener(onGlobalLayoutListener);

            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {

            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {

            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {

            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {

            }
        });
    }


    public static void delegateViewsOnClickListener(final Context context, final View view) {
        if (context == null || view == null) {
            return;
        }
        if (view instanceof AdapterView) {

            DelegateViewHolder.getInstance().spinnerItemClick(view);
            DelegateViewHolder.getInstance().expandableItemClick(view);
            DelegateViewHolder.getInstance().gridViewItemClick(view);

        } else {
            // 获取当前 view 设置的 OnClickListener
            final View.OnClickListener listener = SensorsDataHelper.getOnClickListener(view);

            // 判断已设置的 OnClickListener 类型，如果是自定义的 WrapperOnClickListener，说明已经被 hook 过，防止重复 hook
            if (listener != null && !(listener instanceof MkOnClickListener)) {
                // 替换成自定义的 WrapperOnClickListener
                view.setOnClickListener(new MkOnClickListener(listener));

            }

             DelegateViewHolder.getInstance().compoundButtonItemClick(view);
             DelegateViewHolder.getInstance().radioGroupItemClick(view);
             DelegateViewHolder.getInstance().ratingBarItemClick(view);
             DelegateViewHolder.getInstance().seekBarItemClick(view);

        }
    }

    public static void trackAdapterView(AdapterView<?> adapterView, View view, int position) {
        try {
            final JSONObject jsonObject = new JSONObject();
            jsonObject.put(ITrackClickEvent.CANONICAL_NAME, adapterView.getClass().getCanonicalName());
            jsonObject.put(ITrackClickEvent.ELEMENT_ID, SensorsDataHelper.getViewId(adapterView));
            jsonObject.put(ITrackClickEvent.ELEMENT_POSITION, String.valueOf(position));
            final StringBuilder stringBuilder = new StringBuilder();
            String viewText = SensorsDataHelper.traverseViewContent(stringBuilder, view);
            if (!TextUtils.isEmpty(viewText)) {
                jsonObject.put(ITrackClickEvent.ELEMENT_POSITION, viewText);
            }
            Activity activity = SensorsDataHelper.getActivityFromView(adapterView);
            if (activity != null) {
                jsonObject.put(ITrackClickEvent.ACTIVITY_NAME, activity.getClass().getCanonicalName());
            }

            SensorsReporter.getSensorsDataApiInstance().track(ITrackClickEvent.APP_CLICK, jsonObject);
        } catch (Exception e) {
            Log.getStackTraceString(e);
        }
    }

    public static void trackAdapterView(AdapterView<?> adapterView, View view, int groupPosition, int childPosition) {
        try {
            final JSONObject jsonObject = new JSONObject();
            jsonObject.put(ITrackClickEvent.CANONICAL_NAME, adapterView.getClass().getCanonicalName());
            jsonObject.put(ITrackClickEvent.ELEMENT_ID, SensorsDataHelper.getViewId(adapterView));
            if (childPosition > -1) {
                jsonObject.put(ITrackClickEvent.ELEMENT_POSITION, String.format(Locale.CHINA, "%d:%d", groupPosition, childPosition));
            } else {
                jsonObject.put(ITrackClickEvent.ELEMENT_POSITION, String.format(Locale.CHINA, "%d", groupPosition));
            }
            StringBuilder stringBuilder = new StringBuilder();
            String viewText = SensorsDataHelper.traverseViewContent(stringBuilder, view);
            if (!TextUtils.isEmpty(viewText)) {
                jsonObject.put(ITrackClickEvent.ELEMENT_ELEMENT, viewText);
            }
            final Activity activity = SensorsDataHelper.getActivityFromView(adapterView);
            if (activity != null) {
                jsonObject.put(ITrackClickEvent.ACTIVITY_NAME, activity.getClass().getCanonicalName());
            }

            SensorsReporter.getSensorsDataApiInstance().track(ITrackClickEvent.APP_CLICK, jsonObject);
        } catch (Exception e) {
            Log.getStackTraceString(e);
        }
    }

    /**
     * View 被点击，自动埋点
     *
     * @param view View
     */
    @Keep
    public static void trackViewOnClick(View view) {
        try {
            final JSONObject jsonObject = new JSONObject();
            jsonObject.put(ITrackClickEvent.CANONICAL_NAME, view.getClass().getCanonicalName());
            jsonObject.put(ITrackClickEvent.VIEW_ID, SensorsDataHelper.getViewId(view));
            jsonObject.put(ITrackClickEvent.ELEMENT_CONTENT, SensorsDataHelper.getElementContent(view));

            final Activity activity = SensorsDataHelper.getActivityFromView(view);
            if (activity != null) {
                jsonObject.put(ITrackClickEvent.ACTIVITY_NAME, activity.getClass().getCanonicalName());
            }

            SensorsReporter.getSensorsDataApiInstance().track(ITrackClickEvent.APP_CLICK, jsonObject);
        } catch (Exception e) {
            Log.getStackTraceString(e);
        }
    }
}
