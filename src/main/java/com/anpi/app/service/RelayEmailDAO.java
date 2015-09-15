package com.anpi.app.service;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import net.sf.json.JSONException;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Service;

import com.anpi.app.constants.Constants;
import com.anpi.app.domain.TagMapDTO;
import com.anpi.app.util.DbConnect;
import com.anpi.app.util.EmailUtil;
import com.anpi.app.util.URLReaderUtil;
import com.google.common.base.Strings;


/**
 * Provides relayEmail implementation for generating content,insert in email logs,update logger table and generate query
 */
@Service
public class RelayEmailDAO {

	/** Logging for RelayEmailDAO */
	private static final Logger logger = Logger.getLogger(RelayEmailDAO.class);

	
	/**
	 * Tag value in content and subject are replaced with appropriate value from
	 * API call, Tag map or relay email.
	 * @param elementsForMessage the elements for message
	 * @param configMap the config map
	 * @return the string of final subject and content
	 * @throws SQLException 
	 * @throws JSONException 
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public String generateActualContent(HashMap<String, String> elementsForMessage, HashMap<String, String> configMap) throws SQLException, JSONException, IOException {
		System.out.println("Entering generateActualContent");
		logger.info("Entering generateActualContent");
		String content = null;
		String subject = null;
		String query = null;
		List<String> tagsUsedList = null;
		HashMap<String, String> apiMap = new HashMap<String,String>();
//		HashMap<String,String> legacyMap = new HashMap<String,String>();
		if (!configMap.isEmpty()) {
			subject = StringEscapeUtils.unescapeHtml(configMap.get("subject"));
			content = StringEscapeUtils.unescapeHtml(configMap.get("content_template"));
			logger.info("content" + content);
			query = "select tag_name, tag_value, source from email_tags where email_config_id = '" + configMap.get("id") + "'";
		}
		// Call to get TagMap values from EMAIL_LOGS table
		HashMap<String, TagMapDTO> tagMap = new DbConnect().getTags(query);
		System.out.println("tagMap -> " + tagMap);
		logger.info("tagMap -> " + tagMap);
		//CALL TO API MAP
		// If partner is published, get branding parameters 
		if(configMap.containsKey("is_published") && !Strings.isNullOrEmpty(configMap.get("is_published")) && configMap.get("is_published").equals("1")){
			apiMap =  (HashMap<String, String>) URLReaderUtil.getInputFromUrl(Constants.API_CALL_URL+"partner_id="+configMap.get("partner_id"));
		}
		if (elementsForMessage.containsKey("TAGS USED") && StringUtils.isNotBlank(elementsForMessage.get("TAGS USED"))) {
			tagsUsedList = new ArrayList<String>(Arrays.asList(elementsForMessage.get("TAGS USED").split(",")));
		}
		if (configMap.containsKey("signature") && (null != configMap.get("signature")) && (!configMap.get("signature").isEmpty()) && (!(configMap.get("signature").trim().equals("")))
				&& (!(configMap.get("signature").trim().contains("generated")))) {
			String signature = StringEscapeUtils.unescapeHtml(configMap.get("signature"));
			Pattern p = Pattern.compile("$");
			Matcher matcher = p.matcher(signature);
			while (matcher.find()) {
				signature = signature.replaceAll("\\$", "\\\\\\$");
			}
			for (String tagName : tagMap.keySet()) {
				if(signature.contains("<<." +tagName + ">>") && apiMap.containsKey(tagName) && !Strings.isNullOrEmpty(apiMap.get(tagName))){
					String replacedValue;
					if (tagName.contains("PHONE")) {
						String phoneNumber = EmailUtil.formatNumber(apiMap.get(tagName),Constants.NEWLINE);
						replacedValue = phoneNumber;
					} else {
						replacedValue = apiMap.get(tagName);
					}
					signature = signature.replaceAll("<<." + tagName + ">>", replacedValue);
				}
			}
			content = content.replaceAll("<<.SIGNATURE CONTENT>>", signature);
		} else {
			content = content.replaceAll("<<.SIGNATURE CONTENT>>", "");
		}
		for (String tag : tagMap.keySet()) {
			do {
				for (String tagName : tagMap.keySet()) {
					if (content.contains("<<." + tagName + ">>") || subject.contains("<<." + tagName + ">>")) {
						TagMapDTO tagMapDTO = tagMap.get(tagName);
						System.out.println("tagName:" + tagName);
						logger.info("tagName:" + tagName);
						String replacedValue = "";
						// Tags used condition
						if (tagsUsedList != null && tagsUsedList.size() > 0) {
							if (!tagsUsedList.contains(tagName)) {
								content = content.replaceAll("<<." + tagName + ">>", "");
								subject = subject.replaceAll("<<." + tagName + ">>", "");
								System.out.println(tagName + "tagName not found");
							}
						}
						// CONDITION TO HANDLE API KEY
						if (apiMap.containsKey(tagName) && (null != apiMap.get(tagName)) && (!("null".equalsIgnoreCase(apiMap.get(tagName)))) && (!("".equalsIgnoreCase(apiMap.get(tagName).trim())))) {
							System.out.println("Condition to handle api key" + apiMap.get(tagName));
							if (tagName.contains("PHONE")) {
								String phoneNumber = EmailUtil.formatNumber(apiMap.get(tagName),Constants.COMMA);
								replacedValue = phoneNumber;
							} else {
								replacedValue = apiMap.get(tagName);
							}
						}
						// CONDITION TO HANDLE DB TAGS
						else if (tagMap.containsKey(tagName) && tagMapDTO.getSource()==2) {
								//(null != dbTags.get(tagName)) && content.contains("<<." + tagName + ">>")) {
							// Retrieve partner product information for the actual partner Id
							String dbTagQuery = "select product_type from products where netx_id='" + tagName + "' and   partner_id ='" + configMap.get("actual_partner_id") + "'";
							System.out.println("dbTagQuery:" + dbTagQuery);
							HashMap<String, String> map = new DbConnect().getConfigsFromSingleQuery(dbTagQuery);
							System.out.println("map" + map);
							if(map!=null && !map.isEmpty())
							replacedValue = map.get("product_type");
						} 
						
						// CONDITION TO HANDLE EMAIL_TAGS TABLE
						else if (tagMap.containsKey(tagName) && (null != tagMapDTO.getTagValue())  && (!("null".equalsIgnoreCase(tagMapDTO.getTagValue())))
								&& (!("".equalsIgnoreCase(tagMapDTO.getTagValue().trim())))) {
							String temp =  StringEscapeUtils.unescapeHtml(tagMapDTO.getTagValue());
							Pattern p = Pattern.compile("$");
							Matcher matcher = p.matcher(temp);
							while (matcher.find()) {
								temp = temp.replaceAll("\\$", "\\\\\\$");
							}
							replacedValue = temp;
						} // CONDITION TO HANDLE RELAY EMAIL
						else if ((elementsForMessage.containsKey(tagName) && (null != elementsForMessage.get(tagName)) && (!("null".equalsIgnoreCase(elementsForMessage.get(tagName)))) && (!(""
								.equalsIgnoreCase(elementsForMessage.get(tagName).trim()))))) {
							String temp = StringEscapeUtils.unescapeHtml(elementsForMessage.get(tagName));
							Pattern p = Pattern.compile("$");
							Matcher matcher = p.matcher(temp);
							while (matcher.find()) {
								temp = temp.replaceAll("\\$", "\\\\\\$");
							}
							replacedValue = temp;
						}	// CONDITION TO REPLACE WITH VFTTND
						 else {
							System.out.println("tagName- VFTDND:" + tagName);
							replacedValue = Constants.VFTDND;
						}
						System.out.println(tagName +" replaced by " + replacedValue);
						
						logger.info(tagName +" replaced by " + replacedValue);
						
						subject = subject.replaceAll("<<." + tagName + ">>", replacedValue);
						content = content.replaceAll("<<." + tagName + ">>", replacedValue);
					}
				}
			} while (content.contains("<<." + tag + ">>") || subject.contains("<<." + tag + ">>"));
		}
		System.out.println("Exiting generateActualContent-->" + content);
		logger.info("Exiting generateActualContent -->" + content);
		return subject + "##" + content;
	}

	
	/**
	 * Query email_configs_vw based on email_name and setId or partnerId.
	 * @param queryParamForSetID the query param for set id
	 * @param setId the set id
	 * @param emailName the email name
	 * @return the hash map
	 * @throws Exception 
	 */
	
