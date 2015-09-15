package com.anpi.app.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.Address;
import javax.mail.Message;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.util.StringUtils;

public class CommonUtil
{

	private static final Logger	logger	= Logger.getLogger(CommonUtil.class);
	
	

	/**
	 * Convert Date to utc string.
	 */
	public static String convertToUTCString(Date d)
	{
		DateTime dt = new DateTime(new Date());
		dt = dt.withZone(DateTimeZone.forID("UTC"));
		System.out.println("UTC Date in String: "
				+ dt.toString("yyyy-MM-dd HH:mm:ss"));
		return dt.toString("yyyy-MM-dd HH:mm:ss");
	}

	/**
	 * This method would print FROM,TO and SUBJECT of the message
	 * 
	 * @param m
	 *            the Message object
	 * @throws Exception
	 *             the exception
	 */
	public static void writeEnvelope(Message m) throws Exception
	{
		System.out.println("This is the message envelope");
		System.out.println("---------------------------");
		Address[] a;
		// FROM
		if ((a = m.getFrom()) != null)
		{
			for (int j = 0; j < a.length; j++)
			{
				System.out.println("FROM: " + a[j].toString());
			}
		}
		// TO
		if ((a = m.getRecipients(Message.RecipientType.TO)) != null)
		{
			for (int j = 0; j < a.length; j++)
			{
				System.out.println("TO: " + a[j].toString());
			}
		}
		// SUBJECT
		if (m.getSubject() != null)
		{
			System.out.println("SUBJECT: " + m.getSubject());
		}
	}

	/**
	 * Gets the string from input stream.
	 * 
	 * @param is
	 *            the inputStream
	 * @return the string from input stream
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
	 * 
	 * @param alist
	 *            the arraylist of strings
	 * @return the string
	 */
	public static String convertToCsv(ArrayList<String> alist)
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
		// returnThis = returnThis.substring(0, returnThis.lastIndexOf(","));
		return returnThis;
	}

	/**
	 * Convert to csv.
	 * 
	 * @param alist
	 *            the list of strings
	 * @return the string
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
		// returnThis = returnThis.substring(0, returnThis.lastIndexOf(","));
		return returnThis;
	}

	/**
	 * Extract email address from the String.
	 * 
	 * @param text
	 *            the String
	 * @return the email Address string
	 */
	public static String extractAddr(String text)
	{
		System.out.println("Entering Email Util" + text);
		logger.info("Entering Email Util" + text);
		String addrStr = "";
		Pattern p = Pattern.compile(
				"\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}\\b",
				Pattern.CASE_INSENSITIVE);
		Matcher matcher = p.matcher(text);
		int count = 0;
		while (matcher.find())
		{
			// System.out.println("matcher-"+matcher.group());
			addrStr += (matcher.group() + ";");
			count++;
		}
		System.out.println("Count" + count);
		if (count == 1)
		{
			addrStr = addrStr.replaceAll(";", " ");
		}
		System.out.println("addStr-" + addrStr);
		logger.info("addStr-" + addrStr);
		System.out.println("Exiting Email Util");
		logger.info("Exiting Email Util");
		return addrStr;
	}

	/**
	 * This method formats the phone number
	 * 
	 * @param phoneNumber
	 *            the phone number
	 * @return the formatted phone number
	 */
	public static String formatNumber(String phoneNumber, String delimiter)
	{
		String[] phoneNumberArr = phoneNumber.split(",");
		String finalPhoneNumber = "";
		for (int i = 0; i < phoneNumberArr.length; i++)
		{
			String phoneFmt = phoneNumberArr[i];
			String formattedNumber = "";
			Pattern p = Pattern.compile(("\\d+\\.?"));
			Matcher m = p.matcher(phoneNumberArr[i]);
			if (m.matches())
			{
				System.out
						.println("Format phone Number-->" + phoneNumberArr[i]);
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
			else
			{
				formattedNumber = phoneFmt;
			}
			if (i == 0)
			{
				finalPhoneNumber = formattedNumber;
			}
			else
			{
				finalPhoneNumber += delimiter + formattedNumber;
			}
		}
		System.out.println(finalPhoneNumber);
		logger.info(finalPhoneNumber);
		return finalPhoneNumber;
	}

	public static String getAddressFromMessage(Address[] addressArr) {
		String addressStr = "";
		List<String> addressList = new ArrayList<String>();
		if (addressArr != null)
		{
			for (Address address : addressArr)
			{
				System.out.println("ccAddress:" + CommonUtil.extractAddr(address.toString()));
				addressList.add(extractAddr(address.toString()));
			}
			addressStr = StringUtils.collectionToCommaDelimitedString(addressList);
		}
		return addressStr;
	}
}
