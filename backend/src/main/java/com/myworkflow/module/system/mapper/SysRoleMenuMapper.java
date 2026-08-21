package com.myworkflow.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myworkflow.module.system.entity.SysRoleMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {

    @Select("SELECT data_scope FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON ur.role_id = r.id " +
            "WHERE ur.user_id = #{userId} AND r.deleted = 0 AND r.status = 1")
    List<String> selectDataScopesByUserId(Long userId);
}
