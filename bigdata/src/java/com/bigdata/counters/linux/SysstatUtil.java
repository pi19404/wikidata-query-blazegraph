/*

Copyright (C) SYSTAP, LLC 2006-2015.  All rights reserved.

Contact:
     SYSTAP, LLC
     2501 Calvert ST NW #106
     Washington, DC 20008
     licenses@systap.com

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

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Map;

import org.apache.log4j.Logger;

/**
 * Some utility methods related to integration with <code>sysstat</code>.
 * 
 * @author <a href="mailto:thompsonbry@users.sourceforge.net">Bryan Thompson</a>
 * @version $Id$
 */
public class SysstatUtil {

    final private static Logger log = Logger.getLogger(SysstatUtil.class);

    public interface Options {
        /**
         * The name of the optional property whose value specifies the default
         * location of the SYSSTAT package (pidstat, iostat, etc) (default
         * {@value #DEFAULT_PATH}).
         * 
         * @see #DEFAULT_PATH
         */
        String PATH = "com.bigdata.counters.linux.sysstat.path";
    	
        String DEFAULT_PATH = "/usr/bin";
    }

	/**
	 * Returns the path to the specified sysstat utility (pidstat, sar, etc).
	 * The default is directory is {@value Options#DEFAULT_PATH}. This may be
	 * overridden using the {@value Options#PATH} property. The following
	 * directories are also searched if the program is not found in the
	 * configured default location:
	 * <ul>
	 * <li>/usr/bin</li>
	 * <li>/usr/local/bin</li>
	 * </ul>
	 * 
	 * @return The path to the specified utility. If the utility was not found,
	 *         then the configured path to the utility will be returned anyway.
	 */
    static public final File getPath(final String cmd) {

		File f, path;
        final File configuredPath = path = new File(System.getProperty(
                Options.PATH, Options.DEFAULT_PATH));

        if (log.isInfoEnabled())
            log.info(Options.PATH + "=" + configuredPath);

		if (!(f=new File(path, cmd)).exists() && true) {

			log.warn("Not found: " + f);
			
			path = new File("/usr/bin");
			
			if (!(f = new File(path, cmd)).exists()) {

				log.warn("Not found: " + f);

				path = new File("/usr/local/bin");

				if (!(f = new File(path, cmd)).exists()) {

					log.warn("Not found: " + f);

					log.error("Could not locate: '" + cmd + "'. Set '-D"
							+ Options.PATH + "=<dir>'");

					// restore default even though not found.
					path = configuredPath;
					
				}

			}

		}

		if (configuredPath != path) {

			log.warn("Using effective path: " + Options.PATH + "=" + path);

		}

		return new File(path, cmd);

    }
    
    /**
     * Splits a data line into fields based on whitespace and skipping over the
     * date field (index zero (0) is the index of the first non-date field).
     * <p>
     * Note: Some fields can overflow, e.g., RSS. When this happens the fields
     * in the data lines wind up eating into the whitespace to their
     * <em>right</em>. This means that it is more robust to split the lines
     * based on whitespace once we have skipped over the date field. Since we
     * specify using {@link PIDStatCollector#setEnvironment(Map)} that we want
     * an ISO date format, we know that the date field is 11 characters. The
     * data lines are broken up by whitespace after that.
     * <p>
     * Note: Since we split on whitespace, the resulting strings are already
     * trimmed.
     * 
     * @return The fields.
     */
    static public String[] splitDataLine(final String data) {
        
        final String t = data.substring(11);
        
//        final String s = t.replaceAll("", replacement) 
        
        // split into fields
        final String[] fields = t.split("\\s+");
        
        // the first field is empty, so we put the data in there.
        fields[0] = data.substring(0,11);
        
        if(log.isDebugEnabled()) {
            
            log.debug("fields="+Arrays.toString(fields));
            
        }
    
        return fields;
        
    }

    /**
     * Used to parse the timestamp associated with each row of sysstat output.
     * <p>
     * Note: This assumes that you have controlled the date format using the
     * sysstat ISO date option.
     * <p>
     * Note: This is not thread-safe - use a distinct instance for each
     * {@link PIDStatCollector} or {@link SarCpuUtilizationCollector}.
     * 
     * @deprecated sysstat only reports the TIME OF DAY. In order to get the UTC
     *             time it has to be corrected by the UTC time of the start of
     *             the current day. Since very little latency is expected
     *             between the report by sysstat of its performance counters and
     *             the parsing of those performance counters by our code, it is
     *             MUCH easier and more robust to simply use the current time as
     *             reported by {@link System#currentTimeMillis()}.
     */
    static public DateFormat newDateFormat() {
        
        return new SimpleDateFormat("hh:mm:ss aa");
        
    }

}
