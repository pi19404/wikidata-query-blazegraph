/**

Copyright (C) SYSTAP, LLC 2006-2012.  All rights reserved.

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
 * Created on Mar 1, 2012
 */

package com.bigdata.rdf.sparql.ast.service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.openrdf.model.URI;
import org.openrdf.query.BindingSet;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.query.impl.TupleQueryResultBuilder;
import org.openrdf.query.resultio.TupleQueryResultFormat;
import org.openrdf.query.resultio.TupleQueryResultParser;
import org.openrdf.query.resultio.TupleQueryResultParserFactory;
import org.openrdf.query.resultio.TupleQueryResultParserRegistry;

import com.bigdata.rdf.sail.Sesame2BigdataIterator;
import com.bigdata.rdf.store.AbstractTripleStore;
import com.bigdata.striterator.ICloseableIterator;

/**
 * This class handleS vectored remote service invocation by generating an
 * appropriate SPARQL query (with BINDINGS) and an appropriate HTTP request.
 * 
 * TODO Add {@link RemoteServiceOptions} options for additional URL query
 * parameters (defaultGraph, etc), authentication, HTTP METHOD (GET/POST),
 * preferred result format (SPARQL, BINARY, etc.), etc. This can be configured
 * for a service URI configured using the {@link ServiceRegistry}.
 * 
 * @author <a href="mailto:thompsonbry@users.sourceforge.net">Bryan Thompson</a>
 * @version $Id: RemoteServiceCallImpl.java 6060 2012-03-02 16:07:38Z
 *          thompsonbry $
 */
public class RemoteServiceCallImpl implements RemoteServiceCall {

    private static final Logger log = Logger
            .getLogger(RemoteServiceCallImpl.class);

    /**
     * The name of the <code>UTF-8</code> character encoding.
     */
    static private final String UTF8 = "UTF-8";
    
//    private final AbstractTripleStore store;
//    private final IGroupNode<IGroupMemberNode> groupNode;
    private final URI serviceURI;
    private final ServiceNode serviceNode;
    private final RemoteServiceOptions serviceOptions;
//    private final String exprImage;
//    private final Map<String,String> prefixDecls;
//    private final Set<IVariable<?>> projectedVars;
    
    public RemoteServiceCallImpl(final AbstractTripleStore store,
            final URI serviceURI, final ServiceNode serviceNode,
            final RemoteServiceOptions serviceOptions) {

//        if (store == null)
//            throw new IllegalArgumentException();

        if (serviceURI == null)
            throw new IllegalArgumentException();

        if (serviceNode == null)
            throw new IllegalArgumentException();

        if (serviceOptions == null)
            throw new IllegalArgumentException();

//        this.store = store;
        
        this.serviceURI = serviceURI;
        
        this.serviceNode = serviceNode;
        
        this.serviceOptions = serviceOptions;

    }
    
    @Override
    public RemoteServiceOptions getServiceOptions() {
        
        return serviceOptions;
        
    }

