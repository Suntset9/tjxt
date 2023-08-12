package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author Sunset
 * @since 2023-08-08
 */

@RestController
@RequestMapping("/admin/questions")
@RequiredArgsConstructor
@Api(tags = "互动问题相关接口-管理端")
public class InteractionQuestionAdminController {

     private final IInteractionQuestionService questionService;

     @GetMapping("/page")
     @ApiOperation("分页查询问题-管理端")
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query){
        return questionService.queryQuestionPageAdmin(query);
     }

}
