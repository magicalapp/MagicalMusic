package com.magicalstory.music.dialog;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.magicalstory.music.R;
import com.magicalstory.music.adapter.PlaylistAdapter;
import com.magicalstory.music.adapter.PlaylistItemTouchHelper;
import com.magicalstory.music.databinding.BottomSheetPlaylistBinding;
import com.magicalstory.music.dialog.SongBottomSheetDialogFragment;
import com.magicalstory.music.model.Song;
import com.magicalstory.music.player.MediaControllerHelper;
import com.magicalstory.music.utils.app.ToastUtils;
import com.magicalstory.music.MainActivity;

import java.util.List;

/**
 * 播放列表底部弹出窗口Fragment
 * 继承自BottomSheetDialogFragment，使用ViewBinding实现
 */
@UnstableApi
public class PlaylistBottomSheetDialogFragment extends BottomSheetDialogFragment {

    private static final String TAG = "PlaylistBottomSheetDialogFragment";

    private BottomSheetPlaylistBinding binding;
    private PlaylistAdapter playlistAdapter;
    private ItemTouchHelper itemTouchHelper;
    private MediaControllerHelper controllerHelper;

    // 播放状态监听器
    private final MediaControllerHelper.PlaybackStateListener playbackStateListener = new MediaControllerHelper.PlaybackStateListener() {
        @Override
        public void songChange(Song newSong) {
            // 更新当前播放歌曲的状态
            updateCurrentPlayingSong();
        }

        @Override
        public void stopPlay() {
            // 停止播放时清除当前播放状态
            if (playlistAdapter != null) {
                playlistAdapter.setCurrentPlayingIndex(-1);
                playlistAdapter.notifyDataSetChanged();
            }
        }
    };

    // 回调接口
    public interface OnPlaylistActionListener {
        /**
         * 播放列表项被点击
         */
        void onPlaylistItemClick(int position, Song song);

        /**
         * 播放列表项被移动
         */
        void onPlaylistItemMoved(int fromPosition, int toPosition);

        /**
         * 播放列表被清空
         */
        void onPlaylistCleared();

        /**
         * 更多按钮被点击
         */
        void onMoreButtonClick(int position, Song song, View view);
    }

    private OnPlaylistActionListener actionListener;

    /**
     * 创建新的实例
     */
    public static PlaylistBottomSheetDialogFragment newInstance() {
        return new PlaylistBottomSheetDialogFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 使用ViewBinding创建视图
        binding = BottomSheetPlaylistBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 初始化MediaControllerHelper
        initControllerHelper();

        // 设置点击事件
        setupClickListeners();

        // 初始化播放列表
        initPlaylist();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // 移除播放状态监听器
        if (controllerHelper != null) {
            controllerHelper.removePlaybackStateListener(playbackStateListener);
        }

        binding = null;
    }

    /**
     * 初始化MediaControllerHelper
     */
    private void initControllerHelper() {
        controllerHelper = MediaControllerHelper.getInstance();
        controllerHelper.addPlaybackStateListener(playbackStateListener);
    }

    /**
     * 设置MediaControllerHelper
     */
    public void setMediaControllerHelper(MediaControllerHelper controllerHelper) {
        this.controllerHelper = controllerHelper;
        // 如果视图已经创建，立即初始化播放列表
        if (binding != null) {
            initPlaylist();
        }
    }

    /**
     * 设置播放列表操作监听器
     */
    public void setOnPlaylistActionListener(OnPlaylistActionListener listener) {
        this.actionListener = listener;
    }

