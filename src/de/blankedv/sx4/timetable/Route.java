/*
SX4
Copyright (C) 2019 Michael Blank

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package de.blankedv.sx4.timetable;

import static com.esotericsoftware.minlog.Log.*;
import static de.blankedv.sx4.Constants.*;
import de.blankedv.sx4.LanbahnData;
import de.blankedv.sx4.SXData;
import de.blankedv.sx4.SXUtils;
import static de.blankedv.sx4.timetable.Vars.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Class Route stores a complete route, which contains sensors, signals and
 * turnouts. Offending allRoutes are calculated automatically (defined as all
 * allRoutes which also set one of the turnouts). In addition offending
 * allRoutes can also be defined in the config file (needed for crossing
 * allRoutes, which cannot be found automatically)
 *
 * adapted from lanbahnpanel (android software)
 *
 * @author mblank
 *
 */
public class Route extends PanelElement {

    String routeString = "";
    String sensorsString = "";
    String offendingString = ""; // comma separated list of adr's of offending
    // allRoutes

    // sensors turnout activate for the display of this route
    private ArrayList<PanelElement> rtSensors = new ArrayList<>();
    public PanelElement endSensor = null;   // used for auto- "route completed" detection
    // NOT used in compound routes.

    // signals of this route
    private ArrayList<RouteSignal> rtSignals = new ArrayList<>();

    // turnouts of this route
    private ArrayList<RouteTurnout> rtTurnouts = new ArrayList<>();

    // offending allRoutes
    private ArrayList<Route> rtOffending = new ArrayList<>();

    private long clearRouteTime = Long.MAX_VALUE;  // i.e. => never, if not set
    private boolean automaticFlag = false;

    /**
     * constructs a route
     *
     * @param routeAddr unique identifier (int)
     * @param route string for route setting like "770,1;720,2"
     * @param allSensors string for sensors like "2000,2001,2002"
     * @param offending string with offending allRoutes, separated by comma
     */
    public Route(int routeAddr, String route, String allSensors,
            String offending) {

        super("RT", routeAddr);
        state = RT_INACTIVE;
        // these strings are written back to config file.
        this.routeString = route;
        this.sensorsString = allSensors;
        this.offendingString = offending;

        // route = "750,1;751,2" => set 750 turnout 1 and 751 turnout value 2
        String[] routeElements = route.split(";");
        for (String routeElement : routeElements) {
            String[] reInfo = routeElement.split(",");
            PanelElement pe = PanelElement.getByAddress(Integer.parseInt(reInfo[0]));
            // if this is a signal, then add to my signal list "rtSignals"
            if (pe != null) {
                if (pe.isSignal()) {
                    if (reInfo.length == 3) {  // route signal with dependency
                        rtSignals.add(new RouteSignal(pe,
                                Integer.parseInt(reInfo[1]),
                                Integer.parseInt(reInfo[2])));
                        //  debug("RT, add sig(dep) " + pe.getAdr());
                    } else {
                        rtSignals.add(new RouteSignal(pe, Integer
                                .parseInt(reInfo[1])));
                        //  debug("RT, add sig  " + pe.getAdr());
                    }

                } else if (pe.isTurnout()) {
                    rtTurnouts.add(new RouteTurnout(pe,
                            Integer.parseInt(reInfo[1])));
                    //  debug("RT, add turnout " + pe.getAdr());
                }
            }
        }

        // format for sensors: just a list of addresses, seperated by comma ","
        String[] sensorAddresses = allSensors.split(",");
        for (String sensorAddress : sensorAddresses) {
            // add the matching elements turnout sensors list
            for (PanelElement pe : panelElements) {
                if (pe.isSensor()) {
                    if (pe.getAdr() == Integer.parseInt(sensorAddress)) {
                        rtSensors.add(pe);
                        if (CFG_DEBUG) {
                            debug("RT, add sensor " + pe.getAdr());
                        }
                    }
                }
            }
        }
        if (CFG_DEBUG) {
            debug("creating route id/adr=" + adr + " - " + rtSignals.size() + " signals/" + rtTurnouts.size() + " turnouts/" + rtSensors.size() + " sensors");
        }

        String[] offRoutes = offendingString.split(",");
        for (String offRoute : offRoutes) {
            for (Route rt : allRoutes) {
                try {
                    int offID = Integer.parseInt(offRoute);
                    if ((rt.getAdr() == offID) && (rt.getState() == RT_ACTIVE)) {
                        rtOffending.add(rt);
                        // (debug)("RT, add off. rt " + rt.getAdr());
                    }
                }catch (NumberFormatException e) {
                }
            }
        }
        //	if (DEBUG)
        //		Log.d(TAG, rtOffending.size() + " offending allRoutes in config");
    }

