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
	 */
	

	public static Map<String, String> getInputFromUrl(String url) throws IOException, JSONException {
		logger.info("Entering getInputFromUrl");
		
		Map<String, String>			listOfParameter		= new HashMap<String, String>();
		Gson						gson				= new Gson();
		Type						collectionType		= new TypeToken<Map<String, String>>() {}.getType();

		InputStream					is					= new URL(url).openStream();
		JSONObject					json				= getJSONFromInputStream(is);
		Map<String, String>			listOfParameterTest	= gson.fromJson(json.toString(), collectionType);

		listOfParameter.putAll(listOfParameterTest);
		
		logger.info("Exiting getInputFromUrl-->"+listOfParameter);
		return listOfParameter;
	}
	
	
	public static net.sf.json.JSONObject getJSONFromInputStream(InputStream inputStream)
			throws IOException {
		
		StringBuilder			sb		= new StringBuilder();
		net.sf.json.JSONObject	jsonO	= null;
		String					line	= null;
		
		InputStreamReader		reader	= new InputStreamReader(inputStream);
		BufferedReader			in		= new BufferedReader(reader);
		
		while ((line = in.readLine()) != null) {
			sb.append(line);
		}
		jsonO = net.sf.json.JSONObject.fromObject(sb.toString());
		return jsonO;
	}

	
	public static Map<String, String> getJsonFromUrl(String url) throws MalformedURLException, IOException, ParseException
	{
		Map<String, String>		listOfParameter	= new HashMap<String, String>();
		Gson					gson			= new Gson();
		Type					collectionType	= new TypeToken<HashMap<String, String>>() {}.getType();
												
		logger.info("Entering URL Util - Read Json --> " + url);
		
		InputStream	is			= new URL(url).openStream();
		String		json		= IOUtils.toString(is);
		JSONArray	jsonArray	= (JSONArray) JSONValue.parseWithException(json);
		
		if (jsonArray != null && jsonArray.size() > 0)
		{
			org.json.simple.JSONObject jsonObject 	= (org.json.simple.JSONObject) jsonArray.get(0);
			Map<String, String> listOfParameterTest = gson.fromJson(jsonObject.toString(), collectionType);
			listOfParameter.putAll(listOfParameterTest);
		}
		
		logger.info("Exiting URL Util - Read Json-->" + listOfParameter);
		return listOfParameter;
	}
	
}