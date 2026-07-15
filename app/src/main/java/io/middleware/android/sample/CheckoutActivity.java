package io.middleware.android.sample;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;

import io.middleware.android.sdk.Middleware;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CheckoutActivity extends AppCompatActivity {

    private static final String TAG = "Checkout";

    private TextInputEditText editName;
    private TextInputEditText editEmail;
    private TextInputEditText editAddress;
    private TextInputEditText editCard;
    private TextInputEditText editExpiry;
    private TextInputEditText editCvv;
    private TextView tvOrderTotal;
    private Button btnPlaceOrder;
    private View rootView;

    private Middleware middleware;
    private Call.Factory okHttpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.checkout_title));
        }

        rootView = findViewById(R.id.checkout_root);
        middleware = Middleware.getInstance();
        okHttpClient = ((CoffeeCartApplication) getApplication()).getRumOkHttpClient();

        editName = findViewById(R.id.edit_name);
        editEmail = findViewById(R.id.edit_email);
        editAddress = findViewById(R.id.edit_address);
        editCard = findViewById(R.id.edit_card_number);
        editExpiry = findViewById(R.id.edit_expiry);
        editCvv = findViewById(R.id.edit_cvv);
        tvOrderTotal = findViewById(R.id.tv_order_total);
        btnPlaceOrder = findViewById(R.id.btn_place_order);

        // Session recording: sanitize sensitive payment fields
        middleware.addSanitizedElement(editCard);
        middleware.addSanitizedElement(editCvv);

        tvOrderTotal.setText(getString(R.string.order_total_label)
                + " " + CartManager.getInstance().getFormattedTotal());

        // Pre-fill name/email from SharedPreferences if available
        android.content.SharedPreferences prefs = getSharedPreferences(
                "coffee_cart_prefs", MODE_PRIVATE);
        editName.setText(prefs.getString("customer_name", ""));
        editEmail.setText(prefs.getString("customer_email", ""));

        btnPlaceOrder.setOnClickListener(v -> placeOrder());

        middleware.i(TAG, "Checkout screen opened – cart total: "
                + CartManager.getInstance().getFormattedTotal());
    }

    private void placeOrder() {
        if (!validateForm()) return;

        btnPlaceOrder.setEnabled(false);
        btnPlaceOrder.setText(getString(R.string.placing_order));

        String name = editName.getText().toString().trim();
        String email = editEmail.getText().toString().trim();
        String card = editCard.getText().toString().trim();

        // Simulate payment decline for test card ending in 0002
        if (card.replaceAll("\\s", "").endsWith("0002")) {
            reportPaymentDeclined(new RuntimeException("Test card declined: " + card));
            return;
        }

        Span checkoutSpan = middleware.startWorkflow("Checkout Flow");
        middleware.i(TAG, "Placing order for customer: " + name);
        middleware.addEvent("checkout_payment_submitted", Attributes.of(
                stringKey("customer.email"), email,
                stringKey("cart.total"), CartManager.getInstance().getFormattedTotal()
        ));

        RequestBody body = new FormBody.Builder()
                .add("name", name)
                .add("email", email)
                .add("cart_total", CartManager.getInstance().getFormattedTotal())
                .build();

        Request request = new Request.Builder()
                .url("https://demo.mw.dev/api/checkout")
                .post(body)
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                middleware.e(TAG, "Order network failure: " + e.getMessage());
                middleware.addException(e, Attributes.of(
                        stringKey("checkout.stage"), "network_request",
                        stringKey("customer.email"), email
                ));
                checkoutSpan.setStatus(StatusCode.ERROR, "Network failure during checkout");
                checkoutSpan.end();
                // Proceed anyway in demo mode — network may not exist
                runOnUiThread(() -> finishOrder());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                int code = response.code();
                response.close();
                if (code >= 500) {
                    RuntimeException serverError =
                            new RuntimeException("Checkout server error: HTTP " + code);
                    middleware.e(TAG, "Server error during checkout: HTTP " + code);
                    middleware.addException(serverError, Attributes.of(
                            stringKey("http.status_code"), String.valueOf(code)
                    ));
                    checkoutSpan.setStatus(StatusCode.ERROR, "Server error " + code);
                }
                checkoutSpan.end();
                runOnUiThread(() -> finishOrder());
            }
        });
    }

    private void finishOrder() {
        String orderId = "CC-" + (1000 + (int) (Math.random() * 9000));
        middleware.i(TAG, "Order completed: " + orderId);
        middleware.addEvent("order_placed", Attributes.of(
                stringKey("order.id"), orderId,
                stringKey("order.total"), CartManager.getInstance().getFormattedTotal()
        ));

        CartManager.getInstance().clear();

        Intent intent = new Intent(this, OrderConfirmationActivity.class);
        intent.putExtra(OrderConfirmationActivity.EXTRA_ORDER_ID, orderId);
        startActivity(intent);
        finish();
    }

    private void reportPaymentDeclined(Exception e) {
        btnPlaceOrder.setEnabled(true);
        btnPlaceOrder.setText(getString(R.string.place_order));
        middleware.e(TAG, "Payment declined: " + e.getMessage());
        middleware.addException(e, Attributes.of(
                stringKey("checkout.stage"), "payment_validation",
                stringKey("decline.reason"), "test_card"
        ));
        Snackbar.make(rootView, getString(R.string.payment_declined), Snackbar.LENGTH_LONG).show();
    }

    private boolean validateForm() {
        boolean valid = true;
        String name = editName.getText() != null ? editName.getText().toString().trim() : "";
        String email = editEmail.getText() != null ? editEmail.getText().toString().trim() : "";
        String address = editAddress.getText() != null ? editAddress.getText().toString().trim() : "";
        String card = editCard.getText() != null ? editCard.getText().toString().replaceAll("\\s", "") : "";
        String expiry = editExpiry.getText() != null ? editExpiry.getText().toString().trim() : "";
        String cvv = editCvv.getText() != null ? editCvv.getText().toString().trim() : "";

        if (name.isEmpty()) {
            editName.setError(getString(R.string.checkout_error_name));
            valid = false;
        }
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editEmail.setError(getString(R.string.checkout_error_email));
            valid = false;
        }
        if (address.isEmpty()) {
            editAddress.setError(getString(R.string.checkout_error_address));
            valid = false;
        }
        if (card.length() < 16) {
            editCard.setError(getString(R.string.checkout_error_card));
            valid = false;
        }
        if (expiry.isEmpty()) {
            editExpiry.setError(getString(R.string.checkout_error_expiry));
            valid = false;
        }
        if (cvv.length() < 3) {
            editCvv.setError(getString(R.string.checkout_error_cvv));
            valid = false;
        }
        return valid;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
