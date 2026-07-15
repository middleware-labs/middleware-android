package io.middleware.android.sample;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import io.middleware.android.sdk.Middleware;
import io.opentelemetry.api.common.Attributes;

public class LoginActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "coffee_cart_prefs";
    public static final String KEY_USERNAME = "username";
    public static final String KEY_LOGGED_IN = "logged_in";

    private TextInputEditText editUsername;
    private Middleware middleware;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_LOGGED_IN, false)
                && !prefs.getString(KEY_USERNAME, "").isEmpty()) {
            openMain();
            return;
        }

        setContentView(R.layout.activity_login);
        middleware = Middleware.getInstance();
        middleware.i("Login", "Login screen opened");

        editUsername = findViewById(R.id.edit_username);
        MaterialButton btnContinue = findViewById(R.id.btn_continue);

        btnContinue.setOnClickListener(v -> attemptLogin());
        editUsername.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });
    }

    private void attemptLogin() {
        String username = editUsername.getText() != null
                ? editUsername.getText().toString().trim()
                : "";

        if (username.isEmpty()) {
            editUsername.setError(getString(R.string.login_error_username));
            return;
        }

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_USERNAME, username)
                .putString("customer_name", username)
                .putBoolean(KEY_LOGGED_IN, true)
                .apply();

        // Set username on RUM session for all subsequent telemetry
        middleware.setGlobalAttribute(stringKey("username"), username);
        middleware.setGlobalAttribute(stringKey("customerName"), username);
        middleware.setGlobalAttribute(stringKey("customerId"), username);
        middleware.i("Login", "User logged in as " + username);
        middleware.addEvent("user_login", Attributes.of(
                stringKey("username"), username
        ));

        Toast.makeText(this, getString(R.string.login_welcome, username), Toast.LENGTH_SHORT).show();
        openMain();
    }

    private void openMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    public static void logout(android.app.Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String username = prefs.getString(KEY_USERNAME, "");
        prefs.edit()
                .remove(KEY_USERNAME)
                .remove(KEY_LOGGED_IN)
                .apply();

        Middleware middleware = Middleware.getInstance();
        middleware.setGlobalAttribute(stringKey("username"), "");
        middleware.setGlobalAttribute(stringKey("customerName"), "");
        middleware.setGlobalAttribute(stringKey("customerId"), "");
        middleware.i("Login", "User logged out: " + username);
        middleware.addEvent("user_logout", Attributes.of(
                stringKey("username"), username
        ));

        Intent intent = new Intent(activity, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
