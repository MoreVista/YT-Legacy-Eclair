package com.android.yt_legasy_a21;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;

import org.json.*;

import java.io.*;
import java.net.*;
import java.util.ArrayList;

public class MainActivity extends Activity {

    EditText editSearch;
    Button btnSearch;
    ListView listView;
    ArrayList<String> titles = new ArrayList<String>();
    ArrayList<String> videoUrls = new ArrayList<String>();
    ArrayList<String> videoIds = new ArrayList<String>();
    ArrayList<String> thumbnailUrls = new ArrayList<String>();



    ArrayAdapter<String> adapter;


    // 設定用
    SharedPreferences prefs;
    String invidiousInstance = "http://192.168.2.12:3000";
    String videoPlayerPackage = "org.videolan.vlc";
    //String videoPlayerPackage = "com.redirectin.rockplayer.android.unified.lite";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // SharedPreferences初期化
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        invidiousInstance = prefs.getString("invidious_instance", "http://192.168.2.12:3000");
        videoPlayerPackage = prefs.getString("video_player", "org.videolan.vlc");

        editSearch = (EditText) findViewById(R.id.editSearch);
        btnSearch = (Button) findViewById(R.id.btnSearch);
        listView = (ListView) findViewById(R.id.listView);

        adapter = new VideoListAdapter(this, titles, thumbnailUrls);
        listView.setAdapter(adapter);
        VideoListAdapter customAdapter = new VideoListAdapter(this, titles, thumbnailUrls);
        listView.setAdapter(customAdapter);
        adapter = customAdapter;

        btnSearch.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String query = editSearch.getText().toString().trim();
                if (!query.equals("")) {
                    if (isVideoId(query)) {
                        // 入力が動画ID(直接再生)
                        String videoUrl = invidiousInstance + "/latest_version?id=" + query;
                        Log.d("YTClient", "入力された動画ID: " + query);
                        Log.d("YTClient", "生成されたURL: " + videoUrl);
                        playWithSelectedPlayer(videoUrl);
                    } else {
                        // 通常の検索
                        searchVideos(query);
                    }
                }
            }
        });


        // クリックリスナー
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                if (position < 0 || position >= videoUrls.size()) {
                    Toast.makeText(MainActivity.this, "無効な選択です", Toast.LENGTH_SHORT).show();
                    return;
                }

                final String tappedVideoUrl = videoUrls.get(position); // MP4直リンク
                final String tappedVideoId = videoIds.get(position);

                Log.d("YTClient", "タップされたビデオID: " + tappedVideoId);
                Log.d("YTClient", "タップされたMP4直リンク: " + tappedVideoUrl);
                //preloadVideo(tappedVideoId);
                //Toast.makeText(MainActivity.this, "読み込み中", Toast.LENGTH_SHORT).show();
                //new android.os.Handler().postDelayed(new Runnable() {
                //    @Override
                //    public void run() {
                playWithSelectedPlayer(tappedVideoUrl);
                //    }
                //}, 5000);
            }
        });
    }

    // メニュー生成
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 0, 0, "設定");
        return true;
    }

    // メニュー選択時
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 0) {
            showSettingsDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSettingsDialog() {
        final EditText inputInstance = new EditText(this);
        inputInstance.setText(invidiousInstance);

        final EditText inputPlayer = new EditText(this);
        inputPlayer.setText(videoPlayerPackage);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(new TextView(this) {{
            setText("InvidiousインスタンスURL:");
        }});
        layout.addView(inputInstance);
        layout.addView(new TextView(this) {{
            setText("動画再生アプリパッケージ名:");
        }});
        layout.addView(inputPlayer);

        new AlertDialog.Builder(this)
                .setTitle("設定")
                .setView(layout)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        invidiousInstance = inputInstance.getText().toString().trim();
                        videoPlayerPackage = inputPlayer.getText().toString().trim();
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("invidious_instance", invidiousInstance);
                        editor.putString("video_player", videoPlayerPackage);
                        editor.commit();
                        Toast.makeText(MainActivity.this, "設定保存しました", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void searchVideos(final String query) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    String encoded = URLEncoder.encode(query, "UTF-8");
                    Log.d("YTClient", "検索開始: " + query);
                    URL url = new URL(invidiousInstance + "/api/v1/search?q=" + encoded);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    InputStream is = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    parseJson(sb.toString());

                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(MainActivity.this, "invidiousサーバーへのアクセスに失敗しました", Toast.LENGTH_SHORT).show();
                        }
                    });
                    e.printStackTrace();
                    Log.e("YTClient", "検索失敗", e);
                }
            }
        }).start();
    }

    // 動画ID判定
    private boolean isVideoId(String input) {
        return input.matches("^[a-zA-Z0-9_-]{11}$");
    }

    private void parseJson(final String jsonStr) {
        try {
            JSONArray array = new JSONArray(jsonStr);
            Log.d("YTClient", "取得したJSON: " + jsonStr);

            titles.clear();
            videoUrls.clear();
            videoIds.clear();
            thumbnailUrls.clear();

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);

                if (!obj.has("videoId")) continue;

                String title = obj.optString("title", "無題");
                String videoId = obj.getString("videoId");

                // MP4直リンク
                String videoUrl = invidiousInstance + "/latest_version?id=" + videoId + "&itag=18";

                // サムネ取得
                String thumbnailUrl = "";
                if (obj.has("videoThumbnails")) {
                    JSONArray thumbs = obj.getJSONArray("videoThumbnails");
                    if (thumbs.length() > 0) {
                        JSONObject thumb0 = thumbs.getJSONObject(0);
                        String relUrl = thumb0.optString("url", "");
                        if (relUrl != null && relUrl.length() > 0) {
                            if (relUrl.startsWith("http")) {
                                thumbnailUrl = relUrl;
                            } else {
                                thumbnailUrl = invidiousInstance + relUrl;
                            }
                        }
                    }
                }

                titles.add(title);
                videoUrls.add(videoUrl);
                videoIds.add(videoId);
                thumbnailUrls.add(thumbnailUrl);

                Log.d("YTClient", "title: " + title);
                Log.d("YTClient", "videoUrl: " + videoUrl);
                Log.d("YTClient", "videoId: " + videoId);
            }

            // UIスレッドで ListView 更新
            runOnUiThread(new Runnable() {
                public void run() {
                    adapter.notifyDataSetChanged();
                    Log.d("YTClient", "リスト更新完了: " + titles.size() + "件");
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            Log.e("YTClient", "JSONパース失敗", e);
            runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(MainActivity.this, "JSONを展開できません", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }


    //プレロード機能(未使用)
    private void preloadVideo(String videoId) {
        final String preloadUrl = invidiousInstance + "/latest_version?id=" + videoId;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(preloadUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);

                    // レスポンスを読むが、画面には表示しない
                    InputStream is = conn.getInputStream();
                    while (is.read() != -1) {
                        // 読むだけで特に何もしない
                    }
                    is.close();
                    conn.disconnect();

                    Log.d("YTClient", "プレロード完了: " + preloadUrl);
                } catch (Exception e) {
                    Log.e("YTClient", "プレロード失敗: " + preloadUrl, e);
                }
            }
        }).start();
    }

    private void playWithSelectedPlayer(String videoUrl) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(videoUrl), "video/mp4");
            intent.setPackage(videoPlayerPackage);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "指定された再生アプリが見つかりません", Toast.LENGTH_SHORT).show();
        }
    }
}
