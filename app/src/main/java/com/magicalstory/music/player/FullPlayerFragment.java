package com.magicalstory.music.player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;


import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import com.magicalstory.music.MainActivity;
import com.magicalstory.music.R;
import com.magicalstory.music.dialog.PlaylistBottomSheetDialogFragment;
import com.magicalstory.music.dialog.SonglistBottomSheetDialogFragment;
import com.magicalstory.music.dialog.SongBottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;

import androidx.appcompat.app.AlertDialog;

import com.magicalstory.music.base.BaseFragment;
import com.magicalstory.music.databinding.FragmentFullPlayerBinding;
import com.magicalstory.music.model.Song;
import com.magicalstory.music.model.LyricLine;
import com.magicalstory.music.utils.app.ToastUtils;
import com.magicalstory.music.utils.favorite.FavoriteManager;
import com.magicalstory.music.utils.glide.BlurUtils;
import com.magicalstory.music.utils.glide.ColorExtractor;
import com.magicalstory.music.utils.glide.GlideUtils;
import com.magicalstory.music.utils.lyrics.LyricsParser;
import com.magicalstory.music.utils.text.TimeUtils;

/**
 * 全屏播放器Fragment - 使用MediaControllerHelper统一管理播放状态
 */
@UnstableApi
public class FullPlayerFragment extends BaseFragment<FragmentFullPlayerBinding> {

    private static final String TAG = "FullPlayerFragment";

    // 背景类型常量
    private static final int BACKGROUND_TYPE_DEFAULT = 0;
    private static final int BACKGROUND_TYPE_SOLID = 1;
    private static final int BACKGROUND_TYPE_GRADIENT = 2;
    private static final int BACKGROUND_TYPE_BLUR = 3;

    // MediaControllerHelper相关
    private MediaControllerHelper controllerHelper;

