package io.middleware.android.sample;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import io.middleware.android.sdk.Middleware;

public class MainActivity extends AppCompatActivity implements CartManager.CartChangeListener {

    private static final String TAG_MENU = "tag_menu";
    private static final String TAG_CART = "tag_cart";
    private static final String TAG_ACCOUNT = "tag_account";

    private BottomNavigationView bottomNav;
    private Middleware middleware;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Require login before showing the shop
        android.content.SharedPreferences prefs =
                getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean(LoginActivity.KEY_LOGGED_IN, false)
                || prefs.getString(LoginActivity.KEY_USERNAME, "").isEmpty()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        String username = prefs.getString(LoginActivity.KEY_USERNAME, "");
        setContentView(R.layout.activity_main);

        middleware = Middleware.getInstance();
        middleware.setGlobalAttribute(
                io.opentelemetry.api.common.AttributeKey.stringKey("username"), username);
        middleware.i("APP", "Main shop opened for user " + username);

        bottomNav = findViewById(R.id.bottom_navigation);

        if (savedInstanceState == null) {
            showFragment(TAG_MENU);
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_menu) {
                showFragment(TAG_MENU);
                return true;
            } else if (id == R.id.nav_cart) {
                showFragment(TAG_CART);
                return true;
            } else if (id == R.id.nav_account) {
                showFragment(TAG_ACCOUNT);
                return true;
            }
            return false;
        });

        CartManager.getInstance().addListener(this);
        updateCartBadge();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CartManager.getInstance().removeListener(this);
    }

    @Override
    public void onCartChanged() {
        updateCartBadge();
    }

    private void updateCartBadge() {
        int count = CartManager.getInstance().getTotalItemCount();
        BadgeDrawable badge = bottomNav.getOrCreateBadge(R.id.nav_cart);
        if (count > 0) {
            badge.setNumber(count);
            badge.setVisible(true);
            badge.setBackgroundColor(getResources().getColor(R.color.caramel_accent, getTheme()));
        } else {
            badge.setVisible(false);
        }
    }

    private void showFragment(String tag) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();

        Fragment menuFrag = fm.findFragmentByTag(TAG_MENU);
        Fragment cartFrag = fm.findFragmentByTag(TAG_CART);
        Fragment accountFrag = fm.findFragmentByTag(TAG_ACCOUNT);

        if (menuFrag != null) ft.hide(menuFrag);
        if (cartFrag != null) ft.hide(cartFrag);
        if (accountFrag != null) ft.hide(accountFrag);

        Fragment target = fm.findFragmentByTag(tag);
        if (target == null) {
            target = createFragment(tag);
            ft.add(R.id.fragment_container, target, tag);
        } else {
            ft.show(target);
        }

        ft.commit();
        middleware.d("NAV", "Navigated to tab: " + tag);
    }

    private Fragment createFragment(String tag) {
        switch (tag) {
            case TAG_CART:    return new CartFragment();
            case TAG_ACCOUNT: return new AccountFragment();
            default:          return new MenuFragment();
        }
    }
}
