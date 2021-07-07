package com.xkcoding.exception;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class DubboExceptionHandler {


    public static String blockMthod(BlockException e) {
        log.error("block method :", e);
        return "-1";
    }


}
