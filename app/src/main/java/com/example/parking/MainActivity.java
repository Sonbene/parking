package com.example.parking;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.media.Image;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.sql.Timestamp;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import java.sql.ResultSet;  // Add this import for ResultSet
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private ExecutorService nfcExecutor;
    private TextView txtBienSo;
    private TextView Time_in;
    private TextView Time_out;
    private TextView txtThanhTien;
    private TextRecognizer recognizer;
    private PreviewView previewView;
    private NfcAdapter nfcAdapter;
    private TextView txtID;
    private static final String URL = "jdbc:mysql://pvl.vn:3306/admin_db";
    private static final String USER = "raspberry";
    private static final String PASSWORD = "admin6789@";
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Button btnVao, btnRa;
    private byte[] imageBytes;
    private ImageView capturedImageView;
    private ImageView capturedImageView_Out;
    private TextToSpeech tts;

    @SuppressLint({"MissingInflatedId", "CutPasteId", "WrongViewCast"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {  // API 30 trở lên
            Window window = getWindow();
            WindowInsetsController insetsController = window.getInsetsController();
            if (insetsController != null) {
                insetsController.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                );
            }
            window.setStatusBarColor(Color.WHITE);
        } else {  // API 29 trở xuống
            Window window = getWindow();
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            window.setStatusBarColor(Color.WHITE);
        }

        // Giao diện
        txtBienSo = findViewById(R.id.txtBienSo);
        txtThanhTien = findViewById(R.id.txtThanhTien);
        Time_in = findViewById(R.id.time_in);
        Time_out = findViewById(R.id.time_out);
        capturedImageView = findViewById(R.id.capturedImageView);
        capturedImageView_Out = findViewById(R.id.capturedImageView_Out);
        previewView = findViewById(R.id.previewView);
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        cameraExecutor = Executors.newSingleThreadExecutor();
        txtID = findViewById(R.id.txtID);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            startCamera();
        }

        //chụp ảnh
        previewView.setOnClickListener(v -> {
            if (btnVao.isSelected()) {
                captureImage(capturedImageView);
            } else if (btnRa.isSelected()) {
                captureImage(capturedImageView_Out);
            }
        });
        txtBienSo.setOnClickListener(v -> {
            String idValue = txtID.getText().toString().trim();
            if (!idValue.isEmpty()) {
                txtBienSo.setText(idValue);
            }
        });


        // Return
        Button captureButton = findViewById(R.id.capture_button);
        captureButton.setOnClickListener(v -> {
            step(); // Gọi hàm step() khi bấm nút
        });

        //NFC
        nfcExecutor = Executors.newSingleThreadExecutor();
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            Toast.makeText(this, "Thiết bị không hỗ trợ NFC", Toast.LENGTH_LONG).show();

            // 1) Ẩn hẳn khu vực scan NFC (giả sử bạn gom vào một layout)


            // 2) Cho phép nhập tay ID nếu cần:
            txtID.setEnabled(true);
            txtID.setHint("Nhập ID bằng tay");

            // 3) Kiểm soát để không gọi enableForegroundDispatch(...) khi nfcAdapter == null
            //    (tức bạn sẽ check nfcAdapter != null trước khi bật/tắt NFC ở onResume/onPause)
        } else {
            // bình thường: giữ nguyên enableForegroundDispatch, handleNfcReading(),…
        }


        //price
        setupSpinner();

        //CSDL
        btnVao = findViewById(R.id.btnVao);
        btnRa = findViewById(R.id.btnRa);
        setupMode_InOut();
        addTextWatcher(txtID, txtBienSo);

        //speak
        tts = new TextToSpeech(this, status -> {
            for (Locale locale : Locale.getAvailableLocales()) {
                int result = tts.isLanguageAvailable(locale);
                if (result == TextToSpeech.LANG_AVAILABLE) {
                    Log.d("TTS", "Hỗ trợ: " + locale.toString());
                }
            }
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.forLanguageTag("vi")); // Thiết lập tiếng Việt

                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Ngôn ngữ không được hỗ trợ");
                    Toast.makeText(MainActivity.this, "Ngôn ngữ TTS không được hỗ trợ", Toast.LENGTH_SHORT).show();
                } else {
                    Log.d(TAG, "TTS Tiếng Việt đã khởi tạo thành công");
                    //tts.speak("Chào mừng bạn", TextToSpeech.QUEUE_FLUSH, null, "tts_init");
                }
            } else {
                Log.e(TAG, "Khởi tạo TTS thất bại");
                Toast.makeText(MainActivity.this, "Khởi tạo TTS thất bại", Toast.LENGTH_SHORT).show();
            }
        });
        // Đảm bảo âm lượng không bị tắt
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null && audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
            Toast.makeText(this, "Vui lòng tăng âm lượng để nghe TTS", Toast.LENGTH_LONG).show();
        }
    }

    //region xử lí nhận diện biển số
    private void stopCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                // Lấy cameraProvider từ future
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();  // Dòng này có thể ném ra ExecutionException hoặc InterruptedException

                runOnUiThread(() -> {
                    previewView.setVisibility(View.INVISIBLE);
                    previewView.postDelayed(() -> previewView.setVisibility(View.VISIBLE), 100);
                });
                // Hủy bỏ tất cả các kết nối trước đó
                cameraProvider.unbindAll();
                imageCapture = null;
            } catch (ExecutionException | InterruptedException e) {
                Log.e("Camera", "Error stopping camera", e);
                Thread.currentThread().interrupt();  // Khôi phục trạng thái gián đoạn của luồng
            }
        }, ContextCompat.getMainExecutor(this));
    }
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                // Lấy cameraProvider từ future
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Hủy các kết nối trước đó
                cameraProvider.unbindAll();

                // Khởi tạo Preview và ImageCapture
                Preview preview = new Preview.Builder().build();
                imageCapture = new ImageCapture.Builder().build();

                // Kiểm tra nếu previewView không null mới thiết lập SurfaceProvider
                if (previewView != null) {
                    preview.setSurfaceProvider(previewView.getSurfaceProvider());
                } else {
                    Log.e("Camera", "previewView is null, cannot start camera.");
                    return;
                }

                // Chọn camera sau
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                // Cung cấp preview cho camera
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
            } catch (ExecutionException | InterruptedException e) {
                Log.e("Camera", "Error starting camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void captureImage(ImageView targetImageView) {
        if (imageCapture == null) {
            Log.e("Camera", "imageCapture is null, camera might not be started.");
            return;
        }
        imageCapture.takePicture(ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        @SuppressLint("UnsafeOptInUsageError")
                        Image mediaImage = image.getImage();
                        if (mediaImage == null) {
                            image.close();
                            return;
                        }

                        Bitmap bitmap = imageProxyToBitmap(image);
                        Matrix matrix = new Matrix();
                        matrix.postRotate(image.getImageInfo().getRotationDegrees());
                        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                        Bitmap scaledBitmap = scaleBitmapToPreview(rotatedBitmap);

                        runOnUiThread(() -> {
                            targetImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            targetImageView.setImageBitmap(scaledBitmap);
                        });

                        // Chuyển đổi Bitmap thành mảng byte
                        imageBytes = convertBitmapToByteArray(scaledBitmap);

                        // Xử lý OCR
                        InputImage inputImage = InputImage.fromBitmap(scaledBitmap, 0);
                        processImage(inputImage);

                        image.close();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e("Camera", "Error capturing image", exception);
                    }
                });
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        @SuppressLint("UnsafeOptInUsageError")
        Image mediaImage = image.getImage();
        if (mediaImage == null) return null;

        ByteBuffer buffer = mediaImage.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    private void processImage(InputImage image) {
        recognizer.process(image)
                .addOnSuccessListener(text -> {
                    Log.d("OCR", "Text detected: " + text.getText());
                    String licensePlate = extractLicensePlate(text);

                    // Định dạng lại biển số theo yêu cầu
                    String formattedPlate = formatLicensePlate(extractLicensePlate(text));
                    txtBienSo.setText(formattedPlate);

                    // Nếu nhận diện được biển số hợp lệ
                    if (!licensePlate.equals("Không nhận")) {
                        // Sau khi gán biển số, tắt camera
                        stopCamera();
                        // Bắt đầu xử lý NFC
                        handleNfcReading();
                    } else {
                        // Nếu không nhận diện được biển số, giữ camera mở
                        Toast.makeText(MainActivity.this, "Không nhận diện được biển số", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Log.e("MLKit", "Error processing image", e));
    }

    private String formatLicensePlate(String plate) {
        // Kiểm tra nếu biển số có chứa dấu gạch ngang
        if (plate.contains("-")) {
            // Tách biển số thành 2 phần: phần trước và phần sau dấu gạch ngang
            String part1 = plate.substring(0, 5);  // Lấy 5 ký tự đầu tiên (bao gồm dấu gạch ngang)
            String part2 = plate.substring(5);     // Phần còn lại sau 5 ký tự đầu

            // Loại bỏ dấu gạch ngang trong phần 1 và giữ nguyên phần 2
            part1 = part1.replace("-", "");

            // Trả lại kết quả với phần 1 và phần 2 cách nhau một khoảng trắng
            return part1 + " " + part2;
        }
        return plate;  // Trả lại biển số gốc nếu không cần thay đổi
    }

    private void handleNfcReading() {
        Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE);
        IntentFilter[] intentFilters = new IntentFilter[]{new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)};
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFilters, null);
    }

    private String extractLicensePlate(Text text) {
        for (Text.TextBlock block : text.getTextBlocks()) {
            String blockText = block.getText().replaceAll("\\s+", ""); // Xóa khoảng trắng
            Log.d("OCR", "Checking block (cleaned): " + blockText);

            if (blockText.matches("\\d{2,3}[- ]?[A-Z]\\d{3,5}(\\.\\d{2})?")||
                    blockText.matches("\\d{2,3}[- ]?[A-Z]{2}\\s?\\d{3,5}(\\.\\d{2})?")) {
                Log.d("OCR", "Matched license plate: " + blockText);
                return blockText;  // Trả về biển số đầy đủ
            } else {
                Log.d("OCR", "No match for: " + blockText);
            }
        }
        return "Không nhận";
    }

    private Bitmap scaleBitmapToPreview(Bitmap bitmap) {
        int viewWidth = previewView.getWidth();
        int viewHeight = previewView.getHeight();

        float scale = Math.max((float) viewWidth / bitmap.getWidth(), (float) viewHeight / bitmap.getHeight());
        int newWidth = Math.round(bitmap.getWidth() * scale);
        int newHeight = Math.round(bitmap.getHeight() * scale);

        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);

        // Cắt ảnh để vừa với preview
        int xOffset = (newWidth - viewWidth) / 2;
        int yOffset = (newHeight - viewHeight) / 2;

        return Bitmap.createBitmap(scaledBitmap, xOffset, yOffset, viewWidth, viewHeight);
    }

    private byte[] convertBitmapToByteArray(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream); // Bạn có thể thay đổi định dạng (JPEG, PNG) và chất lượng
        return byteArrayOutputStream.toByteArray();
    }

    //endregion

    //region Mode
    private void setupMode_app() {

    }

    private void setupMode_InOut() {
        btnVao.setSelected(true);
        btnRa.setSelected(false);
        btnVao.setBackgroundResource(R.drawable.toggle_button_2);
        btnRa.setBackgroundResource(android.R.color.transparent);

        btnVao.setOnClickListener(v -> {
            btnVao.setSelected(true);
            btnRa.setSelected(false);
            btnVao.setBackgroundResource(R.drawable.toggle_button_2);
            btnRa.setBackgroundResource(android.R.color.transparent);
            step();
            checkAndExecute();
        });

        btnRa.setOnClickListener(v -> {
            btnVao.setSelected(false);
            btnRa.setSelected(true);
            btnRa.setBackgroundResource(R.drawable.toggle_button_2);
            btnVao.setBackgroundResource(android.R.color.transparent);
            step();
            checkAndExecute();
        });
    }

    private void setupSpinner() {
        Spinner spinner = findViewById(R.id.spinner_price);
        String[] priceOptions = {"4.000 VND", "5.000 VND", "6.000 VND", "8.000 VND", "10.000 VND", "12.000 VND", "15.000 VND"};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, priceOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }
    //endregion

    //region NFC
    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();  // Đảm bảo tài nguyên được đóng đúng cách
        nfcExecutor.shutdown();  // Đóng nfcExecutor khi không cần thiết
    }

    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null) {
            Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE);
            IntentFilter[] intentFilters = new IntentFilter[]{new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)};
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFilters, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (tag != null) {
                String nfcId = bytesToHex(tag.getId());
                txtID.setText(nfcId);
            }
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02X", b));
        }
        return hexString.toString();
    }
    //endregion

    //region CSDL
    private void updateIntime() {
        String id = txtID.getText().toString().trim();         // Lấy giá trị từ txtID
        String bienSo = txtBienSo.getText().toString().trim(); // Lấy giá trị từ txtBienSo

        if (id.isEmpty() || bienSo.isEmpty()) {  // Kiểm tra nếu một trong hai trường trống
            Toast.makeText(this, "Vui lòng nhập đủ thông tin!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (imageBytes == null || imageBytes.length == 0) {
            Toast.makeText(this, "Chưa có ảnh, vui lòng chụp ảnh trước!", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d("DEBUG", "imageBytes: " + Arrays.toString(imageBytes));

        executorService.execute(() -> {
            try {
                Class.forName("com.mysql.jdbc.Driver");

                try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
                    String queryCheckBienSo = "SELECT COUNT(*) FROM startup_baiguixe WHERE BienSoXe = ? AND ThoiGianRa IS NULL"; // tìm xe có biển số xe như đã nhập và chưa có thời gian ra (chưa ra khỏi bãi)
                    try (PreparedStatement psCheckBienSo = connection.prepareStatement(queryCheckBienSo)) {
                        psCheckBienSo.setString(1, bienSo);

                        // Kiểm tra nếu xe vẫn còn trong bãi (ThoiGianRa chưa được cập nhật)
                        try (ResultSet rsCheckBienSo = psCheckBienSo.executeQuery()) {
                            if (rsCheckBienSo.next() && rsCheckBienSo.getInt(1) > 0) {
                                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Xe đã ở trong bãi, chưa thể vào lại!", Toast.LENGTH_SHORT).show());
                                return;
                            }
                        }
                    }


                    String queryCheckNFC = "SELECT COUNT(*) FROM startup_baiguixe WHERE NFC_ID = ? AND ThoiGianRa IS NULL";  //tìm xe có nfc giống như đã nhập và chưa có thời gian ra
                    try (PreparedStatement psCheckNFC = connection.prepareStatement(queryCheckNFC)) {
                        psCheckNFC.setString(1, id);

                        // Kiểm tra NFC ID chỉ với xe chưa ra
                        try (ResultSet rsCheckNFC = psCheckNFC.executeQuery()) {
                            if (rsCheckNFC.next() && rsCheckNFC.getInt(1) > 0) {
                                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Thẻ đã được sử dụng và xe chưa ra bãi!", Toast.LENGTH_SHORT).show());
                                return;
                            }
                        }
                    }

                    // Nếu không có lỗi, tiến hành insert
                    String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
                    //runOnUiThread(() -> Time_in.setText(currentTime));

                    String queryInsert = "INSERT INTO startup_baiguixe (NFC_ID, BienSoXe, ThoiGianVao, AnhXeVao) VALUES (?, ?, ? , ?)";
                    try (PreparedStatement psInsert = connection.prepareStatement(queryInsert)) {
                        psInsert.setString(1, id);           // Chèn giá trị từ txtID vào NFC_ID
                        psInsert.setString(2, bienSo);       // Chèn giá trị từ txtBienSo vào BienSoXe
                        psInsert.setString(3, currentTime);  // Chèn thời gian hiện tại vào ThoiGianVao
                        psInsert.setBytes(4, imageBytes);

                        int rowsInserted = psInsert.executeUpdate();
                        runOnUiThread(() -> {
                            Time_in.setText(currentTime);
                            if (rowsInserted > 0) {
                                //Toast.makeText(getApplicationContext(), "Đã thêm xe " + bienSo, Toast.LENGTH_SHORT).show();
                                tts.speak("Xin mời vào", TextToSpeech.QUEUE_FLUSH, null, "tts_init");
                            } else {
                                Toast.makeText(getApplicationContext(), "Thêm xe thất bại!", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            } catch (ClassNotFoundException | SQLException e) {
                Log.e("DatabaseError", "Lỗi thêm xe", e);
                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Lỗi kết nối!", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void updateExitTime() {
        TextView txtThanhTien = findViewById(R.id.txtThanhTien);
        Spinner spinner = findViewById(R.id.spinner_price); // Lấy Spinner
        String id = txtID.getText().toString().trim();
        String bienSo = txtBienSo.getText().toString().trim();

        if (id.isEmpty() || bienSo.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin!", Toast.LENGTH_SHORT).show();
            return;
        }

        int pricePerHour = getSelectedPrice(spinner);

        if (imageBytes == null || imageBytes.length == 0) {
            Toast.makeText(this, "Chưa có ảnh, vui lòng chụp ảnh trước!", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d("DEBUG", "imageBytes: " + Arrays.toString(imageBytes));

        executorService.execute(() -> {
            try {
                Class.forName("com.mysql.jdbc.Driver");

                try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
                    String queryCheckNFC = "SELECT ID, ThoiGianVao FROM startup_baiguixe " +     // lấy ID và ThoiGianVao của dòng có NFC_ID trùng vs
                            "WHERE NFC_ID = ? AND BienSoXe = ? AND TienGuiXe IS NULL " +          // giá trị đầu vào và chỉ lấy xe chưa thanh toán
                            "ORDER BY ThoiGianVao DESC LIMIT 1";

                    try (PreparedStatement psCheckNFC = connection.prepareStatement(queryCheckNFC)) {
                        psCheckNFC.setString(1, id);       // NFC_ID
                        psCheckNFC.setString(2, bienSo);   // Biển số xe

                        try (ResultSet rsCheckNFC = psCheckNFC.executeQuery()) {
                            if (rsCheckNFC.next()) {
                                int recordId = rsCheckNFC.getInt("ID"); // Lấy ID (khóa chính)
                                Timestamp thoiGianVao = rsCheckNFC.getTimestamp("ThoiGianVao");

                                Timestamp thoiGianRa = new Timestamp(System.currentTimeMillis()); // Lấy thời gian ra
                                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                                String formattedExitTime = sdf.format(thoiGianRa); // Định dạng 24h để lưu vào DB & UI

                                long millisDiff = thoiGianRa.getTime() - thoiGianVao.getTime(); // Tính thời gian đỗ xe
                                long minutesParked = millisDiff / (1000 * 60);
                                int tien_step1 = (minutesParked < 60) ? pricePerHour : (int) Math.ceil(minutesParked * (pricePerHour / 60.0));
                                int tienGuiXe = (int) (Math.ceil(tien_step1 / 1000.0) * 1000);
                                String tien_done = addCommasToNumber(tienGuiXe);

                                String queryUpdate = "UPDATE startup_baiguixe SET ThoiGianRa = ?, TienGuiXe = ?, AnhXeRa = ? WHERE ID = ?";
                                try (PreparedStatement psUpdate = connection.prepareStatement(queryUpdate)) {
                                    psUpdate.setString(1, formattedExitTime); // Lưu chuỗi 24h vào DB
                                    psUpdate.setInt(2, tienGuiXe);
                                    psUpdate.setBytes(3, imageBytes);
                                    psUpdate.setInt(4, recordId);

                                    int rowsUpdated = psUpdate.executeUpdate();
                                    runOnUiThread(() -> {
                                        if (rowsUpdated > 0) {
                                            txtThanhTien.setText(tien_done + " VND");
                                            Time_in.setText(sdf.format(thoiGianVao)); // Hiển thị thời gian vào
                                            Time_out.setText(formattedExitTime); // Hiển thị giờ ra
                                            //Toast.makeText(getApplicationContext(), "Xe " + bienSo + " thanh toán " + tienGuiXe + " VNĐ", Toast.LENGTH_SHORT).show();
                                            //lấy ảnh xe vào
                                            executorService.execute(() -> {
                                                try (Connection connectionnew = DriverManager.getConnection(URL, USER, PASSWORD)) {  // Mở kết nối mới
                                                    String queryGetImage = "SELECT AnhXeVao FROM startup_baiguixe WHERE ID = ?";
                                                    try (PreparedStatement psGetImage = connectionnew.prepareStatement(queryGetImage)) {
                                                        psGetImage.setInt(1, recordId);
                                                        try (ResultSet rsGetImage = psGetImage.executeQuery()) {
                                                            if (rsGetImage.next()) {
                                                                byte[] imageData = rsGetImage.getBytes("AnhXeVao");
                                                                if (imageData == null || imageData.length == 0) {
                                                                    Log.e("DatabaseError", "Ảnh bị NULL hoặc rỗng!");
                                                                    return;
                                                                }

                                                                Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                                                                if (bitmap == null) {
                                                                    Log.e("DatabaseError", "Lỗi giải mã ảnh!");
                                                                    return;
                                                                }

                                                                runOnUiThread(() -> capturedImageView.setImageBitmap(bitmap));
                                                                tts.speak("Chào tạm biệt", TextToSpeech.QUEUE_FLUSH, null, "tts_init");
                                                            } else {
                                                                Log.e("DatabaseError", "Không tìm thấy ảnh!");
                                                            }
                                                        }
                                                    }
                                                } catch (SQLException e) {
                                                    Log.e("DatabaseError", "Lỗi truy vấn ảnh xe vào", e);
                                                }
                                            });

                                        } else {
                                            Toast.makeText(getApplicationContext(), "Cập nhật thất bại!", Toast.LENGTH_SHORT).show();
                                            //tts.speak("Cập nhật thất bại", TextToSpeech.QUEUE_FLUSH, null, "tts_init")
                                        }
                                    });
                                }

                            } else {
                                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Thẻ / Biển số không hợp lệ", Toast.LENGTH_SHORT).show());
                                //tts.speak("Thẻ hoặc biển số không hợp lệ", TextToSpeech.QUEUE_FLUSH, null, "tts_init");
                            }
                        }
                    }

                }
            } catch (ClassNotFoundException | SQLException e) {
                Log.e("DatabaseError", "Lỗi cập nhật giờ ra", e);
                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Lỗi kết nối!", Toast.LENGTH_SHORT).show());
            }
        });
    }
    //endregion

    //region Hàm theo dõi sự thay đổi của txtID và txtBienSo, kiểm tra điều kiện và thực thi hàm
    private void addTextWatcher(TextView txtID, TextView txtBienSo) {
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkAndExecute();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        txtID.addTextChangedListener(textWatcher);
        txtBienSo.addTextChangedListener(textWatcher);
    }

    // Kiểm tra điều kiện và thực thi hàm
    private void checkAndExecute() {
        String id = txtID.getText().toString().trim();
        String bienSo = txtBienSo.getText().toString().trim();

        if (!id.isEmpty() && imageCapture == null) {
            startCamera(); // Khi có ID, mở camera
        }

        if (!id.isEmpty() && !bienSo.isEmpty() && !bienSo.equals("Không nhận")) {
            if (btnVao.isSelected()) {
                updateIntime();
            } else if (btnRa.isSelected()) {
                updateExitTime();
            }
        }
    }
    //endregion

    //region other
    private String addCommasToNumber(int number) {
        String numStr = Integer.toString(number);  // Chuyển số thành chuỗi
        StringBuilder result = new StringBuilder();

        int count = 0;
        for (int i = numStr.length() - 1; i >= 0; i--) {
            result.append(numStr.charAt(i)); // Thêm từng ký tự vào kết quả
            count++;

            if (count % 3 == 0 && i != 0) {
                result.append(","); // Thêm dấu ',' sau mỗi 3 chữ số
            }
        }
        return result.reverse().toString(); // Đảo chuỗi lại để đúng thứ tự
    }


    private int getSelectedPrice(Spinner spinner) {  // lấy đơn giá từ spinner
        String selectedItem = spinner.getSelectedItem().toString(); // Lấy item được chọn
        String numericValue = selectedItem.replaceAll("[^0-9]", ""); // Lọc chỉ giữ số
        return Integer.parseInt(numericValue); // Chuyển thành số nguyên
    }

    private void step(){
        txtID.setText("");
        txtThanhTien.setText("");
        txtBienSo.setText("");
        Time_in.setText("");
        Time_out.setText("");
        imageCapture = null;
        capturedImageView.setImageBitmap(null);
        capturedImageView_Out.setImageBitmap(null);
        stopCamera();
    }
    //endregion
}
