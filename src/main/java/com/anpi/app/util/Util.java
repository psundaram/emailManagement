/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.anpi.app.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedList;

/**
 * Util class for getting string from inputstream, converting list to string
 *
 */
public class Util {
    
    /**
     * Gets the string from input stream.
     *
     * @param is the inputStream
     * @return the string from input stream
     */
    public String getStringFromInputStream(InputStream is) {
 
		BufferedReader br = null;
		StringBuilder sb = new StringBuilder();
 
		String line;
		try {
 
			br = new BufferedReader(new InputStreamReader(is));
			while ((line = br.readLine()) != null) {
				sb.append(line);
			}
 
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
 
		return sb.toString();
 
	}
    
    /**
     * Convert to csv.
     *
     * @param alist the arraylist of strings
     * @return the string
     */
    public static String convertToCsv(ArrayList<String> alist) {
        String returnThis = "";
        for(String str : alist){
            if(returnThis.equals("")){
                returnThis = str;
            } else {
                returnThis = returnThis+","+str;
            }
        }
//        returnThis = returnThis.substring(0, returnThis.lastIndexOf(","));
        return returnThis;
    }
    
    /**
     * Convert to csv.
     *
     * @param alist the list of strings
     * @return the string
     */
    public static String convertToCsv(LinkedList<String> alist) {
        String returnThis = "";
        for(String str : alist){
            if(returnThis.equals("")){
                returnThis = str;
            } else {
                returnThis = returnThis+","+str;
            }
        }
//        returnThis = returnThis.substring(0, returnThis.lastIndexOf(","));
        return returnThis;
    }
}
