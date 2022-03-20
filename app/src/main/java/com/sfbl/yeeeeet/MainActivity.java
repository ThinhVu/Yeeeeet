package com.sfbl.yeeeeet;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.ion.Ion;
import com.sfbl.javaext.asyncAwait.Async;

import java.io.File;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
  private final String TAG = "Main";

  String proxyServer;
  EditText edtProxyServer;
  Button btnSetProxyServer;

  String storePath;
  EditText edtStorePath;
  Button btnStorePath;


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);


    StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
    StrictMode.setThreadPolicy(policy);

    Ion.getDefault(this).getConscryptMiddleware().enable(false);

    SharedPreferences sharedPreferences = this.getSharedPreferences("storage", MODE_PRIVATE);
    proxyServer = sharedPreferences.getString("proxy", "https://media.outstagram.xyz");
    storePath = sharedPreferences.getString("store-path", "/storage/emulated/0/Pictures/Instagram/");

    edtProxyServer = findViewById(R.id.editTextProxyServer);
    edtProxyServer.setText(proxyServer);

    btnSetProxyServer = findViewById(R.id.btnSetProxy);
    btnSetProxyServer.setOnClickListener(v -> {
      proxyServer = edtProxyServer.getText().toString();
      SharedPreferences.Editor editor = sharedPreferences.edit();
      editor.putString("proxy", proxyServer);
      editor.commit();
      editor.apply();
    });

    edtStorePath = findViewById(R.id.editStorePath);
    edtStorePath.setText(storePath);

    btnStorePath = findViewById(R.id.btnSetStorePath);
    btnStorePath.setOnClickListener(v -> {
      storePath = edtStorePath.getText().toString();
      SharedPreferences.Editor editor = sharedPreferences.edit();
      editor.putString("store-path", storePath);
      editor.commit();
      editor.apply();
    });

    // https://developer.android.com/training/sharing/receive
    Intent intent = getIntent();
    String action = intent.getAction();
    String type = intent.getType();

    if (Intent.ACTION_SEND.equals(action) && type != null) {
      if ("text/plain".equals(type)) {
        handleSendText(intent);
      }
    }
  }

  private void handleSendText(Intent intent) {
    try {
      // https://www.instagram.com/p/CbSrMPmp_lQ/?utm_medium=share_sheet
      String shareUrl = intent.getStringExtra(Intent.EXTRA_TEXT);
      String mediaUrl = proxyServer + "/v1?url=" + shareUrl.replace("/?utm_medium=share_sheet", "") + "?__a=1";
      parseContent(loadMetadata(mediaUrl));
    } catch (Exception e) {
      Log.e(TAG, "handleSendText: ", e);
    }
  }

  private void parseContent(String content) {
    JsonObject obj = new Gson().fromJson(content, JsonObject.class);
    JsonArray images = obj.get("items").getAsJsonArray();
    for (int i = 0; i < images.size(); ++i) {
      JsonObject item = images.get(i).getAsJsonObject();
      int carousel_media_count = 0;
      try {
        carousel_media_count = item.get("carousel_media_count").getAsInt();
      } catch (Exception ignored) {
      }
      if (carousel_media_count > 0) {
        JsonArray carousel_media = item.getAsJsonArray("carousel_media");
        for (int x = 0; x < carousel_media.size(); ++x) {
          downloadMedia(carousel_media.get(x).getAsJsonObject());
        }
      } else {
        downloadMedia(item);
      }
      this.finishAndRemoveTask();
    }
  }

  private void downloadMedia(JsonObject item) {
    String id = item.get("id").getAsString();
    JsonObject image_versions2 = item.getAsJsonObject("image_versions2");
    int original_width = item.get("original_width").getAsInt();
    int original_height = item.get("original_height").getAsInt();
    JsonArray candidates = image_versions2.getAsJsonArray("candidates");

    for (int x = 0; x < candidates.size(); ++x) {
      JsonObject candidate = candidates.get(x).getAsJsonObject();
      if (candidate.get("width").getAsInt() == original_width && candidate.get("height").getAsInt() == original_height) {
        String url = candidate.get("url").getAsString();
        String saveTo = storePath + id + ".jpg";
        try {
          downloadImage(url, saveTo);
        } catch (Exception e) {
          Log.e(TAG, "parseContent: ", e);
        }
        return;
      }
    }
  }

  private String loadMetadata(String metadataUrl) throws ExecutionException, InterruptedException {
    Log.d(TAG, "loadMetadata...");
    return Async.await(resolve -> {
          AsyncHttpGet get = new AsyncHttpGet(metadataUrl);
          AsyncHttpClient.getDefaultInstance().executeString(get, new AsyncHttpClient.StringCallback() {
            @Override
            public void onCompleted(Exception e, AsyncHttpResponse source, String result) {
              if (e != null) {
                Log.e(TAG, "loadMetadata: ", e);
                resolve.$(null);
              } else {
                Log.d(TAG, "loadMetadata: " + result);
                resolve.$(result);
              }
            }
          });
        }
    );
  }

  private void downloadImage(String imageUrl, String saveTo) {
    try {
      Log.d(TAG, "downloadImage: " + imageUrl + " then save to " + saveTo);
      Ion.with(getApplicationContext())
          .load(imageUrl)
          .setTimeout(1000000)
          .write(new File(saveTo))
          .setCallback((e, file) -> {
            if (e != null) {
              Log.e(TAG, "DOWNLOAD_FILE:", e);
            } else {
              Log.d(TAG, "DOWNLOAD_FILE: Completed");
            }
          });
    } catch (Exception e) {
      Log.e(TAG, "downloadImage: ", e);
    }
  }
}