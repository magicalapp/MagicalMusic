package com.magicalstory.music.adapter;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.RecyclerView;

import com.magicalstory.music.MainActivity;
import com.magicalstory.music.R;
import com.magicalstory.music.databinding.ItemPlaylistGridBinding;
import com.magicalstory.music.dialog.SonglistBottomSheetDialogFragment;
import com.magicalstory.music.model.Playlist;
import com.magicalstory.music.model.Song;
import com.magicalstory.music.utils.glide.Glide2;

import java.util.ArrayList;
import java.util.List;

/**
 * 歌单宫格双列布局适配器
 */
@UnstableApi
public class PlaylistGridAdapter extends RecyclerView.Adapter<PlaylistGridAdapter.ViewHolder> {
    
    private static final String TAG = "PlaylistGridAdapter";
    
    private Context context;
    private List<Playlist> playlistList;
    private OnItemClickListener onItemClickListener;
    private OnItemLongClickListener onItemLongClickListener;
    private OnSelectionChangedListener onSelectionChangedListener;
    
    // 多选相关
    private boolean isMultiSelectMode = false;
    private List<Playlist> selectedPlaylists = new ArrayList<>();
    
    // 动画相关
    private boolean isFirstLoad = false;
    private static final int ANIMATION_DURATION = 100; // 动画持续时间(毫秒)
    private static final int ANIMATION_DELAY = 30; // 每个item之间的延迟时间(毫秒)
    private static final int MAX_ANIMATED_ITEMS = 8; // 最多为多少个item执行动画(首屏)
    
    public interface OnItemClickListener {
        void onItemClick(Playlist playlist, int position);
    }
    
