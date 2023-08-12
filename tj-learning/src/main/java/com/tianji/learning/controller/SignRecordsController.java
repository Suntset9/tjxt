package com.tianji.learning.controller;


import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.service.ISignRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Api(tags = "签到相关接口")
@RequestMapping("/sign-records")
@RequiredArgsConstructor
public class SignRecordsController {

    private final ISignRecordService recordService;

    @PostMapping
    @ApiOperation("签到功能接口")
    public SignResultVO addSignRecords(){
        return recordService.addSignRecords();
    }

}
