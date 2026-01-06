package com.corems.communicationms.app.service;

import com.corems.common.exception.ServiceException;
import com.corems.common.exception.handler.DefaultExceptionReasonCodes;
import com.corems.common.queue.QueueClient;
import com.corems.common.queue.QueueMessage;
import com.corems.common.queue.QueueProvider;
import com.corems.communicationms.app.model.MessageStatus;
import com.corems.communicationms.app.service.provider.ChannelProvider;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageDispatcher {
    private final QueueProvider queueProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public <T> MessageStatus dispatchMessage(ChannelProvider<T> channelProvider, UUID messageId, T payload) throws ServiceException {
        if (queueProvider.isEnabled()) {
            QueueClient queueClient = queueProvider.getDefaultClient();

            QueueMessage qm = new QueueMessage();
            qm.setId(messageId.toString());
            qm.setType(channelProvider.getMessageType().toString());

            try {
                qm.setPayload(objectMapper.writeValueAsString(payload));
            } catch (Exception e) {
                throw ServiceException.of(DefaultExceptionReasonCodes.SERVER_ERROR, "Error serializing payload");
            }
            
            queueClient.send(qm);
            
            log.info("Message dispatched to queue: messageId={}, type={}, correlationId={}", 
                    messageId, channelProvider.getMessageType(), qm.getCorrelationId());
            
            return MessageStatus.enqueued;
        }

        // Direct send without queue
        channelProvider.send(payload);
        log.info("Message sent directly: messageId={}, type={}", 
                messageId, channelProvider.getMessageType());
        
        return MessageStatus.sent;
    }
}
