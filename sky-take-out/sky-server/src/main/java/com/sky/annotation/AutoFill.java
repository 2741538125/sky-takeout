package com.sky.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
/**
 * 自定义注解。用于标识某个方法需要进行功能字段的自动填充处理
 */

import com.sky.enumeration.OperationType;;
@Target(ElementType.METHOD) //指定只能用于方法，注解作用的地点
@Retention(RetentionPolicy.RUNTIME) //注解作用的时间
public @interface AutoFill {
    //数据库操作类型，update, insert
    OperationType value();
}
