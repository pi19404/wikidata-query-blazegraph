/**

Copyright (C) SYSTAP, LLC 2006-2011.  All rights reserved.

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
package com.bigdata.rdf.sparql.ast;

/**
 * A subquery with a named solution set which can be referenced from other parts
 * of the query.
 * 
 * @see NamedSubqueryInclude
 */
public class NamedSubqueryRoot extends SubqueryRoot {

    private String name;

    public NamedSubqueryRoot() {

    }

    /**
     * The name associated with the subquery.
     */
    public String getName() {

        return name;
        
    }

    /**
     * Set the name associated with the subquery.
     * 
     * @param name
     */
    public void setName(String name) {
     
        this.name = name;
        
    }

}