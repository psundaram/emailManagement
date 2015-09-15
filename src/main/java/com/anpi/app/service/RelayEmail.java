package com.anpi.app.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.NoSuchProviderException;
import javax.mail.Part;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import net.sf.json.JSONException;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities.EscapeMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.anpi.app.constants.Constants;
import com.anpi.app.domain.EmailCredits;
import com.anpi.app.util.DateUtil;
import com.anpi.app.util.EmailUtil;
import com.anpi.app.util.URLReaderUtil;
import com.anpi.app.util.UploadToDocRepo;
import com.anpi.app.util.Util;
import com.anpi.app.util.WriteMailPart;
import com.google.common.base.Strings;


/**
 * Fetch email from the mail server based on the content-type, processes the
 * mail based on its configuration, send email to the respective receipent,
 * inserts log into database and deletes the mail from the mail server.
 */
@Component
public class RelayEmail {

	/** Logging for RelayEmail class */
	private static final Logger logger = Logger.getLogger(RelayEmail.class);

	/** DAO Object. */
	@Autowired
	RelayEmailDAO relayEmailDAO;

	/** Email session Object. */
	public Session emailSession = null;

	/** Final Content */
	public String messageContentFinal = null;

	/** Message object to store composed message */
	public Message newMessage = null;

	/** Hash map of config values */
	public HashMap<String, String> configMap = null;

	/** List of files to be deleted */
	public ArrayList<String> filesToBeDeleted = null;

	/** List of file names */
	public LinkedList<String> fileNames = null;

	/** Message to be sent. */
	public Message messageToBeSent = null;

	/** Message without attachment. */
	public Message messageWithoutAttachment = null;

	/** Hash map of mail values */
	public HashMap<String, String> elementsForMessage = null;

	/** List of uploaded uuids. */
	public ArrayList<String> uploadedUuids = new ArrayList<String>();

