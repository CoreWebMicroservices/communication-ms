package com.corems.communicationms.app.service;

import com.corems.common.exception.ServiceException;
import com.corems.common.security.SecurityUtils;
import com.corems.communicationms.api.model.ChannelType;
import com.corems.communicationms.api.model.SmsMessageRequest;
import com.corems.communicationms.api.model.SmsNotificationRequest;
import com.corems.communicationms.api.model.SmsPayload;
import com.corems.communicationms.api.model.TemplateRequest;
import com.corems.communicationms.api.model.MessageResponse;
import com.corems.communicationms.api.model.NotificationResponse;
import com.corems.communicationms.api.model.SendStatus;
import com.corems.communicationms.app.entity.SMSMessageEntity;
import com.corems.communicationms.app.model.MessageStatus;
import com.corems.communicationms.app.model.MessageSenderType;
import com.corems.communicationms.app.repository.MessageRepository;
import com.corems.communicationms.app.service.provider.SmsServiceProvider;
import com.corems.common.security.UserPrincipal;
import com.corems.templatems.client.TemplateRenderingApi;
import com.corems.templatems.api.model.RenderTemplateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.corems.communicationms.api.model.MessageResponse.SentByTypeEnum;
import com.corems.common.exception.handler.DefaultExceptionReasonCodes;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmsService {
    private final MessageRepository messageRepository;
    private final SmsServiceProvider smsServiceProvider;
    private final MessageDispatcher messageDispatcher;
    private final TemplateRenderingApi templateRenderingApi;

    public MessageResponse sendMessage(SmsMessageRequest smsRequest) {
        String message = resolveMessage(smsRequest.getMessage(), smsRequest.getTemplate());
        
        SMSMessageEntity smsEntity = createEntity(smsRequest, message);
        SmsPayload payload = getPayload(smsRequest.getPhoneNumber(), message);
        try {
            MessageStatus status = messageDispatcher.dispatchMessage(smsServiceProvider, smsEntity.getUuid(), payload);
            smsEntity.setStatus(status);
            messageRepository.save(smsEntity);
        } catch (ServiceException exception) {
            log.error("Failed to send SMS message: ", exception);

            smsEntity.setStatus(MessageStatus.failed);
            smsEntity.setSentAt(Instant.now());
            messageRepository.save(smsEntity);
            throw exception;
        }

        MessageResponse response = new MessageResponse();
        response.setUuid(smsEntity.getUuid());
        response.setUserId(smsEntity.getUserId());
        response.setType(ChannelType.SMS);
        response.setStatus(SendStatus.fromValue(smsEntity.getStatus().toString()));
        response.setCreatedAt(smsEntity.getCreatedAt().atOffset(ZoneOffset.UTC));
        response.setPayload(payload);
        response.setSentById(smsEntity.getSentById());
        if (smsEntity.getSentByType() != null) {
            response.setSentByType(SentByTypeEnum.fromValue(smsEntity.getSentByType().name()));
        }

        return response;
    }

    public NotificationResponse sendNotification(SmsNotificationRequest smsRequest) {
        try {
            String message = resolveMessage(smsRequest.getMessage(), smsRequest.getTemplate());
            SmsPayload payload = getPayload(smsRequest.getPhoneNumber(), message);
            MessageStatus status = messageDispatcher.dispatchMessage(smsServiceProvider, UUID.randomUUID(), payload);

            NotificationResponse response = new NotificationResponse();
            response.setStatus(SendStatus.fromValue(status.toString()));
            response.setSentAt(Instant.now().atOffset(ZoneOffset.UTC));

            return response;
        } catch (ServiceException exception) {
            log.error("Failed to send SMS notification: ", exception);
            throw exception;
        }
    }

    private String resolveMessage(String message, TemplateRequest templateRequest) {
        if (message != null && !message.isBlank()) {
            return message;
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
            "Either message or template must be provided"
        );
    }

    private SmsPayload getPayload(String phoneNumber, String message) {
        SmsPayload payload = new SmsPayload("sms", phoneNumber);
        payload.setMessage(message);
        return payload;
    }

    private SMSMessageEntity createEntity(SmsMessageRequest smsRequest, String message) {
        SMSMessageEntity smsEntity = new SMSMessageEntity();
        smsEntity.setUuid(UUID.randomUUID());
        smsEntity.setPhoneNumber(smsRequest.getPhoneNumber());
        smsEntity.setMessage(message);
        smsEntity.setUserId(smsRequest.getUserId());
        smsEntity.setCreatedAt(Instant.now());
        smsEntity.setStatus(MessageStatus.created);

        UserPrincipal userPrincipal = SecurityUtils.getUserPrincipal();
        if (userPrincipal.getUserId() != null) {
            smsEntity.setSentById(userPrincipal.getUserId());
            smsEntity.setSentByType(MessageSenderType.user);
        } else {
            smsEntity.setSentByType(MessageSenderType.system);
        }

        messageRepository.save(smsEntity);
        return smsEntity;
    }
}
