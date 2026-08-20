package com.myworkflow.module.process.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 「我的已办」按单据聚合。
 * <p>
 * Flowable 的查询 API 没法对流程实例去重，而一个人在同一张单据上可能处理过多个节点
 * （例如驳回后又处理了重新提交），逐条列出来会让同一张单据出现多行。
 * 这里直接对 ACT_HI_TASKINST 做 GROUP BY 拿到去重后的实例 ID，
 * 节点名等明细再交给 Flowable API 按页查询。
 */
@Mapper
public interface WfDoneTaskMapper {

    @Select("SELECT COUNT(DISTINCT PROC_INST_ID_) FROM ACT_HI_TASKINST "
            + "WHERE ASSIGNEE_ = #{userId} AND END_TIME_ IS NOT NULL")
    long countDoneInstances(@Param("userId") String userId);

    @Select("SELECT PROC_INST_ID_ FROM ACT_HI_TASKINST "
            + "WHERE ASSIGNEE_ = #{userId} AND END_TIME_ IS NOT NULL "
            + "GROUP BY PROC_INST_ID_ "
            + "ORDER BY MAX(END_TIME_) DESC")
    List<String> selectDoneInstanceIds(IPage<String> page, @Param("userId") String userId);
}