    public void clear() {
        clearRouteTime = Long.MAX_VALUE;
        // i.e. => never, if not set automatically
        debug("clearing route id=" + this.getAdr());

        // deactivate sensors
        for (PanelElement se : rtSensors) {
            se.setInRoute(false);
            debug("clearing sensor=" + se.getAdr());
            // reset trainNumber data for all but last sensor
            if (se != rtSensors.get(rtSensors.size() - 1)) {
                se.setTrain(0);
            }
            LanbahnData.update(se.getSecondaryAdr(), 0);
        }

        Set<Integer> sxAddressesToUpdate = new HashSet<>();
        // set signals turnout red
        for (RouteSignal rs : rtSignals) {
            rs.signal.setStateAndUpdateSXData(STATE_RED);
            rs.signal.setLocked(false);
            debug("unlocking signal=" + rs.signal.getAdr());
            sxAddressesToUpdate.add(rs.signal.getAdr() / 10);
        }

        // unlock turnouts
        for (RouteTurnout rtt : rtTurnouts) {
            rtt.turnout.setLocked(false);
            debug("unlocking turnout=" + rtt.turnout.getAdr());
        }

        for (int sxaddr : sxAddressesToUpdate) {
            if (SXUtils.isValidSXAddress(sxaddr)) {
                SXData.update(sxaddr, SXData.get(sxaddr), true);  // true => write to Interface
            }
        }
        // TODO unlock turnouts
        /*
		 * for (RouteTurnout to : rtTurnouts) { 
		 *     String cmd = "U " + to.turnout.adr;
		 *     sendQ.add(cmd); 
		 * }
         */
        // notify that route was cleared
        this.setState(RT_INACTIVE);

    }

    @Override
    public int setState(int st) {
        int result = super.setState(st);
        LanbahnData.update(getAdr(), result);
        return result;
    }

    public void clearOffendingRoutes() {
        /* disabled
        debug(" clearing (active) offending Routes");

        String[] offRoutes = offendingString.split(",");
        for (int i = 0; i < offRoutes.length; i++) {
            for (Route rt : allRoutes) {
                try {
                    int offID = Integer.parseInt(offRoutes[i]);
                    if ((rt.getAdr() == offID) && (rt.getState() == RT_ACTIVE)) {
                        rt.clear();
                    }
                } catch (NumberFormatException e) {
                }
            }
        }
         */
    }

    public boolean offendingRouteActive() {
        debug(" checking for (active) offending Routes");
        String[] offRoutes = offendingString.split(",");
        for (String offRoute : offRoutes) {
            for (Route rt : allRoutes) {
                try {
                    int offID = Integer.parseInt(offRoute);
                    if ((rt.getAdr() == offID) && (rt.getState() == RT_ACTIVE)) {
                        return true;
                    }
                }catch (NumberFormatException e) {
                }
            }
        }
        return false;
    }

    public void clearIn3Seconds() {
        debug("rt# " + getAdr() + " will be cleared in 3 sec");
        clearRouteTime = System.currentTimeMillis() + 3 * 1000;
    }

    /**
     * manual route setting
     *
     * @return
     */
    public boolean set() {
        return set(false, 0);
    }

