package com.tianji.learning.controller;


import com.tianji.common.utils.BeanUtils;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.domain.vo.PointsBoardSeasonVO;
import com.tianji.learning.service.IPointsBoardSeasonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author Sunset
 * @since 2023-08-11
 */
@RestController
@Api(tags = "赛季相关接口")
@RequestMapping("/boards/seasons")
@RequiredArgsConstructor
public class PointsBoardSeasonController {

    private final IPointsBoardSeasonService iPointsBoardSeasonService;

    @ApiOperation("查询赛季列表")
    @GetMapping("/list")
    public List<PointsBoardSeasonVO> queryPointsBoardSeasonList(){
        //查询赛季集合
        List<PointsBoardSeason> list = iPointsBoardSeasonService.list();
        //添加集合返回
        List<PointsBoardSeasonVO> voList = BeanUtils.copyList(list, PointsBoardSeasonVO.class);
        return voList;
    }


}
