package io.middleware.android.sample.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.middleware.android.sample.R;
import io.middleware.android.sample.model.Product;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {

    public interface OnProductActionListener {
        void onProductClick(Product product);
        void onAddToCart(Product product);
    }

    private List<Product> products;
    private final OnProductActionListener listener;

    public ProductAdapter(List<Product> products, OnProductActionListener listener) {
        this.products = products;
        this.listener = listener;
    }

    public void setProducts(List<Product> products) {
        this.products = products;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_product, parent, false);
        return new ProductViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        Product product = products.get(position);
        holder.bind(product, listener);
    }

    @Override
    public int getItemCount() {
        return products == null ? 0 : products.size();
    }

    static class ProductViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvEmoji;
        private final TextView tvName;
        private final TextView tvDescription;
        private final TextView tvPrice;
        private final Button btnAddToCart;

        ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEmoji = itemView.findViewById(R.id.tv_product_emoji);
            tvName = itemView.findViewById(R.id.tv_product_name);
            tvDescription = itemView.findViewById(R.id.tv_product_description);
            tvPrice = itemView.findViewById(R.id.tv_product_price);
            btnAddToCart = itemView.findViewById(R.id.btn_add_to_cart);
        }

        void bind(Product product, OnProductActionListener listener) {
            tvEmoji.setText(product.getEmoji());
            tvName.setText(product.getName());
            tvDescription.setText(product.getDescription());
            tvPrice.setText(product.getFormattedPrice());

            itemView.setOnClickListener(v -> listener.onProductClick(product));
            btnAddToCart.setOnClickListener(v -> listener.onAddToCart(product));
        }
    }
}
