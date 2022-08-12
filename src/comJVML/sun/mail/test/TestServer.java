/*
 * Copyright (c) 2009, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package com.sun.mail.test;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import env.java.net.ServerSocket;
import env.java.net.Socket;
import env.java.net.InetSocketAddress;
import javax.net.ssl.*;

/**
 * A simple server for testing.
 *
 * Inspired by, and derived from, POP3Server by sbo.
 *
 * @author sbo
 * @author Bill Shannon
 */
public final class TestServer extends Thread {

    /** Server socket. */
    private ServerSocket serverSocket;

    /** Keep on? */
    private volatile boolean keepOn;

    /** Protocol handler. */
    private final ProtocolHandler handler;

    private List<Thread> clients = new ArrayList<Thread>();

    /**
     * Test server.
     *
     * @param handler	the protocol handler
     */
    public TestServer(final ProtocolHandler handler) throws IOException {
	this(handler, false);
    }

    /**
     * Test server.
     *
     * @param handler	the protocol handler
     * @param isSSL	create SSL sockets?
     */
    public TestServer(final ProtocolHandler handler, final boolean isSSL)
				throws IOException {
        this.handler = handler;

	/*
	 * Allowing the JDK to pick a port number sometimes results in it
	 * picking a number that's already in use by another process, but
	 * no error is returned.  Picking it ourself allows us to make sure
	 * that it's not used before we pick it.  Hopefully the socket
	 * creation will fail if the port is already in use.
	 *
	 * XXX - perhaps we should use Random to choose a port number in
	 * the emphemeral range, in case a lot of low port numbers are
	 * already in use.
	 */
	for (int port = 49152; port < 50000 /*65535*/; port++) {
	    /*
	    if (isListening(port))
		continue;
	    */
	    try {
		serverSocket = createServerSocket(port, isSSL);
		return;
	    } catch (IOException ex) {
		// ignore
	    }
	}
	throw new RuntimeException("Can't find unused port");
    }

    private static ServerSocket createServerSocket(int port, boolean isSSL)
				throws IOException {
	ServerSocket ss;
	if (isSSL) {
	    SSLServerSocketFactory sf =
		(SSLServerSocketFactory)SSLServerSocketFactory.getDefault();
	    //ss = sf.createServerSocket(port); //DK
	    ss = new ServerSocket(port);
	    
	    // enable only the anonymous cipher suites so we don't have to
	    // create a server certificate
	    List<String> anon = new ArrayList<>();
	    String[] suites = sf.getSupportedCipherSuites();
	    for (int i = 0; i < suites.length; i++) {
			if (suites[i].indexOf("_anon_") >= 0) {
			    anon.add(suites[i]);
			}
	    }
	     //((SSLServerSocket)ss).setEnabledCipherSuites(anon.toArray(new String[anon.size()])); // Commented by DK
	} else
	    ss = new ServerSocket(port);
	return ss;
    }

    /**
     * Return the port the server is listening on.
     */
    public int getPort() {
    	return serverSocket.getLocalPort();
    }

    /**
     * Exit server.
     */
    public void quit() {
        try {
            keepOn = false;
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                serverSocket = null;
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
	super.start();
	// don't return until server is really listening
	// XXX - this might not be necessary
	for (int tries = 0; tries < 10; tries++) {
	    if (isListening(getPort())) {
		return;
	    }
	    try {
		Thread.sleep(100);
	    } catch (InterruptedException ex) { }
	}
	throw new RuntimeException("Server isn't listening");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        try {
            keepOn = true;

            while (keepOn) {
                try {
                    final Socket clientSocket = serverSocket.accept();
                    final ProtocolHandler pHandler = (ProtocolHandler)handler.clone();
                    pHandler.setClientSocket(clientSocket);
                    Thread t = new Thread(pHandler);
		    synchronized (clients) {
			clients.add(t);
		    }
		    t.start();
                } catch (final IOException e) {
                    //e.printStackTrace();
		} catch (NullPointerException nex) {
		    // serverSocket can be set to null before we could check
                }
            }
        } finally {
            quit();
        }
    }

    /**
     * Return number of clients ever created.
     */
    public int clientCount() {
	synchronized (clients) {
	    // isListening creates a client that we don't count
	    return clients.size() - 1;
	}
    }

    /**
     * Wait for at least n clients to terminate.
     */
    public void waitForClients(int n) {
	if (n > clientCount())
	    throw new RuntimeException("not that many clients");
	for (;;) {
	    int num = -1;	// ignore isListening client
	    synchronized (clients) {
		for (Thread t : clients) {
		    if (!t.isAlive()) {
			if (++num >= n)
			    return;
		    }
		}
	    }
	    try {
		Thread.sleep(100);
	    } catch (InterruptedException ex) { }
	}
    }

    private boolean isListening(int port) {
	try {
	    Socket s = new Socket();
	    //s.connect(new InetSocketAddress("localhost", port), 100);
	    s.connect(new InetSocketAddress("localhost", port));
	    // it's listening!
	    s.close();
	    return true;
	} catch (Exception ex) {
	    //System.out.println(ex);
	}
	return false;
    }
}