    // 播放状态监听器
    private final MediaControllerHelper.PlaybackStateListener playbackStateListener = new MediaControllerHelper.PlaybackStateListener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            Log.d(TAG, "FullPlayerFragment收到播放状态改变: " + playbackState);
            updatePlaybackState();
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            Log.d(TAG, "FullPlayerFragment收到播放状态改变: " + isPlaying);
            updatePlayButton(isPlaying);
        }

        @Override
        public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            Log.d(TAG, "FullPlayerFragment收到媒体项切换: " + (mediaItem != null ? mediaItem.mediaId : "null"));
            updateCurrentSong();


        }

        @Override
        public void progressInit(long dur, long progress) {
            int progress1 = (int) (((float) progress / dur) * 100);
            binding.seekBarProgress.setProgress(progress1);
            binding.txtCurrentTime.setText(TimeUtils.formatTime(progress));

        }

        @Override
        public void onPlayerError(PlaybackException error) {
            Log.e(TAG, "FullPlayerFragment收到播放错误: " + error.getMessage(), error);
            // 可以在这里处理错误，比如显示错误信息
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {
            Log.d(TAG, "FullPlayerFragment收到重复模式改变: " + repeatMode);
            currentPlayMode = repeatMode;
            updatePlayModeButton(repeatMode);
        }

        @Override
        public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            Log.d(TAG, "FullPlayerFragment收到随机模式改变: " + shuffleModeEnabled);
            updateShuffleButton(shuffleModeEnabled);
        }

        @Override
        public void onPositionChanged(long position, long duration) {
            updateProgressFromPosition(position, duration);
        }

        /**
         * 从位置信息更新进度条
         */
        private void updateProgressFromPosition(long position, long duration) {
            if (duration > 0 && binding != null) {
                if (!isUserSeeking) {
                    int progress = (int) (((float) position / duration) * 1000);
                    binding.seekBarProgress.setProgress(progress);

                    // 更新时间显示
                    binding.txtCurrentTime.setText(TimeUtils.formatTime(position));
                    binding.txtTotalTime.setText(TimeUtils.formatTime(duration));
                }
            }
        }
    };

    private boolean isUserSeeking = false;
    private FavoriteManager favoriteManager;

    // 播放模式
    private int currentPlayMode = Player.REPEAT_MODE_OFF;

    // 背景类型
    private int currentBackgroundType = BACKGROUND_TYPE_GRADIENT;

    // 歌词相关
    private boolean isLyricsVisible = false;
    private final List<LyricLine> currentLyrics = new ArrayList<>();
    private long currentSongId = -1; // 记录当前歌曲ID，用于判断是否需要重新加载歌词
    private boolean hasLyrics = false; // 标记是否有歌词

    // 当前专辑封面和提取的颜色
    private Bitmap currentAlbumBitmap;
    private int dominantColor = Color.parseColor("#3353BE");
    private int darkColor = Color.parseColor("#1A237E");

    // 颜色资源记录
    private int defaultTextPrimaryColor;
    private int defaultTextSecondaryColor;
    private int defaultFavoriteColor;
    private final int whiteColor = Color.WHITE;
    private final int grayColor = Color.parseColor("#E0E0E0");

    private final static int PLAY_STATE_PAUSE = 0;
    private final static int PLAY_STATE_PLAYING = 1;
    private int playButtonStatus = PLAY_STATE_PAUSE;

    // 进度更新相关
    private final Handler progressUpdateHandler = new Handler(Looper.getMainLooper());
    private Runnable progressUpdateRunnable;

    // 播放按钮更新相关
    private final Handler playButtonUpdateHandler = new Handler(Looper.getMainLooper());
    private Runnable playButtonUpdateRunnable;

    // 播放列表底部弹出窗口相关
    private PlaylistBottomSheetDialogFragment playlistBottomSheet;

    // 睡眠定时器相关
    private boolean isSleepTimerActive = false;
    private long sleepTimerRemainingTime = 0; // 剩余时间（毫秒）
    private final Handler sleepTimerHandler = new Handler(Looper.getMainLooper());
    private Runnable sleepTimerRunnable;
    private Runnable sleepTimerUpdateRunnable;

    // 歌词更新广播接收器
    private final BroadcastReceiver lyricsUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.magicalstory.music.LYRICS_UPDATED".equals(intent.getAction())) {
                long songId = intent.getLongExtra("song_id", -1);
                String songPath = intent.getStringExtra("song_path");
                
                if (controllerHelper != null) {
                    Song currentSong = controllerHelper.getCurrentSong();
                    if (currentSong != null && currentSong.getId() == songId) {
                        Log.d(TAG, "收到歌词更新广播，重新加载歌词");
                        loadLyrics(currentSong);
                    }
                }
            }
        }
    };

    @Override
    protected FragmentFullPlayerBinding getViewBinding(LayoutInflater inflater, ViewGroup container) {
        return FragmentFullPlayerBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        super.initView();
        favoriteManager = FavoriteManager.getInstance(context);

        // 初始化颜色资源
        initColors();

        // 初始化歌词View
        initLyricsView();

        // 确保歌词布局初始状态是隐藏的
        binding.lyricsView.setVisibility(View.GONE);
        binding.lyricsEmptyLayout.setVisibility(View.GONE);

        // 设置点击事件
        setupClickListeners();

        // 设置进度条监听
        setupProgressListeners();

        // 注册歌词更新广播接收器
        registerLyricsUpdateReceiver();

        // 设置默认状态
        updateDefaultState();
    }

    private void initColors() {
        defaultTextPrimaryColor = getResources().getColor(R.color.text_primary);
        defaultTextSecondaryColor = getResources().getColor(R.color.text_secondary);
        defaultFavoriteColor = getResources().getColor(R.color.favorite_color);
    }

    private void initLyricsView() {
        // 设置歌词点击监听器
        binding.lyricsView.setOnLyricClickListener((position, lyricLine) -> {
            if (controllerHelper != null) {
                // 跳转到歌词对应的播放位置
                controllerHelper.seekTo(lyricLine.getStartTime());

                // 如果当前是暂停状态，点击歌词后开始播放
                if (!controllerHelper.isPlaying()) {
                    controllerHelper.togglePlayPause();
                }
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // 停止进度更新
        if (progressUpdateRunnable != null) {
            progressUpdateHandler.removeCallbacks(progressUpdateRunnable);
        }

        // 停止播放按钮更新
        if (playButtonUpdateRunnable != null) {
            playButtonUpdateHandler.removeCallbacks(playButtonUpdateRunnable);
        }

        // 停止睡眠定时器
        stopSleepTimer();

        // 移除播放状态监听器
        if (controllerHelper != null) {
            controllerHelper.removePlaybackStateListener(playbackStateListener);
        }

        // 注销歌词更新广播接收器
        unregisterLyricsUpdateReceiver();
    }

    /**
     * 设置MediaControllerHelper
     */
    public void setMediaControllerHelper(@Nullable MediaControllerHelper controllerHelper) {
        Log.d(TAG, "设置MediaControllerHelper: " + (controllerHelper != null ? "不为null" : "为null"));

        // 移除之前的监听器
        if (this.controllerHelper != null) {
            Log.d(TAG, "移除旧的MediaControllerHelper监听器");
            this.controllerHelper.removePlaybackStateListener(playbackStateListener);
        }

        this.controllerHelper = controllerHelper;

        if (controllerHelper != null) {
            Log.d(TAG, "MediaControllerHelper可用，开始初始化");

            // 添加播放状态监听器
            controllerHelper.addPlaybackStateListener(playbackStateListener);
            Log.d(TAG, "已添加播放状态监听器");

            // 记录当前状态
            Log.d(TAG, "当前播放状态: " + controllerHelper.getPlaybackState());
            Log.d(TAG, "当前播放: " + controllerHelper.isPlaying());
            Log.d(TAG, "当前媒体项: " + (controllerHelper.getCurrentSong() != null ?
                    controllerHelper.getCurrentSong().getTitle() : "null"));
            Log.d(TAG, "播放列表大小: " + controllerHelper.getPlaylistSize());
            Log.d(TAG, "当前重复模式: " + controllerHelper.getRepeatMode());
            Log.d(TAG, "当前随机播放: " + controllerHelper.getShuffleModeEnabled());

            // 更新UI状态
            updatePlaybackState();
            updateCurrentSong();
            updatePlayModeButton(controllerHelper.getRepeatMode());

            Log.d(TAG, "MediaControllerHelper设置完成");
        } else {
            updateDefaultState();
        }
    }

    /**
     * 更新播放状态
     */
    private void updatePlaybackState() {
        if (controllerHelper == null) {
            Log.w(TAG, "MediaController为null，无法更新播放状态");
            return;
        }

        Log.d(TAG, "更新播放状态");
        Log.d(TAG, "当前播放状态: " + controllerHelper.getPlaybackState());
        Log.d(TAG, "当前播放: " + controllerHelper.isPlaying());

        updatePlayButton(controllerHelper.isPlaying());

        if (controllerHelper.isPlaying()) {
            Log.d(TAG, "正在播放，启动进度更新");
            startProgressUpdates();
        } else {
            Log.d(TAG, "未在播放，停止进度更新");
            stopProgressUpdates();
        }
    }

    /**
     * 更新当前歌曲信息
     */
    private void updateCurrentSong() {
        if (controllerHelper == null) {
            Log.w(TAG, "MediaControllerHelper为null，无法更新当前歌曲");
            return;
        }

        Song currentSong = controllerHelper.getCurrentSong();
        if (currentSong != null) {
            Log.d(TAG, "更新当前歌曲: " + currentSong.getTitle());
            Log.d(TAG, "歌曲艺术家: " + currentSong.getArtist());
            Log.d(TAG, "歌曲专辑: " + currentSong.getAlbum());
            Log.d(TAG, "歌曲路径: " + currentSong.getPath());
            Log.d(TAG, "歌曲时长: " + currentSong.getDuration());

            // 更新歌曲信息
            updateSongInfo(currentSong);

            // 更新收藏按钮
            updateFavoriteButton();

            // 加载歌词
            loadLyrics(currentSong);
        } else {
            Log.w(TAG, "当前歌曲为null，使用默认状态");
            updateDefaultState();
        }
    }

    /**
     * 更新歌曲信息
     */
    private void updateSongInfo(Song song) {
        if (song == null) return;

        // 更新歌曲标题和艺术家
        binding.fullSongName.setText(song.getTitle());
        binding.fullSongArtist.setText(song.getArtist());

        // 更新专辑封面
        updateAlbumCover(song);

        // 更新时长
        binding.txtTotalTime.setText(TimeUtils.formatDuration(song.getDuration()));

        currentSongId = song.getId();
    }

    /**
     * 更新播放按钮（基于播放状态）
     */
    private void updatePlayButton(boolean isPlaying) {
        // 取消之前的延迟更新
        if (playButtonUpdateRunnable != null) {
            playButtonUpdateHandler.removeCallbacks(playButtonUpdateRunnable);
        }

        // 创建新的延迟更新任务
        playButtonUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                // 延迟300ms后再次检查播放状态
                if (controllerHelper != null) {
                    boolean currentPlayingState = controllerHelper.isPlaying();
                    changeButtonImage(currentPlayingState);
                }
            }
        };

        // 延迟300ms执行
        playButtonUpdateHandler.postDelayed(playButtonUpdateRunnable, 300);
    }

    private void changeButtonImage(boolean isPlay) {
        if (!isPlay) {
            binding.btnPlayPause.setImageResource(R.drawable.ic_play);
        } else {
            binding.btnPlayPause.setImageResource(R.drawable.ic_pause);
        }
    }

    /**
     * 更新播放模式按钮
     */
    private void updatePlayModeButton(int repeatMode) {
        currentPlayMode = repeatMode;

        switch (repeatMode) {
            case Player.REPEAT_MODE_OFF:
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat);
                break;
            case Player.REPEAT_MODE_ONE:
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat_one);
                break;
            case Player.REPEAT_MODE_ALL:
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat);
                break;
        }
        updateButtonColors();
    }

    /**
     * 更新随机播放按钮
     */
    private void updateShuffleButton(boolean shuffleModeEnabled) {
        updateButtonColors();
    }

    /**
     * 更新收藏按钮
     */
    private void updateFavoriteButton() {
        if (controllerHelper == null) return;

        Song currentSong = controllerHelper.getCurrentSong();
        if (currentSong != null) {
            boolean isFavorite = favoriteManager.isFavorite(currentSong.getId());
            if (isFavorite) {
                binding.btnFavorite.setImageResource(R.drawable.ic_favorite_on);
                binding.btnFavorite.setColorFilter(defaultFavoriteColor);
            } else {
                binding.btnFavorite.setImageResource(R.drawable.ic_favorite_off);
                binding.btnFavorite.setColorFilter(currentBackgroundType == BACKGROUND_TYPE_DEFAULT ? defaultTextSecondaryColor : whiteColor);
            }
        }
    }

    /**
     * 注册歌词更新广播接收器
     */
    private void registerLyricsUpdateReceiver() {
        IntentFilter filter = new IntentFilter("com.magicalstory.music.LYRICS_UPDATED");
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(lyricsUpdateReceiver, filter);
    }

    /**
     * 注销歌词更新广播接收器
     */
    private void unregisterLyricsUpdateReceiver() {
        try {
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(lyricsUpdateReceiver);
        } catch (Exception e) {
            Log.w(TAG, "注销歌词更新广播接收器失败", e);
        }
    }

    /**
     * 更新默认状态
     */
    private void updateDefaultState() {
        Log.d(TAG, "更新为默认状态");
        binding.fullSongName.setText("暂无歌曲");
        binding.fullSongArtist.setText("未知艺术家");
        binding.txtCurrentTime.setText("00:00");
        binding.txtTotalTime.setText("00:00");
        binding.seekBarProgress.setProgress(0);

        // 重置背景
        resetBackground();

        // 停止进度更新
        stopProgressUpdates();

        // 清空歌词
        currentLyrics.clear();
        hasLyrics = false;
        // 重置歌词显示状态
        isLyricsVisible = false;
        // 确保歌词布局隐藏
        binding.lyricsView.setVisibility(View.GONE);
        binding.lyricsEmptyLayout.setVisibility(View.GONE);

        currentSongId = -1;
        Log.d(TAG, "默认状态设置完成");
    }

    /**
     * 开始进度更新
     */
    private void startProgressUpdates() {
        if (progressUpdateRunnable == null) {
            progressUpdateRunnable = new Runnable() {
                @Override
                public void run() {
                    updateProgressFromPlayer();
                    progressUpdateHandler.postDelayed(this, 1000); // 每秒更新一次
                }
            };
        }

        progressUpdateHandler.removeCallbacks(progressUpdateRunnable);
        progressUpdateHandler.post(progressUpdateRunnable);
    }

    /**
     * 停止进度更新
     */
    private void stopProgressUpdates() {
        if (progressUpdateRunnable != null) {
            progressUpdateHandler.removeCallbacks(progressUpdateRunnable);
        }
    }

    /**
     * 从播放器更新进度
     */
    private void updateProgressFromPlayer() {
        if (controllerHelper == null || isUserSeeking) return;

        try {
            long currentPosition = controllerHelper.getCurrentPosition();
            long duration = controllerHelper.getDuration();

            if (duration > 0) {
                updateProgress((int) currentPosition, (int) duration);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating progress from player", e);
        }
    }

    /**
     * 更新进度
     */
    private void updateProgress(int currentPosition, int duration) {
        if (isUserSeeking) return;

        // 更新进度条
        if (duration > 0) {
            int progress = (int) ((currentPosition * 100L) / duration);
            binding.seekBarProgress.setProgress(progress);
        }

        // 更新时间显示
        binding.txtCurrentTime.setText(TimeUtils.formatDuration(currentPosition));

        // 更新歌词高亮
        if (isLyricsVisible && !currentLyrics.isEmpty()) {
            binding.lyricsView.updateCurrentTime((long) currentPosition);
        }
    }

    /**
     * 更新专辑封面
     */
    private void updateAlbumCover(Song song) {
        if (song == null) return;

        // 加载专辑封面
        GlideUtils.loadAlbumCover(context, song.getAlbumId(), binding.fullSheetCover);

        // 使用Glide加载专辑封面并提取颜色
        Glide.with(context)
                .asBitmap()
                .load(GlideUtils.getAlbumCoverUri(song.getAlbumId()))
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        currentAlbumBitmap = resource;

                        // 提取颜色
                        dominantColor = ColorExtractor.extractDominantColor(resource);
                        darkColor = ColorExtractor.extractDominantColor(resource);

                        // 更新背景罩层
                        updateBackgroundOverlay();

                        // 更新按钮颜色
                        updateButtonColors();
                    }

                    @Override
                    public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
                        // 清理资源
                    }
                });
    }

    /**
     * 重置背景
     */
    private void resetBackground() {
        currentBackgroundType = BACKGROUND_TYPE_GRADIENT;
        dominantColor = Color.parseColor("#3353BE");
        darkColor = Color.parseColor("#1A237E");
        updateBackgroundOverlay();
    }


    /**
     * 设置点击事件
     */
    private void setupClickListeners() {
        // 播放/暂停按钮
        binding.btnPlayPause.setOnClickListener(v -> {
            Log.d(TAG, "播放/暂停按钮被点击");
            if (controllerHelper != null) {
                Log.d(TAG, "MediaControllerHelper不为null，当前播放状态: " + controllerHelper.isPlaying());
                Log.d(TAG, "当前播放状态: " + controllerHelper.getPlaybackState());

                if (controllerHelper.isPlaying()) {
                    Log.d(TAG, "正在播放，执行暂停操作");
                    changeButtonImage(false);
                    controllerHelper.pause();
                } else {
                    Log.d(TAG, "未在播放，执行播放操作");
                    Log.d(TAG, "当前媒体项: " + (controllerHelper.getCurrentSong() != null ?
                            controllerHelper.getCurrentSong().getId() : "null"));
                    Log.d(TAG, "播放列表大小: " + controllerHelper.getPlaylistSize());

                    // 检查是否有媒体项
                    if (controllerHelper.getPlaylistSize() == 0) {
                        Log.w(TAG, "播放列表为空，无法播放");
                        ToastUtils.showToast(context, "播放列表为空");
                        return;
                    }

                    // 检查当前媒体项
                    if (controllerHelper.getCurrentSong() == null) {
                        Log.w(TAG, "当前媒体项为null，尝试播放第一个媒体项");
                        if (controllerHelper.getPlaylistSize() > 0) {
                            controllerHelper.playAtIndex(0);
                        }
                    }
                    changeButtonImage(true);
                    controllerHelper.play();
                    Log.d(TAG, "播放命令已发送");
                }
            } else {
                Log.e(TAG, "MediaController为null，无法执行播放操作");
                ToastUtils.showToast(context, "播放器未准备好");
            }
        });

        // 上一首按钮
        binding.btnPrevious.setOnClickListener(v -> {
            Log.d(TAG, "上一首按钮被点击");
            if (controllerHelper != null) {
                Log.d(TAG, "执行上一首操作");
                changeButtonImage(true);
                controllerHelper.skipToPrevious();
            } else {
                Log.e(TAG, "MediaController为null，无法执行上一首操作");
            }
        });

        // 下一首按钮
        binding.btnNext.setOnClickListener(v -> {
            Log.d(TAG, "下一首按钮被点击");
            if (controllerHelper != null) {
                Log.d(TAG, "执行下一首操作");
                changeButtonImage(true);
                controllerHelper.skipToNext();
            } else {
                Log.e(TAG, "MediaController为null，无法执行下一首操作");
            }
        });

        // 随机播放按钮
        binding.btnShuffle.setOnClickListener(v -> {
            Log.d(TAG, "随机播放按钮被点击");
            if (controllerHelper != null) {
                boolean currentShuffle = controllerHelper.getShuffleModeEnabled();
                Log.d(TAG, "当前随机播放状态: " + currentShuffle + "，切换为: " + !currentShuffle);
                controllerHelper.setShuffleModeEnabled(!currentShuffle);
            } else {
                Log.e(TAG, "MediaController为null，无法切换随机播放");
            }
        });

        // 循环播放按钮
        binding.btnRepeat.setOnClickListener(v -> {
            Log.d(TAG, "循环播放按钮被点击");
            if (controllerHelper != null) {
                int currentRepeatMode = controllerHelper.getRepeatMode();
                Log.d(TAG, "当前循环播放模式: " + currentRepeatMode);
                switch (currentRepeatMode) {
                    case Player.REPEAT_MODE_OFF:
                        Log.d(TAG, "切换到单曲循环");
                        controllerHelper.setRepeatMode(Player.REPEAT_MODE_ONE);
                        break;
                    case Player.REPEAT_MODE_ONE:
                        Log.d(TAG, "切换到列表循环");
                        controllerHelper.setRepeatMode(Player.REPEAT_MODE_ALL);
                        break;
                    case Player.REPEAT_MODE_ALL:
                        Log.d(TAG, "切换到关闭循环");
                        controllerHelper.setRepeatMode(Player.REPEAT_MODE_OFF);
                        break;
                }
            } else {
                Log.e(TAG, "MediaController为null，无法切换循环播放");
            }
        });

        // 收藏按钮
        binding.btnFavorite.setOnClickListener(v -> {
            Log.d(TAG, "收藏按钮被点击");
            if (controllerHelper == null) {
                Log.e(TAG, "MediaControllerHelper为null，无法操作收藏");
                return;
            }

            Song currentSong = controllerHelper.getCurrentSong();
            if (currentSong != null) {
                Log.d(TAG, "当前歌曲: " + currentSong.getTitle());
                boolean success = favoriteManager.toggleFavorite(currentSong.getId());
                Log.d(TAG, "切换收藏状态结果: " + success);
                if (success) {
                    updateFavoriteButton();
                } else {
                    Log.e(TAG, "切换收藏状态失败");
                    ToastUtils.showToast(context, "操作失败");
                }
            } else {
                Log.w(TAG, "当前歌曲为null，无法操作收藏");
            }
        });

        // 歌词按钮
        binding.btnLyrics.setOnClickListener(v -> {
            Log.d(TAG, "歌词按钮被点击，当前歌词显示状态: " + isLyricsVisible);
            toggleLyrics();
        });

        // 睡眠模式按钮
        binding.btnSleepMode.setOnClickListener(v -> {
            Log.d(TAG, "睡眠模式按钮被点击");
            showSleepTimerDialog();
        });

        // 睡眠定时器倒计时文本点击事件
        binding.titleSleep.setOnClickListener(v -> {
            Log.d(TAG, "睡眠定时器倒计时被点击");
            showSleepTimerDialog();
        });

        // 播放列表按钮
        binding.btnPlaylist.setOnClickListener(v -> {
            Log.d(TAG, "播放列表按钮被点击");
            showPlaylistBottomSheet();
        });

        // 更多按钮
        binding.btnMore.setOnClickListener(v -> {
            Log.d(TAG, "更多按钮被点击");
            showSongBottomSheet();
        });

        // 专辑封面点击事件
        binding.albumCoverFrame.setOnClickListener(v -> {
            Log.d(TAG, "专辑封面被点击，当前歌词显示状态: " + isLyricsVisible);
            if (isLyricsVisible) {
                Log.d(TAG, "歌词已显示，切换背景类型");
                // 如果歌词已显示，切换背景类型
                switchBackgroundType();
            } else {
                Log.d(TAG, "歌词未显示，显示歌词");
                // 如果歌词未显示，显示歌词
                toggleLyrics();
            }
        });

        // 编辑歌词点击事件
        binding.tvEditLyrics.setOnClickListener(v -> {
            Log.d(TAG, "编辑歌词被点击");
            if (controllerHelper != null) {
                Song currentSong = controllerHelper.getCurrentSong();
                if (currentSong != null) {
                    // 使用MainActivity的导航方法
                    if (getActivity() instanceof MainActivity) {
                        MainActivity mainActivity = (MainActivity) getActivity();
                        mainActivity.navigateToLyricsEditor(currentSong);
                    }
                } else {
                    ToastUtils.showToast(context, "当前没有播放的歌曲");
                }
            } else {
                ToastUtils.showToast(context, "播放器未准备好");
            }
        });

        // 歌手名字点击事件
        binding.fullSongArtist.setOnClickListener(v -> {
            Log.d(TAG, "歌手名字被点击");
            if (controllerHelper != null) {
                Song currentSong = controllerHelper.getCurrentSong();
                if (currentSong != null && currentSong.getArtist() != null && !currentSong.getArtist().isEmpty()) {
                    if (getActivity() instanceof MainActivity mainActivity) {
                        mainActivity.navigateToArtistDetail(currentSong.getArtist());
                    }
                } else {
                    Log.w(TAG, "当前歌曲或歌手信息为空，无法跳转");
                    ToastUtils.showToast(context, "无法获取歌手信息");
                }
            } else {
                Log.e(TAG, "MediaControllerHelper为null，无法获取当前歌曲信息");
                ToastUtils.showToast(context, "播放器未准备好");
            }
        });
    }

    /**
     * 切换背景类型
     */
    private void switchBackgroundType() {
        currentBackgroundType = (currentBackgroundType + 1) % 4;
        updateBackgroundOverlay();
    }

    /**
     * 切换歌词显示/隐藏
     */
    private void toggleLyrics() {
        isLyricsVisible = !isLyricsVisible;
        updateLyricsVisibility();
        updateLyricsButton();
    }

    /**
     * 更新歌词显示状态
     */
    private void updateLyricsVisibility() {
        if (isLyricsVisible) {
            showLyrics();
        } else {
            hideLyrics();
        }
    }

    /**
     * 显示歌词
     */
    private void showLyrics() {
        // 加载动画
        Animation fadeInAnimation = AnimationUtils.loadAnimation(context, R.anim.fade_in);
        Animation fadeOutAnimation = AnimationUtils.loadAnimation(context, R.anim.fade_out);
        fadeInAnimation.setDuration(100);
        fadeOutAnimation.setDuration(100);

        // 设置动画监听器
        fadeOutAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                // 淡出动画结束后，隐藏封面和信息，显示歌词区域
                binding.albumCoverFrame.setVisibility(View.INVISIBLE);
                binding.songInfoLayout.setVisibility(View.INVISIBLE);
                
                if (hasLyrics) {
                    // 有歌词，显示歌词View
                    binding.lyricsView.setVisibility(View.VISIBLE);
                    binding.lyricsEmptyLayout.setVisibility(View.GONE);
                    binding.lyricsView.startAnimation(fadeInAnimation);
                } else {
                    // 没有歌词，显示空状态
                    binding.lyricsView.setVisibility(View.GONE);
                    binding.lyricsEmptyLayout.setVisibility(View.VISIBLE);
                    binding.lyricsEmptyLayout.startAnimation(fadeInAnimation);
                }

                // 检查是否需要加载歌词
                if (controllerHelper != null) {
                    Song currentSong = controllerHelper.getCurrentSong();
                    if (currentSong != null && currentSong.getId() != currentSongId) {
                        // 如果是新歌曲，需要重新加载歌词
                        loadLyrics(currentSong);
                    }
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });

        // 开始淡出动画
        binding.albumCoverFrame.startAnimation(fadeOutAnimation);
        binding.songInfoLayout.startAnimation(fadeOutAnimation);
    }

    /**
     * 隐藏歌词
     */
    private void hideLyrics() {
        // 加载动画
        Animation fadeInAnimation = AnimationUtils.loadAnimation(context, R.anim.fade_in);
        Animation fadeOutAnimation = AnimationUtils.loadAnimation(context, R.anim.fade_out);
        fadeInAnimation.setDuration(100);
        fadeOutAnimation.setDuration(100);
        // 设置动画监听器
        fadeOutAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                // 淡出动画结束后，隐藏歌词区域，显示封面和信息
                binding.lyricsView.setVisibility(View.GONE);
                binding.lyricsEmptyLayout.setVisibility(View.GONE);
                binding.albumCoverFrame.setVisibility(View.VISIBLE);
                binding.songInfoLayout.setVisibility(View.VISIBLE);
                binding.albumCoverFrame.startAnimation(fadeInAnimation);
                binding.songInfoLayout.startAnimation(fadeInAnimation);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });

        // 开始淡出动画
        if (hasLyrics) {
            binding.lyricsView.startAnimation(fadeOutAnimation);
        } else {
            binding.lyricsEmptyLayout.startAnimation(fadeOutAnimation);
        }
    }

    /**
     * 更新歌词按钮状态
     */
    private void updateLyricsButton() {
        if (isLyricsVisible) {
            binding.btnLyrics.setImageResource(R.drawable.ic_lyrics_on);
        } else {
            binding.btnLyrics.setImageResource(R.drawable.ic_lyrics_off);
        }
    }

    /**
     * 加载歌词
     */
    private void loadLyrics(Song song) {
        if (controllerHelper == null) return;

        new Thread() {
            @Override
            public void run() {
                super.run();

                // 从歌曲文件解析歌词
                List<LyricLine> lyrics = LyricsParser.parseLyricsFromSong(context, song.getPath());

                binding.lyricsView.post(() -> {
                    currentLyrics.clear();
                    currentLyrics.addAll(lyrics);
                    
                    // 检查是否有真实歌词（不是"暂无歌词"）
                    hasLyrics = !lyrics.isEmpty() && !lyrics.get(0).getContent().equals(getString(R.string.no_lyrics));
                    
                    // 根据当前歌词显示状态来设置可见性
                    if (isLyricsVisible) {
                        if (hasLyrics) {
                            // 有歌词，显示歌词
                            binding.lyricsView.setLyrics(currentLyrics);
                            binding.lyricsView.setVisibility(View.VISIBLE);
                            binding.lyricsEmptyLayout.setVisibility(View.GONE);
                        } else {
                            // 没有歌词，显示空状态
                            binding.lyricsView.setVisibility(View.GONE);
                            binding.lyricsEmptyLayout.setVisibility(View.VISIBLE);
                        }
                    } else {
                        // 歌词未显示状态，确保两个布局都隐藏
                        if (hasLyrics) {
                            // 有歌词，设置歌词内容但不显示
                            binding.lyricsView.setLyrics(currentLyrics);
                        }
                        binding.lyricsView.setVisibility(View.GONE);
                        binding.lyricsEmptyLayout.setVisibility(View.GONE);
                    }

                    // 更新当前播放位置的歌词（如果歌词可见）
                    if (hasLyrics && isLyricsVisible) {
                        long currentPosition = controllerHelper.getCurrentPosition();
                        binding.lyricsView.updateCurrentPosition(currentPosition);
                    }

                    // 打印调试信息
                    System.out.println("歌词加载完成，共 " + lyrics.size() + " 行，是否有歌词: " + hasLyrics + "，歌词显示状态: " + isLyricsVisible);
                });

            }
        }.start();
    }

    /**
     * 更新背景罩层
     */
    private void updateBackgroundOverlay() {
        if (!(getActivity() instanceof MainActivity mainActivity)) return;

        Song currentSong = mainActivity.getCurrentSong();
        if (currentSong == null) {
            // 没有歌曲时显示渐变背景
            switch (currentBackgroundType) {
                case BACKGROUND_TYPE_DEFAULT:
                    binding.backgroundOverlay.setImageDrawable(null);
                    binding.backgroundOverlay.setBackground(null);
                    break;
                case BACKGROUND_TYPE_SOLID:
                    binding.backgroundOverlay.setImageDrawable(null);
                    binding.backgroundOverlay.setBackgroundColor(dominantColor);
                    break;
                case BACKGROUND_TYPE_GRADIENT:
                    binding.backgroundOverlay.setImageDrawable(null);
                    // 使用默认渐变色
                    GradientDrawable gradientDrawable = new GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            new int[]{darkColor, Color.BLACK}
                    );
                    binding.backgroundOverlay.setBackground(gradientDrawable);
                    break;
                case BACKGROUND_TYPE_BLUR:
                    binding.backgroundOverlay.setImageDrawable(null);
                    binding.backgroundOverlay.setBackground(null);
                    break;
            }
            updateUIColors();
            return;
        }

        switch (currentBackgroundType) {
            case BACKGROUND_TYPE_DEFAULT:
                binding.backgroundOverlay.setImageDrawable(null);
                binding.backgroundOverlay.setBackground(null);
                break;
            case BACKGROUND_TYPE_SOLID:
                updateSolidBackground(currentSong);
                break;
            case BACKGROUND_TYPE_GRADIENT:
                updateGradientBackground(currentSong);
                break;
            case BACKGROUND_TYPE_BLUR:
                updateBlurBackground(currentSong);
                break;
        }

        updateUIColors();

    }

    /**
     * 更新UI颜色
     */
    private void updateUIColors() {
        boolean isDefault = currentBackgroundType == BACKGROUND_TYPE_DEFAULT;
        int textPrimaryColor = isDefault ? defaultTextPrimaryColor : whiteColor;
        int textSecondaryColor = isDefault ? defaultTextSecondaryColor : grayColor;

        // 更新歌曲名称和艺术家颜色
        binding.fullSongName.setTextColor(textPrimaryColor);
        binding.fullSongArtist.setTextColor(textPrimaryColor);

        // 更新音质信息颜色
        binding.txtFormat.setTextColor(textPrimaryColor);
        binding.txtBitrate.setTextColor(textPrimaryColor);
        binding.txtSampleRate.setTextColor(textPrimaryColor);

        // 更新时间显示颜色
        binding.txtCurrentTime.setTextColor(textSecondaryColor);
        binding.txtTotalTime.setTextColor(textSecondaryColor);

        // 更新质量信息分隔符颜色
        ViewGroup qualityLayout = binding.qualityLayout;
        for (int i = 0; i < qualityLayout.getChildCount(); i++) {
            View child = qualityLayout.getChildAt(i);
            if (child instanceof TextView) {
                TextView textView = (TextView) child;
                if ("•".equals(textView.getText().toString())) {
                    textView.setTextColor(textPrimaryColor);
                }
            }
        }

        // 更新SeekBar颜色
        updateSeekBarColors(isDefault);

        // 更新按钮颜色
        updateButtonColors();
    }

    /**
     * 更新SeekBar颜色
     * @param isDefault 是否是默认背景
     */
    private void updateSeekBarColors(boolean isDefault) {
        if (isDefault) {
            // 默认背景下保持原有颜色
            binding.seekBarProgress.getProgressDrawable().setColorFilter(null);
            binding.seekBarProgress.getThumb().setColorFilter(null);
        } else {
            // 非默认背景下设置为白色
            binding.seekBarProgress.getProgressDrawable().setColorFilter(whiteColor,
                    android.graphics.PorterDuff.Mode.SRC_IN);
            binding.seekBarProgress.getThumb().setColorFilter(whiteColor,
                    android.graphics.PorterDuff.Mode.SRC_IN);
        }
    }

    /**
     * 更新按钮颜色
     */
    private void updateButtonColors() {
        boolean isDefault = currentBackgroundType == BACKGROUND_TYPE_DEFAULT;
        int buttonNormalColor = isDefault ? defaultTextSecondaryColor : Color.WHITE;
        int buttonActiveColor = isDefault ? defaultTextPrimaryColor : Color.WHITE;

        if (isDefault) {
            binding.btnPlayPause.setColorFilter(whiteColor);
        } else if (currentBackgroundType == BACKGROUND_TYPE_SOLID) {
            binding.btnPlayPause.setColorFilter(dominantColor);
        } else {
            binding.btnPlayPause.setColorFilter(darkColor);
        }

        binding.btnPlayBackground.setCardBackgroundColor(isDefault ? getResources().getColor(R.color.md_theme_primary) : whiteColor);

        binding.btnPrevious.setColorFilter(buttonNormalColor);
        binding.btnNext.setColorFilter(buttonNormalColor);

        // 更新随机播放按钮
        if (controllerHelper != null && controllerHelper.getShuffleModeEnabled()) {
            binding.btnShuffle.setColorFilter(buttonActiveColor);
            binding.btnShuffle.setAlpha(1.0f); // 随机播放开启时设置正常透明度
        } else {
            binding.btnShuffle.setColorFilter(buttonNormalColor);
            binding.btnShuffle.setAlpha(0.5f); // 随机播放未开启时设置较低透明度
        }

        // 更新循环播放按钮
        if (currentPlayMode == Player.REPEAT_MODE_ONE || currentPlayMode == Player.REPEAT_MODE_ALL) {
            binding.btnRepeat.setColorFilter(buttonActiveColor);
            binding.btnRepeat.setAlpha(1.0f); // 循环播放开启时设置正常透明度
        } else {
            binding.btnRepeat.setColorFilter(buttonNormalColor);
            binding.btnRepeat.setAlpha(0.5f); // 循环播放未开启时设置较低透明度
        }

        // 更新收藏按钮
        if (controllerHelper != null) {
            Song currentSong = controllerHelper.getCurrentSong();
            if (currentSong != null && favoriteManager.isFavorite(currentSong.getId())) {
                binding.btnFavorite.setColorFilter(isDefault ? defaultFavoriteColor : Color.parseColor("#FF6B9D"));
            } else {
                binding.btnFavorite.setColorFilter(buttonNormalColor);
            }
        }

        // 更新底部控制按钮
        binding.btnLyrics.setColorFilter(buttonNormalColor);
        binding.btnSleepMode.setColorFilter(buttonNormalColor);
        binding.btnPlaylist.setColorFilter(buttonNormalColor);
        binding.btnMore.setColorFilter(buttonNormalColor);

        // 更新睡眠定时器显示（包括颜色）
        updateSleepTimerDisplay();
    }

    /**
     * 更新纯色背景
     */
    private void updateSolidBackground(Song song) {
        binding.backgroundOverlay.setImageDrawable(null);
        binding.backgroundOverlay.setBackgroundColor(dominantColor);
    }

    /**
     * 更新渐变背景
     */
    private void updateGradientBackground(Song song) {
        binding.backgroundOverlay.setImageDrawable(null);

        // 创建渐变drawable
        GradientDrawable gradientDrawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{darkColor, Color.BLACK}
        );

        binding.backgroundOverlay.setBackground(gradientDrawable);
    }

    /**
     * 更新模糊背景
     */
    private void updateBlurBackground(Song song) {
        binding.backgroundOverlay.setBackground(null);

        if (currentAlbumBitmap != null) {
            // 先缩放图片以提高性能
            Bitmap scaledBitmap = BlurUtils.scaleBitmap(currentAlbumBitmap, 0.25f);

            // 应用高斯模糊
            Bitmap blurredBitmap = BlurUtils.blur(context, scaledBitmap, 20.0f);

            if (blurredBitmap != null) {
                // 让模糊背景更暗
                Bitmap darkenedBitmap = darkenBitmap(blurredBitmap);
                binding.backgroundOverlay.setImageBitmap(darkenedBitmap);
            } else {
                // 如果模糊失败，使用fastBlur作为备用方案
                blurredBitmap = BlurUtils.fastBlur(scaledBitmap, 15);
                if (blurredBitmap != null) {
                    // 让模糊背景更暗
                    Bitmap darkenedBitmap = darkenBitmap(blurredBitmap);
                    binding.backgroundOverlay.setImageBitmap(darkenedBitmap);
                }
            }
        } else {
            // 如果没有专辑封面，使用默认封面
            GlideUtils.loadAlbumCover(context, song.getAlbumId(), binding.backgroundOverlay);
        }
    }

    /**
     * 让图片变暗
     */
    private Bitmap darkenBitmap(Bitmap bitmap) {
        if (bitmap == null) return null;

        // 创建可变的Bitmap副本
        Bitmap darkBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

        // 创建一个半透明的黑色罩层
        Canvas canvas = new Canvas(darkBitmap);
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setAlpha(120); // 透明度：120/255 ≈ 47%
        canvas.drawRect(0, 0, darkBitmap.getWidth(), darkBitmap.getHeight(), paint);

        return darkBitmap;
    }

    private void setupProgressListeners() {
        // SeekBar进度监听
        binding.seekBarProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // 拖动过程中只更新时间显示，不更新媒体进度
                if (fromUser && controllerHelper != null) {
                    long duration = controllerHelper.getDuration();
                    if (duration > 0) {
                        long position = (long) ((float) progress / 100 * duration);
                        // 只更新时间显示，不调用seekTo
                        binding.txtCurrentTime.setText(TimeUtils.formatTime(position));
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                // 松手后才更新媒体进度
                if (controllerHelper != null) {
                    int progress = seekBar.getProgress();
                    long duration = controllerHelper.getDuration();
                    if (duration > 0) {
                        binding.lyricsView.updateCurrentTime(progress);
                        long position = (long) ((float) progress / 100 * duration);
                        controllerHelper.seekTo(position);
                    }
                }
            }
        });
    }


    @Override
    public void onStop() {
        super.onStop();

    }

    /**
     * 获取当前播放的歌曲
     */
    public Song getCurrentSong() {
        if (controllerHelper != null) {
            return controllerHelper.getCurrentSong();
        }
        return null;
    }

    @Override
    public boolean autoHideBottomNavigation() {
        return false;
    }

    /**
     * 显示播放列表底部弹出窗口
     */
    private void showPlaylistBottomSheet() {
        if (controllerHelper == null) {
            ToastUtils.showToast(context, "播放列表为空");
            return;
        }


        // 创建播放列表底部弹出窗口Fragment
        playlistBottomSheet = PlaylistBottomSheetDialogFragment.newInstance();
        playlistBottomSheet.setMediaControllerHelper(controllerHelper);

        // 设置播放列表操作监听器
        playlistBottomSheet.setOnPlaylistActionListener(new PlaylistBottomSheetDialogFragment.OnPlaylistActionListener() {
            @Override
            public void onPlaylistItemClick(int position, Song song) {
                Log.d(TAG, "播放列表项被点击: " + position + " - " + song.getTitle());
            }

            @Override
            public void onPlaylistItemMoved(int fromPosition, int toPosition) {
                Log.d(TAG, "播放列表项被移动: " + fromPosition + " -> " + toPosition);
                Log.d(TAG, "播放列表项被移动: " + fromPosition + " -> " + toPosition);
            }

            @Override
            public void onPlaylistCleared() {
                Log.d(TAG, "播放列表被清空");
            }

            @Override
            public void onMoreButtonClick(int position, Song song, View view) {
                Log.d(TAG, "更多按钮被点击: " + position + " - " + song.getTitle());
                // 这里可以实现更多选项菜单
                ToastUtils.showToast(context, "更多选项: " + song.getTitle());
            }
        });

        // 显示播放列表Fragment
        playlistBottomSheet.show(getParentFragmentManager(), "PlaylistBottomSheet");

    }

    /**
     * 显示睡眠定时器弹窗
     */
    private void showSleepTimerDialog() {
        Log.d(TAG, "显示睡眠定时器弹窗");

        if (isSleepTimerActive) {
            // 如果定时器已激活，显示控制对话框
            showSleepTimerControlDialog();
        } else {
            // 如果定时器未激活，显示开始对话框
            showSleepTimerStartDialog();
        }
    }

    /**
     * 显示睡眠定时器开始对话框
     */
    private void showSleepTimerStartDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_sleep_timer_start, null);
        TextView tvSelectedTime = dialogView.findViewById(R.id.tv_selected_time);
        Slider sliderTime = dialogView.findViewById(R.id.slider_time);
        MaterialButton btnPlayUntilEnd = dialogView.findViewById(R.id.btn_play_until_end);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel);
        MaterialButton btnStart = dialogView.findViewById(R.id.btn_start);

        // 设置滑块
        sliderTime.setValueTo(11); // 0-11对应5,10,15,20,25,30,35,40,45,50,55,60分钟
        sliderTime.setValue(0); // 默认5分钟
        final int[] timeOptions = {5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60};
        final boolean[] isPlayUntilEnd = {false};

        // 滑块监听器
        sliderTime.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int position = (int) value;
                if (position >= 0 && position < timeOptions.length) {
                    tvSelectedTime.setText(timeOptions[position] + " 分钟");
                }
            }
        });

        // 播完这首歌按钮
        btnPlayUntilEnd.setOnClickListener(v -> {
            isPlayUntilEnd[0] = !isPlayUntilEnd[0];
            if (isPlayUntilEnd[0]) {
                btnPlayUntilEnd.setStrokeColorResource(R.color.primary_color);
                btnPlayUntilEnd.setTextColor(getResources().getColor(R.color.primary_color));

                // 立即计算并显示当前歌曲的剩余时间
                if (controllerHelper != null) {
                    long currentPosition = controllerHelper.getCurrentPosition();
                    long duration = controllerHelper.getDuration();
                    long remainingTime = duration - currentPosition;

                    if (remainingTime > 0) {
                        long minutes = remainingTime / (60 * 1000);
                        long seconds = (remainingTime % (60 * 1000)) / 1000;
                        tvSelectedTime.setText(String.format("%d:%02d", minutes, seconds));
                    } else {
                        tvSelectedTime.setText("歌曲已播放完毕");
                    }
                }
            } else {
                btnPlayUntilEnd.setStrokeColorResource(R.color.text_secondary);
                btnPlayUntilEnd.setTextColor(getResources().getColor(R.color.text_primary));

                // 恢复显示选择的时间
                int position = (int) sliderTime.getValue();
                if (position >= 0 && position < timeOptions.length) {
                    tvSelectedTime.setText(timeOptions[position] + " 分钟");
                }
            }
        });

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("睡眠定时器")
                .setView(dialogView);

        AlertDialog dialog = builder.create();

        // 取消按钮
        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
        });

        // 开始按钮
        btnStart.setOnClickListener(v -> {
            int selectedMinutes = timeOptions[(int) sliderTime.getValue()];
            if (isPlayUntilEnd[0]) {
                startPlayUntilEndTimer();
            } else {
                startFixedTimeTimer(selectedMinutes);
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    /**
     * 显示睡眠定时器控制对话框
     */
    private void showSleepTimerControlDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_sleep_timer_control, null);
        TextView tvRemainingTime = dialogView.findViewById(R.id.tv_remaining_time);
        View btnMinus5min = dialogView.findViewById(R.id.btn_minus_5min);
        View btnPlus5min = dialogView.findViewById(R.id.btn_plus_5min);
        MaterialButton btnStop = dialogView.findViewById(R.id.btn_stop);
        MaterialButton btnDone = dialogView.findViewById(R.id.btn_done);

        // 更新剩余时间显示
        updateRemainingTimeDisplay(tvRemainingTime);

        // 减少5分钟按钮
        btnMinus5min.setOnClickListener(v -> {
            sleepTimerRemainingTime -= 5 * 60 * 1000L; // 减少5分钟
            if (sleepTimerRemainingTime < 0) {
                sleepTimerRemainingTime = 0;
            }
            updateRemainingTimeDisplay(tvRemainingTime);
            updateSleepTimerDisplay();
        });

        // 增加5分钟按钮
        btnPlus5min.setOnClickListener(v -> {
            sleepTimerRemainingTime += 5 * 60 * 1000L; // 增加5分钟
            // 限制最大时间为1小时
            if (sleepTimerRemainingTime > 60 * 60 * 1000L) {
                sleepTimerRemainingTime = 60 * 60 * 1000L;
            }
            updateRemainingTimeDisplay(tvRemainingTime);
            updateSleepTimerDisplay();
        });

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("睡眠定时器")
                .setView(dialogView);

        AlertDialog dialog = builder.create();

        // 停止按钮
        btnStop.setOnClickListener(v -> {
            stopSleepTimer();
            dialog.dismiss();
        });

        // 完成按钮
        btnDone.setOnClickListener(v -> {
            dialog.dismiss();
        });

        dialog.show();
    }

    /**
     * 更新剩余时间显示
     */
    private void updateRemainingTimeDisplay(TextView textView) {
        long minutes = sleepTimerRemainingTime / (60 * 1000);
        long seconds = (sleepTimerRemainingTime % (60 * 1000)) / 1000;
        textView.setText(String.format("%02d:%02d", minutes, seconds));
    }

    /**
     * 开始固定时间定时器
     */
    private void startFixedTimeTimer(int minutes) {
        stopSleepTimer(); // 先停止现有定时器

        sleepTimerRemainingTime = minutes * 60 * 1000L;
        isSleepTimerActive = true;

        // 启动倒计时
        startSleepTimerCountdown();

        // 更新UI显示
        updateSleepTimerDisplay();

    }

    /**
     * 开始播完这首歌定时器
     */
    private void startPlayUntilEndTimer() {
        if (controllerHelper == null) {
            ToastUtils.showToast(requireContext(), "无法获取播放信息");
            return;
        }

        long currentPosition = controllerHelper.getCurrentPosition();
        long duration = controllerHelper.getDuration();
        long remainingTime = duration - currentPosition;

        if (remainingTime <= 0) {
            ToastUtils.showToast(requireContext(), "当前歌曲已播放完毕");
            return;
        }

        stopSleepTimer(); // 先停止现有定时器

        sleepTimerRemainingTime = remainingTime;
        isSleepTimerActive = true;

        // 启动倒计时
        startSleepTimerCountdown();

        // 更新UI显示
        updateSleepTimerDisplay();

        ToastUtils.showToast(requireContext(), "播完这首歌模式已启动");
    }

    /**
     * 启动睡眠定时器倒计时
     */
    private void startSleepTimerCountdown() {
        // 停止之前的倒计时更新
        if (sleepTimerUpdateRunnable != null) {
            sleepTimerHandler.removeCallbacks(sleepTimerUpdateRunnable);
        }

        // 创建倒计时更新任务
        sleepTimerUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isSleepTimerActive && sleepTimerRemainingTime > 0) {
                    sleepTimerRemainingTime -= 1000; // 减少1秒
                    updateSleepTimerDisplay();

                    if (sleepTimerRemainingTime <= 0) {
                        // 时间到，停止音乐播放
                        stopSleepTimer();
                        if (controllerHelper != null) {
                            controllerHelper.pause();
                        }
                        ToastUtils.showToast(requireContext(), "睡眠定时器已结束");
                    } else {
                        // 继续倒计时
                        sleepTimerHandler.postDelayed(this, 1000);
                    }
                }
            }
        };

        // 启动倒计时
        sleepTimerHandler.post(sleepTimerUpdateRunnable);
    }

    /**
     * 停止睡眠定时器
     */
    private void stopSleepTimer() {
        isSleepTimerActive = false;
        sleepTimerRemainingTime = 0;

        // 停止倒计时更新
        if (sleepTimerUpdateRunnable != null) {
            sleepTimerHandler.removeCallbacks(sleepTimerUpdateRunnable);
            sleepTimerUpdateRunnable = null;
        }

        // 更新UI显示
        updateSleepTimerDisplay();

        Log.d(TAG, "睡眠定时器已停止");
    }

    /**
     * 更新睡眠定时器显示
     */
    private void updateSleepTimerDisplay() {
        if (binding != null) {
            if (isSleepTimerActive && sleepTimerRemainingTime > 0) {
                // 显示倒计时
                long minutes = sleepTimerRemainingTime / (60 * 1000);
                long seconds = (sleepTimerRemainingTime % (60 * 1000)) / 1000;
                binding.titleSleep.setText(String.format("%d:%02d", minutes, seconds));
                binding.titleSleep.setVisibility(View.VISIBLE);
                binding.btnSleepMode.setVisibility(View.GONE);

                // 设置倒计时文本颜色与btn_lyrics保持一致
                boolean isDefault = currentBackgroundType == BACKGROUND_TYPE_DEFAULT;
                int buttonNormalColor = isDefault ? defaultTextSecondaryColor : Color.WHITE;
                binding.titleSleep.setTextColor(buttonNormalColor);
            } else {
                // 隐藏倒计时，显示睡眠按钮
                binding.titleSleep.setVisibility(View.GONE);
                binding.btnSleepMode.setVisibility(View.VISIBLE);
            }
        }
    }

    /**
     * 显示歌曲底部弹出窗口
     */
    private void showSongBottomSheet() {
        if (controllerHelper == null) {
            ToastUtils.showToast(context, "播放器未准备好");
            return;
        }

        Song currentSong = controllerHelper.getCurrentSong();
        if (currentSong == null) {
            ToastUtils.showToast(context, "当前没有播放的歌曲");
            return;
        }

        // 创建歌曲底部弹出窗口Fragment
        SongBottomSheetDialogFragment songBottomSheet = SongBottomSheetDialogFragment.newInstance(currentSong);
        songBottomSheet.setMediaControllerHelper(controllerHelper);

        // 显示歌曲底部弹出窗口Fragment
        songBottomSheet.show(getParentFragmentManager(), "SongBottomSheet");
    }

    /**
     * 刷新音乐列表
     */
    @Override
    protected void onRefreshMusicList() {
        // 重新加载播放器数据
        refreshFragmentAsync();
    }

    /**
     * 在后台线程执行刷新操作
     */
    @Override
    protected void performRefreshInBackground() {
        try {
            // 重新加载播放器数据
            // 这里可以添加具体的刷新逻辑
            
            // 打印原始数据到控制台
            System.out.println("FullPlayerFragment后台刷新完成");
            
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("FullPlayerFragment后台刷新失败: " + e.getMessage());
        }
    }

    /**
     * 在主线程更新UI
     */
    @Override
    protected void updateUIAfterRefresh() {
        try {
            // 更新UI显示
            if (binding != null) {
                // 更新播放器UI
                updateCurrentSong();
                updatePlaybackState();
                updatePlayButton(controllerHelper != null && controllerHelper.isPlaying());
            }
            
            System.out.println("FullPlayerFragment UI更新完成");
            
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("FullPlayerFragment UI更新失败: " + e.getMessage());
        }
    }
} 