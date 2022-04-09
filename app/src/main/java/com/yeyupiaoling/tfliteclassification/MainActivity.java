package com.yeyupiaoling.tfliteclassification;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MainActivity extends AppCompatActivity {

    private static final int LONG_PRESS_DURATION = 500;
    private static final int SAMPLE_COLLECTION_DELAY = 300;
    private static final String TAG = "connection server";

    private String IMG_PATH;

//    private TFLiteClassificationUtil tfLiteClassificationUtil;
    private ImageView imageView;
    private TextView textView;
    private ArrayList<String> classNames;
    private final ConcurrentLinkedQueue<String> addSampleRequests = new ConcurrentLinkedQueue<>();
    private boolean isCollectingSamples = false;
    private long sampleCollectionButtonPressedTime;
    private final Handler sampleCollectionHandler = new Handler(Looper.getMainLooper());

    private Map<String, Integer> imageMap = new HashMap<>();
    private Utils imageUtils = new Utils();
    private GlobalApp globalApp ;
    private static final String REGISTER_URL = "http://106.15.39.182:8080/device/register";
    private static final String CONNECT_URL = "http://106.15.39.182:8080/device/connect";
    private static final String DOWNLOAD_URL = "http://106.15.39.182:8080/network/download";
    private static final String DEVICE_NUMBER = "1233211234567";
    private static final String DEVICE_NAME = "giao";
    private String token;
    private String network_name = "MobileNetV2";
    private String network_version = "v1.0";

    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 1) {
                String res = (String) msg.obj;
                textView.setText(res);
                Toast.makeText(getApplicationContext(), res, Toast.LENGTH_SHORT).show();
            } else if (msg.what == 2) {
                String res = (String) msg.obj;
                textView.setText(res);
            }
        }
    };

    public final View.OnClickListener onAddSampleClickListener =
            view -> {
                if (IMG_PATH != null) {
                    FileInputStream fis = null;
                    try {
                        fis = new FileInputStream(IMG_PATH);
                    } catch (FileNotFoundException e) {
                        String show_text = "图片路径出错了！";
                        textView.setText(show_text);
                    }
                    Bitmap bitmap = BitmapFactory.decodeStream(fis);
                    float[][][] rgbImage = imageUtils.prepareCameraImage(bitmap, 0);
                    String sampleClass = getClassNameFromResourceId(view.getId());

                    try {
                        globalApp.getTlModel().addSample(rgbImage, sampleClass).get();
                    } catch (ExecutionException e) {
                        String show_text = "添加模型报错！";
                        textView.setText(show_text);
                        throw new RuntimeException("Failed to add sample to model", e.getCause());

                    } catch (InterruptedException e) {
                        // no-op
                    }

                    if (!imageMap.containsKey(sampleClass)){
                        imageMap.put(sampleClass, 1);
                    } else {
                        int val = imageMap.get(sampleClass);
                        imageMap.put(sampleClass, val+1);
                    }

                    String show_text = printImages();
                    textView.setText(show_text);
                } else {
                    String show_text = "请先选择照片";
                    textView.setText(show_text);
//                    throw new RuntimeException("加载图片报错！");
                }

            };

    private String printImages() {
        StringBuilder result = new StringBuilder("已记录");
        for (Map.Entry<String, Integer> entry : imageMap.entrySet()) {

            result.append(" 类别").append(entry.getKey()).append("有").append(entry.getValue()).append("涨 \n");
        }
        return result.toString();
    }

    public final View.OnClickListener trainClickListener =
            view -> {
                Message msg = new Message();
                msg.what = 2;
                globalApp.getTlModel().enableTraining((epoch, loss) -> {
                                msg.obj = epoch +","+loss;
                                handler.sendMessage(msg);
                        });
            };

    public final View.OnClickListener pauseClickListener =
            view -> {
                globalApp.getTlModel().disableTraining();
                globalApp.getTlModel().saveModel(getCacheDir().getAbsolutePath());
            };
    // 连接服务器，注册
    public final View.OnClickListener connectionClickListener =
            view -> {
                    new Thread(){
                    @Override
                    public void run() {
                        try {
                            doRegister(REGISTER_URL);
                            Message msg = new Message();
                            msg.what = 1;  //消息发送的标志
                            msg.obj = "服务器连接成功"; //消息发送的内容如：  Object String 类 int
                            handler.sendMessage(msg);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                }.start();
            };
//    下载模型
    public final View.OnClickListener downClickListener =
            view -> {

                new Thread(){
                    @Override
                    public void run() {
                        try {
                            Message msg = new Message();
                            msg.what = 2;  //消息发送的标志
                            msg.obj = "正在下载，请稍后..."; //消息发送的内容如：  Object String 类 int
                            handler.sendMessage(msg);
                            download(DOWNLOAD_URL);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }.start();
            };

    public final View.OnTouchListener onAddSampleTouchListener =
            (view, motionEvent) -> {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isCollectingSamples = true;
                        sampleCollectionButtonPressedTime = SystemClock.uptimeMillis();
                        sampleCollectionHandler.post(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        long timePressed =
                                                SystemClock.uptimeMillis() - sampleCollectionButtonPressedTime;
                                        view.findViewById(view.getId()).performClick();
                                        if (timePressed < LONG_PRESS_DURATION) {
                                            sampleCollectionHandler.postDelayed(this, LONG_PRESS_DURATION);
                                        } else if (isCollectingSamples) {
                                            String className = getClassNameFromResourceId(view.getId());
//                                            viewModel.setNumCollectedSamples(
//                                                    viewModel.getNumSamples().getValue().get(className) + 1);
                                            sampleCollectionHandler.postDelayed(this, SAMPLE_COLLECTION_DELAY);
//                                            viewModel.setSampleCollectionLongPressed(true);
                                        }
                                    }
                                });
                        break;
                    case MotionEvent.ACTION_UP:
                        sampleCollectionHandler.removeCallbacksAndMessages(null);
                        isCollectingSamples = false;
//                        viewModel.setSampleCollectionLongPressed(false);
                        break;
                    default:
                        break;
                }
                return true;
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        globalApp = ((GlobalApp) getApplicationContext());
        try {
            globalApp.setTlModel(new TransferLearningModelWrapper(MainActivity.this));
        } catch (Exception e) {
            throw new RuntimeException("加载模型报错！",e);
        }
//        globalApp.getTlModel().saveModel(getCacheDir().getAbsolutePath());
        setContentView(R.layout.activity_main);
        if (!hasPermission()) {
            requestPermission();
        }

        for (int buttonId : new int[] {
                R.id.class_btn_1, R.id.class_btn_2, R.id.class_btn_3, R.id.class_btn_4}) {
            findViewById(buttonId).setOnClickListener(onAddSampleClickListener);
//            findViewById(buttonId).setOnTouchListener(onAddSampleTouchListener);
        }

        // 获取控件
        Button connBtn = findViewById(R.id.conn_btn);
        Button downBtn = findViewById(R.id.download_model);
        Button upBtn = findViewById(R.id.upload_model);
        connBtn.setOnClickListener(connectionClickListener);
        downBtn.setOnClickListener(downClickListener);

        Button selectImgBtn = findViewById(R.id.select_img_btn);
        Button openCamera = findViewById(R.id.open_camera);
        Button trainBtn = findViewById(R.id.train_btn);
        Button pauseBtn = findViewById(R.id.pause_btn);
        trainBtn.setOnClickListener(trainClickListener);
        pauseBtn.setOnClickListener(pauseClickListener);


        imageView = findViewById(R.id.image_view);
        textView = findViewById(R.id.result_text);
        selectImgBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 打开相册
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                startActivityForResult(intent, 1); //返回数据
            }
        });
        openCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 打开实时拍摄识别页面
                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 1) {
                if (data == null) {
                    Log.w("onActivityResult", "user photo data is null");
                    return;
                }
                Uri image_uri = data.getData();
                IMG_PATH = getPathFromURI(MainActivity.this, image_uri);
                try {
                    // 预测图像
                    FileInputStream fis = new FileInputStream(IMG_PATH);
                    imageView.setImageBitmap(BitmapFactory.decodeStream(fis));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // 根据相册的Uri获取图片的路径
    public static String getPathFromURI(Context context, Uri uri) {
        String result;
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        if (cursor == null) {
            result = uri.getPath();
        } else {
            cursor.moveToFirst();
            int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            result = cursor.getString(idx);
            cursor.close();
        }
        return result;
    }

    // check had permission
    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    // request permission
    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    private String getClassNameFromResourceId(int id) {
        String className;
        if (id == R.id.class_btn_1) {
            className = "1";
        } else if (id == R.id.class_btn_2) {
            className = "2";
        } else if (id == R.id.class_btn_3) {
            className = "3";
        } else if (id == R.id.class_btn_4) {
            className = "4";
        } else {
            throw new RuntimeException("Listener called for unexpected view");
        }
        return className;
    }

//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//        globalApp.getTlModel().close();
//        globalApp.setTlModel(null);
//    }

    public void doRegister(String url) throws JSONException {
        OkHttpClient okHttpClient = new OkHttpClient();
        // 先封装一个 JSON 对象
        JSONObject param = new JSONObject();
        param.put("device_number", DEVICE_NUMBER);
        param.put("device_name", DEVICE_NAME);
        MediaType JSON = MediaType.parse("application/json;charset=utf-8");
        String params =  param.toString();

        RequestBody requestBody = RequestBody.create(JSON, params);
        //创建一个请求对象
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();
        //发送请求获取响应
        try {
            Response response=okHttpClient.newCall(request).execute();
            //判断请求是否成功
            if(response.isSuccessful()){
                //打印服务端返回结果
               String result = response.body().string();
               JSONObject jsonObject = new JSONObject(result);
               int code = jsonObject.getInt("code");
                if (code == 1) {
                    JSONObject data = jsonObject.getJSONObject("data");
                    token = data.getString("token");
                } else if (code == 201) { //已注册，则通过连接去返回token
                    doConnect(CONNECT_URL);
                } else {
                    Message msg = new Message();
                    msg.what = 1;  //消息发送的标志
                    msg.obj = "设备注册失败，请检查网络设置。"; //消息发送的内容如：  Object String 类 int
                    handler.sendMessage(msg);
                }


            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void doConnect(String url) throws IOException, JSONException {
        OkHttpClient okHttpClient = new OkHttpClient();
        // 先封装一个 JSON 对象
        JSONObject param = new JSONObject();
        param.put("device_number", DEVICE_NUMBER);
        MediaType JSON = MediaType.parse("application/json;charset=utf-8");
        String params =  param.toString();
        RequestBody requestBody = RequestBody.create(JSON, params);
        //创建一个请求对象
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();
        //发送请求获取响应
        try {
            Response response=okHttpClient.newCall(request).execute();
            //判断请求是否成功
            if(response.isSuccessful()){
                //打印服务端返回结果
                String result = response.body().string();
                System.out.println(result);
                JSONObject jsonObject = new JSONObject(result);
                int code = jsonObject.getInt("code");
                if (code == 1) {
                    JSONObject data = jsonObject.getJSONObject("data");
                    token = data.getString("token");
                } else {
                    Message msg = new Message();
                    msg.what = 1;  //消息发送的标志
                    msg.obj = "设备连接失败，请检查网络设置。"; //消息发送的内容如：  Object String 类 int
                    handler.sendMessage(msg);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ResponseBody getResponeBody(String url) {
        if (token == null) {
            Message msg = new Message();
            msg.what = 1;  //消息发送的标志
            msg.obj = "token为空，需要先连接服务器。"; //消息发送的内容如：  Object String 类 int
            handler.sendMessage(msg);
            return null;
        }
        url = url + "?" +
            "network_name=" + network_name +
            "&network_version=" + network_version +
            "&token=" + token;
        System.out.println(url);
        ResponseBody result =null;
        OkHttpClient okHttpClient = new OkHttpClient()
                .newBuilder()
                .connectTimeout(60000, TimeUnit.MILLISECONDS)
                .readTimeout(60000, TimeUnit.MILLISECONDS)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .build();
        Call call = okHttpClient.newCall(request);
        try {
            Response response = call.execute();
            result = response.body();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    //将InputStream写入到文件，成功返回true 失败返回false
    public boolean WriteFile4InputStream(String FilePath, InputStream inputStream)
    {
        //默认为flase 即失败
        boolean result = false;
        try {
            OutputStream os = new FileOutputStream(FilePath);
            byte[] arr = new byte[1024];
            int len = 0;
            while ( ( len=inputStream.read(arr) ) != -1 ){
                os.write(arr, 0, len);
            }
            os.close();
            inputStream.close();
            result = true;
        }catch (IOException e)
        {
            e.printStackTrace();
            result = false;
        }
        return result;
    }

    private void download(String downloadUrl) throws IOException {
        ResponseBody response = getResponeBody(downloadUrl);
        if (response == null ) {
            return;
        }
        InputStream inputStream = response.byteStream();
        String filePath = getCacheDir().getAbsolutePath() + "/" + network_name;

        boolean result = WriteFile4InputStream(filePath, inputStream);
        Message msg = new Message();
        msg.what = 1;  //消息发送的标志

        if (result) {
            msg.obj = "模型下载成功";
        }else {
            msg.obj = "模型下载失败";
        }
        handler.sendMessage(msg);
    }

}