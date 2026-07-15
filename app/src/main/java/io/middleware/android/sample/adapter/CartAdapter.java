package io.middleware.android.sample.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.middleware.android.sample.R;
import io.middleware.android.sample.model.CartItem;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.CartViewHolder> {

    public interface OnCartActionListener {
        void onQuantityDecrement(CartItem item);
        void onQuantityIncrement(CartItem item);
        void onRemoveItem(CartItem item);
    }

    private List<CartItem> items;
    private final OnCartActionListener listener;

    public CartAdapter(List<CartItem> items, OnCartActionListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void setItems(List<CartItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CartViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cart, parent, false);
        return new CartViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CartViewHolder holder, int position) {
        CartItem item = items.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    static class CartViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvEmoji;
        private final TextView tvName;
        private final TextView tvUnitPrice;
        private final TextView tvQuantity;
        private final TextView tvTotal;
        private final ImageButton btnDecrement;
        private final ImageButton btnIncrement;
        private final ImageButton btnRemove;

        CartViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEmoji = itemView.findViewById(R.id.tv_cart_emoji);
            tvName = itemView.findViewById(R.id.tv_cart_name);
            tvUnitPrice = itemView.findViewById(R.id.tv_cart_unit_price);
            tvQuantity = itemView.findViewById(R.id.tv_cart_quantity);
            tvTotal = itemView.findViewById(R.id.tv_cart_total);
            btnDecrement = itemView.findViewById(R.id.btn_decrement);
            btnIncrement = itemView.findViewById(R.id.btn_increment);
            btnRemove = itemView.findViewById(R.id.btn_remove);
        }

        void bind(CartItem item, OnCartActionListener listener) {
            tvEmoji.setText(item.getProduct().getEmoji());
            tvName.setText(item.getProduct().getName());
            tvUnitPrice.setText(item.getProduct().getFormattedPrice() + " each");
            tvQuantity.setText(String.valueOf(item.getQuantity()));
            tvTotal.setText(item.getFormattedTotal());

            btnDecrement.setOnClickListener(v -> listener.onQuantityDecrement(item));
            btnIncrement.setOnClickListener(v -> listener.onQuantityIncrement(item));
            btnRemove.setOnClickListener(v -> listener.onRemoveItem(item));
        }
    }
}
