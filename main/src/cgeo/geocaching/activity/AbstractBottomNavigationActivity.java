package cgeo.geocaching.activity;

import cgeo.geocaching.CacheListActivity;
import cgeo.geocaching.MainActivity;
import cgeo.geocaching.R;
import cgeo.geocaching.SearchActivity;
import cgeo.geocaching.connector.ConnectorFactory;
import cgeo.geocaching.connector.IConnector;
import cgeo.geocaching.connector.capability.ILogin;
import cgeo.geocaching.databinding.ActivityBottomNavigationBinding;
import cgeo.geocaching.list.PseudoList;
import cgeo.geocaching.list.StoredList;
import cgeo.geocaching.maps.DefaultMap;
import cgeo.geocaching.network.Network;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.utils.AndroidRxUtils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import java.util.concurrent.atomic.AtomicInteger;

import com.google.android.material.navigation.NavigationBarView;


public abstract class AbstractBottomNavigationActivity extends AbstractActionBarActivity implements NavigationBarView.OnItemSelectedListener {
    public static final @IdRes
    int MENU_MAP = R.id.page_map;
    public static final @IdRes
    int MENU_LIST = R.id.page_list;
    public static final @IdRes
    int MENU_SEARCH = R.id.page_search;
    public static final @IdRes
    int MENU_NEARBY = R.id.page_nearby;
    public static final @IdRes
    int MENU_HOME = R.id.page_home;
    public static final @IdRes
    int MENU_NOTHING_SELECTED = 0;
    public static final @IdRes
    int MENU_HIDE_BOTTOM_NAVIGATION = -1;

    private static Boolean loginSuccessful = null; // must be static so that the login state is stored while switching between activities


    private ActivityBottomNavigationBinding wrapper = null;
    private boolean doubleBackToExitPressedOnce = false;

    private final ConnectivityChangeReceiver connectivityChangeReceiver = new ConnectivityChangeReceiver();
    private final Handler loginHandler = new Handler();

    private static final AtomicInteger LOGINS_IN_PROGRESS = new AtomicInteger(0);

    @Override
    public void setContentView(final View contentView) {
        wrapper = ActivityBottomNavigationBinding.inflate(getLayoutInflater());
        wrapper.activityContent.addView(contentView);
        super.setContentView(wrapper.getRoot());

        // --- other initialization --- //
        updateSelectedItemId();
        // add item selected listener only after item selection has been updated, as it would otherwise directly call the listener
        ((NavigationBarView) wrapper.activityBottomNavigation).setOnItemSelectedListener(this);
        // long click event listeners
        findViewById(R.id.page_list).setOnLongClickListener(view -> onListsLongClicked());
        // will be called if c:geo cannot log in
        registerLoginIssueHandler(loginHandler, getUpdateUserInfoHandler(), this::onLoginIssue);
    }

    @Nullable
    protected Handler getUpdateUserInfoHandler() {
        return null;
    }


    protected void onLoginIssue(final boolean issue) {
        if (issue) {
            ((NavigationBarView) wrapper.activityBottomNavigation).getOrCreateBadge(MENU_HOME);
        } else {
            ((NavigationBarView) wrapper.activityBottomNavigation).removeBadge(MENU_HOME);
        }
    }

    private boolean onListsLongClicked() {
        new StoredList.UserInterface(this).promptForListSelection(R.string.list_title, selectedListId -> {
            if (selectedListId == PseudoList.HISTORY_LIST.id) {
                startActivity(CacheListActivity.getHistoryIntent(this));
            } else {
                Settings.setLastDisplayedList(selectedListId);
                CacheListActivity.startActivityOffline(this);
            }
            ActivityMixin.finishWithFadeTransition(this);
        }, false, PseudoList.NEW_LIST.id);
        return true;
    }

    @Override
    protected void onDestroy() {
        // remove callbacks before closing activity to avoid memory leaks
        loginHandler.removeCallbacksAndMessages(null);

        super.onDestroy();
    }

