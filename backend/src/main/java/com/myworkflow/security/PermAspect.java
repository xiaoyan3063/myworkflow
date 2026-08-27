package com.myworkflow.security;

import com.myworkflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
@RequiredArgsConstructor
public class PermAspect {

    private final PermissionService permissionService;

    /**
     * 注解可以标在方法或整个控制器上。切点里不能跨 || 绑定形参（AspectJ 会报参数绑定歧义），
     * 因此这里用反射取注解：方法上的优先，其次找类上的。
     */
    @Around("@annotation(com.myworkflow.security.RequiresPerm)"
            + " || @within(com.myworkflow.security.RequiresPerm)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        RequiresPerm requiresPerm = resolve(pjp);
        if (requiresPerm != null && !permissionService.hasPerm(requiresPerm.value())) {
            throw new BizException(403, "无权限：" + requiresPerm.value());
        }
        return pjp.proceed();
    }

    private RequiresPerm resolve(ProceedingJoinPoint pjp) {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        RequiresPerm onMethod = AnnotatedElementUtils.findMergedAnnotation(method, RequiresPerm.class);
        if (onMethod != null) {
            return onMethod;
        }
        Class<?> target = pjp.getTarget() == null
                ? method.getDeclaringClass() : AopUtils.getTargetClass(pjp.getTarget());
        return AnnotatedElementUtils.findMergedAnnotation(target, RequiresPerm.class);
    }
}
