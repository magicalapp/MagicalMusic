package com.magicalstory.music.dialog;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.magicalstory.music.R;
import com.magicalstory.music.databinding.BottomSheetPlaylistMenuBinding;
import com.magicalstory.music.model.Playlist;
import com.magicalstory.music.model.Song;
import com.magicalstory.music.player.MediaControllerHelper;
import com.magicalstory.music.utils.app.ToastUtils;
import com.magicalstory.music.utils.file.SafUtils;
import com.magicalstory.music.utils.glide.GlideUtils;

import java.util.List;

/**
 * 播放列表底部弹出窗口Fragment
 * 显示播放列表的更多操作选项
 */
@UnstableApi
public class SonglistBottomSheetDialogFragment extends BottomSheetDialogFragment {

    private static final String TAG = "PlaylistBottomSheetDialogFragment";
    private static final String ARG_PLAYLIST_ID = "playlist_id";
    private static final String ARG_PLAYLIST_NAME = "playlist_name";
    private static final String ARG_PLAYLIST_DESCRIPTION = "playlist_description";
    private static final String ARG_SONG_COUNT = "song_count";
    private static final String ARG_COVER_PATH = "cover_path";

    private BottomSheetPlaylistMenuBinding binding;
    private Playlist playlist;
    private MediaControllerHelper controllerHelper;

    /**
     * 创建新的实例
     */
    public static SonglistBottomSheetDialogFragment newInstance(Playlist playlist) {
        SonglistBottomSheetDialogFragment fragment = new SonglistBottomSheetDialogFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_PLAYLIST_ID, playlist.getId());
        args.putString(ARG_PLAYLIST_NAME, playlist.getName());
        args.putString(ARG_PLAYLIST_DESCRIPTION, playlist.getDescription());
        args.putInt(ARG_SONG_COUNT, playlist.getSongCount());
        args.putString(ARG_COVER_PATH, playlist.getCoverPath());
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 使用ViewBinding创建视图
        binding = BottomSheetPlaylistMenuBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 从Bundle中重建播放列表对象
        if (getArguments() != null) {
            Bundle args = getArguments();
            playlist = new Playlist();
            playlist.setId(args.getLong(ARG_PLAYLIST_ID, 0));
            playlist.setName(args.getString(ARG_PLAYLIST_NAME, ""));
            playlist.setDescription(args.getString(ARG_PLAYLIST_DESCRIPTION, ""));
            playlist.setSongCount(args.getInt(ARG_SONG_COUNT, 0));
            playlist.setCoverPath(args.getString(ARG_COVER_PATH, ""));
        }

        // 初始化MediaControllerHelper
        initControllerHelper();

        // 设置播放列表信息
        setupPlaylistInfo();

