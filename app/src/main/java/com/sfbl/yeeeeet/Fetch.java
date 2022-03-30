package com.sfbl.yeeeeet;

import android.os.Handler;
import android.os.Looper;

import com.sfbl.javaext.delegate.Action;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public final class Fetch {
  private static final int TIMEOUT_MS = 1000000;

  private static void getRequest(String url, Action._1<HttpURLConnection> cb) throws IOException {
    final HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setRequestMethod("GET");
    connection.setConnectTimeout(TIMEOUT_MS);
    connection.setReadTimeout(TIMEOUT_MS);
    try {
      connection.connect();
      cb.$(connection);
    } finally {
      connection.disconnect();
    }
  }

  public static void download(String url, String toFile) throws IOException {
    File f = new File(toFile);
    f.createNewFile();
    FileOutputStream fo = new FileOutputStream(f);
    getRequest(url, conn -> {
      try {
        switch (conn.getResponseCode()) {
          case 200:
          case 201:
            InputStream is = conn.getInputStream();
            byte[] buffer = new byte[1024];
            int byteRead = 0;
            while ((byteRead = is.read(buffer)) > 0) {
              fo.write(buffer, 0, byteRead);
            }
            is.close();
            fo.flush();
            fo.close();
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
  }

  public static void getAsync(final String url, final Action._1<String> onFetch) {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Handler handler = new Handler(Looper.getMainLooper());
    executor.execute(() -> {
      AtomicReference<String> responseData = new AtomicReference<>();
      try {
        getRequest(url, conn -> {
          try {
            switch (conn.getResponseCode()) {
              case 200:
              case 201:
                final BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                final StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null)
                  builder.append(line).append("\n");
                reader.close();
                responseData.set(builder.toString());
            }
          } catch (Exception e) {
            e.printStackTrace();
          }
        });
      } catch (IOException e) {
        e.printStackTrace();
      }

      String finalResponseData = responseData.get();
      handler.post(() -> onFetch.$(finalResponseData));
    });
  }
}