package com.xkcoding.dubbo.consumer.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.dubbo.config.annotation.Reference;
import com.xkcoding.dubbo.common.service.HelloService;
import com.xkcoding.exception.DubboExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * Hello服务API
 * </p>
 *
 * @author yangkai.shen
 * @date Created in 2018-12-25 17:22
 */
@RestController
@Slf4j
public class HelloController {

    @Reference
    private HelloService helloService;


    @SentinelResource(value = "sayHello",
        blockHandler = "blockMthod", blockHandlerClass = DubboExceptionHandler.class,
        fallback = "fallbackMethod")
    @GetMapping("/sayHello")
    public String sayHello(@RequestParam(defaultValue = "xkcoding") String name) {
        log.info("i'm ready to call someone......");

//        Object currentProxy = AopContext.currentProxy();

        if (name.equals("xkcoding")) {
            throw new IllegalArgumentException("defaut name is" + name);
        }

        return helloService.sayHello(name);
    }


    public String fallbackMethod(String name, Throwable ex) {
        log.error(name, ex);
        return "fallbackMethod -1";
    }

}
