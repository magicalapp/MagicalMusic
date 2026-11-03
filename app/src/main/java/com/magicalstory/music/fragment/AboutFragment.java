package com.magicalstory.music.fragment;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.Navigation;

import com.magicalstory.music.R;
import com.magicalstory.music.base.BaseFragment;
import com.magicalstory.music.databinding.FragmentAboutBinding;
import com.magicalstory.music.utils.app.ToastUtils;

import java.util.Locale;

@UnstableApi
public class AboutFragment extends BaseFragment<FragmentAboutBinding> {

    @Override
    protected FragmentAboutBinding getViewBinding(LayoutInflater inflater, ViewGroup container) {
        return FragmentAboutBinding.inflate(inflater, container, false);
    }

    @Override
    protected boolean usePersistentView() {
        return true;
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.fragment_about;
    }

    @Override
    protected FragmentAboutBinding bindPersistentView(View view) {
        return FragmentAboutBinding.bind(view);
    }

    @Override
    protected void initViewForPersistentView() {
        super.initViewForPersistentView();
        // 设置版本信息
        setVersionInfo();
        // 设置toolbar
        setupToolbar();
    }

    @Override
    protected void initDataForPersistentView() {
        super.initViewForPersistentView();
    }

    @Override
    protected void initListenerForPersistentView() {
        super.initListenerForPersistentView();

        // 开源地址点击事件
        binding.layoutOpenSource.setOnClickListener(v -> {
            openGitee();
        });

        // 公众号点击事件
        binding.layoutWechat.setOnClickListener(v -> {
            openWechat();
        });

        // 联系作者点击事件
        binding.layoutContactAuthor.setOnClickListener(v -> {
            contactAuthor();
        });
    }

    /**
     * 设置toolbar
     */
    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> {
            // 返回上一页
            Navigation.findNavController(requireView()).navigateUp();
        });
    }

    /**
     * 设置版本信息
     */
    private void setVersionInfo() {
        try {
            String versionName = getContext().getPackageManager()
                    .getPackageInfo(getContext().getPackageName(), 0)
                    .versionName;
            binding.tvVersion.setText(String.format(Locale.getDefault(), "版本 %s", versionName));
        } catch (Exception e) {
            binding.tvVersion.setText("版本 1.0.0");
        }
    }

    /**
     * 打开Gitee
     */
    private void openGitee() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://gitee.com/a9292922_admin/MagicalMusic"));
            startActivity(intent);
        } catch (Exception e) {
            ToastUtils.showToast(getContext(), "无法打开链接");
        }
    }

    /**
     * 打开微信并复制公众号名字
     */
    private void openWechat() {
        try {
            // 复制公众号名字到剪贴板
            String wechatAccount = "奇谈君";
            ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("公众号", wechatAccount);
            clipboard.setPrimaryClip(clip);
            
            // 尝试启动微信
            Intent intent = getContext().getPackageManager().getLaunchIntentForPackage("com.tencent.mm");
            if (intent != null) {
                // 微信已安装，启动微信
                startActivity(intent);
                ToastUtils.showToast(getContext(), "已复制公众号：奇谈君\n请在微信中粘贴搜索关注！");
            } else {
                // 微信未安装
                ToastUtils.showToast(getContext(), "已复制公众号：奇谈君\n请先安装微信，然后粘贴搜索关注！");
            }
        } catch (Exception e) {
            ToastUtils.showToast(getContext(), "公众号：奇谈君\n请在微信中搜索关注！");
        }
    }

    /**
     * 联系作者
     */
    private void contactAuthor() {
        try {
            // 创建邮件Intent
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:qitanjun@qq.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "MagicalMusic 用户反馈");
            intent.putExtra(Intent.EXTRA_TEXT, "您好，我是MagicalMusic的用户，我想反馈以下问题：\n\n");
            
            // 检查是否有邮件应用
            if (intent.resolveActivity(getContext().getPackageManager()) != null) {
                startActivity(intent);
            } else {
                // 没有邮件应用，复制邮箱地址到剪贴板
                ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("邮箱", "qitanjun@qq.com");
                clipboard.setPrimaryClip(clip);
                ToastUtils.showToast(getContext(), "已复制邮箱地址：qitanjun@qq.com\n请手动发送邮件");
            }
        } catch (Exception e) {
            ToastUtils.showToast(getContext(), "联系作者失败，请手动发送邮件到：qitanjun@qq.com");
        }
    }

    @Override
    public boolean autoHideBottomNavigation() {
        return false;
    }
} 