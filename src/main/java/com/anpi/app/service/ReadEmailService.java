package com.anpi.app.service;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import net.sf.json.JSONException;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.json.simple.parser.ParseException;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.anpi.app.constants.Constants;
import com.anpi.app.dao.ReadEmailDAO;
import com.anpi.app.domain.EmailCredits;
import com.anpi.app.domain.TagMapDTO;
import com.anpi.app.util.CommonUtil;
import com.anpi.app.util.DbConnect;
import com.anpi.app.util.URLReaderUtil;
import com.google.common.base.Strings;

@Component
public class ReadEmailService {
	
	private static final Logger	logger			= Logger.getLogger(ReadEmailService.class);
	
	@Autowired
	ReadEmailDAO readEmailDAO;
	
	/** Set properies based on emailCredit object */
	public Properties getProperties(EmailCredits emailCredits)
	{
		Properties properties = new Properties();
		
		properties.put("mail.store.protocol", "pop3");
		properties.put("mail.pop3.host", emailCredits.getPopHost());
		properties.put("mail.smtp.auth", "none");
		properties.put("mail.smtp.starttls.enable", "true");
		properties.put("mail.smtp.host", "smtp.anpi.com");
		properties.put("mail.smtp.port", "25");
		
		return properties;
	}
	
	
	/**
	 *  Look for configuration based on email name and then compose message
	 */
	public Map<String,String> getConfiguration(Map<String, String> elementsForMessage)
			throws Exception {
		
		logger.info("Entering createNoAttachmentMessage");
		
		Map<String, String>	configMap	= new HashMap<String, String>();
		String				setId		= CommonUtil.getValueForMap(elementsForMessage, "SetID");		;
		String				partnerId	= CommonUtil.getValueForMap(elementsForMessage, "PartnerID");
		String				isLegacy	= CommonUtil.getValueForMap(elementsForMessage, "isLegacy");
		String				customerId	= CommonUtil.getValueForMap(elementsForMessage, "CustomerID");
		String				orderId		= CommonUtil.getValueForMap(elementsForMessage, "OrderID");
		String				emailName	= StringUtils.deleteWhitespace(CommonUtil.getValueForMap(
												elementsForMessage, "EmailName"));
		
		/* Look for configurations based on the orderID, followed by CustomerID,
		 * followed by the SetID and then PartnerID. If configuration is empty,
		 * look configuration for other parameter */
		
//		if (!Strings.isNullOrEmpty(orderId))
//			configMap = relayEmailDAO.getConfigForOrderId(orderId, emailName);
		
//		if (checkConfigIsEmpty() && !Strings.isNullOrEmpty(customerId))
//			configMap = relayEmailDAO.getConfigForCustomerId(customerId, emailName);
		
		if (CommonUtil.checkConfigIsEmpty(configMap) && !Strings.isNullOrEmpty(setId)) {
			if (isLegacy != null && isLegacy.equalsIgnoreCase(Constants.Y))
				setId = Constants.ANPI;
			
			configMap = generateMultiQueryStringToObtainConfig(true, setId, emailName);
		}
		
		else if (CommonUtil.checkConfigIsEmpty(configMap) && !Strings.isNullOrEmpty(partnerId)) {
			if (isLegacy != null && isLegacy.equalsIgnoreCase(Constants.Y)) {
				setId = Constants.ANPI;
				configMap = generateMultiQueryStringToObtainConfig(true, setId, emailName);
			}
			
			else {
				configMap = generateMultiQueryStringToObtainConfig(false, partnerId, emailName);
			}
		}
		
		logger.info("config-Status --> " + configMap.get("status") + ",\nconfiguration map --> " + configMap);
		
		/* Compose message to be sent with the configMap and relay parameters */
		
		logger.info("Exiting createNoAttachmentMessage");
		
		return configMap;
	}
	
	
	/**
	 * Reads email content from mail server
	 */
	public Map<String, String> identifyElements(String content) {
		logger.info("Entering identifyElements contents");
		
		HashMap<String, String>	elementsForMessage	= new HashMap<String, String>();
		String					usefulContent		= content.split(Constants.SPLITMULTIHASH)[1].trim();
		String[]				lines				= usefulContent.split(Constants.SPLITHASH);
		
		for (String line : lines) {
			
			if (null != line && !("null".equalsIgnoreCase(line))) {
				
							line	= line.replace("\n", "").replace("\r", "");
				String[]	lineArr	= line.split(":", 2);
				String		key		= lineArr[0];
				String		value	= "";
				
				if (lineArr.length == 2) 
					value = lineArr[1];
				
				elementsForMessage.put(key.trim(), value.trim());
			}
			else {
			}
		}
		
		logger.info("Exiting identifyElements contents ");
		return elementsForMessage;
	}
	
	
	/**
	 * Create message part and add attachments if exists
	 */
	public Message createMessageWithAllAttachments(Part part, Message messageWithoutAttachment,Session emailSession,List<String> filesToBeDeleted)
			throws MessagingException, IOException {
		logger.info("Entering createMessageWithAllAttachments");
		
		Message			msgWithAttachments	= null;
		Multipart		multipart					= new MimeMultipart();
		MimeBodyPart	messageBodyPart				= new MimeBodyPart();
		MimeBodyPart	attachPart					= null;
		
		if (part.getContentType().contains("multipart")) {
			
			msgWithAttachments = new MimeMessage(emailSession);
			
			msgWithAttachments.setFrom(messageWithoutAttachment.getFrom()[0]);
			msgWithAttachments.setRecipients(Message.RecipientType.TO,
					messageWithoutAttachment.getRecipients(Message.RecipientType.TO));
			msgWithAttachments.setRecipients(Message.RecipientType.CC,
					messageWithoutAttachment.getRecipients(Message.RecipientType.CC));
			msgWithAttachments.setRecipients(Message.RecipientType.BCC,
					messageWithoutAttachment.getRecipients(Message.RecipientType.BCC));
			msgWithAttachments.setReplyTo(messageWithoutAttachment.getReplyTo());
			msgWithAttachments.setSubject(messageWithoutAttachment.getSubject());
			
			if (filesToBeDeleted == null || filesToBeDeleted.isEmpty()) {
				msgWithAttachments.setContent(messageWithoutAttachment.getContent(),
						"text/html");
			}
			else {
				/* Attaching attachments here */
				messageBodyPart.setContent(messageWithoutAttachment.getContent(), "text/html");
				multipart.addBodyPart(messageBodyPart);
				
				for (String filename : filesToBeDeleted) {
					
					logger.info("Attaching would appear based on the number of attachments");
					
					attachPart = new MimeBodyPart();
					attachPart.attachFile(filename);
					multipart.addBodyPart(attachPart);
				}
				
				msgWithAttachments.setContent(multipart);
			}
		}
		
		else {
			logger.info("No attachments at all");
			
			msgWithAttachments = messageWithoutAttachment;
		}
		
		logger.info("Exiting createMessageWithAllAttachments");
		return msgWithAttachments;
	}
	
	
	/**
	 * Inserts values into email_log table.
	 * Reads the values from relay and config table and insert values into
	 * email_logs table.
	 */
	public Map<String, String> createLoggerMap(Map<String, String> elementsForMessage, Map<String, String> configMap, Message newMessage,String messageContent) throws MessagingException {
		logger.info("Entering createLoggerMap");
		
		Map<String, String>		parameterMap	= new HashMap<String, String>();
		String					signature		= null;
		String					content			= null;
		String					partnerId		= null;
		int						mailSent		= 0;
		String 					status 			= CommonUtil.getValueForMap(configMap,"status");
		String 					useConfig 		= CommonUtil.getValueForMap(elementsForMessage,"UseConfig");
		String 					configuration	= null;
		String 					emailName 		= null;

		logger.info("createLoggerMap configMap -->" + configMap +  "\nelementsForMessage-->" + elementsForMessage);
		logger.info("Status -->" + status + " , UseConfig -->" + useConfig );
		
		/* Read values from the relaymail content (elementsForMessage map) */
		if (configMap.isEmpty() || "inactive".equalsIgnoreCase(status) || "forward".equalsIgnoreCase(status)
				|| ("ask".equalsIgnoreCase(status) && "no".equalsIgnoreCase(useConfig))) {
			
			//TODO If ask status is introduced,configuration value needs to be changed
			
			if (!configMap.isEmpty() && ("ask".equalsIgnoreCase(status) && "no".equalsIgnoreCase(useConfig) || ("forward"
							.equalsIgnoreCase(status)))) {
				mailSent 		= 1;
				configuration 	= "FORWARD";
			}
			else {
				mailSent 		= 0;
				configuration 	= "DISABLED";
			}

			signature 	= CommonUtil.getValueForMap(elementsForMessage,"Signature");
			content 	= CommonUtil.getValueForMap(elementsForMessage,"Content");
			content	    = Jsoup.parse(content).toString();
			content 	= content + "<br/><br/>" + signature;
			emailName 	= CommonUtil.getValueForMap(elementsForMessage,"EmailName");

		} 
		/* Read from the values fetched from the DB and putting values into
		 table with actual configs. */
		else {
			
			mailSent		= 1;
			configuration 	= "ENABLED";
			emailName 		= CommonUtil.getValueForMap(configMap,"email_name_display");
			content 		= CommonUtil.getValueForMap(configMap,"content_template");
			
			if (messageContent != null && StringUtils.isNotBlank(messageContent)) {
				content = messageContent;
			}

			/* In case of Channel partner,insert parent partner id */
			if (!Strings.isNullOrEmpty(CommonUtil.getValueForMap(configMap,"actual_partner_id"))
					&& (!Strings.isNullOrEmpty(CommonUtil.getValueForMap(configMap,"partner_type"))) 
					&& CommonUtil.getValueForMap(configMap,"partner_type").equals("2")) {
				parameterMap.put("parent_partner_id", CommonUtil.getValueForMap(configMap,"actual_partner_id"));
			}
		}

		if (!Strings.isNullOrEmpty(CommonUtil.getValueForMap(configMap,"source"))) {
			parameterMap.put("classification", CommonUtil.getValueForMap(configMap,"source"));
		}

		/* Inserting partner_id value in db*/
		if (!Strings.isNullOrEmpty(CommonUtil.getValueForMap(configMap,"partner_id"))) {
			partnerId = CommonUtil.getValueForMap(configMap,"partner_id");
		}
		else if (!Strings.isNullOrEmpty(CommonUtil.getValueForMap(elementsForMessage,"PartnerID"))) {
			partnerId = CommonUtil.getValueForMap(elementsForMessage,"PartnerID");
		}
		else {
		}
		
		String toAddress = CommonUtil.getAddressFromMessage(newMessage.getRecipients(Message.RecipientType.TO));
		String ccAddress = CommonUtil.getAddressFromMessage(newMessage.getRecipients(Message.RecipientType.CC));
		String subject 	 = StringEscapeUtils.escapeSql(newMessage.getSubject());
		
		parameterMap.put("from", CommonUtil.extractAddr(newMessage.getFrom()[0].toString()));
		parameterMap.put("reply_to", CommonUtil.extractAddr(newMessage.getReplyTo()[0].toString()));
		parameterMap.put("to", toAddress);
		parameterMap.put("cc", ccAddress);
		parameterMap.put("mail_sent", String.valueOf(mailSent));
		parameterMap.put("sent_date", CommonUtil.convertToUTCString(new Date()));
		parameterMap.put("setId", elementsForMessage.get("SetID"));
		parameterMap.put("partner_id", partnerId);
		parameterMap.put("subject", StringEscapeUtils.unescapeHtml(subject));
		parameterMap.put("content", StringEscapeUtils.escapeSql(content));
		parameterMap.put("customer_id", CommonUtil.getValueForMap(elementsForMessage,"CustomerID"));
		parameterMap.put("order_id", CommonUtil.getValueForMap(elementsForMessage,"OrderID"));
		parameterMap.put("sending_status", "Created");
		parameterMap.put("resend_count", "0");
		parameterMap.put("configuration", configuration);
		parameterMap.put("email_name", emailName);
		
		logger.info("Exiting createLoggerMap");
		return parameterMap;
	}
	
	
	/**
	 * Get addressArr for composing message(Status - FORWARD/INACTIVE)
	 */
	public Address[] getAddressArrFromElementsMap(String key,Map<String,String> elementsMap,Map<String,String> configMap) throws Exception {
		
		String 		value 			= CommonUtil.getValueForMap(elementsMap,key);
		Address[] 	addressesArr	= null;
		
		if (!Strings.isNullOrEmpty(value)) {
			String[] addressArr = CommonUtil.extractAddr(value).split(";");
			
			if ("To".equals(key) && !configMap.isEmpty()
					&& configMap.get("status").equalsIgnoreCase("forward")
					&& addressArr.length == 0) {
				addressArr = Constants.NOTIFICATION_EMAIL_ADDRESS.split(";");
			}
			
			if (addressArr != null) {
				addressesArr = new Address[addressArr.length];
				for (int i = 0; i < addressArr.length; i++) {
					if ("From".equals(key) || "To".equals(key)) {
						String address = addressArr[i].split("@")[0].toString().toUpperCase();
						addressesArr[i] = new InternetAddress(addressArr[i], address);
					}
					else {
						addressesArr[i] = new InternetAddress(addressArr[i]);
					}
				}
			}
		}
		
		return addressesArr;
	}
	
	
	/**
	 * Get addressArr for composing message(Status - ACTIVE)
	 */
	public Address[] getAddressForActiveStatus(String configKey, String elementKey,Map<String,String> elementsMap,Map<String,String> configMap) throws AddressException {
		
		String[]	addressArr	= null; 
		String[]	emailArr	= null;
		String		value		= CommonUtil.getValueForMap(configMap,configKey);
		
		if (!Strings.isNullOrEmpty(value) && (!value.contains("generated"))) {
			addressArr = value.split(",");
		}
		else if (!Strings.isNullOrEmpty(value) && (value.contains("generated"))) {
			
			String[]	confArr			= value.split(",");
			String		emailAddress	= CommonUtil.getValueForMap(elementsMap,elementKey);
			
			if (!Strings.isNullOrEmpty(emailAddress)) {
				emailArr = CommonUtil.extractAddr(emailAddress).split(";");
			}
			else if("To".equals(elementKey)){
					emailArr = Constants.NOTIFICATION_EMAIL_ADDRESS.split(";");
			}
			
			List<String> list = new ArrayList<String>(Arrays.asList(emailArr));
			list.addAll(Arrays.asList(confArr));
			
			while(list.remove("generated")){
				//do nothing
			}
			
			Object[] obj = list.toArray();
			addressArr = Arrays.copyOf(obj, obj.length, String[].class);
		}
		
		if (addressArr != null) {
			Address[] addressesArr = new Address[addressArr.length];
			for (int i = 0; i < addressArr.length; i++) {
				addressesArr[i] = new InternetAddress(addressArr[i]);
			}
			return addressesArr;
		}
		
		return null;
	}
	
	
	/**
	 * Send mail based on SMTP server configuration of partner
	 */
	public void sendMail(Message message,final Map<String,String> configMap,Session emailSession) throws Exception {
		
		Transport	transport	= null;
		String		partnerId	= CommonUtil.getValueForMap(configMap,"partner_id");
		String		smtpServer	= CommonUtil.getValueForMap(configMap,"smtp_server");
		String		userName	= CommonUtil.getValueForMap(configMap,"user_name");
		String		password	= CommonUtil.getValueForMap(configMap,"password");
		String		port		= CommonUtil.getValueForMap(configMap,"port");
		
		logger.info("Send mail Partner Id:"  + partnerId);
		
		/* Use partner SMTP server setting for sending mail,if configured. */
		if ("1".equals(CommonUtil.getValueForMap(configMap,"smtp_applicable")) && !Strings.isNullOrEmpty(smtpServer)
				&& !Strings.isNullOrEmpty(port) && !Strings.isNullOrEmpty(userName)
				&& !Strings.isNullOrEmpty(password)) {
			
			Properties props = new Properties();
			props.put("mail.smtp.auth", "true");
			props.put("mail.smtp.host", smtpServer.trim());
			props.put("mail.smtp.port", port);
			
			try {
				Session session = Session.getInstance(props, new javax.mail.Authenticator() {
					protected PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(CommonUtil.getValueForMap(configMap,"user_name"),
								CommonUtil.getValueForMap(configMap,"password").trim());
					}
				});
				
				session.setDebug(true);
				transport = session.getTransport("smtp");
				transport.connect();
			}
			catch (Exception e) {
				e.printStackTrace();
				System.out.println("Exception -->" + e.getMessage());
			}
			
			/* If SMTP authentication fails, send mail through default
			 * SMTP configuration and trigger a mail to account manager */
			if (!transport.isConnected()) {
				transport = emailSession.getTransport("smtp");
				transport.connect();
				Message failureMessage = mailFailure(message,emailSession,configMap.get("account_manager"));
				transport.sendMessage(failureMessage, failureMessage.getAllRecipients());
			}
		}
		