	public HashMap<String, String> generateMultiQueryStringToObtainConfig(boolean queryParamForSetID, String setId, String emailName) throws Exception {
		logger.info("Entering generateMultiQueryStringToObtainConfig --> SetId,emailName " + setId + " , " + emailName);
		
		HashMap<String, String>	configMap	= new HashMap<String, String>();
		String					q1			= null;
		String					q2			= null;
		String					partnerType	= null;
		HashMap<String, String>	partnerMap	= new HashMap<String, String>();

		// Retrieve partner information from partner API (published status, parent_partner_id,etc..)
		if(queryParamForSetID)
			partnerMap = URLReaderUtil.getJsonFromUrl(Constants.PARTNER_SMTP_DETAILS+"partners?partner_id="+setId);
		else
			partnerMap = URLReaderUtil.getJsonFromUrl(Constants.PARTNER_SMTP_DETAILS+"partners?id="+setId);
		
		if(partnerMap!=null && !partnerMap.isEmpty()){
			
			String partnerId = null;
			// If channel partner, apply configuration of parent partner
			if(partnerMap.containsKey("partner_type") && !Strings.isNullOrEmpty(partnerMap.get("partner_type")) && (partnerMap.get("partner_type").equals("2")) && partnerMap.containsKey("parent_partner_id") && !Strings.isNullOrEmpty(partnerMap.get("parent_partner_id"))){
				partnerType = partnerMap.get("partner_type");
				logger.info("Inside channel Partner");
				System.out.println("Inside channel partner");
				partnerId = partnerMap.get("parent_partner_id").toString();
				System.out.println("channel partnerMAp " + partnerMap);
				partnerMap = URLReaderUtil.getJsonFromUrl(Constants.PARTNER_SMTP_DETAILS+"partners?id="+partnerId);
				System.out.println("Parent partner details : " + partnerMap);
				// If partner is non-published, generic config SetID = "*****"
				if(partnerMap.containsKey("is_published") && (Strings.isNullOrEmpty(partnerMap.get("is_published")) || partnerMap.get("is_published").equals("0"))){
					String setID = Constants.DUMMY_PARTNER_ID;
					q1 = "select * from email_configs_vw where setId='" + setID + "' and email_name='" + emailName + "'  AND customer_id=-1";
					q2 = "select cc_email,reply_to_email from email_configs_opt_vw where setId='" + setID + "' AND email_name='" + emailName + "'  AND customer_id=-1";
				}else{
					q1 = "select * from email_configs_vw where partner_id='" + partnerId + "' and email_name='" + emailName + "'  AND customer_id=-1";
					q2 = "select cc_email,reply_to_email from email_configs_opt_vw where partner_id='" + partnerId + "' AND email_name='" + emailName + "'  AND customer_id=-1";
				}
			}// If WS partner is non-published, generic config for setId as "*****"
			else if(partnerMap.containsKey("is_published") && (Strings.isNullOrEmpty(partnerMap.get("is_published")) || partnerMap.get("is_published").equals("0"))){
				String setID = Constants.DUMMY_PARTNER_ID;
				partnerId = partnerMap.get("id").toString();
				q1 = "select * from email_configs_vw where setId='" + setID + "' and email_name='" + emailName + "'  AND customer_id=-1";
				q2 = "select cc_email,reply_to_email from email_configs_opt_vw where setId='" + setID + "' AND email_name='" + emailName + "'  AND customer_id=-1";
			}
			else{
				partnerId = partnerMap.get("id").toString();
				q1 = "select * from email_configs_vw where partner_id='" + partnerId + "' and email_name='" + emailName + "'  AND customer_id=-1";
				q2 = "select cc_email,reply_to_email from email_configs_opt_vw where partner_id='" + partnerId + "' AND email_name='" + emailName + "'  AND customer_id=-1";
			}
			String query = q1 + "###" + q2;
			configMap = new DbConnect().getConfigsFromMultipleQuery(query.split("###"));
			if(configMap!=null && !configMap.isEmpty()){
				configMap.put("is_published",partnerMap.get("is_published"));
				// Setting partner smtp information
				configMap.put("smtp_applicable", partnerMap.get("smtp_applicable"));
				configMap.put("smtp_server", partnerMap.get("smtp_server"));
				configMap.put("user_name", partnerMap.get("user_name"));
				configMap.put("password", partnerMap.get("password"));
				configMap.put("port", partnerMap.get("port"));
				configMap.put("account_manager", partnerMap.get("account_manager"));
				configMap.put("actual_partner_id",partnerId);
				configMap.put("partner_type", partnerType);
			}
		}
		
		System.out.println("Exiting generateMultiQueryStringToObtainConfig"  + configMap);
		logger.info("Exiting generateMultiQueryStringToObtainConfig");
		return configMap;
	}