    /**
     * 初始化播放列表
     */
    private void initPlaylist() {
        if (controllerHelper == null || binding == null) return;

        // 获取播放列表数据
        List<Song> playlist = controllerHelper.getPlaylist();
        int currentIndex = controllerHelper.getCurrentIndex();

        // 设置标题
        binding.tvPlaylistTitle.setText(getString(R.string.playlist_title, playlist.size()));

        // 设置适配器
        playlistAdapter = new PlaylistAdapter(requireContext(), playlist);
        playlistAdapter.setCurrentPlayingIndex(currentIndex);
        binding.rvPlaylist.setAdapter(playlistAdapter);

        // 设置拖动排序
        setupDragAndDrop();

        // 设置点击事件
        setupPlaylistItemListeners();

        // 滚动到当前播放位置
        if (currentIndex >= 0) {
            binding.rvPlaylist.scrollToPosition(currentIndex);
        }
    }

    /**
     * 设置点击事件
     */
    private void setupClickListeners() {
        // 清空播放列表按钮
        binding.btnClearPlaylist.setOnClickListener(v -> {
            clearPlaylist();
        });
    }

    /**
     * 设置拖动排序
     */
    private void setupDragAndDrop() {
        PlaylistItemTouchHelper.OnItemMovedListener listener = (fromPosition, toPosition) -> {
            // 拖动完成后才通知MediaControllerHelper更新播放列表
            if (controllerHelper != null) {
                Log.d(TAG, "开始重新排序播放列表: " + fromPosition + " -> " + toPosition);
                controllerHelper.reorderPlaylist(fromPosition, toPosition);
                Log.d(TAG, "播放列表项重新排序完成: " + fromPosition + " -> " + toPosition);
            }

            // 通知监听器
            if (actionListener != null) {
                actionListener.onPlaylistItemMoved(fromPosition, toPosition);
            }
        };

        // 添加侧滑删除监听器
        PlaylistItemTouchHelper.OnItemSwipeListener swipeListener = (position) -> {
            handleItemSwipeDelete(position);
        };

        PlaylistItemTouchHelper callback = new PlaylistItemTouchHelper(playlistAdapter, listener, swipeListener);
        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(binding.rvPlaylist);

        // 设置拖动图标的触摸事件
        playlistAdapter.setOnItemClickListener((position, song) -> {
            // 点击列表项切换播放
            if (controllerHelper != null) {
                controllerHelper.playAtIndex(position);
                Log.d(TAG, "切换播放位置: " + position);

                // 更新当前播放索引
                playlistAdapter.setCurrentPlayingIndex(position);

                // 通知监听器
                if (actionListener != null) {
                    actionListener.onPlaylistItemClick(position, song);
                }

                // 关闭底部弹出窗口
                dismiss();
            }
        });

        // 设置拖动监听器
        playlistAdapter.setOnDragListener(this::startDrag);

        // 设置删除监听器
        playlistAdapter.setOnItemDeleteListener((position, song) -> {
            // 这里不需要做任何操作，因为删除逻辑已经在handleItemSwipeDelete中处理
        });
    }

    /**
     * 处理侧滑删除
     */
    private void handleItemSwipeDelete(int position) {
        if (controllerHelper == null || playlistAdapter == null) return;

        Log.d(TAG, "处理侧滑删除，位置: " + position);

        // 获取被删除的歌曲
        List<Song> currentSongs = playlistAdapter.getSongs();
        if (position < 0 || position >= currentSongs.size()) {
            Log.w(TAG, "删除位置无效: " + position);
            return;
        }

        Song deletedSong = currentSongs.get(position);
        int currentPlayingIndex = controllerHelper.getCurrentIndex();

        // 检查是否删除的是当前播放的歌曲
        boolean isCurrentSongDeleted = (position == currentPlayingIndex);

        // 从适配器中移除
        playlistAdapter.removeItem(position);

        // 通知MediaControllerHelper删除歌曲
        controllerHelper.removeFromPlaylist(position);

        // 更新播放列表显示
        updatePlaylistDisplay();


        // 如果删除的是当前播放歌曲，检查是否需要特殊处理
        if (isCurrentSongDeleted) {
            handleCurrentSongDeleted();
        }

    }

