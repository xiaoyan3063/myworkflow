package com.myworkflow.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myworkflow.module.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT r.role_code FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.deleted = 0")
    List<String> selectRoleCodesByUserId(Long userId);

    @Select("SELECT u.id FROM sys_user u " +
            "INNER JOIN sys_user_role ur ON u.id = ur.user_id " +
            "INNER JOIN sys_role r ON r.id = ur.role_id " +
            "WHERE r.role_code = #{roleCode} AND u.deleted = 0 AND u.status = 1")
    List<Long> selectUserIdsByRoleCode(String roleCode);

    @Select("SELECT id FROM sys_user WHERE dept_id = #{deptId} AND deleted = 0 AND status = 1")
    List<Long> selectUserIdsByDeptId(Long deptId);
}
