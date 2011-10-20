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
/*
 * Created on Oct 17, 2011
 */

package com.bigdata.bop.join;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import junit.framework.TestCase;

import com.bigdata.bop.Constant;
import com.bigdata.bop.IBindingSet;
import com.bigdata.bop.IConstant;
import com.bigdata.bop.IConstraint;
import com.bigdata.bop.IVariable;
import com.bigdata.bop.Var;
import com.bigdata.bop.bindingSet.ListBindingSet;
import com.bigdata.bop.constraint.Constraint;
import com.bigdata.bop.constraint.EQConstant;
import com.bigdata.bop.engine.AbstractQueryEngineTestCase;
import com.bigdata.relation.accesspath.IBuffer;

/**
 * Test suite for both {@link HashJoinUtility} and {@link JVMHashJoinUtility}.
 * 
 * TODO Unit test of optional solutions
 * 
 * TODO Verify application of constraints, but only to the non-optional
 * solutions (so this means a test with optionals and also we need a test w/o
 * constraints).
 * 
 * TODO See {@link TestHTreeHashJoin} and some of the other join test suites for
 * some of these variations.
 * 
 * @author <a href="mailto:thompsonbry@users.sourceforge.net">Bryan Thompson</a>
 * @version $Id$
 */
abstract public class AbstractHashJoinUtilityTestCase extends TestCase {
    
    public AbstractHashJoinUtilityTestCase() {
    }
    
    public AbstractHashJoinUtilityTestCase(String name) {
        super(name);
    }

    /**
     * Test helper.
     * 
     * @param optional
     * @param joinVars
     * @param selectVars
     * @param left
     * @param right
     * @param expected
     */
    abstract protected void doHashJoinTest(//
            final boolean optional,//
            final IVariable<?>[] joinVars,//
            final IVariable<?>[] selectVars,//
            final IConstraint[] constraints,//
            final List<IBindingSet> left, //
            final List<IBindingSet> right,//
            final IBindingSet[] expected//
            );
    
    /**
     * Empty lhs and rhs with non-optional join.
     */
    public void test_hashJoin01() {

        final boolean optional = false;

        // the join variables.
        final IVariable<?>[] joinVars = new IVariable[]{};

        // the variables projected by the join (iff non-null).
        final IVariable<?>[] selectVars = null;

        // the join constraints.
        final IConstraint[] constraints = null;

        // The left solutions (the pipeline).
        final List<IBindingSet> left = new LinkedList<IBindingSet>();

        // The right solutions (the hash index).
        final List<IBindingSet> right = new LinkedList<IBindingSet>();

        // The expected solutions to the join.
        final IBindingSet[] expected = new IBindingSet[0];
        
        doHashJoinTest(optional, joinVars, selectVars, constraints, left,
                right, expected);

    }

    /**
     * Empty lhs and rhs with optional join.
     */
    public void test_hashJoin02() {

        final boolean optional = true;

        // the join variables.
        final IVariable<?>[] joinVars = new IVariable[]{};

        // the variables projected by the join (iff non-null).
        final IVariable<?>[] selectVars = null;

        // the join constraints.
        final IConstraint[] constraints = null;

        // The left solutions (the pipeline).
        final List<IBindingSet> left = new LinkedList<IBindingSet>();

        // The right solutions (the hash index).
        final List<IBindingSet> right = new LinkedList<IBindingSet>();

        // The expected solutions to the join.
        final IBindingSet[] expected = new IBindingSet[0];
        
        doHashJoinTest(optional, joinVars, selectVars, constraints, left,
                right, expected);

    }
    
    private List<IBindingSet> getLeft1() {

        final IVariable<?> x = Var.var("x");
        final IVariable<?> y = Var.var("y");

        // The left solutions (the pipeline).
        final List<IBindingSet> left = new LinkedList<IBindingSet>();

        IBindingSet tmp;

        tmp = new ListBindingSet();
        tmp.set(x, new Constant<String>("Brad"));
        tmp.set(y, new Constant<String>("Fred"));
        left.add(tmp);

        tmp = new ListBindingSet();
        tmp.set(x, new Constant<String>("Mary"));
        left.add(tmp);

        return left;
    }

    private List<IBindingSet> getRight1() {

        final IVariable<?> a = Var.var("a");
        final IVariable<?> x = Var.var("x");
//        final IVariable<?> y = Var.var("y");

        // The right solutions (the hash index).
        final List<IBindingSet> right = new LinkedList<IBindingSet>();

        IBindingSet tmp;

        tmp = new ListBindingSet();
        tmp.set(a, new Constant<String>("Paul"));
        tmp.set(x, new Constant<String>("Mary"));
        right.add(tmp);

        tmp = new ListBindingSet();
        tmp.set(a, new Constant<String>("Paul"));
        tmp.set(x, new Constant<String>("Brad"));
        right.add(tmp);

        tmp = new ListBindingSet();
        tmp.set(a, new Constant<String>("John"));
        tmp.set(x, new Constant<String>("Mary"));
        right.add(tmp);

        tmp = new ListBindingSet();
        tmp.set(a, new Constant<String>("John"));
        tmp.set(x, new Constant<String>("Brad"));
        right.add(tmp);

        tmp = new ListBindingSet();
        tmp.set(a, new Constant<String>("Mary"));
        tmp.set(x, new Constant<String>("Brad"));
        right.add(tmp);

        tmp = new ListBindingSet();
        tmp.set(a, new Constant<String>("Brad"));
        tmp.set(x, new Constant<String>("Fred"));
        right.add(tmp);

        tmp = new ListBindingSet();
        tmp.set(a, new Constant<String>("Brad"));
        tmp.set(x, new Constant<String>("Leon"));
        right.add(tmp);

        // new E("Paul", "Mary"),// [0]
        // new E("Paul", "Brad"),// [1]
        //
        // new E("John", "Mary"),// [2]
        // new E("John", "Brad"),// [3]
        //
        // new E("Mary", "Brad"),// [4]
        //
        // new E("Brad", "Fred"),// [5]
        // new E("Brad", "Leon"),// [6]

        return right;

    }

