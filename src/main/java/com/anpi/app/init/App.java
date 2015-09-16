package com.anpi.app.init;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.anpi.app.constants.Constants;
import com.anpi.app.controller.ReadEmail;
import com.anpi.app.domain.EmailCredits;

@Component
public class App {
	
	@Autowired
	ReadEmail readEmail;
	
	
	public static void main(String[] args) {
		ApplicationContext context = new ClassPathXmlApplicationContext("applicationContext.xml");
		App app = context.getBean(App.class);
		app.run(args);
	}

	private void run(String[] args) {
		runCron();
	}
	
//	@Async
	@Scheduled(cron="0 0/5 * * * ?")
	private void runCron(){
		System.out.println("Date :" + new Date());
		EmailCredits emailCredits = new EmailCredits();
		emailCredits.setUsername("relay");
		emailCredits.setPassword(Constants.RELAY_PASSWORD);
		emailCredits.setPopHost(Constants.RELAY_HOST);
		readEmail.fetch(emailCredits);
		System.out.println("*************************************");
	}
}