    @Override
    public ICloseableIterator<BindingSet> call(final BindingSet[] bindingSets)
            throws Exception {

        final QueryOptions opts = new QueryOptions(serviceURI.stringValue());

        final TupleQueryResultFormat resultFormat = TupleQueryResultFormat.BINARY;

        opts.acceptHeader = //
        resultFormat.getDefaultMIMEType() + ";q=1" + //
                "," + //
                TupleQueryResultFormat.SPARQL.getDefaultMIMEType() + ";q=1"//
        ;

        /*
         * Note: This uses a factory pattern to handle each of the possible ways
         * in which we have to vector solutions to the service end point.
         */
        final IRemoteSparqlQueryBuilder queryBuilder = RemoteSparqlBuilderFactory
                .get(getServiceOptions(), serviceNode, bindingSets);

//        final BindingSet[] b;
//
////        if (queryBuilder.isVectored()) {
//
//            b = doRemoteQuery(opts, queryBuilder, bindingSets);
//
////        } else {
////
////            b = doRemoteNonVectoredQuery(opts, queryBuilder, bindingSets);
////
////        }
//
//        return new ChunkedArrayIterator<BindingSet>(b);
//        
//    }

//    /**
//     * We can not vector the solutions to the remote service end point. This
//     * will issue one remote query per source solution. It gathers together all
//     * such responses and then returns them in a single chunk to the caller.
//     */
//    private BindingSet[] doRemoteNonVectoredQuery(final QueryOptions opts,
//            final IRemoteSparqlQueryBuilder queryBuilder,
//            final BindingSet[] bindingSets) throws Exception {
//     
//        final List<BindingSet[]> chunks = new LinkedList<BindingSet[]>();
//        
//        int nsolutions = 0;
//        
//        for (int i = 0; i < bindingSets.length; i++) {
//
//            final BindingSet[] b = doRemoteQuery(opts, queryBuilder,
//                    new BindingSet[] { bindingSets[i] });
//            
//            chunks.add(b);
//            
//            nsolutions += b.length;
//
//        }
//
//        /*
//         * Flatten out all of the individual results from the service.
//         */
//        final BindingSet[] b = new BindingSet[nsolutions];
//        
//        int index = 0;
//        for (BindingSet[] chunk : chunks) {
//         
//            System.arraycopy(chunk/* src */, 0/* srcPos */, b/* dest */,
//                    index/* destPos */, chunk.length);
//            
//            index += chunk.length;
//            
//        }
//        
//        return b;
//
//    }
//
//    /**
//     * Execute a remote SPARQL query, returning the solutions for that query.
//     * 
//     * @param bindingSets
//     *            The solutions to flow into the query.
//     *            
//     * @return The solutions received for that query.
//     */
//    private BindingSet[] doRemoteQuery(final QueryOptions opts,
//            final IRemoteSparqlQueryBuilder queryBuilder,
//            final BindingSet[] bindingSets) throws Exception {

//        if (queryBuilder.isVectored() && bindingSets.length > 1) {
//        
//            throw new UnsupportedOperationException();
//
//        }
        
        opts.queryStr = queryBuilder.getSparqlQuery(bindingSets);

        /*
         * Note: This does not stream chunks back. The ServiceCallJoin currently
         * materializes all solutions from the service in a single chunk, so
         * there is no point doing something incremental here unless it is
         * coordinated with the ServiceCallJoin.
         */

        final TupleQueryResult queryResult;

        try {

            queryResult = parseResults(checkResponseCode(doSparqlQuery(opts)));

        } finally {

            /*
             * Note: HttpURLConnection.disconnect() is not a "close". close() is
             * an implicit action for this class.
             */

        }

        return new Sesame2BigdataIterator<BindingSet, QueryEvaluationException>(
                        queryResult);

//        /*
//         * Apply a transform in reverse here using the same builder object.
//         * 
//         * TODO This requires materialization of the result set, but the
//         * ServiceCallJoin already has that requirement. Maybe change
//         * ServiceCall#call() to return an IBindingSet or BindingSet[]? We can
//         * then limit the size of the materialized solution set (indirectly) by
//         * controlling the vector size into the ServiceCallJoin. That is how we
//         * do it for everything else, so it seems like a good idea to follow the
//         * same pattern here.
//         */
//        {
//
//            final List<BindingSet> serviceSolutions = new LinkedList<BindingSet>();
//
//            final ICloseableIterator<BindingSet> itr = new Sesame2BigdataIterator<BindingSet, QueryEvaluationException>(
//                    queryResult);
//
//            try {
//
//                while (itr.hasNext()) {
//
//                    serviceSolutions.add(itr.next());
//
//                }
//
//            } finally {
//
//                itr.close();
//
//            }
//
//            // Convert to array.
//            final BindingSet[] a = serviceSolutions
//                    .toArray(new BindingSet[serviceSolutions.size()]);
//
//            // Possible undo a UNION rewrite of the SERVICE call.
//            final BindingSet[] b = queryBuilder.getSolutions(a);
//
//            return b;
//
//        }

    }
        
    /**
     * Extracts the solutions from a SPARQL query.
     * 
     * @param conn
     *            The connection from which to read the results.
     * 
     * @return The results.
     * 
     * @throws Exception
     *             If anything goes wrong.
     */
    protected TupleQueryResult parseResults(final HttpURLConnection conn)
            throws Exception {

        final String contentType = conn.getContentType();

        final TupleQueryResultFormat format = TupleQueryResultFormat
                .forMIMEType(contentType);

        if (format == null)
            throw new IOException(
                    "Could not identify format for service response: serviceURI="
                            + serviceURI + ", contentType=" + contentType
                            + " : response=" + getResponseBody(conn));

        final TupleQueryResultParserFactory parserFactory = TupleQueryResultParserRegistry
                .getInstance().get(format);

        final TupleQueryResultParser parser = parserFactory.getParser();

        final TupleQueryResultBuilder handler = new TupleQueryResultBuilder();

        parser.setTupleQueryResultHandler(handler);

        parser.parse(conn.getInputStream());

        // done.
        return handler.getQueryResult();

    }
    
    protected static String getResponseBody(final HttpURLConnection conn)
            throws IOException {

        final Reader r = new InputStreamReader(conn.getInputStream());
    
        try {
    
            final StringWriter w = new StringWriter();
    
            int ch;
            while ((ch = r.read()) != -1) {
    
                w.append((char) ch);
    
            }
    
            return w.toString();
        
        } finally {
            
            r.close();
            
        }
        
    }

