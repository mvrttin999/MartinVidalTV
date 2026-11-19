package com.titan.ranger;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import org.json.JSONObject;

/**
 * Puente al SDK nativo ranger-jni (Xuper 4.35.0).
 * Call se registra via RegisterNatives en JNI_OnLoad: el nombre de clase,
 * metodo y firma deben coincidir exactamente. Los demas metodos postean al
 * HandlerThread, igual que el NativeJni original.
 */
public class NativeJni {
    public static String d;
    private static NativeJni singleton;

    private final String tag = NativeJni.class.getSimpleName();
    private Handler handler;
    private HandlerThread thread;

    static {
        System.loadLibrary("ranger-jni");
        System.loadLibrary("c++_shared");
        d = "";
    }

    private native String Call(String cmd, String args);

    private NativeJni() {
        thread = new HandlerThread("handlerRanger");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    public static NativeJni c() {
        if (singleton == null) {
            synchronized (NativeJni.class) {
                if (singleton == null) {
                    singleton = new NativeJni();
                    singleton.d(new c() {
                        @Override
                        public void a(String s) {
                            d = s;
                        }
                    });
                }
            }
        }
        return singleton;
    }

    /** Llamada nativa directa (sin postear). */
    public String invoke(String cmd, String args) {
        return Call(cmd, args);
    }

    private String call(String cmd, String args) {
        return Call(cmd, args);
    }

    private static String json(Object... kv) {
        try {
            JSONObject o = new JSONObject();
            for (int i = 0; i < kv.length; i += 2) o.put((String) kv[i], kv[i + 1]);
            return o.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Registro del callback de version (GetVersion). */
    public void d(final com.titan.ranger.c cb) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                String res = "";
                try {
                    res = new JSONObject(call("GetVersion", "{}")).optString("res", "");
                } catch (Exception e) {
                    Log.e(tag, "GetVersion", e);
                }
                if (cb != null) cb.a(res);
            }
        });
    }

    public void f(final String workPath) {
        handler.post(new Runnable() {
            @Override public void run() {
                call("Init", json("work_path", workPath));
            }
        });
    }

    public void g(final int instance, final String player, final int err) {
        handler.post(new Runnable() {
            @Override public void run() {
                call("SetPlayer", json("instance", instance, "player", player, "err", err));
            }
        });
    }

    public void t(final String env) {
        handler.post(new Runnable() {
            @Override public void run() {
                call("SetEnv", json("env", env));
            }
        });
    }

    public void r(final String entries) {
        handler.post(new Runnable() {
            @Override public void run() {
                call("SetEntries", json("entries", entries));
            }
        });
    }

    public void m(final String object, final String type, final String event) {
        handler.post(new Runnable() {
            @Override public void run() {
                call("OnSystemEvent", json("object", object, "type", type, "event", event));
            }
        });
    }

    public void o(final int instance, final String name, final String program, final String extra) {
        handler.post(new Runnable() {
            @Override public void run() {
                call("PrepareProgram", json("instance", instance, "name", name, "program", program, "extra", extra));
            }
        });
    }

    /** OnPlayerOperation: notifica al SDK las operaciones del reproductor. */
    public void l(final int instance, final String operation, final String data, final int err) {
        handler.post(new Runnable() {
            @Override public void run() {
                call("OnPlayerOperation", json("instance", instance, "operation", operation, "data", data, "err", err));
            }
        });
    }

    /** OnPlayerEvent: notifica al SDK los eventos del reproductor. */
    public void k(final int instance, final String event, final int err, final int extra,
                  final long data, final String reason) {
        handler.post(new Runnable() {
            @Override public void run() {
                call("OnPlayerEvent", json("instance", instance, "event", event, "err", err,
                    "extra", extra, "data", data, "reason", reason));
            }
        });
    }

    /** GetStatus con callback tipado (como el NativeJni.e original). */
    public void e(final int instance, final String name, final com.titan.ranger.b cb) {
        handler.post(new Runnable() {
            @Override public void run() {
                try {
                    String raw = call("GetStatus", json("instance", instance, "name", name));
                    String res = new JSONObject(raw).optString("res", "{}");
                    JSONObject s = new JSONObject(res);
                    Status st = new Status();
                    st.setPlay_url(s.optString("play_url", ""));
                    st.setMedia_buffer(s.optLong("media_buffer", 0L));
                    st.setSnapinfo_url(s.optString("snapinfo_url", ""));
                    if (cb != null) cb.a(st);
                } catch (Exception ex) {
                    Log.e(tag, "GetStatus", ex);
                }
            }
        });
    }
}
