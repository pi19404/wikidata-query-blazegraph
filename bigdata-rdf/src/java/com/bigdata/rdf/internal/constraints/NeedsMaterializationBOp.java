/*

Copyright (C) SYSTAP, LLC 2006-2007.  All rights reserved.

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
package com.bigdata.rdf.internal.constraints;

import java.util.Map;

import org.apache.log4j.Logger;

import com.bigdata.bop.BOp;
import com.bigdata.bop.IBindingSet;
import com.bigdata.bop.IConstraint;
import com.bigdata.bop.IValueExpression;
import com.bigdata.rdf.internal.IV;
import com.bigdata.rdf.internal.NotMaterializedException;
import com.bigdata.rdf.internal.XSDBooleanIV;
import com.bigdata.util.InnerCause;

/**
 * Attempts to run a constraint prior to materialization. Returns false if it
 * completes successfully without a {@link NotMaterializedException}, true
 * otherwise.
 */
public class NeedsMaterializationBOp extends XSDBooleanIVValueExpression {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4767476516948560884L;

	private static final transient Logger log = Logger.getLogger(NeedsMaterializationBOp.class);
	

	public NeedsMaterializationBOp(final IValueExpression x) {
        
        this(new BOp[] { x }, NOANNS); 
        
    }
    
    /**
     * Required shallow copy constructor.
     */
    public NeedsMaterializationBOp(final BOp[] args, final Map<String, Object> anns) {

    	super(args, anns);
    	
        if (args.length != 1 || args[0] == null)
            throw new IllegalArgumentException();

    }

    /**
     * Required deep copy constructor.
     */
    public NeedsMaterializationBOp(final NeedsMaterializationBOp op) {
        super(op);
    }

    public boolean accept(final IBindingSet bs) {

    	final IValueExpression ve = get(0); 
    	
    	try {
    		
    		if (log.isDebugEnabled()) {
    			log.debug("about to attempt evaluation prior to materialization");
    		}
    		
    		ve.get(bs);
    		
    		if (log.isDebugEnabled()) {
    			log.debug("successfully evaluated constraint without materialization");
    		}
    		
    		return false;
    		
    	} catch (Throwable t) {

			if (InnerCause.isInnerCause(t, NotMaterializedException.class)) {
    		
	    		if (log.isDebugEnabled()) {
	    			log.debug("could not evaluate constraint without materialization");
	    		}
	    		
	    		return true;
    		
			} else throw new RuntimeException(t);
    		
    	}

    }
    
}