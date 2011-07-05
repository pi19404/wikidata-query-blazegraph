/**

Copyright (C) SYSTAP, LLC 2006-2010.  All rights reserved.

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
 * Created on Jun 7, 2011
 */
package com.bigdata.rdf.internal;

import java.util.Random;

import junit.framework.TestCase2;

import com.bigdata.btree.BytesUtil;
import com.bigdata.btree.keys.IKeyBuilder;
import com.bigdata.rdf.lexicon.BlobsIndexHelper;
import com.bigdata.rdf.model.BigdataURI;

/**
 * Unit tests for {@link BlobIV}.
 * 
 * @author thompsonbry
 */
public class TestBlobIV extends TestCase2 {

	public TestBlobIV() {
	}

	public TestBlobIV(String name) {
		super(name);
	}

	private BlobsIndexHelper helper;
	
	protected void setUp() throws Exception {
		super.setUp();
		helper = new BlobsIndexHelper();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
		helper = null;
	}

    public void test_BlobIV_isExtensionIV() {

        final BlobIV<BigdataURI> iv = new BlobIV<BigdataURI>(VTE.URI,
                12/* hash */, (short) 50/* counter */);

        assertEquals(VTE.URI, iv.getVTE());

        assertFalse(iv.isInline());

        assertTrue(iv.isExtension());

        assertEquals(DTE.XSDBoolean, iv.getDTE());

        assertEquals(12, iv.hashCode());

        assertEquals(50, iv.counter());

	}
	
	private void doBlobIVTest(final VTE vte, final int hashCode,
			final int counter) {

		final IKeyBuilder keyBuilder = helper.newKeyBuilder();

		final BlobIV<?> iv = new BlobIV<BigdataURI>(vte, hashCode,
				(short) counter);

		assertEquals(BlobIV.toFlags(vte), iv.flags());

		assertEquals(vte, iv.getVTE());

		assertEquals(hashCode, iv.hashCode());

		assertEquals(counter, iv.counter());

		assertEquals(iv, BlobIV.fromString(iv.toString()));

		final byte[] a = iv.encode(keyBuilder.reset()).getKey();
		
		final byte[] key = helper.makeKey(keyBuilder.reset(), vte, hashCode,
				counter);

		if (!BytesUtil.bytesEqual(a, key)) {

			fail("Encoding differs: iv=" + iv + ", expected="
					+ BytesUtil.toString(a) + ", actual="
					+ BytesUtil.toString(key));
			
		}

		final BlobIV<?> iv2 = (BlobIV<?>) IVUtility.decode(key);

		assertEquals(vte, iv2.getVTE());

		assertEquals(hashCode, iv2.hashCode());

		assertEquals(counter, iv2.counter());

		assertEquals(iv, BlobIV.fromString(iv2.toString()));

		assertEquals(iv, iv2);

    }
    
    public void test_BlobIV_URI() {
    	
    	final VTE vte = VTE.URI;
    	
    	final int hashCode = 12;
    	
    	final int counter = 1;

        doBlobIVTest(vte, hashCode, counter);

    }

    public void test_BlobIV_Literal() {
    	
    	final VTE vte = VTE.LITERAL;
    	
    	final int hashCode = 12;
    	
    	final int counter = 1;

    	doBlobIVTest(vte, hashCode, counter);
    	
    }

    public void test_BlobIV_BNode() {
    	
    	final VTE vte = VTE.BNODE;
    	
    	final int hashCode = 12;
    	
    	final int counter = 1;

    	doBlobIVTest(vte, hashCode, counter);

		// TODO test iv.bnodeId() here.
		
    }

    public void test_BlobIV_URI_Counter_ZERO() {
    	
    	final VTE vte = VTE.URI;
    	
    	final int hashCode = 12;
    	
    	final int counter = 0;

    	doBlobIVTest(vte, hashCode, counter);
    	
    }

    public void test_BlobIV_URI_Counter_ONE() {
    	
    	final VTE vte = VTE.URI;
    	
    	final int hashCode = 12;
    	
    	final int counter = 1;
    	
    	doBlobIVTest(vte, hashCode, counter);
		
    }

    public void test_BlobIV_URI_Counter_MIN_VALUE() {
    	
    	final VTE vte = VTE.URI;
    	
    	final int hashCode = 12;
    	
    	final int counter = Short.MIN_VALUE;

    	doBlobIVTest(vte, hashCode, counter);
 		
    }

    public void test_BlobIV_URI_Counter_MAX_VALUE() {
    	
    	final VTE vte = VTE.URI;
    	
    	final int hashCode = 12;
    	
    	final int counter = Short.MAX_VALUE;
 
    	doBlobIVTest(vte, hashCode, counter);
		
    }

    /**
     * Unit tests for {@link BlobIV}
     */
    public void test_BlobIV() {

		final Random r = new Random();

		final IKeyBuilder keyBuilder = helper.newKeyBuilder();

		for (VTE vte : VTE.values()) {

			final int hashCode = r.nextInt();

            final int counter = Short.MAX_VALUE - r.nextInt(2 ^ 16);

			final BlobIV<?> v = new BlobIV(vte, hashCode, (short) counter);

			assertFalse(v.isInline());

			if (!vte.equals(v.getVTE())) {
				fail("Expected=" + vte + ", actual=" + v.getVTE() + " : iv="
						+ v);//+", key="+BytesUtil.toString(key));
			}

			assertEquals(hashCode, v.hashCode());

			assertEquals(counter, v.counter());

			// Verify we can encode/decode the BlobIV.
	        {
		     
				final byte[] key = helper.makeKey(keyBuilder.reset(), vte,
						hashCode, counter);

		        final BlobIV<?> iv2 = (BlobIV<?>) IVUtility.decode(key);

		        assertEquals(v, iv2);
		        
		        assertEquals(0,v.compareTo(iv2));

		        assertEquals(0,iv2.compareTo(v));
		        
                assertEquals(v.isURI(), iv2.isURI());
                assertEquals(v.isLiteral(), iv2.isLiteral());
                assertEquals(v.isBNode(), iv2.isBNode());
                assertEquals(v.isStatement(), iv2.isStatement());
		        
	        }

			try {
				v.getInlineValue();
				fail("Expecting " + UnsupportedOperationException.class);
			} catch (UnsupportedOperationException ex) {
				// ignored.
			}

			switch (vte) {
			case URI:
				assertTrue(v.isURI());
				assertFalse(v.isBNode());
				assertFalse(v.isLiteral());
				assertFalse(v.isStatement());
				break;
			case BNODE:
				assertFalse(v.isURI());
				assertTrue(v.isBNode());
				assertFalse(v.isLiteral());
				assertFalse(v.isStatement());
				break;
			case LITERAL:
				assertFalse(v.isURI());
				assertFalse(v.isBNode());
				assertTrue(v.isLiteral());
				assertFalse(v.isStatement());
				break;
			case STATEMENT:
				assertFalse(v.isURI());
				assertFalse(v.isBNode());
				assertFalse(v.isLiteral());
				assertTrue(v.isStatement());
				break;
			default:
				fail("vte=" + vte);
			}

			// Verify that toString and fromString work,
			{
			
				final String s = v.toString();

				final BlobIV<?> tmp = BlobIV.fromString(s);

				assertEquals(v, tmp);
				
			}
			
		}

	}

}