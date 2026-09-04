package com.myworkflow.module.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myworkflow.module.ticket.entity.TkTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TkTicketMapper extends BaseMapper<TkTicket> {
    @Select("SELECT * FROM tk_ticket WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    TkTicket selectForUpdate(@Param("id") Long id);
}
