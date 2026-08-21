package com.myworkflow.module.process.service;

/**
 * 流程实例真正结束时通知业务侧。没有监听者或匹配不到单据时静默跳过。
 */
public interface ProcessFinishListener {

    /**
     * @param processStatus COMPLETED 正常走完；REJECTED 驳回并终止。回退不停流程，不会进这里。
     */
    void onProcessFinished(String processInstId, String businessKey, String processStatus);
}