		else {
			transport = emailSession.getTransport("smtp");
			transport.connect();
		}
		
		transport.sendMessage(message, message.getAllRecipients());
		transport.close();
	}
	
	
	/**
	 *  If SMTP authentication fails, send mail to account manager
	 */
	public Message mailFailure(Message messageToBeSent,Session emailSession,String accountManager) throws Exception
	{
		Map<String, String> userMap = new HashMap<String, String>();
							userMap = URLReaderUtil.getInputFromUrl(Constants.PARTNER_SMTP_DETAILS + "users/" + accountManager);
							
		Message statusMessage = new MimeMessage(emailSession);
		statusMessage.setFrom(new InternetAddress("donotreply@anpi.com"));
		statusMessage.setRecipient(Message.RecipientType.TO,new InternetAddress(userMap.get("email_id")));
		
		if (messageToBeSent.getContent() instanceof Multipart) {
			statusMessage.setContent((Multipart) messageToBeSent.getContent());
		}
		
		else {
			statusMessage.setContent(messageToBeSent.getContent(), "text/html");
		}
		
		statusMessage.setSubject("Mail delivery failure - SMTP server auth error");
		return statusMessage;
	}
	
	
	/**
	 * Query email_configs_vw based on email_name and setId or partnerId.
	 */
	public Map<String, String> generateMultiQueryStringToObtainConfig(boolean isSetID,
			String setId, String emailName) throws MalformedURLException, IOException,
			ParseException, SQLException {
		logger.info("Entering generateMultiQueryStringToObtainConfig --> SetId,emailName " + setId + " , " + emailName);
		
		Map<String, String>	configMap	= new HashMap<String, String>();
		Map<String, String>	partnerMap	= new HashMap<String, String>();

		
		/* Retrieve partner information from partner API (published status,
		 * parent_partner_id,etc..)*/
		if (isSetID) {
			partnerMap = URLReaderUtil.getJsonFromUrl(Constants.PARTNER_SMTP_DETAILS+ "partners?partner_id=" + setId);
		}
		else {
			partnerMap = URLReaderUtil.getJsonFromUrl(Constants.PARTNER_SMTP_DETAILS+ "partners?id=" + setId);
		}
		
		if (partnerMap != null && !partnerMap.isEmpty()) {
			
			configMap = getConfig(partnerMap, emailName, setId);
			
			if (configMap != null && !configMap.isEmpty()) {
				/* Setting partner smtp information */
				configMap.put("smtp_applicable", CommonUtil.getValueForMap(partnerMap, "smtp_applicable"));
				configMap.put("smtp_server", CommonUtil.getValueForMap(partnerMap, "smtp_server"));
				configMap.put("user_name", CommonUtil.getValueForMap(partnerMap, "user_name"));
				configMap.put("password", CommonUtil.getValueForMap(partnerMap, "password"));
				configMap.put("port", CommonUtil.getValueForMap(partnerMap, "port"));
				configMap.put("account_manager", CommonUtil.getValueForMap(partnerMap, "account_manager"));
				configMap.put("is_published", CommonUtil.getValueForMap(partnerMap, "is_published"));
			}
		}
		
		logger.info("Exiting generateMultiQueryStringToObtainConfig" + configMap);
		return configMap;
	}
	
	/**
	 * Get configuration for given emailname and setId
	 */
	public Map<String, String> getConfig(Map<String, String> partnerMap,String emailName,String setId) throws MalformedURLException, IOException, ParseException, SQLException  {
		
		Map<String, String>		configMap	= new HashMap<String, String>();
		String 					partnerId  	= null;
		String 					setID 		= null;			
		String					partnerType = CommonUtil.getValueForMap(partnerMap, "partner_type");
		String					parentId 	= CommonUtil.getValueForMap(partnerMap, "parent_partner_id");
		String 					isPublished = CommonUtil.getValueForMap(partnerMap, "is_published");
		
		/* If channel partner, apply configuration of parent partner */
		if (!Strings.isNullOrEmpty(partnerType) && "2".equals(partnerType)	&& !Strings.isNullOrEmpty(parentId)) {
			logger.info("Inside channel Partner");
			
			partnerId	 	= parentId;
			partnerMap 		= URLReaderUtil.getJsonFromUrl(Constants.PARTNER_SMTP_DETAILS+ "partners?id=" + partnerId);
			setID 			= CommonUtil.getValueForMap(partnerMap, "partner_id");
			
			// If partner is non-published, generic config SetID = "*****"
			if ((Strings.isNullOrEmpty(isPublished) || "0".equals(isPublished))) {
				setID = Constants.DUMMY_PARTNER_ID;
			}
		}
		
		/* If WS partner is non-published, generic config for setId as "*****" */
		else if ((Strings.isNullOrEmpty(isPublished) || "0".equals(isPublished))) {
			setID 		= Constants.DUMMY_PARTNER_ID;
			partnerId 	= partnerMap.get("id").toString();
		}
		
		else {
			setID 		= partnerMap.get("partner_id");
			partnerId 	= CommonUtil.getValueForMap(partnerMap, "id");
		}
		
		logger.info("SetID:" + setID + "partnerId:" + partnerId);
		
				configMap	= readEmailDAO.getConfigForSetId(emailName,setId);
		
		if(configMap!=null && !configMap.isEmpty()){
			configMap.put("actual_partner_id",partnerId);
			configMap.put("partner_type", partnerType);
		}
		
		return configMap;
	}
	
	
	/**
	 * Tag value in content and subject are replaced with appropriate value from
	 * API call, Tag map or relay email.
	 */
	public String[] generateActualContent(Map<String, String> elementMap, Map<String, String> configMap) throws SQLException, JSONException, IOException {
		logger.info("Entering generateActualContent");
		
		String						content			= null;
		String						subject			= null;
		String						query			= null;
		List<String>				tagsUsedList	= null;
		Map<String, String>			apiMap			= new HashMap<String, String>();
		String 						isPublished 	= CommonUtil.getValueForMap(configMap, "is_published");
		Map<String, String> 		map				= new HashMap<String, String>();
		List<String>				dbTagList		= new ArrayList<String>();
		
		if (!configMap.isEmpty()) {
			
			subject = StringEscapeUtils.unescapeHtml(configMap.get("subject"));
			content = StringEscapeUtils.unescapeHtml(configMap.get("content_template"));
		}
		
		
		Map<String, TagMapDTO> tagMap = readEmailDAO.getTagsList(configMap.get("id"));
				
		logger.info("tagMap -> " + tagMap);

		/* If partner is published, get branding parameters  */
		if(!Strings.isNullOrEmpty(isPublished) && "1".equals(isPublished)){
			String 	partnerId	= CommonUtil.getValueForMap(configMap, "partner_id");
					apiMap 		= URLReaderUtil.getInputFromUrl(Constants.API_CALL_URL+"partner_id="+partnerId);
		}
		
		String tagsUsed = CommonUtil.getValueForMap(elementMap,"TAGS USED");
		if (!Strings.isNullOrEmpty(tagsUsed)) {
			tagsUsedList = new ArrayList<String>(Arrays.asList(tagsUsed.split(",")));
		}
		
		/* Replace signature */
		String signature = CommonUtil.getValueForMap(configMap, "signature");
		if (!Strings.isNullOrEmpty(signature) && (!signature.contains("generated"))) {
			signature	= StringEscapeUtils.unescapeHtml(signature);
			signature 	= CommonUtil.replaceDollarSign(signature);
			content 	= content.replaceAll("<<.SIGNATURE CONTENT>>", signature);
		} else {
			content 	= content.replaceAll("<<.SIGNATURE CONTENT>>", "");
		}
		
		for (String tagName : tagMap.keySet()) {
			TagMapDTO tagMapDTO = tagMap.get(tagName);
			if(tagMapDTO.getSource().equals("2")){
				dbTagList.add(tagMapDTO.getTagValue());
			}
		}
		
		if(dbTagList!=null && !dbTagList.isEmpty()){
			String dbTagQuery = "select netx_id,product_type from products where netx_id in ('" + dbTagList + "' ) and   partner_id ='" + configMap.get("actual_partner_id") + "'";
			System.out.println("dbTagQuery :" + dbTagQuery);
		}
		
		for (String tag : tagMap.keySet()) {
			do {
				for (String tagName : tagMap.keySet()) {
					
					if (content.contains("<<." + tagName + ">>") || subject.contains("<<." + tagName + ">>")) {
						String 		replacedValue 	= "";
						TagMapDTO 	tagMapDTO 		= tagMap.get(tagName);
						
						logger.info("tagName:" + tagName);
						
						/* Tags used condition */
						if (tagsUsedList != null && !tagsUsedList.isEmpty()) {
							if (!tagsUsedList.contains(tagName)) {
								content = content.replaceAll("<<." + tagName + ">>", "");
								subject = subject.replaceAll("<<." + tagName + ">>", "");
								logger.info(tagName + "tagName not found");
							}
						}
						
						/* Replace tags with partner branding configuration */
						if (!Strings.isNullOrEmpty(CommonUtil.getValueForMap(apiMap, tagName))) {
							
							if (tagName.contains("PHONE")) {
								String 	phoneNumber 	= CommonUtil.formatNumber(apiMap.get(tagName),Constants.COMMA);
										replacedValue	= phoneNumber;
							} else {
										replacedValue 	= apiMap.get(tagName);
							}
						}
						
						/* CONDITION TO HANDLE DB TAGS */
						else if (tagMap.containsKey(tagName) && tagMapDTO.getSource()==2) {
							/* Retrieve partner product information for the actual partner Id */
							String dbTagQuery = "select product_type from products where netx_id='" + tagName + "' and   partner_id ='" + configMap.get("actual_partner_id") + "'";
							
							logger.info("dbTagQuery:" + dbTagQuery);
							
							map = new DbConnect().getConfigsFromSingleQuery(dbTagQuery);
							if(map!=null && !map.isEmpty()){
								replacedValue = map.get("product_type");
							}
						} 
						
						/* replace tags with tag value  */
						else if (tagMap.containsKey(tagName) && !Strings.isNullOrEmpty(tagMapDTO.getTagValue())) {
							String 	temp 			=  StringEscapeUtils.unescapeHtml(tagMapDTO.getTagValue());
									temp			= CommonUtil.replaceDollarSign(temp);
									replacedValue 	= temp;
						} 
						
						/* replace tags with elements map (relay email) */
						else if (!Strings.isNullOrEmpty(CommonUtil.getValueForMap(elementMap, tagName))) {
							
							String 	temp 			= StringEscapeUtils.unescapeHtml(elementMap.get(tagName));
									temp 			= CommonUtil.replaceDollarSign(temp);
									replacedValue	= temp;
						}	
						
						/* replace value with VFTTND */
						 else {
							logger.info("value for " + tagName + " is not defined");
							replacedValue = Constants.VFTTND;
						}
						
						logger.info(tagName +" replaced by " + replacedValue);
						
						subject = subject.replaceAll("<<." + tagName + ">>", replacedValue);
						content = content.replaceAll("<<." + tagName + ">>", replacedValue);
					}
				}
			} while (content.contains("<<." + tag + ">>") || subject.contains("<<." + tag + ">>"));
		}
		
		logger.info("Exiting generateActualContent -->" + content);
		String[] arr = {subject,content};
		return arr;
	}
	
	
	/**
	 * Insert into email_logs.
	 * @param parameterMap the hashmap of key,values to be inserted in email_logs
	 * @return the id 
	 */
	public int insert(Map<String, String> parameterMap) {
		logger.info("Entering insert");
		
		String	columns	= "";
		String	val		= "";
		
		for (String key : parameterMap.keySet()) {
			columns 	+= "`" + key + "`" + ",";
			val 		+= "'" + parameterMap.get(key) + "'" + ",";
		}
		
		columns 		= columns.substring(0, columns.lastIndexOf(','));
		val 			= val.substring(0, val.lastIndexOf(','));
		
		return readEmailDAO.insetIntoDb(columns,val);
		
	
	}
	
	/**
	 * Delete the documents uploaded in temp directory.
	 */
	public void deleteFiles(List<String> paths) {
		logger.info("Entering deleteFiles");
		
		File 		file = null;
		
		for (String path : paths) {
			
			file = new File(path);
			
			boolean isdeleted = file.delete();
			if (isdeleted) {
				logger.info("The generated attachment is deleted.");
			}
			else {
				logger.info("The generated attachment failed to get deleted.");
			}
		}
		
		logger.info("Exiting deleteFiles " );
	}



	public void updateLogger(int insertId) {
		 readEmailDAO.updateLogger(insertId);
	}
	

}
