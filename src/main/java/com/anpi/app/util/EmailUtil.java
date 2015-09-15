package com.anpi.app.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

/**
 * Email Util to extract email address from text
 */
public class EmailUtil {

	/**
	 * Logger for EmailUtil
	 */
	
	private static final Logger logger = Logger.getLogger(EmailUtil.class);
	
	/**
	 * Extract email address from the String.
	 *
	 * @param text the String
	 * @return the email Address string
	 */
	public static String extractAddr(String text){
		System.out.println("Entering Email Util"+text);
		logger.info("Entering Email Util" +text);
	    String addrStr="";
		Pattern p = Pattern.compile("\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}\\b",
	    	    Pattern.CASE_INSENSITIVE);
	    	Matcher matcher = p.matcher(text);
	    	
	    	int count =0;
	    	while(matcher.find()) {
	    	//	System.out.println("matcher-"+matcher.group());
	    		
	    		addrStr += (matcher.group() + ";");
	    		count++;
	    	}
	    	System.out.println("Count"+count);
	    	if(count==1){
	    		addrStr = addrStr.replaceAll(";", " ");
	    	}
	    	System.out.println("addStr-"+addrStr);
	    	logger.info("addStr-"+addrStr);
	    	System.out.println("Exiting Email Util");
			logger.info("Exiting Email Util");
	    	return addrStr;
	}
	
	/**
	 * This method formats the phone number 
	 *
	 * @param phoneNumber the phone number
	 * @return the formatted phone number
	 */
	public static String formatNumber(String phoneNumber,String delimiter) {
		String[] phoneNumberArr = phoneNumber.split(",");
		String finalPhoneNumber = "";
		for (int i = 0; i < phoneNumberArr.length; i++) {
			String phoneFmt = phoneNumberArr[i];
			String formattedNumber = "";
			Pattern p = Pattern.compile(("\\d+\\.?"));
			Matcher m = p.matcher(phoneNumberArr[i]);
			if (m.matches()) {
				System.out.println("Format phone Number-->" + phoneNumberArr[i]);
				if (phoneFmt.length() == 10) {
					formattedNumber = String.format("(%s) %s-%s", phoneFmt.substring(0, 3), phoneFmt.substring(3, 6), phoneFmt.substring(6, 10));
				} else if (phoneFmt.length() == 11 && phoneFmt.startsWith("1")) {
					formattedNumber = String.format("+1(%s) %s-%s", phoneFmt.substring(1, 4), phoneFmt.substring(4, 7), phoneFmt.substring(7, 11));
				} else {
					formattedNumber = phoneFmt;
				}
			} else {
				formattedNumber = phoneFmt;
			}
			if (i == 0) {
				finalPhoneNumber = formattedNumber;
			} else {
				finalPhoneNumber += delimiter + formattedNumber;
			}
		}
		System.out.println(finalPhoneNumber);
		logger.info(finalPhoneNumber);
		return finalPhoneNumber;
	}
}
