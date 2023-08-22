package com.tianji.learning.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionPageQuery;
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
@RequestMapping("/questions")
@RequiredArgsConstructor
@Api(tags = "互动问题相关接口-用户端")
public class InteractionQuestionController {

     private final IInteractionQuestionService questionService;

    @ApiOperation("新增互动提问")
    @PostMapping
    public void saveQuestion(@RequestBody @Validated QuestionFormDTO questionTO){
        questionService.saveQuestion(questionTO);
    }

    @PutMapping("/{id}")
    @ApiOperation("修改互动问题")
    public void updateQuestion(@PathVariable Long id , @RequestBody QuestionFormDTO questionDTO){
        questionService.updateQuestion(id,questionDTO);
    }

    @GetMapping("/page")
    @ApiOperation("分页查询互动问题-用户端")
    public PageDTO<QuestionVO> queryQuestionPage (QuestionPageQuery query) {
        return  questionService.queryQuestionPage(query);
    }

    @GetMapping("/{id}")
    @ApiOperation("根据id查询问题详情-用户端")
    public QuestionVO queryQuestionById (@PathVariable("id") Long id){
        return questionService.queryQuestionById(id);
    }


}
