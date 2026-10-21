package com.buy7.mapper.server;

import android.os.IBinder;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Corre con permisos de shell (lo arranca la app por depuración inalámbrica).
 * Recibe comandos por un socket local y los convierte en toques reales.
 *
 * Comandos: D id x y | M id x y | U id | T x y | QUIT
 */
public class InputServer {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(args[0]);
        String token = args[1];
        Injector inj = new Injector();
        ServerSocket ss = new ServerSocket();
        ss.setReuseAddress(true);
        ss.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port));
        while (true) {
            Socket s = ss.accept();
            try {
                serve(s, token, inj);
            } catch (Exception ignored) {
            } finally {
                inj.releaseAll();
                try {
                    s.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void serve(Socket s, String token, Injector inj) throws Exception {
        s.setTcpNoDelay(true);
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
        OutputStream out = s.getOutputStream();
        String first = in.readLine();
        if (first == null || !first.equals("AUTH " + token)) return;
        out.write("OK\n".getBytes());
        out.flush();
        String line;
        while ((line = in.readLine()) != null) {
            try {
                String[] p = line.split(" ");
                switch (p[0]) {
                    case "D":
                        inj.down(Integer.parseInt(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3]));
                        break;
                    case "M":
                        inj.move(Integer.parseInt(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3]));
                        break;
                    case "U":
                        inj.up(Integer.parseInt(p[1]));
                        break;
                    case "T":
                        inj.down(30, Float.parseFloat(p[1]), Float.parseFloat(p[2]));
                        Thread.sleep(40);
                        inj.up(30);
                        break;
                    case "QUIT":
                        inj.releaseAll();
                        System.exit(0);
                        break;
                    default:
                        break;
                }
            } catch (Exception ignored) {
            }
        }
    }

    /** Crea y envía eventos táctiles (multitáctil) al sistema. */
    static class Injector {
        private final Object manager;
        private final Method inject;
        private final LinkedHashMap<Integer, float[]> pointers = new LinkedHashMap<>();
        private long downTime;

        Injector() throws Exception {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Method getService = sm.getDeclaredMethod("getService", String.class);
            IBinder binder = (IBinder) getService.invoke(null, "input");
            Class<?> stub = Class.forName("android.hardware.input.IInputManager$Stub");
            manager = stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
            inject = manager.getClass().getMethod("injectInputEvent", InputEvent.class, int.class);
        }

        private int indexOf(int id) {
            int i = 0;
            for (Integer k : pointers.keySet()) {
                if (k == id) return i;
                i++;
            }
            return -1;
        }

        synchronized void down(int id, float x, float y) throws Exception {
            if (pointers.containsKey(id)) {
                move(id, x, y);
                return;
            }
            boolean first = pointers.isEmpty();
            if (first) downTime = SystemClock.uptimeMillis();
            pointers.put(id, new float[]{x, y});
            int action = first
                    ? MotionEvent.ACTION_DOWN
                    : (MotionEvent.ACTION_POINTER_DOWN | (indexOf(id) << MotionEvent.ACTION_POINTER_INDEX_SHIFT));
            send(action);
        }

        synchronized void move(int id, float x, float y) throws Exception {
            float[] p = pointers.get(id);
            if (p == null) return;
            p[0] = x;
            p[1] = y;
            send(MotionEvent.ACTION_MOVE);
        }

        synchronized void up(int id) throws Exception {
            int idx = indexOf(id);
            if (idx < 0) return;
            int action = pointers.size() == 1
                    ? MotionEvent.ACTION_UP
                    : (MotionEvent.ACTION_POINTER_UP | (idx << MotionEvent.ACTION_POINTER_INDEX_SHIFT));
            send(action);
            pointers.remove(id);
        }

        synchronized void releaseAll() {
            try {
                for (Integer id : new ArrayList<>(pointers.keySet())) up(id);
            } catch (Exception ignored) {
            }
            pointers.clear();
        }

        private void send(int action) throws Exception {
            int n = pointers.size();
            MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[n];
            MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[n];
            int i = 0;
            for (Map.Entry<Integer, float[]> e : pointers.entrySet()) {
                MotionEvent.PointerProperties pp = new MotionEvent.PointerProperties();
                pp.id = e.getKey();
                pp.toolType = MotionEvent.TOOL_TYPE_FINGER;
                MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
                pc.x = e.getValue()[0];
                pc.y = e.getValue()[1];
                pc.pressure = 1f;
                pc.size = 1f;
                props[i] = pp;
                coords[i] = pc;
                i++;
            }
            MotionEvent ev = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, n,
                    props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
            inject.invoke(manager, ev, 0);
            ev.recycle();
        }
    }
}
