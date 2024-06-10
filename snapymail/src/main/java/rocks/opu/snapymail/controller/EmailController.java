package rocks.opu.snapymail.controller;

import rocks.opu.snapymail.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.mail.Message;

import java.util.List;

@RestController
@RequestMapping("/api/emails")
public class EmailController {

    @Autowired
    private EmailService emailService;

    @PostMapping("/send") // POST /api/emails/send?to=...&subject=...&body=...
    public String sendEmail(@RequestParam String to,
            @RequestParam String subject,
            @RequestParam String body) {
        try {
            emailService.sendEmail(to, subject, body);
            return "Email sent successfully";
        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
    }

    @GetMapping("/folders") // GET /api/emails/folders
    public String getFolders() {
        try {
            return emailService.getFolders();
        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
    }

    @GetMapping("/receive") // GET /api/emails/receive
    public String receiveEmail(@RequestParam String folder) {
        try {
            // convert List<Message> to json string
            List<Message> messages = emailService.receiveEmails(folder);

            String result = "[";
            for (Message message : messages) {
                result += "{";
                result += "\"subject\": \"" + message.getSubject().replace("\"", "'") + "\",";
                // replace double quotes with single quotes
                result += "\"from\": \"" + message.getFrom()[0].toString().replace("\"", "'") + "\",";
                result += "\"date\": \"" + message.getSentDate() + "\",";
                result += "\"messageId\": \"" + message.getMessageNumber() + "\"";
                result += "},";
            }

            if (result == "[")
                return "[]"; // no emails

            result = result.substring(0, result.length() - 1);// remove last comma
            result += "]";

            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
    }

    @GetMapping("/read") // GET /api/emails/read?messageId=...
    public String readEmail(@RequestParam String messageId) {
        try {
            return emailService.readEmail(messageId);
        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
    }

    @PostMapping("/forward") // POST /api/emails/forward?messageId=...&to=...
    public String forwardEmail(@RequestParam String messageId,
            @RequestParam String to) {
        try {
            emailService.forwardEmail(messageId, to);
            return "Email forwarded successfully";
        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
    }

    @DeleteMapping("/delete") // DELETE /api/emails/delete?messageId=...
    public String deleteEmail(@RequestParam String messageId) {
        try {
            emailService.deleteEmail(messageId);
            return "Email deleted successfully";
        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
    }
}
