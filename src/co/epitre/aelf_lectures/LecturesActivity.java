package co.epitre.aelf_lectures;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;


import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.RelativeLayout;

import co.epitre.aelf_lectures.data.LectureItem;
import co.epitre.aelf_lectures.data.LecturesController;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

class WhatWhen {
    public LecturesController.WHAT what;
    public GregorianCalendar when;
    public int position;
}

public class LecturesActivity extends SherlockFragmentActivity implements DatePickerFragment.CalendarDialogListener,
                                                                  ActionBar.OnNavigationListener {

    public static final String PREFS_NAME = "aelf-prefs";

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link android.support.v4.app.FragmentPagerAdapter} derivative, which
     * will keep every loaded fragment in memory. If this becomes too memory
     * intensive, it may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    LecturesController lecturesCtrl = null;
    SectionsPagerAdapterFragment mSectionsPagerAdapter;
    WhatWhen whatwhen;
    Menu mMenu;

    List<LectureItem> networkError = new ArrayList<LectureItem>(1);

    /**
     * Sync account related vars
     */
    // The authority for the sync adapter's content provider
    public static final String AUTHORITY = "co.epitre.aelf"; // DANGER: must be the same as the provider's in the manifest
    // An account type, in the form of a domain name
    public static final String ACCOUNT_TYPE = "epitre.co";
    // The account name
    public static final String ACCOUNT = "www.aelf.org";
    // Sync interval in s. ~ 1 Day
    public static final long SYNC_INTERVAL = 60L*60L*22L;
    // Instance fields
    Account mAccount;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    ViewPager mViewPager;

    // TODO: detect first launch + trigger initial sync

    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);

    	// ---- need upgrade ?
    	int currentVersion, savedVersion;

    	// current version
    	PackageInfo packageInfo;
    	try {
    		packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
    	} catch (NameNotFoundException e) {
    		throw new RuntimeException("Could not determine current version");
    	}
    	currentVersion = packageInfo.versionCode;

    	// load saved version, if any
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        savedVersion = settings.getInt("version", -1);

    	// upgrade logic, primitive at the moment...
    	if(savedVersion != currentVersion) {
    		if(savedVersion < 5) {
    			// delete cache DB: needs to force regenerate
    			getApplicationContext().deleteDatabase("aelf_cache.db");
    		}

    		// update saved version
    		SharedPreferences.Editor editor = settings.edit();
    		editor.putInt("version", currentVersion);
    		editor.commit();
    	}
    	// ---- end upgrade

    	// create dummy account for our background sync engine
    	mAccount = CreateSyncAccount(this);

    	// init the lecture controller
    	lecturesCtrl = LecturesController.getInstance(this);

    	// load the "lectures" for today
    	whatwhen = new WhatWhen();
    	whatwhen.when = new GregorianCalendar();
    	whatwhen.what = LecturesController.WHAT.MESSE;
    	whatwhen.position = -1; // for mass, load gospel first

    	// error handler
    	networkError.add(new LectureItem("Erreur Réseau", "<p>Connexion au serveur AELF impossible<br />Veuillez ré-essayer plus tard.</p>", "erreur", -1));

    	// some UI. Most UI init are done in the prev async task
    	setContentView(R.layout.activity_lectures);

    	// Spinner
        ActionBar actionBar = getSupportActionBar();

        Context context = actionBar.getThemedContext();
        ArrayAdapter<CharSequence> list = ArrayAdapter.createFromResource(context, R.array.spinner, R.layout.sherlock_spinner_item);
        list.setDropDownViewResource(R.layout.sherlock_spinner_dropdown_item);

        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        actionBar.setListNavigationCallbacks(list, this);

    	// finally, turn on periodic lectures caching
    	if(mAccount != null) {
    		ContentResolver.setIsSyncable(mAccount, AUTHORITY, 1);
    		ContentResolver.setSyncAutomatically(mAccount, AUTHORITY, true);
    		ContentResolver.addPeriodicSync(mAccount, AUTHORITY, new Bundle(1), SYNC_INTERVAL);
    	}
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
    	// Save instance state. Especially useful on screen rotate on older phones
    	// - what --> mass, tierce, ... ? (id)
    	// - when --> date (timestamp)
    	// - position --> active tab (id)
    	super.onSaveInstanceState(outState);

    	outState.putInt("what", whatwhen.what.getPosition());
    	outState.putLong("when", whatwhen.when.getTimeInMillis());
    	outState.putInt("position", mViewPager.getCurrentItem());
    }

    @Override
    protected void onRestoreInstanceState (Bundle savedInstanceState) {
    	// Restore saved instance state. Especially useful on screen rotate on older phones
    	// - what --> mass, tierce, ... ? (id)
    	// - when --> date (timestamp)
    	// - position --> active tab (id)
    	super.onRestoreInstanceState(savedInstanceState);

        // load state
        whatwhen.what = LecturesController.WHAT.values()[savedInstanceState.getInt("what")];
        whatwhen.when.setTimeInMillis(savedInstanceState.getLong("when"));
        whatwhen.position = savedInstanceState.getInt("position");

        // update UI
        ActionBar actionBar = getSupportActionBar();
        actionBar.setSelectedNavigationItem(whatwhen.what.getPosition());

        // nota: date UI is updated on menu creation
    	// nota: data reload + move to position is triggered by spinner callback

    	// FIXME: factorize date UI
    	// TODO: tab

    }

    public boolean onAbout(MenuItem item) {
    	AboutDialogFragment aboutDialog = new AboutDialogFragment();
    	aboutDialog.show(getSupportFragmentManager(), "aboutDialog");
    	return true;
    }

    public boolean onSyncPref(MenuItem item) {
    	Intent intent = new Intent(this, SyncPrefActivity.class);
    	startActivity(intent);
    	return true;
    }

    public boolean onSyncDo(MenuItem item) {
        // Pass the settings flags by inserting them in a bundle
        Bundle settingsBundle = new Bundle();
        settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);

        // start sync
        ContentResolver.requestSync(mAccount, AUTHORITY, settingsBundle);

        // done
    	return true;
    }

    public boolean onCalendar(MenuItem item) {
    	Bundle args = new Bundle();
    	args.putLong("time", whatwhen.when.getTimeInMillis());

    	DatePickerFragment calendarDialog = new DatePickerFragment();
    	calendarDialog.setArguments(args);
        calendarDialog.show(getSupportFragmentManager(), "datePicker");

        return true;
    }

    @SuppressLint("SimpleDateFormat") // I know but currently French only
    public void onCalendarDialogPicked(int year, int month, int day) {
    	whatwhen.when = new GregorianCalendar(year, month, day);
    	whatwhen.position = mViewPager.getCurrentItem(); // keep on the same reading on date change
    	new DownloadXmlTask().execute(whatwhen);

    	// Update to date button with "this.date"
    	MenuItem calendarItem = mMenu.findItem(R.id.action_calendar);
    	SimpleDateFormat actionDateFormat = new SimpleDateFormat("E d MMM y"); //TODO: move str to cst
    	calendarItem.setTitle(actionDateFormat.format(whatwhen.when.getTime()));
    }

    @Override
    public boolean onNavigationItemSelected(int position, long itemId) {
    	// Are we actually *changing* ? --> maybe not if coming from state reload
    	if(whatwhen.what != LecturesController.WHAT.values()[position]) {
    		whatwhen.what = LecturesController.WHAT.values()[position];
    		whatwhen.position = (whatwhen.what == LecturesController.WHAT.MESSE) ? -1:0; // on what change, move to last for mass, 1st for others
    	}
    	new DownloadXmlTask().execute(whatwhen);
    	return true;
    }

    @SuppressLint("SimpleDateFormat") // I know but currently French only
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	// Inflate the menu; this adds items to the action bar
    	getSupportMenuInflater().inflate(R.menu.lectures, menu);

    	// Update to date button with "this.date"
    	MenuItem calendarItem = menu.findItem(R.id.action_calendar);
    	SimpleDateFormat actionDateFormat = new SimpleDateFormat("E d MMM y"); //TODO: move str to cst
    	calendarItem.setTitle(actionDateFormat.format(whatwhen.when.getTime()));

    	mMenu = menu;

    	return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.action_about:
            return onAbout(item);
        case R.id.action_sync_settings:
        	return onSyncPref(item);
        case R.id.action_sync_do:
        	return onSyncDo(item);
        case R.id.action_calendar:
            return onCalendar(item);
        }
        return true;
    }

    /**
     * Create a new dummy account for the sync adapter
     *
     * @param context The application context
     */
    public static Account CreateSyncAccount(Context context) {
    	// Create the account type and default account
    	Account newAccount = new Account(ACCOUNT, ACCOUNT_TYPE);
    	// Get an instance of the Android account manager
    	AccountManager accountManager = (AccountManager) context.getSystemService(ACCOUNT_SERVICE);
    	/*
    	 * Add the account and account type, no password or user data
    	 * If successful, return the Account object, otherwise report an error.
    	 */
    	if (accountManager.addAccountExplicitly(newAccount, null, null)) {
    		return newAccount;
    	} else {
    		return accountManager.getAccountsByType(ACCOUNT_TYPE)[0];
    	}
    }

    protected void setLoading(final boolean loading) {
    	final RelativeLayout loadingOverlay = (RelativeLayout)findViewById(R.id.loadingOverlay);

    	loadingOverlay.post(new Runnable() {
    		public void run() {
    			if(loading) {
    				loadingOverlay.setVisibility(View.VISIBLE);
    			} else {
    				loadingOverlay.setVisibility(View.INVISIBLE);
    			}
    	    }
    	});
    }

    // Async loader
    private class DownloadXmlTask extends AsyncTask<WhatWhen, Void, List<LectureItem>> {
    	@Override
    	protected List<LectureItem> doInBackground(WhatWhen... whatwhen) {
    		WhatWhen ww = whatwhen[0];

    		try {
    			// attempt to load from cache: skip loading indicator (avoids flickering)
    			List<LectureItem> lectures = lecturesCtrl.getLectures(ww.what, ww.when, true);
    			if(lectures != null) return lectures;

    			// attempts to load from network, with loading indicator
    			setLoading(true);
    			return lecturesCtrl.getLectures(ww.what, ww.when, false);
    		} catch (IOException e) {
    			// TODO print error message
    			setLoading(false);
    			e.printStackTrace();
    			return null;
    		}
    	}

    	@Override
    	protected void onPostExecute(List<LectureItem> lectures) {
    		// Create the adapter that will return a fragment for each of the three
    		// primary sections of the app.
    		if(lectures == null) {
    			lectures = networkError;
    		}
    		mSectionsPagerAdapter = new SectionsPagerAdapterFragment(getSupportFragmentManager(), lectures);

    		// Set up the ViewPager with the sections adapter.
    		mViewPager = (ViewPager) findViewById(R.id.pager);
    		mViewPager.setAdapter(mSectionsPagerAdapter);
    		int start = (whatwhen.position < 0)?lectures.size():0;
    		mViewPager.setCurrentItem(start + whatwhen.position);

    		setLoading(false);
    	}
    }

}
