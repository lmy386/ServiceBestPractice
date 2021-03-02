package com.example.servicebestpractice;


//定义一个回调借口，用于对下载过程中的各种状态进行监听和回调
public interface DownloadListener {
    void onProgress(int progress);//用于监听当前的下载进度
    void onSuccess();//用于通知下载成功事件
    void onFailed();//通知下载失败事件
    void onPaused();//通知下载暂停
    void onCanceled();//下载取消事件
}
