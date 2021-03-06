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

import static de.blankedv.sx4.Constants.INVALID_INT;

/**
 *
 * @author mblank
 */
public class LocoSpeedPairs {

    public int loco1 = INVALID_INT;
    public int speed1 = INVALID_INT;
    public int loco2 = INVALID_INT;
    public int speed2 = INVALID_INT;


    @Override
    public String toString() {
        if ((loco1 == INVALID_INT) || (speed1 == INVALID_INT)) {
            return "";
        } else {
            return "" + loco1 + "/" + speed1 +" -> "+ loco2 + "/" + speed2 +" -> ";
        }
    }
}
