package com.magicalstory.music.homepage;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.search.SearchView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.magicalstory.music.MainActivity;
import com.magicalstory.music.R;
import com.magicalstory.music.base.BaseFragment;
import com.magicalstory.music.databinding.FragmentLibraryBinding;
import com.magicalstory.music.adapter.SongHorizontalAdapter;
import com.magicalstory.music.adapter.AlbumHorizontalAdapter;
import com.magicalstory.music.adapter.ArtistHorizontalAdapter;
import com.magicalstory.music.adapter.SearchResultAdapter;
import com.magicalstory.music.model.Song;
import com.magicalstory.music.model.Album;
import com.magicalstory.music.model.Artist;
import com.magicalstory.music.model.Playlist;
import com.magicalstory.music.utils.screen.DensityUtil;
import com.magicalstory.music.dialog.dialogUtils;
import com.magicalstory.music.service.MusicScanService;
import com.magicalstory.music.utils.app.ToastUtils;

import org.litepal.LitePal;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.magicalstory.music.model.FavoriteSong;
import com.magicalstory.music.utils.glide.CoverFallbackUtils;
import com.magicalstory.music.service.CoverFetchService;

import android.util.Log;

@UnstableApi
public class LibraryFragment extends BaseFragment<FragmentLibraryBinding> {

    // 适配器
    private SongHorizontalAdapter mySongsAdapter;
    private AlbumHorizontalAdapter myAlbumsAdapter;
    private ArtistHorizontalAdapter myArtistsAdapter;

    // 搜索相关
    private SearchResultAdapter searchResultAdapter;
    private String currentSearchQuery = "";
    private String currentSearchType = "all"; // all, songs, album, artist, playlist

    // 网络请求相关
    private ExecutorService executorService;
    private Handler mainHandler;

    // 扫描相关
    private MusicScanService musicScanService;
    private boolean serviceBound = false;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<Intent> safLauncher;

