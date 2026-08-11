package top.niunaijun.blackboxa.view.camera;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import top.niunaijun.blackbox.entity.camera.BCameraConfig;
import top.niunaijun.blackbox.entity.camera.BFakeCamera;
import top.niunaijun.blackbox.fake.frameworks.BCameraManager;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackboxa.R;
import top.niunaijun.blackboxa.databinding.ActivityCameraSettingsBinding;

public class CameraSettingActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "user_id";
    public static final String EXTRA_PACKAGE_NAME = "package_name";
    public static final String EXTRA_APP_NAME = "app_name";

    private ActivityCameraSettingsBinding binding;
    private int userId;
    private String packageName;
    private String appName;

    private final ActivityResultLauncher<String[]> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    binding.editSourcePath.setText(uri.toString());
                }
            });

    public static void start(Context context, int userId, String packageName, String appName) {
        Intent intent = new Intent(context, CameraSettingActivity.class);
        intent.putExtra(EXTRA_USER_ID, userId);
        intent.putExtra(EXTRA_PACKAGE_NAME, packageName);
        intent.putExtra(EXTRA_APP_NAME, appName);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCameraSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        extractIntentData();
        setupToolbar();
        setupProtectionSpinner();
        setupSwitchListeners();
        setupFilePicker();
        setupSaveButton();
        loadExistingConfig();
    }

    private void extractIntentData() {
        userId = getIntent().getIntExtra(EXTRA_USER_ID, 0);
        packageName = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
        appName = getIntent().getStringExtra(EXTRA_APP_NAME);
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbarLayout.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(appName + " - Camera Settings");
        }
        binding.toolbarLayout.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupProtectionSpinner() {
        String[] protectionMethods = {
            getString(R.string.disable_camera),
            getString(R.string.local_video),
            getString(R.string.network_stream),
            getString(R.string.local_image)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, protectionMethods);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerProtectionMethod.setAdapter(adapter);
    }

    private void setupSwitchListeners() {
        binding.switchFakeCamera.setOnCheckedChangeListener((buttonView, isChecked) -> {
            binding.cardCameraSettings.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
    }

    private void setupFilePicker() {
        binding.btnSelectFile.setOnClickListener(v -> {
            filePickerLauncher.launch(new String[]{
                    "video/*",
                    "image/*"
            });
        });
    }

    private void setupSaveButton() {
        binding.btnSave.setOnClickListener(v -> saveSettings());
    }

    private void loadExistingConfig() {
        try {
            int pattern = BCameraManager.get().getPattern(userId, packageName);
            BFakeCamera fakeCamera = BCameraManager.get().getFakeCamera(userId, packageName);

            if (pattern != BFakeCamera.DISABLED && fakeCamera != null) {
                binding.switchFakeCamera.setChecked(true);
                binding.cardCameraSettings.setVisibility(View.VISIBLE);
                
                binding.spinnerProtectionMethod.setSelection(fakeCamera.getMode());
                binding.editSourcePath.setText(fakeCamera.getSourcePath());
                binding.editWidth.setText(String.valueOf(fakeCamera.getWidth()));
                binding.editHeight.setText(String.valueOf(fakeCamera.getHeight()));
                binding.switchAudio.setChecked(fakeCamera.isAudioEnabled());
            } else {
                binding.switchFakeCamera.setChecked(false);
                binding.cardCameraSettings.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load camera config", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveSettings() {
        try {
            boolean fakeCameraEnabled = binding.switchFakeCamera.isChecked();
            
            if (!fakeCameraEnabled) {
                BCameraManager.disableFakeCamera(userId, packageName);
                Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            int protectionMethod = binding.spinnerProtectionMethod.getSelectedItemPosition();
            String sourcePath = binding.editSourcePath.getText().toString().trim();
            int width = parseIntOrDefault(binding.editWidth.getText().toString(), 1280);
            int height = parseIntOrDefault(binding.editHeight.getText().toString(), 720);
            boolean audioEnabled = binding.switchAudio.isChecked();

            BFakeCamera fakeCamera = new BFakeCamera();
            fakeCamera.setMode(protectionMethod);
            fakeCamera.setSourcePath(sourcePath);
            fakeCamera.setWidth(width);
            fakeCamera.setHeight(height);
            fakeCamera.setAudioEnabled(audioEnabled);

            BCameraManager.get().setPattern(userId, packageName, BCameraManager.OWN_MODE);
            BCameraManager.get().setFakeCamera(userId, packageName, fakeCamera);

            Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to save camera settings", Toast.LENGTH_SHORT).show();
        }
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
