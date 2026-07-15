package io.middleware.android.sample;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import io.middleware.android.sample.model.Product;
import io.middleware.android.sdk.Middleware;
import io.opentelemetry.api.common.Attributes;

public class ProductDetailActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT = "extra_product";
    private static final String TAG = "ProductDetail";

    private int quantity = 1;
    private Product product;
    private Middleware middleware;
    private TextView tvQuantity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        middleware = Middleware.getInstance();

        product = (Product) getIntent().getSerializableExtra(EXTRA_PRODUCT);
        if (product == null) {
            finish();
            return;
        }

        getSupportActionBar().setTitle(product.getName());

        TextView tvEmoji = findViewById(R.id.tv_detail_emoji);
        TextView tvName = findViewById(R.id.tv_detail_name);
        TextView tvCategory = findViewById(R.id.tv_detail_category);
        TextView tvDescription = findViewById(R.id.tv_detail_description);
        TextView tvPrice = findViewById(R.id.tv_detail_price);
        tvQuantity = findViewById(R.id.tv_detail_quantity);
        Button btnMinus = findViewById(R.id.btn_qty_minus);
        Button btnPlus = findViewById(R.id.btn_qty_plus);
        Button btnAddToCart = findViewById(R.id.btn_detail_add_to_cart);

        tvEmoji.setText(product.getEmoji());
        tvName.setText(product.getName());
        tvCategory.setText(product.getCategory().toUpperCase());
        tvDescription.setText(product.getDescription());
        tvPrice.setText(product.getFormattedPrice());
        tvQuantity.setText(String.valueOf(quantity));

        btnMinus.setOnClickListener(v -> {
            if (quantity > 1) {
                quantity--;
                tvQuantity.setText(String.valueOf(quantity));
                middleware.d(TAG, "Quantity decreased to " + quantity);
            }
        });

        btnPlus.setOnClickListener(v -> {
            quantity++;
            tvQuantity.setText(String.valueOf(quantity));
            middleware.d(TAG, "Quantity increased to " + quantity);
        });

        btnAddToCart.setOnClickListener(v -> addToCart());

        middleware.i(TAG, "Viewing product: " + product.getName());
        middleware.addEvent("product_detail_viewed", Attributes.of(
                stringKey("product.id"), product.getId(),
                stringKey("product.name"), product.getName(),
                stringKey("product.price"), product.getFormattedPrice()
        ));
    }

    private void addToCart() {
        CartManager.getInstance().addProduct(product, quantity);
        middleware.i(TAG, "Added to cart: " + product.getName() + " x" + quantity);
        middleware.addEvent("add_to_cart", Attributes.of(
                stringKey("product.id"), product.getId(),
                stringKey("product.name"), product.getName(),
                stringKey("quantity"), String.valueOf(quantity),
                stringKey("source"), "product_detail"
        ));
        Toast.makeText(this, product.getName() + " added to cart!", Toast.LENGTH_SHORT).show();
        finish();
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
