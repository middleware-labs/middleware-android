package io.middleware.android.sample.model;

import java.io.Serializable;
import java.util.Locale;

public class Product implements Serializable {

    private final String id;
    private final String name;
    private final String description;
    private final double price;
    private final String currency;
    private final String category;
    private final String emoji;

    public Product(String id, String name, String description,
                   double price, String currency, String category, String emoji) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.currency = currency;
        this.category = category;
        this.emoji = emoji;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public double getPrice() { return price; }
    public String getCurrency() { return currency; }
    public String getCategory() { return category; }
    public String getEmoji() { return emoji; }

    public String getFormattedPrice() {
        return String.format(Locale.US, "$%.2f", price);
    }
}
