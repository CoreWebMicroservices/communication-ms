package com.corems.communicationms.app.service;

import com.corems.common.exception.ServiceException;
import com.corems.common.security.SecurityUtils;
import com.corems.communicationms.api.model.ChannelType;
import com.corems.communicationms.api.model.EmailMessageRequest;
import com.corems.communicationms.api.model.EmailNotificationRequest;
import com.corems.communicationms.api.model.EmailPayload;
import com.corems.communicationms.api.model.MessageResponse;
import com.corems.communicationms.api.model.NotificationResponse;
import com.corems.communicationms.api.model.SendStatus;
import com.corems.communicationms.api.model.TemplateRequest;
import com.corems.communicationms.app.config.MailConfig;
import com.corems.communicationms.app.entity.EmailAttachmentEntity;
import com.corems.communicationms.app.entity.EmailMessageEntity;
import com.corems.communicationms.app.model.MessageStatus;
import com.corems.communicationms.app.model.MessageSenderType;
import com.corems.communicationms.app.repository.MessageRepository;
import com.corems.communicationms.app.service.provider.EmailServiceProvider;
import com.corems.common.security.UserPrincipal;
import com.corems.documentms.api.model.DocumentResponse;
import com.corems.templatems.client.TemplateRenderingApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.corems.communicationms.api.model.MessageResponse.SentByTypeEnum;
import com.corems.documentms.client.DocumentApi;
import com.corems.common.exception.handler.DefaultExceptionReasonCodes;
import com.corems.templatems.api.model.RenderTemplateRequest;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailService {
    private final MailConfig config;
    private final MessageRepository messageRepository;
    private final EmailServiceProvider emailServiceProvider;
    private final MessageDispatcher messageDispatcher;
    private final DocumentApi documentApi;
    private final TemplateRenderingApi templateRenderingApi;

    public MessageResponse sendMessage(EmailMessageRequest emailRequest) {
        if (emailRequest.getTemplate() != null) {
            emailRequest.setEmailType(EmailMessageRequest.EmailTypeEnum.HTML);
        }

        String body = resolveBody(emailRequest.getBody(), emailRequest.getTemplate());
        
        EmailMessageEntity emailEntity = createEntity(emailRequest, body);
        EmailPayload payload = getPayload(emailRequest, body);

        if (payload.getDocumentUuids() != null) {
            validateAttachments(payload.getDocumentUuids()).forEach(documentResponse -> {
                EmailAttachmentEntity att = new EmailAttachmentEntity();
                att.setEmailMessage(emailEntity);
                att.setDocumentUuid(documentResponse.getUuid());
                att.setChecksum(documentResponse.getChecksum());
                emailEntity.getAttachments().add(att);
            });
        }

        try {
            MessageStatus status = messageDispatcher.dispatchMessage(emailServiceProvider, emailEntity.getUuid(), payload);
            emailEntity.setStatus(status);
        } catch (ServiceException exception) {
            log.error("Failed to send email message: ", exception);

            emailEntity.setStatus(MessageStatus.failed);
            emailEntity.setSentAt(Instant.now());
            throw exception;
        }

        messageRepository.save(emailEntity);

        MessageResponse response = new MessageResponse();
        response.setUuid(emailEntity.getUuid());
        response.setUserId(emailEntity.getUserId());
        response.setType(ChannelType.EMAIL);
        response.setStatus(SendStatus.fromValue(emailEntity.getStatus().toString()));
        response.setCreatedAt(emailEntity.getCreatedAt().atOffset(ZoneOffset.UTC));
        response.setPayload(payload);
        response.setSentById(emailEntity.getSentById());
        if (emailEntity.getSentByType() != null) {
            response.setSentByType(SentByTypeEnum.fromValue(emailEntity.getSentByType().name()));
        }

        return response;
    }

    public NotificationResponse sendNotification(EmailNotificationRequest emailRequest) {
        try {
            if (emailRequest.getTemplate() != null) {
                emailRequest.setEmailType(EmailNotificationRequest.EmailTypeEnum.HTML);
            }

            String body = resolveBody(emailRequest.getBody(), emailRequest.getTemplate());
            EmailPayload payload = getPayload(emailRequest, body);

            if (payload.getDocumentUuids() != null) {
                validateAttachments(payload.getDocumentUuids());
            }

            MessageStatus status = messageDispatcher.dispatchMessage(emailServiceProvider, UUID.randomUUID(), payload);

            NotificationResponse response = new NotificationResponse();
            response.setStatus(SendStatus.fromValue(status.toString()));
            response.setSentAt(Instant.now().atOffset(ZoneOffset.UTC));

            return response;
        } catch (ServiceException exception) {
            log.error("Failed to send email message: ", exception);
            throw exception;
        }
    }

    private String resolveBody(String body, TemplateRequest templateRequest) {
        if (body != null && !body.isBlank()) {
            return body;
        }
        
        if (templateRequest != null) {
            try {
                var renderRequest = new RenderTemplateRequest();
                if (templateRequest.getParams() != null) {
                    renderRequest.setParams(templateRequest.getParams());
                }
                var renderResult = templateRenderingApi.renderTemplate(
                    templateRequest.getTemplateId(),
                    renderRequest,
                    templateRequest.getLanguage()
                );
                return renderResult.getHtml();
            } catch (Exception e) {
                log.error("Failed to render template: {}", e.getMessage());
                throw ServiceException.of(
                    DefaultExceptionReasonCodes.SERVER_ERROR,
                    "Failed to render template: " + e.getMessage()
                );
            }
        }
        
        throw ServiceException.of(
            DefaultExceptionReasonCodes.INVALID_REQUEST,
            "Either body or template must be provided"
        );
    }

    private EmailPayload getPayload(EmailMessageRequest emailRequest, String body) {
        EmailPayload payload = new EmailPayload("email", emailRequest.getSubject(), emailRequest.getRecipient());
        payload.setBody(body);
        payload.setSender(emailRequest.getSender() == null ? config.getDefaultFrom() : emailRequest.getSender());
        payload.setEmailType(EmailPayload.EmailTypeEnum.fromValue(emailRequest.getEmailType().getValue()));
        payload.setSenderName(emailRequest.getSenderName());
        payload.setCc(emailRequest.getCc());
        payload.setBcc(emailRequest.getBcc());
        payload.setDocumentUuids(emailRequest.getDocumentUuids());
        return payload;
    }

    private EmailPayload getPayload(EmailNotificationRequest emailRequest, String body) {
        EmailPayload payload = new EmailPayload("email", emailRequest.getSubject(), emailRequest.getRecipient());
        payload.setBody(body);
        payload.setSender(emailRequest.getSender() == null ? config.getDefaultFrom() : emailRequest.getSender());
        payload.setEmailType(EmailPayload.EmailTypeEnum.fromValue(emailRequest.getEmailType().getValue()));
        payload.setSenderName(emailRequest.getSenderName());
        payload.setCc(emailRequest.getCc());
        payload.setBcc(emailRequest.getBcc());
        payload.setDocumentUuids(emailRequest.getDocumentUuids());
        return payload;
    }

    private EmailMessageEntity createEntity(EmailMessageRequest emailRequest, String body) {
        EmailMessageEntity emailEntity = new EmailMessageEntity();
        emailEntity.setUuid(UUID.randomUUID());
        emailEntity.setEmailType(emailRequest.getEmailType() == null ? "TXT" : emailRequest.getEmailType().toString());
        emailEntity.setSubject(emailRequest.getSubject());
        emailEntity.setSender(emailRequest.getSender() == null ? config.getDefaultFrom() : emailRequest.getSender());
        emailEntity.setSenderName(emailRequest.getSenderName());
        if (emailRequest.getCc() != null && !emailRequest.getCc().isEmpty()) {
            emailEntity.setCc(String.join(",", emailRequest.getCc()));
        }
        if (emailRequest.getBcc() != null && !emailRequest.getBcc().isEmpty()) {
            emailEntity.setBcc(String.join(",", emailRequest.getBcc()));
        }
        emailEntity.setRecipient(emailRequest.getRecipient());
        emailEntity.setBody(body);
        emailEntity.setUserId(emailRequest.getUserId());
        emailEntity.setCreatedAt(Instant.now());
        emailEntity.setStatus(MessageStatus.created);

        Optional<UserPrincipal> userPrincipal = SecurityUtils.getUserPrincipalOptional();
        if (userPrincipal.isPresent() && userPrincipal.get().getUserId() != null) {
            emailEntity.setSentById(userPrincipal.get().getUserId());
            emailEntity.setSentByType(MessageSenderType.user);
        } else {
            emailEntity.setSentByType(MessageSenderType.system);
        }

        messageRepository.save(emailEntity);
        return emailEntity;
    }

    private List<DocumentResponse> validateAttachments(List<UUID> documentUuids) {
        if (documentUuids == null || documentUuids.isEmpty()) return List.of();

        long uniqueCount = documentUuids.stream().distinct().count();
        if (uniqueCount != documentUuids.size()) {
            throw ServiceException.of(DefaultExceptionReasonCodes.INVALID_REQUEST, "Duplicate document UUIDs found in attachment list.");
        }

        List<DocumentResponse> documents = new ArrayList<>();
        for (UUID uuid : documentUuids) {
            try {
                var meta = documentApi.getDocumentMetadata(uuid);
                if (meta == null) {
                    throw ServiceException.of(DefaultExceptionReasonCodes.INVALID_REQUEST, "Invalid or missing document: " + uuid);
                }
                documents.add(meta);
            } catch (WebClientResponseException wex) {
                log.error("Document validation failed for {}: {}", uuid, wex.getMessage());
                throw ServiceException.of(DefaultExceptionReasonCodes.INVALID_REQUEST, "Invalid or missing document: " + uuid);
            } catch (ServiceException se) {
                log.error("Document validation failed for {}: {}", uuid, se.getMessage());
                throw se;
            } catch (Exception ex) {
                log.error("Unexpected error during document validation for {}: {}", uuid, ex.getMessage(), ex);
                throw ServiceException.of(DefaultExceptionReasonCodes.SERVER_ERROR, "Document validation failed: " + uuid + " - " + ex.getMessage());
            }
        }

        return documents;
    }
}
