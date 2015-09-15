package com.anpi.app.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.anpi.app.constants.Constants;
import com.anpi.app.domain.EmailCredits;
import com.anpi.app.service.RelayEmail;

@Component
public class App {
	
	@Autowired
	RelayEmail relayEmail;
	
	
	@Autowired
	ReadEmail readEmail;
	
	
	public static void main(String[] args) {
		ApplicationContext context = new ClassPathXmlApplicationContext("applicationContext.xml");
		App p = context.getBean(App.class);
		p.start(args);
	}

	private void start(String[] args) {
		runCron();
	}
	
//	@Async
//	@Scheduled(initialDelay = 10000, fixedRate=30000)
	private void runCron(){
		EmailCredits emailCredits = new EmailCredits();
		emailCredits.setUsername("relay");
		emailCredits.setPassword(Constants.RELAY_PASSWORD);
		emailCredits.setPopHost(Constants.RELAY_HOST);
		readEmail.fetch(emailCredits);
	}
}
