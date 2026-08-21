package com.myworkflow.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myworkflow.module.system.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    @Select("SELECT DISTINCT m.* FROM sys_menu m " +
            "INNER JOIN sys_role_menu rm ON m.id = rm.menu_id " +
            "INNER JOIN sys_user_role ur ON ur.role_id = rm.role_id " +
            "WHERE ur.user_id = #{userId} AND m.deleted = 0 AND m.status = 1 " +
            "ORDER BY m.sort_no ASC, m.id ASC")
    List<SysMenu> selectByUserId(Long userId);
}