	/**
	 * This method fetches the email based on content_type from mail server,
	 * then process it,inserts the value into db and deletes the email from the
	 * mail server.
	 * 
	 * @param emailCredits
	 *            the email credits
	 */
	public void fetch(EmailCredits emailCredits) {
		logger.info("Entering Fetch method");
		System.out.println("Entering Fetch method");
		Store store;
		Folder emailFolder;
		String line;
		try {
			// create properties field
			Properties properties = new Properties();
			properties.put("mail.store.protocol", "pop3");
			properties.put("mail.pop3.host", emailCredits.getPopHost());
			properties.put("mail.smtp.auth", "none");
			properties.put("mail.smtp.starttls.enable", "true");
			properties.put("mail.smtp.host", "smtp.anpi.com");
			properties.put("mail.smtp.port", "25");
			emailSession = Session.getDefaultInstance(properties);
			/**
			 * create the POP3 store object and connect with the pop server Use
			 * the storeType object Identify the store object by the email
			 * server's configuration
			 */
			store = emailSession.getStore("pop3");
			store.connect(emailCredits.getPopHost(), emailCredits.getUsername(), emailCredits.getPassword());
			// create the folder object and open it
			emailFolder = store.getFolder("INBOX");
			emailFolder.open(Folder.READ_WRITE);
			// retrieve the messages from the folder in an array and print it
			Message[] messages = emailFolder.getMessages();
			System.out.println("Number of messages to be popped--->" + messages.length);
			logger.info("Number of messages to be popped--->" + messages.length);
			for (int i = 0; i < messages.length; i++) {
				filesToBeDeleted = new ArrayList<String>();
				fileNames = new LinkedList<String>();
				Message message = messages[i];
				if (!message.isSet(Flags.Flag.DELETED)) {

					System.out.println("Message Number that is being read ---> " + message.getMessageNumber());
					logger.info("Message Number that is being read ---> " + message.getMessageNumber());
					/** New objects for every new email in queue */

					/**
					 * ######################## Comment below to mark the
					 * messages read - if case of error in message
					 */

					try {
						writePart(message);
						// System.out.println("message property -> "+message.getContentType()+" => "+message.getDisposition());
						messageToBeSent = createMessageWithAllAttachments(message, messageWithoutAttachment);
						String partnerId = "1";
						System.out.println("configmap => " + configMap);
						logger.info("configmap => " + configMap);
						if (!configMap.isEmpty() && (configMap.containsKey("partner_id")) && (null != configMap.get("partner_id")) && ("".equalsIgnoreCase((configMap.get("partner_id").trim())))) {
							partnerId = configMap.get("partner_id");
						}
						System.out.println("filesToBeDeleted:" + filesToBeDeleted.size());
						logger.info("filesToBeDeleted:" + filesToBeDeleted.size());
						// Upload files to doc Repository
						if (null != filesToBeDeleted && filesToBeDeleted.size() > 0) {
							uploadFiles(filesToBeDeleted, partnerId);
						}

						// Add logger to the Email_logs tables here.
						int insertId = addLogger();
						System.out.println("insert ID => " + insertId);

						if (!configMap.isEmpty() && !configMap.get("status").equalsIgnoreCase("inactive") && null != messageToBeSent) {
							sendMail(messageToBeSent);
						}

						// This is where the updation of the same row will happen.
						// Changing status of mail from created to Sent if config.status is active
						if (!configMap.isEmpty() && !configMap.get("status").equalsIgnoreCase("inactive")) {
							relayEmailDAO.updateLogger(insertId);
						}

						// Deletes file uploaded to local temp directory
						if (null != filesToBeDeleted && filesToBeDeleted.size() > 0) {
							deleteFiles(filesToBeDeleted);
						}
						line = "YES";
					} catch (Exception e) {
						logger.info("Exception" + e.getMessage());
						System.out.println("Exception" + e.getMessage());
						e.printStackTrace();
						line = "no";
					} finally {
						elementsForMessage = null;
					}
					/* ######################## END COMMENT */
					System.out.println("Want to see the original email? - commented");
					logger.info("Want to see the original email? - commented");
					// System.out.println("line--> "+line);
					if ("YES".equalsIgnoreCase(line)) {
						System.out.println("This is the original Email");
						logger.info("This is the original Email");
						System.out.println("--------------------------");
						logger.info("--------------------------");
						message.writeTo(System.out);
						System.out.println("Original Email is read");
						logger.info("Original Email is read");
						message.setFlag(Flags.Flag.DELETED, true);
					}
				} else {
					System.out.println("This message is marked notdeleted ---> " + message.getMessageNumber());
					logger.info("This message is marked notdeleted ---> " + message.getMessageNumber());
				}
			}// close the store and folder objects
				// Set to true if msg needs to be marked deleted
			emailFolder.close(false);
			store.close();
		} catch (NoSuchProviderException e) {
			e.printStackTrace();
			System.out.println("NoSuchProviderException" + e.getMessage());
			logger.info("NoSuchProviderException" + e.getMessage());
		} catch (MessagingException e) {
			e.printStackTrace();
			System.out.println("MessagingException" + e.getMessage());
			logger.info("MessagingException" + e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Exception" + e.getMessage());
			logger.info("Exception" + e.getMessage());
		}
		System.out.println("Exiting fetch");
		logger.info("Exiting fetch");
	}

	/**
	 * Inserts values into email_log table.
	 * 
	 * @return the id of email_logs table
	 * @throws MessagingException
	 */
	public int addLogger() throws MessagingException {
		System.out.println("Entering addLogger");
		logger.info("Entering addLogger");
		// Generate the map for the logs to be added.
		HashMap<String, String> parameterMap = createLoggerMap(elementsForMessage, configMap, newMessage);
		int insertId = relayEmailDAO.insert(parameterMap);
		// new DbConnect().putLogs(parameterMap);
		System.out.println("Exiting addLogger --> " + insertId);
		logger.info("Exiting addLogger --> " + insertId);
		return insertId;
	}

	/**
	 * Reads the values from relay and config table and insert values into
	 * email_logs table.
	 * 
	 * @param elementsForMessage
	 *            the elements for message
	 * @param configMap
	 *            the config map
	 * @param newMessage
	 *            the new message
	 * @return the hashmap of column values to be inserted in database
	 * @throws MessagingException
	 */
	public HashMap<String, String> createLoggerMap(HashMap<String, String> elementsForMessage, HashMap<String, String> configMap, Message newMessage) throws MessagingException {
		logger.info("Entering createLoggerMap");
		System.out.println("Entering createLoggerMap");
		HashMap<String, String> parameterMap = new HashMap<String, String>();
		String signature = null;
		String content = null;
		String partnerId = null;
		String customerId = null;
		String orderId = null;
		int mailSent = 0;
		logger.info("createLoggerMap configMap -->" + configMap + ", elementsForMessage-->" + elementsForMessage);
		System.out.println("ConfigMap Status -->" + configMap.get("status"));
		logger.info("ConfigMap Status -->" + configMap.get("status"));
		if (configMap.isEmpty() || "inactive".equalsIgnoreCase(configMap.get("status")) || "forward".equalsIgnoreCase(configMap.get("status"))
				|| ("ask".equalsIgnoreCase("status") && "no".equalsIgnoreCase(elementsForMessage.get("UseConfig")))) {
			System.out.println("UseConfig-->" + elementsForMessage.get("UseConfig"));
			logger.info("UseConfig-->" + elementsForMessage.get("UseConfig"));
			// Read from the mail that reached the relay box and Putting values
			// into table with relay values.
			if (!configMap.isEmpty()
					&& (configMap.get("status").equalsIgnoreCase("ask") && "no".equalsIgnoreCase(elementsForMessage.get("UseConfig")) || (configMap.get("status").equalsIgnoreCase("forward")))) {
				mailSent = 1;
			} else {
				mailSent = 0;
			}
			if (!configMap.isEmpty() && configMap.get("status").equalsIgnoreCase("forward"))
				parameterMap.put("configuration", "FORWARD");
			else
				parameterMap.put("configuration", "DISABLED");
			parameterMap.put("email_name", elementsForMessage.get("EmailName"));
			parameterMap.put("from", elementsForMessage.get("From"));
			if(!configMap.isEmpty() && configMap.get("status").equalsIgnoreCase("forward")){
				if(elementsForMessage.containsKey("To") && StringUtils.isNotBlank(elementsForMessage.get("To"))){
					parameterMap.put("to", elementsForMessage.get("To").replaceAll(";", ","));
				}
				else{
					parameterMap.put("to", Constants.NOTIFICATION_EMAIL_ADDRESS.replaceAll(";", ","));
				}
			}else{
				parameterMap.put("to", elementsForMessage.get("To").replaceAll(";", ","));
			}
			
			if (elementsForMessage.containsKey("CC")) {
				parameterMap.put("cc", elementsForMessage.get("CC").replaceAll(";", ","));
			}
			if (elementsForMessage.containsKey("ReplyTo") && StringUtils.isNotBlank(elementsForMessage.get("ReplyTo"))) {
				parameterMap.put("reply_to", elementsForMessage.get("ReplyTo").replaceAll(";", ","));
			} else {
				parameterMap.put("reply_to", elementsForMessage.get("From"));
			}
			if (elementsForMessage.containsKey("Signature") && StringUtils.isNotBlank(elementsForMessage.get("Signature"))) {
				signature = elementsForMessage.get("Signature");
				System.out.println("Signature" + signature);
			} else {
				signature = "";
			}
			if (elementsForMessage.containsKey("Content")) {
				content = elementsForMessage.get("Content");
				content = Jsoup.parse(content).toString();
			}
			if (elementsForMessage.containsKey("CustomerID") && StringUtils.isNotBlank(elementsForMessage.get("CustomerID"))) {
				customerId = elementsForMessage.get("CustomerID");

			}
			if (elementsForMessage.containsKey("OrderID") && StringUtils.isNotBlank(elementsForMessage.get("OrderID"))) {
				orderId = elementsForMessage.get("OrderID");
			}
			if (configMap.containsKey("source") && (null != configMap.get("source")) && (!configMap.get("source").isEmpty()) && (!(configMap.get("source").trim().equals("")))) {
				parameterMap.put("classification", configMap.get("source"));
			}
			// Inserting partner_id value in db
			if (configMap.containsKey("partner_id") && (null != configMap.get("partner_id")) && (!configMap.get("partner_id").isEmpty()) && (!(configMap.get("partner_id").trim().equals("")))) {
				partnerId = configMap.get("partner_id");
			} else if (elementsForMessage.containsKey("PartnerID")) {
				partnerId = elementsForMessage.get("PartnerID");
			} else {
				// do nothing
			}
			content = content + "<br/><br/>" + signature;
		} else {
			// Read from the values fetched from the DB and putting values into
			// table with actual configs.
			if (configMap.containsKey("email_name_display") && (null != configMap.get("email_name_display")) && (!configMap.get("email_name_display").isEmpty())
					&& (!(configMap.get("email_name_display").trim().equals("")))) {
				parameterMap.put("email_name", configMap.get("email_name_display"));
			} else {
				/* Therotically this cannot happen */
				parameterMap.put("email_name", "");
			}

			parameterMap.put("configuration", "ENABLED");

			if (configMap.containsKey("from_email") && (null != configMap.get("from_email")) && (!configMap.get("from_email").isEmpty()) && (!(configMap.get("from_email").trim().equals("")))
					&& (!(configMap.get("from_email").trim().contains("generated")))) {
				parameterMap.put("from", configMap.get("from_email"));
			} else {
				parameterMap.put("from", elementsForMessage.get("From"));
			}

			if (configMap.containsKey("to_email") && (null != configMap.get("to_email")) && (!configMap.get("to_email").isEmpty()) && (!(configMap.get("to_email").trim().equals("")))
					&& (!(configMap.get("to_email").trim().contains("generated")))) {
				parameterMap.put("to", configMap.get("to_email").replaceAll(";", ","));
			} else if (configMap.containsKey("to_email") && (null != configMap.get("to_email")) && (!configMap.get("to_email").isEmpty()) && (!(configMap.get("to_email").trim().equals("")))
					&& (configMap.get("to_email").trim().contains("generated"))) {
				String confStr = configMap.get("to_email");
				String emailStr = null;
				if(elementsForMessage.containsKey("To") && StringUtils.isNotBlank(elementsForMessage.get("To"))){
				 emailStr = elementsForMessage.get("To");
				}else{
					emailStr = Constants.NOTIFICATION_EMAIL_ADDRESS;
				}
				parameterMap.put("to", ((confStr + "," + emailStr).replaceAll(";", ",").replaceAll(",generated", "").replace("generated,", "").replaceAll("generated", "").replace(",,", ",")));
			} else {
				parameterMap.put("to", elementsForMessage.get("To").replaceAll(";", ","));
			}

			if (configMap.containsKey("cc_email") && (null != configMap.get("cc_email")) && (!configMap.get("cc_email").isEmpty()) && (!(configMap.get("cc_email").trim().equals("")))
					&& (!(configMap.get("cc_email").trim().contains("generated")))) {
				parameterMap.put("cc", configMap.get("cc_email").replaceAll(";", ","));
			} else if (configMap.containsKey("cc_email") && (null != configMap.get("cc_email")) && (!configMap.get("cc_email").isEmpty()) && (!(configMap.get("cc_email").trim().equals("")))
					&& (configMap.get("cc_email").trim().contains("generated"))) {
				String confStr = configMap.get("cc_email");
				String emailStr = "";
				if (elementsForMessage.containsKey("CC") && StringUtils.isNotBlank(elementsForMessage.get("CC")))
					emailStr = elementsForMessage.get("CC");
				System.out.println("confStr:" + confStr + "emaiStr:" + emailStr);
				parameterMap.put("cc", ((confStr + "," + emailStr).replaceAll(";", ",").replaceAll(",generated", "").replace("generated,", "").replaceAll("generated", "").replace(",,", ",")));
				System.out.println("CC:" + parameterMap.get("cc"));
			}
			if (configMap.containsKey("reply_to_email") && (null != configMap.get("reply_to_email")) && (!configMap.get("reply_to_email").isEmpty())
					&& (!(configMap.get("reply_to_email").trim().equals(""))) && (!(configMap.get("reply_to_email").trim().contains("generated")))) {
				parameterMap.put("reply_to", configMap.get("reply_to_email").replaceAll(";", ","));
			} else if (configMap.containsKey("reply_to_email") && (null != configMap.get("reply_to_email")) && (!configMap.get("reply_to_email").isEmpty())
					&& (!(configMap.get("reply_to_email").trim().equals(""))) && (configMap.get("reply_to_email").trim().contains("generated"))) {
				String confStr = configMap.get("reply_to_email");
				String emailStr = null;
				if (elementsForMessage.containsKey("ReplyTo") && StringUtils.isNotBlank(elementsForMessage.get("ReplyTo"))) {
					emailStr = elementsForMessage.get("ReplyTo");
				} else {
					emailStr = parameterMap.get("from");
				}
				parameterMap.put("reply_to", ((confStr + "," + emailStr).replaceAll(";", ",").replaceAll(",generated", "").replace("generated,", "").replaceAll("generated", "").replace(",,", ",")));
			} else if (elementsForMessage.containsKey("ReplyTo") && StringUtils.isNotBlank(elementsForMessage.get("ReplyTo"))) {
				parameterMap.put("reply_to", elementsForMessage.get("ReplyTo"));
			} else {
				parameterMap.put("reply_to", parameterMap.get("from"));
			}
			if (configMap.containsKey("content_template") && (null != configMap.get("content_template")) && (!configMap.get("content_template").isEmpty())
					&& (!(configMap.get("content_template").trim().equals(""))) && (!(configMap.get("content_template").trim().contains("generated")))) {
				content = configMap.get("content_template");
			} else {
				content = "";
			}
			if (messageContentFinal != null && StringUtils.isNotBlank(messageContentFinal)) {
				content = messageContentFinal;
			}
			if (configMap.containsKey("source") && (null != configMap.get("source")) && (!configMap.get("source").isEmpty()) && (!(configMap.get("source").trim().equals("")))) {
				parameterMap.put("classification", configMap.get("source"));
			}
			// Inserting partner_id value in db
			// If condition, in case of Channel partner
			if(configMap.containsKey("actual_partner_id") && StringUtils.isNotBlank(configMap.get("actual_partner_id"))){
				if(configMap.containsKey("partner_type") && (!Strings.isNullOrEmpty(configMap.get("partner_type"))) && (configMap.get("partner_type").equals("2")))
					parameterMap.put("parent_partner_id",configMap.get("actual_partner_id"));
				partnerId = configMap.get("partner_id");
			}
			else if (configMap.containsKey("partner_id") && (null != configMap.get("partner_id")) && (!configMap.get("partner_id").isEmpty()) && (!(configMap.get("partner_id").trim().equals("")))) {
				partnerId = configMap.get("partner_id");
			} else if (elementsForMessage.containsKey("PartnerID") && (null != elementsForMessage.get("PartnerID")) && (!elementsForMessage.get("PartnerID").isEmpty())
					&& (!(elementsForMessage.get("PartnerID").trim().equals("")))) {
				partnerId = elementsForMessage.get("PartnerID");
			} else {
				// do nothing
			}
			if (elementsForMessage.containsKey("CustomerID") && StringUtils.isNotBlank(elementsForMessage.get("CustomerID"))) {
				customerId = elementsForMessage.get("CustomerID");
			}
			if (elementsForMessage.containsKey("OrderID") && StringUtils.isNotBlank(elementsForMessage.get("OrderID"))) {
				orderId = elementsForMessage.get("OrderID");
			}
			mailSent = 1;

		}
		String sentDate = DateUtil.convertToUTCString(new Date());
		parameterMap.put("mail_sent", String.valueOf(mailSent));
		parameterMap.put("sent_date", sentDate);
		parameterMap.put("setId", elementsForMessage.get("SetID"));
		parameterMap.put("partner_id", partnerId);
		String subject = StringEscapeUtils.escapeSql(newMessage.getSubject());
		parameterMap.put("subject", StringEscapeUtils.unescapeHtml(subject));
		parameterMap.put("content", StringEscapeUtils.escapeSql(content));
		parameterMap.put("customer_id", customerId);
		parameterMap.put("order_id", orderId);
		if (uploadedUuids != null && !uploadedUuids.isEmpty()) {
			parameterMap.put("attachments", Util.convertToCsv(uploadedUuids));
			parameterMap.put("file_names", Util.convertToCsv(fileNames));
		}
		parameterMap.put("sending_status", "Created");
		parameterMap.put("resend_count", "0");

		/*
		 * Message Object is never been used as of now. The most ideal way to
		 * read values for the logger would be from the Message object.
		 */
		logger.info("Exiting createLoggerMap");
		System.out.println("Exiting createLoggerMap");
		return parameterMap;
	}

	/**
	 * Compose email message based on the relay message and database.
	 * 
	 * @param elementsForMessage
	 *            the elements for message
	 * @param configMap
	 *            the config map
	 * @return the message created based on the value from the database and mail
	 *         from relay
	 * @throws SQLException
	 * @throws MessagingException
	 * @throws JSONException
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public Message createMessageOnly(HashMap<String, String> elementsForMessage, HashMap<String, String> configMap) throws SQLException, MessagingException, JSONException, IOException {
		System.out.println("Entering createMessageOnly");
		logger.info("Entering createMessageOnly");
		Message newMessage = new MimeMessage(emailSession);
		if (configMap.isEmpty() || "inactive".equalsIgnoreCase(configMap.get("status")) || "forward".equalsIgnoreCase(configMap.get("status")) || "ask".equalsIgnoreCase("status")
				&& "no".equalsIgnoreCase(elementsForMessage.get("UseConfig"))) {
			// Read values from the message object passed to relay and create
			// the new message
			String[] fromAddressArr = EmailUtil.extractAddr(elementsForMessage.get("From")).split(";");
			InternetAddress fromAddress = new InternetAddress(fromAddressArr[0].toString(), fromAddressArr[0].split("@")[0].toString().toUpperCase());
			newMessage.setFrom(fromAddress);
			String[] toAddressArr = null;
			if(!configMap.isEmpty() && configMap.get("status").equalsIgnoreCase("forward")){
				if(elementsForMessage.containsKey("To") && StringUtils.isNotBlank(elementsForMessage.get("To"))){
					 toAddressArr = EmailUtil.extractAddr(elementsForMessage.get("To")).split(";");
				}
				else{
					toAddressArr = Constants.NOTIFICATION_EMAIL_ADDRESS.split(";");
				}
			}else{
				toAddressArr = EmailUtil.extractAddr(elementsForMessage.get("To")).split(";");
			}
			Address[] toAddressesArr = new Address[toAddressArr.length];
			for (int i = 0; i < toAddressArr.length; i++) {
				toAddressesArr[i] = new InternetAddress(toAddressArr[i], toAddressArr[i].split("@")[0].toString().toUpperCase());
			}
			newMessage.setRecipients(Message.RecipientType.TO, toAddressesArr);

			if (elementsForMessage.containsKey("CC") && !Strings.isNullOrEmpty(elementsForMessage.get("CC"))) {
				String[] ccAddressArr = elementsForMessage.get("CC").split(";");
				Address[] ccAddressesArr = new Address[ccAddressArr.length];
				for (int i = 0; i < ccAddressArr.length; i++) {
					ccAddressesArr[i] = new InternetAddress(ccAddressArr[i]);
				}
				newMessage.setRecipients(Message.RecipientType.CC, ccAddressesArr);
			}
			InternetAddress bccList[] = InternetAddress.parse(Constants.BCC_EMAIL_ADDRESS);
			newMessage.setRecipients(Message.RecipientType.BCC, bccList);
			if (elementsForMessage.containsKey("ReplyTo") && !Strings.isNullOrEmpty(elementsForMessage.get("ReplyTo"))) {
				String[] replyToAddressArr = elementsForMessage.get("ReplyTo").split(";");
				Address[] replyToAddressesArr = new Address[replyToAddressArr.length];
				for (int i = 0; i < replyToAddressArr.length; i++) {
					replyToAddressesArr[i] = new InternetAddress(replyToAddressArr[i]);
				}
				newMessage.setReplyTo(replyToAddressesArr);
			}
			newMessage.setSubject(StringEscapeUtils.unescapeHtml(elementsForMessage.get("Subject")));
			String signature = "";
			if (elementsForMessage.containsKey("Signature") && (StringUtils.isNotBlank(elementsForMessage.get("Signature")))) {
				signature = elementsForMessage.get("Signature");
			}
			String content = elementsForMessage.get("Content") + "<br/><br/>" + signature;
			content = StringEscapeUtils.unescapeHtml(content);
			Document doc = Jsoup.parse(content);

		    Document.OutputSettings settings = doc.outputSettings();

		    settings.prettyPrint(false);
		    settings.escapeMode(EscapeMode.extended);
		    settings.charset("ASCII");

		    String modifiedContent= doc.html();
		    content = modifiedContent;
			logger.info("content parsed:" + content);
			newMessage.setContent(content, "text/html");
		} else {
			// Read values from the DB and create the new message
			String fromAddress;
			if (configMap.containsKey("from_email") && (null != configMap.get("from_email")) && (!configMap.get("from_email").isEmpty()) && (!(configMap.get("from_email").trim().equals("")))
					&& (!(configMap.get("from_email").trim().equals("generated")))) {
				fromAddress = configMap.get("from_email");
			} else {
				fromAddress = elementsForMessage.get("From");
			}
			newMessage.setFrom(new InternetAddress(fromAddress.toString(), fromAddress.split("@")[0].toString().toUpperCase()));
			String[] toAddressArr = null;
			String[] toEmailArr = null;
			if (configMap.containsKey("to_email") && (null != configMap.get("to_email")) && (!configMap.get("to_email").isEmpty()) && (!(configMap.get("to_email").trim().equals("")))
					&& (!(configMap.get("to_email").trim().contains("generated")))) {
				toAddressArr = configMap.get("to_email").split(",");
			} else if (configMap.containsKey("to_email") && (null != configMap.get("to_email")) && (!configMap.get("to_email").isEmpty()) && (!(configMap.get("to_email").trim().equals("")))
					&& (configMap.get("to_email").trim().contains("generated"))) {
				String[] confArr = configMap.get("to_email").split(",");
				System.out.println(elementsForMessage.get("To"));
				if(elementsForMessage.get("To")!=null && !elementsForMessage.get("To").equals("")){
					toEmailArr = EmailUtil.extractAddr(elementsForMessage.get("To")).split(";");
				}else{
					toEmailArr = Constants.NOTIFICATION_EMAIL_ADDRESS.split(";");
				}
				List list = new ArrayList(Arrays.asList(toEmailArr));
				list.addAll(Arrays.asList(confArr));
				// System.out.println("list value -> "+list);
				while (list.remove("generated")) {
					// do nothing
				}
				Object[] c = list.toArray();
				toAddressArr = Arrays.copyOf(c, c.length, String[].class);
			} else {
				toAddressArr = elementsForMessage.get("To").split(";");
			}
			if (toAddressArr != null) {
				Address[] toAddressesArr = new Address[toAddressArr.length];
				for (int i = 0; i < toAddressArr.length; i++) {
					toAddressesArr[i] = new InternetAddress(toAddressArr[i]);
				}
				newMessage.setRecipients(Message.RecipientType.TO, toAddressesArr);
			}
			String[] ccAddressArr = null;
			if (configMap.containsKey("cc_email") && (null != configMap.get("cc_email")) && (!configMap.get("cc_email").isEmpty()) && (!(configMap.get("cc_email").trim().equals("")))
					&& (!(configMap.get("cc_email").trim().contains("generated")))) {
				ccAddressArr = configMap.get("cc_email").split(",");
			} else if (configMap.containsKey("cc_email") && (null != configMap.get("cc_email")) && (!configMap.get("cc_email").isEmpty()) && (!(configMap.get("cc_email").trim().equals("")))
					&& (configMap.get("cc_email").trim().contains("generated"))) {
				String[] confArr = configMap.get("cc_email").split(",");
				List list = null;
				if (elementsForMessage.containsKey("CC") && (null != elementsForMessage.get("CC")) && (!("".equalsIgnoreCase(elementsForMessage.get("CC").trim())))) {
					String[] emailArr = elementsForMessage.get("CC").split(";");
					list = new ArrayList(Arrays.asList(emailArr));
				} else {
					list = new ArrayList<String>();
				}
				list.addAll(Arrays.asList(confArr));
				while (list.remove("generated")) {
					// do nothing
				}
				Object[] c = list.toArray();
				ccAddressArr = Arrays.copyOf(c, c.length, String[].class);
			}
			if (ccAddressArr != null) {
				Address[] ccAddressesArr = new Address[ccAddressArr.length];
				for (int i = 0; i < ccAddressArr.length; i++) {
					ccAddressesArr[i] = new InternetAddress(ccAddressArr[i]);
				}
				newMessage.setRecipients(Message.RecipientType.CC, ccAddressesArr);
			}
			InternetAddress bccList[] = InternetAddress.parse(Constants.BCC_EMAIL_ADDRESS);
			newMessage.setRecipients(Message.RecipientType.BCC, bccList);
			String[] replyToAddressArr = null;
			if (configMap.containsKey("reply_to_email") && (null != configMap.get("reply_to_email")) && (!configMap.get("reply_to_email").isEmpty())
					&& (!(configMap.get("reply_to_email").trim().equals(""))) && (!(configMap.get("reply_to_email").trim().contains("generated")))) {
				replyToAddressArr = configMap.get("reply_to_email").split(",");
			} else if (configMap.containsKey("reply_to_email") && (null != configMap.get("reply_to_email")) && (!configMap.get("reply_to_email").isEmpty())
					&& (!(configMap.get("reply_to_email").trim().equals(""))) && (configMap.get("reply_to_email").trim().contains("generated"))) {
				String[] confArr = configMap.get("reply_to_email").split(",");
				List list = null;
				if (elementsForMessage.containsKey("ReplyTo") && (null != elementsForMessage.get("ReplyTo")) && (!("".equalsIgnoreCase(elementsForMessage.get("ReplyTo").trim())))) {
					String[] emailArr = elementsForMessage.get("ReplyTo").split(";");
					list = new ArrayList(Arrays.asList(emailArr));
				} else {
					list = new ArrayList<String>();
				}
				list.addAll(Arrays.asList(confArr));
				while (list.remove("generated")) {
					// do nothing
				}
				Object[] c = list.toArray();
				replyToAddressArr = Arrays.copyOf(c, c.length, String[].class);
			}
			if (replyToAddressArr != null) {
				Address[] replyToAddressesArr = new Address[replyToAddressArr.length];
				for (int i = 0; i < replyToAddressArr.length; i++) {
					replyToAddressesArr[i] = new InternetAddress(replyToAddressArr[i]);
				}
				newMessage.setReplyTo(replyToAddressesArr);
			}
			String generatedString = relayEmailDAO.generateActualContent(elementsForMessage, configMap);
			if (configMap.containsKey("subject") && (null != configMap.get("subject")) && (!configMap.get("subject").isEmpty()) && (!(configMap.get("subject").trim().equals("")))) {
			String subject = generatedString.split("##")[0].toString();
				System.out.println("subject:" + subject);
				newMessage.setSubject(StringEscapeUtils.unescapeHtml(subject));
			} else {
				newMessage.setSubject(StringEscapeUtils.unescapeHtml(elementsForMessage.get("Subject")));
			}
			
			String content = generatedString.split("##")[1].toString();
			//System.out.println("Generated contetn"+content);
			Document doc = Jsoup.parse(content);

		    Document.OutputSettings settings = doc.outputSettings();

		    settings.prettyPrint(false);
		    settings.escapeMode(EscapeMode.extended);
		    settings.charset("ASCII");

		    String modifiedContent= doc.html();
			messageContentFinal = modifiedContent;
			content = messageContentFinal;
			logger.info("Content" + content);
			System.out.println("Jsoup parsed content:"+content);
			newMessage.setContent(content,"text/html");
		}
		newMessage.saveChanges();
		return newMessage;
	}

	/**
	 * Creating message body part.
	 * 
	 * @param elementsForMessage
	 *            the elements for message
	 * @return the message object for message
	 * @throws Exception 
	 */
	public Message createNoAttachmentMessage(HashMap<String, String> elementsForMessage) throws Exception {
		System.out.println("Entering createNoAttachmentMessage");
		logger.info("Entering createNoAttachmentMessage");
		configMap = new HashMap<String, String>();
		// DbConnect dbConnect = new DbConnect();
		// boolean queryParamForSetID;
		String setId;
		String emailName = StringUtils.deleteWhitespace(elementsForMessage.get("EmailName"));
		if (elementsForMessage.containsKey("OrderID") && !Strings.isNullOrEmpty(elementsForMessage.get("OrderID"))) {
			configMap = relayEmailDAO.getConfigForOrderId(elementsForMessage.get("OrderID"), emailName);
		}
		if (elementsForMessage.containsKey("CustomerID") && !Strings.isNullOrEmpty(elementsForMessage.get("CustomerID")) && (configMap == null || configMap.isEmpty())) {
			String customerId = elementsForMessage.get("CustomerID");
			configMap = relayEmailDAO.getConfigForCustomerId(customerId, emailName);
		}
		if (elementsForMessage.containsKey("SetID") && !Strings.isNullOrEmpty(elementsForMessage.get("SetID")) && (configMap == null || configMap.isEmpty())) {
			if (elementsForMessage.containsKey("isLegacy") && StringUtils.isNotBlank(elementsForMessage.get("isLegacy"))
					&& (elementsForMessage.get("isLegacy").equalsIgnoreCase(Constants.Y))){
				System.out.println("IsLegacy - Yes");
				setId = Constants.ANPI;
			}else{
				System.out.println("IsLegacy - No");
				setId = elementsForMessage.get("SetID");
			}
			// queryParamForSetID = true;
			configMap = relayEmailDAO.generateMultiQueryStringToObtainConfig(true, setId, emailName);
		}
		else if (elementsForMessage.containsKey("PartnerID") && !Strings.isNullOrEmpty(elementsForMessage.get("PartnerID")) && (configMap == null || configMap.isEmpty())) {
			if (elementsForMessage.containsKey("isLegacy") && StringUtils.isNotBlank(elementsForMessage.get("isLegacy"))
					&& (elementsForMessage.get("isLegacy").equalsIgnoreCase(Constants.Y))){
				System.out.println("Inside Is legacy");
				setId = Constants.ANPI;
				configMap = relayEmailDAO.generateMultiQueryStringToObtainConfig(true, setId, emailName);
			}else{
				setId = elementsForMessage.get("PartnerID");
			// queryParamForSetID = false;
				configMap = relayEmailDAO.generateMultiQueryStringToObtainConfig(false, setId, emailName);
			}
		}
		System.out.println("config-Status" + configMap.get("status") + ", conf --> " + configMap);
		logger.info("config-Status" + configMap.get("status") + ", conf --> " + configMap);
		newMessage = createMessageOnly(elementsForMessage, configMap);
		newMessage.saveChanges();
		System.out.println("Exiting createNoAttachmentMessage");
		logger.info("Exiting createNoAttachmentMessage");
		return newMessage;
	}

	/**
	 * Attaching attachments here.
	 * 
	 * @param p
	 *            the mail part
	 * @param messageWithoutAttachment
	 *            the message without attachment
	 * @return the message with attachments
	 * @throws MessagingException
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public Message createMessageWithAllAttachments(Part p, Message messageWithoutAttachment) throws MessagingException, IOException {
		System.out.println("Entering createMessageWithAllAttachments ");
		logger.info("Entering createMessageWithAllAttachments");
		Message messageWithAllAttachments = null;
		if (p.getContentType().contains("multipart")) {
			messageWithAllAttachments = new MimeMessage(emailSession);
			messageWithAllAttachments.setFrom(messageWithoutAttachment.getFrom()[0]);
			messageWithAllAttachments.setRecipients(Message.RecipientType.TO, messageWithoutAttachment.getRecipients(Message.RecipientType.TO));
			messageWithAllAttachments.setRecipients(Message.RecipientType.CC, messageWithoutAttachment.getRecipients(Message.RecipientType.CC));
			messageWithAllAttachments.setRecipients(Message.RecipientType.BCC, messageWithoutAttachment.getRecipients(Message.RecipientType.BCC));
			messageWithAllAttachments.setReplyTo(messageWithoutAttachment.getReplyTo());
			messageWithAllAttachments.setSubject(messageWithoutAttachment.getSubject());
			if (filesToBeDeleted == null || filesToBeDeleted.isEmpty()) {
				// No Attachments to be sent
			//	messageWithAllAttachments.setContent(Jsoup.parse(messageWithoutAttachment.getContent().toString()).toString(), "text/html");
//				Document doc = Jsoup.parse(messageWithoutAttachment.getContent().toString());
//				Document.OutputSettings settings = doc.outputSettings();
//				settings.prettyPrint(false);
//				settings.escapeMode(EscapeMode.extended);
//				settings.charset("ASCII");
//				String modifiedContent = doc.html();
//				System.out.println(modifiedContent);
				messageWithAllAttachments.setContent(messageWithoutAttachment.getContent(), "text/html");
			} else {
				// Attaching attachments here
				Multipart multipart = new MimeMultipart();
				// Create the message part
				MimeBodyPart messageBodyPart = new MimeBodyPart();
				// Now set the actual message
//				messageBodyPart.setContent(Jsoup.parse(messageWithoutAttachment.getContent().toString()).toString(), "text/html");
//				Document doc = Jsoup.parse(messageWithoutAttachment.getContent().toString());
//			    Document.OutputSettings settings = doc.outputSettings();
//			    settings.prettyPrint(false);
//			    settings.escapeMode(EscapeMode.extended);
//			    settings.charset("ASCII");
//			    String modifiedContent= doc.html();
//			    System.out.println(modifiedContent);
			    messageBodyPart.setContent(messageWithoutAttachment.getContent(), "text/html");
				multipart.addBodyPart(messageBodyPart);
				MimeBodyPart attachPart = null;
				for (String filename : filesToBeDeleted) {
					System.out.println("Attaching would appear based on the number of attachments");
					logger.info("Attaching would appear based on the number of attachments");
					attachPart = new MimeBodyPart();
					attachPart.attachFile(filename);
					multipart.addBodyPart(attachPart);
				}

				// Send the complete message parts
				messageWithAllAttachments.setContent(multipart);
			}
		} else {
			System.out.println("No attachments at all");
			logger.info("No attachments at all");
			messageWithAllAttachments = messageWithoutAttachment;
		}
		System.out.println("Exiting createMessageWithAllAttachments ");
		logger.info("Exiting createMessageWithAllAttachments");
		return messageWithAllAttachments;
	}

	/**
	 * saves an attachment from a MimeBodyPart to a local path.
	 * 
	 * @param p
	 *            the p
	 * @param messageWithoutAttachment
	 *            the message without attachment
	 * @return the message
	 * @throws MessagingException
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public Message createLocalFileForAttachments(Part p, Message messageWithoutAttachment) throws MessagingException, IOException {
		logger.info("Entering createLocalFileForAttachments");
		System.out.println("Entering createLocalFileForAttachments");
		Message messageWithAttachment = null;
		if (null != p.getDisposition() && p.getDisposition().equalsIgnoreCase("attachment")) {
			System.out.println("Email contains attachement");
			logger.info("Email contains attachement");
			messageWithAttachment = new MimeMessage(emailSession);
			System.out.print(messageWithoutAttachment.getFrom()[0]);
			messageWithAttachment.setFrom(messageWithoutAttachment.getFrom()[0]);
			messageWithAttachment.setRecipients(Message.RecipientType.TO, messageWithoutAttachment.getRecipients(Message.RecipientType.TO));
			messageWithAttachment.setRecipients(Message.RecipientType.CC, messageWithoutAttachment.getRecipients(Message.RecipientType.CC));
			messageWithAttachment.setReplyTo(messageWithoutAttachment.getReplyTo());
			messageWithAttachment.setSubject(messageWithoutAttachment.getSubject());
			// Creates a local file
			MimeBodyPart mimeBodyPart = (MimeBodyPart) p;
			// Creates Directory if not exists
			File file = new File(Constants.TEMP_DIR_ATTACHMENTS);
			boolean b = false;
			if (!file.exists()) {
				System.out.println("Creating directory");
				b = file.mkdirs();
				System.out.println("directory created" + file);
			}
			System.out.println("Local directory for attaching file-->" + file);
			logger.info("Local directory for attaching file->" + file);
			mimeBodyPart.saveFile(file + "/" + mimeBodyPart.getFileName().trim());
			messageWithAttachment.setContent(Jsoup.parse(messageWithoutAttachment.getContent().toString()).toString(), "text/html");
			filesToBeDeleted.add(file + "/" + mimeBodyPart.getFileName().trim());
			System.out.println("filesToBeDeleted:" + filesToBeDeleted.size());
			logger.info("filesToBeDeleted:" + filesToBeDeleted.size());
			fileNames.add(mimeBodyPart.getFileName().trim());
		} else {
			messageWithAttachment = messageWithoutAttachment;
		}
		logger.info("Exiting createLocalFileForAttachments");
		System.out.println("Exiting createLocalFileForAttachments");
		return messageWithAttachment;
	}

	/**
	 * Reads email content from mail server.
	 * 
	 * @param content
	 *            the content
	 * @return the hashmap of email
	 */
	public HashMap<String, String> identifyElements(String content) {
		HashMap<String, String> elementsForMessage = new HashMap<String, String>();
		System.out.println("Entering identifyElements contents");
		logger.info("Entering identifyElements contents");
		String usefulContent = content.split(Constants.SPLITMULTIHASH)[1].trim();
		String[] lines = usefulContent.split(Constants.SPLITHASH);
		for (String line : lines) {
			if (null != line && !("null".equalsIgnoreCase(line))) {
				line = line.replace("\n", "").replace("\r", "");
				// System.out.println("eachline => "+line);
				String[] lineArr = line.split(":", 2);
				String key = lineArr[0];
				String value = "";
				if (lineArr.length == 2) {
					value = lineArr[1];
				}
				elementsForMessage.put(key.trim(), value.trim());
				// To be followed if the datastructure is hashmap of
				// String,Arraylist of String
				// if(elementsForMessage.containsKey(key)){
				// elementsForMessage.get(key).add(line.split(":")[1]); } else {
				// ArrayList<String> valueList = new ArrayList<>();
				// valueList.add(line.split(":")[1]);
				// elementsForMessage.put(key, valueList); }
				// May be utilized for future use
				// if ("From".equals(key)) {
				// } else if ("To".equals(key)) {
				// } else if ("CC".equals(key)) {
				// } else if ("ReplyTo".equals(key)) {
				// } else if ("Subject".equals(key)) {
				// } else if ("Content".equals(key)) {
				// } else {
				// System.out.println("Unidentified Message Key");
				// }
			} else {
				// System.out.println("This is the last value of the line");
			}
		}
		System.out.println("Exiting identifyElements contents");
		logger.info("Exiting identifyElements contents ");
		return elementsForMessage;
	}

	/**
	 * This method uploads the documents in document Repository.
	 * 
	 * @param paths
	 *            the paths
	 * @param partnerId
	 *            the partner id
	 * @throws Exception
	 */
	public void uploadFiles(ArrayList<String> paths, String partnerId) throws Exception {
		System.out.println("Entering uploadFiles");
		logger.info("Entering uploadFiles");
		if (!paths.isEmpty()) {
			for (String path : paths) {
				uploadedUuids.add(UploadToDocRepo.uploadFile(path, "emails_sent?partner_id=" + partnerId));
			}
		}
		System.out.println("Exiting uploadFiles");
		logger.info("Exiting uploadFiles");
	}

	/**
	 * This method deletes the documents uploaded in temp directory.
	 * 
	 * @param paths
	 *            the paths
	 */
	public void deleteFiles(ArrayList<String> paths) {
		System.out.println("Entering deleteFiles");
		logger.info("Entering deleteFiles");
		File f = null;
		uploadedUuids = new ArrayList<String>();
		for (String path : paths) {
			f = new File(path);
			boolean isdeleted = f.delete();
			if (isdeleted) {
				System.out.println("The generated attachement is deleted.");
				logger.info("The generated attachement is deleted.");
			} else {
				System.out.println("The genereated attachement failed to get deleted.");
				logger.info("The genereated attachement failed to get deleted.");
			}
		}
		System.out.println("Exiting deleteFiles");
		logger.info("Exiting deleteFiles");
	}

	/**
	 * This method checks for content-type based on which, it processes and
	 * fetches the content of the message.
	 * 
	 * @param p
	 *            the mail part
	 * @throws Exception
	 */
	public void writePart(Part p) throws Exception {
		if (p instanceof Message)
		// Call method writeEnvelope
		{
			WriteMailPart.writeEnvelope((Message) p);
		}
		System.out.println("CONTENT-TYPE: " + p.getContentType());
		System.out.println("---------------------------");
		logger.info("CONTENT-TYPE: " + p.getContentType());
		// check if the content is plain text
		if (p.isMimeType("text/plain")) {
			System.out.println("Plain text Email");
			logger.info("Plain text Email");
			String content = (String) p.getContent();
			if (null == p.getDisposition() || ((null != p.getDisposition()) && (!p.getDisposition().equalsIgnoreCase("attachment")))) {
				elementsForMessage = identifyElements(content);
				messageWithoutAttachment = createNoAttachmentMessage(elementsForMessage);
			}
			messageToBeSent = createLocalFileForAttachments(p, messageWithoutAttachment);
			System.out.println("messageTobeSent :" + messageToBeSent);
		}
		// check if the content has attachment
		else if (p.isMimeType("multipart/*")) {
			System.out.println("This is a Multipart");
			logger.info("This is a Multipart");
			Multipart mp = (Multipart) p.getContent();
			int count = mp.getCount();
			System.out.println("multipart count ==>" + count);
			for (int i = 0; i < count; i++) {
				writePart(mp.getBodyPart(i));
			}
		} // check if the content is a nested message
		else if (p.isMimeType("message/rfc822")) {
			System.out.println("This is a Nested Message");
			logger.info("This is a Nested Message");
			writePart((Part) p.getContent());
		} // check if the content is an inline image
		else if (p.getContentType().contains("image/")) {
			
			 if (Part.ATTACHMENT.equalsIgnoreCase(p.getDisposition())) {
                 // this part is attachment
                 String fileName = p.getFileName();
                 MimeBodyPart mimeBodyPart = (MimeBodyPart) p;
                 mimeBodyPart.saveFile(fileName);
                 filesToBeDeleted.add(fileName);
             }
		}
        else
        {
			Object o = p.getContent();
			if (o instanceof String) {
				System.out.println("This is a string");
				System.out.println("---------------------------");
				logger.info("---------------------------");
				if (null == p.getDisposition() || ((null != p.getDisposition()) && (!p.getDisposition().equalsIgnoreCase("attachment")))) {
					elementsForMessage = identifyElements(o.toString());
					messageWithoutAttachment = createNoAttachmentMessage(elementsForMessage);
				}
				messageToBeSent = createLocalFileForAttachments(p, messageWithoutAttachment);
			} else if (o instanceof InputStream) {
				System.out.println("This is just an input stream");
				// System.out.println("---------------------------");
				InputStream is = (InputStream) o;
				is = (InputStream) o;
				int c;
				while ((c = is.read()) != -1) {
					// System.out.write(c);
//					logger.info("Reads input Stream");
				}
				if (null == p.getDisposition() || ((null != p.getDisposition()) && (!p.getDisposition().equalsIgnoreCase("attachment")))) {
					elementsForMessage = identifyElements(new Util().getStringFromInputStream(is));
					messageWithoutAttachment = createNoAttachmentMessage(elementsForMessage);
				}
				messageToBeSent = createLocalFileForAttachments(p, messageWithoutAttachment);

			} else {
				System.out.println("This is an unknown type");
				System.out.println("---------------------------");
				logger.info("This is an unknown type");
				logger.info("---------------------------");
				logger.info(o.toString());
			}
		}
	}
	
	public Message mailFailure(String account_manager) throws Exception{
		HashMap<String,String> userMap = (HashMap<String, String>) URLReaderUtil.getInputFromUrl(Constants.PARTNER_SMTP_DETAILS+"users/"+account_manager);
		Message statusMessage = new MimeMessage(emailSession);
		statusMessage.setFrom(new InternetAddress("donotreply@anpi.com"));
		System.out.println("EmaiId :"+ userMap.get("email_id"));
		statusMessage.setRecipient(Message.RecipientType.TO,new InternetAddress(userMap.get("email_id")));
		if(messageToBeSent.getContent() instanceof Multipart){
			System.out.println("Multipart Message");
			statusMessage.setContent((Multipart) messageToBeSent.getContent());
		}else{
			statusMessage.setContent(messageToBeSent.getContent(),"text/html");
		}
		statusMessage.setSubject("Mail delivery failure - SMTP server auth error");
		System.out.println("getContentType:"+ messageToBeSent.getContentType());
		return statusMessage;
	}
	
	/**
	 * This method sends email to all receipients.
	 * 
	 * @param message
	 *            the message
	 * @throws Exception 
	 */
	public void sendMail(Message message) throws Exception {
		String partnerId = configMap.get("partner_id");
		System.out.println("partnerId:"+partnerId);
		Transport t = null;
		if(configMap.containsKey("smtp_applicable") && !Strings.isNullOrEmpty(configMap.get("smtp_applicable")) && configMap.get("smtp_applicable").equals("1") 
				&& !Strings.isNullOrEmpty(configMap.get("smtp_server")) && !Strings.isNullOrEmpty(configMap.get("port")) 
				&& !Strings.isNullOrEmpty(configMap.get("user_name")) && !Strings.isNullOrEmpty(configMap.get("password"))){
			Properties props = new Properties();
			props.put("mail.smtp.auth", "true");
	        props.put("mail.smtp.host", configMap.get("smtp_server").trim());
	        props.put("mail.smtp.port",configMap.get("port"));
	        try{
	        Session session = Session.getInstance(props,
	                new javax.mail.Authenticator() {
	            protected PasswordAuthentication getPasswordAuthentication() {
	                return new PasswordAuthentication(configMap.get("user_name"),configMap.get("password").trim());
	            }
	        });
		         session.setDebug(true);
				 t = session.getTransport("smtp");
				 t.connect();
		        }catch(Exception e){
		        	e.printStackTrace();
		        	System.out.println("Exception -->"+e.getMessage());
		        }
				 if(!t.isConnected()){
						t= emailSession.getTransport("smtp");
						t.connect();
						Message mailFailureMessage = mailFailure(configMap.get("account_manager"));
						t.sendMessage(mailFailureMessage, mailFailureMessage.getAllRecipients());
				}
			}
			else{
				t= emailSession.getTransport("smtp");
				t.connect();
			}
			 t.sendMessage(message, message.getAllRecipients());
			 t.close();
	}

	/**
	 * This method checks for content-type based on which, it processes and
	 * fetches the content of the message.
	 * 
	 * @param args
	 *            the arguments
	 */
	public static void main(String[] args) {
		EmailCredits emailCredits = new EmailCredits();
		emailCredits.setUsername("relay");
		emailCredits.setPassword(Constants.RELAY_PASSWORD);
		emailCredits.setPopHost(Constants.RELAY_HOST);
		RelayEmail relayEmail = new RelayEmail();
		relayEmail.fetch(emailCredits);

	}

}