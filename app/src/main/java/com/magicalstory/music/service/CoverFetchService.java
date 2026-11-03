package com.magicalstory.music.service;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.magicalstory.music.R;
import com.magicalstory.music.model.Album;
import com.magicalstory.music.model.Artist;
import com.magicalstory.music.utils.glide.CoverFallbackUtils;
import com.magicalstory.music.utils.network.NetUtils;
import com.magicalstory.music.utils.text.RawTextReader;

import org.litepal.LitePal;

import java.util.List;

import okhttp3.Request;
import okhttp3.Response;

/**
 * 封面获取服务
 * 用于后台批量获取和处理专辑和歌手的封面
 */
public class CoverFetchService extends IntentService {

    private static final String TAG = "CoverFetchService";

    public static final String ACTION_FETCH_ALL_COVERS = "com.magicalstory.music.service.FETCH_ALL_COVERS";
    public static final String ACTION_FETCH_ALBUM_COVERS = "com.magicalstory.music.service.FETCH_ALBUM_COVERS";
    public static final String ACTION_FETCH_ARTIST_COVERS = "com.magicalstory.music.service.FETCH_ARTIST_COVERS";

    public CoverFetchService() {
        super("CoverFetchService");
    }

    /**
     * 启动批量获取所有封面的服务
     */
    public static void startFetchAllCovers(Context context) {
        Intent intent = new Intent(context, CoverFetchService.class);
        intent.setAction(ACTION_FETCH_ALL_COVERS);
        context.startService(intent);
    }

    /**
     * 启动批量获取专辑封面的服务
     */
    public static void startFetchAlbumCovers(Context context) {
        Intent intent = new Intent(context, CoverFetchService.class);
        intent.setAction(ACTION_FETCH_ALBUM_COVERS);
        context.startService(intent);
    }

