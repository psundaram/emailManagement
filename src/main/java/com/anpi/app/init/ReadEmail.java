package com.anpi.app.init;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities.EscapeMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Component;

import com.anpi.app.constants.Constants;
import com.anpi.app.domain.EmailCredits;
import com.anpi.app.service.ReadEmailService;
import com.anpi.app.util.CommonUtil;
import com.anpi.app.util.UploadToDocRepo;
import com.google.common.base.Strings;

@Component
public class ReadEmail {
	
	private static final Logger	logger						= Logger.getLogger(ReadEmail.class);
	
	@Autowired
	ReadEmailService 			readEmailService;
	
	public Session				emailSession				= null;
	public String				messageContentFinal			= null;
	public Message				newMessage					= null;
	public Map<String, String>	configMap					= null;
	public List<String>			filesToBeDeleted			= null;
	public LinkedList<String>	fileNames					= null;
	public Message				messageToBeSent				= null;
	public Map<String, String>	elementsForMessage			= null;
	public List<String>			uploadedUuids				= new ArrayList<String>();
	
	
	static{
		ApplicationContext	context		= new ClassPathXmlApplicationContext("applicationContext.xml");
		ReadEmail			readEmail	= context.getBean(ReadEmail.class);
		
		/* Relay SMTP server details */
		EmailCredits 		emailCredits= new EmailCredits();
		
		emailCredits.setUsername("relay");
		emailCredits.setPassword(Constants.RELAY_PASSWORD);
		emailCredits.setPopHost(Constants.RELAY_HOST);
		
		readEmail.fetch(emailCredits);
	}
	
	
	/** 
	 * This method fetches the email based on content_type from mail server,
	 * then process it,inserts the value into db and deletes the email from the
	 * mail server.
	 */
	public void fetch(EmailCredits emailCredits) {
		logger.info("Entering Fetch method");
		
		Store		store;
		Folder		emailFolder;
		String		line	= null;
		Properties	properties;
		
		try {

			/* Set smtp server configurations */
			properties 	 = readEmailService.getProperties(emailCredits);
			emailSession = Session.getDefaultInstance(properties);
			
			/* create the POP3 store object and connect with the pop server */
			store 		 = emailSession.getStore("pop3");
			store.connect(emailCredits.getPopHost(), emailCredits.getUsername(),emailCredits.getPassword());
			
			/* create the folder object and open it */
			emailFolder = store.getFolder("INBOX");
			emailFolder.open(Folder.READ_WRITE);
			
			/* retrieve the messages from the folder */
			Message[] messages = emailFolder.getMessages();
			
			logger.info("Number of messages to be popped--->" + messages.length);
			
			/*Loop through each messages */
			for (Message message : messages) {
				
				filesToBeDeleted = new ArrayList<String>();
				fileNames 		 = new LinkedList<String>();
				
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

	
	/** 
	 * This method process the email based on content_type from mail server,
	 * and logs the email into db
	 */
	public String processMessage(Message message)
	{
		String partnerId = "1";
		try
		{
			/* Process the message based on the content-type */
			writePart(message);
			
			/* Fetch configuration based on the email name */
			configMap = readEmailService.getConfiguration(elementsForMessage);
			
			/* Compose message with all attachments */
			messageToBeSent = createMessage(message,elementsForMessage, configMap);
			
			/* Upload files to doc Repository */
			if (!Strings.isNullOrEmpty(CommonUtil.getValueForMap(elementsForMessage,"partner_id"))){
				partnerId = CommonUtil.getValueForMap(elementsForMessage,"partner_id");
			}
			
			if (null != filesToBeDeleted && !filesToBeDeleted.isEmpty()) 
				uploadFiles(filesToBeDeleted, partnerId);

			/*Insert into emailLogs table */
			int insertId = addLogger();
			
			/* If status = Active/forward, then send mail to the user and update
			 * the email log table */
			if (!configMap.isEmpty() && !configMap.get("status").equalsIgnoreCase("inactive")
					&& null != messageToBeSent) {
				readEmailService.sendMail(messageToBeSent,configMap,emailSession);
				readEmailService.updateLogger(insertId);
			}
			
			/* Deletes files from temp directory */
			if (null != filesToBeDeleted && !filesToBeDeleted.isEmpty()) {
				uploadedUuids 	 = new ArrayList<String>();
				readEmailService.deleteFiles(filesToBeDeleted);
			}
			
			return "YES";
		}
		catch (Exception e)
		{
			logger.info("Exception" + e.getMessage() + "\nFull exception:" + e);
			
			return "no";
		}
		finally
		{
			elementsForMessage 		 = null;
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
				elementsForMessage = readEmailService.identifyElements(content);
			}
			
			downloadAttachments(p);
		}
		
		/* check if the content has attachment */
		else if (p.isMimeType("multipart/*")) {
			
			logger.info("This is a Multipart");
			
			Multipart multipart = (Multipart) p.getContent();
			int count = multipart.getCount();
			
			logger.info("multipart count ==>" + count);
			
			for (int i = 0; i < count; i++) {
				writePart(multipart.getBodyPart(i));
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
			Object object = p.getContent();
			if (object instanceof String) {
				
				logger.info("This is a string");
				
				if (null == p.getDisposition()
						|| ((null != p.getDisposition()) && (!p.getDisposition().equalsIgnoreCase(
								Part.ATTACHMENT)))) {
					elementsForMessage = readEmailService.identifyElements(object.toString());
				}
				
				downloadAttachments(p);
			}
			
			else if (object instanceof InputStream) {
				
				logger.info("This is just an input stream");
				
				InputStream is = (InputStream) object;
				is = (InputStream) object;
				int c;
				
				while ((c = is.read()) != -1) {
					// System.out.write(c);
					// logger.info("Reads input Stream");
				}
				
				if (null == p.getDisposition()
						|| ((null != p.getDisposition()) && (!p.getDisposition().equalsIgnoreCase(
								Part.ATTACHMENT)))) {
					elementsForMessage = readEmailService.identifyElements(CommonUtil.getStringFromInputStream(is));
				}
				
				downloadAttachments(p);
			}
			
			else {
				logger.info("This is an unknown type : " + object.toString());
			}
		}
	}

	
	/**
	 * Inserts values into email_log table.
	 */
	public int addLogger() throws MessagingException {
		logger.info("Entering addLogger");
		
		/* Generate the map for the logs to be added.*/
		Map<String, String> 	parameterMap = readEmailService.createLoggerMap(elementsForMessage, configMap,newMessage,messageContentFinal);
		
		if (uploadedUuids != null && !uploadedUuids.isEmpty()) {
			parameterMap.put("attachments", CommonUtil.convertToCsv(uploadedUuids));
			parameterMap.put("file_names", CommonUtil.convertToCsv(fileNames));
		}
		
		int 					insertId 	 = readEmailService.insert(parameterMap);
		
		logger.info("Exiting addLogger --> " + insertId);
		return insertId;
	}
	
	
	/**
	 * Compose email message based on the relay message and database.
	 */
	public Message createMessage(Message message,Map<String, String> elementsForMessage, Map<String, String> configMap) throws Exception {
		logger.info("Entering createMessageOnly");
		
		String	content		= null;
				newMessage	= new MimeMessage(emailSession);
		String	status		= CommonUtil.getValueForMap(configMap,"status");
		String	useConfig	= CommonUtil.getValueForMap(elementsForMessage,"UseConfig");
		
		logger.info("Status -->" + status + " , UseConfig -->" + useConfig );
		
		/* Reads value from elementsMap(relay email content) and create new message */
		if (configMap.isEmpty() || "inactive".equalsIgnoreCase(status)
				|| "forward".equalsIgnoreCase(status) || ("ask".equalsIgnoreCase(status)
				&& "no".equalsIgnoreCase(useConfig))) {
			
			Address[] fromAddressArr = readEmailService.getAddressArrFromElementsMap("From",elementsForMessage,configMap);
			newMessage.setFrom(fromAddressArr[0]);
			
			Address[] toAddressesArr = readEmailService.getAddressArrFromElementsMap("To",elementsForMessage,configMap);
			newMessage.setRecipients(Message.RecipientType.TO, toAddressesArr);

			Address[] ccAddressesArr = readEmailService.getAddressArrFromElementsMap("CC",elementsForMessage,configMap);
			newMessage.setRecipients(Message.RecipientType.CC, ccAddressesArr);
			
			InternetAddress bccList[] = InternetAddress.parse(Constants.BCC_EMAIL_ADDRESS);
			newMessage.setRecipients(Message.RecipientType.BCC, bccList);
			
			Address[] replyToAddressesArr = readEmailService.getAddressArrFromElementsMap("ReplyTo",elementsForMessage,configMap);
			newMessage.setReplyTo(replyToAddressesArr);
				
			newMessage.setSubject(StringEscapeUtils.unescapeHtml(CommonUtil.getValueForMap(elementsForMessage,"Subject")));
			
			String signature = CommonUtil.getValueForMap(elementsForMessage,"Signature");
			if(!Strings.isNullOrEmpty(signature)){
				content = CommonUtil.getValueForMap(elementsForMessage,"Content") + "<br/><br/>" + signature;
			}else{
				content = CommonUtil.getValueForMap(elementsForMessage,"Content") + "<br/><br/>";
			}
			
			content = StringEscapeUtils.unescapeHtml(content);
			
		}
		/* Read values from the DB and create the new message */
		else {
			
			String fromEmailValue = CommonUtil.getValueForMap(configMap,"from_email");
			String fromAddress ;
			
			if (!Strings.isNullOrEmpty(fromEmailValue) && !"generated".equals(fromEmailValue)) 
				fromAddress = configMap.get("from_email");
			 else
				fromAddress = CommonUtil.getValueForMap(elementsForMessage,"From");
			
			newMessage.setFrom(new InternetAddress(fromAddress.toString(), fromAddress.split("@")[0].toString().toUpperCase()));
			newMessage.setRecipients(Message.RecipientType.TO,
					readEmailService.getAddressForActiveStatus("to_email", "To",elementsForMessage,configMap));
			newMessage.setRecipients(Message.RecipientType.CC,
					readEmailService.getAddressForActiveStatus("cc_email", "CC",elementsForMessage,configMap));
			newMessage.setReplyTo(readEmailService.getAddressForActiveStatus("reply_to_email", "ReplyTo",elementsForMessage,configMap));
			newMessage.setRecipients(Message.RecipientType.BCC,
					InternetAddress.parse(Constants.BCC_EMAIL_ADDRESS));
			
			String[] generatedString = readEmailService.generateActualContent(elementsForMessage, configMap);
			
			if (!Strings.isNullOrEmpty(CommonUtil.getValueForMap(configMap,"subject"))) {
				String subject = generatedString[0];
				newMessage.setSubject(StringEscapeUtils.unescapeHtml(subject));
			}
			else {
				newMessage.setSubject(StringEscapeUtils.unescapeHtml(CommonUtil.getValueForMap(elementsForMessage,"Subject")));
			}
			
			content = generatedString[1];
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
		Message messageSent = readEmailService.createMessageWithAllAttachments(message, newMessage,emailSession,filesToBeDeleted);
		
		return messageSent;
	}
	
	
	/**
	 * If message contains attachments, save it to temp directory
	 */
	public void downloadAttachments(Part part)
			throws MessagingException, IOException {
		logger.info("Entering createLocalFileForAttachments");
		
		boolean mkDir = false;
		File file = new File(Constants.TEMP_DIR_ATTACHMENTS);
		
		if (null != part.getDisposition() && part.getDisposition().equalsIgnoreCase(Part.ATTACHMENT)) {
			logger.info("Email contains attachement");
			
			MimeBodyPart mimeBodyPart = (MimeBodyPart) part;
			
			/* Creates Directory if not exists*/
			if (!file.exists()){
				mkDir = file.mkdirs();
			}
			
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
	public void uploadFiles(List<String> paths, String partnerId) throws Exception {
		logger.info("Entering uploadFiles");
		
		if (!paths.isEmpty()) {
			for (String path : paths) {
				uploadedUuids.add(UploadToDocRepo.uploadFile(path, "emails_sent?partner_id=" + partnerId));
			}
		}
		
		logger.info("Exiting uploadFiles");
	}
	
	
}