    /**
     * set function if the route is part of a compound route
     *
     * @param automatic
     * @param trainNumber
     * @return
     */
    public boolean set(boolean automatic, int trainNumber) {
        automaticFlag = automatic;
        clearRouteTime = Long.MAX_VALUE;   // set only if route could be set successfully

        // if not given from compound route, get from occupation of first sensor
        if (trainNumber == 0) {
            trainNumber = getStartTrainNumber();
        }

        if (automatic) {
            // check if any of the PanelElements in this route are locked
            String pesLocked = panelElementsLocked();
            if (!pesLocked.isEmpty()) {
                info("cannot set route id=" + getAdr() + " because some PanelElements are locked: " + pesLocked);
                return false;  // cannot set route.
            }

            if (offendingRouteActive()) {
                debug(" offending route active");
                return false;
            }

            if (!isFreeExceptStart()) {
                error("cannot set route id=" + getAdr() + " because there is a train on route!");
                return false;
            }
        } else {

        }

        for (PanelElement se : rtSensors) {
            se.setInRoute(true);
            LanbahnData.update(se.getSecondaryAdr(), 1);  // st to "inroute
            // only virtual, no matching real SX address
        }
        debug(" setting route id=" + this.getAdr() + " trainNumber=" + trainNumber);

        // automatically
        clearOffendingRoutes();

        // activate sensors, set "IN_ROUTE" (this is stored in as "LanbahnData"
        // in secondary address of the sensor
        for (PanelElement se : rtSensors) {
            se.setInRoute(true);
            if (se.getState() == STATE_FREE) {
                // do not override if not free !              
                se.setTrain(trainNumber);
            }
            LanbahnData.update(se.getSecondaryAdr(), 1);  // st to "inroute
            // only virtual, no matching real SX address
        }

        // add sxadr values to a set, then update all these via RS232 only once
        Set<Integer> sxAddressesToUpdate = new HashSet<>();
        // set signals
        for (RouteSignal rs : rtSignals) {
            int d = rs.dynamicValueToSetForRoute();
            rs.signal.setStateAndUpdateSXData(d);
            rs.signal.setLocked(true);
            sxAddressesToUpdate.add(rs.signal.getAdr() / 10);
        }
        // set and lock turnouts
        for (RouteTurnout rtt : rtTurnouts) {
            int d = rtt.valueToSetForRoute;   // can be only 1 or 0
            rtt.turnout.setStateAndUpdateSXData(d);
            rtt.turnout.setLocked(true);
            // debug("RT, set turn= " + rtt.turnout.getAdr() + " state=" + d);
            sxAddressesToUpdate.add(rtt.turnout.getAdr() / 10);

        }
        for (int sxaddr : sxAddressesToUpdate) {
            if (SXUtils.isValidSXAddress(sxaddr)) {
                SXData.update(sxaddr, SXData.get(sxaddr), true);  // true => write to Interface
            }
        }

        if (!automaticFlag) {
            // only autoclear when route manual set
            clearRouteTime = System.currentTimeMillis() + AUTO_CLEAR_ROUTE_TIME_SECONDS * 1000L;
        }

        this.setState(RT_ACTIVE);
        return true;
    }

    @Override
    public boolean isLocked() {
        return !panelElementsLocked().isEmpty();
    }

    private String panelElementsLocked() {
        StringBuilder sb = new StringBuilder();
        for (RouteSignal rs : rtSignals) {
            if (rs.signal.isLocked()) {
                sb.append(rs.signal.getAdr());
                sb.append(";");
            }
        }
        // set and lock turnouts
        for (RouteTurnout rtt : rtTurnouts) {
            if (rtt.turnout.isLocked()) {
                sb.append(rtt.turnout.getAdr());
                sb.append(";");
            }
        }
        return sb.toString();
    }

    public boolean isActive() {
        return (this.getState() == RT_ACTIVE);
    }

    public int getStartTrainNumber() {
        return rtSensors.get(0).getTrain();   // train occupation of starting sensor
    }

    public PanelElement getStartSensor() {
        return rtSensors.get(0);   // start (first) sensor
    }

    public PanelElement getEndSensor() {
        int si = rtSensors.size();
        if (si > 1) {
            return rtSensors.get(si - 1);  // last sensor
        } else {
            return rtSensors.get(0);
        }
    }

    public boolean isFreeExceptStart() {
        // check if route is FREE (except for startSensor)
        for (int i = 1; i < rtSensors.size(); i++) {
            if (rtSensors.get(i).getState() != STATE_FREE) {
                debug("route id=" + getAdr() + " is not free");
                return false;
            }
        }
        debug("route id=" + getAdr() + " is free (except start)");
        return true;
    }

    public boolean isFree() {
        //check if route is FREE
        for (int i = 0; i < rtSensors.size(); i++) {
            if (rtSensors.get(i).getState() != STATE_FREE) {
                debug("route id=" + getAdr() + " is not free");
                return false;
            }
        }
        debug("route id=" + getAdr() + " is completely free (except start)");
        return true;
    }

