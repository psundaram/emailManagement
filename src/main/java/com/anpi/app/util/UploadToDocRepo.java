package com.anpi.app.util;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import com.anpi.app.constants.Constants;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Used to upload files to the document repository
 */
@SuppressWarnings("deprecation")
public class UploadToDocRepo {

	private static final Logger logger = Logger.getLogger(UploadToDocRepo.class);
	 
    /**
	 * A mock SSL Class to trust all HTTPS Certificates
	 */
	private static ClientConnectionManager getSchemeFactory() throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", 80, PlainSocketFactory.getSocketFactory()));
        schemeRegistry.register(new Scheme("https", 443, new FakeSSLSocketFactory()));
        ClientConnectionManager cm = new SingleClientConnManager(schemeRegistry);
        return cm;
    }
    

	/**
	 * Uploads file to the document repository.
	 */
	@SuppressWarnings("resource")
	public static String uploadFile(String fileToUpload, String contextPath) throws IOException, UnrecoverableKeyException, KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
		logger.info("Entering uploadFile api" + "\nfileToUpload:"+fileToUpload+"contextPath:"+contextPath);
		
		/* Context Path will be of the form : "emails_sent?partner_id=ANPI"*/
		String				postUrl		= Constants.DOC_REPO_PATH + contextPath;
		DefaultHttpClient	httpclient	= new DefaultHttpClient(getSchemeFactory());
		HttpPost			httppost	= new HttpPost(postUrl);
		MultipartEntity		mpEntity	= new MultipartEntity();
		File				file		= new File(fileToUpload);
		ContentBody			cbFile		= new FileBody(file);
		
		mpEntity.addPart("user_file", cbFile);
		httppost.setEntity(mpEntity);
		
		logger.info("httppost =>"+httppost.toString());
		
		CloseableHttpResponse	httpResponse= httpclient.execute(httppost);
		String					response	= EntityUtils.toString(httpResponse.getEntity());
        
    	// parse the json response
    	JsonParser	parser	= new JsonParser();
    	JsonObject	jo		= new JsonObject();
    				jo		= (JsonObject) parser.parse(response);
    	JsonElement	uuid	= jo.get("uuid");
    	String		uuidStr	= uuid.getAsString();
    	
    	logger.info("filepath::"+Constants.DOC_REPO_PATH+uuidStr);
        return Constants.DOC_REPO_PATH+uuidStr;
    }
	
}