        // 设置点击事件
        setupClickListeners();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // 处理SAF文件保存结果
        if (playlist != null) {
            SafUtils.handleActivityResult(this, requestCode, resultCode, data, playlist);
        }
    }

    /**
     * 初始化MediaControllerHelper
     */
    private void initControllerHelper() {
        controllerHelper = MediaControllerHelper.getInstance();
    }

    /**
     * 设置MediaControllerHelper
     */
    public void setMediaControllerHelper(MediaControllerHelper controllerHelper) {
        this.controllerHelper = controllerHelper;
    }

    /**
     * 设置播放列表信息
     */
    private void setupPlaylistInfo() {
        if (playlist == null) {
            Log.w(TAG, "播放列表数据为null");
            return;
        }

        // 设置播放列表名称
        binding.tvPlaylistName.setText(playlist.getName());

        // 设置播放列表信息
        String playlistInfo = playlist.getDescription();
        if (playlist.getSongCount() > 0) {
            if (playlistInfo != null && !playlistInfo.isEmpty()) {
                playlistInfo += " • " + playlist.getSongCount() + "首歌曲";
            } else {
                playlistInfo = playlist.getSongCount() + "首歌曲";
            }
        }
        binding.tvPlaylistInfo.setText(playlistInfo);

        // 加载播放列表封面
        if (playlist.getCoverPath() != null && !playlist.getCoverPath().isEmpty()) {
            // 如果有封面路径，尝试加载
            try {
                // 尝试从封面路径中提取专辑ID
                if (playlist.getCoverPath().contains("albumart/")) {
                    String albumIdStr = playlist.getCoverPath().substring(playlist.getCoverPath().lastIndexOf("/") + 1);
                    long albumId = Long.parseLong(albumIdStr);
                    GlideUtils.loadAlbumCover(requireContext(), albumId, binding.ivPlaylistCover);
                } else {
                    // 如果不是专辑封面路径，显示默认封面
                    binding.ivPlaylistCover.setImageResource(R.drawable.place_holder_album);
                }
            } catch (Exception e) {
                // 如果加载失败，显示默认封面
                binding.ivPlaylistCover.setImageResource(R.drawable.place_holder_album);
            }
        } else {
            // 如果没有封面，显示默认封面
            binding.ivPlaylistCover.setImageResource(R.drawable.place_holder_album);
        }
    }

    /**
     * 设置点击事件
     */
    private void setupClickListeners() {
        // 下一首播放
        binding.llPlayNext.setOnClickListener(v -> {
            Log.d(TAG, "下一首播放被点击");
            handlePlayNext();
            dismiss();
        });

        // 添加到播放队列
        binding.llAddToQueue.setOnClickListener(v -> {
            Log.d(TAG, "添加到播放队列被点击");
            handleAddToQueue();
            dismiss();
        });

        // 重命名
        binding.llRename.setOnClickListener(v -> {
            Log.d(TAG, "重命名被点击");
            handleRename();
            dismiss();
        });

        // 另存为文件
        binding.llSaveAsFile.setOnClickListener(v -> {
            Log.d(TAG, "另存为文件被点击");
            handleSaveAsFile();
            dismiss();
        });

        // 删除歌单
        binding.llDeletePlaylist.setOnClickListener(v -> {
            Log.d(TAG, "删除歌单被点击");
            handleDeletePlaylist();
            dismiss();
        });
    }

    /**
     * 处理下一首播放
     */
    private void handlePlayNext() {
        if (playlist == null) {
            ToastUtils.showToast(requireContext(), "播放列表数据为空");
            return;
        }

        if (controllerHelper == null) {
            ToastUtils.showToast(requireContext(), "播放控制器未初始化");
            return;
        }

        // 获取播放列表中的所有歌曲
        List<Song> playlistSongs = playlist.getSongs();
        if (playlistSongs == null || playlistSongs.isEmpty()) {
            ToastUtils.showToast(requireContext(), "播放列表中没有歌曲");
            return;
        }

        // 添加到下一首播放
        controllerHelper.addSongsToPlayNext(playlistSongs);
        ToastUtils.showToast(requireContext(), "已添加到下一首播放");

        Log.d(TAG, "播放列表歌曲已添加到下一首播放: " + playlist.getName() + ", 歌曲数量: " + playlistSongs.size());
    }

    /**
     * 处理添加到播放队列
     */
    private void handleAddToQueue() {
        if (playlist == null) {
            ToastUtils.showToast(requireContext(), "播放列表数据为空");
            return;
        }

        if (controllerHelper == null) {
            ToastUtils.showToast(requireContext(), "播放控制器未初始化");
            return;
        }

        // 获取播放列表中的所有歌曲
        List<Song> playlistSongs = playlist.getSongs();
        if (playlistSongs == null || playlistSongs.isEmpty()) {
            ToastUtils.showToast(requireContext(), "播放列表中没有歌曲");
            return;
        }

        // 添加到播放列表末尾
        controllerHelper.addSongsToPlaylist(playlistSongs);
        ToastUtils.showToast(requireContext(), "已添加到播放队列");

        Log.d(TAG, "播放列表歌曲已添加到播放队列: " + playlist.getName() + ", 歌曲数量: " + playlistSongs.size());
    }

    /**
     * 处理重命名
     */
    private void handleRename() {
        if (playlist == null) {
            ToastUtils.showToast(requireContext(), "播放列表数据为空");
            return;
        }

        // 使用dialogUtils显示输入对话框
        dialogUtils.getInstance().showInputDialog(
                requireContext(),
                getString(R.string.rename_playlist_title),
                getString(R.string.rename_playlist_hint),
                playlist.getName(),
                false,
                "",
                new dialogUtils.InputDialogListener() {
                    @Override
                    public void onInputProvided(String newName) {
                        if (newName != null && !newName.trim().isEmpty()) {
                            // 更新播放列表名称
                            playlist.setName(newName.trim());
                            playlist.save();
                            
                            ToastUtils.showToast(requireContext(), getString(R.string.playlist_renamed));
                            Log.d(TAG, "播放列表已重命名: " + newName.trim());
                        } else {
                            ToastUtils.showToast(requireContext(), getString(R.string.playlist_name_empty));
                        }
                    }
                }
        );
    }

    /**
     * 处理另存为文件
     */
    private void handleSaveAsFile() {
        if (playlist == null) {
            ToastUtils.showToast(requireContext(), "播放列表数据为空");
            return;
        }

        // 检查播放列表是否有歌曲
        List<Song> songs = playlist.getSongs();
        if (songs == null || songs.isEmpty()) {
            ToastUtils.showToast(requireContext(), "播放列表中没有歌曲");
            return;
        }

        // 使用SAF保存文件
        SafUtils.savePlaylistToFile(this, playlist);
    }

    /**
     * 处理删除歌单
     */
    private void handleDeletePlaylist() {
        if (playlist == null) {
            ToastUtils.showToast(requireContext(), "播放列表数据为空");
            return;
        }

        // 检查是否为系统播放列表
        if (playlist.isSystemPlaylist()) {
            ToastUtils.showToast(requireContext(), getString(R.string.system_playlist_cannot_delete));
            return;
        }

        // 使用dialogUtils显示确认对话框
        dialogUtils.showAlertDialog(
                requireContext(),
                getString(R.string.delete_confirmation_title),
                String.format(getString(R.string.delete_playlist_confirmation), playlist.getName()),
                getString(R.string.dialog_delete),
                getString(R.string.dialog_cancel),
                null,
                true,
                new dialogUtils.onclick_with_dismiss() {
                    @Override
                    public void click_confirm() {
                        performDeletePlaylist();
                    }

                    @Override
                    public void click_cancel() {
                        // 取消删除
                    }

                    @Override
                    public void click_three() {
                        // 不使用第三个按钮
                    }

                    @Override
                    public void dismiss() {
                        // 对话框关闭
                    }
                }
        );

        Log.d(TAG, "显示删除确认对话框: " + playlist.getName());
    }

    /**
     * 执行删除播放列表操作
     */
    private void performDeletePlaylist() {
        try {
            // 删除播放列表中的所有歌曲关联
            List<Song> songs = playlist.getSongs();
            for (Song song : songs) {
                playlist.removeSong(song);
            }

            // 删除播放列表本身
            playlist.delete();

            ToastUtils.showToast(requireContext(), getString(R.string.playlist_deleted));
            Log.d(TAG, "播放列表已删除: " + playlist.getName());

        } catch (Exception e) {
            Log.e(TAG, "删除播放列表失败", e);
            ToastUtils.showToast(requireContext(), "删除失败: " + e.getMessage());
        }
    }

    /**
     * 获取ViewBinding
     */
    public BottomSheetPlaylistMenuBinding getBinding() {
        return binding;
    }
} 