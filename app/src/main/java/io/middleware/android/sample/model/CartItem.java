package io.middleware.android.sample.model;

import java.util.Locale;

public class CartItem {

    private final Product product;
    private int quantity;

    public CartItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getTotalPrice() {
        return product.getPrice() * quantity;
    }

    public String getFormattedTotal() {
        return String.format(Locale.US, "$%.2f", getTotalPrice());
    }
}