    /**
     * Options for the query.
     */
    private static class QueryOptions {

        /** The URL of the SPARQL end point. */
        public String serviceURL = null;
        
        /** The HTTP method (GET, POST, etc). */
        public String method = "GET";

        /** The accept header. */
        public String acceptHeader;
        
        /**
         * The SPARQL query (this is a short hand for setting the
         * <code>query</code> URL query parameter).
         */
        public String queryStr = null;
        
        /** Request parameters to be formatted as URL query parameters. */
        public Map<String,String[]> requestParams;
        
        /**
         * The Content-Type (iff there will be a request body).
         */
        public String contentType = null;
        
        /**
         * The data to send as the request body (optional).
         */
        public byte[] data = null;
        
        /** The connection timeout (ms) -or- ZERO (0) for an infinite timeout. */
        public int timeout = 0;

        public QueryOptions(final String serviceURL) {
        
            this.serviceURL = serviceURL;
            
        }
        
    }

    /**
     * Connect to a SPARQL end point (GET or POST query only).
     * 
     * @param opts
     *            The query request.
     * @param requestPath
     *            The request path, including the leading "/".
     * 
     * @return The connection.
     */
    protected HttpURLConnection doSparqlQuery(final QueryOptions opts)
            throws Exception {

        /*
         * Generate the fully formed and encoded URL.
         */

        final StringBuilder urlString = new StringBuilder(opts.serviceURL);

        if (opts.queryStr != null) {

            if (opts.requestParams == null) {

                opts.requestParams = new LinkedHashMap<String, String[]>();

            }
            
            opts.requestParams.put("query", new String[] { opts.queryStr });

        }

        addQueryParams(urlString, opts.requestParams);

        if (log.isDebugEnabled()) {
            log.debug("*** Request ***");
            log.debug(serviceURI);
            log.debug(opts.queryStr);
        }

        HttpURLConnection conn = null;
        try {

            // conn = doConnect(urlString.toString(), opts.method);
            final URL url = new URL(urlString.toString());
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(opts.method);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setReadTimeout(opts.timeout);
            conn.setRequestProperty("Accept", opts.acceptHeader);
            if (log.isDebugEnabled())
                log.debug("Accept: " + opts.acceptHeader);
            
            if (opts.contentType != null) {

                if (opts.data == null)
                    throw new AssertionError();

                final String contentLength = Integer.toString(opts.data.length);
                
                conn.setRequestProperty("Content-Type", opts.contentType);

                conn.setRequestProperty("Content-Length", contentLength);

                if (log.isDebugEnabled()) {
                    log.debug("Content-Type: " + opts.contentType);
                    log.debug("Content-Length: " + contentLength);
                }

                final OutputStream os = conn.getOutputStream();
                try {
                    os.write(opts.data);
                    os.flush();
                } finally {
                    os.close();
                }

            }

            // connect.
            conn.connect();

            return conn;

        } catch (Throwable t) {
            /*
             * If something goes wrong, then close the http connection.
             * Otherwise, the connection will be closed by the caller.
             */
            try {
                // clean up the connection resources
                if (conn != null)
                    conn.disconnect();
            } catch (Throwable t2) {
                // ignored.
            }
            throw new RuntimeException("serviceUrl=" + opts.serviceURL + " : "
                    + t, t);
        }

    }

    protected HttpURLConnection checkResponseCode(final HttpURLConnection conn)
            throws IOException {
        final int rc = conn.getResponseCode();
        if (rc < 200 || rc >= 300) {
            // conn.disconnect();
            throw new IOException("Status Code=" + rc + ", Status Line="
                    + conn.getResponseMessage() + ", Response="
                    + getResponseBody(conn));
        }

        if (log.isDebugEnabled()) {
            /*
             * write out the status list, headers, etc.
             */
            log.debug("*** Response ***");
            log.debug("Status Line: " + conn.getResponseMessage());
        }
        return conn;
    }

    /**
     * Add any URL query parameters.
     */
    private void addQueryParams(final StringBuilder urlString,
            final Map<String, String[]> requestParams)
            throws UnsupportedEncodingException {
        boolean first = true;
        for (Map.Entry<String, String[]> e : requestParams.entrySet()) {
            urlString.append(first ? "?" : "&");
            first = false;
            final String name = e.getKey();
            final String[] vals = e.getValue();
            if (vals == null) {
                urlString.append(URLEncoder.encode(name, UTF8));
            } else {
                for (String val : vals) {
                    urlString.append(URLEncoder.encode(name, UTF8));
                    urlString.append("=");
                    urlString.append(URLEncoder.encode(val, UTF8));
                }
            }
        } // next Map.Entry

    }

}