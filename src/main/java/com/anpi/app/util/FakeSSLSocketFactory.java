package com.anpi.app.util;


import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.conn.ssl.X509HostnameVerifier;

/**
 * Helper class to accept the self-signed certificates.
 */

public class FakeSSLSocketFactory extends SSLSocketFactory {

    /**
     * Instantiates a new fake ssl socket factory.
     *
     * @throws NoSuchAlgorithmException the no such algorithm exception
     * @throws KeyManagementException the key management exception
     * @throws KeyStoreException the key store exception
     * @throws UnrecoverableKeyException the unrecoverable key exception
     */
    public FakeSSLSocketFactory() throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
        super(trustStrategy, hostnameVerifier);
    }

    private static final X509HostnameVerifier hostnameVerifier = new X509HostnameVerifier() {
        public void verify(String host, SSLSocket ssl) throws IOException {
            // Do nothing
        }

        public void verify(String host, X509Certificate cert) throws SSLException {
            //Do nothing
        }

        public void verify(String host, String[] cns, String[] subjectAlts) throws SSLException {
            //Do nothing
        }

        public boolean verify(String s, SSLSession sslSession) {
            return true;
        }
    };

    private static final TrustStrategy trustStrategy = new TrustStrategy() {
        public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            return true;
        }
    };
}
