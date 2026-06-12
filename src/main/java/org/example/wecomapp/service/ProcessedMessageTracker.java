package org.example.wecomapp.service;

import org.example.wecomapp.dto.SyncMsgResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息去重追踪器
 *
 * <p>记录已处理过的消息 msgid，避免同一消息被多个并发回调重复处理。</p>
 * <p>定期清理过期记录（7天前），防止内存泄漏。</p>
 *
 * @author dixonyen
 */
@Service
public class ProcessedMessageTracker {

    private static final long EXPIRE_MS = 7L * 24 * 60 * 60 * 1000; // 7天

    /**
     * key = "external_userid:msgid", value = 处理时间戳
     */
    private final ConcurrentHashMap<String, Long> processed = new ConcurrentHashMap<>();

    private String buildKey(String externalUserid, String msgid) {
        return externalUserid + ":" + msgid;
    }

    /**
     * 判断消息是否已处理
     */
    public boolean isProcessed(String externalUserid, String msgid) {
        return processed.containsKey(buildKey(externalUserid, msgid));
    }

    /**
     * 标记单条消息为已处理
     */
    public void markProcessed(String externalUserid, String msgid) {
        processed.put(buildKey(externalUserid, msgid), System.currentTimeMillis());
    }

    /**
     * 批量标记消息为已处理
     */
    public void markAllProcessed(String externalUserid, List<String> msgids) {
        long now = System.currentTimeMillis();
        for (String msgid : msgids) {
            processed.put(buildKey(externalUserid, msgid), now);
        }
    }

    /**
     * 从消息列表中过滤出未处理的消息
     *
     * @param externalUserid 用户标识
     * @param messages       原始消息列表
     * @return 仅包含未处理消息的列表（保持原顺序）
     */
    public List<SyncMsgResponse.MsgItem> filterNewMessages(String externalUserid,
                                                            List<SyncMsgResponse.MsgItem> messages) {
        List<SyncMsgResponse.MsgItem> result = new ArrayList<>();
        for (SyncMsgResponse.MsgItem msg : messages) {
            if (msg.getOrigin() != null && msg.getOrigin() == 3) {
                // 只过滤用户消息（origin=3），系统事件和客服消息不追踪
                if (!isProcessed(externalUserid, msg.getMsgid())) {
                    result.add(msg);
                }
            }
        }
        return result;
    }

    /**
     * 定期清理过期记录，防止内存泄漏
     */
    @Scheduled(fixedRate = 3600000) // 每小时执行一次
    public void cleanup() {
        long now = System.currentTimeMillis();
        int removed = 0;
        var iterator = processed.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now - entry.getValue() > EXPIRE_MS) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            System.out.println("[ProcessedMessageTracker] 清理过期记录: " + removed + " 条");
        }
    }
}