    // 扫描完成广播接收器
    private final BroadcastReceiver scanCompleteReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("LibraryFragment", "收到广播: " + intent.getAction());
            if (MusicScanService.ACTION_SCAN_COMPLETE.equals(intent.getAction())) {
                int scanCount = intent.getIntExtra(MusicScanService.EXTRA_SCAN_COUNT, 0);
                Log.d("LibraryFragment", "扫描完成广播，新增歌曲数量: " + scanCount);
                ToastUtils.showToast(getContext(), "扫描完成，新增 " + scanCount + " 首歌曲");
                // 扫描完成后重新加载歌曲并显示布局
                reloadMusicDataAndShowLayout();
                // 关闭进度对话框
                dialogUtils.getInstance().dismissProgressDialog();
            }
        }
    };

    // 服务连接
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicScanService.MusicScanBinder binder = (MusicScanService.MusicScanBinder) service;
            musicScanService = binder.getService();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    @Override
    protected FragmentLibraryBinding getViewBinding(LayoutInflater inflater, ViewGroup container) {
        return FragmentLibraryBinding.inflate(inflater, container, false);
    }

    @Override
    protected boolean usePersistentView() {
        return true;
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.fragment_library;
    }

    @Override
    protected FragmentLibraryBinding bindPersistentView(View view) {
        return FragmentLibraryBinding.bind(view);
    }
    
    @Override
    protected void initViewForPersistentView() {
        super.initViewForPersistentView();
        // 初始化权限请求启动器
        initPermissionLauncher();
        // 初始化SAF启动器
        initSafLauncher();
        // 初始化视图
        setupSearchView();
        // 初始化RecyclerView
        initRecyclerViews();
        // 初始化搜索相关
        initSearchComponents();
        // 加载音乐数据
        loadMusicData();
    }
    
    @Override
    protected void initDataForPersistentView() {
        super.initDataForPersistentView();
        // 初始化Handler和线程池
        mainHandler = new Handler(Looper.getMainLooper());
        executorService = Executors.newCachedThreadPool();

        // 绑定服务
        Intent serviceIntent = new Intent(getContext(), MusicScanService.class);
        context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        // 注册广播接收器
        registerScanCompleteReceiver();
    }
    
    @Override
    protected void initListenerForPersistentView() {
        super.initListenerForPersistentView();

        // 关于应用按钮点击事件
        binding.buttonAboutApp.setOnClickListener(v -> {
            // 跳转到关于应用页面
            navigateToAboutApp();
        });

        // 扫描媒体按钮点击事件
        binding.buttonScanMusic.setOnClickListener(v -> {
            // 开始SAF授权扫描
            startSafScan();
        });

        // 我的歌曲标题点击事件
        binding.itemHeaderMySongs.setOnClickListener(v -> {
            // 跳转到歌曲列表页面
            navigateToSongsList();
        });

        // 我的专辑标题点击事件
        binding.itemHeaderMyAlbums.setOnClickListener(v -> {
            // 跳转到专辑页面
            navigateToAlbums();
        });

        // 艺人列表标题点击事件
        binding.itemHeaderMyArtists.setOnClickListener(v -> {
            // 跳转到艺术家页面
            navigateToArtists();
        });
    }

    /**
     * 初始化搜索相关组件
     */
    private void initSearchComponents() {
        // 初始化搜索结果RecyclerView
        binding.searchLayout.recyclerviewResult.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        searchResultAdapter = new SearchResultAdapter(getContext());

        // 设置搜索结果点击事件
        searchResultAdapter.setOnSongClickListener((song, position) -> {
            // 播放选中的歌曲
            if (getActivity() instanceof MainActivity mainActivity) {
                List<Song> allSongs = searchResultAdapter.getAllSongs();
                mainActivity.playFromPlaylist(allSongs, position);
            }
        });

        searchResultAdapter.setOnAlbumClickListener((album, position) -> {
            // 跳转到专辑详情页面
            Bundle bundle = new Bundle();
            bundle.putLong("album_id", album.getAlbumId());
            bundle.putString("artist_name", album.getArtist());
            bundle.putString("album_name", album.getAlbumName());
            Navigation.findNavController(requireView()).navigate(R.id.action_library_to_album_detail, bundle);
        });

        searchResultAdapter.setOnArtistClickListener((artist, position) -> {
            // 跳转到艺术家详情页面
            Bundle bundle = new Bundle();
            bundle.putLong("artist_id", artist.getArtistId());
            bundle.putString("artist_name", artist.getArtistName());
            Navigation.findNavController(requireView()).navigate(R.id.action_library_to_artist_detail, bundle);
        });

        binding.searchLayout.recyclerviewResult.setAdapter(searchResultAdapter);

        // 初始化搜索界面状态 - 默认显示placeholder，隐藏其他
        binding.searchLayout.layoutEmpty.setVisibility(View.GONE);
        binding.searchLayout.placeholder.setVisibility(View.VISIBLE);

        // 设置搜索监听器
        binding.openSearchView.addTransitionListener((searchView, previousState, newState) -> {
            if (newState == SearchView.TransitionState.SHOWING) {
                binding.searchLayout.getRoot().setVisibility(View.VISIBLE);
                // SearchView展开时隐藏BottomNavigationView
                hideBottomNavigation();
            } else if (newState == SearchView.TransitionState.HIDING) {
                // SearchView收起时显示BottomNavigationView
                showBottomNavigation();
            }
        });

        // 设置搜索文本变化监听器
        binding.openSearchView.getEditText().addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                currentSearchQuery = s.toString().trim();
                android.util.Log.d("LibraryFragment", "搜索文本变化: " + currentSearchQuery);
                if (!TextUtils.isEmpty(currentSearchQuery)) {
                    performSearch(currentSearchQuery, currentSearchType);
                } else {
                    // 清空搜索结果，显示placeholder
                    searchResultAdapter.updateSearchResults(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
                    binding.searchLayout.layoutEmpty.setVisibility(View.GONE);
                    binding.searchLayout.placeholder.setVisibility(View.VISIBLE);
                }
            }
        });

        // 设置Chip点击监听器
        binding.searchLayout.chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                Chip selectedChip = group.findViewById(checkedIds.get(0));
                if (selectedChip != null) {
                    String chipText = selectedChip.getText().toString();
                    updateSearchType(chipText);
                }
            }
        });
    }

    /**
     * 更新搜索类型
     */
    private void updateSearchType(String chipText) {
        String newSearchType;
        switch (chipText) {
            case "全部":
                newSearchType = "all";
                break;
            case "歌曲":
                newSearchType = "songs";
                break;
            case "专辑":
                newSearchType = "album";
                break;
            case "艺术家":
                newSearchType = "artist";
                break;
            case "播放列表":
                newSearchType = "playlist";
                break;
            default:
                newSearchType = "all";
                break;
        }

        android.util.Log.d("LibraryFragment", "搜索类型切换: " + chipText + " -> " + newSearchType);

        if (!currentSearchType.equals(newSearchType)) {
            currentSearchType = newSearchType;
            if (!TextUtils.isEmpty(currentSearchQuery)) {
                performSearch(currentSearchQuery, currentSearchType);
            }
        }
    }

    /**
     * 执行搜索
     */
    private void performSearch(String query, String searchType) {
        android.util.Log.d("LibraryFragment", "开始搜索: " + query + ", 类型: " + searchType);

        if (executorService == null) {
            executorService = Executors.newCachedThreadPool();
        }

        executorService.execute(() -> {
            try {
                List<Song> songs = new ArrayList<>();
                List<Album> albums = new ArrayList<>();
                List<Artist> artists = new ArrayList<>();

                switch (searchType) {
                    case "all":
                        // 搜索全部类型
                        songs = searchAllSongs(query);
                        albums = searchAllAlbums(query);
                        artists = searchAllArtists(query);
                        break;
                    case "songs":
                        songs = searchSongs(query);
                        break;
                    case "album":
                        albums = searchAlbums(query);
                        break;
                    case "artist":
                        artists = searchArtists(query);
                        break;
                    case "playlist":
                        songs = searchByPlaylist(query);
                        break;
                }

                // 创建final变量用于lambda表达式
                final List<Song> finalSongs = songs;
                final List<Album> finalAlbums = albums;
                final List<Artist> finalArtists = artists;

                // 在主线程中更新UI
                if (mainHandler != null) {
                    mainHandler.post(() -> {
                        if (searchType.equals("all")) {
                            // 全部搜索：显示所有类型结果
                            searchResultAdapter.updateSearchResults(finalSongs, finalAlbums, finalArtists);
                        } else {
                            // 单一类型搜索
                            switch (searchType) {
                                case "songs":
                                    searchResultAdapter.updateSingleTypeResults("songs", finalSongs);
                                    break;
                                case "album":
                                    searchResultAdapter.updateSingleTypeResults("album", finalAlbums);
                                    break;
                                case "artist":
                                    searchResultAdapter.updateSingleTypeResults("artist", finalArtists);
                                    break;
                                case "playlist":
                                    searchResultAdapter.updateSingleTypeResults("songs", finalSongs);
                                    break;
                            }
                        }

                        // 检查是否有搜索结果
                        boolean hasResults = !finalSongs.isEmpty() || !finalAlbums.isEmpty() || !finalArtists.isEmpty();
                        if (hasResults) {
                            binding.searchLayout.layoutEmpty.setVisibility(View.GONE);
                            binding.searchLayout.placeholder.setVisibility(View.GONE);
                        } else {
                            binding.searchLayout.layoutEmpty.setVisibility(View.VISIBLE);
                            binding.searchLayout.placeholder.setVisibility(View.GONE);
                        }
                    });
                }
            } catch (Exception e) {
                android.util.Log.e("LibraryFragment", "搜索失败: " + e.getMessage(), e);
                if (mainHandler != null) {
                    mainHandler.post(() -> {
                        searchResultAdapter.updateSearchResults(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
                        binding.searchLayout.layoutEmpty.setVisibility(View.VISIBLE);
                        binding.searchLayout.placeholder.setVisibility(View.GONE);
                    });
                }
            }
        });
    }

    /**
     * 搜索全部歌曲（包括标题、艺术家、专辑匹配）
     */
    private List<Song> searchAllSongs(String query) {
        List<Song> results = new ArrayList<>();

        // 搜索歌曲标题
        List<Song> titleResults = LitePal.where("title like ?", "%" + query + "%").find(Song.class);
        results.addAll(titleResults);

        // 搜索艺术家
        List<Song> artistResults = LitePal.where("artist like ?", "%" + query + "%").find(Song.class);
        for (Song song : artistResults) {
            if (!results.contains(song)) {
                results.add(song);
            }
        }

        // 搜索专辑
        List<Song> albumResults = LitePal.where("album like ?", "%" + query + "%").find(Song.class);
        for (Song song : albumResults) {
            if (!results.contains(song)) {
                results.add(song);
            }
        }

        return results;
    }

    /**
     * 搜索全部专辑
     */
    private List<Album> searchAllAlbums(String query) {
        return LitePal.where("albumName like ?", "%" + query + "%").find(Album.class);
    }

    /**
     * 搜索全部艺术家
     */
    private List<Artist> searchAllArtists(String query) {
        return LitePal.where("artistName like ?", "%" + query + "%").find(Artist.class);
    }

    /**
     * 搜索歌曲（包括标题、艺术家、专辑匹配）
     */
    private List<Song> searchSongs(String query) {
        List<Song> results = new ArrayList<>();

        // 搜索歌曲标题
        List<Song> titleResults = LitePal.where("title like ?", "%" + query + "%").find(Song.class);
        results.addAll(titleResults);

        // 搜索艺术家
        List<Song> artistResults = LitePal.where("artist like ?", "%" + query + "%").find(Song.class);
        for (Song song : artistResults) {
            if (!results.contains(song)) {
                results.add(song);
            }
        }

        // 搜索专辑
        List<Song> albumResults = LitePal.where("album like ?", "%" + query + "%").find(Song.class);
        for (Song song : albumResults) {
            if (!results.contains(song)) {
                results.add(song);
            }
        }

        return results;
    }

    /**
     * 搜索专辑
     */
    private List<Album> searchAlbums(String query) {
        return LitePal.where("albumName like ?", "%" + query + "%").find(Album.class);
    }

    /**
     * 搜索艺术家
     */
    private List<Artist> searchArtists(String query) {
        return LitePal.where("artistName like ?", "%" + query + "%").find(Artist.class);
    }

    /**
     * 按播放列表搜索
     */
    private List<Song> searchByPlaylist(String query) {
        List<Song> results = new ArrayList<>();

        // 先查找匹配的播放列表
        List<Playlist> playlists = LitePal.where("name like ?", "%" + query + "%").find(Playlist.class);

        for (Playlist playlist : playlists) {
            // 查找播放列表中的歌曲
            List<com.magicalstory.music.model.PlaylistSong> playlistSongs =
                    LitePal.where("playlistId = ?", String.valueOf(playlist.getId())).find(com.magicalstory.music.model.PlaylistSong.class);

            for (com.magicalstory.music.model.PlaylistSong playlistSong : playlistSongs) {
                Song song = LitePal.find(Song.class, playlistSong.getSongId());
                if (song != null && !results.contains(song)) {
                    results.add(song);
                }
            }
        }

        return results;
    }

    private void setupSearchView() {
        binding.openSearchView.getEditText().setGravity(Gravity.CENTER_VERTICAL);
        binding.openSearchView.getEditText().setPadding(0, DensityUtil.dip2px(context, 6), 0, 0);
    }

    private void hideBottomNavigation() {
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            mainActivity.hideBottomNavigation();
        }
    }

    private void showBottomNavigation() {
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            mainActivity.showBottomNavigation();
        }
    }

    /**
     * 检查搜索框是否展开
     * @return true表示展开，false表示收起
     */
    public boolean isSearchViewExpanded() {
        return binding != null && binding.openSearchView != null &&
                binding.openSearchView.getCurrentTransitionState() == SearchView.TransitionState.SHOWN;
    }

    /**
     * 关闭搜索框
     * @return true表示成功关闭，false表示搜索框未展开
     */
    public boolean closeSearchView() {
        if (binding != null && binding.openSearchView != null && isSearchViewExpanded()) {
            binding.openSearchView.hide();
            return true;
        }
        return false;
    }

    /**
     * 初始化RecyclerView
     */
    private void initRecyclerViews() {
        // 我的歌曲
        binding.rvMySongs.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        mySongsAdapter = new SongHorizontalAdapter(getContext(), new ArrayList<>(), true);
        binding.rvMySongs.setAdapter(mySongsAdapter);

        // 我的专辑
        binding.rvMyAlbums.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        myAlbumsAdapter = new AlbumHorizontalAdapter(getContext(), new ArrayList<>());
        myAlbumsAdapter.setOnItemClickListener((album, position) -> {
            // 跳转到专辑详情页面
            Bundle bundle = new Bundle();
            bundle.putLong("album_id", album.getAlbumId());
            bundle.putString("artist_name", album.getArtist());
            bundle.putString("album_name", album.getAlbumName());
            Navigation.findNavController(requireView()).navigate(R.id.action_library_to_album_detail, bundle);
        });
        binding.rvMyAlbums.setAdapter(myAlbumsAdapter);

        // 艺人列表
        binding.rvMyArtists.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        myArtistsAdapter = new ArtistHorizontalAdapter(getContext(), new ArrayList<>(), this);
        binding.rvMyArtists.setAdapter(myArtistsAdapter);
    }

    /**
     * 加载音乐数据
     */
    private void loadMusicData() {
        // 在后台线程中进行数据库查询，避免ANR
        if (executorService == null) {
            executorService = Executors.newCachedThreadPool();
        }

        // 启动后台服务批量获取所有专辑和歌手的封面
        CoverFetchService.startFetchAllCovers(getContext());

        executorService.execute(() -> {
            try {
                // 加载所有歌曲（按添加时间倒序，取前10首）
                List<Song> allSongs = LitePal.order("dateAdded desc").limit(10).find(Song.class);

                // 加载所有专辑（按添加时间倒序排列，取前10个）
                List<Album> allAlbums = LitePal.order("dateAdded desc").limit(10).find(Album.class);

                // 为专辑设置回退封面
                if (allAlbums != null && !allAlbums.isEmpty()) {
                    int albumCoverCount = CoverFallbackUtils.setAlbumsFallbackCover(allAlbums);
                    if (albumCoverCount > 0) {
                        android.util.Log.d("LibraryFragment", "为 " + albumCoverCount + " 个专辑设置了回退封面");
                    }
                }

                // 加载所有艺术家（按添加时间倒序排列，取前10个）
                List<Artist> allArtists = LitePal.order("dateAdded desc").limit(10).find(Artist.class);

                if (allArtists != null && !allArtists.isEmpty()) {
                    int artistCoverCount = CoverFallbackUtils.setArtistsFallbackCover(allArtists);
                    if (artistCoverCount > 0) {
                        android.util.Log.d("LibraryFragment", "为 " + artistCoverCount + " 个艺术家设置了回退封面");
                    }
                }

                // 在主线程中更新UI
                if (mainHandler != null) {
                    mainHandler.post(() -> {
                        try {
                            // 更新我的歌曲
                            if (allSongs != null && !allSongs.isEmpty()) {
                                mySongsAdapter.updateData(allSongs);
                                binding.layoutMySongs.setVisibility(View.VISIBLE);
                            } else {
                                binding.layoutMySongs.setVisibility(View.GONE);
                            }

                            // 更新我的专辑
                            if (allAlbums != null && !allAlbums.isEmpty()) {
                                myAlbumsAdapter.updateData(allAlbums);
                                binding.layoutMyAlbums.setVisibility(View.VISIBLE);
                            } else {
                                binding.layoutMyAlbums.setVisibility(View.GONE);
                            }

                            // 更新艺人列表
                            if (allArtists != null && !allArtists.isEmpty()) {
                                myArtistsAdapter.updateData(allArtists);
                                binding.layoutMyArtists.setVisibility(View.VISIBLE);
                            } else {
                                binding.layoutMyArtists.setVisibility(View.GONE);
                            }

                            // 检查是否所有数据都为空，如果是则显示空状态
                            boolean hasSongs = allSongs != null && !allSongs.isEmpty();
                            boolean hasAlbums = allAlbums != null && !allAlbums.isEmpty();
                            boolean hasArtists = allArtists != null && !allArtists.isEmpty();
                            
                            if (!hasSongs && !hasAlbums && !hasArtists) {
                                // 所有数据都为空，显示空状态
                                binding.layoutEmpty.setVisibility(View.VISIBLE);
                            } else {
                                // 有数据，隐藏空状态
                                binding.layoutEmpty.setVisibility(View.GONE);
                            }

                        } catch (Exception e) {
                            android.util.Log.e("LibraryFragment", "Error updating UI: " + e.getMessage(), e);
                        }
                    });
                }
            } catch (Exception e) {
                android.util.Log.e("LibraryFragment", "Error loading music data: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 跳转到歌曲列表页面
     */
    private void navigateToSongsList() {
        // 使用Navigation组件进行跳转，传递数据类型参数
        Bundle bundle = new Bundle();
        bundle.putString("dataType", "all");
        Navigation.findNavController(requireView()).navigate(R.id.action_library_to_recent_songs, bundle);
    }

    /**
     * 跳转到专辑页面
     */
    private void navigateToAlbums() {
        // 使用Navigation组件进行跳转，传递排序参数
        Bundle bundle = new Bundle();
        bundle.putString("sortType", "dateAdded");
        Navigation.findNavController(requireView()).navigate(R.id.action_library_to_albums, bundle);
    }

    /**
     * 跳转到艺术家页面
     */
    private void navigateToArtists() {
        // 使用Navigation组件进行跳转，传递排序参数
        Bundle bundle = new Bundle();
        bundle.putString("sortType", "dateAdded");
        Navigation.findNavController(requireView()).navigate(R.id.action_library_to_artists, bundle);
    }

    /**
     * 初始化权限请求启动器
     */
    private void initPermissionLauncher() {
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allGranted = true;
                    for (Boolean granted : result.values()) {
                        if (!granted) {
                            allGranted = false;
                            break;
                        }
                    }

                    if (allGranted) {
                        // 权限已授予，开始扫描
                        startMusicScan();
                    } else {
                        // 权限被拒绝
                        ToastUtils.showToast(getContext(), "需要存储权限才能扫描音乐文件");
                    }
                }
        );
    }

    /**
     * 初始化SAF启动器
     */
    private void initSafLauncher() {
        safLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            // 获取SAF权限，开始扫描
                            startMusicScanWithSaf(uri);
                        }
                    } else {
                        dialogUtils.getInstance().dismissProgressDialog();
                        ToastUtils.showToast(getContext(), "未选择文件夹或权限被拒绝");
                    }
                }
        );
    }

    /**
     * 开始SAF授权扫描
     */
    private void startSafScan() {
        // 显示进度对话框
        dialogUtils.getInstance().showProgressDialog(getContext(), "正在准备扫描...");
        
        // 创建SAF Intent
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        
        // 启动SAF选择器
        safLauncher.launch(intent);
    }

    /**
     * 使用SAF权限开始扫描
     */
    private void startMusicScanWithSaf(Uri uri) {
        try {
            // 获取持久权限
            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            getContext().getContentResolver().takePersistableUriPermission(uri, takeFlags);
            
            // 更新进度对话框
            dialogUtils.getInstance().changeProgressDialogText("正在扫描音乐文件...");
            
            // 开始扫描
            if (serviceBound && musicScanService != null) {
                if (!musicScanService.isScanning()) {
                    musicScanService.startMusicScan();
                }
            } else {
                // 服务未绑定，直接启动服务
                Intent serviceIntent = new Intent(getContext(), MusicScanService.class);
                getContext().startService(serviceIntent);
            }
            
        } catch (Exception e) {
            Log.e("LibraryFragment", "SAF扫描失败: " + e.getMessage(), e);
            dialogUtils.getInstance().dismissProgressDialog();
            ToastUtils.showToast(getContext(), "扫描失败: " + e.getMessage());
        }
    }

    /**
     * 开始音乐扫描
     */
    private void startMusicScan() {
        // 显示进度对话框
        dialogUtils.getInstance().showProgressDialog(getContext(), "正在扫描音乐文件...");
        
        if (serviceBound && musicScanService != null) {
            if (!musicScanService.isScanning()) {
                musicScanService.startMusicScan();
            }
        } else {
            // 服务未绑定，直接启动服务
            Intent serviceIntent = new Intent(getContext(), MusicScanService.class);
            getContext().startService(serviceIntent);
        }
    }

    /**
     * 注册扫描完成广播接收器
     */
    private void registerScanCompleteReceiver() {
        try {
            IntentFilter filter = new IntentFilter(MusicScanService.ACTION_SCAN_COMPLETE);
            LocalBroadcastManager.getInstance(getContext()).registerReceiver(scanCompleteReceiver, filter);
            Log.d("LibraryFragment", "扫描完成广播接收器注册成功");
        } catch (Exception e) {
            Log.e("LibraryFragment", "注册扫描完成广播接收器失败: " + e.getMessage(), e);
        }
    }

    /**
     * 重新加载歌曲数据并显示布局
     */
    private void reloadMusicDataAndShowLayout() {
        // 在后台线程中重新加载数据
        if (executorService == null) {
            executorService = Executors.newCachedThreadPool();
        }

        executorService.execute(() -> {
            try {
                // 检查数据库中是否有音乐数据
                boolean hasMusicData = LitePal.count("song") > 0;

                // 回到主线程更新UI
                if (mainHandler != null) {
                    mainHandler.post(() -> {
                        if (hasMusicData) {
                            // 有音乐数据，隐藏空布局，加载音乐数据
                            binding.layoutEmpty.setVisibility(View.GONE);
                            loadMusicData();
                        } else {
                            // 没有音乐数据，显示空布局，隐藏其他列表
                            binding.layoutEmpty.setVisibility(View.VISIBLE);
                            hideAllMusicLists();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e("LibraryFragment", "Error reloading music data: " + e.getMessage(), e);
                // 发生错误时，回到主线程显示空布局
                if (mainHandler != null) {
                    mainHandler.post(() -> {
                        binding.layoutEmpty.setVisibility(View.VISIBLE);
                        hideAllMusicLists();
                    });
                }
            }
        });
    }

    /**
     * 隐藏所有音乐列表
     */
    private void hideAllMusicLists() {
        binding.layoutMySongs.setVisibility(View.GONE);
        binding.layoutMyAlbums.setVisibility(View.GONE);
        binding.layoutMyArtists.setVisibility(View.GONE);
    }

    /**
     * 跳转到关于应用页面
     */
    private void navigateToAboutApp() {
        // 使用Navigation组件进行跳转到关于页面
        Navigation.findNavController(requireView()).navigate(R.id.action_library_to_about);
    }

    @Override
    public void onResume() {
        super.onResume();
        showBottomNavigation();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // 清理ExecutorService
        if (executorService != null) {
            executorService.shutdown();
            executorService = null;
        }

        // 清理Handler
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
            mainHandler = null;
        }

        // 释放适配器资源
        if (mySongsAdapter != null) {
            mySongsAdapter.release();
            mySongsAdapter = null;
        }
        if (myAlbumsAdapter != null) {
            myAlbumsAdapter = null;
        }
        if (myArtistsAdapter != null) {
            myArtistsAdapter = null;
        }

        // 注销广播接收器
        try {
            if (getContext() != null) {
                LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(scanCompleteReceiver);
                Log.d("LibraryFragment", "广播接收器注销成功");
            }
        } catch (Exception e) {
            Log.e("LibraryFragment", "注销广播接收器失败: " + e.getMessage(), e);
        }

        // 解绑服务
        if (serviceBound) {
            try {
                getContext().unbindService(serviceConnection);
                serviceBound = false;
            } catch (Exception e) {
                Log.e("LibraryFragment", "解绑服务失败: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public boolean autoHideBottomNavigation() {
        return false;
    }

    /**
     * 刷新音乐列表
     */
    @Override
    protected void onRefreshMusicList() {
        // 重新加载音乐数据
        refreshFragmentAsync();
    }

    /**
     * 在后台线程执行刷新操作
     */
    @Override
    protected void performRefreshInBackground() {
        try {
            // 重新加载音乐数据
            loadMusicData();

            // 打印原始数据到控制台
            System.out.println("LibraryFragment后台刷新完成");

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("LibraryFragment后台刷新失败: " + e.getMessage());
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
                // 重新加载数据并更新各个适配器
                System.out.println("LibraryFragment UI更新完成 - 已刷新所有适配器数据");
                
                // 确保空状态显示正确
                boolean hasSongs = binding.layoutMySongs.getVisibility() == View.VISIBLE;
                boolean hasAlbums = binding.layoutMyAlbums.getVisibility() == View.VISIBLE;
                boolean hasArtists = binding.layoutMyArtists.getVisibility() == View.VISIBLE;
                
                if (!hasSongs && !hasAlbums && !hasArtists) {
                    // 所有数据都为空，显示空状态
                    binding.layoutEmpty.setVisibility(View.VISIBLE);
                } else {
                    // 有数据，隐藏空状态
                    binding.layoutEmpty.setVisibility(View.GONE);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("LibraryFragment UI更新失败: " + e.getMessage());
        }
    }
}