package io.middleware.android.sample;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.middleware.android.sample.model.CartItem;
import io.middleware.android.sample.model.Product;

public class CartManager {

    private static CartManager instance;

    private final List<CartItem> items = new ArrayList<>();
    private final List<CartChangeListener> listeners = new ArrayList<>();

    public interface CartChangeListener {
        void onCartChanged();
    }

    public static CartManager getInstance() {
        if (instance == null) {
            instance = new CartManager();
        }
        return instance;
    }

    public void addProduct(Product product, int quantity) {
        for (CartItem item : items) {
            if (item.getProduct().getId().equals(product.getId())) {
                item.setQuantity(item.getQuantity() + quantity);
                notifyListeners();
                return;
            }
        }
        items.add(new CartItem(product, quantity));
        notifyListeners();
    }

    public void updateQuantity(String productId, int quantity) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getProduct().getId().equals(productId)) {
                if (quantity <= 0) {
                    items.remove(i);
                } else {
                    items.get(i).setQuantity(quantity);
                }
                notifyListeners();
                return;
            }
        }
    }

    public void removeProduct(String productId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getProduct().getId().equals(productId)) {
                items.remove(i);
                notifyListeners();
                return;
            }
        }
    }

    public void clear() {
        items.clear();
        notifyListeners();
    }

    public List<CartItem> getItems() {
        return new ArrayList<>(items);
    }

    public int getTotalItemCount() {
        int count = 0;
        for (CartItem item : items) {
            count += item.getQuantity();
        }
        return count;
    }

    public double getTotalPrice() {
        double total = 0;
        for (CartItem item : items) {
            total += item.getTotalPrice();
        }
        return total;
    }

    public String getFormattedTotal() {
        return String.format(Locale.US, "$%.2f", getTotalPrice());
    }

    public void addListener(CartChangeListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(CartChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (CartChangeListener listener : new ArrayList<>(listeners)) {
            listener.onCartChanged();
        }
    }
}
