package cgeo.geocaching.maps.interfaces;

import cgeo.geocaching.maps.MapSettingsUtils;
import cgeo.geocaching.utils.IndividualRouteUtils;
import cgeo.geocaching.utils.TrackUtils;

import android.content.res.Resources;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Defines the common functions of the provider-specific
 * MapActivity implementations.
 */
public interface MapActivityImpl {

    Resources getResources();

    AppCompatActivity getActivity();

    void superOnCreate(Bundle savedInstanceState);

    void superOnResume();

    void superOnDestroy();

    void superOnStart();

    void superOnStop();

    void superOnPause();

    boolean superOnCreateOptionsMenu(Menu menu);

    boolean superOnPrepareOptionsMenu(Menu menu);

    boolean superOnOptionsItemSelected(MenuItem item);

    TrackUtils getTrackUtils();

    IndividualRouteUtils getIndividualRouteUtils();

    MapSettingsUtils getMapSettingsUtils();

    /**
     * called from the pseudo actionbar layout
     */
    void navigateUp(View view);
}
