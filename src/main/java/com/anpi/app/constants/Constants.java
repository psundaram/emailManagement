package com.anpi.app.constants;

import com.anpi.app.util.PropUtils;

/**
 * The Class Constants.
 */
public class Constants {
	
	public static final String	NO							= "NO";
	public static final String	VFTTND						= "VFTTND"; // Value for Tag Not defined 
	public static final String	SPLITMULTIHASH				= "##########";
	public static final String	SPLITHASH					= "##";
	public static final String	NEWLINE						= "<br>";
	public static final String	COMMA						= ",";
	public static final String	VIPRESENTNOW				= "VIPresentNow";
	public static final String	VIPRESENTNOWLINK			= "VIPRESENT NOW LINK";
	public static final String	ANPI						= "ANPI";
	public static final String	CUSTOMERPORTALURL			= "CUSTOMER PORTAL URL";
	public static final String	Y							= "Y";
	public static final String	NOTIFICATION_EMAIL_ADDRESS	= "araman@anpi.com;mdinakaran@anpi.com";
	public static final String	DUMMY_PARTNER_ID			= "XXXXX"; // Dummy partner id for non-published partner
	
	  // Property configurations
	public static final String	RELAY_HOST					= PropUtils.getVal("mail.relay.config");
	public static final String	RELAY_USERNAME				= PropUtils.getVal("mail.relay.username");
	public static final String	RELAY_PASSWORD				= PropUtils.getVal("mail.relay.password");
	public static final String	MYSQL_URL					= PropUtils.getVal("mail.jdbc.url");
	public static final String	MYSQL_UNAME					= PropUtils.getVal("mail.jdbc.username");
	public static final String	MYSQL_PWD					= PropUtils.getVal("mail.jdbc.password");
	public static final String	DOC_REPO_PATH				= PropUtils.getVal("mail.upload.url");
	public static final String	TEMP_DIR_ATTACHMENTS		= PropUtils.getVal("mail.directory.attachments");
	public static final String	BCC_EMAIL_ADDRESS			= "";
//			PropUtils.getVal("mail.bcc.address");
	public static final String	API_CALL_URL				= PropUtils.getVal("api.call.url");
	public static final String	PARTNER_SMTP_DETAILS		= PropUtils.getVal("smtp.server.url");
	 
}
