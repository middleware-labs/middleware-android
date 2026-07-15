package io.middleware.android.sample;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.middleware.android.sample.adapter.CartAdapter;
import io.middleware.android.sample.model.CartItem;
import io.middleware.android.sdk.Middleware;
import io.opentelemetry.api.common.Attributes;

public class CartFragment extends Fragment
        implements CartManager.CartChangeListener, CartAdapter.OnCartActionListener {

    private static final String TAG = "CartFragment";

    private RecyclerView recyclerView;
    private CartAdapter adapter;
    private LinearLayout layoutEmpty;
    private LinearLayout layoutSummary;
    private TextView tvTotal;
    private Button btnCheckout;
    private Middleware middleware;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cart, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        middleware = Middleware.getInstance();

        recyclerView = view.findViewById(R.id.recycler_cart);
        layoutEmpty = view.findViewById(R.id.layout_empty_cart);
        layoutSummary = view.findViewById(R.id.layout_cart_summary);
        tvTotal = view.findViewById(R.id.tv_cart_total_price);
        btnCheckout = view.findViewById(R.id.btn_proceed_checkout);

        adapter = new CartAdapter(CartManager.getInstance().getItems(), this);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        btnCheckout.setOnClickListener(v -> openCheckout());

        CartManager.getInstance().addListener(this);
        middleware.i(TAG, "Cart screen opened");
        refreshCart();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        CartManager.getInstance().removeListener(this);
    }

    @Override
    public void onCartChanged() {
        if (isAdded()) {
            requireActivity().runOnUiThread(this::refreshCart);
        }
    }

    private void refreshCart() {
        List<CartItem> items = CartManager.getInstance().getItems();
        adapter.setItems(items);

        if (items.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            layoutSummary.setVisibility(View.GONE);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            layoutSummary.setVisibility(View.VISIBLE);
            tvTotal.setText(CartManager.getInstance().getFormattedTotal());
        }
    }

    private void openCheckout() {
        middleware.i(TAG, "Proceeding to checkout – items: "
                + CartManager.getInstance().getTotalItemCount());
        middleware.addEvent("checkout_started", Attributes.of(
                stringKey("cart.item_count"),
                String.valueOf(CartManager.getInstance().getTotalItemCount())
        ));
        startActivity(new Intent(requireContext(), CheckoutActivity.class));
    }

    @Override
    public void onQuantityDecrement(CartItem item) {
        int newQty = item.getQuantity() - 1;
        CartManager.getInstance().updateQuantity(item.getProduct().getId(), newQty);
        middleware.d(TAG, "Decremented " + item.getProduct().getName() + " to " + newQty);
    }

    @Override
    public void onQuantityIncrement(CartItem item) {
        int newQty = item.getQuantity() + 1;
        CartManager.getInstance().updateQuantity(item.getProduct().getId(), newQty);
        middleware.d(TAG, "Incremented " + item.getProduct().getName() + " to " + newQty);
    }

    @Override
    public void onRemoveItem(CartItem item) {
        middleware.d(TAG, "Removed from cart: " + item.getProduct().getName());
        middleware.addEvent("remove_from_cart", Attributes.of(
                stringKey("product.id"), item.getProduct().getId(),
                stringKey("product.name"), item.getProduct().getName()
        ));
        CartManager.getInstance().removeProduct(item.getProduct().getId());
    }
}
