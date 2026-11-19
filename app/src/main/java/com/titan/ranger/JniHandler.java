package com.titan.ranger;

import android.text.TextUtils;
import com.titan.ranger.bean.RangerResult;
import org.json.JSONException;
import org.json.JSONObject;

/* loaded from: /tmp/opencode/x435dex/classes3.dex */
public class JniHandler {
    private static a[] a = new a[2];

    /* loaded from: /tmp/opencode/x435dex/classes3.dex */
    public interface a {
        int a(int i, String str, String str2, String str3);

        int b();

        int c(String str, String str2, int i);

        int d(int i, String str, Object obj, long j);

        int t(String str, String str2, long j);
    }

    /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
    /* JADX WARN: Code restructure failed: missing block: B:52:0x003d, code lost:
    
        if (r14.equals("OnPrepareEvent") == false) goto L4;
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
    */
    private String Callback(String str, String str2) {
        android.util.Log.i("JniHandler", "Callback(" + str + ") data=" +
            (str2 == null ? "null" : str2.substring(0, Math.min(400, str2.length()))));
        char c = 0;
        RangerResult rangerResult = new RangerResult(0, "");
        str.hashCode();
        switch (str.hashCode()) {
            case -1086793358:
                break;
            case 1241495447:
                if (str.equals("SetPlayerUI")) {
                    c = 1;
                    break;
                }
                c = 65535;
                break;
            case 1528533011:
                if (str.equals("OnReport")) {
                    c = 2;
                    break;
                }
                c = 65535;
                break;
            case 2046860249:
                if (str.equals("OnPrefetchAd")) {
                    c = 3;
                    break;
                }
                c = 65535;
                break;
            default:
                c = 65535;
                break;
        }
        switch (c) {
            case 0:
                if (!TextUtils.isEmpty(str2)) {
                    try {
                        JSONObject jSONObject = new JSONObject(str2);
                        rangerResult = b(jSONObject.getInt("instance"), jSONObject.getString("event"), (Status) zc.a.a().fromJson(jSONObject.getString("status"), Status.class), jSONObject.getLong("err"));
                        break;
                    } catch (JSONException e) {
                        e.printStackTrace();
                        rangerResult.setErr(22);
                        break;
                    }
                } else {
                    rangerResult.setErr(22);
                    break;
                }
            case 1:
                if (!TextUtils.isEmpty(str2)) {
                    try {
                        JSONObject jSONObject2 = new JSONObject(str2);
                        rangerResult = e(jSONObject2.getInt("instance"), jSONObject2.getString("object"), jSONObject2.getString("action"), jSONObject2.getInt("data"));
                        break;
                    } catch (JSONException e2) {
                        e2.printStackTrace();
                        rangerResult.setErr(22);
                        break;
                    }
                } else {
                    rangerResult.setErr(22);
                    break;
                }
            case 2:
                if (!TextUtils.isEmpty(str2)) {
                    try {
                        JSONObject jSONObject3 = new JSONObject(str2);
                        rangerResult = c(jSONObject3.getInt("instance"), jSONObject3.getString("event"), jSONObject3.getString("data"), jSONObject3.getString("extra"));
                        break;
                    } catch (Exception e3) {
                        e3.printStackTrace();
                        rangerResult.setErr(22);
                        break;
                    }
                } else {
                    rangerResult.setErr(22);
                    break;
                }
            case 3:
                if (!TextUtils.isEmpty(str2)) {
                    try {
                        JSONObject jSONObject4 = new JSONObject(str2);
                        rangerResult = a(jSONObject4.getString("name"), jSONObject4.getString("path"), jSONObject4.getInt("err"));
                        break;
                    } catch (Exception e4) {
                        e4.printStackTrace();
                        rangerResult.setErr(22);
                        break;
                    }
                } else {
                    rangerResult.setErr(22);
                    break;
                }
            default:
                rangerResult.setErr(2);
                break;
        }
        return zc.a.a().toJson(rangerResult);
    }

    private RangerResult a(String str, String str2, long j) {
        RangerResult rangerResult = new RangerResult(0, "");
        if (!TextUtils.isEmpty(str) && !TextUtils.isEmpty(str2)) {
            a aVar = a[0];
            if (aVar != null) {
                rangerResult.setErr(aVar.t(str, str2, j));
            }
        } else {
            rangerResult.setErr(22);
        }
        return rangerResult;
    }

    private RangerResult b(int i, String str, Object obj, long j) {
        RangerResult rangerResult = new RangerResult(0, "");
        if (obj instanceof Status) {
            a aVar = a[i];
            if (aVar != null && i == aVar.b()) {
                rangerResult.setErr(aVar.d(i, str, (Status) obj, j));
            }
        } else {
            rangerResult.setErr(22);
        }
        return rangerResult;
    }

    private RangerResult c(int i, String str, String str2, String str3) {
        RangerResult rangerResult = new RangerResult(0, "");
        if (!TextUtils.isEmpty(str2) && !TextUtils.isEmpty(str)) {
            a aVar = a[i];
            if (aVar != null && i == aVar.b()) {
                rangerResult.setErr(aVar.a(i, str, str2, str3));
            }
        } else {
            rangerResult.setErr(22);
        }
        return rangerResult;
    }

    public static void d(int i, a aVar) {
        a[i] = aVar;
    }

    private RangerResult e(int i, String str, String str2, int i2) {
        RangerResult rangerResult = new RangerResult(0, "");
        if (!TextUtils.isEmpty(str) && !TextUtils.isEmpty(str2)) {
            a aVar = a[i];
            if (aVar != null && i == aVar.b()) {
                rangerResult.setErr(aVar.c(str, str2, i2));
            }
        } else {
            rangerResult.setErr(22);
        }
        return rangerResult;
    }
}
