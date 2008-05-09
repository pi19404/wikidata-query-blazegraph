/*

Copyright (C) SYSTAP, LLC 2006-2008.  All rights reserved.

Contact:
     SYSTAP, LLC
     4501 Tower Road
     Greensboro, NC 27410
     licenses@bigdata.com

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; version 2 of the License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

*/
/*
 * Created on May 9, 2008
 */

package com.bigdata.counters.linux;

import java.util.Arrays;
import java.util.Map;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.bigdata.counters.AbstractStatisticsCollector;

/**
 * Some utility methods related to integration with <code>sysstat</code>.
 * 
 * @author <a href="mailto:thompsonbry@users.sourceforge.net">Bryan Thompson</a>
 * @version $Id$
 */
public class SysstatUtil {


    final protected static Logger log = Logger
            .getLogger(AbstractStatisticsCollector.class);

    /**
     * True iff the {@link #log} level is DEBUG or less.
     */
    final protected static boolean DEBUG = log.getEffectiveLevel().toInt() <= Level.DEBUG
            .toInt();

    /**
     * True iff the {@link #log} level is INFO or less.
     */
    final protected static boolean INFO = log.getEffectiveLevel().toInt() <= Level.INFO
            .toInt();
    
    /**
     * Splits a data line into fields based on whitespace and skipping over
     * the date field (index zero (0) is the index of the first non-date
     * field).
     * <p>
     * Note: Some fields can overflow, e.g., RSS. When this happens the
     * fields in the data lines wind up eating into the whitespace to their
     * <em>right</em>. This means that it is more robust to split the
     * lines based on whitespace once we have skipped over the date field.
     * Since we specify using {@link PIDStatCollector#setEnvironment(Map)}
     * that we want an ISO date format, we know that the date field is 11
     * characters. The data lines are broken up by whitespace after that.
     * <p>
     * Note: Since we split on whitespace, the resulting strings are already
     * trimmed.
     * 
     * @return The fields.
     */
    static public String[] splitDataLine(String data) {
        
        final String t = data.substring(11);
        
//        final String s = t.replaceAll("", replacement) 
        
        // split into fields
        final String[] fields = t.split("\\s+");
        
        // the first field is empty, so we put the data in there.
        fields[0] = data.substring(0,11);
        
        if(DEBUG) {
            
            log.debug("fields="+Arrays.toString(fields));
            
        }
    
        return fields;
        
    }

}
