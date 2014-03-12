package de.geeksfactory.opacclient.frontend;

import org.acra.ACRA;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import de.geeksfactory.opacclient.NotReachableException;
import de.geeksfactory.opacclient.OpacClient;
import de.geeksfactory.opacclient.OpacTask;
import de.geeksfactory.opacclient.R;
import de.geeksfactory.opacclient.objects.SearchRequestResult;

/**
 * An activity representing a list of SearchResults. This activity has different
 * presentations for handset and tablet-size devices. On handsets, the activity
 * presents a list of items, which when touched, lead to a
 * {@link SearchResultDetailActivity} representing item details. On tablets, the
 * activity presents the list of items and item details side-by-side using two
 * vertical panes.
 * <p>
 * The activity makes heavy use of fragments. The list of items is a
 * {@link SearchResultListFragment} and the item details (if present) is a
 * {@link SearchResultDetailFragment}.
 * <p>
 * This activity also implements the required
 * {@link SearchResultListFragment.Callbacks} interface to listen for item
 * selections.
 */
public class SearchResultListActivity extends OpacActivity implements
		SearchResultListFragment.Callbacks, SearchResultDetailFragment.Callbacks {

	/**
	 * Whether or not the activity is in two-pane mode, i.e. running on a tablet
	 * device.
	 */
	private boolean mTwoPane;
	
	protected SearchRequestResult searchresult;
	private SparseArray<SearchRequestResult> cache = new SparseArray<SearchRequestResult>();
	private int page;

	private SearchStartTask st;
	private SearchPageTask sst;
	
	private SearchResultListFragment listFragment;
	private SearchResultDetailFragment detailFragment;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Show the Up button in the action bar.
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		
		listFragment = (SearchResultListFragment) getSupportFragmentManager()
				.findFragmentById(R.id.searchresult_list);

		if (findViewById(R.id.searchresult_detail_container) != null) {
			// The detail container view will be present only in the
			// large-screen layouts (res/values-large and
			// res/values-sw600dp). If this view is present, then the
			// activity should be in two-pane mode.
			mTwoPane = true;

			// In two-pane mode, list items should be given the
			// 'activated' state when touched.
			listFragment.setActivateOnItemClick(true);
		}
		
		page = 1;

		if (savedInstanceState == null)
			performsearch();
	}
	
	public void performsearch() {
		if (page == 1) {
			st = new SearchStartTask();
			st.execute(app, getIntent().getBundleExtra("query"));
		} else {
			sst = new SearchPageTask();
			sst.execute(app, page);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() ==  android.R.id.home) {
			// This ID represents the Home or Up button. In the case of this
			// activity, the Up button is shown. Use NavUtils to allow users
			// to navigate up one level in the application structure. For
			// more details, see the Navigation pattern on Android Design:
			//
			// http://developer.android.com/design/patterns/navigation.html#up-vs-back
			//
			NavUtils.navigateUpFromSameTask(this);
			return true;
		} else if (item.getItemId() == R.id.action_prev) {
			listFragment.setListShown(false);
			if (sst != null) {
				sst.cancel(false);
			}
			page--;
			if (cache.get(page) != null) {
				searchresult = cache.get(page);
				loaded();
			} else {
				searchresult = null;
				sst = new SearchPageTask();
				sst.execute(app, page);
			}
			supportInvalidateOptionsMenu();
			return true;
		} else if (item.getItemId() == R.id.action_next) {
			listFragment.setListShown(false);
			if (sst != null) {
				sst.cancel(false);
			}
			page++;
			if (cache.get(page) != null) {
				searchresult = cache.get(page);
				loaded();
			} else {
				searchresult = null;
				sst = new SearchPageTask();
				sst.execute(app, page);
			}
			supportInvalidateOptionsMenu();
			return true;
		} else if (item.getItemId() == android.R.id.home) {
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater mi = new MenuInflater(this);
		mi.inflate(R.menu.activity_search_results, menu);

		if (page == 1) {
			menu.findItem(R.id.action_prev).setVisible(false);
		} else {

			menu.findItem(R.id.action_prev).setVisible(true);
		}

		return super.onCreateOptionsMenu(menu);
	}

	/**
	 * Callback method from {@link SearchResultListFragment.Callbacks}
	 * indicating that the item with the given ID was selected.
	 */
	@Override
	public void onItemSelected(int nr, String id) {
		if (mTwoPane) {
			// In two-pane mode, show the detail view in this activity by
			// adding or replacing the detail fragment using a
			// fragment transaction.
			Bundle arguments = new Bundle();
			arguments.putInt(SearchResultDetailFragment.ARG_ITEM_NR, nr);
			if(id != null)
				arguments.putString(SearchResultDetailFragment.ARG_ITEM_ID, id);
			detailFragment = new SearchResultDetailFragment();
			detailFragment.setArguments(arguments);
			getSupportFragmentManager().beginTransaction()
					.replace(R.id.searchresult_detail_container, detailFragment)
					.commit();

		} else {
			// In single-pane mode, simply start the detail activity
			// for the selected item ID.
			Intent detailIntent = new Intent(this,
					SearchResultDetailActivity.class);
			detailIntent.putExtra(SearchResultDetailFragment.ARG_ITEM_NR, nr);
			if(id != null)
				detailIntent.putExtra(SearchResultDetailFragment.ARG_ITEM_ID, id);
			startActivity(detailIntent);
		}
	}
	
	public class SearchStartTask extends OpacTask<SearchRequestResult> {
		protected boolean success;
		protected Exception exception;

		@Override
		protected SearchRequestResult doInBackground(Object... arg0) {
			super.doInBackground(arg0);
			Bundle query = (Bundle) arg0[1];

			try {
				SearchRequestResult res = app.getApi().search(query);
				//Load cover images, if search worked and covers available
				success = true;
				return res;
			} catch (java.net.UnknownHostException e) {
				success = false;
				exception = e;
				e.printStackTrace();
			} catch (java.net.SocketException e) {
				success = false;
				exception = e;
			} catch (Exception e) {
				exception = e;
				ACRA.getErrorReporter().handleException(e);
				success = false;
			}

			return null;
		}

		@Override
		protected void onPostExecute(SearchRequestResult result) {
			if (success) {
				if (result == null) {

					if (app.getApi().getLast_error().equals("is_a_redirect")) {
						// Some libraries (SISIS) do not show a result list if only one result
						// is found but instead directly show the result details.
						Intent intent = new Intent(SearchResultListActivity.this,
								SearchResultDetailActivity.class);
						intent.putExtra(SearchResultDetailFragment.ARG_ITEM_ID, (String) null);
						startActivity(intent);
						finish();
						return;
					}

					listFragment.showConnectivityError(app.getApi().getLast_error());
				} else {
					searchresult = result;
					if (searchresult != null) {
						if (searchresult.getResults().size() > 0) {
							if (searchresult.getResults().get(0).getId() != null)
								cache.put(page, searchresult);
						}
					}
					loaded();
				}
			} else {				
				if (exception != null
						&& exception instanceof NotReachableException)
					listFragment.showConnectivityError(getResources().getString(R.string.connection_error_detail_nre));
				else
					listFragment.showConnectivityError();
			}
		}
	}
	
	public class SearchPageTask extends SearchStartTask {

		@Override
		protected SearchRequestResult doInBackground(Object... arg0) {
			OpacClient app = (OpacClient) arg0[0];
			Integer page = (Integer) arg0[1];

			try {
				SearchRequestResult res = app.getApi().searchGetPage(page);
				//Load cover images, if search worked and covers available
				success = true;
				return res;
			} catch (java.net.UnknownHostException e) {
				success = false;
				exception = e;
				e.printStackTrace();
			} catch (java.net.SocketException e) {
				success = false;
				exception = e;
			} catch (Exception e) {
				exception = e;
				ACRA.getErrorReporter().handleException(e);
				success = false;
			}

			return null;
		}
	}

	protected void loaded() {		
		listFragment.setListShown(true);
		listFragment.setSearchResult(searchresult);
	}

	@Override
	protected int getContentView() {
		return R.layout.activity_searchresult_list;
	}

	@Override
	public void removeFragment() {
		getSupportFragmentManager().beginTransaction().remove(detailFragment).commit();
	}

	@Override
	public void reload() {
		performsearch();
	}
}