    @Override
    public void onPause() {
        unregisterReceiver(connectivityChangeReceiver);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerLoginIssueHandler(loginHandler, getUpdateUserInfoHandler(), this::onLoginIssue);
        registerReceiver(connectivityChangeReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    public void setContentView(final int layoutResID) {
        final View view = getLayoutInflater().inflate(layoutResID, null);
        setContentView(view);
    }

    @Override
    public void onBackPressed() {
        if (!isTaskRoot() || doubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }

        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, R.string.touch_again_to_exit, Toast.LENGTH_SHORT).show();
        new Handler().postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
    }

    /**
     * WARNING: This triggers {@link AbstractBottomNavigationActivity#onNavigationItemSelected} while changing the current selection.
     */
    private void updateSelectedItemId() {
        final int menuId = getSelectedBottomItemId();
        final MenuItem workaroundItem = ((NavigationBarView) wrapper.activityBottomNavigation).getMenu().getItem(0);

        // According to material design guidelines, bottom navigation activities always should be top-level.
        // Therefore hiding the bottom navigation to not confuse the users.
        if (menuId == MENU_HIDE_BOTTOM_NAVIGATION || !isTaskRoot()) {
            wrapper.activityBottomNavigation.setVisibility(View.GONE);
        } else if (menuId == MENU_NOTHING_SELECTED) {
            wrapper.activityBottomNavigation.setVisibility(View.VISIBLE);
            ((NavigationBarView) wrapper.activityBottomNavigation).setSelectedItemId(workaroundItem.getItemId());
            workaroundItem.setCheckable(false);
        } else {
            wrapper.activityBottomNavigation.setVisibility(View.VISIBLE);
            ((NavigationBarView) wrapper.activityBottomNavigation).setSelectedItemId(menuId);
            workaroundItem.setCheckable(true);
        }
    }

    /**
     * @return the menu item id which should be selected
     */
    public abstract @IdRes
    int getSelectedBottomItemId();

    public void onNavigationItemReselected(final @NonNull MenuItem item) {
        // do nothing by default. Can be overridden by subclasses.
    }

    @Override
    public boolean onNavigationItemSelected(final @NonNull MenuItem item) {
        final int id = item.getItemId();

        if (id == getSelectedBottomItemId()) {
            onNavigationItemReselected(item);
            return true;
        }
        return onNavigationItemSelectedIgnoreReselected(item);
    }

    public boolean onNavigationItemSelectedIgnoreReselected(final @NonNull MenuItem item) {
        final int id = item.getItemId();

        if (id == MENU_MAP) {
            startActivity(DefaultMap.getLiveMapIntent(this));
        } else if (id == MENU_LIST) {
            CacheListActivity.startActivityOffline(this);
        } else if (id == MENU_SEARCH) {
            startActivity(new Intent(this, SearchActivity.class));
        } else if (id == MENU_NEARBY) {
            startActivity(CacheListActivity.getNearestIntent(this));
        } else if (id == MENU_HOME) {
            startActivity(new Intent(this, MainActivity.class));
        } else {
            throw new IllegalStateException("unknown navigation item selected"); // should never happen
        }
        // avoid weired transitions
        ActivityMixin.finishWithFadeTransition(this);
        return true;
    }


    private final class ConnectivityChangeReceiver extends BroadcastReceiver {
        private boolean isConnected = Network.isConnected();

        @Override
        public void onReceive(final Context context, final Intent intent) {
            final boolean wasConnected = isConnected;
            isConnected = Network.isConnected();
            if (isConnected && !wasConnected) {
                registerLoginIssueHandler(loginHandler, getUpdateUserInfoHandler(), AbstractBottomNavigationActivity.this::onLoginIssue);
            }
        }
    }

    /**
     * check if at least one connector has been logged in successfully
     */
    public static boolean anyConnectorLoggedIn() {
        final ILogin[] activeConnectors = ConnectorFactory.getActiveLiveConnectors();
        for (final IConnector conn : activeConnectors) {
            if (((ILogin) conn).isLoggedIn()) {
                return true;
            }
        }
        return false;
    }

    /**
     * detect whether c:geo is unable to log in
     *
     * @param updateUserInfoHandler handle completed logins
     * @param loginIssueCallback    code to be executed on login issues
     */
    public static void registerLoginIssueHandler(final Handler scheduler, @Nullable final Handler updateUserInfoHandler, final Consumer<Boolean> loginIssueCallback) {
        if (loginSuccessful != null && !loginSuccessful) {
            loginSuccessful = anyConnectorLoggedIn();
        }
        if (loginSuccessful != null && !loginSuccessful) {
            loginIssueCallback.accept(true); // login still failing. Start loginIssueCallback
        }
        if (loginSuccessful != null && loginSuccessful) {
            loginIssueCallback.accept(false);
            return; // there was a successfully login
        }

        if (!Network.isConnected()) {
            loginIssueCallback.accept(true);
            return;
        }

        // We are probably not yet ready. Log in and wait a bit...
        startBackgroundLogin(updateUserInfoHandler);
        scheduler.postDelayed(() -> {
            loginSuccessful = anyConnectorLoggedIn();
            loginIssueCallback.accept(!loginSuccessful);
        }, 10000);
    }

    private static void startBackgroundLogin(@Nullable final Handler updateUserInfoHandler) {

        final ILogin[] loginConns = ConnectorFactory.getActiveLiveConnectors();

        //ensure that login is not done while another login is still in progress
        synchronized (LOGINS_IN_PROGRESS) {
            if (LOGINS_IN_PROGRESS.get() > 0) {
                return;
            }
            LOGINS_IN_PROGRESS.set(loginConns.length);
        }

        final boolean mustLogin = ConnectorFactory.mustRelog();

        for (final ILogin conn : loginConns) {
            if (mustLogin || !conn.isLoggedIn()) {
                AndroidRxUtils.networkScheduler.scheduleDirect(() -> {
                    if (mustLogin) {
                        // Properly log out from geocaching.com
                        conn.logout();
                    }
                    conn.login();

                    LOGINS_IN_PROGRESS.addAndGet(-1);

                    if (updateUserInfoHandler != null) {
                        updateUserInfoHandler.sendEmptyMessage(-1);
                    }
                });
            }
        }
    }
}