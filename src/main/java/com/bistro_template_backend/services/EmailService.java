package com.bistro_template_backend.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    public void sendEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        message.setFrom(senderEmail); // Must match the username in application.properties

        mailSender.send(message);
    }

    public void sendHtmlEmail(String to, String subject, String htmlBody) throws MessagingException {
        logger.info("Attempting to send HTML email to: {}, subject: {}", to, subject);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // true indicates the body is HTML
            helper.setFrom(senderEmail);
            mailSender.send(message);
            logger.info("HTML email sent successfully to: {}", to);
        } catch (MessagingException e) {
            logger.error("Failed to send HTML email to: {}, error: {}", to, e.getMessage(), e);
            throw e;
        }
    }
}