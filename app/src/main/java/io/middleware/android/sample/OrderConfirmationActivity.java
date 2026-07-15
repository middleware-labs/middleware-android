package io.middleware.android.sample;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import io.middleware.android.sdk.Middleware;
import io.opentelemetry.api.common.Attributes;

public class OrderConfirmationActivity extends AppCompatActivity {

    public static final String EXTRA_ORDER_ID = "extra_order_id";
    private static final String TAG = "OrderConfirmation";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_confirmation);

        String orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        if (orderId == null) orderId = "CC-????";

        TextView tvOrderId = findViewById(R.id.tv_order_id);
        Button btnBackToMenu = findViewById(R.id.btn_back_to_menu);

        tvOrderId.setText(getString(R.string.order_id_prefix) + orderId);

        Middleware.getInstance().i(TAG, "Order confirmation shown: " + orderId);
        Middleware.getInstance().addEvent("order_confirmed_viewed",
                Attributes.empty());

        btnBackToMenu.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }
}
