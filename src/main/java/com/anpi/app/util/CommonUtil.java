package com.anpi.app.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.Address;
import javax.mail.Message;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.util.StringUtils;

import com.google.common.base.Strings;

public class CommonUtil
{

	private static final Logger	logger	= Logger.getLogger(CommonUtil.class);
	
	/** Get value corresponding to key for map */
	public static String getValueForMap(Map<String, String> map,String key) {
		
		String	value	= "";
		
		if (map.containsKey(key) && !Strings.isNullOrEmpty(map.get(key))) {
			value =  map.get(key);
		}
		
		return value;
	}

	/** 
	 * Check If map is empty 
	 */
	public static boolean checkConfigIsEmpty(Map<String,String> map) {
		
		boolean isEmpty = false;
		if (map == null || map.isEmpty())
			isEmpty = true;
		
		return isEmpty;
	}
	
	
	/** Replace dollar sign */
	public static String replaceDollarSign(String value) {
		String replacedValue = "";
		Pattern pattern = Pattern.compile("$");
		Matcher matcher = pattern.matcher(value);
		while (matcher.find()) {
			System.out.println("matcher");
			replacedValue = value.replaceAll("\\$", "\\\\\\$");
		}
		return replacedValue;
	}
	
	
	/**
	 * Convert Date to utc string.
	 */
	public static String convertToUTCString(Date d)
	{
		DateTime dt = new DateTime(new Date());
		dt = dt.withZone(DateTimeZone.forID("UTC"));
		
		logger.info("UTC Date in String: " + dt.toString("yyyy-MM-dd HH:mm:ss"));
		return dt.toString("yyyy-MM-dd HH:mm:ss");
	}

	
	/**
	 * This method would print FROM,TO and SUBJECT of the message
	 */
	public static void writeEnvelope(Message m) throws Exception
	{
		logger.info("This is the message envelope");
		logger.info("---------------------------");
		Address[] a;
		// FROM
		if ((a = m.getFrom()) != null)
		{
			for (int j = 0; j < a.length; j++)
			{
				logger.info("FROM: " + a[j].toString());
			}
		}
		// TO
		if ((a = m.getRecipients(Message.RecipientType.TO)) != null)
		{
			for (int j = 0; j < a.length; j++)
			{
				logger.info("TO: " + a[j].toString());
			}
		}
		// SUBJECT
		if (m.getSubject() != null)
		{
			logger.info("SUBJECT: " + m.getSubject());
		}
	}

	
	/**
	 * Gets the string from input stream.
	 */
	public static String getStringFromInputStream(InputStream is)
	{
		BufferedReader br = null;
		StringBuilder sb = new StringBuilder();
		String line;
		try
		{
			br = new BufferedReader(new InputStreamReader(is));
			while ((line = br.readLine()) != null)
			{
				sb.append(line);
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		finally
		{
			if (br != null)
			{
				try
				{
					br.close();
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
			}
		}
		return sb.toString();
	}

	
	/**
	 * Convert to csv.
	 */
	public static String convertToCsv(List<String> alist)
	{
		String returnThis = "";
		for (String str : alist)
		{
			if (returnThis.equals(""))
			{
				returnThis = str;
			}
			else
			{
				returnThis = returnThis + "," + str;
			}
		}
		return returnThis;
	}

	
	/**
	 * Convert to csv.
	 */
	public static String convertToCsv(LinkedList<String> alist)
	{
		String returnThis = "";
		for (String str : alist)
		{
			if (returnThis.equals(""))
			{
				returnThis = str;
			}
			else
			{
				returnThis = returnThis + "," + str;
			}
		}
		return returnThis;
	}

	
	/**
	 * Extract email address from the html string.
	 */
	public static String extractAddr(String text) {
		logger.info("Entering Email Util" + text);

		String	addrStr	= "";
		Pattern	p		= Pattern.compile("\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}\\b",
								Pattern.CASE_INSENSITIVE);
		Matcher	matcher	= p.matcher(text);
		int		count	= 0;
		
		while (matcher.find()) {
			addrStr += (matcher.group() + ";");
			count++;
		}
		
		logger.info("Count" + count);
		if (count == 1) {
			addrStr = addrStr.replaceAll(";", " ");
		}
		
		logger.info("Exiting Email Util" + addrStr) ;
		return addrStr;
	}
	

	/**
	 * This method formats the phone number
	 * 
	 * @param phoneNumber
	 *            the phone number to be formated
	 * @return the formatted phone number
	 */
	public static String formatNumber(String phoneNumber, String delimiter)
	{
		String[] phoneNumberArr = phoneNumber.split(",");
		String finalPhoneNumber = "";
		
		for (int i = 0; i < phoneNumberArr.length; i++)
		{
			String	phoneFmt		= phoneNumberArr[i];
			String	formattedNumber	= "";
			Pattern	p				= Pattern.compile(("\\d+\\.?"));
			Matcher	m				= p.matcher(phoneNumberArr[i]);
			if (m.matches())
			{
				logger.info("Format phone Number-->" + phoneNumberArr[i]);
				
				if (phoneFmt.length() == 10)
				{
					formattedNumber = String.format("(%s) %s-%s",
							phoneFmt.substring(0, 3), phoneFmt.substring(3, 6),
							phoneFmt.substring(6, 10));
				}
				else if (phoneFmt.length() == 11 && phoneFmt.startsWith("1"))
				{
					formattedNumber = String.format("+1(%s) %s-%s",
							phoneFmt.substring(1, 4), phoneFmt.substring(4, 7),
							phoneFmt.substring(7, 11));
				}
				else
				{
					formattedNumber = phoneFmt;
				}
			}
			else {
				formattedNumber = phoneFmt;
			}
			if (i == 0) {
				finalPhoneNumber = formattedNumber;
			}
			else {
				finalPhoneNumber += delimiter + formattedNumber;
			}
		}
		logger.info(finalPhoneNumber);
		
		return finalPhoneNumber;
	}

	
	/** Parse address from Address object */
	public static String getAddressFromMessage(Address[] addressArr) {
		String 		 addressStr  = "";
		List<String> addressList = new ArrayList<String>();
		
		if (addressArr != null)
		{
			for (Address address : addressArr)
			{
				addressList.add(extractAddr(address.toString()));
			}
			addressStr = StringUtils.collectionToCommaDelimitedString(addressList);
		}
		return addressStr;
	}
}
