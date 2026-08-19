package com.myworkflow.module.notify.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myworkflow.common.context.UserContext;
import com.myworkflow.common.result.PageResult;
import com.myworkflow.module.notify.entity.WfNotifyMessage;
import com.myworkflow.module.notify.mapper.WfNotifyMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyService {

    private final WfNotifyMessageMapper messageMapper;

    @Value("${myworkflow.notify.in-app-enabled:true}")
    private boolean inAppEnabled;

    public void send(Long userId, String title, String content, String msgType, String bizId) {
        if (!inAppEnabled || userId == null) {
            return;
        }
        WfNotifyMessage msg = new WfNotifyMessage();
        msg.setUserId(userId);
        msg.setTitle(title);
        msg.setContent(content);
        msg.setMsgType(msgType);
        msg.setBizId(bizId);
        msg.setReadFlag(0);
        messageMapper.insert(msg);
        log.debug("站内信 -> userId={}, title={}", userId, title);
    }

    public PageResult<WfNotifyMessage> myMessages(long page, long size, Integer readFlag) {
        Page<WfNotifyMessage> p = messageMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<WfNotifyMessage>()
                        .eq(WfNotifyMessage::getUserId, UserContext.currentUserId())
                        .eq(readFlag != null, WfNotifyMessage::getReadFlag, readFlag)
                        .orderByDesc(WfNotifyMessage::getCreateTime));
        return PageResult.of(p.getTotal(), p.getRecords());
    }

    public void markRead(Long id) {
        WfNotifyMessage msg = messageMapper.selectById(id);
        if (msg != null && msg.getUserId().equals(UserContext.currentUserId())) {
            msg.setReadFlag(1);
            messageMapper.updateById(msg);
        }
    }

    public long unreadCount() {
        return messageMapper.selectCount(new LambdaQueryWrapper<WfNotifyMessage>()
                .eq(WfNotifyMessage::getUserId, UserContext.currentUserId())
                .eq(WfNotifyMessage::getReadFlag, 0));
    }
}
