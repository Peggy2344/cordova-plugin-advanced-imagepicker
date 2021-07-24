package de.einfachhans.AdvancedImagePicker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.os.AsyncTask;
import android.app.ProgressDialog;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gun0912.tedimagepicker.builder.TedImagePicker;
import com.bumptech.glide.Glide;
import top.zibin.luban.Luban;

public class AdvancedImagePicker extends CordovaPlugin {

    private CallbackContext _callbackContext;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this._callbackContext = callbackContext;
        try {
            if (action.equals("present")) {
                this.presentFullScreen(args);
                return true;
            } else {
                returnError(AdvancedImagePickerErrorCodes.UnsupportedAction);
                return false;
            }
        } catch (JSONException exception) {
            returnError(AdvancedImagePickerErrorCodes.WrongJsonObject);
        } catch (Exception exception) {
            returnError(AdvancedImagePickerErrorCodes.UnknownError, exception.getMessage());
        }

        return true;
    }

    private void presentFullScreen(JSONArray args) throws JSONException {
        JSONObject options = args.getJSONObject(0);
        String mediaType = options.optString("mediaType", "IMAGE");
        boolean showCameraTile = options.optBoolean("showCameraTile", true);
        String scrollIndicatorDateFormat = options.optString("scrollIndicatorDateFormat");
        boolean showTitle = options.optBoolean("showTitle", true);
        String title = options.optString("title");
        boolean zoomIndicator = options.optBoolean("zoomIndicator", true);
        int min = options.optInt("min");
        String defaultMinCountMessage = "You need to select a minimum of " + (min == 1 ? "one picture" : min + " pictures");
        String minCountMessage = options.optString("minCountMessage", defaultMinCountMessage);
        int max = options.optInt("max");
        String defaultMaxCountMessage = "最多只能選擇" + max + "張圖片";
        String maxCountMessage = options.optString("maxCountMessage", defaultMaxCountMessage);
        String buttonText = options.optString("buttonText");
        String buttonBackground = options.optString("buttonBackground");
        boolean asDropdown = options.optBoolean("asDropdown");
        boolean asBase64 = options.optBoolean("asBase64");
        boolean asJpeg = options.optBoolean("asJpeg");

        if (min < 0 || max < 0) {
            this.returnError(AdvancedImagePickerErrorCodes.WrongJsonObject, "Min and Max can not be less then zero.");
            return;
        }

        if (max != 0 && max < min) {
            this.returnError(AdvancedImagePickerErrorCodes.WrongJsonObject, "Max can not be smaller than Min.");
            return;
        }

        TedImagePicker.Builder builder = TedImagePicker.with(this.cordova.getContext())
                .showCameraTile(showCameraTile)
                .showTitle(showTitle)
                .zoomIndicator(zoomIndicator)
                .errorListener(error -> {
                    this.returnError(AdvancedImagePickerErrorCodes.UnknownError, error.getMessage());
                });

        if (!scrollIndicatorDateFormat.equals("")) {
            builder.scrollIndicatorDateFormat(scrollIndicatorDateFormat);
        }
        if (!title.equals("")) {
            builder.title(title);
        }
        if (!buttonText.equals("")) {
            builder.buttonText(buttonText);
        }
        if (!buttonBackground.equals("")) {
            int defaultImage = this.cordova.getActivity().getResources().getIdentifier("bg_btn", "drawable", this.cordova.getActivity().getPackageName());
            builder.buttonBackground(defaultImage);
        }
        if (asDropdown) {
            builder.dropDownAlbum();
        }
        String type = "image";
        if (mediaType.equals("VIDEO")) {
            builder.video();
            type = "video";
        }

        if (max == 1) {
            String finalType = type;
            builder.start(result -> {
                this.handleResult(result, asBase64, finalType, asJpeg);
            });
        } else {
            if (min > 0) {
                builder.min(min, minCountMessage);
            }
            if (max > 0) {
                builder.max(max, maxCountMessage);
            }

            String finalType1 = type;
            builder.startMultiImage(result -> {
                this.handleResult(result, asBase64, finalType1, asJpeg);
            });
        }
    }

    private void handleResult(Uri uri, boolean asBase64, String type, boolean asJpeg) {
        List<Uri> list = new ArrayList<>();
        list.add(uri);
    }

    private void handleResult(List<? extends Uri> uris, boolean asBase64, String type, boolean asJpeg) {
        List<Uri> list = new ArrayList<>();
        for (Uri uri : uris) {
            list.add(uri);
        }
        new UploadImagesTask().execute(list);
    }


    private String encodeVideo(Uri uri) throws IOException {
        final InputStream videoStream = this.cordova.getContext().getContentResolver().openInputStream(uri);
        byte[] bytes;
        byte[] buffer = new byte[8192];
        int bytesRead;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while ((bytesRead = videoStream.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
        bytes = output.toByteArray();
        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }

    private String encodeImage(Uri uri, boolean asJpeg) throws FileNotFoundException {
        final InputStream imageStream = this.cordova.getContext().getContentResolver().openInputStream(uri);
        final Bitmap selectedImage = BitmapFactory.decodeStream(imageStream);
        return encodeImage(selectedImage, asJpeg);
    }
    
    private String encodeImage(Bitmap bm, boolean asJpeg) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (asJpeg) {
            bm.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        } else {
            bm.compress(Bitmap.CompressFormat.PNG, 80, baos);
        }
        byte[] b = baos.toByteArray();
        return Base64.encodeToString(b, Base64.DEFAULT);
    }

    private void returnError(AdvancedImagePickerErrorCodes errorCode) {
        Glide.get(cordova.getContext()).clearDiskCache();
        returnError(errorCode, null);
    }

    private void returnError(AdvancedImagePickerErrorCodes errorCode, String message) {
        if (_callbackContext != null) {
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("code", errorCode.value);
            resultMap.put("message", message == null ? "" : message);
            _callbackContext.error(new JSONObject(resultMap));
            _callbackContext = null;
            Glide.get(cordova.getContext()).clearDiskCache();
        }
    }

    private class UploadImagesTask extends AsyncTask<List<Uri>, Void , JSONArray>{
            private ProgressDialog progressBar;
            //進度條元件
            @Override
            protected void onPreExecute() {
                //執行前 設定可以在這邊設定
                super.onPreExecute();

                progressBar = new ProgressDialog(cordova.getActivity());
                progressBar.setMessage("圖片加載中，請稍候...");
                progressBar.setCancelable(false);
                progressBar.show();
                //初始化進度條並設定樣式及顯示的資訊。
            }

            @Override
            protected JSONArray doInBackground(List<Uri>... files) {
                JSONArray result = new JSONArray();
                Context appContext = cordova.getActivity().getApplicationContext();
                File targetDir = new File(appContext.getCacheDir(), "ImagePicker");
                if(!targetDir.exists()) {
                    targetDir.mkdirs();
                }
                String targetDirPath = targetDir.getAbsolutePath();
                List<Uri> uris = files[0];
                for (Uri uri : uris) {
                    File oldFile = new File(uri.getPath());
                    File newFile = null;
                    Map<String, Object> resultMap = new HashMap<>();
                    try {
                        List<File> file = Luban.with(appContext)
                                                .load(oldFile)
                                                .setTargetDir(targetDirPath)
                                                .get();
                        if(file.size() > 0) {
                            newFile = file.get(0);
                        } else {
                            throw new Exception("Unknown exception when compressing " + oldFile.getAbsolutePath());
                        }
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                    resultMap.put("src", newFile.getAbsolutePath());
                    result.put(new JSONObject(resultMap));
                }
                Glide.get(cordova.getContext()).clearDiskCache();
                return result;
        }

        protected void onPostExecute(JSONArray result) {
            progressBar.dismiss();
            _callbackContext.success(result);
        }
    }
}
