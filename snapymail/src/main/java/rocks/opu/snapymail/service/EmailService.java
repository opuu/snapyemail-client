package rocks.opu.snapymail.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

@Service
public class EmailService {

    @Value("${spring.mail.imap.host}")
    private String imapHost;

    @Value("${spring.mail.imap.port}")
    private int imapPort;

    @Value("${spring.mail.imap.username}")
    private String imapUsername;

    @Value("${spring.mail.imap.password}")
    private String imapPassword;

    @Value("${spring.mail.smtp.host}")
    private String smtpHost;

    @Value("${spring.mail.smtp.port}")
    private int smtpPort;

    @Value("${spring.mail.smtp.username}")
    private String smtpUsername;

    @Value("${spring.mail.smtp.password}")
    private String smtpPassword;

    /**
     * Sends an email with the specified recipient, subject, and body.
     *
     * @param to      The recipient of the email.
     * @param subject The subject of the email.
     * @param body    The body of the email.
     * @throws Exception If an error occurs while sending the email.
     */
    public void sendEmail(String to, String subject, String body) throws Exception {
        String host = smtpHost;
        String username = smtpUsername;
        String password = smtpPassword;

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", smtpPort);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(username));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject);
        message.setContent(body, "text/html");

        Transport.send(message);

        System.out.println("Email sent successfully");
    }

    public List<Message> receiveEmails(String folder) throws Exception {
        String host = imapHost;
        String username = imapUsername;
        String password = imapPassword;

        Properties props = new Properties();
        props.setProperty("mail.imap.ssl.enable", "true");

        Session session = jakarta.mail.Session.getInstance(props);
        Store store = session.getStore("imap");
        store.connect(host, username, password);

        if (folder == null) {
            folder = "INBOX";
        }

        Folder inbox = store.getFolder(folder);

        if (!inbox.exists()) {
            System.out.println("Folder not found");
            return new ArrayList<>();
        }

        inbox.open(Folder.READ_ONLY);
        Message[] messages = inbox.getMessages();

        List<Message> emails = new ArrayList<>();

        for (Message message : messages) {
            emails.add(message);
        }

        return emails;
    }

    public String getFolders() throws Exception {
        String host = imapHost;
        String username = imapUsername;
        String password = imapPassword;

        Properties props = new Properties();
        props.setProperty("mail.imap.ssl.enable", "true");

        Session session = Session.getInstance(props);
        Store store = session.getStore("imap");
        store.connect(host, username, password);

        Folder[] folders = store.getDefaultFolder().list("*");

        StringBuilder result = new StringBuilder();
        for (Folder folder : folders) {
            result.append(folder.getName()).append("\n");
        }

        store.close();

        return result.toString();
    }

    /**
     * Reads an email by its message number and converts it to a JSON string.
     *
     * @param messageNumber The message number of the email to read.
     * @return The email content as a JSON string.
     * @throws Exception If an error occurs while reading the email.
     */
    public String readEmail(String messageNumber) throws Exception {
        System.out.println("Reading email with message number: " + messageNumber);
        // IMAP server connection properties
        Properties props = new Properties();
        props.setProperty("mail.imap.ssl.enable", "true");

        // Create a mail session
        Session session = Session.getInstance(props);
        Store store = session.getStore("imap");
        store.connect(imapHost, imapUsername, imapPassword);

        // Open the inbox folder
        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_ONLY);

        // Get the message by its number
        Message message = inbox.getMessage(Integer.parseInt(messageNumber));

        // Convert the email content to a JSON object
        JSONObject json = new JSONObject();
        json.put("subject", message.getSubject());
        json.put("from", Arrays.toString(message.getFrom()));
        json.put("sentDate", message.getSentDate().toString());
        json.put("content", getTextFromMessage(message));

        // Close the folder and store
        inbox.close(false);
        store.close();

