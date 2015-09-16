package com.anpi.app.dao;

import java.sql.SQLException;
import java.util.Map;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.anpi.app.domain.TagMapDTO;
import com.anpi.app.util.DbConnect;

@Component
public class ReadEmailDAO {
	
	private static final Logger	logger			= Logger.getLogger(ReadEmailDAO.class);
	
	@Autowired
	DbConnect dbConnect; 
	
	public Map<String, String> getConfigForSetId(String emailName, String setID)
			throws SQLException {
		
		String query1 = "select * from email_configs_vw where setId='" + setID
				+ "' and email_name='" + emailName + "'  AND customer_id=-1";
		String query2 = "select cc_email,reply_to_email from email_configs_opt_vw where setId='"
				+ setID + "' AND email_name='" + emailName + "'  AND customer_id=-1";
		String query = query1 + "###" + query2;
		
		return dbConnect.getConfigsFromMultipleQuery(query.split("###"));
	}

	
	/** Get configuration for given orderId and emailName */
	public Map<String, String> getConfigForOrderId(String orderId, String emailName)
			throws SQLException {
		
		String query1 = "select * from email_configs_vw where order_id='" + orderId
				+ "' AND email_name='" + emailName + "'";
		String query2 = "select cc_email,reply_to_email from email_configs_opt_vw where order_id='"
				+ orderId + "' AND email_name='" + emailName + "'";
		
		String query = query1 + "###" + query2;
		
		return dbConnect.getConfigsFromMultipleQuery(query.split("###"));
	}

	
	/** Get configuration for given customerId and emailName */
	public Map<String, String> getConfigForCustomerId(String customerId, String emailName)
			throws SQLException {
		
		String query1 = "select * from email_configs_vw where customer_id='" + customerId
				+ "' AND email_name='" + emailName + "'  AND order_id=-1";
		String query2 = "select cc_email,reply_to_email from email_configs_opt_vw where customer_id='"
				+ customerId + "' AND email_name='" + emailName + "' AND order_id=-1";
		
		String query = query1 + "###" + query2;
		
		return dbConnect.getConfigsFromMultipleQuery(query.split("###"));
	}
	
	
	public Map<String, TagMapDTO> getTagsList(String configId) throws SQLException {
		
		String query = "select tag_name, tag_value, source from email_tags where email_config_id = '" + configId + "'";
		
		return dbConnect.getTags(query);
	}


	public int insetIntoDb(String columns, String val) {
		String sql = "INSERT INTO email_logs (" + columns + ") VALUES (" + val + ");";
		
		logger.info("Exiting insert relayDAO==>" + sql);
		return dbConnect.putLogs(sql);
	}
	
	
	/**
	 * Updates sending status from created to sent.
	 * @param id the id
	 */
	public void updateLogger(int emailId) {
		
		String sql = "UPDATE email_logs SET sending_status='Sent' WHERE id=" + emailId;
		dbConnect.updateLogs(sql);
	
	}
	
	
}
