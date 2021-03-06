/**
 *     Copyright (C) 2009 Anders Aagaard <aagaande@gmail.com>
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.neuron.trafikanten.views.map;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.AnalyticsUtils;
import com.google.android.FixedMyLocationOverlay;
import com.google.android.TransparentPanel;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Overlay;
import com.neuron.trafikanten.R;
import com.neuron.trafikanten.dataProviders.IGenericProviderHandler;
import com.neuron.trafikanten.dataProviders.trafikanten.TrafikantenTrip;
import com.neuron.trafikanten.dataSets.RouteData;
import com.neuron.trafikanten.dataSets.StationData;
import com.neuron.trafikanten.db.FavoriteDbAdapter;

public class GenericMap extends MapActivity {
	private final static String TAG = "Trafikanten-Map";
	private static final int DIALOG_LIST = Menu.FIRST;
	private MyLocationOverlay locationOverlay;
	private static GenericStationOverlay stationOverlay;
	private static ViewHolder viewHolder = new ViewHolder();
	private FavoriteDbAdapter favoriteDbAdapter = null;
	private Drawable iconMapMarker = null;
	
	/*
	 * For route loading:
	 */
	private ArrayList<RouteData> routeList;
	private int routeLength = 0;
	private TrafikantenTrip tripProvider = null;
	
	/*
	 * Holder for currently selected station in panel
	 */
	static private StationData selectedStation;
	
	/*
	 * Options menu items
	 */
	private final static int MYLOCATION_ID = Menu.FIRST;
	
	/*
	 * Variables cached locally for performance
	 */
	private MapView mapView;
	
	public static void Show(Activity activity, @SuppressWarnings("rawtypes") ArrayList stationList, boolean isRoute, int what) {
		Intent intent = new Intent(activity, GenericMap.class);
		if (isRoute) {
			intent.putExtra(RouteData.PARCELABLE, stationList);
		} else {
			intent.putExtra(StationData.PARCELABLE, stationList);
		}
		activity.startActivityForResult(intent, what);
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_PROGRESS);
		setContentView(R.layout.map);
		favoriteDbAdapter = new FavoriteDbAdapter(this);
		iconMapMarker = getResources().getDrawable(R.drawable.icon_mapmarker);
		
		AnalyticsUtils.getInstance(this).trackPageView("/map");
		
		/*
		 * Setup map view
		 */
		String apiKey = "0C4n0QlD7VKWny63h-bygLe8DF4bhWdnxCYYhNA";
		try {
			if (new File("/sdcard/trafikanten.debug").exists()) {
				apiKey = "0C4n0QlD7VKUBXEwAaDHHuGHIpMavSe5zbT27AQ";
			}
		} catch (Exception e) {
			// We don't care if this fails.
		}
			
		mapView = new MapView(this, apiKey);
		mapView.setBuiltInZoomControls(true);
		mapView.setClickable(true);
		
		final RelativeLayout relativeLayout = (RelativeLayout) findViewById(R.id.view);
		final View panel = LayoutInflater.from(this).inflate(R.layout.map_overlay, null);
		relativeLayout.addView(mapView);
		relativeLayout.addView(panel);

		viewHolder.list = (ImageButton) findViewById(R.id.list);
		viewHolder.infoPanel = (TransparentPanel) findViewById(R.id.infoPanel);
		viewHolder.infoPanel.setVisibility(View.GONE);
		viewHolder.name = (TextView) findViewById(R.id.name);
		viewHolder.information = (TextView) findViewById(R.id.information);
		viewHolder.select = (ImageButton) findViewById(R.id.select);
		
		/*
		 * Setup onClick handler for list button
		 */
		viewHolder.list.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				showDialog(DIALOG_LIST);
			}
		});
		
		/*
		 * Setup onClick handler for select station button
		 */
		viewHolder.select.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent();
				intent.putExtra(StationData.PARCELABLE, selectedStation);
				setResult(RESULT_OK, intent);
				finish();
			}
			
		});
		
		/*
		 * Setup overlays
		 */
        final List<Overlay> overlays = mapView.getOverlays();
		stationOverlay = new GenericStationOverlay(iconMapMarker);
		
		/*
		 * Load stations
		 */
		if (savedInstanceState == null) {
			final Bundle bundle = getIntent().getExtras();
			if (bundle.containsKey(StationData.PARCELABLE)) {
				/*
				 * Load stations passed to us
				 */
				final ArrayList<StationData> stationList = bundle.getParcelableArrayList(StationData.PARCELABLE);
				favoriteDbAdapter.refreshFavorites(stationList);
				stationOverlay.add(this, stationList);
				overlays.add(stationOverlay);
			} else if (bundle.containsKey(RouteData.PARCELABLE)) {
				/*
				 * Load stations passed to us
				 */
				routeList = bundle.getParcelableArrayList(RouteData.PARCELABLE);
				routeLength = routeList.size() + 1;
				setProgress(0);
				loadRouteData();
				
				/*
				 * Hide select station button, it does nothing in this view
				 */
				final View panelSelectStationButton = panel.findViewById(R.id.select);
				panelSelectStationButton.setVisibility(View.GONE);
			}
		} else {
			// TODO : saveInstanceState in mapview.
			finish();
		}

		/*
         * Add MyLocationOverlay so we can see where we are.
         */
		locationOverlay = new FixedMyLocationOverlay(this, mapView);

		boolean animateToGpsPosition = true;
        if (animateToGpsPosition) {
        	locationOverlay.runOnFirstFix(new Runnable() {
	            public void run() {
	            	mapView.getController().animateTo(locationOverlay.getMyLocation());
	            }
	        });
        }
        
        /*
         * Add all overlays to the overlay list
         */
        overlays.add(locationOverlay);
	}
	
	/*
	 * This takes routeList[0] and deals with it until routeList.size == 0, then it add the overlay and stops.
	 */
	private void loadRouteData() {
		if (routeList.size() == 0) {
			/*
			 * Activate routeOverlay and add it to the map.
			 */
			RouteOverlay routeOverlay = new RouteOverlay(iconMapMarker, stationOverlay.items);
			final List<Overlay> overlays = mapView.getOverlays();
			overlays.add(routeOverlay);
			overlays.add(stationOverlay);
			setProgress(10000);
			mapView.invalidate();
			return;
		}

		final RouteData routeData = routeList.get(0);
		routeList.remove(0);
		setProgress((routeLength - routeList.size() + 1) * 10000 / routeLength);
		
		if (routeData.tourID == 0) {
			/*
			 * We got no tour id, just add the stations directly.
			 */
			stationOverlay.add(GenericMap.this, routeData.fromStation, false, routeData.transportType);
			stationOverlay.add(GenericMap.this, routeData.toStation, true, routeData.transportType);
			loadRouteData();
			return;
		}
		
		/*
		 * We have a tour id, lets get our list.
		 */
		AnalyticsUtils.getInstance(this).trackPageView("/map/showTrip");
		tripProvider = new TrafikantenTrip(this, routeData.tourID, routeData.fromStation.stationId, routeData.toStation.stationId, new IGenericProviderHandler<StationData>() {
			@Override
			public void onData(StationData data) {
				stationOverlay.add(GenericMap.this, data, true, routeData.transportType);
			}

			@Override
			public void onExtra(int i, Object data) {}

			@Override
			public void onPostExecute(Exception exception) {
				tripProvider = null; 
				if (exception != null) {
					Log.w(TAG,"onException " + exception);
		        	Toast.makeText(GenericMap.this, getText(R.string.trafikantenErrorOther), Toast.LENGTH_SHORT).show();
		        	setProgress(10000);
				} else {
					loadRouteData();
				}
			}

			@Override
			public void onPreExecute() {}
		});
	}
	
	static public void onStationTap(StationData station) {
		viewHolder.infoPanel.setVisibility(View.VISIBLE);
		viewHolder.name.setText(station.stopName);
		viewHolder.information.setText(station.extra);
		selectedStation = station;
	}

	
	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		switch(id) {
		case DIALOG_LIST:
			/*
			 * Dialog contains a list, force recreating it.
			 */
			removeDialog(DIALOG_LIST);
			dialog = onCreateDialog(DIALOG_LIST);
			break;
		}
		super.onPrepareDialog(id, dialog);
	}
	
	/*
	 * Creating dialogs
	 * @see android.app.Activity#onCreateDialog(int)
	 */
	@Override
	protected Dialog onCreateDialog(int id) {
		switch(id) {
		case DIALOG_LIST:
			/*
			 * TODO : If stationOverlay items are dynamic, and can be updated while if the view is open, this will need to be converted to prepareDialog
			 */
			AlertDialog.Builder builder = new AlertDialog.Builder(GenericMap.this);
			builder.setTitle(getText(R.string.selectStation));
			String[] items = new String[stationOverlay.size()];
			for (int i = 0; i < stationOverlay.size(); i++) {
				items[i] = stationOverlay.getItem(i).station.stopName;
			}
			builder.setItems(items, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					final StationOverlayItem stationItem = stationOverlay.getItem(which);
					mapView.getController().animateTo(stationItem.getPoint());
					onStationTap(stationItem.station);
				}
			});
			return builder.create(); 
		}
		return super.onCreateDialog(id);
	}

	
	
	/*
	 * Setup options menu (available on menu button)
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		final MenuItem myLocation = menu.add(0, MYLOCATION_ID, 0, R.string.myLocation);
		myLocation.setIcon(android.R.drawable.ic_menu_mylocation);
		return true;
	}

	/*
	 * Options menu item selected (available on menu button)
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
        case MYLOCATION_ID:
        	final GeoPoint point = locationOverlay.getMyLocation();
        	if (point == null) {
        		Toast.makeText(this, R.string.noLocationFound, Toast.LENGTH_SHORT).show();
        	} else {
        		mapView.getController().animateTo(locationOverlay.getMyLocation());
        	}
        	break;
        }
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}

	@Override
	protected void onPause() {
		locationOverlay.disableMyLocation();
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		locationOverlay.enableMyLocation();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		// TODO Auto-generated method stub
		super.onSaveInstanceState(outState);
	}
	
	
	@Override
	protected void onStop() {
		favoriteDbAdapter.close();
		if (tripProvider != null) {
			tripProvider.kill();
		}
		super.onStop();
	}
	
	static class ViewHolder {
		ImageButton list;
		
		TransparentPanel infoPanel;
		TextView name;
		TextView information;
		ImageButton select;
	}
}