	/**
	 * Insert into email_logs.
	 * @param parameterMap the hashmap of key,values to be inserted in email_logs
	 * @return the id 
	 */
	public int insert(HashMap<String, String> parameterMap) {
		logger.info("Entering insert");
		
		String	columns	= "";
		String	val		= "";
		
		for (String key : parameterMap.keySet()) {
			columns 	+= "`" + key + "`" + ",";
			val 		+= "'" + parameterMap.get(key) + "'" + ",";
		}
		
		columns 		= columns.substring(0, columns.lastIndexOf(","));
		val 			= val.substring(0, val.lastIndexOf(","));
		
		String sql = "INSERT INTO email_logs (" + columns + ") VALUES (" + val + ");";
		
		logger.info("Exiting insert relayDAO==>" + sql);
		return new DbConnect().putLogs(sql);
	}

	
	/**
	 * Updates sending status from created to sent.
	 * @param id the id
	 */
	public void updateLogger(int id) {
		
		String sql = "UPDATE email_logs SET sending_status='Sent' WHERE id=" + id;
		new DbConnect().updateLogs(sql);
	
	}
	

	/**
	 * Increments resend count in email_logs.
	 * @param id the id
	 * @param count the count of resend triggered for the id 
	 */
	public void updateLogsForResend(int id, int count) {
		logger.info("Entering updateLogsForResend==>" + count);
		
		int actualResendCount = count + 1;
		String sql = "UPDATE email_logs SET resend_count=" + actualResendCount + " WHERE id=" + id;
		
		logger.info("Exiting updateLogsForResend");
		new DbConnect().updateResendCount(sql);
	}

	
	/**
	 * Query to fetch email contents for resend.
	 */
	public Map<String, String> generateQueryForResend(String pid) throws SQLException {
		logger.info("Entering generateQueryForResend==>" + pid);
		
		String query = "select * from email_logs where id='" + pid + "'";
		
		logger.info("Exiting generateQueryForResend");
		return new DbConnect().getConfigsFromSingleQuery(query);
	}

	
	public HashMap<String, String> getConfigForOrderId(String orderId, String emailName)
			throws SQLException {
		logger.info("Entering generateMultiQueryStringToObtainConfig --> orderId,emailName "
				+ orderId + " , " + emailName);
		
		String q1 = "select * from email_configs_vw where order_id='" + orderId
				+ "' AND email_name='" + emailName + "'";
		
		String q2 = "select cc_email,reply_to_email from email_configs_opt_vw where order_id='"
				+ orderId + "' AND email_name='" + emailName + "'";
		
		String query = q1 + "###" + q2;
		
		logger.info("Exiting generateMultiQueryStringToObtainConfig");
		
		HashMap<String, String> result = new DbConnect().getConfigsFromMultipleQuery(query
				.split("###"));
		return result;
	}

	
	public HashMap<String, String> getConfigForCustomerId(String customerId, String emailName)
			throws SQLException {
		logger.info("Entering generateMultiQueryStringToObtainConfig --> customerId,emailName "
				+ customerId + " , " + emailName);
		
		String q1 = "select * from email_configs_vw where customer_id='" + customerId
				+ "' AND email_name='" + emailName + "'  AND order_id=-1";
		
		String q2 = "select cc_email,reply_to_email from email_configs_opt_vw where customer_id='"
				+ customerId + "' AND email_name='" + emailName + "' AND order_id=-1";
		
		String query = q1 + "###" + q2;
		
		logger.info("Exiting generateMultiQueryStringToObtainConfig");
		
		HashMap<String, String> result = new DbConnect().getConfigsFromMultipleQuery(query
				.split("###"));
		return result;
	}
}
