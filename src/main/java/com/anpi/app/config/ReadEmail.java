package com.anpi.app.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities.EscapeMode;
import org.springframework.stereotype.Component;

import com.anpi.app.constants.Constants;
import com.anpi.app.domain.EmailCredits;
import com.anpi.app.service.RelayEmail;
import com.anpi.app.service.RelayEmailDAO;
import com.anpi.app.util.CommonUtil;
import com.anpi.app.util.URLReaderUtil;
import com.anpi.app.util.UploadToDocRepo;
import com.google.common.base.Strings;

@Component
public class ReadEmail {
	
	private static final Logger		logger						= Logger.getLogger(RelayEmail.class);
	
	RelayEmailDAO					relayEmailDAO				= new RelayEmailDAO();
	public Session					emailSession				= null;
	public String					messageContentFinal			= null;
	public Message					newMessage					= null;
	public Map<String, String>		configMap					= null;
	public ArrayList<String>		filesToBeDeleted			= null;
	public LinkedList<String>		fileNames					= null;
	public Message					messageToBeSent				= null;
	public Message					messageWithoutAttachment	= null;
	public Map<String, String>		elementsForMessage			= null;
	public ArrayList<String>		uploadedUuids				= new ArrayList<String>();

	
	public static void main(String[] args) {
		
		EmailCredits emailCredits = new EmailCredits();
		
		emailCredits.setUsername("relay");
		emailCredits.setPassword(Constants.RELAY_PASSWORD);
		emailCredits.setPopHost(Constants.RELAY_HOST);
		
		new ReadEmail().fetch(emailCredits);
	}

	
	/** 
	 * This method fetches the email based on content_type from mail server,
	 * then process it,inserts the value into db and deletes the email from the
	 * mail server.
	 */
	public void fetch(EmailCredits emailCredits) {
		
		Store store;
		Folder emailFolder;
		String line = null;
		Properties properties;

		logger.info("Entering Fetch method");
		
		try {

			properties = getProperties(emailCredits);
			emailSession = Session.getDefaultInstance(properties);
			
			/* create the POP3 store object and connect with the pop server */
			store = emailSession.getStore("pop3");
			store.connect(emailCredits.getPopHost(), emailCredits.getUsername(),
					emailCredits.getPassword());
			
			/* create the folder object and open it */
			emailFolder = store.getFolder("INBOX");
			emailFolder.open(Folder.READ_WRITE);
			
			/* retrieve the messages from the folder */
			Message[] messages = emailFolder.getMessages();
			
			logger.info("Number of messages to be popped--->" + messages.length);
			
			for (Message message : messages) {
				
				filesToBeDeleted = new ArrayList<String>();
				fileNames = new LinkedList<String>();
				
				logger.info("Message Number that is being read ---> " + message.getMessageNumber());
				
				if (!message.isSet(Flags.Flag.DELETED)) {
					
					/* Process the message to be sent. Comment below to mark the
					 * message read - in case of error in message */
					line = processMessage(message);
					
					if ("YES".equalsIgnoreCase(line)) {
						
						logger.info("\nThis is the original Email \n----------------------------");
						
//						message.writeTo(System.out);
						
						logger.info("---------------------------- \nOriginal Email is read");
						
						message.setFlag(Flags.Flag.DELETED, true);
					}
					
				}
				else {
					logger.info("This message is marked not deleted ---> "
							+ message.getMessageNumber());
				}
			}
			/* close the store and folder object. Expunges the folder to remove
			 * messages which are marked deleted */
			emailFolder.close(false);
			store.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	
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
	
	
	public String processMessage(Message message)
	{
		String partnerId = "1";
		try
		{
			/* Process the message based on the content-type */
			writePart(message);
			
			/* Fetch configuration based on the email name */
			getConfiguration(elementsForMessage);
			
			/* Compose message with all attachments */
			messageToBeSent = createMessage(message,elementsForMessage, configMap);
			
			/* Upload files to doc Repository */
			if (!Strings.isNullOrEmpty(getValueForElementsMap("partner_id")))
				partnerId = getValueForElementsMap("partner_id");
			
			if (null != filesToBeDeleted && filesToBeDeleted.size() > 0) 
				uploadFiles(filesToBeDeleted, partnerId);

			int insertId = addLogger();
			
			/* If status = Active/forward, then send mail to the user and update
			 * the email log table */
			if (!configMap.isEmpty() && !configMap.get("status").equalsIgnoreCase("inactive")
					&& null != messageToBeSent) 
			{
				sendMail(messageToBeSent);
				relayEmailDAO.updateLogger(insertId);
			}
			
			/* Deletes files from temp directory */
			if (null != filesToBeDeleted && filesToBeDeleted.size() > 0) {
				deleteFiles(filesToBeDeleted);
			}
			
			return "YES";
		}
		catch (Exception e)
		{
			logger.info("Exception" + e.getMessage() + "\nFull exception:" + e);
			
			e.printStackTrace();
			return "no";
		}
		finally
		{
			elementsForMessage 		 = null;
			messageWithoutAttachment = null;
		}
	}

	
	/**
	 * This method checks for content-type based on which, it processes and
	 * fetches the content of the message.
	 */
	public void writePart(Part p) throws Exception {
		if (p instanceof Message) { 
			/* Prints FROM, TO, SUBJECT of the message  */
			CommonUtil.writeEnvelope((Message) p); 
		}
		
		logger.info("CONTENT-TYPE: " + p.getContentType());
		
		/* check if the content is plain text */
		if (p.isMimeType("text/plain")) {
			logger.info("Plain text Email");
			
			String content = (String) p.getContent();
			if (null == p.getDisposition()
					|| ((null != p.getDisposition()) && (!p.getDisposition().equalsIgnoreCase(
							Part.ATTACHMENT)))) {
				elementsForMessage = identifyElements(content);
			}
			
			downloadAttachments(p, messageWithoutAttachment);
		}
		
		/* check if the content has attachment */
		else if (p.isMimeType("multipart/*")) {
			
			logger.info("This is a Multipart");
			
			Multipart mp = (Multipart) p.getContent();
			int count = mp.getCount();
			
			logger.info("multipart count ==>" + count);
			
			for (int i = 0; i < count; i++) {
				writePart(mp.getBodyPart(i));
			}
		}
		
		/* check if the content is a nested message */
		else if (p.isMimeType("message/rfc822")) {
			
			logger.info("This is a Nested Message");
			
			writePart((Part) p.getContent());
		}
		
		/* check if the content is an inline image */
		else if (p.getContentType().contains("image/")) {
			
			if (Part.ATTACHMENT.equalsIgnoreCase(p.getDisposition())) {
				String fileName = p.getFileName();
				MimeBodyPart mimeBodyPart = (MimeBodyPart) p;
				mimeBodyPart.saveFile(fileName);
				filesToBeDeleted.add(fileName);
			}
			
		}
		else {
			Object o = p.getContent();
			if (o instanceof String) {
				
				logger.info("This is a string");
				
				if (null == p.getDisposition()
						|| ((null != p.getDisposition()) && (!p.getDisposition().equalsIgnoreCase(
								Part.ATTACHMENT)))) {
					elementsForMessage = identifyElements(o.toString());
				}
				
				downloadAttachments(p, messageWithoutAttachment);
			}
			
			else if (o instanceof InputStream) {
				
				logger.info("This is just an input stream");
				
				InputStream is = (InputStream) o;
				is = (InputStream) o;
				int c;
				
				while ((c = is.read()) != -1) {
					// System.out.write(c);
					// logger.info("Reads input Stream");
				}
				
				if (null == p.getDisposition()
						|| ((null != p.getDisposition()) && (!p.getDisposition().equalsIgnoreCase(
								Part.ATTACHMENT)))) {
					elementsForMessage = identifyElements(CommonUtil.getStringFromInputStream(is));
				}
				
				downloadAttachments(p, messageWithoutAttachment);
			}
			
			else {
				logger.info("This is an unknown type : " + o.toString());
			}
		}
	}

	
	/**
	 *  Look for configuration based on email name and then compose message
	 */
	public void getConfiguration(Map<String, String> elementsForMessage)
			throws Exception {
		
		logger.info("Entering createNoAttachmentMessage");
		
				configMap	= new HashMap<String, String>();
		String	setId		= getValueForElementsMap("SetID");												;
		String	partnerId	= getValueForElementsMap("PartnerID");
		String	isLegacy	= getValueForElementsMap("isLegacy");
		String	customerId	= getValueForElementsMap("CustomerID");
		String	orderId		= getValueForElementsMap("OrderID");
		String	emailName	= StringUtils.deleteWhitespace(elementsForMessage.get("EmailName"));
		
		/* Look for configurations based on the orderID, followed by CustomerID,
		 * followed by the SetID and then PartnerID. If configuration is empty,
		 * look configuration for other parameter */
		
		if (!Strings.isNullOrEmpty(orderId))
			configMap = relayEmailDAO.getConfigForOrderId(orderId, emailName);
		
		if (checkConfigIsEmpty() && !Strings.isNullOrEmpty(customerId))
			configMap = relayEmailDAO.getConfigForCustomerId(customerId, emailName);
		
		if (checkConfigIsEmpty() && !Strings.isNullOrEmpty(setId)) {
			if (isLegacy != null && isLegacy.equalsIgnoreCase(Constants.Y))
				setId = Constants.ANPI;
			
			configMap = relayEmailDAO.generateMultiQueryStringToObtainConfig(true, setId, emailName);
		}
		
		else if (checkConfigIsEmpty() && !Strings.isNullOrEmpty(partnerId)) {
			if (isLegacy != null && isLegacy.equalsIgnoreCase(Constants.Y)) {
				setId = Constants.ANPI;
				configMap = relayEmailDAO.generateMultiQueryStringToObtainConfig(true, setId, emailName);
			}
			
			else {
				configMap = relayEmailDAO.generateMultiQueryStringToObtainConfig(false, partnerId, emailName);
			}
		}
		
		logger.info("config-Status --> " + configMap.get("status") + ",\nconfiguration map --> " + configMap);
		
		/* Compose message to be sent with the configMap and relay parameters */
		
		logger.info("Exiting createNoAttachmentMessage");
	}
	
	
	/**
	 * Inserts values into email_log table.
	 */
	public int addLogger() throws MessagingException {
		logger.info("Entering addLogger");
		
		/* Generate the map for the logs to be added.*/
		Map<String, String> 	parameterMap = createLoggerMap(elementsForMessage, configMap,newMessage);
		int 					insertId 	 = relayEmailDAO.insert(parameterMap);
		
		logger.info("Exiting addLogger --> " + insertId);
		return insertId;
	}
	
	
	/**
	 * Inserts values into email_log table.
	 * Reads the values from relay and config table and insert values into
	 * email_logs table.
	 */
	public Map<String, String> createLoggerMap(Map<String, String> elementsForMessage, Map<String, String> configMap, Message newMessage) throws MessagingException {
		logger.info("Entering createLoggerMap");
		
		Map<String, String>		parameterMap	= new HashMap<String, String>();
		String					signature		= null;
		String					content			= null;
		String					partnerId		= null;
		int						mailSent		= 0;
		String 					status 			= getValueForConfig("status");
		String 					useConfig 		= getValueForElementsMap("UseConfig");
		String 					configuration	= null;
		String 					emailName 		= null;

		logger.info("createLoggerMap configMap -->" + configMap +  "\nelementsForMessage-->" + elementsForMessage);
		logger.info("Status -->" + status + " , UseConfig -->" + useConfig );
		
		/* Read values from the relaymail content (elementsForMessage map) */
		if (configMap.isEmpty() || "inactive".equalsIgnoreCase(status) || "forward".equalsIgnoreCase(status)
				|| ("ask".equalsIgnoreCase(status) && "no".equalsIgnoreCase(useConfig))) {
			
			//TODO If ask status is introduced,configuration value needs to be changed
			
			if (!configMap.isEmpty() && (status.equalsIgnoreCase("ask") && "no".equalsIgnoreCase(useConfig) || (status
							.equalsIgnoreCase("forward")))) {
				mailSent 		= 1;
				configuration 	= "FORWARD";
			}
			else {
				mailSent 		= 0;
				configuration 	= "DISABLED";
			}

			signature 	= getValueForElementsMap("Signature");
			content 	= getValueForElementsMap("Content");
			content	    = Jsoup.parse(content).toString();
			content 	= content + "<br/><br/>" + signature;
			emailName 	= getValueForElementsMap("EmailName");

		} 
		/* Read from the values fetched from the DB and putting values into
		 table with actual configs. */
		else {
			
			mailSent		= 1;
			configuration 	= "ENABLED";
			emailName 		= getValueForConfig("email_name_display");
			content 		= getValueForConfig("content_template");
			
			if (messageContentFinal != null && StringUtils.isNotBlank(messageContentFinal)) {
				content = messageContentFinal;
			}

			/* In case of Channel partner,insert parent partner id */
			if (!Strings.isNullOrEmpty(getValueForConfig("actual_partner_id"))
					&& (!Strings.isNullOrEmpty(getValueForConfig("partner_type"))) 
					&& getValueForConfig("partner_type").equals("2")) {
				parameterMap.put("parent_partner_id", getValueForConfig("actual_partner_id"));
			}
		}

		if (!Strings.isNullOrEmpty(getValueForConfig("source"))) {
			parameterMap.put("classification", getValueForConfig("source"));
		}

		/* Inserting partner_id value in db*/
		if (!Strings.isNullOrEmpty(getValueForConfig("partner_id"))) {
			partnerId = getValueForConfig("partner_id");
		}
		else if (!Strings.isNullOrEmpty(getValueForElementsMap("PartnerID"))) {
			partnerId = getValueForElementsMap("PartnerID");
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
		parameterMap.put("customer_id", getValueForElementsMap("CustomerID"));
		parameterMap.put("order_id", getValueForElementsMap("OrderID"));
		parameterMap.put("sending_status", "Created");
		parameterMap.put("resend_count", "0");
		parameterMap.put("configuration", configuration);
		parameterMap.put("email_name", emailName);
		
		if (uploadedUuids != null && !uploadedUuids.isEmpty()) {
			parameterMap.put("attachments", CommonUtil.convertToCsv(uploadedUuids));
			parameterMap.put("file_names", CommonUtil.convertToCsv(fileNames));
		}
		
		logger.info("Exiting createLoggerMap");
		return parameterMap;
	}
	
	
	/**
	 * Compose email message based on the relay message and database.
	 */
	public Message createMessage(Message message,Map<String, String> elementsForMessage, Map<String, String> configMap) throws Exception {
		logger.info("Entering createMessageOnly");
		
		String	content		= null;
				newMessage	= new MimeMessage(emailSession);
		String	status		= getValueForConfig("status");
		String	useConfig	= getValueForElementsMap("UseConfig");
		
		logger.info("Status -->" + status + " , UseConfig -->" + useConfig );
		
		/* Reads value from elementsMap(relay email content) and create new message */
		if (configMap.isEmpty() || "inactive".equalsIgnoreCase(status)
				|| "forward".equalsIgnoreCase(status) || ("ask".equalsIgnoreCase(status)
				&& "no".equalsIgnoreCase(useConfig))) {
			
			Address[] fromAddressArr = getAddressArrFromElementsMap("From");
			newMessage.setFrom(fromAddressArr[0]);
			
			Address[] toAddressesArr = getAddressArrFromElementsMap("To");
			newMessage.setRecipients(Message.RecipientType.TO, toAddressesArr);

			Address[] ccAddressesArr = getAddressArrFromElementsMap("CC");
			newMessage.setRecipients(Message.RecipientType.CC, ccAddressesArr);
			
			InternetAddress bccList[] = InternetAddress.parse(Constants.BCC_EMAIL_ADDRESS);
			newMessage.setRecipients(Message.RecipientType.BCC, bccList);
			
			Address[] replyToAddressesArr = getAddressArrFromElementsMap("ReplyTo");
			newMessage.setReplyTo(replyToAddressesArr);
				
			newMessage.setSubject(StringEscapeUtils.unescapeHtml(getValueForElementsMap("Subject")));
			
			String signature = getValueForElementsMap("Signature");
			if(!Strings.isNullOrEmpty(signature)){
				content = getValueForElementsMap("Content") + "<br/><br/>" + signature;
			}else{
				content = getValueForElementsMap("Content") + "<br/><br/>";
			}
			
			content = StringEscapeUtils.unescapeHtml(content);
			
		}
		/* Read values from the DB and create the new message */
		else {
			
			String fromEmailValue = getValueForConfig("from_email");
			String fromAddress ;
			
			if (!Strings.isNullOrEmpty(fromEmailValue) && !fromEmailValue.equals("generated")) 
				fromAddress = configMap.get("from_email");
			 else
				fromAddress = getValueForElementsMap("From");
			
			newMessage.setFrom(new InternetAddress(fromAddress.toString(), fromAddress.split("@")[0].toString().toUpperCase()));
			newMessage.setRecipients(Message.RecipientType.TO,
					getAddressForActiveStatus("to_email", "To"));
			newMessage.setRecipients(Message.RecipientType.CC,
					getAddressForActiveStatus("cc_email", "CC"));
			newMessage.setReplyTo(getAddressForActiveStatus("reply_to_email", "ReplyTo"));
			newMessage.setRecipients(Message.RecipientType.BCC,
					InternetAddress.parse(Constants.BCC_EMAIL_ADDRESS));
			
			String generatedString = relayEmailDAO.generateActualContent(elementsForMessage, configMap);
			
			if (!Strings.isNullOrEmpty(getValueForConfig("subject"))) {
				String subject = generatedString.split("##")[0].toString();
				newMessage.setSubject(StringEscapeUtils.unescapeHtml(subject));
			}
			else {
				newMessage.setSubject(StringEscapeUtils.unescapeHtml(getValueForElementsMap("Subject")));
			}
			
			content = generatedString.split("##")[1].toString();
		}
		
		Document doc = Jsoup.parse(content);
	    Document.OutputSettings settings = doc.outputSettings();
	    settings.prettyPrint(false);
	    settings.escapeMode(EscapeMode.extended);
	    settings.charset("ASCII");
	    
	    String	modifiedContent		= doc.html();
	    		messageContentFinal	= modifiedContent;
	    		content				= messageContentFinal;
	    
		logger.info("content parsed:" + content);
		
		newMessage.setContent(content, "text/html");
		newMessage.saveChanges();
		
		/** Add attachments if exist */
		Message messageSent = createMessageWithAllAttachments(message, newMessage);
		
		return messageSent;
	}
	
	
	
	/**
	 * Create message part and add attachments if exists
	 */
	public Message createMessageWithAllAttachments(Part p, Message messageWithoutAttachment)
			throws MessagingException, IOException {
		logger.info("Entering createMessageWithAllAttachments");
		
		Message			messageWithAllAttachments	= null;
		Multipart		multipart					= new MimeMultipart();
		MimeBodyPart	messageBodyPart				= new MimeBodyPart();
		MimeBodyPart	attachPart					= null;
		
		if (p.getContentType().contains("multipart")) {
			
			messageWithAllAttachments = new MimeMessage(emailSession);
			
			messageWithAllAttachments.setFrom(messageWithoutAttachment.getFrom()[0]);
			messageWithAllAttachments.setRecipients(Message.RecipientType.TO,
					messageWithoutAttachment.getRecipients(Message.RecipientType.TO));
			messageWithAllAttachments.setRecipients(Message.RecipientType.CC,
					messageWithoutAttachment.getRecipients(Message.RecipientType.CC));
			messageWithAllAttachments.setRecipients(Message.RecipientType.BCC,
					messageWithoutAttachment.getRecipients(Message.RecipientType.BCC));
			messageWithAllAttachments.setReplyTo(messageWithoutAttachment.getReplyTo());
			messageWithAllAttachments.setSubject(messageWithoutAttachment.getSubject());
			
			if (filesToBeDeleted == null || filesToBeDeleted.isEmpty()) {
				messageWithAllAttachments.setContent(messageWithoutAttachment.getContent(),
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
				
				messageWithAllAttachments.setContent(multipart);
			}
		}
		
		else {
			logger.info("No attachments at all");
			
			messageWithAllAttachments = messageWithoutAttachment;
		}
		
		logger.info("Exiting createMessageWithAllAttachments");
		return messageWithAllAttachments;
	}
	


	/**
	 * Reads email content from mail server
	 */
	public HashMap<String, String> identifyElements(String content) {
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
	 * If message contains attachments, save it to temp directory
	 */
	public void downloadAttachments(Part p, Message messageWithoutAttachment)
			throws MessagingException, IOException {
		logger.info("Entering createLocalFileForAttachments");
		
		boolean b = false;
		File file = new File(Constants.TEMP_DIR_ATTACHMENTS);
		
		if (null != p.getDisposition() && p.getDisposition().equalsIgnoreCase(Part.ATTACHMENT)) {
			logger.info("Email contains attachement");
			
			MimeBodyPart mimeBodyPart = (MimeBodyPart) p;
			
			/* Creates Directory if not exists*/
			if (!file.exists())
				b = file.mkdirs();
			
			logger.info("Local directory for attaching file->" + file);
			
			mimeBodyPart.saveFile(file + "/" + mimeBodyPart.getFileName().trim());
			filesToBeDeleted.add(file + "/" + mimeBodyPart.getFileName().trim());
			fileNames.add(mimeBodyPart.getFileName().trim());
		}
		
		logger.info("Exiting createLocalFileForAttachments");
	}
	
	
	/**
	 * Upload file to document repository
	 */
	public void uploadFiles(ArrayList<String> paths, String partnerId) throws Exception {
		logger.info("Entering uploadFiles");
		
		if (!paths.isEmpty()) {
			for (String path : paths) {
				uploadedUuids.add(UploadToDocRepo.uploadFile(path, "emails_sent?partner_id=" + partnerId));
			}
		}
		
		logger.info("Exiting uploadFiles");
	}
	
	
	/**
	 * Send mail based on SMTP server configuration of partner
	 */
	public void sendMail(Message message) throws Exception {
		
		Transport	t			= null;
		String		partnerId	= getValueForConfig("partner_id");
		String		smtpServer	= getValueForConfig("smtp_server");
		String		userName	= getValueForConfig("user_name");
		String		password	= getValueForConfig("password");
		String		port		= getValueForConfig("port");
		
		logger.info("Send mail Partner Id:"  + partnerId);
		
		/* Use partner SMTP server setting for sending mail,if configured. */
		if (getValueForConfig("smtp_applicable").equals("1")
				&& !Strings.isNullOrEmpty(smtpServer) && !Strings.isNullOrEmpty(port)
				&& !Strings.isNullOrEmpty(userName) && !Strings.isNullOrEmpty(password)) {
			
			Properties props = new Properties();
			props.put("mail.smtp.auth", "true");
			props.put("mail.smtp.host", smtpServer.trim());
			props.put("mail.smtp.port", port);
			
			try {
				Session session = Session.getInstance(props, new javax.mail.Authenticator() {
					protected PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(getValueForConfig("user_name"),
								getValueForConfig("password").trim());
					}
				});
				
				session.setDebug(true);
				t = session.getTransport("smtp");
				t.connect();
			}
			catch (Exception e) {
				e.printStackTrace();
				System.out.println("Exception -->" + e.getMessage());
			}
			
			/* If SMTP authentication failure occurs, send mail through default
			 * SMTP configuration and trigger a mail to account manager */
			if (!t.isConnected()) {
				t = emailSession.getTransport("smtp");
				t.connect();
				Message mailFailureMessage = mailFailure(configMap.get("account_manager"));
				t.sendMessage(mailFailureMessage, mailFailureMessage.getAllRecipients());
			}
		}
		
		else {
			t = emailSession.getTransport("smtp");
			t.connect();
		}
		
		t.sendMessage(message, message.getAllRecipients());
		t.close();
	}
	
	
	/**
	 *  Incase of SMTP authentication failure, send mail to account manager
	 */
	public Message mailFailure(String accountManager) throws Exception
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
	 * Delete the documents uploaded in temp directory.
	 */
	public void deleteFiles(ArrayList<String> paths) {
		logger.info("Entering deleteFiles");
		
		File 		f = null;
		uploadedUuids = new ArrayList<String>();
		
		for (String path : paths) {
			
			f = new File(path);
			
			boolean isdeleted = f.delete();
			if (isdeleted) {
				logger.info("The generated attachment is deleted.");
			}
			else {
				logger.info("The generated attachment failed to get deleted.");
			}
		}
		
		logger.info("Exiting deleteFiles " );
	}
	
	
	/**
	 * Get value corresponding to key from elementsForMessage(Relay email) map 
	 */
	public String getValueForElementsMap(String key) {
		
		if (elementsForMessage.containsKey(key)
				&& !Strings.isNullOrEmpty(elementsForMessage.get(key))) {
			return elementsForMessage.get(key);
		}
		
		return "";
	}
	
	
	/**
	 * Get value corresponding to key from config(database config) map 
	 */
	public String getValueForConfig(String key) {
		
		if (configMap.containsKey(key) && !Strings.isNullOrEmpty(configMap.get(key))) {
			return configMap.get(key);
		}
		
		return "";
	}
	
	
	/**
	 * Get addressArr for composing message(Status - FORWARD/INACTIVE)
	 */
	private Address[] getAddressArrFromElementsMap(String key) throws Exception {
		
		String 		value 			= getValueForElementsMap(key);
		Address[] 	addressesArr	= null;
		
		System.out.println("value:" + value);
		if (!Strings.isNullOrEmpty(value)) {
			String[] addressArr = CommonUtil.extractAddr(value).split(";");
			
			if (key.equals("To")) {
				if (!configMap.isEmpty() && configMap.get("status").equalsIgnoreCase("forward")) {
					if (addressArr.length == 0) {
						addressArr = Constants.NOTIFICATION_EMAIL_ADDRESS.split(";");
					}
				}
			}
			
			if (addressArr != null) {
				addressesArr = new Address[addressArr.length];
				System.out.println("length:" + addressArr.length);
				for (int i = 0; i < addressArr.length; i++) {
					if (key.equals("From") || key.equals("To")) {
						String address = addressArr[i].split("@")[0].toString().toUpperCase();
						addressesArr[i] = new InternetAddress(addressArr[i], address);
					}
					else {
						addressesArr[i] = new InternetAddress(addressArr[i]);
					}
				}
			}
		}
		System.out.println("addressesArr:" + addressesArr);
		
		return addressesArr;
	}
	
	
	/**
	 * Get addressArr for composing message(Status - ACTIVE)
	 */
	private Address[] getAddressForActiveStatus(String configKey, String elementKey) throws AddressException {
		
		String[]	addressArr	= null;
		String[]	emailArr	= null;
		String		value		= getValueForConfig(configKey);
		
		System.out.println("value:"+ value);
		if (!Strings.isNullOrEmpty(value) && (!value.contains("generated"))) {
			addressArr = value.split(",");
		}
		else if (!Strings.isNullOrEmpty(value) && (value.contains("generated"))) {
			
			String[]	confArr			= value.split(",");
			String		emailAddress	= getValueForElementsMap(elementKey);
			
			if (!Strings.isNullOrEmpty(emailAddress)) {
				emailArr = CommonUtil.extractAddr(emailAddress).split(";");
			}
			else {
				if (elementKey.equals("To"))
					emailArr = Constants.NOTIFICATION_EMAIL_ADDRESS.split(";");
			}
			
			List<String> list = new ArrayList<String>(Arrays.asList(emailArr));
			list.addAll(Arrays.asList(confArr));
			
			while (list.remove("generated")) {
				// do nothing
			}
			
			Object[] c = list.toArray();
			addressArr = Arrays.copyOf(c, c.length, String[].class);
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
	 * Check If Config map is empty 
	 */
	public boolean checkConfigIsEmpty() {
		
		boolean isEmpty = false;
		if (configMap == null || configMap.isEmpty())
			isEmpty = true;
		
		return isEmpty;
	}
	
}
