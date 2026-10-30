package com.wherelee.edu.message;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;
import com.wherelee.edu.common.PageResult;
import com.wherelee.edu.message.dto.MessageDtos;
import com.wherelee.edu.user.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private static final int MAX_PAGE_SIZE = 50;

    private final MessageMapper messageMapper;
    private final UserMapper userMapper;

    /**
     * 发送站内信：收件人可以是「指定的一批人」或「某个角色的所有人」，二选一。
     * 群发走一条批量 INSERT，不逐条插。
     */
    @Transactional
    public int send(Long senderId, MessageDtos.SendRequest request) {
        Set<Long> receivers = new LinkedHashSet<>();

        if (request.receiverIds() != null && !request.receiverIds().isEmpty()) {
            for (Long id : request.receiverIds()) {
                if (userMapper.findById(id) == null) {
                    throw new BizException(ErrorCode.USER_NOT_FOUND, "收件人不存在: " + id);
                }
                receivers.add(id);
            }
        } else if (request.roleCode() != null && !request.roleCode().isBlank()) {
            receivers.addAll(userMapper.findIdsByRoleCode(request.roleCode()));
        } else {
            throw new BizException(ErrorCode.INVALID_PARAM, "必须指定收件人或角色");
        }

        if (receivers.isEmpty()) {
            log.warn("站内信没有匹配到收件人 roleCode={}", request.roleCode());
            return 0;
        }

        List<Message> messages = new ArrayList<>(receivers.size());
        for (Long receiverId : receivers) {
            Message m = new Message();
            m.setReceiverId(receiverId);
            m.setSenderId(senderId);
            m.setTitle(request.title());
            m.setContent(request.content());
            messages.add(m);
        }
        messageMapper.batchInsert(messages);
        log.info("站内信发送 sender={} receivers={} title={}", senderId, messages.size(), request.title());
        return messages.size();
    }

    public PageResult<MessageDtos.MessageSummaryVO> inbox(Long receiverId, Boolean isRead, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long total = messageMapper.countPage(receiverId, isRead);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<MessageDtos.MessageSummaryVO> records = messageMapper
                .findPage(receiverId, isRead, (safePage - 1) * safeSize, safeSize)
                .stream().map(MessageDtos.MessageSummaryVO::from).toList();
        return PageResult.of(records, total, safePage, safeSize);
    }

    /** 详情只能看自己的：不是自己的消息一律按不存在处理，不泄露别人的消息 ID */
    public MessageDtos.MessageVO detail(Long receiverId, Long messageId) {
        Message message = messageMapper.findById(messageId);
        if (message == null || !message.getReceiverId().equals(receiverId)) {
            throw BizException.of(ErrorCode.MESSAGE_NOT_FOUND);
        }
        return MessageDtos.MessageVO.from(message);
    }

    public void markRead(Long receiverId, Long messageId) {
        int updated = messageMapper.markRead(messageId, receiverId);
        if (updated == 0 && messageMapper.findById(messageId) == null) {
            throw BizException.of(ErrorCode.MESSAGE_NOT_FOUND);
        }
    }

    public int markAllRead(Long receiverId) {
        return messageMapper.markAllRead(receiverId);
    }

    public long unreadCount(Long receiverId) {
        return messageMapper.countUnread(receiverId);
    }
}
