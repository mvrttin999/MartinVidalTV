package zc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/* loaded from: /tmp/opencode/x435dex/classes3.dex */
public abstract class a {
    private static Gson a;

    public static Gson a() {
        if (a == null) {
            synchronized (a.class) {
                if (a == null) {
                    a = new GsonBuilder().disableHtmlEscaping().create();
                }
            }
        }
        return a;
    }
}
