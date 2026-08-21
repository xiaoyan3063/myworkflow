package com.myworkflow.security;

import com.myworkflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class PermAspect {

    private final PermissionService permissionService;

    @Around("@annotation(requiresPerm)")
    public Object around(ProceedingJoinPoint pjp, RequiresPerm requiresPerm) throws Throwable {
        if (!permissionService.hasPerm(requiresPerm.value())) {
            throw new BizException(403, "无权限：" + requiresPerm.value());
        }
        return pjp.proceed();
    }
}