        return json.toString();
    }

    /**
     * Extracts the text content from a message.
     *
     * @param message The email message.
     * @return The text content of the email.
     * @throws Exception If an error occurs while extracting the text.
     */
    private String getTextFromMessage(Message message) throws Exception {
        if (message.isMimeType("text/plain")) {
            return message.getContent().toString();
        } else if (message.isMimeType("multipart/*")) {
            MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
            return getTextFromMimeMultipart(mimeMultipart);
        }
        return "";
    }

    /**
     * Extracts the text content from a multipart message.
     *
     * @param mimeMultipart The multipart message.
     * @return The text content of the multipart message.
     * @throws Exception If an error occurs while extracting the text.
     */
    private String getTextFromMimeMultipart(MimeMultipart mimeMultipart) throws Exception {
        StringBuilder result = new StringBuilder();
        int count = mimeMultipart.getCount();
        for (int i = 0; i < count; i++) {
            BodyPart bodyPart = mimeMultipart.getBodyPart(i);
            if (bodyPart.isMimeType("text/plain")) {
                result.append(bodyPart.getContent().toString());
            } else if (bodyPart.isMimeType("text/html")) {
                // Prefer plain text over HTML content
                String html = (String) bodyPart.getContent();
                result.append(html);
            } else if (bodyPart.getContent() instanceof MimeMultipart) {
                result.append(getTextFromMimeMultipart((MimeMultipart) bodyPart.getContent()));
            }
        }
        return result.toString();
    }

    /**
     * Forwards an email by its message number to the specified recipient.
     *
     * @param messageId The message number of the email to forward.
     * @param to        The recipient of the forwarded email.
     * @throws Exception If an error occurs while forwarding the email.
     */
    public void forwardEmail(String messageId, String to) throws Exception {
        // IMAP settings
        String imapHost = this.imapHost;
        String imapUsername = this.imapUsername;
        String imapPassword = this.imapPassword;

        // SMTP settings
        String smtpHost = this.smtpHost;
        String smtpUsername = this.smtpUsername;
        String smtpPassword = this.smtpPassword;

        // Retrieve original email using IMAP
        Properties imapProps = new Properties();
        imapProps.setProperty("mail.imap.ssl.enable", "true");
        Session imapSession = Session.getInstance(imapProps);
        Store store = imapSession.getStore("imap");
        store.connect(imapHost, imapUsername, imapPassword);

        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_ONLY);
        Message originalMessage = inbox.getMessage(Integer.parseInt(messageId));

        // forward the email using SMTP
        Properties smtpProps = new Properties();
        smtpProps.put("mail.smtp.auth", "true");
        smtpProps.put("mail.smtp.ssl.enable", "true");
        smtpProps.put("mail.smtp.host", smtpHost);
        smtpProps.put("mail.smtp.port", smtpPort);

        Session smtpSession = Session.getInstance(smtpProps, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUsername, smtpPassword);
            }
        });

        Message forwardMessage = new MimeMessage(smtpSession);
        forwardMessage.setFrom(new InternetAddress(smtpUsername));
        forwardMessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        forwardMessage.setSubject("Fwd: " + originalMessage.getSubject());
        forwardMessage.setSentDate(originalMessage.getSentDate());
        forwardMessage.setContent("---------- Forwarded message ----------\n"
                + "From: " + Arrays.toString(originalMessage.getFrom()) + "\n"
                + "Date: " + originalMessage.getSentDate() + "\n"
                + "Subject: " + originalMessage.getSubject() + "\n"
                + "To: " + Arrays.toString(originalMessage.getAllRecipients()) + "\n"
                + "Message: " + getTextFromMessage(originalMessage), "text/plain");

        Transport.send(forwardMessage);

        // Close resources
        inbox.close(false);
        store.close();

        System.out.println("Email forwarded successfully");
    }

    /**
     * Deletes an email by its message number.
     *
     * @param messageId The message number of the email to delete.
     * @throws Exception If an error occurs while deleting the email.
     */
    public void deleteEmail(String messageId) throws Exception {
        String host = imapHost;
        String username = imapUsername;
        String password = imapPassword;

        Properties props = new Properties();
        props.setProperty("mail.imap.ssl.enable", "true");

        Session session = Session.getInstance(props);
        Store store = session.getStore("imap");
        store.connect(host, username, password);

        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_WRITE); // Open in read-write mode to allow deletion
        Message message = inbox.getMessage(Integer.parseInt(messageId));
        message.setFlag(Flags.Flag.DELETED, true);

        // Close the folder with expunge option to permanently delete the message
        inbox.close(true);
        store.close();

        System.out.println("Email deleted successfully");
    }
}
