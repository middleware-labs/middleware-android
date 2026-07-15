package io.middleware.android.sample;

import static io.middleware.android.sample.LoginActivity.KEY_USERNAME;
import static io.middleware.android.sample.LoginActivity.PREFS_NAME;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import io.middleware.android.sdk.Middleware;
import io.opentelemetry.api.common.Attributes;

public class AccountFragment extends Fragment {

    private static final String TAG = "AccountFragment";
    private static final String KEY_EMAIL = "customer_email";

    private TextInputEditText editName;
    private TextInputEditText editEmail;
    private TextView tvSignedInAs;
    private TextView tvSessionId;
    private TextView tvAppVersion;
    private Middleware middleware;
    private String username = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_account, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        middleware = Middleware.getInstance();

        SharedPreferences prefs = requireActivity()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        username = prefs.getString(KEY_USERNAME, "");

        tvSignedInAs = view.findViewById(R.id.tv_signed_in_as);
        editName = view.findViewById(R.id.edit_customer_name);
        editEmail = view.findViewById(R.id.edit_customer_email);
        tvSessionId = view.findViewById(R.id.tv_session_id);
        tvAppVersion = view.findViewById(R.id.tv_app_version);
        Button btnSave = view.findViewById(R.id.btn_save_profile);
        Button btnHelp = view.findViewById(R.id.btn_help);
        Button btnRumLab = view.findViewById(R.id.btn_rum_lab);
        Button btnSignOut = view.findViewById(R.id.btn_sign_out);

        tvSignedInAs.setText(getString(R.string.signed_in_as, username));
        editName.setText(username);
        editEmail.setText(prefs.getString(KEY_EMAIL, ""));

        tvAppVersion.setText(getString(R.string.app_version_label) + BuildConfig.VERSION_NAME);
        tvSessionId.setText(getString(R.string.session_id_label)
                + middleware.getRumSessionId());

        btnSave.setOnClickListener(v -> saveProfile(view));
        btnHelp.setOnClickListener(v -> openHelp());
        btnRumLab.setOnClickListener(v -> openRumLab());
        btnSignOut.setOnClickListener(v -> LoginActivity.logout(requireActivity()));

        // Ensure username is present on RUM for returning sessions
        if (!username.isEmpty()) {
            middleware.setGlobalAttribute(stringKey("username"), username);
            middleware.setGlobalAttribute(stringKey("customerName"), username);
            middleware.setGlobalAttribute(stringKey("customerId"), username);
        }

        middleware.i(TAG, "Account screen opened for user " + username);
    }

    private void saveProfile(View rootView) {
        String email = editEmail.getText() != null ? editEmail.getText().toString().trim() : "";

        requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_EMAIL, email)
                .apply();

        middleware.setGlobalAttribute(stringKey("username"), username);
        middleware.setGlobalAttribute(stringKey("customerName"), username);
        middleware.setGlobalAttribute(stringKey("customer.email"), email);
        middleware.setGlobalAttribute(stringKey("customerId"), username);

        middleware.i(TAG, "Profile saved – username: " + username);
        middleware.addEvent("profile_saved", Attributes.of(
                stringKey("username"), username
        ));

        Snackbar.make(rootView, getString(R.string.profile_saved), Snackbar.LENGTH_SHORT).show();
    }

    private void openHelp() {
        middleware.d(TAG, "Opening Help WebView");
        startActivity(new Intent(requireContext(), WebViewActivity.class));
    }

    private void openRumLab() {
        middleware.d(TAG, "Opening RUM Lab");
        startActivity(new Intent(requireContext(), RumLabActivity.class));
    }
}
