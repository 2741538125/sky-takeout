package com.sky.aspect;

import java.lang.reflect.Method;
import java.time.LocalDateTime;


import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import com.sky.annotation.AutoFill;
import com.sky.context.BaseContext;
import com.sky.enumeration.OperationType;

import org.aspectj.lang.reflect.MethodSignature;
import lombok.extern.slf4j.Slf4j;

/**
 * 自定义切面，实现公共字段自动填充
 */
@Aspect //表示其是一个切面，切面 = 切入点 + 通知
@Component  //标注一个类为Spring容器的Bean
@Slf4j  //简化在java中添加日志的操作
public class AutoFillAspect {

    /**
     * 切入点
     */
    @Pointcut("execution(* com.sky.mapper.*.*(..)) && @annotation(com.sky.annotation.AutoFill)") //切点表达式
    public void autoFillPointCut() {}

    /**
     * 前置通知, 在通知中进行公共字段的赋值
     */
    @Before("autoFillPointCut()") //定义是哪个切入点执行前执行
    public void autoFill(JoinPoint joinPoint) {
        log.info("开始进行公共字段自动填充...");

        //第一步，获取当前被拦截的方法上的数据库操作类型
        MethodSignature signature = (MethodSignature)joinPoint.getSignature(); //方法签名对象
        AutoFill autoFill = signature.getMethod().getAnnotation(AutoFill.class); //获得方法上的注解对象
        OperationType operationType = autoFill.value(); //获得数据库操作类型

        //第二步，获取到当前被拦截方法的参数，也就是实体对象Employee实体
        Object[] args = joinPoint.getArgs();
        if(args == null || args.length == 0) { return; }

        Object entity = args[0]; //如果参数有多个，统一使用第一个，用Object，泛用性
        
        //第三步，为公共属性来统一准备赋值的数据，也就是当前时间和当前登陆的用户id
        LocalDateTime now = LocalDateTime.now();
        Long currentId = BaseContext.getCurrentId();

        //第四步，根据当前的操作类型，通过<反射>来为不同的属性赋值
        if(operationType == OperationType.INSERT) {
            //为四个公共字段赋值
            try {
                Method setCreateTime = entity.getClass().getDeclaredMethod("setCreateTime", LocalDateTime.class);
                Method setCreateUser = entity.getClass().getDeclaredMethod("setCreateUser", Long.class);
                Method setUpdateTime = entity.getClass().getDeclaredMethod("setUpdateTime", LocalDateTime.class);
                Method setUpdateUser = entity.getClass().getDeclaredMethod("setUpdateUser", Long.class);

                //通过反射为对象属性赋值
                setCreateTime.invoke(entity, now);
                setCreateUser.invoke(entity, currentId);
                setUpdateTime.invoke(entity, now);
                setUpdateUser.invoke(entity, currentId);
            } catch (Exception e) {
                e.printStackTrace();
            }
            
        }else if(operationType == OperationType.UPDATE) {
            //为两个update字段赋值
            try {
                Method setUpdateTime = entity.getClass().getDeclaredMethod("setUpdateTime", LocalDateTime.class);
                Method setUpdateUser = entity.getClass().getDeclaredMethod("setUpdateUser", Long.class);

                //通过反射为对象属性赋值
                setUpdateTime.invoke(entity, now);
                setUpdateUser.invoke(entity, currentId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
