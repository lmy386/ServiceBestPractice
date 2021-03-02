package com.example.servicebestpractice;

import android.os.AsyncTask;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


//泛型参数String，表示在执行任务的时候需要传入一个字符串参数给后台任务
//Integer表示使用整型数据来作为进度展示单位
//第三个泛型参数指定为Integer，表示使用整型数据来反馈执行结果
public class DownloadTask extends AsyncTask <String,Integer,Integer>{

    //表示下载的状态
    private static final int TYPE_SUCCESS = 0;
    private static final int TYPE_FAILED = 1;
    private static final int TYPE_PAUSED = 2;//暂停下载
    private static final int TYPE_CANCELED = 3;//取消下载

    private  DownloadListener listener;
    private boolean isCanceled = false;
    private boolean isPaused = false;
    private int lastProgress;

    public DownloadTask(DownloadListener listener){
        this.listener = listener;
    }

    //该部分在后台执行具体的下在逻辑
    @Override
    protected Integer doInBackground(String... params) {
        InputStream is = null;
        RandomAccessFile savedFile = null;
        File file = null;
        try{
            long downloadedLength = 0; //用于记录已下载的文件长度
            String downloadUrl = params[0]; //从参数中获取下载的URL地址
            String fileName = downloadUrl.substring(downloadUrl.lastIndexOf("/"));//从URL中解析下载的文件名
            //指定将文件下载到Environment.DIRECTORY_DOWNLOADS目录下，也就是SD卡的downloads目录下
            String directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
            file = new File(directory + fileName);
            //判断目录中是否已经存在要下载的文件
            if (file.exists()){
                //读取已下载的字节数，可以在后面使用断点续传的功能
                downloadedLength = file.length();
            }
            //获取待下载文件的总长度
            long contentLength = getContentLength(downloadUrl);
            if (contentLength == 0){
                //文件长度为0 ，说明文件有问题，直接返回失败。
                return TYPE_FAILED;
            }else if (contentLength == downloadedLength){
                //已下载字节和文件总字节相等，说明已经下载完成了
                return TYPE_SUCCESS;
            }
            OkHttpClient client = new OkHttpClient();

            //添加了一个hander，用于告诉服务器想要从那个字节开始下载，下载过的部分就不用从新下载了
            Request request = new Request.Builder()
                    .addHeader("RANGE","bytes=" + downloadedLength + "-")
                    .url(downloadUrl)
                    .build();
            Response response = client.newCall(request).execute();
            if (response != null){
                is = response.body().byteStream();
                savedFile = new RandomAccessFile(file,"rw");
                savedFile.seek(downloadedLength);
                byte[] b = new byte[1024];
                int total = 0;
                int len;
                while ((len = is.read(b)) != -1){
                    if (isCanceled){
                        return TYPE_CANCELED;
                    }
                    else if (isPaused){
                        return TYPE_PAUSED;
                    }
                    else {
                        total+=len;
                        savedFile.write(b,0,len);
                        //计算已下载的百分比
                        int progress = (int) ((total + downloadedLength)*100 / contentLength);
                        publishProgress(progress);
                    }
                }
                response.body().close();
                return TYPE_SUCCESS;
            }
        }catch(Exception e){
            e.printStackTrace();
        }finally {
            try{
                if(is != null) {
                    is.close();
                }
                if (savedFile != null){
                    savedFile.close();
                }
                if (isCancelled() && file != null){
                    file.delete();
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        return null;
    }

    private long getContentLength(String dowloadUrl) throws IOException {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(dowloadUrl).build();
        Response response = client.newCall(request).execute();
        if (response != null && response.isSuccessful()){
            long contentLength = response.body().contentLength();
            response.body().close();
            return contentLength;
        }
        return TYPE_FAILED;
    }

    //用于在界面上更新当前的下载进度
    @Override
    protected void onProgressUpdate(Integer... values) {
        int progress = values[0];
        if(progress > lastProgress){
            listener.onProgress(progress);
            lastProgress = progress;
        }
    }

    //用于通知最终的下载结果
    @Override
    protected void onPostExecute(Integer status) {
        switch (status){
            case TYPE_SUCCESS:
                listener.onSuccess();
                break;
            case TYPE_FAILED:
                listener.onFailed();
                break;
            case TYPE_PAUSED:
                listener.onPaused();
                break;
            case TYPE_CANCELED:
                listener.onCanceled();
            default:
                break;
        }
    }

    public void pauseDownload(){
        isPaused = true;
    }

    public void cancelDownload(){
        isCanceled = true;
    }
}
