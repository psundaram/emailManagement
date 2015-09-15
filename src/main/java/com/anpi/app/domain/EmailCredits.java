/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.anpi.app.domain;

/**
 * Represents a EmailCredits, providing access to username,password,smtp,imap and pop.
 */
public class EmailCredits {
    
    private String username;
    private String password;
    private String smtpHost;
    private String smtpPort;
    private String popHost;
    private String popPort;
    private String imapHost;
    private String imapPort;

    /**
     * Gets the username.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the username.
     *
     * @param username the new username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Gets the password.
     *
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password.
     *
     * @param password the new password
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Gets the smtp host.
     *
     * @return the smtp host
     */
    public String getSmtpHost() {
        return smtpHost;
    }

    /**
     * Sets the smtp host.
     *
     * @param smtpHost the new smtp host
     */
    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    /**
     * Gets the smtp port.
     *
     * @return the smtp port
     */
    public String getSmtpPort() {
        return smtpPort;
    }

    /**
     * Sets the smtp port.
     *
     * @param smtpPort the new smtp port
     */
    public void setSmtpPort(String smtpPort) {
        this.smtpPort = smtpPort;
    }

    /**
     * Gets the pop host.
     *
     * @return the pop host
     */
    public String getPopHost() {
        return popHost;
    }

    /**
     * Sets the pop host.
     *
     * @param popHost the new pop host
     */
    public void setPopHost(String popHost) {
        this.popHost = popHost;
    }

    /**
     * Gets the pop port.
     *
     * @return the pop port
     */
    public String getPopPort() {
        return popPort;
    }

    /**
     * Sets the pop port.
     *
     * @param popPort the new pop port
     */
    public void setPopPort(String popPort) {
        this.popPort = popPort;
    }

    /**
     * Gets the imap host.
     *
     * @return the imap host
     */
    public String getImapHost() {
        return imapHost;
    }

    /**
     * Sets the imap host.
     *
     * @param imapHost the new imap host
     */
    public void setImapHost(String imapHost) {
        this.imapHost = imapHost;
    }

    /**
     * Gets the imap port.
     *
     * @return the imap port
     */
    public String getImapPort() {
        return imapPort;
    }

    /**
     * Sets the imap port.
     *
     * @param imapPort the new imap port
     */
    public void setImapPort(String imapPort) {
        this.imapPort = imapPort;
    }
    
}