    /**
     * Non-optional join.
     */
    public void test_hashJoin03() {

        final boolean optional = false;

        final IVariable<?> a = Var.var("a");
        final IVariable<?> x = Var.var("x");
        final IVariable<?> y = Var.var("y");
        
        // the join variables.
        final IVariable<?>[] joinVars = new IVariable[]{x};

        // the variables projected by the join (iff non-null).
        final IVariable<?>[] selectVars = new IVariable[]{x,y};

        // the join constraints.
        final IConstraint[] constraints = new IConstraint[] { Constraint
                .wrap(new EQConstant(a, new Constant<String>("John"))) };

        // The left solutions (the pipeline).
        final List<IBindingSet> left = getLeft1();

        // The right solutions (the hash index).
        final List<IBindingSet> right = getRight1();

        // The expected solutions to the join.
        final IBindingSet[] expected = new IBindingSet[] {//
                new ListBindingSet(//
                        new IVariable[] { x },//
                        new IConstant[] { new Constant<String>("Mary") }//
                ),//
                new ListBindingSet(//
                        new IVariable[] { x, y },//
                        new IConstant[] { new Constant<String>("Brad"),
                                          new Constant<String>("Fred"),
                                }//
                ),//
        };

        doHashJoinTest(optional, joinVars, selectVars, constraints, left,
                right, expected);

    }

    /**
     * Variant with no join variables.
     */
    public void test_hashJoin04() {

        final boolean optional = false;

        final IVariable<?> a = Var.var("a");
        final IVariable<?> x = Var.var("x");
        final IVariable<?> y = Var.var("y");
        
        // the join variables.
        final IVariable<?>[] joinVars = new IVariable[]{};

        // the variables projected by the join (iff non-null).
        final IVariable<?>[] selectVars = new IVariable[]{x,y};

        // the join constraints.
        final IConstraint[] constraints = new IConstraint[] { Constraint
                .wrap(new EQConstant(a, new Constant<String>("John"))) };

        // The left solutions (the pipeline).
        final List<IBindingSet> left = getLeft1();

        // The right solutions (the hash index).
        final List<IBindingSet> right = getRight1();

        // The expected solutions to the join.
        final IBindingSet[] expected = new IBindingSet[] {//
                new ListBindingSet(//
                        new IVariable[] { x },//
                        new IConstant[] { new Constant<String>("Mary") }//
                ),//
                new ListBindingSet(//
                        new IVariable[] { x, y },//
                        new IConstant[] { new Constant<String>("Brad"),
                                          new Constant<String>("Fred"),
                                }//
                ),//
        };

        doHashJoinTest(optional, joinVars, selectVars, constraints, left,
                right, expected);

    }

    /**
     * Variant without select variables.
     */
    public void test_hashJoin05() {

        final boolean optional = false;

        final IVariable<?> a = Var.var("a");
        final IVariable<?> x = Var.var("x");
        final IVariable<?> y = Var.var("y");
        
        // the join variables.
        final IVariable<?>[] joinVars = new IVariable[]{};

        // the variables projected by the join (iff non-null).
        final IVariable<?>[] selectVars = null;

        // the join constraints.
        final IConstraint[] constraints = new IConstraint[] { Constraint
                .wrap(new EQConstant(a, new Constant<String>("John"))) };

        // The left solutions (the pipeline).
        final List<IBindingSet> left = getLeft1();

        // The right solutions (the hash index).
        final List<IBindingSet> right = getRight1();

        // The expected solutions to the join.
        final IBindingSet[] expected = new IBindingSet[] {//
                new ListBindingSet(//
                        new IVariable[] { a, x },//
                        new IConstant[] { new Constant<String>("John"),
                                          new Constant<String>("Mary") }//
                ),//
                new ListBindingSet(//
                        new IVariable[] { a, x, y },//
                        new IConstant[] { new Constant<String>("John"),
                                          new Constant<String>("Brad"),
                                          new Constant<String>("Fred"),
                                }//
                ),//
        };

        doHashJoinTest(optional, joinVars, selectVars, constraints, left,
                right, expected);

    }


    @SuppressWarnings("deprecation")
    protected static void assertSameSolutionsAnyOrder(
            final IBindingSet[] expected, final Iterator<IBindingSet> actual) {

        AbstractQueryEngineTestCase.assertSameSolutionsAnyOrder(expected,
                actual);

    }
    
    /**
     * A buffer which absorbs solutions and let's us replay them via an
     * iterator.
     */
    protected static class TestBuffer<E> implements IBuffer<E> {
        
        @SuppressWarnings({ "rawtypes", "unchecked" })
        private final List<E> a = new LinkedList();
        
        public Iterator<E> iterator() {
            return a.iterator();
        }

        public int size() {
            return a.size();
        }

        public boolean isEmpty() {
            return a.isEmpty();
        }

        public void add(E e) {
            a.add(e);
        }

        public long flush() {
            return 0;
        }

        public void reset() {
        }
        
    }
    
}