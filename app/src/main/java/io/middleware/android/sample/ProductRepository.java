package io.middleware.android.sample;

import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.middleware.android.sample.model.Product;
import io.middleware.android.sdk.Middleware;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ProductRepository {

    private static final String TAG = "ProductRepository";
    private static final String PRODUCTS_URL = "https://demo.mw.dev/api/products?currencyCode=USD";

    public interface ProductsCallback {
        void onProducts(List<Product> products);
    }

    private final Call.Factory okHttpClient;
    private final Middleware middleware;

    public ProductRepository(Call.Factory okHttpClient, Middleware middleware) {
        this.okHttpClient = okHttpClient;
        this.middleware = middleware;
    }

    public void fetchProducts(ProductsCallback callback) {
        middleware.i(TAG, "Fetching products from API: " + PRODUCTS_URL);
        Request request = new Request.Builder().url(PRODUCTS_URL).get().build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                middleware.e(TAG, "Product fetch failed: " + e.getMessage());
                middleware.addException(e);
                callback.onProducts(getLocalCatalog());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        middleware.w(TAG, "API returned HTTP " + response.code() + ", using local catalog");
                        callback.onProducts(getLocalCatalog());
                        return;
                    }
                    String json = body.string();
                    List<Product> products = parseProducts(json);
                    if (products.isEmpty()) {
                        middleware.w(TAG, "API returned 0 parseable products, using local catalog");
                        callback.onProducts(getLocalCatalog());
                    } else {
                        middleware.i(TAG, "Loaded " + products.size() + " products from API");
                        callback.onProducts(products);
                    }
                } catch (Exception e) {
                    middleware.e(TAG, "Response parse error: " + e.getMessage());
                    middleware.addException(e);
                    callback.onProducts(getLocalCatalog());
                }
            }
        });
    }

    private List<Product> parseProducts(String json) {
        List<Product> products = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("products");
            if (arr == null) return products;

            String[] emojis = {"☕", "🧋", "🍵", "🥤", "🍫", "☕", "🥛", "🍃"};
            for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.getJSONObject(i);
                String id = p.optString("id", String.valueOf(i + 1));
                String name = p.optString("name", "Coffee");
                String description = p.optString("description", "A delightful brew.");

                double price = 4.50;
                JSONObject priceObj = p.optJSONObject("priceUsd");
                if (priceObj != null) {
                    int units = priceObj.optInt("units", 4);
                    long nanos = priceObj.optLong("nanos", 500000000L);
                    price = units + nanos / 1_000_000_000.0;
                }

                String category = p.optString("categories", "coffee");
                String emoji = emojis[i % emojis.length];
                products.add(new Product(id, name, description, price, "USD", category, emoji));
            }
        } catch (Exception e) {
            Log.e(TAG, "JSON parse error", e);
        }
        return products;
    }

    public static List<Product> getLocalCatalog() {
        List<Product> catalog = new ArrayList<>();
        catalog.add(new Product("1", "Espresso",
                "Bold and intense single shot pulled from premium Colombian beans. Rich crema with notes of dark chocolate.",
                3.50, "USD", "espresso", "☕"));
        catalog.add(new Product("2", "Cappuccino",
                "Velvety espresso topped with equal parts steamed and foamed whole milk. A true Italian classic.",
                4.75, "USD", "milk", "🥛"));
        catalog.add(new Product("3", "Latte",
                "Double espresso with silky microfoam milk. Smooth, mellow and endlessly customizable.",
                5.00, "USD", "milk", "🧋"));
        catalog.add(new Product("4", "Cold Brew",
                "12-hour slow-steeped Colombian beans served over ice. Naturally sweet, never bitter.",
                5.50, "USD", "cold", "🥤"));
        catalog.add(new Product("5", "Mocha",
                "Double espresso, house-made chocolate sauce and steamed milk. A guilt-worthy indulgence.",
                5.25, "USD", "milk", "🍫"));
        catalog.add(new Product("6", "Americano",
                "Two ristretto shots diluted with hot water for a clean, bright, long cup.",
                3.75, "USD", "espresso", "☕"));
        catalog.add(new Product("7", "Flat White",
                "Ristretto shots with velvety microfoamed milk. Stronger than a latte, creamier than an espresso.",
                5.00, "USD", "milk", "🥛"));
        catalog.add(new Product("8", "Matcha Latte",
                "Ceremonial-grade Japanese matcha whisked with oat milk. Earthy, creamy and vibrant.",
                5.75, "USD", "specialty", "🍵"));
        return catalog;
    }
}
