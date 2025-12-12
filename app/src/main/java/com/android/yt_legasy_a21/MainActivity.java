package com.android.yt_legasy_a21;

import android.app.Activity;
import android.app.ProgressDialog;
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
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;


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
        registerForContextMenu(listView);

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
                // preloadVideo(tappedVideoId);
                // Toast.makeText(MainActivity.this, "読み込み中", Toast.LENGTH_SHORT).show();
                // new android.os.Handler().postDelayed(new Runnable() {
                //   @Override
                //    public void run() {
                playWithSelectedPlayer(tappedVideoUrl);
                //    }
                //}, 1000);
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

    //プレロード機能
    private void preloadVideo(final String videoId) {

        new Thread(new Runnable() {
            @Override
            public void run() {

                // InvidiousのURL
                String invidiousUrl = invidiousInstance + "/latest_version?id=" + videoId + "&itag=18";

                try {
                    // 最終URLを取得
                    String finalGoogleUrl = resolveRedirectUrl(invidiousUrl);
                    Log.d("YTClient", "最終URL: " + finalGoogleUrl);

                    // URL をプレロード
                    URL url = new URL(finalGoogleUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);

                    // レスポンスを読むが、画面には表示しない
                    InputStream is = conn.getInputStream();
                    byte[] buffer = new byte[2048];
                    while (is.read(buffer) != -1) {
                        // 読むだけで特に何もしない
                    }
                    is.close();
                    conn.disconnect();

                    Log.d("YTClient", "プレロード完了: " + finalGoogleUrl);

                } catch (Exception e) {
                    Log.e("YTClient", "プレロード失敗: " + invidiousUrl, e);
                }
            }
        }).start();
    }


    private void playWithSelectedPlayer(final String videoUrl) {
        new Thread(new Runnable() {
            @Override
            public void run() {

                //リダイレクト確認
                final String resolvedUrl = resolveRedirectUrl(videoUrl);
                Log.d("YTClient", "最終URL: " + resolvedUrl);

                //アクセス可能かチェック
                if (!isUrlAccessible(resolvedUrl)) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(MainActivity.this,
                                    "再生エラー（アクセス不可） もう一度お試しください",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                    return;
                }

                //VLCに渡す
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(Uri.parse(resolvedUrl), "video/mp4");
                            intent.setPackage(videoPlayerPackage);
                            startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this,
                                    "指定された再生アプリが見つかりません",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    private String resolveRedirectUrl(String urlString) {
        int maxRedirects = 10;  // 何段目まで読み込むか
        String currentUrl = urlString;

        for (int i = 0; i < maxRedirects; i++) {
            HttpURLConnection conn = null;

            try {
                URL url = new URL(currentUrl);
                conn = (HttpURLConnection) url.openConnection();

                conn.setInstanceFollowRedirects(false);  // 自動追跡しない
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int code = conn.getResponseCode();
                Log.d("YTClient", "Redirect step " + i + ": " + code + " / " + currentUrl);

                // リダイレクト判定
                if (code == HttpURLConnection.HTTP_MOVED_TEMP ||     // 302
                        code == HttpURLConnection.HTTP_MOVED_PERM ||     // 301
                        code == HttpURLConnection.HTTP_SEE_OTHER ||      // 303
                        code == 307 || code == 308) {                    // 307/308

                    String location = conn.getHeaderField("Location");
                    Log.d("YTClient", "Location: " + location);

                    if (location == null || location.length() == 0) {
                        break;  // Location が無ければ終了
                    }

                    URL base = new URL(currentUrl);
                    URL nextUrl = new URL(base, location);  // 相対 URL 対応

                    currentUrl = nextUrl.toString();
                } else {
                    // 200系 → 最終URL
                    return currentUrl;
                }

            } catch (Exception e) {
                Log.e("YTClient", "resolveRedirectUrl error: " + e);
                return currentUrl;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        return currentUrl; // ループ上限到達 → 最後の URL を返す
    }

    private boolean isUrlAccessible(String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int code = conn.getResponseCode();
            Log.d("YTClient", "アクセスチェック: " + code + " → " + urlString);

            return (code >= 200 && code < 300);

        } catch (Exception e) {
            Toast.makeText(this, "指定された再生アプリが見つかりません", Toast.LENGTH_SHORT).show();
            Log.e("YTClient", "isUrlAccessible エラー: " + e);
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    //コンテキストメニュー
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        if (v.getId() == R.id.listView) {
            menu.setHeaderTitle("サブメニュー");
            menu.add(0, 1, 0, "動画を保存");
            menu.add(0, 2, 1, "コピー: 動画URL");
            menu.add(0, 3, 2, "コピー: 動画ID");
        }
    }

    @SuppressWarnings("deprecation")
    private void copyTextLegacy(String text) {
        android.text.ClipboardManager cm =
                (android.text.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setText(text);
    }

    //メニュー選択
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info =
                (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

        int index = info.position;

        switch (item.getItemId()) {
            case 1: // 保存
                String saveUrl = videoUrls.get(index);
                String originalUrl = videoUrls.get(index);
                final String finalUrl = resolveRedirectUrl(originalUrl);
                String saveId = videoIds.get(index);
                Toast.makeText(this, "保存処理: " + saveId, Toast.LENGTH_SHORT).show();
                String fileName = saveId + ".mp4";

                downloadVideoWithProgress(finalUrl, fileName);
                return true;

            case 2: // URLコピー
                String url = videoUrls.get(index);
                copyTextLegacy(url);
                Toast.makeText(this, "URL をコピーしました", Toast.LENGTH_SHORT).show();
                return true;

            case 3: // IDコピー
                String id = videoIds.get(index);
                copyTextLegacy(id);
                Toast.makeText(this, "動画ID をコピーしました", Toast.LENGTH_SHORT).show();
                return true;
        }

        return super.onContextItemSelected(item);
    }


    //動画ダウンロード
    private void downloadVideoWithProgress(final String finalUrl, final String fileName) {

        // ダイアログ生成
        final ProgressDialog progressDialog = new ProgressDialog(MainActivity.this);
        progressDialog.setTitle("ダウンロード中");
        progressDialog.setMessage("準備しています...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setIndeterminate(false);（UIスレッドで作る必要がある）
        progressDialog.setMax(100);
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(new Runnable() {
            @Override
            public void run() {

                HttpURLConnection conn = null;
                InputStream is = null;
                FileOutputStream fos = null;

                try {
                    URL url = new URL(finalUrl);
                    conn = (HttpURLConnection) url.openConnection();

                    // ユーザーエージェント偽装
                    conn.setRequestProperty("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                    conn.setRequestProperty("Range", "bytes=0-");

                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.connect();

                    int code = conn.getResponseCode();
                    Log.d("YTClient", "ダウンロード開始 code=" + code);

                    if (code < 200 || code >= 300) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressDialog.dismiss();
                                Toast.makeText(MainActivity.this,
                                        "ダウンロード開始に失敗しました",
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                        return;
                    }

                    int contentLength = conn.getContentLength();
                    Log.d("YTClient", "ファイルサイズ: " + contentLength);

                    // 進捗最大値設定
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (contentLength > 0) {
                                progressDialog.setMax(100);
                            }
                        }
                    });

                    // 保存
                    File sdcard = new File("/sdcard/Download");
                    if (!sdcard.exists()) {
                        sdcard = new File("/mnt/sdcard/Download");
                    }
                    if (!sdcard.exists()) {
                        sdcard = getFilesDir(); // 最終手段
                    }

                    File outFile = new File(sdcard, fileName);
                    fos = new FileOutputStream(outFile);
                    Log.d("YTClient", "保存先パス: " + outFile.getAbsolutePath());
                    fos = new FileOutputStream(outFile);

                    is = conn.getInputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    int downloaded = 0;

                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                        downloaded += len;

                        if (contentLength > 0) {
                            final int percent = (int) ((downloaded * 100L) / contentLength);

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    progressDialog.setProgress(percent);
                                }
                            });
                        }
                    }

                    fos.flush();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            Toast.makeText(MainActivity.this,
                                    "保存完了: " + fileName,
                                    Toast.LENGTH_LONG).show();
                        }
                    });

                } catch (Exception e) {
                    Log.e("YTClient", "ダウンロード失敗", e);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            Toast.makeText(MainActivity.this,
                                    "ダウンロード中にエラーが発生しました",
                                    Toast.LENGTH_LONG).show();
                        }
                    });

                } finally {
                    try { if (is != null) is.close(); } catch (Exception ignored) {}
                    try { if (fos != null) fos.close(); } catch (Exception ignored) {}
                    try { if (conn != null) conn.disconnect(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

}