    /**
     * 启动批量获取歌手封面的服务
     */
    public static void startFetchArtistCovers(Context context) {
        Intent intent = new Intent(context, CoverFetchService.class);
        intent.setAction(ACTION_FETCH_ARTIST_COVERS);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();

            if (ACTION_FETCH_ALL_COVERS.equals(action)) {
                handleFetchAllCovers();
            } else if (ACTION_FETCH_ALBUM_COVERS.equals(action)) {
                handleFetchAlbumCovers();
            } else if (ACTION_FETCH_ARTIST_COVERS.equals(action)) {
                handleFetchArtistCovers();
            }
        }
    }

    /**
     * 处理获取所有封面的请求
     */
    private void handleFetchAllCovers() {
        Log.d(TAG, "开始批量获取所有封面...");

        // 获取数据库中所有的专辑
        List<Album> allAlbums = LitePal.findAll(Album.class);
        Log.d(TAG, "数据库中共有 " + allAlbums.size() + " 个专辑");

        // 获取数据库中所有的歌手
        List<Artist> allArtists = LitePal.findAll(Artist.class);
        Log.d(TAG, "数据库中共有 " + allArtists.size() + " 个歌手");

        // 先处理专辑封面
        batchProcessAlbumCovers(allAlbums);

        // 再处理歌手封面
        batchProcessArtistCovers(allArtists);

        Log.d(TAG, "批量获取所有封面完成");
    }

    /**
     * 处理获取专辑封面的请求
     */
    private void handleFetchAlbumCovers() {
        Log.d(TAG, "开始批量获取专辑封面...");

        List<Album> allAlbums = LitePal.findAll(Album.class);
        Log.d(TAG, "数据库中共有 " + allAlbums.size() + " 个专辑");

        batchProcessAlbumCovers(allAlbums);

        Log.d(TAG, "批量获取专辑封面完成");
    }

    /**
     * 处理获取歌手封面的请求
     */
    private void handleFetchArtistCovers() {
        Log.d(TAG, "开始批量获取歌手封面...");

        List<Artist> allArtists = LitePal.findAll(Artist.class);
        Log.d(TAG, "数据库中共有 " + allArtists.size() + " 个歌手");

        batchProcessArtistCovers(allArtists);

        Log.d(TAG, "批量获取歌手封面完成");
    }

    /**
     * 批量处理专辑封面
     */
    private void batchProcessAlbumCovers(List<Album> albums) {
        if (albums == null || albums.isEmpty()) {
            return;
        }

        int processedCount = 0;
        int successCount = 0;

        for (Album album : albums) {
            processedCount++;

            // 检查专辑是否已经有封面
            if (TextUtils.isEmpty(album.getAlbumArt())) {
                Log.d(TAG, "处理专辑封面: " + album.getAlbumName() + " - " + album.getArtist() + " (" + processedCount + "/" + albums.size() + ")");

                // 使用封面回退逻辑获取封面
                if (CoverFallbackUtils.setAlbumFallbackCover(album)) {
                    successCount++;
                    Log.d(TAG, "成功为专辑 " + album.getAlbumName() + " 设置封面");
                } else {
                    Log.d(TAG, "专辑 " + album.getAlbumName() + " 未找到可用封面");
                }
            } else {
                Log.d(TAG, "专辑 " + album.getAlbumName() + " 已有封面，跳过");
            }

            // 每处理10个专辑输出一次进度
            if (processedCount % 10 == 0) {
                Log.d(TAG, "专辑封面处理进度: " + processedCount + "/" + albums.size() + " (成功: " + successCount + ")");
            }
        }

        Log.d(TAG, "专辑封面处理完成: 处理了 " + processedCount + " 个专辑，成功设置 " + successCount + " 个封面");
    }

    /**
     * 批量处理歌手封面
     */
    private void batchProcessArtistCovers(List<Artist> artists) {
        if (artists == null || artists.isEmpty()) {
            return;
        }

        int processedCount = 0;
        int successCount = 0;
        int apiRequestCount = 0;

        long time_start = System.currentTimeMillis();
        String singerName = RawTextReader.getRawText(this, R.raw.singer_name).replace(".","");
        long time_end = System.currentTimeMillis();
        System.out.println("读取耗时 = " + (time_end - time_start));
        for (Artist artist : artists) {
            processedCount++;

            // 检查歌手是否已经有封面或已经尝试过获取封面
            if (TextUtils.isEmpty(artist.getCoverUrl()) && !artist.isCoverFetched()) {
                Log.d(TAG, "处理歌手封面: " + artist.getArtistName() + " (" + processedCount + "/" + artists.size() + ")");
                if (singerName.contains(artist.getArtistName().split(";")[0])) {
                    artist.setCoverUrl("https://cdn.harmony5.cn/singerCover/singerCover/" + artist.getArtistName().split(";")[0] + ".png");
                    artist.setCoverFetched(true);
                    System.out.println("歌手 = " + artist.getArtistName() + " 有CDN封面");
                    successCount++;
                    artist.save();
                } else if (fetchArtistCoverFromAPI(artist)) {
                    successCount++;
                    apiRequestCount++;
                    Log.d(TAG, "成功从API获取歌手 " + artist.getArtistName() + " 的封面");
                } else {
                    // 如果API获取失败，则尝试使用封面回退逻辑获取封面
                    if (CoverFallbackUtils.setArtistFallbackCover(artist)) {
                        successCount++;
                        Log.d(TAG, "成功为歌手 " + artist.getArtistName() + " 设置回退封面");
                    } else {
                        Log.d(TAG, "歌手 " + artist.getArtistName() + " 未找到可用封面");
                    }
                }
            } else if (!TextUtils.isEmpty(artist.getCoverUrl())) {
                Log.d(TAG, "歌手 " + artist.getArtistName() + " 已有封面，跳过");
            } else {
                Log.d(TAG, "歌手 " + artist.getArtistName() + " 已尝试过获取封面，跳过");
            }

            // 每处理10个歌手输出一次进度
            if (processedCount % 10 == 0) {
                Log.d(TAG, "歌手封面处理进度: " + processedCount + "/" + artists.size() + " (成功: " + successCount + ", API请求: " + apiRequestCount + ")");
            }

        }

        singerName = null;
        Log.d(TAG, "歌手封面处理完成: 处理了 " + processedCount + " 个歌手，成功设置 " + successCount + " 个封面 (API请求: " + apiRequestCount + ")");
    }

    /**
     * 从API获取歌手封面
     */
    private boolean fetchArtistCoverFromAPI(Artist artist) {
        try {
            String artistName = artist.getArtistName();
            String encodedArtistName = java.net.URLEncoder.encode(artistName.split(";")[0], "UTF-8");
            String url = "https://music.163.com/api/search/get/web?s=" + encodedArtistName + "&type=100";

            // 构建完整的浏览器请求头
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 " +
                            "Safari/537.36")
                    .addHeader("Accept", "application/json, text/plain, */*")
                    .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .addHeader("Accept-Encoding", "identity")
                    .addHeader("Referer", "https://music.163.com/")
                    .addHeader("Origin", "https://music.163.com")
                    .addHeader("Sec-Fetch-Dest", "empty")
                    .addHeader("Sec-Fetch-Mode", "cors")
                    .addHeader("Sec-Fetch-Site", "same-origin")
                    .addHeader("Cache-Control", "no-cache")
                    .addHeader("Pragma", "no-cache")
                    .addHeader("Connection", "keep-alive")
                    .addHeader("DNT", "1").addHeader("Cookie", "NTES_P_UTID=XuaJneU5JxroE1YViYG1gL0u2w8RGAdj|1752402317; " +
                            "NTES_CMT_USER_INFO=470198709%7C%E6%9C%89%E6%80%81%E5%BA%A6%E7%BD%91%E5%8F%8B0s1GCR%7Chttp%3A%2F%2Fcms-bucket.nosdn.127" +
                            ".net%2F2018%2F08%2F13%2F078ea9f65d954410b62a52ac773875a1.jpeg%7Cfalse%7CcWl0YW5qdW5AMTYzLmNvbQ%3D%3D; " +
                            "NTES_PASSPORT=ohpNlpR_tDq1v2rcMUeolW7tBcUmaIDJeP6ZRO5VB4sjxaw1xYbLvehNQx7xaaR5HSFx2MvlIac079YMhF6ajJoXe.H3R8tLrBGFA06" +
                            ".03ArljGIBBQVDpHI5Q9Wr76i5xeeON50DjFpy0AqQDhEkVhYR9csx7GxELbUlt12903FIASKjbF9KPfKL; P_INFO=qitanjun@163" +
                            ".com|1752402317|1|mail163|00&99|hun&1752366796&mailmaster_android#CN&null#10#0#0|139232&0|ipet_client" +
                            "&mailmaster_android&mailmaster_mac&mail163&newsclient|qitanjun@163.com; nts_mail_user=qitanjun@163.com:-1:1; " +
                            "NMTID=00OTG9HcDButiQIZEC9joHUO1lwaYoAAAGYCOoiBg");

            Request request = requestBuilder.build();
            Response response = NetUtils.getInstance().mOkHttpClient.newCall(request).execute();

            // 如果自定义请求失败，尝试使用现有的方法
            if (!response.isSuccessful()) {
                Log.w(TAG, "自定义请求失败，尝试使用现有方法");
                response = NetUtils.getInstance().getDataSynFromNetPC(url);
            }

            if (response != null && response.isSuccessful()) {
                String jsonResponse = response.body().string();
                Log.d(TAG, "歌手 " + artistName + " 的原始数据长度: " + jsonResponse.length());
                System.out.println("歌手 " + artistName + " 的数据: " + jsonResponse);
                return parseAndSaveArtistCover(artist, jsonResponse);
            } else {
                Log.w(TAG, "API请求失败，状态码: " + (response != null ? response.code() : "null"));
                // 获取失败，标记为已尝试过
                artist.setCoverFetched(true);
                artist.save();
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "从API获取歌手封面失败: " + e.getMessage(), e);
            // 异常情况下也标记为已尝试过
            artist.setCoverFetched(true);
            artist.save();
            return false;
        }
    }

    /**
     * 解析并保存艺术家封面
     */
    private boolean parseAndSaveArtistCover(Artist artist, String jsonResponse) {
        try {
            // 检查响应是否为空或无效
            if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                Log.w(TAG, "API返回空响应");
                artist.setCoverFetched(true);
                artist.save();
                return false;
            }

            // 检查响应是否包含乱码（压缩数据）
            if (jsonResponse.length() < 10 || !jsonResponse.trim().startsWith("{")) {
                Log.w(TAG, "API返回的数据可能被压缩或格式错误，长度: " + jsonResponse.length());
                artist.setCoverFetched(true);
                artist.save();
                return false;
            }

            JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
            if (jsonObject.has("code") && jsonObject.get("code").getAsInt() == 200 && jsonObject.has("result")) {
                JsonObject result = jsonObject.getAsJsonObject("result");
                if (result.has("artists")) {
                    JsonArray artists = result.getAsJsonArray("artists");
                    if (!artists.isEmpty()) {
                        JsonObject artistInfo = artists.get(0).getAsJsonObject();
                        if (artistInfo.has("picUrl")) {
                            String picUrl = artistInfo.get("picUrl").getAsString();
                            if (picUrl != null && !picUrl.isEmpty()) {
                                Log.d(TAG, "成功获取歌手 " + artist.getArtistName() + " 的封面URL: " + picUrl);
                                // 更新数据库
                                artist.setCoverUrl(picUrl);
                                artist.setCoverFetched(true);
                                artist.save();

                                return true;
                            } else {
                                Log.w(TAG, "歌手 " + artist.getArtistName() + " 的封面URL为空");
                            }
                        } else {
                            Log.w(TAG, "歌手 " + artist.getArtistName() + " 的响应中没有picUrl字段");
                        }
                    } else {
                        Log.w(TAG, "歌手 " + artist.getArtistName() + " 的响应中没有找到艺术家信息");
                    }
                } else {
                    Log.w(TAG, "歌手 " + artist.getArtistName() + " 的响应中没有artists字段");
                }
            } else {
                Log.w(TAG, "歌手 " + artist.getArtistName() + " 的API响应状态码不是200或没有result字段");
            }

            // 如果没有获取到封面，也标记为已尝试过
            artist.setCoverFetched(true);
            artist.save();
            return false;
        } catch (Exception e) {
            Log.e(TAG, "解析艺术家封面失败: " + e.getMessage(), e);
            // 异常情况下也标记为已尝试过
            artist.setCoverFetched(true);
            artist.save();
            return false;
        }
    }
} 