    public interface OnItemLongClickListener {
        void onItemLongClick(Playlist playlist, int position);
    }
    
    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }
    
    public PlaylistGridAdapter(Context context, List<Playlist> playlistList) {
        this.context = context;
        this.playlistList = playlistList;
    }
    
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }
    
    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.onItemLongClickListener = listener;
    }
    
    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.onSelectionChangedListener = listener;
    }
    
    /**
     * 设置多选模式
     */
    public void setMultiSelectMode(boolean multiSelectMode) {
        this.isMultiSelectMode = multiSelectMode;
        if (!multiSelectMode) {
            selectedPlaylists.clear();
        }
        notifyDataSetChanged();
    }
    
    /**
     * 获取多选模式状态
     */
    public boolean isMultiSelectMode() {
        return isMultiSelectMode;
    }
    
    /**
     * 切换歌单选中状态
     */
    public void toggleSelection(Playlist playlist) {
        if (selectedPlaylists.contains(playlist)) {
            selectedPlaylists.remove(playlist);
        } else {
            selectedPlaylists.add(playlist);
        }
        notifyDataSetChanged();
        
        // 通知选中状态变化
        if (onSelectionChangedListener != null) {
            onSelectionChangedListener.onSelectionChanged(selectedPlaylists.size());
        }
    }
    
    /**
     * 全选
     */
    public void selectAll() {
        selectedPlaylists.clear();
        selectedPlaylists.addAll(playlistList);
        notifyDataSetChanged();
        
        // 通知选中状态变化
        if (onSelectionChangedListener != null) {
            onSelectionChangedListener.onSelectionChanged(selectedPlaylists.size());
        }
    }
    
    /**
     * 取消全选
     */
    public void clearSelection() {
        selectedPlaylists.clear();
        notifyDataSetChanged();
    }
    
    /**
     * 获取选中的歌单列表
     */
    public List<Playlist> getSelectedPlaylists() {
        return new ArrayList<>(selectedPlaylists);
    }
    
    /**
     * 获取选中的歌单数量
     */
    public int getSelectedCount() {
        return selectedPlaylists.size();
    }
    
    /**
     * 检查歌单是否被选中
     */
    public boolean isSelected(Playlist playlist) {
        return selectedPlaylists.contains(playlist);
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPlaylistGridBinding binding = ItemPlaylistGridBinding.inflate(
                LayoutInflater.from(context), parent, false);
        
        // 调整item宽度以适应宫格布局
        ViewGroup.LayoutParams layoutParams = binding.getRoot().getLayoutParams();
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        binding.getRoot().setLayoutParams(layoutParams);
        
        return new ViewHolder(binding);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Playlist playlist = playlistList.get(position);
        
        // 设置歌单名称
        holder.binding.tvPlaylistName.setText(playlist.getName());
        
        // 设置歌曲数量
        holder.binding.tvSongCount.setText(playlist.getSongCount() + " 首歌曲");
        
        // 设置背景和文字颜色
        boolean isSelected = isMultiSelectMode && isSelected(playlist);
        
        // 设置多选背景
        if (isMultiSelectMode) {
            holder.binding.viewMultiselectBackground.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        } else {
            holder.binding.viewMultiselectBackground.setVisibility(View.GONE);
        }
        
        // 设置文字颜色
        holder.binding.tvPlaylistName.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        holder.binding.tvSongCount.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
        
        // 加载歌单封面
        loadPlaylistCover(holder.binding.ivCover, playlist);

        holder.binding.btnPlay.setOnClickListener(v -> {
            // 播放歌单音乐
            if (context instanceof MainActivity mainActivity) {
                playPlaylistSongs(mainActivity, playlist);
            }
        });

        // 设置菜单按钮点击事件 - 显示歌单底部弹窗
        holder.binding.btnMenu.setOnClickListener(v -> {
            showPlaylistBottomSheet(playlist);
        });
        
        // 设置点击事件
        holder.itemView.setOnClickListener(v -> {
            if (isMultiSelectMode) {
                // 多选模式：切换选中状态
                toggleSelection(playlist);
            } else {
                // 普通模式：执行点击事件
                if (onItemClickListener != null) {
                    onItemClickListener.onItemClick(playlist, position);
                }
            }
        });
        
        // 设置长按事件
        holder.itemView.setOnLongClickListener(v -> {
            if (!isMultiSelectMode && onItemLongClickListener != null) {
                onItemLongClickListener.onItemLongClick(playlist, position);
                return true;
            }
            return false;
        });
        
        // 执行加载动画（仅首次加载的首屏项目）
        if (isFirstLoad && position < MAX_ANIMATED_ITEMS) {
            animateItem(holder.itemView, position);
        } else {
            // 重置视图状态，避免复用时显示异常
            holder.itemView.setAlpha(1f);
            holder.itemView.setTranslationY(0f);
        }
    }
    
    /**
     * 为item执行渐变动画
     */
    private void animateItem(View view, int position) {
        // 设置初始状态：透明度为0，稍微向下偏移
        view.setAlpha(0f);
        view.setTranslationY(50f);
        
        // 创建透明度动画
        ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f);
        alphaAnimator.setDuration(ANIMATION_DURATION);
        
        // 创建位移动画
        ObjectAnimator translationAnimator = ObjectAnimator.ofFloat(view, "translationY", 50f, 0f);
        translationAnimator.setDuration(ANIMATION_DURATION);
        
        // 创建动画集合
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(alphaAnimator, translationAnimator);
        animatorSet.setInterpolator(new DecelerateInterpolator());
        
        // 设置延迟时间，让每个item按顺序出现
        animatorSet.setStartDelay(position * ANIMATION_DELAY);
        
        // 开始动画
        animatorSet.start();
    }
    
    private void loadPlaylistCover(ImageView imageView, Playlist playlist) {
        // 获取歌单中的第一首歌曲作为封面
        List<Song> songs = playlist.getSongs();
        String albumArtUri = null;
        
        if (songs != null && !songs.isEmpty()) {
            Song firstSong = songs.get(0);
            if (firstSong.getAlbumId() > 0) {
                albumArtUri = "content://media/external/audio/albumart/" + firstSong.getAlbumId();
            }
        }


        Glide2.loadImage(context,imageView,albumArtUri,R.drawable.place_holder_album);


    }
    
    /**
     * 播放歌单歌曲
     */
    private void playPlaylistSongs(MainActivity mainActivity, Playlist playlist) {
        List<Song> playlistSongs = playlist.getSongs();
        
        if (playlistSongs != null && !playlistSongs.isEmpty()) {
            Log.d(TAG, "播放歌单: " + playlist.getName() + ", 歌曲数量: " + playlistSongs.size());
            mainActivity.playFromPlaylist(playlistSongs, 0); // 播放歌单的第一首歌曲
        }
    }

    /**
     * 显示歌单底部弹窗
     */
    private void showPlaylistBottomSheet(Playlist playlist) {
        if (context instanceof MainActivity) {
            SonglistBottomSheetDialogFragment fragment = SonglistBottomSheetDialogFragment.newInstance(playlist);
            fragment.show(((MainActivity) context).getSupportFragmentManager(), "PlaylistBottomSheet");
            Log.d(TAG, "显示歌单底部弹窗: " + playlist.getName());
        }
    }
    
    @Override
    public int getItemCount() {
        return playlistList != null ? playlistList.size() : 0;
    }
    
    public void updateData(List<Playlist> newPlaylistList) {
        this.playlistList = newPlaylistList;
        // 重置首次加载标志，允许重新执行动画
        
        notifyDataSetChanged();
    }
    
    /**
     * 禁用后续的加载动画
     */
    public void disableLoadAnimation() {
        this.isFirstLoad = false;
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        ItemPlaylistGridBinding binding;
        
        public ViewHolder(@NonNull ItemPlaylistGridBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
} 