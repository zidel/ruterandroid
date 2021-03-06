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

package com.neuron.trafikanten.dataSets;

import java.util.ArrayList;

import android.os.Parcel;
import android.os.Parcelable;

public class NotificationData  implements Parcelable {
	public final static String PARCELABLE = "NotificationData";
	
	/*
	 * Realtime notification:
	 */
	public StationData station;
	public RealtimeData departureInfo;
	
	/*
	 * Route notification:
	 */
	public ArrayList<RouteProposal> routeProposalList;
	public int proposalPosition;
	public long routeDeparture;
	public RouteDeviData deviList;
	
	/*
	 * Shared:
	 */
	public long notifyTime;
	public String with; // What transport we're traveling with.
	
	
	public NotificationData(StationData station, RealtimeData departureInfo, long notifyTime, String with) {
		this.station = station;
		this.departureInfo = departureInfo;
		this.notifyTime = notifyTime;
		this.with = with;
	}
	
	public NotificationData(ArrayList<RouteProposal> routeProposalList, int proposalPosition, RouteDeviData deviList, long departure, long notifyTime, String with) {
		this.routeProposalList = routeProposalList;
		this.proposalPosition = proposalPosition;
		this.deviList = deviList;
		routeDeparture = departure;
		this.notifyTime = notifyTime;
		this.with = with;
	}
	
	/*
	 * Helper functions:
	 */
	public long getDepartureTime() {
		return departureInfo != null ? departureInfo.expectedDeparture : routeDeparture;
	}
	public String getStopName() {
		return station != null ? station.stopName : routeProposalList.get(proposalPosition).travelStageList.get(0).fromStation.stopName;
	}
	
	/*
	 * @see android.os.Parcelable
	 */
	@Override
	public int describeContents() {	return 0; }
	
	/*
	 * Function for reading the parcel
	 */
	public NotificationData(Parcel in) {
		notifyTime = in.readLong();
		with = in.readString();
		
		routeDeparture = in.readLong();
		routeProposalList = new ArrayList<RouteProposal>();
		in.readTypedList(routeProposalList, RouteProposal.CREATOR);
		proposalPosition = in.readInt();
		deviList = in.readParcelable(RouteDeviData.class.getClassLoader());
		
		station = in.readParcelable(StationData.class.getClassLoader());
		departureInfo = in.readParcelable(RealtimeData.class.getClassLoader());
	}
	
	/*
	 * Writing current data to parcel.
	 * @see android.os.Parcelable#writeToParcel(android.os.Parcel, int)
	 */
	@Override
	public void writeToParcel(Parcel out, int flags) {
		out.writeLong(notifyTime);
		out.writeString(with);
		
		out.writeLong(routeDeparture);
		out.writeTypedList(routeProposalList);
		out.writeInt(proposalPosition);
		out.writeParcelable(deviList, 0);
		
		out.writeParcelable(station, 0);
		out.writeParcelable(departureInfo, 0);
	}
	
	/*
	 * Used for bundle.getParcel 
	 */
    public static final Parcelable.Creator<NotificationData> CREATOR = new Parcelable.Creator<NotificationData>() {
		public NotificationData createFromParcel(Parcel in) {
		    return new NotificationData(in);
		}
		
		public NotificationData[] newArray(int size) {
		    return new NotificationData[size];
		}
	};
}
