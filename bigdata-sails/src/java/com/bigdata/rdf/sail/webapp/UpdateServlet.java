package com.bigdata.rdf.sail.webapp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParser;
import org.openrdf.rio.RDFParserFactory;
import org.openrdf.rio.RDFParserRegistry;
import org.openrdf.rio.helpers.RDFHandlerBase;
import org.openrdf.sail.SailException;

import com.bigdata.journal.ITx;
import com.bigdata.rdf.sail.BigdataSail;
import com.bigdata.rdf.sail.BigdataSail.BigdataSailConnection;
import com.bigdata.rdf.sail.bench.NanoSparqlServer;
import com.bigdata.rdf.store.AbstractTripleStore;
import com.bigdata.util.httpd.NanoHTTPD;

public class UpdateServlet extends BigdataServlet {
	
	/**
	 * The logger for the concrete {@link NanoSparqlServer} class.  The {@link NanoHTTPD}
	 * class has its own logger.
	 */
	static private final Logger log = Logger.getLogger(BigdataServlet.class); 
	
    public UpdateServlet() {
        getContext().registerServlet(this);
    }

	protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
		doPut(req, resp);
	}

	protected void doPut(HttpServletRequest req, HttpServletResponse resp) {
		try {
			doPostWithBody(req,  resp);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
    	try {
			buildResponse(resp, HTTP_BADREQUEST, MIME_TEXT_PLAIN, 
					"GET method not valid for update");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

    /**
     * POST with request body containing statements to be inserted.
     * 
     * @param req
     *            The request.
     *
     * @return The response.
     * 
     * @throws Exception
     */
    private void doPostWithBody(final HttpServletRequest req, final HttpServletResponse resp) throws Exception {
        
        final String baseURI = "";// @todo baseURI query parameter?
        
        final String namespace = getNamespace(req.getRequestURI());

        final String contentType = req.getContentType();

        if (false && contentType == null)
            throw new UnsupportedOperationException();

        if (log.isInfoEnabled())
            log.info("Request body: " + contentType);

        final RDFFormat format = RDFFormat.forMIMEType(contentType);

        if (format == null) {
        	buildResponse(resp, HTTP_BADREQUEST, MIME_TEXT_PLAIN, 
        			"Content-Type not recognized as RDF: " + contentType);
        	
        	return;
        }

        if (log.isInfoEnabled())
            log.info("RDFFormat=" + format);
        
        final RDFParserFactory rdfParserFactory = RDFParserRegistry
                .getInstance().get(format);

        if (rdfParserFactory == null) {
        	buildResponse(resp, HTTP_INTERNALERROR, MIME_TEXT_PLAIN,
                    "Parser not found: Content-Type=" + contentType);
        	
        	return;
        }

        try {

            // resolve the default namespace.
            final AbstractTripleStore tripleStore = (AbstractTripleStore) getIndexManager()
                    .getResourceLocator().locate(namespace, ITx.UNISOLATED);

            if (tripleStore == null) {
            	buildResponse(resp, HTTP_BADREQUEST, MIME_TEXT_PLAIN,
                        "Not found: namespace=" + namespace);
            	return;
            }

            final AtomicLong nmodified = new AtomicLong(0L);

            // Wrap with SAIL.
            final BigdataSail sail = new BigdataSail(tripleStore);
            BigdataSailConnection conn = null;
            try {

                sail.initialize();
                conn = sail.getConnection();

                /*
                 * There is a request body, so let's try and parse it.
                 */

                final RDFParser rdfParser = rdfParserFactory.getParser();

                rdfParser.setValueFactory(tripleStore.getValueFactory());

                rdfParser.setVerifyData(true);

                rdfParser.setStopAtFirstError(true);

                rdfParser
                        .setDatatypeHandling(RDFParser.DatatypeHandling.IGNORE);

                rdfParser.setRDFHandler(new AddStatementHandler(conn,nmodified));

                /*
                 * Run the parser, which will cause statements to be inserted.
                 */
                rdfParser.parse(debugStream(req.getInputStream()), baseURI);

                // Commit the mutation.
                conn.commit();

                XMLBuilder xml = new XMLBuilder();
                xml.root("data")
                	.attr("modified", nmodified.get())
                	.close();
                
                buildResponse(resp, HTTP_OK, MIME_TEXT_XML, xml.toString());
                
                return;

            } finally {

                if (conn != null)
                    conn.close();

//                sail.shutDown();
                
            }

        } catch (Exception ex) {

            // Will be rendered as an INTERNAL_ERROR.
            throw new RuntimeException(ex);
            
        }

    }
    
    
    /**
     * POST with URIs of resources to be inserted.
     * 
     * @param req
     *            The request.
     *
     * @return The response.
     * 
     * @throws Exception
     */
    private void doPostWithURIs(final HttpServletRequest req, final HttpServletResponse resp) throws Exception {
        
        final String namespace = getNamespace(req.getRequestURI());

        final String contentType = req.getContentType();

        final String[] uris = req.getParameterValues("uri");
        
        if (uris == null)
            throw new UnsupportedOperationException();

        if (uris.length == 0) {
        	buildResponse(resp, HTTP_OK, MIME_TEXT_PLAIN,
                    "0 statements modified");
        	
        	return;
        }

        if (log.isInfoEnabled())
            log.info("URIs: " + uris);

        // Before we do anything, make sure we have valid URLs.
        final Vector<URL> urls = new Vector<URL>(uris.length);
        for (String uri : uris) {
            urls.add(new URL(uri));
        }

        try {

            // resolve the default namespace.
            final AbstractTripleStore tripleStore = (AbstractTripleStore) getIndexManager()
                    .getResourceLocator().locate(namespace, ITx.UNISOLATED);

            if (tripleStore == null) {
            	buildResponse(resp, HTTP_BADREQUEST, MIME_TEXT_PLAIN,
                        "Not found: namespace=" + namespace);
            	
            	return;
            }

            final AtomicLong nmodified = new AtomicLong(0L);

            // Wrap with SAIL.
            final BigdataSail sail = new BigdataSail(tripleStore);
            BigdataSailConnection conn = null;
            try {

                conn = sail.getConnection();

                for (URL url : urls) {

                    HttpURLConnection hconn = null;
                    try {

                        hconn = (HttpURLConnection) url.openConnection();
                        hconn.setRequestMethod("GET");
                        hconn.setReadTimeout(0);// no timeout? http param?

                        /*
                         * There is a request body, so let's try and parse it.
                         */

                        final RDFFormat format = RDFFormat
                                .forMIMEType(contentType);

                        if (format == null) {
                        	buildResponse(resp, HTTP_BADREQUEST,
                                    MIME_TEXT_PLAIN,
                                    "Content-Type not recognized as RDF: "
                                            + contentType);
                        	
                        	return;
                        }

                        final RDFParserFactory rdfParserFactory = RDFParserRegistry
                                .getInstance().get(format);

                        if (rdfParserFactory == null) {
                        	buildResponse(resp, HTTP_INTERNALERROR,
                                    MIME_TEXT_PLAIN,
                                    "Parser not found: Content-Type="
                                            + contentType);
                        	
                        	return;
                        }

                        final RDFParser rdfParser = rdfParserFactory
                                .getParser();

                        rdfParser
                                .setValueFactory(tripleStore.getValueFactory());

                        rdfParser.setVerifyData(true);

                        rdfParser.setStopAtFirstError(true);

                        rdfParser
                                .setDatatypeHandling(RDFParser.DatatypeHandling.IGNORE);

                        rdfParser.setRDFHandler(new AddStatementHandler(conn, nmodified));

                        /*
                         * Run the parser, which will cause statements to be
                         * inserted.
                         */
                        
                        rdfParser.parse(req.getInputStream(), url
                                .toExternalForm()/* baseURL */);

                    } finally {
                        
                        if (hconn != null)
                            hconn.disconnect();

                    } // next URI.

                }

                // Commit the mutation.
                conn.commit();

                buildResponse(resp, HTTP_OK, MIME_TEXT_PLAIN, nmodified.get()
                        + " statements modified.");

            } finally {

                if (conn != null)
                    conn.close();

//                sail.shutDown();

            }

        } catch (Exception ex) {

            // Will be rendered as an INTERNAL_ERROR.
            throw new RuntimeException(ex);
            
        }

    }
    

    InputStream debugStream(final InputStream instr) throws IOException {
    	if (true) {
	    	ByteArrayOutputStream outstr = new ByteArrayOutputStream();
	    	
	    	byte[] buf = new byte[1024];
	    	int rdlen = 0;
	    	while (rdlen >= 0) {
	    		rdlen = instr.read(buf);
	    		if (rdlen > 0) {
	    			outstr.write(buf, 0, rdlen);
	    		}
	    	}
	    	
	    	InputStreamReader rdr = new InputStreamReader(new ByteArrayInputStream(outstr.toByteArray()));
	    	char[] chars = new char[outstr.size()];
	    	rdr.read(chars);
	    	System.out.println("debugStream, START");
	    	System.out.println(chars);
	    	System.out.println("debugStream, END");
	    	
	    	return new ByteArrayInputStream(outstr.toByteArray());
    	} else {
    		return instr;
    	}
    }
   
    /**
     * Helper class adds statements to the sail as they are visited by a parser.
     */
    private static class AddStatementHandler extends RDFHandlerBase {

        private final BigdataSailConnection conn;
        private final AtomicLong nmodified;
        
        public AddStatementHandler(final BigdataSailConnection conn,
                final AtomicLong nmodified) {
            this.conn = conn;
            this.nmodified = nmodified;
        }

        public void handleStatement(Statement stmt) throws RDFHandlerException {

            try {

                conn.addStatement(//
                        stmt.getSubject(), //
                        stmt.getPredicate(), //
                        stmt.getObject(), //
                        (Resource[]) (stmt.getContext() == null ? new Resource[] { }
                                : new Resource[] { stmt.getContext() })//
                        );

            } catch (SailException e) {

                throw new RDFHandlerException(e);

            }

            nmodified.incrementAndGet();

        }

    }


}