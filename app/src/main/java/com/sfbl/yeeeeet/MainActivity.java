package com.sfbl.yeeeeet;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.StrictMode;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity {
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

    // Ion.getDefault(this).getConscryptMiddleware().enable(false);

    SharedPreferences sharedPreferences = this.getSharedPreferences("storage", MODE_PRIVATE);
    SharedPreferences.Editor editor = sharedPreferences.edit();
    proxyServer = sharedPreferences.getString("proxy", "https://media.outstagram.xyz");
    storePath = sharedPreferences.getString("store-path", "/storage/emulated/0/Pictures/Instagram/");

    edtProxyServer = findViewById(R.id.editTextProxyServer);
    edtProxyServer.setText(proxyServer);

    btnSetProxyServer = findViewById(R.id.btnSetProxy);
    btnSetProxyServer.setOnClickListener(v -> {
      proxyServer = edtProxyServer.getText().toString();
      editor.putString("proxy", proxyServer);
      editor.apply();
    });

    edtStorePath = findViewById(R.id.editStorePath);
    edtStorePath.setText(storePath);

    btnStorePath = findViewById(R.id.btnSetStorePath);
    btnStorePath.setOnClickListener(v -> {
      storePath = edtStorePath.getText().toString();
      editor.putString("store-path", storePath);
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
      Fetch.getAsync(mediaUrl, this::parseContent);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void parseContent(String content) {
    try {
      if (null == content || content.equals("{}")) {
        runOnUiThread(() -> {
          Toast
              .makeText(this.getApplicationContext(), "Failed to load metadata!", Toast.LENGTH_LONG)
              .show();
        });
        this.finishAndRemoveTask();
      }

      JSONObject obj = new JSONObject(content);

      try {
        String status = obj.getString("status");
        if (status.equals("fail")) {
          runOnUiThread(() -> Toast.makeText(this.getApplicationContext(), "Proxy server has been blocked by Instagram. Try another server!", Toast.LENGTH_LONG).show());
          return;
        }
      } catch (Exception ignored) {}

      JSONArray images = obj.getJSONArray("items");
      for (int i = 0; i < images.length(); ++i) {
        JSONObject item = images.getJSONObject(i);
        int carousel_media_count = 0;
        try {
          carousel_media_count = item.getInt("carousel_media_count");
        } catch (Exception ignored) {
        }
        if (carousel_media_count > 0) {
          JSONArray carousel_media = item.getJSONArray("carousel_media");
          for (int x = 0; x < carousel_media.length(); ++x) {
            downloadMedia(carousel_media.getJSONObject(x));
          }
        } else {
          downloadMedia(item);
        }
        this.finishAndRemoveTask();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void downloadMedia(JSONObject item) throws JSONException {

    String id = item.getString("id");
    try {
      JSONArray video_versions = item.getJSONArray("video_versions");
      if (video_versions != null && video_versions.length() > 0) {
        JSONObject video_version = video_versions.getJSONObject(0);
        Fetch.download(video_version.getString("url"), storePath + id + ".mp4");
        return;
      }
    } catch (Exception ignored) {}


    JSONObject image_versions2 = item.getJSONObject("image_versions2");
    int original_width = item.getInt("original_width");
    int original_height = item.getInt("original_height");
    JSONArray candidates = image_versions2.getJSONArray("candidates");

    for (int x = 0; x < candidates.length(); ++x) {
      JSONObject candidate = candidates.getJSONObject(x);
      if (candidate.getInt("width") == original_width && candidate.getInt("height") == original_height) {
        try {
          Fetch.download(candidate.getString("url"), storePath + id + ".jpg");
        } catch (Exception e) {
          e.printStackTrace();
        }
        return;
      }
    }
  }
}