package com.xkcoding.properties.bean;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@Slf4j
public class LifeCycleBean1 implements SmartLifecycle {

    boolean b = Boolean.FALSE;

    @Override
    public void start() {
        b = true;
        log.info("start");
    }

    @Override
    public void stop() {
        log.info("stop");
    }

    @Override
    public boolean isRunning() {
        log.info("isRunning");
        return b;
    }
}