    /**
     * 处理当前播放歌曲被删除的情况
     */
    private void handleCurrentSongDeleted() {
        if (controllerHelper == null) return;

        List<Song> playlist = controllerHelper.getPlaylist();

        if (playlist.isEmpty()) {
            // 播放列表为空，关闭底部弹窗并停止播放
            Log.d(TAG, "播放列表为空，关闭底部弹窗");
            dismiss();

            // 通知MainActivity停止播放
            if (getActivity() instanceof MainActivity mainActivity) {
                mainActivity.stopMusicPlayback();
            }
        } else {
            // 播放列表不为空，切换到下一首
            Log.d(TAG, "播放列表不为空，继续播放");
            // MediaControllerHelper已经自动处理了播放切换
        }
    }

    /**
     * 更新播放列表显示
     */
    private void updatePlaylistDisplay() {
        if (controllerHelper != null && binding != null) {
            List<Song> playlist = controllerHelper.getPlaylist();
            int currentIndex = controllerHelper.getCurrentIndex();

            // 更新标题
            binding.tvPlaylistTitle.setText(getString(R.string.playlist_title, playlist.size()));

            // 更新适配器
            if (playlistAdapter != null) {
                playlistAdapter.updateSongs(playlist);
                playlistAdapter.setCurrentPlayingIndex(currentIndex);
            }
        }
    }

    /**
     * 设置播放列表项监听器
     */
    private void setupPlaylistItemListeners() {
        playlistAdapter.setOnItemMoreClickListener((position, song, view) -> {
            // 显示歌曲底部弹出窗口
            showSongBottomSheetDialog(song);
        });
    }

    /**
     * 清空播放列表
     */
    private void clearPlaylist() {
        if (controllerHelper != null) {
            controllerHelper.clearPlaylist();
            Log.d(TAG, "播放列表已清空");

            // 通知监听器
            if (actionListener != null) {
                actionListener.onPlaylistCleared();
            }

            // 关闭底部弹出窗口
            dismiss();

            ToastUtils.showToast(requireContext(), "播放列表已清空");
        }
    }

    /**
     * 开始拖动
     */
    public void startDrag(PlaylistAdapter.PlaylistViewHolder viewHolder) {
        if (itemTouchHelper != null) {
            itemTouchHelper.startDrag(viewHolder);
        }
    }

    /**
     * 更新播放列表数据
     */
    public void updatePlaylist() {
        if (playlistAdapter != null && controllerHelper != null && binding != null) {
            List<Song> playlist = controllerHelper.getPlaylist();
            int currentIndex = controllerHelper.getCurrentIndex();

            playlistAdapter.updateSongs(playlist);
            playlistAdapter.setCurrentPlayingIndex(currentIndex);

            // 更新标题
            binding.tvPlaylistTitle.setText(getString(R.string.playlist_title, playlist.size()));

        }
    }

    /**
     * 更新当前播放歌曲的状态
     */
    private void updateCurrentPlayingSong() {
        if (controllerHelper != null && playlistAdapter != null) {
            int currentIndex = controllerHelper.getCurrentIndex();
            playlistAdapter.setCurrentPlayingIndex(currentIndex);
            playlistAdapter.notifyDataSetChanged();

            // 滚动到当前播放位置
            if (currentIndex >= 0) {
                binding.rvPlaylist.scrollToPosition(currentIndex);
            }
        }
    }

    /**
     * 获取当前播放列表适配器
     */
    public PlaylistAdapter getPlaylistAdapter() {
        return playlistAdapter;
    }

    /**
     * 显示歌曲底部弹出窗口
     */
    private void showSongBottomSheetDialog(Song song) {
        SongBottomSheetDialogFragment fragment = SongBottomSheetDialogFragment.newInstance(song);
        fragment.show(getChildFragmentManager(), "SongBottomSheetDialog");
    }

    /**
     * 获取ViewBinding
     */
    public BottomSheetPlaylistBinding getBinding() {
        return binding;
    }


}