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
import java.net.FileNameMap;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
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

    private Map<String, List<float[][][]>> imageMap = new HashMap<>();
    private Utils imageUtils = new Utils();
    private GlobalApp globalApp ;
    private String network_name = "MobileNetV2";
    private String network_file_name = network_name+".tflite";
    private String network_version = "v1.0";
    private static final String REGISTER_URL = "http://106.15.39.182:8080/device/register";
    private static final String CONNECT_URL = "http://106.15.39.182:8080/device/connect";
    private static final String DOWNLOAD_URL = "http://106.15.39.182:8080/network/download";
    private static final String UPLOAD_URL = "http://106.15.39.182:8080/network/upload";
    private static final String DEVICE_NUMBER = "1233211234567";
    private static final String DEVICE_NAME = "giao";
    private String token;


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
                        String show_text = "????????????????????????";
                        textView.setText(show_text);
                    }
                    Bitmap bitmap = BitmapFactory.decodeStream(fis);
                    float[][][] rgbImage = imageUtils.prepareCameraImage(bitmap, 0);
                    String sampleClass = getClassNameFromResourceId(view.getId());

                    //????????????????????????????????????
                    if (!imageMap.containsKey(sampleClass)){
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ImageView imageView = null;
                                if (sampleClass.equals("1")) {
                                    imageView = findViewById(R.id.class_img_1);
                                } else if (sampleClass.equals("2")) {
                                    imageView = findViewById(R.id.class_img_2);
                                } else if (sampleClass.equals("3")) {
                                    imageView = findViewById(R.id.class_img_3);
                                } else {
                                    imageView = findViewById(R.id.class_img_4);
                                }
                                if (imageView!=null) {
                                    updateClassBtnImage(imageView, bitmap);
                                }else{
                                    System.out.println("AEL einai null toimg");
                                }
                            }
                        });
                    }



                    if (!imageMap.containsKey(sampleClass)){
                        List<float[][][]> imgList = new ArrayList<>();
                        imgList.add(rgbImage);
                        imageMap.put(sampleClass, imgList);
                    } else {
                        imageMap.get(sampleClass).add(rgbImage);
                    }

                    String show_text = printImages();
                    textView.setText(show_text);
                } else {
                    String show_text = "??????????????????";
                    textView.setText(show_text);
//                    throw new RuntimeException("?????????????????????");
                }

            };

    private String printImages() {
        StringBuilder result = new StringBuilder("?????????");
        for (Map.Entry<String, List<float[][][]>> entry : imageMap.entrySet()) {

            result.append(" ??????").append(entry.getKey()).append("???").append(entry.getValue().size()).append("??? \n");
        }
        return result.toString();
    }

    public final View.OnClickListener trainClickListener =
            view -> {
                globalApp = ((GlobalApp) getApplicationContext());
                try {
                    globalApp.setTlModel(new TransferLearningModelWrapper(MainActivity.this));
                } catch (Exception e) {
                    throw new RuntimeException("?????????????????????",e);
                }
                for (Map.Entry<String, List<float[][][]>> entry:
                        imageMap.entrySet()) {
                    for (float[][][] rgbImage :
                            entry.getValue()) {
                        String sampleClass = entry.getKey();
                        try {
                            globalApp.getTlModel().addSample(rgbImage, sampleClass).get();
                        } catch (ExecutionException e) {
                            String show_text = "?????????????????????";
                            textView.setText(show_text);
                            throw new RuntimeException("Failed to add sample to model", e.getCause());

                        } catch (InterruptedException e) {
                            // no-op
                        }
                    }
                }

                globalApp.getTlModel().enableTraining((epoch, loss) -> {
                                Message msg = new Message();
                                msg.what = 2;
                                msg.obj = (epoch +","+loss).toString();
                                handler.sendMessage(msg);
                        });
            };

    public final View.OnClickListener pauseClickListener =
            view -> {
                globalApp.getTlModel().disableTraining();
                globalApp.getTlModel().saveModel(getCacheDir().getAbsolutePath());
            };
    // ????????????????????????
    public final View.OnClickListener connectionClickListener =
            view -> {
                    new Thread(){
                    @Override
                    public void run() {
                        try {
                            doRegister(REGISTER_URL);

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                }.start();
            };
//    ????????????
    public final View.OnClickListener downClickListener =
            view -> {

                new Thread(){
                    @Override
                    public void run() {
                        try {
                            Message msg = new Message();
                            msg.what = 2;  //?????????????????????
                            msg.obj = "????????????????????????..."; //???????????????????????????  Object String ??? int
                            handler.sendMessage(msg);
                            download(DOWNLOAD_URL);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }.start();
            };

    //????????????
    public final View.OnClickListener upClickListener =
            view -> {

                new Thread(){
                    @Override
                    public void run() {
                        try {
                            Message msg = new Message();
                            msg.what = 2;  //?????????????????????
                            msg.obj = "????????????????????????..."; //???????????????????????????  Object String ??? int
                            handler.sendMessage(msg);
                            upload();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }.start();
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        globalApp = ((GlobalApp) getApplicationContext());
//        try {
//            globalApp.setTlModel(new TransferLearningModelWrapper(MainActivity.this));
//        } catch (Exception e) {
//            throw new RuntimeException("?????????????????????",e);
//        }
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

        // ????????????
        Button connBtn = findViewById(R.id.conn_btn);
        Button downBtn = findViewById(R.id.download_model);
        Button upBtn = findViewById(R.id.upload_model);
        connBtn.setOnClickListener(connectionClickListener);
        downBtn.setOnClickListener(downClickListener);
        upBtn.setOnClickListener(upClickListener);

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
                // ????????????
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                startActivityForResult(intent, 1); //????????????
            }
        });
        openCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // ??????????????????????????????
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
                    // ????????????
                    FileInputStream fis = new FileInputStream(IMG_PATH);
                    imageView.setImageBitmap(BitmapFactory.decodeStream(fis));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // ???????????????Uri?????????????????????
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
        // ??????????????? JSON ??????
        JSONObject param = new JSONObject();
        param.put("device_number", DEVICE_NUMBER);
        param.put("device_name", DEVICE_NAME);
        MediaType JSON = MediaType.parse("application/json;charset=utf-8");
        String params =  param.toString();

        RequestBody requestBody = RequestBody.create(JSON, params);
        //????????????????????????
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();
        //????????????????????????
        try {
            Response response=okHttpClient.newCall(request).execute();
            //????????????????????????
            if(response.isSuccessful()){
                //???????????????????????????
               String result = response.body().string();
               JSONObject jsonObject = new JSONObject(result);
               int code = jsonObject.getInt("code");
                if (code == 1) {
                    JSONObject data = jsonObject.getJSONObject("data");
                    token = data.getString("token");
                    Message msg = new Message();
                    msg.what = 1;  //?????????????????????
                    msg.obj = "?????????????????????"; //???????????????????????????  Object String ??? int
                    handler.sendMessage(msg);
                } else if (code == 201) { //????????????????????????????????????token
                    doConnect(CONNECT_URL);
                } else {
                    Message msg = new Message();
                    msg.what = 1;  //?????????????????????
                    msg.obj = "?????????????????????????????????????????????"; //???????????????????????????  Object String ??? int
                    handler.sendMessage(msg);
                }
            } else {
                Message msg = new Message();
                msg.what = 1;  //?????????????????????
                msg.obj = "?????????????????????????????????????????????"; //???????????????????????????  Object String ??? int
                handler.sendMessage(msg);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void doConnect(String url) throws IOException, JSONException {
        OkHttpClient okHttpClient = new OkHttpClient();
        // ??????????????? JSON ??????
        JSONObject param = new JSONObject();
        param.put("device_number", DEVICE_NUMBER);
        MediaType JSON = MediaType.parse("application/json;charset=utf-8");
        String params =  param.toString();
        RequestBody requestBody = RequestBody.create(JSON, params);
        //????????????????????????
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();
        //????????????????????????
        try {
            Response response=okHttpClient.newCall(request).execute();
            //????????????????????????
            if(response.isSuccessful()){
                //???????????????????????????
                String result = response.body().string();
                System.out.println(result);
                JSONObject jsonObject = new JSONObject(result);
                int code = jsonObject.getInt("code");
                if (code == 1) {
                    JSONObject data = jsonObject.getJSONObject("data");
                    token = data.getString("token");
                    Message msg = new Message();
                    msg.what = 1;  //?????????????????????
                    msg.obj = "?????????????????????"; //???????????????????????????  Object String ??? int
                    handler.sendMessage(msg);
                } else {
                    Message msg = new Message();
                    msg.what = 1;  //?????????????????????
                    msg.obj = "?????????????????????????????????????????????"; //???????????????????????????  Object String ??? int
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
            msg.what = 1;  //?????????????????????
            msg.obj = "token????????????????????????????????????"; //???????????????????????????  Object String ??? int
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

    //???InputStream??????????????????????????????true ????????????false
    public boolean WriteFile4InputStream(String FilePath, InputStream inputStream)
    {
        //?????????flase ?????????
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
        String filePath = getCacheDir().getAbsolutePath() + "/" + network_file_name;

        boolean result = WriteFile4InputStream(filePath, inputStream);
        Message msg = new Message();
        msg.what = 1;  //?????????????????????

        if (result) {
            msg.obj = "??????????????????";
        }else {
            msg.obj = "??????????????????";
        }
        handler.sendMessage(msg);
    }

    private void upload() {
        String path  = getCacheDir().getAbsolutePath() + File.separator + network_file_name;
        String url = UPLOAD_URL + "?network_name=" + network_name;
        System.out.println(url);
        upLoadingFile(path,url);
    }

    /**
     * ????????????(????????????, ??????????????????)
     */
    public void upLoadingFile(String filePath,String url) {

        Message msg = new Message();
        msg.what = 1;
        // 1.RequestBody
        //??????MultipartBody.Builder??????????????????????????????
        MultipartBody.Builder multipartBodyBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM);
        File file = new File(filePath); //????????????
        if (!file.exists()){
            msg.obj = "?????????????????????";
            handler.sendMessage(msg);
            return;
        }

        // ?????????????????????????????????????????????
        String fileType = getMimeType(file.getName());
        //???Builder?????????????????????
        multipartBodyBuilder.addFormDataPart(
                "network_file",  //???????????????
                file.getName(), //?????????????????????????????????????????????
                RequestBody.create(MediaType.parse(fileType), file) //??????RequestBody???????????????????????????
        );

        // ????????????????????????, ?????????????????????????????????, ????????????????????????????????????????????????
        RequestBody requestBody = multipartBodyBuilder.build();//??????Builder????????????

        // 2.requestBuilder
        Request requestBuilderRequest = new Request.Builder()
                .url(url)
                .header("Authorization", token)
                .post(requestBody)
                .build();

        // 3.OkHttpClient
        OkHttpClient mOkHttpClient = new OkHttpClient.Builder()
                .connectTimeout(60000, TimeUnit.SECONDS)
                .readTimeout(60000, TimeUnit.SECONDS)
                .writeTimeout(60000, TimeUnit.SECONDS)
                .build();

        Call call = mOkHttpClient.newCall(requestBuilderRequest);


        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                //??????????????????: ????????????(????????????)
                msg.obj = "??????????????????,?????????";
                handler.sendMessage(msg);
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String res = response.body().string();
                System.out.println(res);
                try {
                    JSONObject resJson = new JSONObject(res);
                    int code = resJson.getInt("code");
                    if (code == 1) {
                        msg.obj = "??????????????????";
                    } else {
                        msg.obj = "??????????????????";
                    }
                    handler.sendMessage(msg);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

    }

    /**
     * ????????????MimeType
     *
     * @param fileName
     * @return
     */
    private static String getMimeType(String fileName) {
        FileNameMap filenameMap = URLConnection.getFileNameMap();
        String contentType = filenameMap.getContentTypeFor(fileName);
        if (contentType == null) {
            contentType = "application/octet-stream"; //* exe,????????????????????????
        }
        return contentType;
    }

    public void updateClassBtnImage(ImageView imageView,Bitmap bitmap){
        imageView.setImageBitmap(bitmap);
    }




}