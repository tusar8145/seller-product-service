package com.enterprise.product.config.datasource;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(0)
public class MasterReadAspect {

    @Around("@annotation(readFromMaster) || @within(readFromMaster)")
    public Object forceMaster(ProceedingJoinPoint pjp, ReadFromMaster readFromMaster) throws Throwable {
        RoutingDataSource.setDataSourceType(RoutingDataSource.DataSourceType.MASTER);
        try {
            return pjp.proceed();
        } finally {
            RoutingDataSource.clearDataSourceType();
        }
    }
}
