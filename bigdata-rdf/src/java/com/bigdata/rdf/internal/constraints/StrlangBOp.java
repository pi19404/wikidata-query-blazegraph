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
package com.bigdata.rdf.internal.constraints;

import java.util.Map;

import com.bigdata.bop.BOp;
import com.bigdata.bop.IBindingSet;
import com.bigdata.bop.IValueExpression;
import com.bigdata.bop.NV;
import com.bigdata.rdf.error.SparqlTypeErrorException;
import com.bigdata.rdf.internal.IV;
import com.bigdata.rdf.internal.NotMaterializedException;
import com.bigdata.rdf.internal.WrappedIV;
import com.bigdata.rdf.model.BigdataLiteral;
import com.bigdata.rdf.model.BigdataValue;
import com.bigdata.rdf.model.BigdataValueFactory;
import com.bigdata.rdf.sparql.ast.DummyConstantNode;

public class StrlangBOp extends LexiconBOp {

    private static final long serialVersionUID = 4227610629554743647L;

    public StrlangBOp(IValueExpression<? extends IV> x, IValueExpression<? extends IV> dt, String lex) {
        this(new BOp[] { x, dt }, NV.asMap(new NV(Annotations.NAMESPACE, lex)));
    }

    public StrlangBOp(BOp[] args, Map<String, Object> anns) {
        super(args, anns);
        if (args.length != 2 || args[0] == null || args[1] == null)
            throw new IllegalArgumentException();

    }

    public StrlangBOp(StrlangBOp op) {
        super(op);
    }

    @Override
    public Requirement getRequirement() {
        return Requirement.SOMETIMES;
    }

    protected IV generateIV(final BigdataValueFactory vf, final IV iv, final IBindingSet bs) throws SparqlTypeErrorException {
        final IV lang = get(1).get(bs);

        if(lang==null){
            throw new SparqlTypeErrorException.UnboundVarException();
        }
        if (!lang.isLiteral())
            throw new SparqlTypeErrorException();

        if (!lang.isInline() && !lang.hasValue())
            throw new NotMaterializedException();

        BigdataLiteral l = (BigdataLiteral)lang.getValue();

        final BigdataLiteral lit = (BigdataLiteral) iv.getValue();
        String label = lit.getLabel();
        String langLit = l.getLabel();
        final BigdataLiteral str = vf.createLiteral(label, langLit);
        return DummyConstantNode.dummyIV(str);

    }

}