    protected class RouteSignal {

        PanelElement signal;
        final private int valueToSetForRoute;
        final private int depFrom;

        RouteSignal(PanelElement se, int value) {
            signal = se;
            valueToSetForRoute = value;
            depFrom = INVALID_INT;
        }

        RouteSignal(PanelElement se, int value, int dependentFrom) {
            signal = se;
            valueToSetForRoute = value;
            depFrom = dependentFrom;
        }

        int dynamicValueToSetForRoute() {
            // set standard value if not green
            if ((depFrom == INVALID_INT) || (valueToSetForRoute != STATE_GREEN)) {
                return valueToSetForRoute;
            } else {
                // if standard-value == GREEN then check the other signal, which
                // this signal state depends on (can only be a ONE other signal)
                PanelElement depPe = PanelElement.getByAddress(depFrom);
                if (depPe.getState() == STATE_RED) {
                    // if other signal red, then set to yellow
                    return STATE_YELLOW;
                } else {
                    return valueToSetForRoute;
                }

            }
        }
    }

    protected void updateDependencies() {
        // update signals which have a dependency from another signal
        // set signals
        for (RouteSignal rs : rtSignals) {
            if (rs.depFrom != INVALID_INT) {
                if (rs.signal.getState() != rs.dynamicValueToSetForRoute()) {
                    rs.signal.setState(rs.dynamicValueToSetForRoute());
                    //LbUtils.updateLanbahnData(rs.signal.adr, rs.signal.state);
                }
            }
        }

    }

    protected class RouteTurnout {

        PanelElement turnout;
        int valueToSetForRoute;

        RouteTurnout(PanelElement te, int value) {
            turnout = te;
            valueToSetForRoute = value;
        }
    }

    /**
     * check for auto reset of allRoutes
     *
     */
    public static void auto() {

        // debug("checking route auto clear");
        for (Route rt : allRoutes) {
            if (rt.getState() == RT_ACTIVE) {  // check only active routes

                if (rt.automaticFlag) {  // only for automatic driven traines
                    // check for route end sensor - if it gets occupied (train reached end of route), rt will be cleared immediately
                    if ((rt.endSensor != null) && (rt.endSensor.getState() == STATE_OCCUPIED)) {
                        debug("end sensor" + rt.endSensor.getAdr() + " occupied =>  route#" + rt.getAdr() + " cleared");
                        rt.clear();
                    }
                }
                if ((System.currentTimeMillis() - rt.clearRouteTime) > 0) {
                    debug("route#" + rt.getAdr() + " cleared (time)");
                    rt.clear();
                }

                // update dependencies
                rt.updateDependencies();
            }
        }

    }

    public void addOffending(Route rt2) {
        // check if not already contained in offending string
        if (!rtOffending.contains(rt2)) {
            rtOffending.add(rt2);
        }
    }

    public String getOffendingString() {

        StringBuilder sb = new StringBuilder("");
        for (Route r : rtOffending) {
            if (sb.length() == 0) {
                sb.append(r.getAdr());
            } else {
                sb.append(",");
                sb.append(r.getAdr());
            }
        }
        /*		if (sb.length() == 0)
			Log.d(TAG, "route adr=" + adr + " has no offending allRoutes.");
		else
			Log.d(TAG, "route adr=" + adr + " has offending allRoutes with ids="
					+ sb.toString()); */
        return sb.toString();

    }

    public static void calcOffendingRoutes() {
        for (Route rt : allRoutes) {
            for (RouteTurnout t : rt.rtTurnouts) {
                // iterate over all turnouts of rt and check, if another route
                // activates the same turnout to a different position 
                for (Route rt2 : allRoutes) {
                    if (rt.getAdr() != rt2.getAdr()) {
                        for (RouteTurnout t2 : rt2.rtTurnouts) {
                            if ((t.turnout.getAdr() == t2.turnout.getAdr())
                                    && (t.valueToSetForRoute != t2.valueToSetForRoute)) {
                                rt.addOffending(rt2);
                                break;
                            }

                        }
                    }
                }
            }
            rt.offendingString = rt.getOffendingString();
        }

    }

    public static Route getFromAddress(int a) {
        for (Route r : allRoutes) {
            if (r.getAdr() == a) {
                return r;
            }
        }
        return null;
    }
}
