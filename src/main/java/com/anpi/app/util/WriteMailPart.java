package com.anpi.app.util;

import javax.mail.Address;
import javax.mail.Message;

/**
 * Print from,to and subject of the email 
 */
public class WriteMailPart {

    
    /**
      * This method would print FROM,TO and SUBJECT of the message
      *
      * @param m the Message object
      * @throws Exception the exception
      */
     public static void writeEnvelope(Message m) throws Exception {
        System.out.println("This is the message envelope");
        System.out.println("---------------------------");
        Address[] a;
        
        // FROM
        if ((a = m.getFrom()) != null) {
            for (int j = 0; j < a.length; j++) {
                System.out.println("FROM: " + a[j].toString());
            }
        }

        // TO
        if ((a = m.getRecipients(Message.RecipientType.TO)) != null) {
            for (int j = 0; j < a.length; j++) {
                System.out.println("TO: " + a[j].toString());
            }
        }

        // SUBJECT
        if (m.getSubject() != null) {
            System.out.println("SUBJECT: " + m.getSubject());
        }

    }
    

}
