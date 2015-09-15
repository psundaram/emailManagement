package com.anpi.app.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Reads value from the URL
 */
public class URLReaderUtil {

	private static final Logger logger = Logger.getLogger(URLReaderUtil.class);
	
	/**
	 * This method gets the json value from a url
	 *
	 * @param partnerId the partner id
	 * @return the input from url
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws JSONException the jSON exception
	 */
	public static Map<String, String> getInputFromUrl(String url) throws IOException, JSONException {
		System.out.println("Entering getInputFromUrl");
		logger.info("Entering getInputFromUrl");
//		String address = Constants.API_CALL_URL+partnerId;
		InputStream is = new URL(url).openStream();
		JSONObject json = getJSONFromInputStream(is);
		Map<String, String> listOfParameter = new HashMap<String, String>();
		Gson gson = new Gson();
		Type collectionType = new TypeToken<Map<String, String>>() {
		}.getType();
		Map<String, String> listOfParameterTest = gson.fromJson(json.toString(), collectionType);

		listOfParameter.putAll(listOfParameterTest);
		System.out.println("Exiting getInputFromUrl-->"+listOfParameter);
		logger.info("Exiting getInputFromUrl-->"+listOfParameter);
		return listOfParameter;
	}
	
	
	
	public static net.sf.json.JSONObject getJSONFromInputStream(
			InputStream inputStream) throws IOException
	{
		StringBuilder			sb		= new StringBuilder();
		net.sf.json.JSONObject	jsonO	= null;
		String					line	= null;
		
		InputStreamReader reader 		= new InputStreamReader(inputStream);
		BufferedReader in			    = new BufferedReader(reader);
		
		while ((line = in.readLine()) != null)
		{
			sb.append(line);
		}
		
		jsonO = net.sf.json.JSONObject.fromObject(sb.toString());
		return jsonO;
	}

	
	public static HashMap<String, String> getJsonFromUrl(String url) throws MalformedURLException, IOException, ParseException
			
	{
		
		HashMap<String, String>	listOfParameter	= new HashMap<String, String>();
		Gson					gson			= new Gson();
		Type					collectionType	= new TypeToken<HashMap<String, String>>() {}.getType();
												
		System.out.println("Entering URL Util - Read Json --> " + url);
		logger.info("Entering URL Util - Read Json --> " + url);
		
		InputStream is = new URL(url).openStream();
		String json = IOUtils.toString(is);
		JSONArray jsonArray = (JSONArray) JSONValue.parseWithException(json);
		
		if (jsonArray != null && jsonArray.size() > 0)
		{
			org.json.simple.JSONObject jsonObject = (org.json.simple.JSONObject) jsonArray.get(0);
			Map<String, String> listOfParameterTest = gson.fromJson(jsonObject.toString(), collectionType);
			listOfParameter.putAll(listOfParameterTest);
		}
		System.out.println("Exiting URL Util - Read Json-->" + listOfParameter);
		logger.info("Exiting URL Util - Read Json-->" + listOfParameter);
		return listOfParameter;
	}
	
	
	/**
	 * The main method.
	 *
	 * @param args the arguments
	 * @throws Exception 
	 */
	public static void main(String args[]) throws Exception {
		getJsonFromUrl("http://10.5.3.233:8080/partner/api/partners?id=1");
		System.out.println("" + getInputFromUrl("1"));
	}
}