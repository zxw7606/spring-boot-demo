package com.xkcoding.properties.bean;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
@Slf4j
public class Bean2 implements InitializingBean, DisposableBean {

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("{} init ", getClass().getName() );
    }


    @Override
    public void destroy() throws Exception {
        log.info("{} destory ", getClass().getName() );

    }


}
