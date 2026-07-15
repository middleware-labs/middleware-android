package io.middleware.android.sample;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.middleware.android.sample.adapter.ProductAdapter;
import io.middleware.android.sample.model.Product;
import io.middleware.android.sdk.Middleware;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;

public class MenuFragment extends Fragment implements ProductAdapter.OnProductActionListener {

    private static final String TAG = "MenuFragment";

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private ProductAdapter adapter;
    private Middleware middleware;
    private Span browseWorkflow;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        middleware = Middleware.getInstance();

        recyclerView = view.findViewById(R.id.recycler_products);
        progressBar = view.findViewById(R.id.progress_bar);
        tvEmpty = view.findViewById(R.id.tv_empty);

        adapter = new ProductAdapter(new ArrayList<>(), this);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        browseWorkflow = middleware.startWorkflow("Browse Menu");
        middleware.i(TAG, "Menu screen opened – loading products");

        loadProducts();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (browseWorkflow != null) {
            browseWorkflow.end();
            browseWorkflow = null;
        }
    }

    private void loadProducts() {
        showLoading(true);

        CoffeeCartApplication app = (CoffeeCartApplication) requireActivity().getApplication();
        app.getProductRepository().fetchProducts(products -> {
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                showLoading(false);
                if (products.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                    adapter.setProducts(products);
                    middleware.d(TAG, "Displayed " + products.size() + " products");
                }
            });
        });
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
    }

    @Override
    public void onProductClick(Product product) {
        middleware.d(TAG, "Product tapped: " + product.getName());
        middleware.addEvent("product_viewed", Attributes.of(
                stringKey("product.id"), product.getId(),
                stringKey("product.name"), product.getName(),
                stringKey("product.category"), product.getCategory()
        ));

        Intent intent = new Intent(requireContext(), ProductDetailActivity.class);
        intent.putExtra(ProductDetailActivity.EXTRA_PRODUCT, product);
        startActivity(intent);
    }

    @Override
    public void onAddToCart(Product product) {
        CartManager.getInstance().addProduct(product, 1);
        middleware.i(TAG, "Quick-add to cart: " + product.getName());
        middleware.addEvent("add_to_cart", Attributes.of(
                stringKey("product.id"), product.getId(),
                stringKey("product.name"), product.getName(),
                stringKey("source"), "menu_list"
        ));
    }
}
