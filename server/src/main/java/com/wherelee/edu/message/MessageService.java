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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private static final int MAX_PAGE_SIZE = 50;
    /** 页码硬上限，防止 (page-1)*size 溢出成负 offset */
    private static final int MAX_PAGE = 10_000;

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
            Set<Long> requested = new LinkedHashSet<>(request.receiverIds());
            // 一次 IN 查出真实存在的收件人，避免逐个 findById 造成 N+1
            Set<Long> existing = new HashSet<>(userMapper.findExistingIds(List.copyOf(requested)));
            for (Long id : requested) {
                if (!existing.contains(id)) {
                    throw new BizException(ErrorCode.USER_NOT_FOUND, "收件人不存在: " + id);
                }
            }
            receivers.addAll(requested);
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

    /**
     * 业务动作产生通知时调用。
     *
     * 必须在主事务提交之后再发，理由有三条：
     *   1. 主事务回滚时通知不会发出，不会出现「通知指向一条不存在的数据」；
     *   2. 不用 REQUIRES_NEW 另开事务，不会在业务事务里多占一条连接；
     *   3. 发送失败只影响通知，不会把提问/回答本身带死。
     */
    public void notifyAfterCommit(Long receiverId, String title, String content, String link) {
        if (receiverId == null) {
            return;
        }
        Runnable task = () -> safeNotify(receiverId, title, content, link);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    /** 真正写消息的地方。这里在事务之外，try/catch 才拦得住失败 */
    private void safeNotify(Long receiverId, String title, String content, String link) {
        try {
            Message m = new Message();
            m.setReceiverId(receiverId);
            m.setSenderId(null);
            m.setTitle(title);
            m.setContent(content);
            m.setLink(link);
            messageMapper.insert(m);
        } catch (Exception e) {
            log.warn("系统通知发送失败 receiver={} title={}", receiverId, title, e);
        }
    }

    /**
     * 通知指向的问题被撤回后，把链接置空。消息本身留着——
     * 那是用户已经收到的内容，不能因为他撤了提问就把别人收到的信也删了。
     */
    public int clearLinkForQuestion(Long courseId, Long questionId) {
        return messageMapper.clearLink("/courses/" + courseId + "?question=" + questionId);
    }

    public PageResult<MessageDtos.MessageSummaryVO> inbox(Long receiverId, Boolean isRead, int page, int size) {
        int safePage = Math.min(Math.max(page, 1), MAX_PAGE);
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
